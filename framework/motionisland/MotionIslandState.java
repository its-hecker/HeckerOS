// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** User-scoped event/lifetime policy. No sensor subscriptions or media commands. */
final class MotionIslandState {
    static final int NONE = 0, PLAY = 1, PAUSE = 2, NEXT = 3, PREVIOUS = 4,
            VOLUME_UP = 5, VOLUME_DOWN = 6, SEEK_FORWARD = 7, SEEK_BACK = 8,
            MODE_TRACK = 9, MODE_VOLUME = 10, MODE_SEEK = 11;
    static final long MAX_EVENT_AGE = 1200, ACTION_DURATION = 2400;
    boolean island, creature, awake, music, confirmed;
    int user = -1, action, volume = -1, direction;
    long sequence, when = -1, lastEvent = -1;

    void environment(boolean island, boolean creature, boolean awake, boolean music, int user) {
        if (this.user != user || !awake || !island) {
            clear();
            if (this.user != user) lastEvent = -1;
        }
        this.island = island; this.creature = creature;
        this.awake = awake; this.music = music; this.user = user;
    }

    boolean accept(int action, int user, long when, long now) {
        if (!awake || !island || this.user != user || action < PLAY
                || action > MODE_SEEK || when < 0 || when > now
                || now - when > MAX_EVENT_AGE || when <= lastEvent) return false;
        this.action = action; this.when = when; lastEvent = when;
        confirmed = false; volume = -1; sequence++;
        return true;
    }

    int current(long now) {
        if (when < 0 || now < when || now - when >= ACTION_DURATION) return NONE;
        return action;
    }

    boolean visible(long now) { return awake && island && (music || farewell(now)); }
    boolean farewell(long now) { return action == PAUSE && current(now) == PAUSE; }
    boolean creatureVisible(long now) { return creature && visible(now) && current(now) != NONE; }
    boolean update(long sequence, boolean confirmed, int volume) {
        if (this.sequence != sequence || action == NONE) return false;
        this.confirmed = confirmed;
        this.volume = volume >= 0 && volume <= 100 ? volume : -1;
        return true;
    }

    static int volumePercent(int current, int max) {
        if (max <= 0 || current < 0 || current > max) return -1;
        return (int) Math.round(100.0 * current / max);
    }

    void clear() { action = NONE; when = -1; confirmed = false; volume = -1; direction = 0; sequence++; }
}
