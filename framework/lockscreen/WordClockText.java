// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Exact minute wording for the reference's English word clock; independent of rendering. */
public final class WordClockText {
    private static final String[] SMALL = {"ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE",
            "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE", "THIRTEEN",
            "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN"};
    private WordClockText() {}
    public static String[] lines(int hour, int minute) {
        hour = Math.floorMod(hour, 24);
        minute = Math.floorMod(minute, 60);
        int h = minute > 30 ? hour + 1 : hour;
        String name = SMALL[(h + 11) % 12 + 1];
        if (minute == 0) return new String[]{"IT’S", name, "O’CLOCK"};
        String amount = minute == 15 || minute == 45 ? "QUARTER"
                : minute == 30 ? "HALF" : number(Math.min(minute, 60 - minute));
        return new String[]{"IT’S", amount, minute > 30 ? "TO" : "PAST", name};
    }
    private static String number(int n) {
        return n < 20 ? SMALL[n] : n == 20 ? "TWENTY" : "TWENTY " + SMALL[n - 20];
    }
}
