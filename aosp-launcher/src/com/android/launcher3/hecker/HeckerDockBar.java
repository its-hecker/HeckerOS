// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Launcher-owned search below the dock, independent of Google widgets and Quickstep. */
public final class HeckerDockBar extends LinearLayout
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private int requestedVisibility = VISIBLE;
    private final GradientDrawable colourWash;
    public static boolean available(Context context) {
        return HeckerStudio.res(context, "string", "hs_dock_search") != 0;
    }
    public static View create(ViewGroup container) {
        Context context = container.getContext();
        if (available(context)) return new HeckerDockBar(context);
        return LayoutInflater.from(context).inflate(
                HeckerStudio.res(context, "layout", "qsb_container_hotseat"), container, false);
    }
    public HeckerDockBar(Context context) {
        super(context); setGravity(Gravity.CENTER_VERTICAL);
        int pad = HeckerStudio.dp(context, 18); setPadding(pad, 0, pad, 0);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xf0254d60, 0xf028334e, 0xf044315f});
        glass.setCornerRadius(HeckerStudio.dp(context, 28));
        colourWash = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x3046a9b8, 0x001a2340, 0x305d37b5});
        colourWash.setCornerRadius(HeckerStudio.dp(context, 28));
        setWillNotDraw(false);
        glass.setStroke(HeckerStudio.dp(context, 1), 0x9097dfe8);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x408dbdff), glass, null));
        ImageView mark = new ImageView(context);
        mark.setImageResource(HeckerStudio.res(context, "drawable", "hs_search_mark"));
        mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mark, new LayoutParams(HeckerStudio.dp(context, 28), HeckerStudio.dp(context, 28)));
        TextView label = HeckerStudio.label(context, HeckerStudio.text(context, "dock_search_brand"), 15);
        label.setTextColor(0xffe0fcff);
        label.setSingleLine(); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(HeckerStudio.dp(context, 12), 0, 0, 0);
        addView(label, new LayoutParams(0, -2, 1));
        ImageView detail = new ImageView(context);
        detail.setImageResource(android.R.drawable.ic_menu_search);
        detail.setImageTintList(ColorStateList.valueOf(0xffd5bcff));
        detail.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(detail, new LayoutParams(HeckerStudio.dp(context, 23), HeckerStudio.dp(context, 23)));
        setFocusable(true); setContentDescription(HeckerStudio.text(context, "search_hint"));
        setOnClickListener(view -> HeckerStudio.search(context));
        setOnLongClickListener(view -> { HeckerStudio.open(context, "search"); return true; });
        int id = HeckerStudio.res(context, "id", "search_container_hotseat");
        if (id != 0) setId(id);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Retain the brand colours when Glass Studio replaces the background with wallpaper frost.
        colourWash.setBounds(0, 0, getWidth(), getHeight()); colourWash.draw(canvas);
    }
    @Override public void setVisibility(int visibility) {
        requestedVisibility = visibility;
        super.setVisibility(HeckerStudio.enabled(getContext(), "dock_search", true) ? visibility : GONE);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        HeckerStudio.prefs(getContext()).registerOnSharedPreferenceChangeListener(this);
        setVisibility(requestedVisibility);
    }
    @Override protected void onDetachedFromWindow() {
        HeckerStudio.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if ((HeckerStudio.PREFIX + "dock_search").equals(key)) {
            setVisibility(requestedVisibility); requestLayout();
        }
    }
    public static int extraHeight(View bar, int nativeHeight, boolean vertical) {
        return bar instanceof HeckerDockBar && !vertical
                && HeckerStudio.enabled(bar.getContext(), "dock_search", true)
                ? Math.max(0, HeckerStudio.dp(bar.getContext(), 48) - nativeHeight) : 0;
    }
    public static void measure(View bar, int width, int height, int availableWidth) {
        if (bar instanceof HeckerDockBar) {
            int safeWidth = Math.max(1, availableWidth - HeckerStudio.dp(bar.getContext(), 32));
            width = width > 0 ? Math.min(width, safeWidth) : safeWidth;
            height = Math.max(height, HeckerStudio.dp(bar.getContext(), 48));
        }
        bar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    public static int bottomOffset(View bar, int nativeOffset, int navigationInset) {
        return bar instanceof HeckerDockBar
                ? Math.max(nativeOffset, navigationInset + HeckerStudio.dp(bar.getContext(), 8)) : nativeOffset;
    }
}
