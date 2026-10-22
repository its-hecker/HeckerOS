// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.view.MenuItem;

public final class QuickSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.qs_title);
        page.addView(Ui.text(this, getString(R.string.qs_description), 16, false));
        RadioGroup group = new RadioGroup(this);
        String[] names = {getString(R.string.qs_classic), getString(R.string.qs_dashboard)};
        int current = Settings.Secure.getInt(getContentResolver(), "hecker_qs_layout", 0);
        for (int index = 0; index < names.length; index++) {
            RadioButton button = new RadioButton(this);
            button.setTextColor(getColor(R.color.hecker_foreground));
            button.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.hecker_accent)));
            button.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            button.setText(names[index]); button.setId(index + 1); group.addView(button);
        }
        group.check(current == 1 ? 2 : 1);
        group.setOnCheckedChangeListener((g, id) -> Settings.Secure.putInt(getContentResolver(), "hecker_qs_layout", id == 2 ? 1 : 0));
        page.addView(group);
        Ui.scroll(this, page);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
