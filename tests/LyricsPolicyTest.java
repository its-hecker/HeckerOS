// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;
import java.util.List;
public final class LyricsPolicyTest {
    private static int checks;
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Lyric check " + checks); checks++; }
    public static void main(String[] args) {
        List<LyricsPolicy.Line> lines = LyricsPolicy.parse("[ar:Artist]\n[offset:-250]\n[00:02.50][00:08.125]Again\n[00:01.2]First\n[00:05.000]\n");
        check(lines.size() == 4);
        check(lines.get(0).timeMs == 950 && lines.get(0).text.equals("First"));
        check(lines.get(1).timeMs == 2250 && lines.get(1).text.equals("Again"));
        check(lines.get(2).text.isEmpty());
        check(lines.get(3).timeMs == 7875);
        check(LyricsPolicy.activeIndex(lines, 949) == -1);
        check(LyricsPolicy.activeIndex(lines, 2250) == 1);
        check(LyricsPolicy.activeIndex(lines, 5000) == 2);
        check(LyricsPolicy.activeIndex(lines, 99999) == 3);
        check(LyricsPolicy.parse("[00:99]Invalid").isEmpty());
        check(LyricsPolicy.parse(null).isEmpty());
        check(LyricsPolicy.parse("x".repeat(200001)).isEmpty());
        check(LyricsPolicy.position(1000, 2000, 3000, 1.5f, true, 0) == 2500);
        check(LyricsPolicy.position(1000, 2000, 3000, 1.5f, false, 0) == 1000);
        check(LyricsPolicy.position(1000, 4000, 3000, 1, true, 0) == 1000);
        check(LyricsPolicy.position(1000, 2000, 9000, 1, true, 5000) == 5000);
        check(LyricsPolicy.position(-1, 0, 0, 1, false, 0) == 0);
        check(LyricsPolicy.matchScore("ARTIST", "Song!", "Artist", "song") == 100);
        check(LyricsPolicy.matchScore("Artist", "Song", "Artist & Guest", "Song") == 90);
        check(LyricsPolicy.matchScore("Artist", "Song", "Artist", "Song Live") == 80);
        check(LyricsPolicy.matchScore("Artist", "Song", "Other", "Song") == 0);
        check(LyricsPolicy.matchScore("", "Song", "Artist", "Song") == 0);
        check(LyricsPolicy.usableRecording(100, 100000, 104999));
        check(!LyricsPolicy.usableRecording(100, 100000, 110000));
        check(!LyricsPolicy.usableRecording(60, 100000, 100000));
        check(!LyricsPolicy.usableRecording(80, 0, 100000));
        check(LyricsPolicy.usableRecording(100, 0, 100000));
        System.out.println("LRC timing, seek/speed and recording matching: " + checks + " checks passed");
    }
}
