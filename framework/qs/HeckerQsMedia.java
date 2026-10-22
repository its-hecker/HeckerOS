// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;
import java.util.List;

/** Current-user sessions only. The reference media strip never invents track metadata. */
public final class HeckerQsMedia {
    public interface Listener { void changed(String title, boolean playing); }
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
        this.context = context;
        this.listener = listener;
        sessions = context.getSystemService(MediaSessionManager.class);
    }

    public void start() {
        if (started) return;
        started = true;
        context.registerReceiver(users, new IntentFilter(Intent.ACTION_USER_SWITCHED),
                Context.RECEIVER_NOT_EXPORTED);
        if (sessions != null) {
            try { sessions.addOnActiveSessionsChangedListener(null, UserHandle.ALL,
                    context.getMainExecutor(), active); }
            catch (RuntimeException unavailable) { Log.w("HeckerQsMedia", "Session listener unavailable", unavailable); }
        }
        refresh();
    }

    public void stop() {
        if (!started) return;
        started = false;
        context.unregisterReceiver(users);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(active);
        bind(null);
    }

    private void refresh() {
        if (!started || sessions == null) return;
        try {
            List<MediaController> available = sessions.getActiveSessionsForUser(null,
                    UserHandle.of(ActivityManager.getCurrentUser()));
            MediaController selected = available.isEmpty() ? null : available.get(0);
            for (MediaController candidate : available) {
                if (candidate.getPlaybackState() != null
                        && candidate.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                    selected = candidate; break;
                }
            }
            bind(selected);
        } catch (RuntimeException unavailable) { bind(null); }
    }

    private void bind(MediaController next) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = next;
        if (controller != null) controller.registerCallback(callback, handler);
        update();
    }

    private void update() {
        MediaMetadata metadata = controller == null ? null : controller.getMetadata();
        String title = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        listener.changed(title, state != null && state.getState() == PlaybackState.STATE_PLAYING);
    }

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
        } catch (RuntimeException unavailable) { Log.w("HeckerQsMedia", "Playback unavailable", unavailable); }
    }

    public void output() {
        String action = controller == null
                ? "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"
                : "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG";
        Intent intent = new Intent(action).setPackage(context.getPackageName())
                .putExtra("user_handle", UserHandle.of(ActivityManager.getCurrentUser()));
        if (controller != null) intent.putExtra("package_name", controller.getPackageName())
                .putExtra("key_media_session_token", controller.getSessionToken());
        context.sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }
}
