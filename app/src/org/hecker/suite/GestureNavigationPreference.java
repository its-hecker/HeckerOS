// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class GestureNavigationPreference {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    static void add(Activity activity, LinearLayout page) {
        LinearLayout card = Ui.card(activity);
        card.addView(Ui.text(activity, activity.getString(R.string.gesture_navigation_title), 18, true));
        Switch toggle = new Switch(activity);
        toggle.setText(R.string.gesture_hide_pill);
        toggle.setTextColor(activity.getColor(R.color.hecker_foreground));
        toggle.setMinHeight(Ui.dp(activity, 48));
        toggle.setChecked(GestureNavigationController.enabled(activity));
        toggle.setEnabled(GestureNavigationController.available(activity));
        card.addView(toggle);
        card.addView(Ui.text(activity, activity.getString(R.string.gesture_hide_pill_summary), 14, false));
        toggle.setOnClickListener(view -> {
            boolean requested = toggle.isChecked();
            toggle.setEnabled(false);
            WORK.execute(() -> {
                boolean success = GestureNavigationController.apply(activity, requested);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    toggle.setChecked(GestureNavigationController.enabled(activity));
                    toggle.setEnabled(GestureNavigationController.available(activity));
                    if (!success) Toast.makeText(activity, R.string.gesture_hide_pill_failed,
                            Toast.LENGTH_LONG).show();
                });
            });
        });
        page.addView(card);
        // On upgrade, clear the legacy framework-space overlay even if the switch
        // was previously enabled. Keep the visual handle's current state.
        WORK.execute(() -> {
            boolean restored = GestureNavigationController.restoreLegacySpacing(activity);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                toggle.setChecked(GestureNavigationController.enabled(activity));
                toggle.setEnabled(GestureNavigationController.available(activity));
                if (!restored) Toast.makeText(activity, R.string.gesture_hide_pill_failed,
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private GestureNavigationPreference() {}
}
