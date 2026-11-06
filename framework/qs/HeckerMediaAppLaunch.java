// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;
import com.android.systemui.plugins.ActivityStarter;

/** Session navigation uses SystemUI's shade, provisioning and keyguard launch path. */
public final class HeckerMediaAppLaunch {
    private final Context context;
    private final ActivityStarter starter;

    public HeckerMediaAppLaunch(Context context, ActivityStarter starter) {
        this.context = context;
        this.starter = starter;
    }

    public boolean open(HeckerQsMedia.LaunchTarget target) {
        if (target == null || target.user < 0 || target.user != ActivityManager.getCurrentUser()) return false;
        try {
            PendingIntent activity = target.sessionActivity;
            if (activity != null && activity.isActivity()
                    && activity.getCreatorUid() / 100000 == target.user) {
                if (target.user != ActivityManager.getCurrentUser()) return false;
                starter.postStartActivityDismissingKeyguard(activity);
                return true;
            }
            // Some players omit sessionActivity. Resolve only their own launcher
            // activity in the same Android user, not a global/default music app.
            Context foreground = context.createContextAsUser(UserHandle.of(target.user), 0);
            Intent launch = foreground.getPackageManager().getLaunchIntentForPackage(target.packageName);
            if (launch == null || target.user != ActivityManager.getCurrentUser()) return false;
            starter.postStartActivityDismissingKeyguard(
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0, null, null, UserHandle.of(target.user));
            return true;
        } catch (RuntimeException unavailable) {
            Log.w("HeckerQsMedia", "Media app unavailable", unavailable);
            return false;
        }
    }
}
