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
    private static final LruCache<String, Bitmap> MASKS = new LruCache<>(96);
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
        Bitmap mask;
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
        if (mask == null || mask.getWidth() == 1) return original;
        return context.getPackageManager().getUserBadgedIcon(new Icon(context.getApplicationContext(),original, mask, host), user);
    }
    private static void load(Context context, ComponentName component, UserHandle user, String key) {
        Bitmap result = null;
        try {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            LauncherActivityInfo info = apps.resolveActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component), user);
            if (info != null) {
                Drawable source = info.getIcon(context.getResources().getConfiguration().densityDpi);
                Drawable glyph = source instanceof AdaptiveIconDrawable adaptive ? adaptive.getMonochrome() : null;
                if (glyph == null && source instanceof AdaptiveIconDrawable adaptive) glyph = adaptive.getForeground();
                if (glyph == null) glyph = source;
                Bitmap pixels = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
                glyph.setBounds(0, 0, SIZE, SIZE); glyph.draw(new Canvas(pixels));
                int[] values = new int[SIZE * SIZE]; pixels.getPixels(values, 0, SIZE, 0, 0, SIZE, SIZE);
                GlassIconMask.extract(values, SIZE, SIZE);
                boolean ink = false;
                for (int value : values) if ((value >>> 24) > 24) { ink = true; break; }
                if (ink) { pixels.setPixels(values, 0, SIZE, 0, 0, SIZE, SIZE); result = pixels; }
            }
        } catch (RuntimeException unavailable) { /* Quiet/removed profile retains its native icon. */ }
        if (result == null) result = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        List<Runnable> callbacks;
        synchronized (HeckerGlassIcons.class) { MASKS.put(key, result); callbacks = PENDING.remove(key); }
        if (callbacks != null) MAIN.post(() -> { for (Runnable callback : callbacks) callback.run(); });
    }
    private static final class Icon extends Drawable {
        final Context context; final Drawable original; final Bitmap glyph; final WeakReference<View> host;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF destination = new RectF(),plate=new RectF(),rim=new RectF(); final Rect source = new Rect();
        int alpha = 255; ColorFilter filter;
        Icon(Context context,Drawable original, Bitmap glyph, View host) {
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
            paint.setColor(Color.WHITE); paint.setColorFilter(filter == null
                    ? new PorterDuffColorFilter(flow?Color.WHITE:0xffefdee5, PorterDuff.Mode.SRC_IN) : filter);
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
