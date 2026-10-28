// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.LruCache;
import android.widget.TextView;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Icon-pack parsing and rasterization run off main. The stock icon remains until its replacement is ready. */
public final class HeckerIconStudio {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> ICONS = new LruCache<>(96);
    private static final Map<String, Map<String, String>> FILTERS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final Map<String, List<Runnable>> WAITERS = new HashMap<>();
    public static List<ResolveInfo> packs(Context c) {
        Map<String, ResolveInfo> unique = new TreeMap<>();
        for (String action : new String[]{"org.adw.launcher.THEMES", "com.gau.go.launcherex.theme", "com.novalauncher.THEME", "com.anddoes.launcher.THEME"})
            for (ResolveInfo r : c.getPackageManager().queryIntentActivities(new Intent(action), 0)) unique.put(r.activityInfo.packageName, r);
        return new ArrayList<>(unique.values());
    }
    public static synchronized void clear() { ICONS.evictAll(); FILTERS.clear(); HeckerGlassIcons.clear(); }
    /** Supported public icon-pack format, in res/xml/appfilter.xml or assets/appfilter.xml. */
    public static Map<String, String> filter(Context c, String pack) {
        if (FILTERS.containsKey(pack)) return FILTERS.get(pack);
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Resources r = c.getPackageManager().getResourcesForApplication(pack);
            int id = r.getIdentifier("appfilter", "xml", pack);
            if (id != 0) { try (XmlResourceParser parser = r.getXml(id)) { parse(parser, result); } }
            else try (InputStream stream = r.getAssets().open("appfilter.xml")) {
                XmlPullParser parser = android.util.Xml.newPullParser(); parser.setInput(stream, "UTF-8"); parse(parser, result);
            }
        } catch (Exception unavailable) { android.util.Log.w("HeckerLauncher", "Icon pack appfilter unavailable: " + pack); }
        Map<String, String> immutable = Collections.unmodifiableMap(result); FILTERS.putIfAbsent(pack, immutable); return FILTERS.get(pack);
    }
    private static void parse(XmlPullParser parser, Map<String, String> result) throws Exception {
        int count = 0;
        while (parser.next() != XmlPullParser.END_DOCUMENT && count++ < 150000) {
            if (parser.getEventType() != XmlPullParser.START_TAG || !"item".equals(parser.getName())) continue;
            String component = parser.getAttributeValue(null, "component"), drawable = parser.getAttributeValue(null, "drawable");
            if (component == null || drawable == null || drawable.length() > 200) continue;
            if (component.startsWith("ComponentInfo{") && component.endsWith("}")) component = component.substring(14, component.length() - 1);
            ComponentName name = ComponentName.unflattenFromString(component);
            if (name != null) result.put(name.flattenToString(), drawable);
        }
    }
    public static CharSequence label(Context c, ComponentName component, UserHandle user, CharSequence original) {
        if (component == null) return original;
        String value = HeckerStudio.value(c, "label_" + HeckerStudio.appKey(c, component, user), "");
        return value.trim().isEmpty() ? original : value;
    }
    public static void labels(TextView view, boolean drawer) {
        Context c = view.getContext(); String target = drawer ? "drawer" : "home";
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, HeckerStudio.number(c, target + "_label_size", 13, 9, 20));
    }
    public static Drawable icon(Context c, ComponentName component, UserHandle user, Drawable original, Runnable refresh, android.view.View host) {
        if (component == null || original == null) return original;
        String selected = HeckerStudio.value(c, "icon_" + HeckerStudio.appKey(c, component, user), "");
        if (selected.equals("@system")) return shaped(c, original);
        String pack = selected.isEmpty() ? HeckerStudio.value(c, "icon_pack", "") : selected.split("\\|", 2)[0];
        if (pack.isEmpty()) return HeckerGlassIcons.icon(c, component, user, shaped(c, original), refresh, host);
        String key = pack + ":" + selected + ":" + component.flattenToString() + ":" + c.getResources().getConfiguration().densityDpi;
        Bitmap cached;
        synchronized (HeckerIconStudio.class) {
            cached = ICONS.get(key);
            if (cached == null) {
                if (refresh != null) WAITERS.computeIfAbsent(key, ignored -> new ArrayList<>()).add(refresh);
                if (PENDING.add(key)) {
                    Context app = c.getApplicationContext();
                    WORK.execute(() -> {
                        Bitmap bitmap = null;
                        try {
                            String name = selected.contains("|") ? selected.split("\\|", 2)[1] : filter(app, pack).get(component.flattenToString());
                            if (name != null) {
                                Resources resources = app.getPackageManager().getResourcesForApplication(pack);
                                int id = resources.getIdentifier(name, "drawable", pack);
                                if (id != 0) {
                                    Drawable icon = resources.getDrawable(id, null);
                                    bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
                                    icon.setBounds(0, 0, 192, 192); icon.draw(new Canvas(bitmap));
                                }
                            }
                        } catch (Exception unavailable) { }
                        // A transparent 1px sentinel prevents retry storms for missing mappings.
                        if (bitmap == null) bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                        List<Runnable> waiting;
                        synchronized (HeckerIconStudio.class) { ICONS.put(key, bitmap); PENDING.remove(key); waiting = WAITERS.remove(key); }
                        if (waiting != null) MAIN.post(() -> { for (Runnable callback : waiting) callback.run(); });
                    });
                }
            }
        }
        if (cached == null || cached.getWidth() == 1) return shaped(c, original);
        return c.getPackageManager().getUserBadgedIcon(shaped(c, new BitmapDrawable(c.getResources(), cached)), user);
    }
    private static Drawable shaped(Context c, Drawable original) {
        int shape = HeckerStudio.number(c, "icon_shape", 0, 0, 3);
        if (shape == 0) return original;
        return new Mask(original, shape);
    }
    private static final class Mask extends Drawable {
        final Drawable child; final int shape; final Path path = new Path();
        Mask(Drawable value, int type) { child = value; shape = type; }
        @Override public void draw(Canvas canvas) {
            RectF r = new RectF(getBounds()); path.reset();
            float radius = shape == 1 ? r.width() / 2 : r.width() * (shape == 2 ? .28f : .15f);
            path.addRoundRect(r, radius, radius, Path.Direction.CW); int save = canvas.save(); canvas.clipPath(path); child.setBounds(getBounds()); child.draw(canvas); canvas.restoreToCount(save);
        }
        @Override public int getIntrinsicWidth() { return child.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return child.getIntrinsicHeight(); }
        @Override public void setAlpha(int value) { child.setAlpha(value); }
        @Override public void setColorFilter(ColorFilter value) { child.setColorFilter(value); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    public static boolean badge(Context c, Canvas canvas, Rect icon, int count) {
        if (!HeckerStudio.enabled(c, "badges", true)) return true;
        if (count <= 0) return true;
        String value = StudioPolicy.badge(count); float d = c.getResources().getDisplayMetrics().density;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); paint.setTextSize(10 * d);
        float width = Math.max(17 * d, paint.measureText(value) + 8 * d), height = 17 * d;
        float x = icon.right - width * .65f, y = icon.top - 2 * d;
        RectF rect = new RectF(x, y, x + width, y + height); paint.setColor(0xffa9e8e0); canvas.drawRoundRect(rect, height / 2, height / 2, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f * d); paint.setColor(0xff18232d); canvas.drawRoundRect(rect, height / 2, height / 2, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xff12242c); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value, rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
        return true;
    }
    private HeckerIconStudio() {}
}
