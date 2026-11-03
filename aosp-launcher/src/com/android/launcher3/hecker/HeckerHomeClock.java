// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.FrameLayout;

/** Launcher-owned first-page header, outside the model's movable app/widget cells. */
public final class HeckerHomeClock extends FrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    public HeckerHomeClock(Context context) {
        super(context);
        HeckerClockView clock = new HeckerClockView(context, -1);
        addView(clock, new LayoutParams(-1, -1));
        clock.setOnClickListener(view -> clock.cycleMessage());
        clock.setOnLongClickListener(view -> { HeckerStudio.open(context, "clock"); return true; });
    }
    /** Keep native icon/label cells usable, including large grids and large accessibility fonts. */
    public int reservedHeight(int available, int rows, int rowGap) {
        if (!HeckerStudio.enabled(getContext(), "clock_home", true)) return 0;
        float font = getResources().getConfiguration().fontScale;
        int icon = HeckerStudio.dp(getContext(), 48 * HeckerStudio.number(getContext(), "icon_size", 100, 65, 125) / 100f);
        int label = HeckerStudio.enabled(getContext(), "home_labels", true)
                ? HeckerStudio.dp(getContext(), HeckerStudio.number(getContext(), "home_label_size", 13, 9, 20) * font + 8) : 0;
        int remaining = available - Math.max(1, rows) * (icon + label + HeckerStudio.dp(getContext(), 8))
                - Math.max(0, rows - 1) * rowGap;
        return remaining < HeckerStudio.dp(getContext(), 112) ? 0
                : Math.min(remaining, HeckerStudio.dp(getContext(),
                        HeckerStudio.number(getContext(), "clock_height", 184, 112, 320)
                        + HeckerStudio.number(getContext(), "clock_offset", 0, 0, 120)));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int room = Math.max(0, android.view.View.MeasureSpec.getSize(heightSpec) - HeckerStudio.dp(getContext(), 112));
        int top = Math.min(room, HeckerStudio.dp(getContext(), HeckerStudio.number(getContext(), "clock_offset", 0, 0, 120)));
        if (getPaddingTop() != top) setPadding(0, top, 0, 0);
        super.onMeasure(widthSpec, heightSpec);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); HeckerStudio.prefs(getContext()).registerOnSharedPreferenceChangeListener(this);
    }
    @Override protected void onDetachedFromWindow() {
        HeckerStudio.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(this); super.onDetachedFromWindow();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key != null && key.startsWith(HeckerStudio.PREFIX)) {
            requestLayout(); if (getParent() instanceof android.view.View) ((android.view.View) getParent()).requestLayout();
        }
    }
}
