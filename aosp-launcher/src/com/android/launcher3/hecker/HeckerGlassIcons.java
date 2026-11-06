// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.LruCache;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native app monochrome art, on a wallpaper-sampled glass plate. No downloaded icon pack. */
public final class HeckerGlassIcons {
    private static final int SIZE = 144;
    private static final LruCache<String, Glyph> MASKS = new LruCache<>(96);
    private static final Map<View,Boolean> HOSTS=new WeakHashMap<>();
    private static final Map<String, List<Runnable>> PENDING = new HashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static synchronized void clear() { MASKS.evictAll(); MAIN.post(() -> { synchronized(HeckerGlassIcons.class) { for(View v:new ArrayList<>(HOSTS.keySet()))if(v!=null)v.invalidate(); } }); }

    public static Drawable icon(Context context, ComponentName component, UserHandle user,
            Drawable original, Runnable refresh, View host) {
        if (!HeckerStudio.enabled(context, "glass_icons", true) || original == null || component == null
                || original instanceof android.graphics.drawable.Animatable
                || original.getColorFilter() != null
                || original.getClass().getSimpleName().contains("Preload")) return original;
        if(host!=null)synchronized(HeckerGlassIcons.class){HOSTS.put(host,true);}
        String key = HeckerStudio.appKey(context, component, user) + ":"
                + context.getResources().getConfiguration().densityDpi;
        Glyph mask;
        synchronized (HeckerGlassIcons.class) {
            mask = MASKS.get(key);
            if (mask == null) {
                List<Runnable> waiting = PENDING.get(key);
                if (waiting == null) {
                    waiting = new ArrayList<>(); PENDING.put(key, waiting);
                    Context app = context.getApplicationContext();
                    WORKER.execute(() -> load(app, component, user, key));
                }
                if (refresh != null && waiting.size() < 12) waiting.add(refresh);
            }
        }
        if (mask == null || mask.bitmap.getWidth() == 1) return original;
        return context.getPackageManager().getUserBadgedIcon(new Icon(context.getApplicationContext(),original, mask.bitmap, mask.tinted, host), user);
    }
    private static void load(Context context, ComponentName component, UserHandle user, String key) {
        Bitmap result = null; boolean tinted = false;
        try {
            // Drive's adaptive icon does not consistently publish a monochrome
            // layer on Coral. Do not depend on LauncherApps resolving that layer.
            if ("com.google.android.apps.docs".equals(component.getPackageName())) {
                result = driveGlyph(); tinted = true;
            } else if ("org.hecker.suite".equals(component.getPackageName())
                    && "org.hecker.suite.GameSpaceLauncher".equals(component.getClassName())) {
                Drawable glyph = context.getDrawable(HeckerStudio.res(context,"drawable","hs_gamespace_monochrome"));
                result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
                glyph.setBounds(0, 0, SIZE, SIZE); glyph.draw(new Canvas(result)); tinted = true;
            } else {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            LauncherActivityInfo info = apps.resolveActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component), user);
            if (info != null) {
                if (component.getPackageName().equals("com.google.android.GoogleCamera")
                        || component.getPackageName().equals("com.android.camera2")) {
                    result = cameraGlyph(); tinted = true;
                } else {
                Drawable source = info.getIcon(context.getResources().getConfiguration().densityDpi);
                Drawable glyph = source instanceof AdaptiveIconDrawable adaptive ? adaptive.getMonochrome() : null;
                tinted = glyph != null;
                // Legacy art keeps its luminance detail in the same cool accent family.
                // Its opaque pixels must not be flattened into a solid white disc.
                if (glyph == null) glyph = source instanceof AdaptiveIconDrawable adaptive ? adaptive.getForeground() : source;
                Bitmap pixels = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
                glyph.setBounds(0, 0, SIZE, SIZE); glyph.draw(new Canvas(pixels));
                int[] values = new int[SIZE * SIZE]; pixels.getPixels(values, 0, SIZE, 0, 0, SIZE, SIZE);
                if (tinted) GlassIconMask.extract(values, SIZE, SIZE);
                else GlassIconMask.harmonize(values);
                boolean ink = false;
                for (int value : values) if ((value >>> 24) > 24) { ink = true; break; }
                if (ink) { pixels.setPixels(values, 0, SIZE, 0, 0, SIZE, SIZE); result = pixels; }
                }
            }
            }
        } catch (RuntimeException unavailable) { /* Quiet/removed profile retains its native icon. */ }
        if (result == null) result = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        List<Runnable> callbacks;
        synchronized (HeckerGlassIcons.class) { MASKS.put(key, new Glyph(result,tinted)); callbacks = PENDING.remove(key); }
        if (callbacks != null) MAIN.post(() -> { for (Runnable callback : callbacks) callback.run(); });
    }
    /** White, transparent Drive triangle that inherits the user's glass icon tint. */
    private static Bitmap driveGlyph() {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        Path left = new Path();
        left.moveTo(55, 20); left.lineTo(80, 20); left.lineTo(41, 113);
        left.lineTo(14, 113); left.close(); canvas.drawPath(left, paint);
        Path right = new Path();
        right.moveTo(80, 20); right.lineTo(132, 104); right.lineTo(107, 116);
        right.lineTo(55, 29); right.close(); canvas.drawPath(right, paint);
        Path base = new Path();
        base.moveTo(41, 91); base.lineTo(116, 91); base.lineTo(132, 114);
        base.lineTo(14, 114); base.close(); canvas.drawPath(base, paint);
        return bitmap;
    }
    /** A clean camera body and open lens, rather than flattening the app's opaque plate. */
    private static Bitmap cameraGlyph() {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(7);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path body = new Path(); body.moveTo(24, 43); body.lineTo(44, 43);
        body.lineTo(53, 29); body.lineTo(91, 29); body.lineTo(100, 43); body.lineTo(120, 43);
        body.quadTo(128, 43, 128, 51); body.lineTo(128, 107); body.quadTo(128, 115, 120, 115);
        body.lineTo(24, 115); body.quadTo(16, 115, 16, 107); body.lineTo(16, 51);
        body.quadTo(16, 43, 24, 43); body.close(); canvas.drawPath(body, paint);
        canvas.drawCircle(72, 79, 24, paint);
        paint.setStyle(Paint.Style.FILL); canvas.drawCircle(111, 58, 4, paint);
        return bitmap;
    }
    private static final class Glyph {
        final Bitmap bitmap; final boolean tinted;
        Glyph(Bitmap bitmap,boolean tinted){this.bitmap=bitmap;this.tinted=tinted;}
    }
    private static final class Icon extends Drawable {
        final Context context; final Drawable original; final Bitmap glyph; final WeakReference<View> host;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF destination = new RectF(),plate=new RectF(),rim=new RectF(); final Rect source = new Rect();
        int alpha = 255; ColorFilter filter; final boolean tinted;
        Icon(Context context,Drawable original, Bitmap glyph, boolean tinted, View host) {
            this.tinted=tinted;
            this.context=context; this.original = original; this.glyph = glyph; this.host = new WeakReference<>(host);
            int[] pixels = new int[glyph.getWidth() * glyph.getHeight()];
            glyph.getPixels(pixels, 0, glyph.getWidth(), 0, 0, glyph.getWidth(), glyph.getHeight());
            int[] box = GlassIconMask.bounds(pixels, glyph.getWidth(), glyph.getHeight());
            source.set(box[0], box[1], box[2], box[3]);
        }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds(); if (bounds.isEmpty() || alpha == 0) return;
            if (original.getColorFilter() != null) { original.setBounds(bounds); original.draw(canvas); return; }
            int save = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha);
            boolean flow=HeckerStudio.number(context,"glass_icon_style",0,0,1)==1;
            if(flow){
                float radius=Math.min(bounds.width(),bounds.height())*.32f;
                paint.setShader(null);paint.setColorFilter(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0x73171928);
                plate.set(bounds);canvas.drawRoundRect(plate,radius,radius,paint);
                float inset=Math.max(1,Math.min(bounds.width(),bounds.height())/48f);
                rim.set(bounds);rim.inset(inset/2,inset/2);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(inset);paint.setColor(0x78ffffff);
                canvas.drawRoundRect(rim,radius,radius,paint);rim.inset(inset,inset);paint.setColor(0x1cffffff);
                canvas.drawRoundRect(rim,Math.max(0,radius-inset),Math.max(0,radius-inset),paint);paint.setStyle(Paint.Style.FILL);
            } else if (!HeckerLauncherGlass.drawIconPlate(host.get(), canvas, bounds)) {
                paint.setColor(0xb8332632); paint.setColorFilter(null);
                canvas.drawOval(new RectF(bounds), paint);
            }
            float span = Math.min(bounds.width(), bounds.height()) * (flow?.56f:.68f);
            float scale = span / Math.max(source.width(), source.height());
            float width = source.width() * scale, height = source.height() * scale;
            destination.set(bounds.exactCenterX() - width / 2, bounds.exactCenterY() - height / 2,
                    bounds.exactCenterX() + width / 2, bounds.exactCenterY() + height / 2);
            paint.setColor(Color.WHITE); paint.setColorFilter(filter != null ? filter : tinted
                    ? new PorterDuffColorFilter(flow?Color.WHITE:0xffefdee5, PorterDuff.Mode.SRC_IN) : null);
            canvas.drawBitmap(glyph, source, destination, paint); canvas.restoreToCount(save);
        }
        @Override public int getIntrinsicWidth() { return original.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return original.getIntrinsicHeight(); }
        @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter value) { filter = value; invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    private HeckerGlassIcons() { }
}
