// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

/** Advanced options: screen-capture compatibility and per-app signature test configuration. */
public final class CaptureSettingsActivity extends Activity {
    private static final String SECURE_WINDOWS_KEY = "disable_secure_windows";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.capture_title);
        Ui.hero(this, page, R.raw.suite_capture, R.string.capture_summary);

        LinearLayout screenshots = Ui.card(this);
        Switch capture = new Switch(this);
        capture.setText(R.string.capture_secure_title);
        capture.setTextColor(getColor(R.color.hecker_foreground));
        capture.setMinHeight(Ui.dp(this, 56));
        capture.setChecked(Settings.Secure.getInt(
                getContentResolver(), SECURE_WINDOWS_KEY, 0) != 0);
        capture.setOnClickListener(button -> {
            boolean checked = capture.isChecked();
            try {
                if (!Settings.Secure.putInt(getContentResolver(),
                        SECURE_WINDOWS_KEY, checked ? 1 : 0)) {
                    capture.setChecked(!checked);
                    Toast.makeText(this, R.string.native_page_unavailable,
                            Toast.LENGTH_LONG).show();
                }
            } catch (SecurityException failure) {
                capture.setChecked(!checked);
                Toast.makeText(this, R.string.native_page_unavailable,
                        Toast.LENGTH_LONG).show();
            }
        });
        screenshots.addView(capture);
        screenshots.addView(Ui.text(this,
                getString(R.string.capture_secure_summary), 14, false));
        page.addView(screenshots);

        // No global signature bypass switch. Always open the searchable per-app picker.
        Ui.actionCard(this, page, R.string.capture_signature_title,
                R.string.capture_signature_summary,
                view -> startActivity(new Intent(this, SignatureTestAppsActivity.class)));
        Ui.scroll(this, page);
    }
}
