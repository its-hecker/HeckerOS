/*
 * Copyright (C) 2026 Project Infinity X
 * Copyright (C) 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from Infinity's MediaControlChipInteractor and LyricsCard.
 */
package com.android.systemui.hecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing, recording matching and playback math, independent of Android/UI. */
public final class LyricsPolicy {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\s*\\[offset:([+-]?\\d{1,7})\\]\\s*$");
    public static final class Line {
        public final long timeMs;
        public final String text;
        Line(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }
    private LyricsPolicy() {}

    public static List<Line> parse(String lrc) {
        if (lrc == null || lrc.length() > 200_000) return Collections.emptyList();
        long offset = 0;
        Matcher adjustment = OFFSET.matcher(lrc);
        while (adjustment.find()) offset = Long.parseLong(adjustment.group(1));
        List<Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n", 4000)) {
            String row = raw.trim();
            Matcher matcher = TIME.matcher(row);
            List<Long> timestamps = new ArrayList<>();
            int end = 0;
            while (matcher.find(end) && matcher.start() == end) {
                long minutes = Long.parseLong(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                if (seconds >= 60) break;
                String fraction = matcher.group(3);
                int millis = fraction == null ? 0 : Integer.parseInt(fraction)
                        * (fraction.length() == 1 ? 100 : fraction.length() == 2 ? 10 : 1);
                timestamps.add(Math.max(0, (minutes * 60 + seconds) * 1000 + millis + offset));
                end = matcher.end();
                if (end == row.length()) break;
            }
            String text = row.substring(end).trim();
            // An empty timestamped line ends the preceding lyric during an instrumental gap.
            for (long timestamp : timestamps) {
                lines.add(new Line(timestamp, text));
                if (lines.size() >= 2000) break;
            }
            if (lines.size() >= 2000) break;
        }
        lines.sort(Comparator.comparingLong(line -> line.timeMs));
        return Collections.unmodifiableList(lines);
    }

    public static int activeIndex(List<Line> lines, long position) {
        int low = 0, high = lines.size() - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= position) { found = mid; low = mid + 1; }
            else high = mid - 1;
        }
        return found;
    }

    public static long position(long base, long updated, long now, float speed,
            boolean playing, long duration) {
        double delta = playing && updated > 0 ? Math.max(0, now - updated) * (double) speed : 0;
        long value = Math.max(0, (long) Math.min(Long.MAX_VALUE, Math.max(0, base) + delta));
        return duration > 0 ? Math.min(duration, value) : value;
    }

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").replaceAll("\\s+", " ").trim();
    }

    public static int matchScore(String artist, String title, String resultArtist, String resultTitle) {
        String a = normalize(artist), t = normalize(title);
        String ra = normalize(resultArtist), rt = normalize(resultTitle);
        if (a.isEmpty() || t.isEmpty()) return 0;
        if (ra.equals(a) && rt.equals(t)) return 100;
        if (rt.equals(t) && ra.contains(a)) return 90;
        if (ra.equals(a) && rt.contains(t)) return 80;
        if (ra.contains(a) && rt.contains(t)) return 60;
        return 0;
    }

    public static boolean usableRecording(int score, long duration, long resultDuration) {
        if (score < 80) return false;
        return duration > 0 && resultDuration > 0
                ? Math.abs(duration - resultDuration) <= 5000 : score >= 90;
    }
}
