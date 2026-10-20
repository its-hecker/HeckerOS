// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;
import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.widget.Toast;
final class SuitePages {
    static void settings(Activity activity, String action) {
        try { activity.startActivity(new Intent(action).setPackage("com.android.settings")); }
        catch (ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(activity, R.string.native_page_unavailable, Toast.LENGTH_LONG).show(); }
    }
    static void wallpaper(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(activity, R.string.native_page_unavailable, Toast.LENGTH_LONG).show(); }
    }
    static void theme(Activity activity, int category) {
        activity.startActivity(new Intent(activity, ThemePackActivity.class).putExtra("category", category));
    }
}
