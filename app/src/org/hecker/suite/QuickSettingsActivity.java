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
            if (index == 1) {
                android.widget.ImageView art = new android.widget.ImageView(this);
                art.setImageResource(R.drawable.qs_hecker_preview);
                art.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                art.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.addView(art, new LinearLayout.LayoutParams(-1, Ui.dp(this, 156)));
            } else {
                SuiteAnimation art = new SuiteAnimation(this, card);
                art.show(previews[index], true);
            }
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
        LinearLayout blur = Ui.card(this);
        android.widget.TextView label = Ui.text(this, getString(R.string.qs_blur_strength), 16, true);
        blur.addView(label);
        android.widget.SeekBar strength = new android.widget.SeekBar(this);
        strength.setMax(100);
        strength.setProgress(Math.max(0, Math.min(100, Settings.Secure.getInt(
                getContentResolver(), "hecker_qs_blur_strength", 55))));
        label.setText(getString(R.string.qs_blur_strength) + " · " + strength.getProgress() + "%");
        strength.setContentDescription(getString(R.string.qs_blur_strength));
        strength.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean user) {
                label.setText(getString(R.string.qs_blur_strength) + " · " + value + "%");
                if (user && !Settings.Secure.putInt(getContentResolver(), "hecker_qs_blur_strength", value))
                    bar.setProgress(Settings.Secure.getInt(getContentResolver(), "hecker_qs_blur_strength", 55));
            }
        });
        blur.addView(strength);
        blur.addView(Ui.text(this, getString(R.string.qs_blur_strength_summary), 14, false));
        page.addView(blur);
        Ui.scroll(this, page);
    }
    private void select(int layout) {
        if (!Settings.Secure.putInt(getContentResolver(), "hecker_qs_layout", layout)) return;
        for (int i = 0; i < choices.length; i++) choices[i].setChecked(i == layout);
    }
}
