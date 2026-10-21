package org.local.tabgroups;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.DialogInterface;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * UI of the experimental injection. Uses host runtime API only through reflection.
 * Displays tabs grouped in a separate dialog. It does NOT patch 1DM+'s own
 * RecyclerView adapter or license system, and never closes/reloads host tabs.
 */
public final class TabGroupsOverlay {
    private static final Map<Activity, View> ATTACHED = new WeakHashMap<>();
    private static final Map<Activity, TabGroupCoordinator> CONTROLLERS = new WeakHashMap<>();
    private TabGroupsOverlay() { }

    public static boolean isBrowser(Activity activity) {
        Class<?> type = activity.getClass();
        while(type != null) {
            if ("acr.browser.lightning.activity.BrowserActivity".equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }
    private static int dp(Activity a, int v) {
        return (int)(v*a.getResources().getDisplayMetrics().density + 0.5f);
    }
    public static void install(Activity activity) {
        if (ATTACHED.containsKey(activity)) return;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        FrameLayout root=(FrameLayout)content;
        TextView button=new TextView(activity);
        button.setText("▦ Grupy");
        button.setTextSize(13);
        button.setTypeface(null,Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setElevation(dp(activity,6));
        GradientDrawable background=new GradientDrawable();
        background.setColor(Color.argb(224,37,54,77));
        background.setCornerRadius(dp(activity,22));
        button.setBackground(background);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(activity,86),dp(activity,42),Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        params.rightMargin=dp(activity,4);
        root.addView(button,params);
        ATTACHED.put(activity,button);
        button.setOnClickListener(v->open(activity));
        // Hold to hide the floating button when it overlaps content.
        button.setOnLongClickListener(v->{
            button.setVisibility(View.GONE);
            Toast.makeText(activity,"Przycisk Grup ukryty do ponownego wejścia do przeglądarki", Toast.LENGTH_SHORT).show();
            return true;
        });
    }
    public static void detach(Activity activity) { ATTACHED.remove(activity); CONTROLLERS.remove(activity); }

    private static Object nativeManager(Activity activity) throws Exception {
        Class<?> clz=activity.getClass();
        while(clz!=null) {
            try {
                Field f=clz.getDeclaredField("mTabsManager");
                f.setAccessible(true);
                Object out=f.get(activity);
                if(out==null) throw new IllegalStateException("Karty jeszcze się wczytują");
                return out;
            } catch (NoSuchFieldException ex) { clz=clz.getSuperclass(); }
        }
        throw new NoSuchFieldException("Nie znaleziono mTabsManager");
    }
    private static TabGroupCoordinator coordinator(Activity a, Object manager) throws IOException {
        TabGroupCoordinator c=CONTROLLERS.get(a);
        boolean privateMode=false;
        try { privateMode=(Boolean)a.getClass().getMethod("isIncognito").invoke(a); }
        catch (Exception ignored) { }
        if(c==null) {
            File state=new File(a.getFilesDir(),"tabgroups-experimental-v1.txt");
            c=new TabGroupCoordinator(state,new ReflectiveTabProvider(manager),()->{});
            CONTROLLERS.put(a,c);
            if(privateMode) c.changeMode(true);
            c.onSessionReady();
        } else if(c.isIncognito()!=privateMode) {
            c.changeMode(privateMode);
            c.onSessionReady();
        } else {
            c.reconcileAfterBulkChange();
        }
        return c;
    }
    private static void open(Activity a) {
        try {
            Object manager=nativeManager(a);
            TabGroupCoordinator c=coordinator(a,manager);
            showGroups(a,manager,c);
        } catch(Exception ex) { fail(a,ex); }
    }
    private static void showGroups(Activity a,Object manager,TabGroupCoordinator c) {
        List<TabGroupStore.Group> groups=c.groups();
        String[] choices=new String[groups.size()+2];
        for(int i=0;i<groups.size();i++) {
            TabGroupStore.Group g=groups.get(i);
            choices[i]=(g.getId().equals(c.activeGroupId())?"✓ ":"   ")+g.getName()+" ("+g.getTabIds().size()+")";
        }
        choices[groups.size()]="＋ Nowa grupa";
        choices[groups.size()+1]="⋯ Zarządzanie grupami";
        new AlertDialog.Builder(a).setTitle(c.isIncognito()?"Prywatne grupy":"Grupy kart 1DM+")
          .setItems(choices,(d,which)->{
              if(which==groups.size()) { editName(a,"Nowa grupa","",name->{c.createGroup(name);showGroups(a,manager,c);});return; }
              if(which==groups.size()+1) { showManage(a,manager,c);return; }
              try { c.activateGroup(groups.get(which).getId()); showGroupTabs(a,manager,c); }
              catch(Exception e){fail(a,e);}
          })
          .setNegativeButton("Zamknij",null).show();
    }
    private static void showGroupTabs(Activity a,Object manager,TabGroupCoordinator c) {
        List<Integer> ids=c.visibleTabIds();
        List<Integer> hostOrder=new ReflectiveTabProvider(manager).openTabIds();
        String[] names=new String[ids.size()+2];
        for(int i=0;i<ids.size();i++) {
            int index=hostOrder.indexOf(ids.get(i));
            try {
                Object tab=manager.getClass().getMethod("getTabAtPosition",int.class).invoke(manager,index);
                String title=(String)tab.getClass().getMethod("getTitle").invoke(tab);
                names[i]=(title==null||title.trim().isEmpty())?"Karta "+(i+1):title;
            } catch(Exception ex){names[i]="Karta "+(i+1);}
        }
        names[ids.size()]="↔ Przenieś obecną kartę do innej grupy";
        names[ids.size()+1]="← Wszystkie grupy";
        new AlertDialog.Builder(a)
            .setTitle("Karty grupy ("+ids.size()+")")
            .setItems(names,(d,which)->{
                if(which==ids.size()){ showMoveCurrent(a,manager,c); return; }
                if(which==ids.size()+1){showGroups(a,manager,c);return;}
                int index=hostOrder.indexOf(ids.get(which));
                if(index<0) return;
                try {
                    manager.getClass().getMethod("switchToTab",Activity.class,int.class).invoke(manager,a,index);
                } catch(Exception ex){fail(a,ex);}
            })
            .setNegativeButton("Zamknij",null).show();
    }
    private static void showMoveCurrent(Activity a,Object manager,TabGroupCoordinator c) {
        try {
            Object tab=manager.getClass().getMethod("getCurrentTab").invoke(manager);
            if(tab==null) {Toast.makeText(a,"Brak aktywnej karty",Toast.LENGTH_SHORT).show();return;}
            int tabId=((Number)tab.getClass().getMethod("getId").invoke(tab)).intValue();
            List<TabGroupStore.Group> groups=c.groups();
            String[] names=new String[groups.size()];
            for(int i=0;i<groups.size();i++) names[i]=groups.get(i).getName();
            new AlertDialog.Builder(a).setTitle("Przenieś bieżącą kartę")
                .setItems(names,(d,which)->{
                    try{c.moveTab(tabId,groups.get(which).getId());showGroupTabs(a,manager,c);}
                    catch(Exception ex){fail(a,ex);}
                }).setNegativeButton("Anuluj",null).show();
        } catch(Exception ex){fail(a,ex);}
    }
    private static void showManage(Activity a,Object manager,TabGroupCoordinator c){
        List<TabGroupStore.Group> groups=c.groups();
        String[] names=new String[groups.size()];
        for(int i=0;i<groups.size();i++) names[i]=groups.get(i).getName();
        new AlertDialog.Builder(a).setTitle("Zarządzaj grupami")
            .setItems(names,(d,which)->{
                TabGroupStore.Group group=groups.get(which);
                if(TabGroupStore.DEFAULT_ID.equals(group.getId())) {showGroups(a,manager,c);return;}
                new AlertDialog.Builder(a).setTitle(group.getName())
                    .setItems(new String[]{"Zmień nazwę","Usuń grupę"},(menu,action)->{
                        if(action==0) {
                            editName(a,"Nowa nazwa",group.getName(),name->{c.renameGroup(group.getId(),name);showManage(a,manager,c);});
                        } else {
                            new AlertDialog.Builder(a).setTitle("Usunąć grupę?")
                                .setMessage("Otwarte karty zostaną przeniesione do Pozostałe.")
                                .setPositiveButton("Usuń",(yes,n)->{
                                    try{c.deleteGroup(group.getId());showManage(a,manager,c);}
                                    catch(Exception ex){fail(a,ex);}
                                }).setNegativeButton("Anuluj",null).show();
                        }
                    }).show();
            }).setNegativeButton("Wróć",(d,w)->showGroups(a,manager,c)).show();
    }
    private interface NameHandler { void done(String name) throws Exception; }
    private static void editName(Activity a,String title,String previous,NameHandler callback){
        EditText input=new EditText(a);
        input.setSingleLine(true);
        input.setText(previous);
        input.selectAll();
        FrameLayout frame=new FrameLayout(a);
        int pad=dp(a,20);
        frame.setPadding(pad,0,pad,0);
        frame.addView(input);
        new AlertDialog.Builder(a).setTitle(title).setView(frame)
            .setPositiveButton("Zapisz",(d,w)->{
                try {callback.done(input.getText().toString());}
                catch(Exception ex){fail(a,ex);}
            }).setNegativeButton("Anuluj",null).show();
    }
    private static void fail(Activity a,Exception error){
        Toast.makeText(a,"Grupy: "+error.getClass().getSimpleName()+": "+error.getMessage(),Toast.LENGTH_LONG).show();
    }
}
