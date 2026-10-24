// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.RadioButton;

/** Animated layout previews retain the native tile editor and per-user setting. */
public final class QuickSettingsActivity extends Activity {
    private final RadioButton[] choices = new RadioButton[2];
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.qs_title);
        page.addView(Ui.text(this, getString(R.string.qs_description), 15, false));
        int[] names = {R.string.qs_classic, R.string.qs_dashboard};
        int[] previews = {R.raw.suite_qs_oneui, R.raw.suite_qs_hecker};
        int selected = Settings.Secure.getInt(getContentResolver(), "hecker_qs_layout", 0);
        for (int index = 0; index < choices.length; index++) {
            final int layout = index;
            LinearLayout card = Ui.card(this);
            SuiteAnimation art = new SuiteAnimation(this, card);
            art.show(previews[index], true);
            RadioButton button = new RadioButton(this);
            button.setText(names[index]); button.setTextSize(16);
            button.setTextColor(getColor(R.color.hecker_foreground));
            button.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.hecker_accent)));
            button.setMinHeight(Ui.dp(this, 56));
            button.setChecked(index == (selected == 1 ? 1 : 0));
            choices[index] = button;
            button.setOnClickListener(v -> select(layout));
            card.addView(button);
            card.setOnClickListener(v -> select(layout));
            page.addView(card);
        }
        Ui.scroll(this, page);
    }
    private void select(int layout) {
        if (!Settings.Secure.putInt(getContentResolver(), "hecker_qs_layout", layout)) return;
        for (int i = 0; i < choices.length; i++) choices[i].setChecked(i == layout);
    }
}
