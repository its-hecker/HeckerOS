// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;

/** Navigation controls belong together; the existing overlay state is retained. */
public final class GestureNavigationActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.gestures_navigation_title);
        Ui.hero(this, page, R.raw.suite_navigation, R.string.gestures_navigation_summary);
        Ui.actionCard(this, page, R.string.navigation_title, R.string.navigation_summary,
                view -> SuitePages.settings(this, "com.android.settings.NAVIGATION_MODE_SETTINGS"));
        GestureNavigationPreference.add(this, page);
        Ui.scroll(this, page);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
