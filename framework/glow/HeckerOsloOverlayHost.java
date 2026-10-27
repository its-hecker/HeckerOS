// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.android.systemui.plugins.OverlayPlugin;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

/** Oslo owns one attached, non-interactive surface for its entire plugin lifetime. */
public final class HeckerOsloOverlayHost {
    private static final int GLOW_WINDOW_TYPE = 2041;
    private static final Map<OverlayPlugin, Host> HOSTS = new IdentityHashMap<>();
    private static int displayState = Display.STATE_UNKNOWN;
    // Compatibility for previously prepared DozeTriggers adapters. Motion Sense
    // feedback no longer asks for ambient pulses or AOD refreshes.
    public static void attachDoze(IntSupplier requester) { }
    public static void detachDoze() {
        displayState = Display.STATE_UNKNOWN;
        for (Host host : HOSTS.values()) host.handler.post(host::refreshFrame);
    }

    public static View rootFor(OverlayPlugin plugin, Context pluginContext, View root,
            KeyguardStateController keyguard, StatusBarStateController status) {
        if (!"com.google.oslo".equals(pluginContext.getPackageName())
                || !"com.google.oslo.OsloOverlay".equals(plugin.getClass().getName())
                || !(root instanceof ViewGroup)) return root;
        disconnect(plugin);
        try {
            Host host = new Host(root.getContext(), keyguard, status);
            HOSTS.put(plugin, host);
            return host.container;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Log.w("HeckerOsloGlow", "Gesture surface unavailable; retaining native host", unavailable);
            return root;
        }
    }

    /** GlowFeedbackView reads its ViewRootImpl during setup and setVisibility. */
    public static void whenAttached(OverlayPlugin plugin, View root, Runnable setup) {
        Host host = HOSTS.get(plugin);
        Runnable ready = () -> {
            if (root.isAttachedToWindow() && (host == null || !host.destroyed)) setup.run();
        };
        if (root.isAttachedToWindow()) { ready.run(); return; }
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                if (host != null) host.pending = null;
                ready.run();
            }
            public void onViewDetachedFromWindow(View view) { }
        };
        if (host != null) host.pending = listener;
        root.addOnAttachStateChangeListener(listener);
    }

    public static OverlayPlugin.Callback callbackFor(OverlayPlugin plugin, OverlayPlugin.Callback callback) {
        Host host = HOSTS.get(plugin);
        if (host == null) return callback;
        host.nativeCallback = callback;
        // Never detach/reparent from the animation-start callback. The following
        // native setVisibility immediately registers luma sampling on this root.
        return () -> { if (!host.destroyed) callback.onHoldStatusBarOpenChange(); };
    }

    public static boolean holdsNativeShade(OverlayPlugin plugin) {
        Host host = HOSTS.get(plugin);
        return plugin.holdStatusBarOpen() && (host == null
                || host.keyguard.isShowing() && !host.ambient());
    }

    /** Hide feedback on ambient/off; refresh it again when the normal display returns. */
    public static void onDozeScreenState(int state) {
        displayState = state;
        for (Host host : HOSTS.values()) host.handler.post(host::refreshFrame);
    }

    public static void dozeTimeTick() { }

    public static void disconnect(OverlayPlugin plugin) {
        Host host = HOSTS.remove(plugin);
        if (host != null) host.destroy();
    }

    private static final class Host {
        final FrameLayout container;
        final WindowManager windows;
        final KeyguardStateController keyguard;
        final StatusBarStateController status;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Set<View> seeded = Collections.newSetFromMap(new IdentityHashMap<>());
        final KeyguardStateController.Callback keyguardCallback = new KeyguardStateController.Callback() {
            @Override public void onKeyguardShowingChanged() {
                // Keep the original lockscreen/AOD hold, without holding the unlocked shade open.
                handler.post(() -> {
                    if (!destroyed && nativeCallback != null) nativeCallback.onHoldStatusBarOpenChange();
                    refreshFrame();
                });
            }
        };
        final StatusBarStateController.StateListener dozeCallback = new StatusBarStateController.StateListener() {
            @Override public void onDozingChanged(boolean dozing) { handler.post(Host.this::refreshFrame); }
            @Override public void onPulsingChanged(boolean pulsing) { handler.post(Host.this::refreshFrame); }
        };
        OverlayPlugin.Callback nativeCallback;
        View.OnAttachStateChangeListener pending;
        volatile boolean destroyed;
        boolean wasAmbient;
        HeckerMotionIsland companion;

        Host(Context context, KeyguardStateController keyguard, StatusBarStateController status)
                throws ReflectiveOperationException {
            this.keyguard = keyguard;
            this.status = status;
            Context overlayContext = context.createWindowContext(GLOW_WINDOW_TYPE, null);
            windows = overlayContext.getSystemService(WindowManager.class);
            if (windows == null) throw new IllegalStateException("No window manager");
            container = new FrameLayout(overlayContext) {
                @Override public void onViewAdded(View child) {
                    super.onViewAdded(child);
                    // Oslo registers on attach, but the native controller does not replay current state.
                    handler.post(() -> seedState(child));
                }
                @Override public void onViewRemoved(View child) {
                    seeded.remove(child);
                    if (companion != null) companion.feedbackRemoved(child);
                    super.onViewRemoved(child);
                }
            };
            container.setClipChildren(false);
            container.setClipToPadding(false);
            // The plugin inserts its own shader/feedback children at index one.
            container.addView(new View(overlayContext));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                    GLOW_WINDOW_TYPE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP;
            params.setFitInsetsTypes(0);
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setTitle("HeckerOsloGestureFeedback");
            WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(params);
            windows.addView(container, params);
            companion = HeckerMotionIsland.create(overlayContext);
            keyguard.addCallback(keyguardCallback);
            status.addCallback(dozeCallback);
            refreshFrame();
        }

        void seedState(View child) {
            if (destroyed || !child.isAttachedToWindow() || seeded.contains(child)
                    || !child.getClass().getName().startsWith("com.google.oslo.ui.")
                    || !(child instanceof StatusBarStateController.StateListener)) return;
            seeded.add(child);
            StatusBarStateController.StateListener listener = (StatusBarStateController.StateListener) child;
            // Pulsing first prevents a late-attached view from briefly entering the wrong AOD mode.
            listener.onPulsingChanged(status.isPulsing());
            listener.onDozingChanged(status.isDozing());
            if (companion != null) companion.feedbackAdded(child);
            refreshFrame();
        }

        boolean ambient() {
            return status.isDozing() || displayState == Display.STATE_OFF
                    || displayState == Display.STATE_DOZE || displayState == Display.STATE_DOZE_SUSPEND;
        }
        void refreshFrame() {
            if (destroyed) return;
            boolean ambient = ambient();
            container.setVisibility(ambient ? View.INVISIBLE : View.VISIBLE);
            if (companion != null) companion.setAmbient(ambient);
            if (wasAmbient != ambient) {
                wasAmbient = ambient;
                if (nativeCallback != null) nativeCallback.onHoldStatusBarOpenChange();
            }
            if (ambient || !container.isAttachedToWindow()) return;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof GLSurfaceView && child.getVisibility() == View.VISIBLE) {
                    ((GLSurfaceView) child).requestRender();
                }
            }
        }

        void destroy() {
            destroyed = true;
            if (companion != null) companion.close();
            companion = null;
            keyguard.removeCallback(keyguardCallback);
            status.removeCallback(dozeCallback);
            seeded.clear(); nativeCallback = null;
            if (pending != null) container.removeOnAttachStateChangeListener(pending);
            pending = null;
            // Drain already queued Oslo animation messages before releasing its root.
            handler.post(() -> {
                try { windows.removeViewImmediate(container); }
                catch (IllegalArgumentException alreadyRemoved) { }
            });
        }
    }
}
