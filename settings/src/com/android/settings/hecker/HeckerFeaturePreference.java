// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import com.android.settings.R;
import com.android.settings.widget.HomepagePreference;

/** Homepage links to existing Suite pages through its signature-checked, allowlisted router. */
public final class HeckerFeaturePreference extends HomepagePreference {
    public HeckerFeaturePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnPreferenceClickListener(preference -> {
            String key = getKey();
            String page = "hecker_home_lock".equals(key) ? "LockScreenSettingsActivity"
                    : "hecker_home_themes".equals(key) ? "ThemePackActivity"
                    : "LauncherSettingsActivity";
            try {
                context.startActivity(new Intent().setClassName("org.hecker.suite",
                        "org.hecker.suite.SuiteRouterActivity").putExtra("org.hecker.suite.PAGE", page));
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                android.widget.Toast.makeText(context, R.string.hecker_suite_unavailable,
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return true;
        });
    }
}
