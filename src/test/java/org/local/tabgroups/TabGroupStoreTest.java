package org.local.tabgroups;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public final class TabGroupStoreTest {
    private static int checks;
    private static void eq(Object expected,Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
        checks++;
    }
    private static void mustFail(Runnable runnable) {
        try { runnable.run(); throw new AssertionError("Expected failure"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        TabGroupStore s=new TabGroupStore();
        eq("Pozostałe",s.get(TabGroupStore.DEFAULT_ID).getName());
        String film=s.create(" Filmy ");
        String series=s.create("Seriale");
        eq("Filmy",s.get(film).getName());
        s.activate(film);
        s.addNewTab("tab-1");
        s.addNewTab("tab/2&?");
        eq(film,s.groupOf("tab-1"));
        s.moveTab("tab-1",series);
        eq(series,s.groupOf("tab-1"));
        eq(1,s.get(film).getTabIds().size());
        s.rename(series,"Odcinki");
        eq("Odcinki",s.get(series).getName());
        File file=Files.createTempFile("tabgroups-", ".properties").toFile();
        try {
            s.save(file);
            TabGroupStore loaded=TabGroupStore.load(file);
            eq(film,loaded.activeGroupId());
            eq(series,loaded.groupOf("tab-1"));
            eq(film,loaded.groupOf("tab/2&?"));
            loaded.reconcileOpenTabs(Arrays.asList("tab-1","new-tab"));
            eq(nullAsText(),loaded.groupOf("tab/2&?")==null?nullAsText():"unexpected");
            eq(TabGroupStore.DEFAULT_ID,loaded.groupOf("new-tab"));
            loaded.delete(series);
            eq(TabGroupStore.DEFAULT_ID,loaded.groupOf("tab-1"));
            loaded.activate(film);
            loaded.delete(film);
            eq(TabGroupStore.DEFAULT_ID,loaded.activeGroupId());
            loaded.closeTab("tab-1");
            eq(nullAsText(),loaded.groupOf("tab-1")==null?nullAsText():"unexpected");
            mustFail(()->loaded.delete(TabGroupStore.DEFAULT_ID));
            mustFail(()->loaded.create(" "));
            mustFail(()->loaded.moveTab("x", "missing"));
            System.out.println("OK: " + checks + " checks");
        } finally { file.delete(); }
    }
    private static String nullAsText() { return "<none>"; }
}
