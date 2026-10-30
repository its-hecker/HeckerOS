// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;

import java.util.WeakHashMap;

/** Observes the actual collapsed bar, including short taps not forwarded to the shade. */
public final class HeckerStatusBarSleepGesture {
    // Only called by View dispatch on the main thread. Values never retain their View keys.
    private static final WeakHashMap<View, Tracker> TRACKERS = new WeakHashMap<>();

    private static final class Tracker {
        final DoubleTapSleepGesture taps;
        int user;

        Tracker(Context context, int user) {
            ViewConfiguration config = ViewConfiguration.get(context);
            taps = new DoubleTapSleepGesture(ViewConfiguration.getDoubleTapMinTime(),
                    ViewConfiguration.getDoubleTapTimeout(), config.getScaledTouchSlop(),
                    config.getScaledDoubleTapSlop());
            this.user = user;
        }
    }

    public static void reset(View view) {
        TRACKERS.remove(view);
    }

    public static boolean onTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || event.getPointerCount() != 1
                || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                || event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) {
            reset(view);
            return false;
        }
        Context context = view.getContext();
        Tracker tracker = TRACKERS.get(view);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
            int user = ActivityManager.getCurrentUser();
            PowerManager power = context.getSystemService(PowerManager.class);
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            AccessibilityManager accessibility = context.getSystemService(AccessibilityManager.class);
            // Awake lockscreen touches stay with its native handler and falsing/bouncer policy.
            if (!HeckerDoubleTapSleep.isEnabled(context, user) || power == null
                    || !power.isInteractive() || keyguard == null || keyguard.isKeyguardLocked()
                    || accessibility == null || accessibility.isTouchExplorationEnabled()) {
                reset(view);
                return false;
            }
            if (tracker != null && tracker.user != user) {
                reset(view);
                tracker = null;
            }
            if (tracker == null) {
                if (action != MotionEvent.ACTION_DOWN) return false;
                tracker = new Tracker(context, user);
                TRACKERS.put(view, tracker);
            }
            if (action == MotionEvent.ACTION_UP
                    && tracker.taps.up(event.getEventTime(), event.getRawX(), event.getRawY())) {
                reset(view);
                power.goToSleep(event.getEventTime());
                return true;
            }
        }
        if (tracker != null) {
            if (action == MotionEvent.ACTION_DOWN) {
                tracker.taps.down(event.getEventTime(), event.getRawX(), event.getRawY());
            } else if (action == MotionEvent.ACTION_MOVE) {
                tracker.taps.move(event.getRawX(), event.getRawY());
            } else if (action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_OUTSIDE) {
                reset(view);
            }
        }
        return false;
    }
}
