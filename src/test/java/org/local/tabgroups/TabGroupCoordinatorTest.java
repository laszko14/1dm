package org.local.tabgroups;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class TabGroupCoordinatorTest {
    private static int count;
    private static void eq(Object a,Object b){
        if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);
        count++;
    }
    private interface Checked { void run()throws Exception; }
    private static void fail(Class<?> type,Checked cb)throws Exception{
        try{cb.run();throw new AssertionError("Expected "+type.getSimpleName());}
        catch(Exception e){if(!type.isInstance(e))throw e;count++;}
    }
    static class Host implements TabGroupCoordinator.OpenTabProvider,TabGroupCoordinator.Listener{
        final List<Integer> tabs=new ArrayList<>();
        int updates;
        public List<Integer> openTabIds(){return new ArrayList<>(tabs);}
        public void onGroupsChanged(){updates++;}
    }
    public static void main(String[] args)throws Exception{
        File folder=Files.createTempDirectory("tg").toFile();
        File state=new File(folder,"groups.properties");
        try{
            Host host=new Host();
            host.tabs.addAll(Arrays.asList(10,20,30));
            TabGroupCoordinator app=new TabGroupCoordinator(state,host,host);
            eq(false,app.isSessionReady());
            eq(Collections.emptyList(),app.visibleTabIds());
            fail(IllegalStateException.class,()->app.createGroup("Filmy"));
            app.onSessionReady();
            eq(true,state.isFile());
            eq(Arrays.asList(10,20,30),app.visibleTabIds());
            String films=app.createGroup("Filmy");
            app.moveTab(20,films);
            eq(films,app.groupOf(20));
            eq(Arrays.asList(10,30),app.visibleTabIds());
            app.activateGroup(films);
            eq(Arrays.asList(20),app.visibleTabIds());
            host.tabs.add(40);
            app.onTabOpened(40);
            eq(films,app.groupOf(40));
            eq(Arrays.asList(20,40),app.visibleTabIds());
            fail(IllegalArgumentException.class,()->app.moveTab(999,films));
            app.renameGroup(films,"Seriale");
            eq("Seriale",app.groups().get(1).getName());
            app.changeMode(true);
            eq(false,app.isSessionReady());
            host.tabs.clear();host.tabs.addAll(Arrays.asList(20,50));
            app.onSessionReady();
            eq(TabGroupStore.DEFAULT_ID,app.groupOf(20));
            String secret=app.createGroup("Prywatne");
            app.moveTab(20,secret);
            eq(secret,app.groupOf(20));
            // No incognito data may be written to the regular persistence file.
            TabGroupStore disk=TabGroupStore.load(state);
            eq(films,disk.groupOf("20"));
            eq(2,disk.groups().size());
            app.changeMode(false);
            host.tabs.clear();host.tabs.addAll(Arrays.asList(10,20,30,40));
            app.onSessionReady();
            eq(films,app.groupOf(20));
            eq(films,app.groupOf(40));
            app.activateGroup(TabGroupStore.DEFAULT_ID);
            eq(Arrays.asList(10,30),app.visibleTabIds());
            host.tabs.clear();host.tabs.addAll(Arrays.asList(30,20,10));
            app.reconcileAfterBulkChange();
            eq(Arrays.asList(30,10),app.visibleTabIds());
            eq(null,app.groupOf(40));
            app.deleteGroup(films);
            eq(Arrays.asList(30,20,10),app.visibleTabIds());
            host.tabs.remove(Integer.valueOf(10));
            app.onTabClosed(10);
            eq(null,app.groupOf(10));
            fail(IllegalArgumentException.class,()->app.moveTab(20,"invalid"));
            host.tabs.add(20);
            fail(IllegalArgumentException.class,app::reconcileAfterBulkChange);
            eq(true,host.updates>=8);
            System.out.println("OK: "+count+" coordinator checks");
        }finally{state.delete();folder.delete();}
    }
}