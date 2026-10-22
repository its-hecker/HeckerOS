// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

/** Native Android 17 implementations of the Infinity-X launcher integration points. */
public final class HeckerPlatform {
    private HeckerPlatform() {}

    public static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    public static boolean hasNavbarByDefault(Context context) {
        int id = context.getResources().getIdentifier("config_showNavigationBar", "bool", "android");
        return id == 0 || context.getResources().getBoolean(id);
    }

    public static void sleep(Context context) {
        // Uses the normal system sleep path; Face Unlock can still unlock after this gesture.
        context.getSystemService(PowerManager.class).goToSleep(SystemClock.uptimeMillis(),
                PowerManager.GO_TO_SLEEP_REASON_APPLICATION, 0);
    }

    public static void longPressKey(Context context, int keyCode, int displayId) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD);
        down.setDisplayId(displayId);
        InputManager input = context.getSystemService(InputManager.class);
        input.injectInputEvent(down, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        KeyEvent repeat = KeyEvent.changeTimeRepeat(down, now, 1,
                down.getFlags() | KeyEvent.FLAG_LONG_PRESS);
        input.injectInputEvent(repeat, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        input.injectInputEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public static void openFreeform(Context context, ComponentName component, int userId, int taskId) {
        if (component == null || userId != UserHandle.myUserId()) return;
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
            if (!ActivityInfo.isResizeableMode(info.resizeMode)) return;
            Rect area = context.getSystemService(android.view.WindowManager.class)
                    .getCurrentWindowMetrics().getBounds();
            int width = Math.round(area.width() * 0.72f), height = Math.round(area.height() * 0.62f);
            int x = area.left + (area.width() - width) / 2;
            int y = area.top + (area.height() - height) / 2;
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchWindowingMode(5);
            options.setLaunchBounds(new Rect(x, y, x + width, y + height));
            if (taskId >= 0) {
                ActivityTaskManager.getService().startActivityFromRecents(taskId, options.toBundle());
            } else {
                context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle());
            }
        } catch (Exception failure) {
            Log.w("HeckerLauncher", "Unable to open resizeable app in a popup", failure);
        }
    }
}
