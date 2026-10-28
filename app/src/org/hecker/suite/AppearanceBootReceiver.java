// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded wallpaper preparation runs outside the broadcast/UI thread, once for each fresh user. */
public final class AppearanceBootReceiver extends BroadcastReceiver {
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_WALLPAPER_CHANGED.equals(action)) return;
        PendingResult pending = goAsync();
        WORKER.execute(() -> {
            try {
                if (Intent.ACTION_WALLPAPER_CHANGED.equals(action)) DefaultAppearance.reconcile(context);
                else DefaultAppearance.apply(context, false);
            } catch (Exception | OutOfMemoryError failure) {
                android.util.Log.w("HeckerAppearance", "Default appearance unavailable; native wallpaper retained", failure);
            } finally { pending.finish(); }
        });
    }
}
