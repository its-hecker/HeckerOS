// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;
import java.util.List;

/** Current-user media session, with artwork and transport capabilities from the player. */
public final class HeckerQsMedia {
    public interface Listener { void changed(Snapshot snapshot); }
    public static final class Snapshot {
        public final String title, artist;
        public final Bitmap artwork;
        public final boolean active, playing;
        public final long duration, position, updated, actions;
        public final float speed;
        Snapshot(MediaController controller) {
            active = controller != null;
            MediaMetadata m = active ? controller.getMetadata() : null;
            PlaybackState state = active ? controller.getPlaybackState() : null;
            title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
            artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            Bitmap art = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null && m != null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null && m != null) art = m.getDescription().getIconBitmap();
            artwork = art;
            duration = m == null ? 0 : Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
            position = state == null ? 0 : Math.max(0, state.getPosition());
            updated = state == null ? 0 : state.getLastPositionUpdateTime();
            speed = state == null ? 0 : state.getPlaybackSpeed();
            actions = state == null ? 0 : state.getActions();
            playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        }
        public long positionNow() {
            long value = position + (playing && updated > 0
                    ? (long) ((SystemClock.elapsedRealtime() - updated) * speed) : 0);
            return Math.max(0, duration > 0 ? Math.min(duration, value) : value);
        }
        public boolean supports(long action) { return (actions & action) != 0; }
    }
    private final Context context;
    private final MediaSessionManager sessions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private MediaController controller;
    private boolean started;
    private final MediaSessionManager.OnActiveSessionsChangedListener active = ignored -> refresh();
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { update(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { update(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };
    private final BroadcastReceiver users = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { bind(null); refresh(); }
    };
    public HeckerQsMedia(Context context, Listener listener) {
        this.context = context; this.listener = listener;
        sessions = context.getSystemService(MediaSessionManager.class);
    }
    public void start() {
        if (started) return;
        started = true;
        context.registerReceiver(users, new IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED);
        if (sessions != null) try {
            sessions.addOnActiveSessionsChangedListener(null, UserHandle.ALL, context.getMainExecutor(), active);
        } catch (RuntimeException e) { Log.w("HeckerQsMedia", "Session listener unavailable", e); }
        refresh();
    }
    public void stop() {
        if (!started) return;
        started = false; context.unregisterReceiver(users);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(active);
        bind(null);
    }
    private void refresh() {
        if (!started || sessions == null) return;
        try {
            List<MediaController> available = sessions.getActiveSessionsForUser(null, UserHandle.of(ActivityManager.getCurrentUser()));
            MediaController selected = available.isEmpty() ? null : available.get(0);
            for (MediaController candidate : available) {
                if (candidate.getPlaybackState() != null && candidate.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                    selected = candidate; break;
                }
            }
            if (controller != null && selected != null && controller.getSessionToken().equals(selected.getSessionToken())) update();
            else bind(selected);
        } catch (RuntimeException e) { bind(null); }
    }
    private void bind(MediaController next) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = next;
        if (controller != null) controller.registerCallback(callback, handler);
        update();
    }
    private void update() { listener.changed(new Snapshot(controller)); }
    public void playPause() {
        try {
            if (controller != null) {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) controller.getTransportControls().pause();
                else controller.getTransportControls().play();
            } else {
                AudioManager audio = context.getSystemService(AudioManager.class);
                audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
                audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
            }
        } catch (RuntimeException e) { Log.w("HeckerQsMedia", "Playback unavailable", e); }
    }
    public void previous() { transport(PlaybackState.ACTION_SKIP_TO_PREVIOUS, 0); }
    public void next() { transport(PlaybackState.ACTION_SKIP_TO_NEXT, 0); }
    public void seek(long position) { transport(PlaybackState.ACTION_SEEK_TO, position); }
    private void transport(long action, long position) {
        try {
            if (controller == null || controller.getPlaybackState() == null || (controller.getPlaybackState().getActions() & action) == 0) return;
            if (action == PlaybackState.ACTION_SKIP_TO_PREVIOUS) controller.getTransportControls().skipToPrevious();
            else if (action == PlaybackState.ACTION_SKIP_TO_NEXT) controller.getTransportControls().skipToNext();
            else controller.getTransportControls().seekTo(Math.max(0, position));
        } catch (RuntimeException e) { Log.w("HeckerQsMedia", "Transport unavailable", e); }
    }
    public void output() {
        String action = controller == null ? "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG" : "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG";
        Intent intent = new Intent(action).setPackage(context.getPackageName()).putExtra("user_handle", UserHandle.of(ActivityManager.getCurrentUser()));
        if (controller != null) intent.putExtra("package_name", controller.getPackageName()).putExtra("key_media_session_token", controller.getSessionToken());
        context.sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }
}
