// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

/** Native advanced controls. Package-install test mode is owner-only and off by default. */
public final class CaptureSettingsActivity extends Activity {
    private static final String SECURE_WINDOWS_KEY = "disable_secure_windows";
    // Read by the Android 17 Package Manager in system_server.
    private static final String SIGNATURE_TEST_KEY = "hecker_package_signature_test_mode";
    private Switch signatureTest;

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

        LinearLayout testing = Ui.card(this);
        signatureTest = new Switch(this);
        signatureTest.setText(R.string.capture_signature_title);
        signatureTest.setTextColor(getColor(R.color.hecker_foreground));
        signatureTest.setMinHeight(Ui.dp(this, 56));
        signatureTest.setEnabled(signatureTestSupported());
        refreshSignatureTest();
        signatureTest.setOnClickListener(view -> {
            if (!signatureTestSupported()) {
                refreshSignatureTest();
                return;
            }
            if (!signatureTest.isChecked()) {
                writeSignatureTest(false);
                return;
            }
            // Never turn this sensitive development setting on without consent.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.capture_signature_title)
                    .setMessage(R.string.capture_signature_warning)
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> refreshSignatureTest())
                    .setPositiveButton(R.string.capture_signature_confirm,
                            (dialog, which) -> writeSignatureTest(true))
                    .setOnCancelListener(dialog -> refreshSignatureTest())
                    .show();
        });
        testing.addView(signatureTest);
        testing.addView(Ui.text(this,
                getString(R.string.capture_signature_summary), 14, false));
        if (!signatureTestSupported()) {
            testing.addView(Ui.text(this,
                    getString(R.string.capture_signature_unsupported), 12, false));
        }
        page.addView(testing);
        Ui.scroll(this, page);
    }

    private boolean signatureTestSupported() {
        return Build.IS_DEBUGGABLE && UserHandle.myUserId() == UserHandle.USER_SYSTEM;
    }

    private void refreshSignatureTest() {
        if (signatureTest == null) return;
        boolean active = false;
        if (signatureTestSupported()) {
            try {
                active = Settings.Global.getInt(getContentResolver(),
                        SIGNATURE_TEST_KEY, 0) == 1;
            } catch (SecurityException failure) {
                active = false;
            }
        }
        signatureTest.setChecked(active);
    }

    private void writeSignatureTest(boolean enabled) {
        boolean written = false;
        try {
            if (signatureTestSupported()) {
                written = Settings.Global.putInt(getContentResolver(),
                        SIGNATURE_TEST_KEY, enabled ? 1 : 0);
            }
        } catch (SecurityException failure) {
            // A denied privileged setting must never leave a misleading checked switch.
        }
        if (!written) {
            Toast.makeText(this, R.string.native_page_unavailable,
                    Toast.LENGTH_LONG).show();
        }
        refreshSignatureTest();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshSignatureTest();
    }
}
