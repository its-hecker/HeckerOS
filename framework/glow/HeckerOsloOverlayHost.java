// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
    private static IntSupplier pulseRequester;

    /** Native DozeTriggers returns 0=suppressed, 1=accepted, 2=transient transition. */
    public static void attachDoze(IntSupplier requester) { pulseRequester = requester; }
    public static void detachDoze() { pulseRequester = null; }

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
        return plugin.holdStatusBarOpen() && (host == null || host.keyguard.isShowing());
    }

    /** Redraw the existing GL surface after a pulse resumes native display refresh. */
    public static void onDozeScreenState(int state) {
        displayState = state;
        if (state == Display.STATE_ON || state == Display.STATE_DOZE) {
            for (Host host : HOSTS.values()) host.handler.post(host::refreshFrame);
        }
    }

    /** Follow the native AOD minute/burn-in tick without adding an animation timer. */
    public static void dozeTimeTick() {
        for (Host host : HOSTS.values()) host.handler.post(host::refreshFrame);
    }

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
        final Map<View, Runnable> pulseBindings = new IdentityHashMap<>();
        long lastPulse = -2000;
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
                    Runnable unbind = pulseBindings.remove(child);
                    if (unbind != null) unbind.run();
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
            keyguard.addCallback(keyguardCallback);
            status.addCallback(dozeCallback);
        }

        void seedState(View child) {
            if (destroyed || !child.isAttachedToWindow() || seeded.contains(child)
                    || !child.getClass().getName().startsWith("com.google.oslo.ui.")
                    || !(child instanceof StatusBarStateController.StateListener)) return;
            seeded.add(child);
            pulseBindings.put(child, OsloFeedbackPulse.bind(child, this::requestGesturePulse));
            StatusBarStateController.StateListener listener = (StatusBarStateController.StateListener) child;
            // Pulsing first prevents a late-attached view from briefly entering the wrong AOD mode.
            listener.onPulsingChanged(status.isPulsing());
            listener.onDozingChanged(status.isDozing());
            refreshFrame();
        }

        void requestGesturePulse() {
            long now = SystemClock.elapsedRealtime();
            IntSupplier requester = pulseRequester;
            if (destroyed || requester == null || !keyguard.isShowing() || !status.isDozing()
                    || status.isPulsing() || now - lastPulse < 2000) return;
            lastPulse = now;
            tryGesturePulse(requester, 0);
        }

        void tryGesturePulse(IntSupplier requester, int attempt) {
            if (destroyed || pulseRequester != requester || !keyguard.isShowing()
                    || !status.isDozing() || status.isPulsing()) return;
            try {
                if (requester.getAsInt() == 2 && attempt < 4) {
                    handler.postDelayed(() -> tryGesturePulse(requester, attempt + 1), 200);
                }
            } catch (RuntimeException unavailable) {
                Log.w("HeckerOsloGlow", "Native gesture pulse unavailable", unavailable);
            }
        }

        void refreshFrame() {
            if (destroyed || !container.isAttachedToWindow() || displayState == Display.STATE_OFF) return;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof GLSurfaceView && child.getVisibility() == View.VISIBLE) {
                    ((GLSurfaceView) child).requestRender();
                }
            }
        }

        void destroy() {
            destroyed = true;
            keyguard.removeCallback(keyguardCallback);
            status.removeCallback(dozeCallback);
            for (Runnable unbind : pulseBindings.values()) unbind.run();
            pulseBindings.clear();
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
