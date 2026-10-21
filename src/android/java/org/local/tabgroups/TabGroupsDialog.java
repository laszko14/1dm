package org.local.tabgroups;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.IOException;
import java.util.List;

/**
 * Native Android dialogs for group management, ready to be hooked into a host.
 * NOTE: compiling these classes alone does not modify the proprietary 1DM+ UI.
 */
public final class TabGroupsDialog {
    private TabGroupsDialog(){}
    private interface NameCallback { void onName(String name)throws IOException; }
    private static void notice(Activity a,Exception e){
        Toast.makeText(a,"Grupy: "+e.getMessage(),Toast.LENGTH_LONG).show();
    }
    private static void nameDialog(Activity a,String title,String initial,NameCallback callback){
        EditText edit=new EditText(a);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setText(initial);
        edit.selectAll();
        FrameLayout frame=new FrameLayout(a);
        int pad=Math.round(20*a.getResources().getDisplayMetrics().density);
        frame.setPadding(pad,0,pad,0);
        frame.addView(edit);
        new AlertDialog.Builder(a).setTitle(title).setView(frame)
          .setPositiveButton("Zapisz",(d,which)->{
              try{callback.onName(edit.getText().toString());}
              catch(IOException|IllegalArgumentException e){notice(a,e);}
          })
          .setNegativeButton("Anuluj",null).show();
    }
    public static void showGroupPicker(Activity a,TabGroupCoordinator groups){
        if(!groups.isSessionReady()){
            Toast.makeText(a,"Czekam na przywrócenie kart",Toast.LENGTH_SHORT).show();
            return;
        }
        List<TabGroupStore.Group> entries=groups.groups();
        String[] labels=new String[entries.size()];
        for(int i=0;i<entries.size();i++){
            TabGroupStore.Group group=entries.get(i);
            labels[i]=(group.getId().equals(groups.activeGroupId())?"✓  ":"     ")
                +group.getName()+" ("+group.getTabIds().size()+")";
        }
        new AlertDialog.Builder(a)
          .setTitle(groups.isIncognito()?"Prywatne grupy kart":"Grupy kart")
          .setItems(labels,(d,which)->{
              try{groups.activateGroup(entries.get(which).getId());}
              catch(IOException e){notice(a,e);}
          })
          .setPositiveButton("Nowa grupa",(d,w)->
              nameDialog(a,"Nowa grupa","",name->groups.createGroup(name)))
          .setNeutralButton("Zarządzaj",(d,w)->showManager(a,groups))
          .setNegativeButton("Zamknij",null).show();
    }
    public static void showMoveTab(Activity a,TabGroupCoordinator groups,int nativeTabId){
        List<TabGroupStore.Group> entries=groups.groups();
        String[] names=new String[entries.size()];
        for(int i=0;i<entries.size();i++)names[i]=entries.get(i).getName();
        new AlertDialog.Builder(a).setTitle("Przenieś kartę")
          .setItems(names,(d,i)->{
              try{groups.moveTab(nativeTabId,entries.get(i).getId());}
              catch(IOException|IllegalArgumentException e){notice(a,e);}
          })
          .setNegativeButton("Anuluj",null).show();
    }
    public static void showManager(Activity a,TabGroupCoordinator groups){
        List<TabGroupStore.Group> entries=groups.groups();
        String[] names=new String[entries.size()];
        for(int i=0;i<entries.size();i++)names[i]=entries.get(i).getName();
        new AlertDialog.Builder(a).setTitle("Zarządzaj grupami")
          .setItems(names,(dialog,index)->{
              TabGroupStore.Group group=entries.get(index);
              if(TabGroupStore.DEFAULT_ID.equals(group.getId())){
                  Toast.makeText(a,"Grupy Pozostałe nie można usunąć",Toast.LENGTH_SHORT).show();
                  return;
              }
              new AlertDialog.Builder(a).setTitle(group.getName())
                .setItems(new String[]{"Zmień nazwę","Usuń grupę"},(d,action)->{
                    if(action==0){
                        nameDialog(a,"Nazwa grupy",group.getName(),
                            name->groups.renameGroup(group.getId(),name));
                    } else {
                        new AlertDialog.Builder(a).setTitle("Usunąć grupę?")
                          .setMessage("Karty pozostaną otwarte i trafią do Pozostałe.")
                          .setPositiveButton("Usuń",(confirm,w)->{
                              try{groups.deleteGroup(group.getId());}
                              catch(IOException e){notice(a,e);}
                          })
                          .setNegativeButton("Anuluj",null).show();
                    }
                }).show();
          })
          .setNegativeButton("Wróć",(d,w)->showGroupPicker(a,groups)).show();
    }
}