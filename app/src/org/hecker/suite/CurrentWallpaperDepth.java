// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.*;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Shared worker pipeline for automatic jobs and the explicit current-lock-wallpaper action. */
final class CurrentWallpaperDepth {
    static final Object PUBLISH_LOCK = new Object();
    static synchronized boolean extract(Context context, BooleanSupplier allowed, boolean force) throws IOException {
        WallpaperManager manager = WallpaperManager.getInstance(context);
        int wallpaper = DefaultAppearance.wallpaperId(context);
        if (!allowed.getAsBoolean()
                || manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK) != null) return false;
        String active = Settings.Secure.getString(context.getContentResolver(), DepthWallpaperProvider.ID);
        if (!force && active != null && wallpaper == Settings.Secure.getInt(context.getContentResolver(),
                DefaultAppearance.WALLPAPER_ID, -1) && available(context, active)) return true;
        Bitmap background = read(context, manager);
        Bitmap foreground = null;
        File directory = null;
        boolean committed = false;
        try {
            if (!current(context, allowed, wallpaper)) return false;
            foreground = DepthSubjectExtractor.extract(context, background);
            if (!current(context, allowed, wallpaper)) return false;
            synchronized (PUBLISH_LOCK) {
            String id = UUID.randomUUID().toString().replace("-", "");
            directory = new File(new File(context.getFilesDir(), "depth"), id);
            if (!directory.mkdirs()) throw new IOException("Cannot stage depth layers");
            write(new File(directory, "background.png"), background);
            write(new File(directory, "foreground.png"), foreground);
            if (!current(context, allowed, wallpaper)) return false;
            if (!Settings.Secure.putString(context.getContentResolver(), DepthWallpaperProvider.ID, id)
                    || !Settings.Secure.putInt(context.getContentResolver(), DefaultAppearance.WALLPAPER_ID, wallpaper)
                    || !Settings.Secure.putInt(context.getContentResolver(), "hecker_custom_lockscreen_clock", 1)
                    || !Settings.Secure.putInt(context.getContentResolver(), "hecker_depth_wallpaper", 1))
                throw new IOException("Cannot publish depth pair");
            // Keep the selected geometric, word or Living Flow clock. Extraction never changes its style.
            committed = true;
            String home = Settings.Secure.getString(context.getContentResolver(), HomeAppearance.KEY);
            File[] previous = directory.getParentFile().listFiles();
            if (previous != null) for (File old : previous)
                if (!old.equals(directory) && old.getName().matches("[a-f0-9]{32}")
                        && !old.getName().equals(home)) erase(old);
            return true;
            }
        } finally {
            background.recycle();
            if (foreground != null) foreground.recycle();
            if (!committed && directory != null) erase(directory);
        }
    }
    private static boolean current(Context context, BooleanSupplier allowed, int wallpaper) {
        return allowed.getAsBoolean() && !Thread.currentThread().isInterrupted()
                && wallpaper == DefaultAppearance.wallpaperId(context);
    }
    private static boolean available(Context context, String id) {
        if (!id.matches("[a-f0-9]{32}")) return false;
        File directory = new File(new File(context.getFilesDir(), "depth"), id);
        return new File(directory, "background.png").isFile() && new File(directory, "foreground.png").isFile();
    }
    private static Bitmap read(Context context, WallpaperManager manager) throws IOException {
        int flag = manager.getWallpaperId(WallpaperManager.FLAG_LOCK) > 0
                ? WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM;
        try (ParcelFileDescriptor source = manager.getWallpaperFile(flag)) {
            if (source != null) {
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(source.getFileDescriptor(), null, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                        || (long) bounds.outWidth * bounds.outHeight > 100_000_000)
                    throw new IOException("Unsupported wallpaper size");
                BitmapFactory.Options options = new BitmapFactory.Options();
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2;
                options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap decoded = BitmapFactory.decodeFileDescriptor(source.getFileDescriptor(), null, options);
                if (decoded == null) throw new IOException("Cannot decode current wallpaper");
                return decoded;
            }
        }
        // An untouched built-in wallpaper has no saved file. The old job silently stopped here.
        Drawable builtin = manager.getBuiltInDrawable(0, 0, true, .5f, .5f, flag);
        if (builtin == null) throw new IOException("No static wallpaper is available");
        int width = builtin.getIntrinsicWidth(), height = builtin.getIntrinsicHeight();
        if (width <= 0 || height <= 0) throw new IOException("Empty built-in wallpaper");
        float scale = Math.min(1f, 1600f / Math.max(width, height));
        Bitmap image = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        builtin.setBounds(0, 0, image.getWidth(), image.getHeight()); builtin.draw(new Canvas(image));
        return image;
    }
    private static void write(File path, Bitmap image) throws IOException {
        AtomicFile file = new AtomicFile(path); FileOutputStream stream = file.startWrite();
        try {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("Cannot encode layer");
            file.finishWrite(stream);
        } catch (IOException | RuntimeException failure) { file.failWrite(stream); throw failure; }
    }
    private static void erase(File directory) {
        File[] files = directory.listFiles(); if (files != null) for (File file : files) file.delete();
        directory.delete();
    }
    private CurrentWallpaperDepth() { }
}
