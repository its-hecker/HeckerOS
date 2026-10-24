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

/** Keeps Oslo's actual glow visible when the scene shade root is hidden on HOME/apps. */
public final class HeckerOsloOverlayHost {
    // @hide WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL in Android 17.
    // Its value is checked against the pinned framework by check_native_ports.
    private static final int GLOW_WINDOW_TYPE = 2041;
    private static final Map<OverlayPlugin, Host> HOSTS = new IdentityHashMap<>();

    public static View rootFor(OverlayPlugin plugin, Context pluginContext, View root,
            KeyguardStateController keyguard) {
        if (!"com.google.oslo".equals(pluginContext.getPackageName())
                || !"com.google.oslo.OsloOverlay".equals(plugin.getClass().getName())
                || !(root instanceof ViewGroup)) return root;
        disconnect(plugin);
        try {
            Host host = new Host(plugin, (ViewGroup) root, keyguard);
            HOSTS.put(plugin, host);
            return host.container;
        } catch (RuntimeException unavailable) {
            Log.w("HeckerOsloGlow", "Gesture host unavailable; retaining native host", unavailable);
            return root;
        }
    }

    public static OverlayPlugin.Callback callbackFor(OverlayPlugin plugin, OverlayPlugin.Callback callback) {
        Host host = HOSTS.get(plugin);
        if (host == null) return callback;
        return () -> {
            if (host.destroyed) return;
            host.refresh();
            // Preserve native collapse, icon restoration and input-minimizer behavior.
            callback.onHoldStatusBarOpenChange();
        };
    }

    public static void disconnect(OverlayPlugin plugin) {
        Host host = HOSTS.remove(plugin);
        if (host != null) host.destroy();
    }

    private static final class Host {
        final OverlayPlugin plugin;
        final ViewGroup original;
        final FrameLayout container;
        final WindowManager windows;
        final KeyguardStateController keyguard;
        final Handler handler = new Handler(Looper.getMainLooper());
        boolean detached;
        volatile boolean destroyed;
        final KeyguardStateController.Callback callback = new KeyguardStateController.Callback() {
            @Override public void onKeyguardShowingChanged() { refresh(); }
        };

        Host(OverlayPlugin plugin, ViewGroup original, KeyguardStateController keyguard) {
            this.plugin = plugin;
            this.original = original;
            this.keyguard = keyguard;
            Context context = original.getContext();
            Context overlayContext = context.createWindowContext(GLOW_WINDOW_TYPE, null);
            windows = overlayContext.getSystemService(WindowManager.class);
            container = new FrameLayout(overlayContext);
            container.setClipChildren(false);
            container.setClipToPadding(false);
            // Oslo's addViews inserts its shader and feedback at index one.
            container.addView(new View(context));
            original.addView(container, new ViewGroup.LayoutParams(-1, -1));
            keyguard.addCallback(callback);
        }

        void refresh() {
            if (Looper.myLooper() != Looper.getMainLooper()) { handler.post(this::refresh); return; }
            if (destroyed) return;
            boolean separate = !keyguard.isShowing() && plugin.holdStatusBarOpen();
            if (separate == detached || windows == null) return;
            if (separate) {
                original.removeView(container);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                        GLOW_WINDOW_TYPE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP;
                params.setFitInsetsTypes(0);
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                params.setTitle("HeckerOsloGestureFeedback");
                try {
                    // This trusted SystemUI surface must not block Android's
                    // obscured-touch protection for applications beneath it.
                    WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(params);
                    windows.addView(container, params); detached = true;
                }
                catch (ReflectiveOperationException | RuntimeException unavailable) {
                    Log.w("HeckerOsloGlow", "Standalone gesture surface unavailable", unavailable);
                    original.addView(container, new ViewGroup.LayoutParams(-1, -1));
                }
            } else {
                removeWindow();
                original.addView(container, new ViewGroup.LayoutParams(-1, -1));
            }
        }

        void removeWindow() {
            if (!detached) return;
            try { windows.removeViewImmediate(container); }
            catch (IllegalArgumentException alreadyRemoved) { }
            detached = false;
        }

        void destroy() {
            destroyed = true;
            handler.removeCallbacksAndMessages(null);
            keyguard.removeCallback(callback);
            removeWindow();
            if (container.getParent() instanceof ViewGroup) ((ViewGroup) container.getParent()).removeView(container);
        }
    }
}
