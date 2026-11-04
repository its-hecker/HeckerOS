// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import java.io.IOException;
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
    static boolean extractCurrent(Context context) throws IOException {
        android.os.UserManager users = context.getSystemService(android.os.UserManager.class);
        return users != null && users.isUserUnlocked()
                && CurrentWallpaperDepth.extract(context, () -> !Thread.currentThread().isInterrupted(), true);
    }
    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        if (!enabled(context)) { jobs.cancel(JOB); return; }
        jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(context,DepthWallpaperJob.class))
                .setMinimumLatency(600).setOverrideDeadline(5000).build());
    }
    private static boolean enabled(Context context) {
        // Auto extraction is its own opt-in. A newly applied wallpaper may have
        // invalidated/disabled the old mask before this job is scheduled.
        return Settings.Secure.getInt(context.getContentResolver(),AUTO,0) == 1;
    }
    @Override public boolean onStartJob(JobParameters params) {
        int serial = ++generation;
        pending = worker.submit(() -> {
            try {
                android.os.UserManager users = getSystemService(android.os.UserManager.class);
                if (enabled(this) && users != null && users.isUserUnlocked())
                    CurrentWallpaperDepth.extract(this,
                        () -> serial == generation && enabled(this) && !Thread.currentThread().isInterrupted(), false);
            }
            catch (Exception | OutOfMemoryError failure) {
                android.util.Log.w("HeckerDepth", "No usable wallpaper subject; manual import remains available", failure);
            } finally { if (serial == generation) jobFinished(params,false); }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        generation++; if (pending != null) pending.cancel(true); return false;
    }
    @Override public void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
