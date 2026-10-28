// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import com.android.systemui.res.R;

/** Coral music capsule and finite gesture companion, owned by the native Oslo plugin host. */
public final class HeckerMotionIsland implements AutoCloseable {
    private static final String TAG = "HeckerMotionIsland";
    private static final String ACTION = "com.google.oslo.HECKER_MOTION_FEEDBACK";
    private static final String ISLAND = "hecker_motion_island", CREATURE = "hecker_motion_creature";
    private final Context context;
    private final WindowManager windows;
    private final PowerManager power;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MotionIslandState state = new MotionIslandState();
    private final HeckerQsMedia music;
    private HeckerQsMedia.Snapshot media = new HeckerQsMedia.Snapshot(null);
    private MediaSession.Token lastPlaying, feedbackToken, dialogToken;
    private long lastPlayingAt;
    private String beforeIdentity;
    private MotionIslandView capsule;
    private MotionAstronautView astronaut;
    private Dialog dialog;
    private boolean ambient = true, screenOff, closed, refreshing, actionRegistered, environmentRegistered;
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };

    public static HeckerMotionIsland create(Context context) {
        if (!"coral".equals(Build.DEVICE)) return null;
        try { return new HeckerMotionIsland(context); }
        catch (RuntimeException unavailable) {
            Log.w(TAG, "Motion Island unavailable; retaining native glow", unavailable);
            return null;
        }
    }
    private HeckerMotionIsland(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
        power = context.getSystemService(PowerManager.class);
        if (windows == null || power == null) throw new IllegalStateException("No window/power service");
        music = new HeckerQsMedia(context, snapshot -> {
            // A song may have played for minutes without a callback. Timestamp its transition
            // out of PLAYING, so a pause event arriving after that callback still gets a goodbye.
            if (media.playing) { lastPlaying = media.token; lastPlayingAt = now(); }
            media = snapshot;
            if (snapshot.playing) { lastPlaying = snapshot.token; lastPlayingAt = now(); }
            refresh();
        }, true, null);
        try {
            context.registerReceiver(actions, new IntentFilter(ACTION),
                    "android.permission.STATUS_BAR_SERVICE", main, Context.RECEIVER_EXPORTED);
            actionRegistered = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            context.registerReceiver(environment, filter, Context.RECEIVER_NOT_EXPORTED);
            environmentRegistered = true;
            for (String key : new String[]{ISLAND, CREATURE, "aware_enabled"})
                context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key),
                        false, observer, UserHandle.USER_ALL);
        } catch (RuntimeException unavailable) { close(); throw unavailable; }
    }
    // The native host still owns glow lifecycle. Music visibility is independent of glow fades.
    public void feedbackAdded(View feedback) { refresh(); }
    public void feedbackRemoved(View feedback) { refresh(); }
    public void setAmbient(boolean ambient) { this.ambient = ambient; refresh(); }

    private final BroadcastReceiver actions = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (closed || !ACTION.equals(intent.getAction()) || intent.getIntExtra("version", 0) != 1) return;
            try {
                refresh();
                MediaSession.Token token = intent.getParcelableExtra("session", MediaSession.Token.class);
                HeckerQsMedia.Snapshot target = token == null ? null : music.snapshotFor(token);
                // A pause callback can precede its command event. Only the just-playing music
                // session gets that finite farewell; arbitrary paused/video sessions cannot show it.
                boolean recent = token != null && token.equals(lastPlaying) && now() - lastPlayingAt < 3000;
                int action = intent.getIntExtra("action", 0);
                // Keep a successful PLAY request pending while its callback catches up. Visibility
                // still requires actual playback, so a rejected/delayed play cannot show an idle island.
                if (target == null || !target.active || !(target.playing || recent || action == MotionIslandState.PLAY)
                        || !state.accept(action, intent.getIntExtra("user", -1),
                                intent.getLongExtra("when", -1), now())) return;
                feedbackToken = token;
                state.direction = Math.max(-1, Math.min(1, intent.getIntExtra("direction", 0)));
                beforeIdentity = intent.getStringExtra("before_identity");
                if (beforeIdentity != null && beforeIdentity.length() > 512) beforeIdentity = null;
                main.removeCallbacks(expire);
                main.postDelayed(expire, Math.max(1, state.when + MotionIslandState.ACTION_DURATION - now()));
                refresh();
            } catch (RuntimeException malformed) {
                state.clear(); refresh();
                Log.w(TAG, "Invalid motion feedback", malformed);
            }
        }
    };
    private final BroadcastReceiver environment = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) screenOff = true;
            if (Intent.ACTION_SCREEN_ON.equals(action)) screenOff = false;
            if (Intent.ACTION_USER_SWITCHED.equals(action) || Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                state.clear(); lastPlaying = null; feedbackToken = null;
                music.stop(); media = new HeckerQsMedia.Snapshot(null);
                removeViews(); dismissDialog();
            }
            refresh();
        }
    };
    private boolean setting(String key, int fallback, int user) {
        return Settings.Secure.getIntForUser(context.getContentResolver(), key, fallback, user) != 0;
    }
    private void refresh() {
        if (closed || refreshing) return;
        refreshing = true;
        try {
            int user = ActivityManager.getCurrentUser();
            boolean awake = !ambient && !screenOff && power.isInteractive();
            boolean enabled = setting(ISLAND, 1, user) && setting("aware_enabled", 0, user);
            if (awake && enabled) music.start();
            else {
                music.stop(); media = new HeckerQsMedia.Snapshot(null); lastPlaying = null;
                dismissDialog();
            }
            state.environment(enabled, setting(CREATURE, 1, user), awake, media.playing, user);
            if (!media.active) { state.clear(); feedbackToken = null; }
            if (dialog != null && (!awake || !enabled || dialogToken == null
                    || !music.snapshotFor(dialogToken).active)) dismissDialog();
            if (feedbackToken != null && state.current(now()) != MotionIslandState.NONE) {
                HeckerQsMedia.Snapshot result = music.snapshotFor(feedbackToken);
                boolean confirmed = state.confirmed || state.action == MotionIslandState.PLAY && result.playing
                        || state.action == MotionIslandState.PAUSE && result.paused;
                if (state.action == MotionIslandState.NEXT || state.action == MotionIslandState.PREVIOUS)
                    confirmed |= beforeIdentity != null && result.identity != null && !beforeIdentity.equals(result.identity);
                state.update(state.sequence, confirmed, result.volume);
            }
            render();
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "Motion Island refresh unavailable", unavailable); close();
        } finally { refreshing = false; }
    }
    private void render() {
        if (!state.visible(now())) { removeViews(); return; }
        boolean animate = ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode();
        if (capsule == null) {
            MotionIslandView view = new MotionIslandView(context);
            view.setOnClickListener(v -> openPlayer());
            if (!add(view, dp(148), dp(32), 0, false, "HeckerMotionIsland")) return;
            capsule = view;
        }
        String label = state.current(now()) == MotionIslandState.NONE
                ? (media.title == null ? context.getString(R.string.hecker_motion_playing) : media.title) : label();
        capsule.update(media, label, animate);
        if (state.creatureVisible(now()) && dialog == null) {
            if (astronaut == null) {
                MotionAstronautView view = new MotionAstronautView(context);
                // Match MotionIslandView's painted bottom, including density rounding.
                if (add(view, dp(224), dp(150), dp(32) - dp(3), true, "HeckerMotionAstronaut")) astronaut = view;
            }
            if (astronaut != null) astronaut.update(state, animate);
        } else removeAstronaut();
    }
    private boolean add(View view, int width, int height, int y, boolean decoration, String title) {
        int available = windows.getCurrentWindowMetrics().getBounds().width();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(Math.min(width, available - dp(24)),
                height, 2041, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | (decoration ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : 0), PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; params.y = y;
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        params.setTitle(title);
        try {
            WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(params);
            windows.addView(view, params); return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Log.w(TAG, "Motion Island window unavailable", unavailable); return false;
        }
    }
    private void openPlayer() {
        if (dialog != null || !state.visible(now()) || media.token == null) return;
        try {
            dialogToken = media.token;
            dialog = HeckerMotionMediaDialog.show(context, dialogToken, () -> {
                dialog = null; dialogToken = null; refresh();
            });
            removeAstronaut();
        } catch (RuntimeException unavailable) {
            dialogToken = null; Log.w(TAG, "Media card unavailable", unavailable);
        }
    }
    private void dismissDialog() {
        Dialog old = dialog; dialog = null; dialogToken = null;
        if (old != null) old.dismiss();
    }
    private String label() {
        switch (state.current(now())) {
            case MotionIslandState.PLAY: return context.getString(state.confirmed ? R.string.hecker_motion_playing : R.string.hecker_motion_play);
            case MotionIslandState.PAUSE: return context.getString(state.confirmed ? R.string.hecker_motion_paused : R.string.hecker_motion_pause);
            case MotionIslandState.NEXT: return context.getString(state.confirmed ? R.string.hecker_motion_skipped : R.string.hecker_motion_next);
            case MotionIslandState.PREVIOUS: return context.getString(state.confirmed ? R.string.hecker_motion_previous_done : R.string.hecker_motion_previous);
            case MotionIslandState.VOLUME_UP:
            case MotionIslandState.VOLUME_DOWN:
                return state.volume >= 0 ? context.getString(R.string.hecker_motion_volume, state.volume)
                        : context.getString(state.action == MotionIslandState.VOLUME_UP ? R.string.hecker_motion_volume_up : R.string.hecker_motion_volume_down);
            case MotionIslandState.SEEK_FORWARD: return context.getString(R.string.hecker_motion_forward);
            case MotionIslandState.SEEK_BACK: return context.getString(R.string.hecker_motion_rewind);
            case MotionIslandState.MODE_TRACK: return context.getString(R.string.hecker_motion_track_mode);
            case MotionIslandState.MODE_VOLUME: return context.getString(R.string.hecker_motion_volume_mode);
            case MotionIslandState.MODE_SEEK: return context.getString(R.string.hecker_motion_seek_mode);
            default: return context.getString(R.string.hecker_motion_playing);
        }
    }
    private final Runnable expire = () -> { state.clear(); feedbackToken = null; refresh(); };
    private void removeAstronaut() {
        if (astronaut == null) return;
        astronaut.stop(); remove(astronaut); astronaut = null;
    }
    private void removeViews() {
        removeAstronaut();
        if (capsule != null) { capsule.stop(); remove(capsule); capsule = null; }
    }
    private void remove(View view) {
        try { windows.removeViewImmediate(view); } catch (IllegalArgumentException alreadyRemoved) { }
    }
    private int dp(float n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    @Override public void close() {
        if (closed) return;
        closed = true; main.removeCallbacksAndMessages(null);
        music.stop(); dismissDialog(); removeViews();
        context.getContentResolver().unregisterContentObserver(observer);
        if (actionRegistered) context.unregisterReceiver(actions);
        if (environmentRegistered) context.unregisterReceiver(environment);
    }
    private static long now() { return SystemClock.elapsedRealtime(); }
}
