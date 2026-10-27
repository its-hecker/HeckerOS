// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Bridge the pinned Oslo APK's real gesture callbacks without synthesizing sensor input. */
final class OsloFeedbackPulse {
    // OsloFeedback.apk SHA-256: 5f6c2e0c908e4d726eb62543c8d7f8492ade99b1d9043baa415873093598ac54.
    static Runnable bind(View feedback, Runnable gesture) {
        if (!"com.google.oslo.ui.glow.GlowFeedbackView".equals(feedback.getClass().getName())) return () -> {};
        try {
            Field callback = field(feedback.getClass(), "mCallback");
            callback.setAccessible(true);
            Object original = callback.get(feedback);
            callback.setAccessible(false);
            if (original == null) return () -> {};
            Field animation = field(feedback.getClass(), "mCurrAnim");
            animation.setAccessible(true);
            Class<?> type = callback.getType();
            Method setter = feedback.getClass().getMethod("setCallback", type);
            boolean[] active = { true };
            Object wrapper = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> {
                        Object result;
                        try { result = method.invoke(original, args); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                        if ("onAnimationStart".equals(method.getName())) {
                            // The handler sets mCurrAnim immediately after its start callback.
                            feedback.post(() -> {
                                if (!active[0] || !feedback.isAttachedToWindow()
                                        || feedback.getVisibility() != View.VISIBLE) return;
                                try {
                                    int kind = animation.getInt(feedback);
                                    // 0=idle, 7=disengage, 14=doze fade. Never repulse on an AOD fade.
                                    if (kind >= 2 && kind <= 13 && kind != 7) gesture.run();
                                } catch (IllegalAccessException unavailable) {
                                    Log.w("HeckerOsloGlow", "Gesture state unavailable", unavailable);
                                }
                            });
                        }
                        return result;
                    });
            setter.invoke(feedback, wrapper);
            return () -> {
                active[0] = false;
                try { setter.invoke(feedback, original); }
                catch (ReflectiveOperationException unavailable) {
                    Log.w("HeckerOsloGlow", "Could not restore gesture callback", unavailable);
                }
            };
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Log.w("HeckerOsloGlow", "Gesture pulse bridge unavailable; keeping plugin callbacks", unavailable);
            return () -> {};
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        while (type != null && type.getName().startsWith("com.google.oslo.ui.")) {
            try { return type.getDeclaredField(name); }
            catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private OsloFeedbackPulse() {}
}
