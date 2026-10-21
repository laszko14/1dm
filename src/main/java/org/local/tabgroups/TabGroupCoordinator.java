package org.local.tabgroups;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Integrates group metadata with real native tab IDs without owning or reloading WebViews.
 * Important: onSessionReady() MUST run only after native tab restoration is complete.
 */
public final class TabGroupCoordinator {
    public interface OpenTabProvider { List<Integer> openTabIds(); }
    public interface Listener { void onGroupsChanged(); }

    private final File stateFile;
    private final OpenTabProvider host;
    private final Listener listener;
    private final TabGroupStore normal;
    private final TabGroupStore privateGroups = new TabGroupStore();
    private boolean incognito, ready;

    public TabGroupCoordinator(File stateFile, OpenTabProvider host, Listener listener) throws IOException {
        this.stateFile=Objects.requireNonNull(stateFile);
        this.host=Objects.requireNonNull(host);
        this.listener=Objects.requireNonNull(listener);
        this.normal=TabGroupStore.load(stateFile);
    }
    private TabGroupStore current(){return incognito?privateGroups:normal;}
    private void requireReady(){if(!ready)throw new IllegalStateException("Browser session is still restoring");}
    private static String key(int id){return Integer.toString(id);}
    private static List<Integer> ids(List<Integer> source) {
        Objects.requireNonNull(source,"openTabIds");
        if(source.contains(null)||new HashSet<>(source).size()!=source.size())
            throw new IllegalArgumentException("Null or duplicate tab ID");
        return new ArrayList<>(source);
    }
    private static List<String> keys(List<Integer> source){
        List<String> out=new ArrayList<>();
        for(int id:source)out.add(key(id));
        return out;
    }
    public void changeMode(boolean privateMode){
        incognito=privateMode;ready=false;listener.onGroupsChanged();
    }
    public void onSessionReady() throws IOException {
        current().reconcileOpenTabs(keys(ids(host.openTabIds())));
        ready=true;changed();
    }
    public void onTabOpened(int id) throws IOException{
        requireReady();current().addNewTab(key(id));changed();
    }
    public void onTabClosed(int id) throws IOException{
        requireReady();current().closeTab(key(id));changed();
    }
    public String createGroup(String name) throws IOException{
        requireReady();String result=current().create(name);changed();return result;
    }
    public void renameGroup(String groupId,String name)throws IOException{
        requireReady();current().rename(groupId,name);changed();
    }
    public void deleteGroup(String groupId)throws IOException{
        requireReady();current().delete(groupId);changed();
    }
    public void activateGroup(String groupId)throws IOException{
        requireReady();current().activate(groupId);changed();
    }
    public void moveTab(int tabId,String groupId)throws IOException{
        requireReady();
        if(!ids(host.openTabIds()).contains(tabId))
            throw new IllegalArgumentException("No native tab with ID "+tabId);
        current().moveTab(key(tabId),groupId);changed();
    }
    public void reconcileAfterBulkChange()throws IOException{
        requireReady();current().reconcileOpenTabs(keys(ids(host.openTabIds())));changed();
    }
    public List<TabGroupStore.Group> groups(){return current().groups();}
    public String activeGroupId(){return current().activeGroupId();}
    public String groupOf(int id){return current().groupOf(key(id));}
    public boolean isIncognito(){return incognito;}
    public boolean isSessionReady(){return ready;}
    public List<Integer> visibleTabIds(){
        if(!ready)return Collections.emptyList();
        List<Integer> out=new ArrayList<>();
        for(int id:ids(host.openTabIds()))
            if(current().activeGroupId().equals(groupOf(id)))out.add(id);
        return out;
    }
    private void changed()throws IOException{
        // Never persist the mapping for private/incognito tabs.
        if(!incognito)normal.save(stateFile);
        listener.onGroupsChanged();
    }
}