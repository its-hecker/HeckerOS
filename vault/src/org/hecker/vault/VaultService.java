// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.*;
import android.app.Service;
import android.content.*;
import android.os.*;
import java.io.File;
import java.util.concurrent.*;

/** User-started foreground work with explicit progress and cancellation. */
public final class VaultService extends Service {
    static final String CANCEL="org.hecker.vault.CANCEL",CANCEL_SCHEDULED="org.hecker.vault.CANCEL_SCHEDULED";
    private static final String CHANNEL="heckervault";private static final int NOTIFICATION=71;
    static volatile boolean busy;static volatile String progress="";
    private ExecutorService executor;private Future<?> worker;private boolean scheduled;
    private volatile Thread workerThread;private volatile boolean cancelRequested;private volatile long lastNotification;
    static void launch(Context c,VaultRequest request){String id=request.enqueue();
        try{c.startForegroundService(new Intent(c,VaultService.class).putExtra("request",id));}
        catch(RuntimeException e){VaultRequest.pending.remove(id);request.clear();throw e;}}
    @Override public void onCreate(){super.onCreate();executor=Executors.newSingleThreadExecutor();
        NotificationChannel channel=new NotificationChannel(CHANNEL,getString(R.string.vault_title),NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);}
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,VaultActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,2,new Intent(this,VaultService.class).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_vault).setContentTitle(getString(R.string.vault_title))
                .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null,getString(android.R.string.cancel),cancel).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf(startId);return START_NOT_STICKY;}
        String action=intent.getAction();if(CANCEL.equals(action)||CANCEL_SCHEDULED.equals(action)){
            if(busy&&(!CANCEL_SCHEDULED.equals(action)||scheduled)){VaultSession session=VaultSession.current;if(session!=null)session.cancelled=true;
                cancelRequested=true;Thread running=workerThread;if(running!=null)running.interrupt();cancelNative();}
            if(!busy)stopSelf(startId);return START_NOT_STICKY;}
        VaultRequest request=VaultRequest.pending.remove(intent.getStringExtra("request"));
        if(request==null){if(!busy)stopSelf(startId);return START_NOT_STICKY;}
        if(busy){request.clear();if(request.scheduled)VaultJobService.finished();return START_NOT_STICKY;}
        busy=true;cancelRequested=false;scheduled=request.scheduled;progress=getString(R.string.vault_preparing);startForeground(NOTIFICATION,notification(progress));
        worker=executor.submit(()->{workerThread=Thread.currentThread();String report;boolean failed=false;
            try{if(cancelRequested)throw new InterruptedException();VaultNative.recover(this);VaultSystemData.recoverSmsWrite(this);cleanOldStaging();
                report=new VaultEngine(this,this::update).run(request);
            }catch(Exception e){failed=true;report=Thread.currentThread().isInterrupted()?"Operation cancelled. Review the result before retrying.":
                    "Operation failed: "+safeError(e)+". Review permissions, folder access and free storage before retrying.";
            }finally{request.clear();workerThread=null;}
            VaultStorage.prefs(this).edit().putString("report",report).putBoolean("last_failed",failed).putLong("last_finished",System.currentTimeMillis()).commit();
            new Handler(getMainLooper()).post(()->{busy=false;progress="";stopForeground(STOP_FOREGROUND_REMOVE);
                if(scheduled)VaultJobService.finished();stopSelf();});
        });return START_NOT_STICKY;
    }
    private static String safeError(Exception e){String message=e.getMessage();
        if(e instanceof java.io.IOException||e instanceof java.security.GeneralSecurityException){
            // All owned I/O error strings are fixed descriptions. Platform/provider details remain private.
            if(message!=null&&e.getStackTrace().length>0&&e.getStackTrace()[0].getClassName().startsWith("org.hecker.vault."))return message;
        }
        return e.getClass().getSimpleName();
    }
    private void cleanOldStaging(){File parent=new File(getNoBackupFilesDir(),"staging");File[] children=parent.listFiles();if(children==null)return;
        for(File f:children){VaultEngine.Prepared p=VaultEngine.prepared;if(p==null||!f.equals(p.root))VaultEngine.cleanup(f);}}
    private void update(String value){progress=value;long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastNotification>=700){lastNotification=now;getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(value));}}
    private void cancelNative(){android.app.backup.BackupManager manager=new android.app.backup.BackupManager(this);
        if(VaultStorage.prefs(this).getBoolean("transport_handoff",false)&&VaultTransport.NAME.equals(manager.getCurrentTransport()))manager.cancelBackups();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){Thread running=workerThread;if(running!=null)running.interrupt();if(executor!=null)executor.shutdownNow();super.onDestroy();}
}
