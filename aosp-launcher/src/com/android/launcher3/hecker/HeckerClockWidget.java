// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.appwidget.*;
import android.content.*;
import android.view.*;
import android.widget.*;

/** Standard resizable widget entry; Launcher3 hosts an animated local view for this provider only. */
public final class HeckerClockWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            if (!HeckerStudio.prefs(c).contains(HeckerStudio.PREFIX + "clock_widget_" + id))
                HeckerStudio.prefs(c).edit().putString(HeckerStudio.PREFIX + "clock_widget_" + id, HeckerStudio.value(c, "clock_style", "signature")).apply();
            RemoteViews views = new RemoteViews(c.getPackageName(), HeckerStudio.res(c, "layout", "hs_clock_widget"));
            manager.updateAppWidget(id, views);
        }
    }
    @Override public void onDeleted(Context c, int[] ids) {
        SharedPreferences.Editor edit = HeckerStudio.prefs(c).edit();
        for (int id : ids) edit.remove(HeckerStudio.PREFIX + "clock_widget_" + id); edit.apply();
    }
    public static boolean bind(AppWidgetHostView host) {
        AppWidgetProviderInfo info = host.getAppWidgetInfo();
        if (info == null || !new ComponentName(host.getContext(), HeckerClockWidget.class).equals(info.provider)) return false;
        if (host.getChildCount() != 1 || !(host.getChildAt(0) instanceof HeckerClockView)) {
            host.removeAllViews(); HeckerClockView view = new HeckerClockView(host.getContext(), host.getAppWidgetId());
            host.addView(view, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return true;
    }
}
