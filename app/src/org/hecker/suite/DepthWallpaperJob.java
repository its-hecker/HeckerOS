// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.WallpaperManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Debounced wallpaper work, scoped to this Android user's Suite UID and cancelled on changes. */
public final class DepthWallpaperJob extends JobService {
    static final String AUTO = "hecker_depth_auto_extract";
    private static final int JOB = 0x484450;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile int generation;
    private Future<?> pending;
    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        if (!enabled(context)) { jobs.cancel(JOB); return; }
        jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(context,DepthWallpaperJob.class))
                .setMinimumLatency(600).setOverrideDeadline(5000).build());
    }
    private static boolean enabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),AUTO,0) == 1
                && Settings.Secure.getInt(context.getContentResolver(),"hecker_depth_wallpaper",0) == 1;
    }
    @Override public boolean onStartJob(JobParameters params) {
        int serial = ++generation;
        pending = worker.submit(() -> {
            try { extract(serial); }
            catch (Exception | OutOfMemoryError failure) {
                android.util.Log.w("HeckerDepth", "No usable wallpaper subject; manual import remains available", failure);
            } finally { if (serial == generation) jobFinished(params,false); }
        });
        return true;
    }
    private boolean current(int serial, int wallpaper) {
        return serial == generation && !Thread.currentThread().isInterrupted() && enabled(this)
                && wallpaper == DefaultAppearance.wallpaperId(this);
    }
    private void extract(int serial) throws IOException {
        if (!enabled(this) || !getSystemService(android.os.UserManager.class).isUserUnlocked()) return;
        WallpaperManager manager = WallpaperManager.getInstance(this);
        int wallpaper = DefaultAppearance.wallpaperId(this);
        if (wallpaper <= 0 || manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK) != null) return;
        String active = Settings.Secure.getString(getContentResolver(),DepthWallpaperProvider.ID);
        if (active != null && wallpaper == Settings.Secure.getInt(getContentResolver(),DefaultAppearance.WALLPAPER_ID,-1)) return;
        Bitmap background;
        try (ParcelFileDescriptor lock = manager.getWallpaperFile(WallpaperManager.FLAG_LOCK);
                ParcelFileDescriptor home = lock == null ? manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) : null) {
            ParcelFileDescriptor source = lock != null ? lock : home;
            if (source == null) return;
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(source.getFileDescriptor(),null,bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long)bounds.outWidth*bounds.outHeight > 100_000_000) return;
            BitmapFactory.Options options = new BitmapFactory.Options();
            int sample = 1; while (Math.max(bounds.outWidth,bounds.outHeight)/sample > 1600) sample *= 2;
            options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            background = BitmapFactory.decodeFileDescriptor(source.getFileDescriptor(),null,options);
        }
        if (background == null) return;
        Bitmap foreground = null; File directory = null; boolean committed = false;
        try {
            if (!current(serial,wallpaper)) return;
            foreground = DepthSubjectExtractor.extract(this,background);
            if (!current(serial,wallpaper)) return;
            String id = UUID.randomUUID().toString().replace("-","");
            directory = new File(new File(getFilesDir(),"depth"),id);
            if (!directory.mkdirs()) throw new IOException("Cannot stage depth layers");
            write(new File(directory,"background.png"),background); write(new File(directory,"foreground.png"),foreground);
            if (!current(serial,wallpaper)) return;
            // Change the identity while the wallpaper mismatch hides it, then publish its wallpaper ID.
            if (!Settings.Secure.putString(getContentResolver(),DepthWallpaperProvider.ID,id)
                    || !Settings.Secure.putInt(getContentResolver(),DefaultAppearance.WALLPAPER_ID,wallpaper)) throw new IOException("Cannot publish pair");
            committed = true;
            String home = Settings.Secure.getString(getContentResolver(),HomeAppearance.KEY);
            File[] previous = directory.getParentFile().listFiles();
            if (previous != null) for (File old : previous) {
                if (!old.equals(directory) && old.getName().matches("[a-f0-9]{32}") && !old.getName().equals(home)) {
                    File[] files = old.listFiles(); if (files != null) for (File file : files) file.delete(); old.delete();
                }
            }
            if (Settings.Secure.getInt(getContentResolver(),"hecker_custom_lockscreen_clock",0) == 0)
                Settings.Secure.putInt(getContentResolver(),"hecker_custom_lockscreen_clock",1);
            if (Settings.Secure.getInt(getContentResolver(),"hecker_lockscreen_clock_style",0) < 2)
                Settings.Secure.putInt(getContentResolver(),"hecker_lockscreen_clock_style",3);
        } finally {
            background.recycle(); if (foreground != null) foreground.recycle();
            if (!committed && directory != null) { File[] files = directory.listFiles(); if(files!=null) for (File f : files) f.delete(); directory.delete(); }
        }
    }
    private static void write(File path, Bitmap image) throws IOException {
        AtomicFile file = new AtomicFile(path); FileOutputStream stream = file.startWrite();
        try { if (!image.compress(Bitmap.CompressFormat.PNG,100,stream)) throw new IOException("Cannot encode layer"); file.finishWrite(stream); }
        catch (IOException | RuntimeException failure) { file.failWrite(stream); throw failure; }
    }
    @Override public boolean onStopJob(JobParameters params) {
        generation++; if (pending != null) pending.cancel(true); return false;
    }
    @Override public void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
