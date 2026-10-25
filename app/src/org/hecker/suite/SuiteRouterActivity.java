// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Signature-only bridge from Settings to private, separately permissioned feature pages. */
public final class SuiteRouterActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String page = getIntent().getStringExtra("org.hecker.suite.PAGE");
        Class<? extends Activity> target = target(page);
        if (target != null) {
            Intent intent = new Intent(this, target);
            if (target == ThemePackActivity.class) {
                intent.putExtra("category", getIntent().getIntExtra("category", -1));
            }
            startActivity(intent);
        }
        finish();
    }

    private static Class<? extends Activity> target(String page) {
        if (page == null) return null;
        switch (page) {
            case "QuickSettingsActivity": return QuickSettingsActivity.class;
            case "LockScreenSettingsActivity": return LockScreenSettingsActivity.class;
            case "LyricsSettingsActivity": return LyricsSettingsActivity.class;
            case "StatusBarSettingsActivity": return StatusBarSettingsActivity.class;
            case "ThemePackActivity": return ThemePackActivity.class;
            case "MemoryBoostActivity": return MemoryBoostActivity.class;
            case "LauncherSettingsActivity": return LauncherSettingsActivity.class;
            case "SandboxActivity": return SandboxActivity.class;
            case "HeadphonesActivity": return HeadphonesActivity.class;
            case "CaptureSettingsActivity": return CaptureSettingsActivity.class;
            case "AppLockActivity": return AppLockActivity.class;
            case "GamingActivity": return GamingActivity.class;
            case "InfraredCameraActivity": return InfraredCameraActivity.class;
            default: return null;
        }
    }
}
