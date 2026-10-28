// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.view.View;
import com.android.settings.R;
import com.android.settings.overlay.FeatureFactory;
import java.util.ArrayList;

/** Uses the system speech dialog; a recognized phrase opens native Settings search. */
public final class HeckerSettingsVoiceSearch {
    private static final int REQUEST = 0x484b;
    private HeckerSettingsVoiceSearch() {}
    public static void bind(Activity activity) {
        View microphone = activity.findViewById(R.id.hecker_voice_search);
        if (microphone == null) return;
        Intent voice = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (voice.resolveActivity(activity.getPackageManager()) == null) {
            microphone.setVisibility(View.GONE);
            return;
        }
        microphone.setOnClickListener(view -> {
            try { activity.startActivityForResult(voice, REQUEST); }
            catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                microphone.setVisibility(View.GONE);
                View search = activity.findViewById(R.id.search_action_bar);
                if (search != null) search.performClick();
            }
        });
    }
    public static void result(Activity activity, int request, int result, Intent data) {
        if (request != REQUEST || result != Activity.RESULT_OK || data == null) return;
        ArrayList<String> phrases = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (phrases == null || phrases.isEmpty()) return;
        Intent search = FeatureFactory.getFeatureFactory().getSearchFeatureProvider()
                .buildSearchIntent(activity, android.app.settings.SettingsEnums.SETTINGS_HOMEPAGE)
                .putExtra(SearchManager.QUERY, phrases.get(0));
        try { activity.startActivity(search); }
        catch (android.content.ActivityNotFoundException | SecurityException unavailable) { }
    }
}
