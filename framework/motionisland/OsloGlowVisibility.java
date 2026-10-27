// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Observe the reviewed Oslo shader's real surface/animation lifecycle without changing its input. */
final class OsloGlowVisibility implements AutoCloseable {
    private final View feedback, shader;
    private final Object glow, original, wrapper;
    private final Method active, animating, setter;
    private final Field callback;
    private final Runnable changed;
    private final ViewTreeObserver tree;
    private boolean closed;
    private final ViewTreeObserver.OnPreDrawListener preDraw;

    static OsloGlowVisibility bind(View view, Runnable changed) {
        if (!"com.google.oslo.ui.glow.GlowFeedbackView".equals(view.getClass().getName())) return null;
        try { return new OsloGlowVisibility(view, changed); }
        catch (ReflectiveOperationException | RuntimeException unavailable) {
            Log.w("HeckerMotionIsland", "Pinned glow interface unavailable", unavailable);
            return null;
        }
    }

    private OsloGlowVisibility(View feedback, Runnable changed) throws ReflectiveOperationException {
        this.feedback = feedback; this.changed = changed;
        glow = feedback.getClass().getField("mGlow").get(feedback);
        active = glow.getClass().getMethod("isActive");
        animating = glow.getClass().getMethod("isAnimating");
        Field surface = field(feedback.getClass(), "mShaderView");
        surface.setAccessible(true);
        shader = (View) surface.get(feedback);
        surface.setAccessible(false);
        callback = field(feedback.getClass(), "mCallback");
        callback.setAccessible(true);
        original = callback.get(feedback);
        callback.setAccessible(false);
        if (original == null) throw new IllegalStateException("Oslo callback not installed");
        Class<?> type = callback.getType();
        setter = feedback.getClass().getMethod("setCallback", type);
        wrapper = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    Object result;
                    try { result = method.invoke(original, args); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                    if ("onAnimationStart".equals(method.getName())
                            || "onAnimationEnd".equals(method.getName())) {
                        // Oslo updates its state/visibility immediately after these callbacks.
                        feedback.post(() -> { if (!closed) changed.run(); });
                    }
                    return result;
                });
        setter.invoke(feedback, wrapper);
        tree = feedback.getViewTreeObserver();
        preDraw = () -> { if (!closed) changed.run(); return true; };
        tree.addOnPreDrawListener(preDraw);
    }

    boolean belongsTo(View view) { return feedback == view; }
    boolean visible() {
        if (closed || !feedback.isAttachedToWindow() || !feedback.isShown() || !shader.isShown())
            return false;
        try { return (Boolean) active.invoke(glow) || (Boolean) animating.invoke(glow); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (tree.isAlive()) tree.removeOnPreDrawListener(preDraw);
        try {
            callback.setAccessible(true);
            boolean ours = callback.get(feedback) == wrapper;
            callback.setAccessible(false);
            if (ours) setter.invoke(feedback, original);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Log.w("HeckerMotionIsland", "Glow observer cleanup unavailable", unavailable);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        while (type != null && type.getName().startsWith("com.google.oslo.ui.")) {
            try { return type.getDeclaredField(name); }
            catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
