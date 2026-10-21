package org.local.tabgroups;
import java.util.*;
public final class ReflectiveTabProviderTest{
    public static class Tab{
        final int id;
        Tab(int id){this.id=id;}
        public int getId(){return id;}
    }
    public static class Tabs{
        public List<Tab> list=new ArrayList<>();
        public int size(){return list.size();}
        public Tab getTabAtPosition(int index){return list.get(index);}
    }
    public static void main(String[] args){
        Tabs tabs=new Tabs();
        tabs.list.add(new Tab(4));
        tabs.list.add(new Tab(8));
        ReflectiveTabProvider adapter=new ReflectiveTabProvider(tabs);
        if(!adapter.openTabIds().equals(Arrays.asList(4,8)))throw new AssertionError();
        tabs.list.clear();
        if(!adapter.openTabIds().isEmpty())throw new AssertionError();
        try{new ReflectiveTabProvider(new Object());throw new AssertionError();}
        catch(IllegalArgumentException expected){}
        System.out.println("OK: 3 adapter checks");
    }
}