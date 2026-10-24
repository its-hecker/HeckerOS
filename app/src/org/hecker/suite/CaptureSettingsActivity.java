// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

/** Uses Android's existing WindowManager secure-surface refresh and setting observer. */
public final class CaptureSettingsActivity extends Activity {
    private static final String KEY = "disable_secure_windows";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.capture_title);
        Ui.hero(this, page, R.raw.suite_capture, R.string.capture_summary);
        LinearLayout card = Ui.card(this);
        Switch enabled = new Switch(this);
        enabled.setText(R.string.capture_secure_title);
        enabled.setTextColor(getColor(R.color.hecker_foreground));
        enabled.setMinHeight(Ui.dp(this, 56));
        enabled.setChecked(Settings.Secure.getInt(getContentResolver(), KEY, 0) != 0);
        enabled.setOnClickListener(button -> {
            boolean checked = enabled.isChecked();
            if (!Settings.Secure.putInt(getContentResolver(), KEY, checked ? 1 : 0)) {
                enabled.setChecked(!checked);
                Toast.makeText(this, R.string.native_page_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        card.addView(enabled);
        card.addView(Ui.text(this, getString(R.string.capture_secure_summary), 14, false));
        page.addView(card);
        Ui.scroll(this, page);
    }
}
