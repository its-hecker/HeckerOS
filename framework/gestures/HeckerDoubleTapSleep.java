// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.content.Context;
import android.provider.Settings;

/** The Settings > Display switch is the single source of truth, for the current user. */
public final class HeckerDoubleTapSleep {
    public static final String SETTING = "double_tap_sleep_gesture";

    private HeckerDoubleTapSleep() {}

    public static boolean isEnabled(Context context) {
        return isEnabled(context, ActivityManager.getCurrentUser());
    }

    public static boolean isEnabled(Context context, int user) {
        return Settings.System.getIntForUser(context.getContentResolver(), SETTING, 0, user) != 0;
    }
}
