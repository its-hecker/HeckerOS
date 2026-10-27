// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Current-user media session, with artwork and transport capabilities from the player. */
public final class HeckerQsMedia {
    public interface Listener { void changed(Snapshot snapshot); }
    public static final class Snapshot {
        public final String title, artist;
        public final Bitmap artwork;
        public final boolean active, playing, paused;
        public final MediaSession.Token token;
        public final String identity;
        public final int volume;
        public final long duration, position, updated, actions;
        public final float speed;
        Snapshot(MediaController controller) {
            active = controller != null;
            token = active ? controller.getSessionToken() : null;
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
            paused = state != null && state.getState() == PlaybackState.STATE_PAUSED;
            String id = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            long queue = state == null ? -1 : state.getActiveQueueItemId();
            identity = id == null && title == null && queue == -1 ? null
                    : bounded(id) + '\n' + bounded(title) + '\n' + queue;
            MediaController.PlaybackInfo info = active ? controller.getPlaybackInfo() : null;
            volume = info == null || info.getMaxVolume() <= 0 ? -1
                    : Math.max(0, Math.min(100, Math.round(100f * info.getCurrentVolume() / info.getMaxVolume())));
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
    private final boolean musicOnly;
    private final MediaSession.Token pinned;
    private final Map<MediaSession.Token, Watch> watching = new LinkedHashMap<>();
    private final Set<MediaSession.Token> destroyed = new HashSet<>();
    private MediaController controller;
    private boolean started;
    private final MediaSessionManager.OnActiveSessionsChangedListener active = ignored -> refresh();
    private final class Watch extends MediaController.Callback {
        final MediaController media;
        Watch(MediaController media) { this.media = media; }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        @Override public void onAudioInfoChanged(MediaController.PlaybackInfo info) { refresh(); }
        @Override public void onSessionDestroyed() { destroyed.add(media.getSessionToken()); refresh(); }
        void close() { try { media.unregisterCallback(this); } catch (RuntimeException ignored) { } }
    }
    private final BroadcastReceiver users = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { clear(); refresh(); }
    };
    public HeckerQsMedia(Context context, Listener listener) {
        this(context, listener, false, null);
    }
    public HeckerQsMedia(Context context, Listener listener, boolean musicOnly, MediaSession.Token pinned) {
        this.context = context; this.listener = listener;
        this.musicOnly = musicOnly; this.pinned = pinned;
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
        clear();
    }
    private void refresh() {
        if (!started || sessions == null) return;
        try {
            int user = ActivityManager.getCurrentUser();
            List<MediaController> available = sessions.getActiveSessionsForUser(null, UserHandle.of(user));
            if (user != ActivityManager.getCurrentUser()) { clear(); update(); return; }
            Set<MediaSession.Token> present = new HashSet<>();
            // Watch every candidate, including paused sessions. Membership alone does not change
            // when a different existing player starts or when a session changes audio attributes.
            for (MediaController candidate : available.subList(0, Math.min(32, available.size()))) {
                MediaSession.Token token = candidate.getSessionToken();
                present.add(token);
                if (!watching.containsKey(token) && !destroyed.contains(token)) {
                    Watch watch = new Watch(candidate);
                    candidate.registerCallback(watch, handler);
                    watching.put(token, watch);
                }
            }
            for (MediaSession.Token token : new ArrayList<>(watching.keySet()))
                if (!present.contains(token) || destroyed.contains(token)) watching.remove(token).close();
            destroyed.retainAll(present);
            MediaController selected = null;
            for (MediaController candidate : available) {
                if (!watching.containsKey(candidate.getSessionToken())
                        || pinned != null && !pinned.equals(candidate.getSessionToken())
                        || musicOnly && !music(candidate)) continue;
                if (selected == null) selected = candidate;
                if (candidate.getPlaybackState() != null && candidate.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                    selected = candidate; break;
                }
            }
            controller = selected;
            update();
        } catch (RuntimeException e) { clear(); update(); }
    }
    private void clear() {
        for (Watch watch : watching.values()) watch.close();
        watching.clear(); destroyed.clear(); controller = null;
    }
    private static boolean music(MediaController media) {
        MediaController.PlaybackInfo info = media.getPlaybackInfo();
        AudioAttributes attrs = info == null ? null : info.getAudioAttributes();
        MediaMetadata m = media.getMetadata();
        boolean album = m != null && nonempty(m.getString(MediaMetadata.METADATA_KEY_ARTIST))
                && nonempty(m.getString(MediaMetadata.METADATA_KEY_ALBUM));
        return MusicSessionPolicy.eligible(media.getPackageName(), attrs == null ? 0 : attrs.getUsage(),
                attrs == null ? 0 : attrs.getContentType(), album);
    }
    private static boolean nonempty(String value) { return value != null && !value.trim().isEmpty(); }
    private static String bounded(String value) { return value == null ? "" : value.substring(0, Math.min(192, value.length())); }
    public Snapshot snapshotFor(MediaSession.Token token) {
        Watch watch = watching.get(token);
        try { return new Snapshot(watch != null && (!musicOnly || music(watch.media)) ? watch.media : null); }
        catch (RuntimeException unavailable) { return new Snapshot(null); }
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
