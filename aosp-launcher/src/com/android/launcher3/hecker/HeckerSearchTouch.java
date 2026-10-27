// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Preserve parent swipe interception and long presses while routing a tap to local search. */
public final class HeckerSearchTouch {
    private static final WeakHashMap<View, GestureDetector> DETECTORS = new WeakHashMap<>();
    public static boolean enabled(Context c) {
        return HeckerStudio.enabled(c, "universal_search", true) && HeckerStudio.res(c, "string", "hs_universal_search") != 0;
    }
    public static boolean click(View view) {
        if (!enabled(view.getContext())) return false;
        HeckerStudio.search(view.getContext()); return true;
    }
    public static boolean touch(View view, MotionEvent event) {
        if (!enabled(view.getContext())) { DETECTORS.remove(view); return false; }
        GestureDetector detector = DETECTORS.get(view);
        if (detector == null) {
            WeakReference<View> reference = new WeakReference<>(view);
            detector = new GestureDetector(view.getContext().getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onSingleTapUp(MotionEvent e) { View v = reference.get(); return v != null && v.performClick(); }
                @Override public void onLongPress(MotionEvent e) { View v = reference.get(); if (v != null) v.performLongClick(); }
            }); DETECTORS.put(view, detector);
        }
        detector.onTouchEvent(event); return true;
    }
    public static boolean bind(AppWidgetHostView host) {
        if (!host.getClass().getName().equals("com.android.launcher3.qsb.OseWidgetView") || !enabled(host.getContext())) return false;
        if (host.getChildCount() != 1 || !"hecker_universal_search_bar".equals(host.getChildAt(0).getTag())) {
            host.removeAllViews(); TextView text = HeckerStudio.label(host.getContext(), HeckerStudio.text(host.getContext(), "search_hint"), 16);
            text.setTag("hecker_universal_search_bar"); text.setGravity(Gravity.CENTER_VERTICAL); int p = HeckerStudio.dp(host.getContext(), 18); text.setPadding(p, 0, p, 0);
            text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            host.setContentDescription(HeckerStudio.text(host.getContext(), "search_hint")); host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); host.setClickable(true);
            host.addView(text, new FrameLayout.LayoutParams(-1, -1));
        }
        return true;
    }
    private HeckerSearchTouch() {}
}
