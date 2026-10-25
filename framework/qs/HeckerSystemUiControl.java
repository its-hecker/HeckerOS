// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IHeckerMemoryService;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.widget.Toast;
import com.android.systemui.res.R;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Trusted settings actions; this class does not intercept status-bar touches. */
public final class HeckerSystemUiControl {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean started;
    public static synchronized void start(Context context) {
        if (started) return;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if ("org.hecker.suite.RESTART_SYSTEMUI".equals(i.getAction())) {
                    MAIN.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 160);
                }
            }
        }, new IntentFilter("org.hecker.suite.RESTART_SYSTEMUI"),
                "android.permission.WRITE_SECURE_SETTINGS", MAIN, Context.RECEIVER_EXPORTED);
        started = true;
    }
    public static void boost(Context context) {
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        final int user = ActivityManager.getCurrentUser();
        WORKER.execute(() -> {
            Bundle result = null;
            try {
                IHeckerMemoryService service = IHeckerMemoryService.Stub.asInterface(ServiceManager.getService("hecker_memory"));
                if (service != null) result = service.boost();
            } catch (RemoteException | RuntimeException ignored) { }
            final Bundle outcome = result;
            MAIN.post(() -> {
                if (user != ActivityManager.getCurrentUser()
                        || context.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
                String text = outcome == null || !outcome.getBoolean("available")
                        ? context.getString(R.string.hecker_memory_failed)
                        : outcome.getInt("processes") == 0 ? context.getString(R.string.hecker_memory_none)
                        : context.getString(R.string.hecker_memory_done, outcome.getInt("apps"), outcome.getInt("processes"));
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            });
        });
    }
}
