// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import java.util.Random;

/** Offline messages, chosen once per visit and preserved across rotation. */
final class SuiteGreetings {
    private SuiteGreetings() {}

    static int next(Context context) {
        int count = context.getResources().getStringArray(R.array.suite_greetings).length;
        android.content.SharedPreferences prefs = context.getSharedPreferences("greetings", 0);
        int previous = prefs.getInt("last", -1);
        int index = previous < 0 || previous >= count ? new Random().nextInt(count)
                : count == 1 ? 0 : (previous + 1 + new Random().nextInt(count - 1)) % count;
        prefs.edit().putInt("last", index).apply();
        return index;
    }

    static String text(Context context, int index) {
        String[] messages = context.getResources().getStringArray(R.array.suite_greetings);
        return messages[Math.floorMod(index, messages.length)];
    }
}
