package org.local.tabgroups;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Independent group metadata for an existing browser's open tab IDs.
 * Does not create/close WebViews, touch downloads, or take ownership of tabs.
 * Single-threaded UI/controller access (or caller-provided synchronization) required.
 */
public final class TabGroupStore {
    public static final String DEFAULT_ID = "default";
    public static final String DEFAULT_NAME = "Pozostałe";

    public static final class Group {
        private final String id;
        private String name;
        private final LinkedHashSet<String> tabIds = new LinkedHashSet<>();

        private Group(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        public String getName() { return name; }
        public List<String> getTabIds() { return Collections.unmodifiableList(new ArrayList<>(tabIds)); }
    }

    private final LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
    private String activeGroupId = DEFAULT_ID;

    public TabGroupStore() {
        groups.put(DEFAULT_ID, new Group(DEFAULT_ID, DEFAULT_NAME));
    }

    public List<Group> groups() {
        return Collections.unmodifiableList(new ArrayList<>(groups.values()));
    }

    public String activeGroupId() { return activeGroupId; }
    public Group get(String id) {
        Group group = groups.get(id);
        if (group == null) throw new IllegalArgumentException("Unknown group: " + id);
        return group;
    }

    public void activate(String id) { get(id); activeGroupId = id; }

    public String create(String name) {
        String cleaned = cleanName(name);
        String id = UUID.randomUUID().toString();
        groups.put(id, new Group(id, cleaned));
        return id;
    }

    public void rename(String id, String name) {
        Group g = get(id);
        if (DEFAULT_ID.equals(id)) throw new IllegalArgumentException("Default group cannot be renamed");
        g.name = cleanName(name);
    }

    /** A tab may belong to exactly one group. */
    public void moveTab(String tabId, String targetGroupId) {
        String tab = cleanTabId(tabId);
        Group destination = get(targetGroupId);
        for (Group g : groups.values()) g.tabIds.remove(tab);
        destination.tabIds.add(tab);
    }

    public void addNewTab(String tabId) {
        moveTab(tabId, activeGroupId);
    }

    public void closeTab(String tabId) {
        String tab = cleanTabId(tabId);
        for (Group g : groups.values()) g.tabIds.remove(tab);
    }

    /** Delete a group without closing tabs. Move its tabs into the default group. */
    public void delete(String id) {
        if (DEFAULT_ID.equals(id)) throw new IllegalArgumentException("Cannot delete default group");
        Group group = get(id);
        groups.get(DEFAULT_ID).tabIds.addAll(group.tabIds);
        groups.remove(id);
        if (activeGroupId.equals(id)) activeGroupId = DEFAULT_ID;
    }

    public String groupOf(String tabId) {
        String tab = cleanTabId(tabId);
        for (Group group : groups.values()) if (group.tabIds.contains(tab)) return group.id;
        return null;
    }

    /**
     * Reconcile with the browser's existing open-tab session on startup, restore,
     * or tab-list refresh. Unknown tabs go to Pozostałe; closed tabs are removed.
     */
    public void reconcileOpenTabs(Collection<String> openTabIds) {
        Objects.requireNonNull(openTabIds, "openTabIds");
        LinkedHashSet<String> allOpen = new LinkedHashSet<>();
        for (String id : openTabIds) allOpen.add(cleanTabId(id));
        LinkedHashSet<String> claimed = new LinkedHashSet<>();
        for (Group group : groups.values()) {
            group.tabIds.removeIf(id -> !allOpen.contains(id) || !claimed.add(id));
        }
        for (String id : allOpen) if (!claimed.contains(id)) groups.get(DEFAULT_ID).tabIds.add(id);
    }

    /** Save to an app-private file only. Caller chooses the file path. */
    public void save(File target) throws IOException {
        Objects.requireNonNull(target, "target");
        Properties p = new Properties();
        p.setProperty("format", "1");
        p.setProperty("active", activeGroupId);
        p.setProperty("count", Integer.toString(groups.size()));
        int i = 0;
        for (Group g : groups.values()) {
            String prefix = "group." + i++ + ".";
            p.setProperty(prefix + "id", g.id);
            p.setProperty(prefix + "name", g.name);
            List<String> encoded = new ArrayList<>();
            for (String tabId : g.tabIds) encoded.add(encode(tabId));
            p.setProperty(prefix + "tabs", String.join(",", encoded));
        }
        File parent = target.getAbsoluteFile().getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create parent directory");
        File tmp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, "1DM+ tab groups metadata; no page content stored");
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            // Avoid destroying an existing valid state if atomic replacement is unavailable.
            if (!target.exists() || !target.delete() || !tmp.renameTo(target)) {
                throw new IOException("Cannot replace tab groups state");
            }
        }
    }

    public static TabGroupStore load(File source) throws IOException {
        TabGroupStore store = new TabGroupStore();
        if (!source.isFile()) return store;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(source)) { p.load(in); }
        if (!"1".equals(p.getProperty("format"))) throw new IOException("Unsupported metadata format");
        int count;
        try { count = Integer.parseInt(p.getProperty("count", "0")); }
        catch (NumberFormatException e) { throw new IOException("Invalid group count", e); }
        if (count < 1 || count > 10000) throw new IOException("Invalid group count");
        store.groups.clear();
        for (int i = 0; i < count; i++) {
            String prefix = "group." + i + ".";
            String id = p.getProperty(prefix + "id");
            String name = p.getProperty(prefix + "name");
            if (id == null || id.isEmpty() || name == null || name.trim().isEmpty() || store.groups.containsKey(id))
                throw new IOException("Invalid or duplicate group at index " + i);
            Group group = new Group(id, name);
            String encoded = p.getProperty(prefix + "tabs", "");
            if (!encoded.isEmpty()) {
                try {
                    for (String tabId : encoded.split(",", -1)) group.tabIds.add(cleanTabId(decode(tabId)));
                } catch (IllegalArgumentException e) { throw new IOException("Invalid tab data", e); }
            }
            store.groups.put(id, group);
        }
        if (!store.groups.containsKey(DEFAULT_ID)) throw new IOException("Missing default group");
        store.activeGroupId = p.getProperty("active", DEFAULT_ID);
        if (!store.groups.containsKey(store.activeGroupId)) store.activeGroupId = DEFAULT_ID;
        // A corrupted file should not allow duplicate membership.
        Set<String> found = new LinkedHashSet<>();
        for (Group group : store.groups.values()) group.tabIds.removeIf(id -> !found.add(id));
        return store;
    }

    private static String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Group name required");
        String trimmed = name.trim();
        if (trimmed.length() > 80) throw new IllegalArgumentException("Group name too long");
        return trimmed;
    }
    private static String cleanTabId(String tabId) {
        if (tabId == null || tabId.isEmpty()) throw new IllegalArgumentException("Tab ID required");
        return tabId;
    }
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
