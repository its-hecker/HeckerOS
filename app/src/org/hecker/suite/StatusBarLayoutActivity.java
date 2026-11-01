// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Dedicated destination so dual-row and lyrics controls have a discoverable home. */
public final class StatusBarLayoutActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.sb_options_title);
        StatusBarCustomization.add(this, page);
        Ui.scroll(this, page);
    }
}
