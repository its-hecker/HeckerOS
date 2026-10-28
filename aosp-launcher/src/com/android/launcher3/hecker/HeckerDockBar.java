// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One inset-safe slot and one glass renderer for the user's Hecker/Google search choice. */
public final class HeckerDockBar extends LinearLayout
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private int requestedVisibility = VISIBLE;
    public static boolean available(Context context) {
        return HeckerStudio.res(context, "string", "hs_dock_search") != 0;
    }
    public static int profileHeight(Context context, int nativeHeight, boolean vertical) {
        return available(context) && !vertical && HeckerStudio.enabled(context, "dock_search", true)
                ? Math.max(nativeHeight, HeckerStudio.dp(context, 60)) : nativeHeight;
    }
    public static View create(ViewGroup container) {
        Context context = container.getContext();
        if (available(context)) return new HeckerDockBar(context);
        return LayoutInflater.from(context).inflate(
                HeckerStudio.res(context, "layout", "qsb_container_hotseat"), container, false);
    }
    public HeckerDockBar(Context context) {
        super(context); setGravity(Gravity.CENTER_VERTICAL);
        int pad = HeckerStudio.dp(context, 10); setPadding(pad, 0, pad, 0);
        GradientDrawable fallback = new GradientDrawable(); fallback.setColor(0xb830232e);
        fallback.setCornerRadius(HeckerStudio.dp(context, 28));
        fallback.setStroke(HeckerStudio.dp(context, 1), 0x35efd6e2);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x25f1d5e2), fallback, null));
        setFocusable(true);
        setOnLongClickListener(view -> { HeckerStudio.open(context, "search"); return true; });
        int id = HeckerStudio.res(context, "id", "search_container_hotseat");
        if (id != 0) setId(id);
        rebuild();
    }
    private void rebuild() {
        removeAllViews(); Context context = getContext();
        boolean google = HeckerStudio.enabled(context, "google_search", false);
        if (google) {
            TextView mark = new TextView(context); mark.setText("G"); mark.setTextSize(27);
            mark.setTextColor(0xffefdee5); mark.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            mark.setGravity(Gravity.CENTER); mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(mark, new LayoutParams(HeckerStudio.dp(context, 38), -1));
            View spacer = new View(context); addView(spacer, new LayoutParams(0, 1, 1));
            action("google_search", "hs_google_search", () -> HeckerGoogleSearch.search(context));
            Intent voice = HeckerGoogleSearch.voice(), lens = HeckerGoogleSearch.lens(context);
            if (HeckerGoogleSearch.available(context, voice))
                action("google_voice", "hs_google_mic", () -> HeckerGoogleSearch.launch(context, voice));
            if (HeckerGoogleSearch.available(context, lens))
                action("google_lens", "hs_google_lens", () -> HeckerGoogleSearch.launch(context, lens));
            setContentDescription(HeckerStudio.text(context, "google_search"));
            setOnClickListener(view -> HeckerGoogleSearch.search(context));
        } else {
            ImageView mark = new ImageView(context);
            mark.setImageResource(HeckerStudio.res(context, "drawable", "hs_search_mark"));
            mark.setImageTintList(ColorStateList.valueOf(0xffefdee5));
            mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams markSize = new LayoutParams(HeckerStudio.dp(context, 28), HeckerStudio.dp(context, 28));
            markSize.leftMargin = HeckerStudio.dp(context, 7); addView(mark, markSize);
            TextView label = HeckerStudio.label(context, HeckerStudio.text(context, "dock_search_brand"), 15);
            label.setTextColor(0xffefdee5); label.setSingleLine();
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setPadding(HeckerStudio.dp(context, 12), 0, 0, 0);
            addView(label, new LayoutParams(0, -2, 1));
            action("search_hint", "hs_google_search", () -> HeckerStudio.search(context));
            setContentDescription(HeckerStudio.text(context, "search_hint"));
            setOnClickListener(view -> HeckerStudio.search(context));
        }
    }
    private void action(String description, String resource, Runnable click) {
        Context context = getContext(); ImageView icon = new ImageView(context);
        icon.setImageResource(HeckerStudio.res(context, "drawable", resource));
        icon.setImageTintList(ColorStateList.valueOf(0xffefdee5));
        int padding = HeckerStudio.dp(context, 10); icon.setPadding(padding, padding, padding, padding);
        icon.setContentDescription(HeckerStudio.text(context, description)); icon.setFocusable(true);
        icon.setOnClickListener(view -> click.run());
        addView(icon, new LayoutParams(HeckerStudio.dp(context, 44), HeckerStudio.dp(context, 48)));
    }
    @Override public void setVisibility(int visibility) {
        requestedVisibility = visibility;
        super.setVisibility(HeckerStudio.enabled(getContext(), "dock_search", true) ? visibility : GONE);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        HeckerStudio.prefs(getContext()).registerOnSharedPreferenceChangeListener(this);
        rebuild(); setVisibility(requestedVisibility);
    }
    @Override protected void onDetachedFromWindow() {
        HeckerStudio.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if ((HeckerStudio.PREFIX + "dock_search").equals(key)) {
            setVisibility(requestedVisibility); requestLayout();
        } else if ((HeckerStudio.PREFIX + "google_search").equals(key)) {
            rebuild(); requestLayout();
        }
    }
    public static int extraHeight(View bar, int nativeBottomPadding, int nativeOffset,
            int navigationInset, boolean vertical) {
        return bar instanceof HeckerDockBar && !vertical
                && HeckerStudio.enabled(bar.getContext(), "dock_search", true)
                ? DockSearchLayout.extraHeight(nativeBottomPadding,
                        bottomOffset(bar, nativeOffset, navigationInset),
                        HeckerStudio.dp(bar.getContext(), 48), HeckerStudio.dp(bar.getContext(), 12)) : 0;
    }
    public static void measure(View bar, int width, int height, int availableWidth) {
        if (bar instanceof HeckerDockBar) {
            int safeWidth = Math.max(1, availableWidth - HeckerStudio.dp(bar.getContext(), 32));
            width = width > 0 ? Math.min(width, safeWidth) : safeWidth;
            height = HeckerStudio.dp(bar.getContext(), 48);
        }
        bar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    public static int bottomOffset(View bar, int nativeOffset, int navigationInset) {
        return bar instanceof HeckerDockBar
                ? Math.max(nativeOffset, navigationInset + HeckerStudio.dp(bar.getContext(), 8)) : nativeOffset;
    }
}
