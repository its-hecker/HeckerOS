// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.backup.*;
import android.content.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the temporary Android transport selection and recovers it following process death. */
final class VaultNative implements AutoCloseable {
    interface Status{void text(String text);}
    final Context context;final BackupManager manager;final Status status;
    RestoreSession restore;
    private static volatile String selectingId;
    VaultNative(Context c,Status status)throws Exception{
        context=c;manager=new BackupManager(c);this.status=status;
        synchronized(VaultNative.class){
            recover(c);
            if(VaultStorage.prefs(c).getBoolean("transport_handoff",false))throw new IOException("The previous backup provider handoff is still recovering");
            String previous=manager.getCurrentTransport();
            // Persist before changing Android state. In-process registration callbacks retain ownership after timeout.
            if(!VaultStorage.prefs(c).edit().putBoolean("transport_handoff",true).putString("previous_transport",previous==null?"":previous).commit())
                throw new IOException("Cannot persist backup provider recovery state");
            selectingId=UUID.randomUUID().toString();
        }
        CompletableFuture<String> selected=new CompletableFuture<>();AtomicBoolean abandoned=new AtomicBoolean();
        try{manager.selectBackupTransport(new ComponentName(c,VaultTransportService.class),new SelectBackupTransportCallback(){
            @Override public void onSuccess(String name){selectingId=null;if(abandoned.get())recover(c);selected.complete(name);}
            @Override public void onFailure(int reason){selectingId=null;selected.completeExceptionally(new IOException("HeckerVault transport is unavailable; build the matching ROM"));}});}
        catch(RuntimeException e){selectingId=null;abandoned.set(true);recover(c);throw e;}
        try{if(!VaultTransport.NAME.equals(selected.get(60,TimeUnit.SECONDS)))throw new IOException("Unexpected backup transport");}
        catch(Exception e){abandoned.set(true);recover(c);throw e;}
    }
    static synchronized void recoverIdle(Context c){if(!VaultService.busy)recover(c);}
    static synchronized void recover(Context c){
        if(selectingId!=null)return;
        android.content.SharedPreferences p=VaultStorage.prefs(c);if(!p.getBoolean("transport_handoff",false))return;
        BackupManager bm=new BackupManager(c);String current=bm.getCurrentTransport(),previous=p.getString("previous_transport","");
        if(VaultTransport.NAME.equals(current)&&!previous.isEmpty()&&!previous.equals(current)){
            bm.selectBackupTransport(previous);
            if(VaultTransport.NAME.equals(bm.getCurrentTransport()))return; // Keep recovery state if the old provider is unavailable.
        }
        p.edit().remove("transport_handoff").remove("previous_transport").commit();
    }
    void backup(VaultSession session)throws Exception{
        if(!manager.isBackupEnabled())throw new IOException("Enable Android backup in Settings before including app data");
        CompletableFuture<Integer> finished=new CompletableFuture<>();
        // Non-incremental snapshots do not get @pm@ implicitly. Every standalone archive
        // needs fresh package/signature metadata, including a full-backup-only selection.
        LinkedHashSet<String> targets=new LinkedHashSet<>();targets.add("@pm@");targets.addAll(session.packages);
        int accepted=manager.requestBackup(targets.toArray(new String[0]),new BackupObserver(){
            @Override public void onUpdate(String pkg,BackupProgress progress){status.text("Backing up app data: "+pkg);}
            @Override public void onResult(String pkg,int code){session.results.put(pkg,code);}
            @Override public void backupFinished(int code){finished.complete(code);}},null,BackupManager.FLAG_NON_INCREMENTAL_BACKUP);
        if(accepted!=BackupManager.SUCCESS)throw new IOException("Android could not start app-data backup");
        try{int result=finished.get(6,TimeUnit.HOURS);if(result!=BackupManager.SUCCESS)throw new IOException("Android app-data backup did not complete");}
        catch(Exception e){if(VaultTransport.NAME.equals(manager.getCurrentTransport()))manager.cancelBackups();throw e;}
    }
    void restore(VaultSession session,Collection<String> packages)throws Exception{
        if(packages.isEmpty())return;restore=manager.beginRestoreSession();if(restore==null)throw new IOException("Another Android restore is running; try again later");
        CompletableFuture<RestoreSet[]> sets=new CompletableFuture<>();
        if(restore.getAvailableRestoreSets(new RestoreObserver(){@Override public void restoreSetsAvailable(RestoreSet[] result){sets.complete(result);}})!=0)
            throw new IOException("Cannot inspect native restore metadata");
        boolean found=false;for(RestoreSet set:sets.get(60,TimeUnit.SECONDS))if(set.token==session.token)found=true;
        if(!found)throw new IOException("Restore snapshot is unavailable");CompletableFuture<Integer> done=new CompletableFuture<>();
        int accepted=restore.restorePackages(session.token,new RestoreObserver(){
            @Override public void onUpdate(int position,String pkg){status.text("Restoring app data: "+pkg);}
            @Override public void restoreFinished(int code){done.complete(code);}},new HashSet<>(packages),null);
        if(accepted!=0)throw new IOException("Android could not start the restore");
        if(done.get(6,TimeUnit.HOURS)!=0)throw new IOException("Android reported an incomplete app-data restore");
    }
    @Override public void close(){try{if(restore!=null)restore.endRestoreSession();}finally{recover(context);}}
}
