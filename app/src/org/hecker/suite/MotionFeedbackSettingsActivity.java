// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

/** Settings for the Coral-only native companion; the existing Motion Sense page remains intact. */
public final class MotionFeedbackSettingsActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!"coral".equals(Build.DEVICE)) { finish(); return; }
        LinearLayout page = Ui.page(this, R.string.motion_feedback_title);
        LinearLayout intro = Ui.card(this);
        intro.addView(Ui.text(this, getString(R.string.motion_feedback_summary), 14, false));
        page.addView(intro);
        LinearLayout options = Ui.card(this);
        toggle(options, R.string.motion_island_title, "hecker_motion_island");
        options.addView(Ui.text(this, getString(R.string.motion_island_summary), 13, false));
        toggle(options, R.string.motion_creature_title, "hecker_motion_creature");
        options.addView(Ui.text(this, getString(R.string.motion_creature_summary), 13, false));
        page.addView(options);
        LinearLayout behavior = Ui.card(this);
        behavior.addView(Ui.text(this, getString(R.string.motion_feedback_behavior), 14, false));
        page.addView(behavior);
        Ui.scroll(this, page);
    }

    private void toggle(LinearLayout parent, int title, String key) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setTextColor(getColor(R.color.hecker_foreground));
        control.setMinHeight(Ui.dp(this, 56));
        control.setChecked(Settings.Secure.getInt(getContentResolver(), key, 1) != 0);
        control.setOnCheckedChangeListener((view, enabled) -> {
            if (!Settings.Secure.putInt(getContentResolver(), key, enabled ? 1 : 0)) {
                control.setOnCheckedChangeListener(null);
                control.setChecked(!enabled);
                Toast.makeText(this, R.string.motion_feedback_save_failed, Toast.LENGTH_LONG).show();
                recreate();
            }
        });
        parent.addView(control);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
