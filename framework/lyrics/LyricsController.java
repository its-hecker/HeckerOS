/*
 * Copyright (C) 2026 Project Infinity X
 * Copyright (C) 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Native PixelOS adapter for Infinity's LRCLIB lookup and recording matching.
 */
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Current-user lyrics while the lock-screen root is composed, including ambient display. */
public final class LyricsController implements AutoCloseable {
    public static final String SETTING = "hecker_lockscreen_lyrics";
    public static final class Snapshot {
        public final List<LyricsPolicy.Line> lines;
        public final long position, updatedAt, duration;
        public final float speed;
        public final boolean playing;
        Snapshot(List<LyricsPolicy.Line> lines, PlaybackState state, long duration) {
            this.lines = lines;
            this.position = state == null ? 0 : state.getPosition();
            this.updatedAt = state == null ? 0 : state.getLastPositionUpdateTime();
            this.speed = state == null ? 0 : state.getPlaybackSpeed();
            this.playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            this.duration = duration;
        }
        public int activeIndex(long realtime) {
            return LyricsPolicy.activeIndex(lines, LyricsPolicy.position(position, updatedAt,
                    realtime, speed, playing, duration));
        }
    }
    private static final class Cached {
        final List<LyricsPolicy.Line> lines;
        final long at = SystemClock.elapsedRealtime();
        Cached(List<LyricsPolicy.Line> lines) { this.lines = lines; }
    }
    private static final LruCache<String, Cached> CACHE = new LruCache<>(24);
    private final Context context;
    private final Consumer<Snapshot> listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1), runnable -> new Thread(runnable, "HeckerLyrics"));
    private final ContentObserver setting = new ContentObserver(main) {
        @Override public void onChange(boolean self) { refreshSessions(); }
    };
    private final BroadcastReceiver users = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { bindUser(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::chooseSession;
    private final MediaController.Callback callbacks = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { updateTrack(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { publish(); }
        @Override public void onSessionDestroyed() { refreshSessions(); }
    };
    private MediaSessionManager manager;
    private MediaController controller;
    private List<LyricsPolicy.Line> lines = Collections.emptyList();
    private String trackKey = "";
    private int userId, generation;
    private Future<?> request;
    private volatile HttpURLConnection connection;
    private boolean closed;

    public LyricsController(Context context, Consumer<Snapshot> listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(SETTING),
                false, setting, UserHandle.USER_ALL);
        this.context.registerReceiver(users, new IntentFilter(Intent.ACTION_USER_SWITCHED),
                null, main, Context.RECEIVER_NOT_EXPORTED);
        bindUser();
    }

    private boolean enabled() {
        return !closed && Settings.Secure.getIntForUser(context.getContentResolver(),
                SETTING, 0, userId) == 1;
    }

    private void bindUser() {
        if (manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        detach();
        userId = ActivityManager.getCurrentUser();
        manager = context.createContextAsUser(UserHandle.of(userId), 0)
                .getSystemService(MediaSessionManager.class);
        refreshSessions();
    }

    private void refreshSessions() {
        if (closed || manager == null) return;
        manager.removeOnActiveSessionsChangedListener(sessions);
        if (!enabled()) { detach(); return; }
        try {
            manager.addOnActiveSessionsChangedListener(sessions, null, main);
            chooseSession(manager.getActiveSessions(null));
        } catch (SecurityException unavailable) { detach(); }
    }

    private void chooseSession(List<MediaController> available) {
        if (!enabled()) { detach(); return; }
        MediaController chosen = null;
        if (available != null) {
            for (MediaController item : available) {
                PlaybackState state = item.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) { chosen = item; break; }
            }
        }
        if (chosen == null) { detach(); return; }
        if (controller == null || !controller.getSessionToken().equals(chosen.getSessionToken())) {
            detach();
            controller = chosen;
            controller.registerCallback(callbacks, main);
        }
        updateTrack();
    }

    private void updateTrack() {
        if (!enabled() || controller == null) { detach(); return; }
        MediaMetadata metadata = controller.getMetadata();
        String title = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        long duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (title == null || artist == null || title.isBlank() || artist.isBlank()) {
            cancel(); trackKey = ""; lines = Collections.emptyList(); publish(); return;
        }
        String key = userId + ":" + controller.getPackageName() + ":"
                + LyricsPolicy.normalize(artist) + ":" + LyricsPolicy.normalize(title) + ":" + duration;
        if (key.equals(trackKey)) { publish(); return; }
        cancel(); trackKey = key; lines = Collections.emptyList(); publish();
        Cached cached = CACHE.get(key);
        if (cached != null && (!cached.lines.isEmpty() || SystemClock.elapsedRealtime() - cached.at < 300_000)) {
            lines = cached.lines; publish(); return;
        }
        int serial = generation;
        request = worker.submit(() -> {
            List<LyricsPolicy.Line> found = lookup(artist, title, duration);
            main.post(() -> {
                if (closed || serial != generation || !enabled() || !key.equals(trackKey)) return;
                CACHE.put(key, new Cached(found)); lines = found; publish();
            });
        });
    }

    private List<LyricsPolicy.Line> lookup(String artist, String title, long duration) {
        HttpURLConnection http = null;
        try {
            String query = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);
            http = (HttpURLConnection) new URL("https://lrclib.net/api/search?q=" + query).openConnection();
            connection = http;
            http.setConnectTimeout(5000); http.setReadTimeout(5000); http.setInstanceFollowRedirects(false);
            http.setRequestProperty("User-Agent", "HeckerOS-LockscreenLyrics/1.0 (https://github.com/its-hecker/HeckerOS)");
            if (http.getResponseCode() != 200 || Thread.currentThread().isInterrupted()) return Collections.emptyList();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream stream = http.getInputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = stream.read(buffer)) != -1;) {
                    if (output.size() + count > 1_048_576 || Thread.currentThread().isInterrupted()) return Collections.emptyList();
                    output.write(buffer, 0, count);
                }
            }
            JSONArray results = new JSONArray(output.toString(StandardCharsets.UTF_8));
            int bestScore = -1;
            List<LyricsPolicy.Line> best = Collections.emptyList();
            for (int i = 0; i < Math.min(results.length(), 10); i++) {
                JSONObject candidate = results.getJSONObject(i);
                int score = LyricsPolicy.matchScore(artist, title, candidate.optString("artistName"),
                        candidate.optString("trackName"));
                long candidateDuration = Math.round(candidate.optDouble("duration", 0) * 1000);
                if (!LyricsPolicy.usableRecording(score, duration, candidateDuration)
                        || candidate.optBoolean("instrumental") || score <= bestScore) continue;
                List<LyricsPolicy.Line> parsed = LyricsPolicy.parse(candidate.optString("syncedLyrics", ""));
                if (!parsed.isEmpty()) { best = parsed; bestScore = score; }
            }
            return best;
        } catch (Exception unavailable) { return Collections.emptyList(); }
        finally { if (http != null) http.disconnect(); if (connection == http) connection = null; }
    }

    private void publish() {
        if (closed) return;
        MediaMetadata metadata = controller == null ? null : controller.getMetadata();
        listener.accept(new Snapshot(enabled() ? lines : Collections.emptyList(),
                controller == null ? null : controller.getPlaybackState(),
                metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)));
    }

    private void cancel() {
        generation++;
        if (request != null) request.cancel(true);
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
        worker.getQueue().clear();
    }

    private void detach() {
        cancel();
        if (controller != null) controller.unregisterCallback(callbacks);
        controller = null; trackKey = ""; lines = Collections.emptyList(); publish();
    }

    @Override public void close() {
        closed = true; detach();
        if (manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        context.getContentResolver().unregisterContentObserver(setting);
        context.unregisterReceiver(users);
        worker.shutdownNow();
    }
}
