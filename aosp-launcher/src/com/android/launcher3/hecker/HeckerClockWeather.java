// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.*;

/** One visible-clock subscription to the retained Google weather adapter. Optional and offline-safe. */
public final class HeckerClockWeather {
    private static final WeakHashMap<View, Boolean> VIEWS = new WeakHashMap<>();
    private static Object provider;
    private static String text;
    private static Drawable icon;
    public static void start(View view) {
        VIEWS.put(view, true); if (provider != null) return;
        try {
            Class<?> type = Class.forName("com.android.launcher3.hecker.HeckerWeather");
            Runnable changed = () -> {
                try {
                    text = provider == null ? null : (String) type.getMethod("text").invoke(provider);
                    icon = provider == null ? null : (Drawable) type.getMethod("icon").invoke(provider);
                } catch (ReflectiveOperationException unavailable) { text = null; icon = null; }
                for (View v : new ArrayList<>(VIEWS.keySet())) if (v != null) v.invalidate();
            };
            provider = type.getConstructor(Context.class, Runnable.class).newInstance(view.getContext().getApplicationContext(), changed);
            type.getMethod("start").invoke(provider);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { provider = null; text = null; icon = null; }
    }
    public static void stop(View view) {
        VIEWS.remove(view); if (!VIEWS.isEmpty() || provider == null) return;
        Object old = provider; provider = null; text = null; icon = null;
        try { old.getClass().getMethod("stop").invoke(old); } catch (ReflectiveOperationException ignored) { }
    }
    public static String text() { return text; }
    public static Drawable icon() { return icon; }
    private HeckerClockWeather() {}
}
