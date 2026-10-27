// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.android.launcher3.LauncherFiles;
import java.util.*;

/** Shared, per-user launcher preferences. Hiding never changes Android App Lock state. */
public final class HeckerStudio {
    public static final String PREFIX = "hecker_studio_";
    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }
    public static int number(Context c, String key, int fallback, int min, int max) {
        try { return StudioPolicy.clamp(prefs(c).getInt(PREFIX + key, fallback), min, max); }
        catch (ClassCastException bad) { return fallback; }
    }
    public static boolean enabled(Context c, String key, boolean fallback) {
        try { return prefs(c).getBoolean(PREFIX + key, fallback); }
        catch (ClassCastException bad) { return fallback; }
    }
    public static String value(Context c, String key, String fallback) {
        try { return prefs(c).getString(PREFIX + key, fallback); }
        catch (ClassCastException bad) { return fallback; }
    }
    public static int res(Context c, String type, String name) {
        return c.getResources().getIdentifier(name, type, c.getPackageName());
    }
    public static String text(Context c, String name) { return c.getString(res(c, "string", "hs_" + name)); }
    public static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    public static String appKey(Context c, ComponentName app, UserHandle user) {
        android.os.UserManager manager = c.getSystemService(android.os.UserManager.class);
        return app.flattenToString() + "@" + manager.getSerialNumberForUser(user);
    }
    public static boolean hidden(Context c, ComponentName app, UserHandle user) {
        return app != null && prefs(c).getStringSet(PREFIX + "hidden", Collections.emptySet()).contains(appKey(c, app, user));
    }
    public static void hide(Context c, ComponentName app, UserHandle user, boolean hidden) {
        Set<String> next = new HashSet<>(prefs(c).getStringSet(PREFIX + "hidden", Collections.emptySet()));
        if (hidden) next.add(appKey(c, app, user)); else next.remove(appKey(c, app, user));
        prefs(c).edit().putStringSet(PREFIX + "hidden", next).apply();
    }
    public static boolean locked(Context c) { return enabled(c, "layout_lock", false); }
    public static boolean customGridMetrics(Context c) {
        SharedPreferences p = prefs(c);
        for (String key : new String[]{"home_rows", "home_columns", "drawer_columns", "drawer_rows", "home_spacing", "drawer_spacing", "home_label_size", "drawer_label_size"})
            if (p.contains(PREFIX + key)) return true;
        return false;
    }
    public static boolean blockMove(Context c) {
        if (!locked(c)) return false;
        Toast.makeText(c, text(c, "layout_locked"), Toast.LENGTH_SHORT).show(); return true;
    }
    public static void open(Context c, String section) {
        c.startActivity(new Intent(c, HeckerStudioActivity.class).putExtra("section", section));
    }
    public static void appLock(Context c, ComponentName component, UserHandle user) {
        if (user != null && !android.os.Process.myUserHandle().equals(user)) {
            Toast.makeText(c, text(c, "app_lock_profile"), Toast.LENGTH_LONG).show(); return;
        }
        // The signature-only Suite router keeps all configuration behind native authentication.
        Intent intent = new Intent().setClassName("org.hecker.suite", "org.hecker.suite.SuiteRouterActivity")
                .putExtra("org.hecker.suite.PAGE", "AppLockActivity");
        if (component != null && android.os.Process.myUserHandle().equals(user)) {
            intent.putExtra("org.hecker.suite.APP_PACKAGE", component.getPackageName());
        }
        try { c.startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(c, text(c, "app_lock_unavailable"), Toast.LENGTH_LONG).show();
        }
    }
    public static void editIcon(Context c, ComponentName component, UserHandle user) {
        c.startActivity(new Intent(c, HeckerStudioActivity.class).putExtra("section", "app")
                .putExtra("component", component).putExtra("user", user));
    }
    public static void search(Context c) { c.startActivity(new Intent(c, HeckerSearchActivity.class)); }
    public static float[] scaled(float[] values, float factor) {
        float[] result = values.clone(); for (int i = 0; i < result.length; i++) result[i] *= factor; return result;
    }
    public static PointF[] spacing(PointF[] values, int extra) {
        PointF[] result = new PointF[values.length];
        for (int i = 0; i < result.length; i++) result[i] = new PointF(values[i].x + extra, values[i].y + extra);
        return result;
    }
    /** Lifetime follows the native invariant profile, including its closeable tracker. */
    public static Runnable observeProfile(Context c, Runnable changed) {
        SharedPreferences p = prefs(c);
        Handler main = new Handler(Looper.getMainLooper());
        Runnable update = changed::run;
        SharedPreferences.OnSharedPreferenceChangeListener listener = (preferences, key) -> {
            if (key != null && key.startsWith(PREFIX) && !key.startsWith(PREFIX + "clock")) {
                main.removeCallbacks(update); main.post(update);
            }
        };
        p.registerOnSharedPreferenceChangeListener(listener);
        return () -> { p.unregisterOnSharedPreferenceChangeListener(listener); main.removeCallbacks(update); };
    }
    public static GradientDrawable panel(int color, float radius, int border) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        d.setStroke(1, border); return d;
    }
    public static TextView label(Context c, CharSequence title, float size) {
        TextView v = new TextView(c); v.setText(title); v.setTextSize(size); v.setTextColor(0xfff6f3e8);
        v.setPadding(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 8)); return v;
    }
    public static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    public static void showPage(Activity activity, LinearLayout page) {
        page.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 24));
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true); scroll.setBackgroundColor(0xff10161c);
        scroll.addView(page); activity.setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom); return insets;
        });
        scroll.requestApplyInsets();
    }
    private HeckerStudio() {}
}
