// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.android.systemui.plugins.OverlayPlugin;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import java.util.IdentityHashMap;
import java.util.Map;

/** Oslo owns one attached, non-interactive surface for its entire plugin lifetime. */
public final class HeckerOsloOverlayHost {
    private static final int GLOW_WINDOW_TYPE = 2041;
    private static final Map<OverlayPlugin, Host> HOSTS = new IdentityHashMap<>();

    public static View rootFor(OverlayPlugin plugin, Context pluginContext, View root,
            KeyguardStateController keyguard) {
        if (!"com.google.oslo".equals(pluginContext.getPackageName())
                || !"com.google.oslo.OsloOverlay".equals(plugin.getClass().getName())
                || !(root instanceof ViewGroup)) return root;
        disconnect(plugin);
        try {
            Host host = new Host(root.getContext());
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
        // Never detach/reparent from the animation-start callback. The following
        // native setVisibility immediately registers luma sampling on this root.
        return () -> { if (!host.destroyed) callback.onHoldStatusBarOpenChange(); };
    }

    public static boolean holdsNativeShade(OverlayPlugin plugin) {
        return !HOSTS.containsKey(plugin) && plugin.holdStatusBarOpen();
    }

    public static void disconnect(OverlayPlugin plugin) {
        Host host = HOSTS.remove(plugin);
        if (host != null) host.destroy();
    }

    private static final class Host {
        final FrameLayout container;
        final WindowManager windows;
        final Handler handler = new Handler(Looper.getMainLooper());
        View.OnAttachStateChangeListener pending;
        volatile boolean destroyed;

        Host(Context context) throws ReflectiveOperationException {
            Context overlayContext = context.createWindowContext(GLOW_WINDOW_TYPE, null);
            windows = overlayContext.getSystemService(WindowManager.class);
            if (windows == null) throw new IllegalStateException("No window manager");
            container = new FrameLayout(overlayContext);
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
        }

        void destroy() {
            destroyed = true;
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
