package org.local.tabgroups;

import java.lang.reflect.*;
import java.util.*;

/** Adapter for DEX method signatures observed in the user's 1DM+ 18.2 APK.
 * This is NOT an APK patch: it requires host integration to run. */
public final class ReflectiveTabProvider implements TabGroupCoordinator.OpenTabProvider {
    private final Object manager;
    private final Method size, tabAt;
    public ReflectiveTabProvider(Object manager){
        this.manager=Objects.requireNonNull(manager);
        try{
            size=manager.getClass().getMethod("size");
            tabAt=manager.getClass().getMethod("getTabAtPosition",int.class);
        }catch(NoSuchMethodException e){
            throw new IllegalArgumentException("Unsupported native tab manager",e);
        }
    }
    public List<Integer> openTabIds(){
        try{
            int n=((Number)size.invoke(manager)).intValue();
            if(n<0||n>100000)throw new IllegalStateException("Invalid count: "+n);
            List<Integer> result=new ArrayList<>();
            for(int i=0;i<n;i++){
                Object tab=tabAt.invoke(manager,i);
                if(tab==null)throw new IllegalStateException("Missing native tab "+i);
                result.add(((Number)tab.getClass().getMethod("getId").invoke(tab)).intValue());
            }
            return result;
        }catch(ReflectiveOperationException e){
            throw new IllegalStateException("Unable to read native tabs",e);
        }
    }
}