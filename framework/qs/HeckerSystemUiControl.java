// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

/** Trusted settings actions; this class does not intercept status-bar touches. */
public final class HeckerSystemUiControl {
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
}
