// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.provider.Settings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Fresh defaults and an ownership-checked update of the previous bundled wallpaper. */
public final class DefaultAppearance {
    public static final String ID = "ca2aca2da00000000000000000000002";
    private static final String PREVIOUS_ID = "ca2aca1da00000000000000000000001";
    public static final String WALLPAPER_ID = "hecker_depth_wallpaper_wallpaper_id";
    public static final String HOME_WALLPAPER_ID = "hecker_home_depth_wallpaper_wallpaper_id";
    public static final String HOME_DEPTH = "hecker_home_depth_wallpaper";
    private static final String ASSETS = "wallpapers/caracal-dawn-v2/";
    private static final String PREFS = "hecker_default_appearance";
    public static int wallpaperId(Context context) {
        WallpaperManager manager = WallpaperManager.getInstance(context);
        int lock = manager.getWallpaperId(WallpaperManager.FLAG_LOCK);
        return lock > 0 ? lock : manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
    }
    public static void reconcile(Context context) {
        int expected = Settings.Secure.getInt(context.getContentResolver(), WALLPAPER_ID, Integer.MIN_VALUE);
        if (expected != Integer.MIN_VALUE && expected != wallpaperId(context))
            Settings.Secure.putInt(context.getContentResolver(), "hecker_depth_wallpaper", 0);
        int home = Settings.Secure.getInt(context.getContentResolver(), HOME_WALLPAPER_ID, Integer.MIN_VALUE);
        if (home != Integer.MIN_VALUE && home != WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM))
            Settings.Secure.putInt(context.getContentResolver(), HOME_DEPTH, 0);
    }
    public static boolean apply(Context context, boolean requested) throws IOException {
        Context device = context.createDeviceProtectedStorageContext();
        SharedPreferences prefs = device.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!requested && prefs.getBoolean("applied_v2", false)) return false;
        WallpaperManager manager = WallpaperManager.getInstance(context);
        int previous = Settings.Secure.getInt(context.getContentResolver(), WALLPAPER_ID, -1);
        boolean updateBundled = previous > 0 && PREVIOUS_ID.equals(Settings.Secure.getString(context.getContentResolver(), DepthWallpaperProvider.ID))
                && previous == wallpaperId(context) && previous == manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                && manager.getWallpaperInfo() == null && Settings.Secure.getInt(context.getContentResolver(), "hecker_depth_wallpaper", 0) == 1;
        boolean chosenClock = Settings.Secure.getString(context.getContentResolver(), "hecker_custom_lockscreen_clock") != null
                || Settings.Secure.getString(context.getContentResolver(), "hecker_lockscreen_clock_style") != null
                || Settings.Secure.getString(context.getContentResolver(), DepthWallpaperProvider.ID) != null;
        boolean pristine;
        // WallpaperData can have a positive ID even while ImageWallpaper still uses the built-in
        // resource. Check for actual saved wallpaper files, rather than mistaking that ID for a choice.
        try (android.os.ParcelFileDescriptor home = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM);
                android.os.ParcelFileDescriptor lock = manager.getWallpaperFile(WallpaperManager.FLAG_LOCK)) {
            pristine = home == null && lock == null && manager.getWallpaperInfo() == null;
        }
        if (!requested && !updateBundled && (!pristine || chosenClock)) {
            prefs.edit().putBoolean("applied_v2", true).commit(); return false;
        }
        Bitmap back = decode(context, "background"), front = decode(context, "foreground");
        if (back.getWidth() != front.getWidth() || back.getHeight() != front.getHeight())
            throw new IOException("Default wallpaper layers do not align");
        float factor = Math.min(1, 1600f / Math.max(back.getWidth(), back.getHeight()));
        int width = Math.max(1, Math.round(back.getWidth() * factor));
        int height = Math.max(1, Math.round(back.getHeight() * factor));
        Bitmap background = Bitmap.createScaledBitmap(back, width, height, true);
        Bitmap foreground = Bitmap.createScaledBitmap(front, width, height, true);
        File directory = new File(new File(device.getFilesDir(), "depth"), ID);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create depth defaults");
        write(new File(directory, "background.png"), background);
        write(new File(directory, "foreground.png"), foreground);
        Bitmap wallpaper = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(wallpaper); Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        RectF bounds = new RectF(0, 0, width, height);
        canvas.drawBitmap(background, null, bounds, paint); canvas.drawBitmap(foreground, null, bounds, paint);
        manager.setBitmap(wallpaper, null, true, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
        boolean published = Settings.Secure.putInt(context.getContentResolver(), WALLPAPER_ID, wallpaperId(context))
                && Settings.Secure.putInt(context.getContentResolver(), HOME_WALLPAPER_ID, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
                && Settings.Secure.putString(context.getContentResolver(), HomeAppearance.KEY, ID)
                && Settings.Secure.putInt(context.getContentResolver(), HOME_DEPTH,
                        !requested && updateBundled ? Settings.Secure.getInt(context.getContentResolver(), HOME_DEPTH, 1) : 1);
        if (requested || !updateBundled) published = published
                && Settings.Secure.putInt(context.getContentResolver(), "hecker_lockscreen_clock_opacity", 76)
                && Settings.Secure.putInt(context.getContentResolver(), "hecker_lockscreen_clock_style", 2)
                && Settings.Secure.putInt(context.getContentResolver(), "hecker_custom_lockscreen_clock", 1)
                && Settings.Secure.putInt(context.getContentResolver(), "hecker_depth_wallpaper_wake", 1);
        published = published && Settings.Secure.putInt(context.getContentResolver(), "hecker_depth_wallpaper", 1)
                && Settings.Secure.putString(context.getContentResolver(), DepthWallpaperProvider.ID, ID);
        if (!published) throw new IOException("Cannot publish default appearance");
        prefs.edit().putBoolean("applied_v1", true).putBoolean("applied_v2", true).commit();
        return true;
    }
    private static Bitmap decode(Context context, String layer) throws IOException {
        try (InputStream input = context.getAssets().open(ASSETS + layer + ".png")) {
            Bitmap result = BitmapFactory.decodeStream(input);
            if (result == null || result.getWidth() > 4096 || result.getHeight() > 4096)
                throw new IOException("Invalid bundled wallpaper");
            return result;
        }
    }
    private static void write(File target, Bitmap bitmap) throws IOException {
        android.util.AtomicFile file = new android.util.AtomicFile(target);
        FileOutputStream stream = file.startWrite();
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("Cannot encode layer");
            file.finishWrite(stream);
        } catch (IOException | RuntimeException failure) { file.failWrite(stream); throw failure; }
    }
    private DefaultAppearance() { }
}
