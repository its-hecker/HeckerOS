// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;

/** User-selected Google search. Resolve optional handlers instead of invoking private activities. */
public final class HeckerGoogleSearch {
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    public static Intent voice() {
        return new Intent(RecognizerIntent.ACTION_WEB_SEARCH).setPackage(GOOGLE);
    }
    public static Intent lens(Context context) {
        Intent deepLink = new Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GOOGLE);
        if (available(context, deepLink)) return deepLink;
        return context.getPackageManager().getLaunchIntentForPackage("com.google.ar.lens");
    }
    public static boolean available(Context context, Intent intent) {
        return intent != null && intent.resolveActivity(context.getPackageManager()) != null;
    }
    public static void launch(Context context, Intent intent) {
        if (intent == null) return;
        try { context.startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (ActivityNotFoundException | SecurityException unavailable) { search(context); }
    }
    public static void search(Context context) {
        Intent search = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).setPackage(GOOGLE);
        if (!available(context, search)) search = new Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE);
        if (!available(context, search)) search = context.getPackageManager().getLaunchIntentForPackage(GOOGLE);
        if (!available(context, search)) search = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/"));
        try { context.startActivity(search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (ActivityNotFoundException | SecurityException unavailable) {
            android.widget.Toast.makeText(context, HeckerStudio.text(context, "google_unavailable"),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    private HeckerGoogleSearch() { }
}
