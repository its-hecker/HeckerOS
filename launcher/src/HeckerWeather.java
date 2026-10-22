// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import io.chaldeaprjkt.seraphixgoogle.SeraphixDataProvider;

/** Google Smartspace weather, with no OmniJaws service or fallback. Main-thread only. */
public final class HeckerWeather {
    private final Context context;
    private final Runnable changed;
    private SeraphixDataProvider provider;
    private String text;
    private Drawable icon;

    public HeckerWeather(Context context, Runnable changed) {
        this.context = context;
        this.changed = changed;
    }

    public void start() {
        if (provider != null) return;
        SeraphixDataProvider candidate = new SeraphixDataProvider(context, 1022,
                context.getSharedPreferences("hecker_weather", 0).getInt("widget_id", -1));
        provider = candidate;
        candidate.setOnDataUpdated(card -> {
            // Ignore callbacks from a provider already stopped during a page/profile change.
            if (provider != candidate) return;
            String next = TextUtils.isEmpty(card.getText()) ? null : card.getText();
            Bitmap bitmap = next == null ? null : card.getImage();
            text = next;
            icon = bitmap == null ? null : new BitmapDrawable(context.getResources(), bitmap);
            changed.run();
        });
        try {
            if (!candidate.bind(id -> context.getSharedPreferences("hecker_weather", 0)
                    .edit().putInt("widget_id", id).apply())) stop();
        } catch (RuntimeException failure) {
            Log.w("HeckerLauncher", "Google weather is unavailable", failure);
            stop();
        }
    }

    public void stop() {
        SeraphixDataProvider previous = provider;
        provider = null;
        if (previous != null) {
            previous.setOnDataUpdated(null);
            try { previous.unbind(); }
            catch (RuntimeException failure) { Log.w("HeckerLauncher", "Weather cleanup failed", failure); }
        }
        boolean hadData = text != null || icon != null;
        text = null;
        icon = null;
        if (hadData) changed.run();
    }

    public boolean available() { return text != null; }
    public String text() { return text; }
    public Drawable icon() { return icon; }
}
