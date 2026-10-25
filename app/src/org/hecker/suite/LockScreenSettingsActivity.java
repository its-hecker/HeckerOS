// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;
public final class LockScreenSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=Ui.page(this,R.string.lock_screen_title);
        Ui.hero(this, page, R.raw.suite_lock, R.string.lock_screen_summary);
        LinearLayout faceCard = Ui.card(this);
        android.widget.Switch faceIsland = new android.widget.Switch(this);
        faceIsland.setText(R.string.face_island_title);
        faceIsland.setTextColor(getColor(R.color.hecker_foreground));
        faceIsland.setMinHeight(Ui.dp(this, 56));
        faceIsland.setChecked(Settings.Secure.getInt(getContentResolver(),
                "hecker_face_unlock_island", 1) != 0);
        faceIsland.setOnCheckedChangeListener((button, checked) -> Settings.Secure.putInt(
                getContentResolver(), "hecker_face_unlock_island", checked ? 1 : 0));
        faceCard.addView(faceIsland);
        faceCard.addView(Ui.text(this, getString(R.string.face_island_summary), 14, false));
        page.addView(faceCard);
        Ui.actionCard(this,page,R.string.lyrics_title,R.string.lyrics_summary,v->startActivity(new Intent(this,LyricsSettingsActivity.class)));
        Ui.actionCard(this,page,R.string.lockscreen_wallpaper_title,R.string.lockscreen_wallpaper_summary,v->SuitePages.wallpaper(this));
        LinearLayout clock = Ui.card(this);
        toggle(clock, R.string.custom_clock_title, R.string.custom_clock_summary,
                "hecker_custom_lockscreen_clock", 0);
        page.addView(clock);
        LinearLayout charge = Ui.card(this);
        SuiteAnimation art = new SuiteAnimation(this, charge, 112);
        art.show(R.raw.suite_charge, true);
        toggle(charge, R.string.ambient_charging_title, R.string.ambient_charging_summary,
                "hecker_charging_on_ambient", 1);
        toggle(charge, R.string.charging_animation_title, R.string.charging_animation_summary,
                "hecker_charging_animation", 1);
        charge.addView(Ui.text(this, getString(R.string.charging_animation_style), 18, true));
        choices(charge, "hecker_charging_animation_style", 0,
                new int[]{R.string.charging_energy_title, R.string.charging_plasma_title});
        charge.addView(Ui.text(this, getString(R.string.aod_charging_info_title), 18, true));
        choices(charge, "hecker_aod_charging_style", 1,
                new int[]{R.string.aod_charging_info_off, R.string.aod_charging_info_detailed,
                        R.string.aod_charging_info_minimal});
        charge.addView(Ui.text(this, getString(R.string.aod_charging_info_summary), 14, false));
        page.addView(charge);
        Ui.scroll(this,page);
    }
    private void toggle(LinearLayout card, int title, int summary, String key, int fallback) {
        Switch button = new Switch(this);
        button.setText(title); button.setTextColor(getColor(R.color.hecker_foreground));
        button.setMinHeight(Ui.dp(this, 56));
        button.setChecked(Settings.Secure.getInt(getContentResolver(), key, fallback) != 0);
        button.setOnClickListener(v -> {
            if (!put(key, button.isChecked() ? 1 : 0)) button.setChecked(!button.isChecked());
        });
        card.addView(button);
        card.addView(Ui.text(this, getString(summary), 14, false));
    }
    private void choices(LinearLayout card, String key, int fallback, int[] labels) {
        RadioGroup group = new RadioGroup(this);
        int selected = Settings.Secure.getInt(getContentResolver(), key, fallback);
        if (selected < 0 || selected >= labels.length) selected = fallback;
        for (int i = 0; i < labels.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(android.view.View.generateViewId()); button.setTag(i);
            button.setText(labels[i]); button.setTextColor(getColor(R.color.hecker_foreground));
            button.setMinHeight(Ui.dp(this, 48)); group.addView(button);
            if (i == selected) group.check(button.getId());
        }
        group.setOnCheckedChangeListener((buttons, id) -> {
            RadioButton button = buttons.findViewById(id);
            if (button != null && !put(key, (Integer) button.getTag())) {
                int stored = Settings.Secure.getInt(getContentResolver(), key, fallback);
                group.setOnCheckedChangeListener(null);
                group.check(group.getChildAt(Math.max(0, Math.min(labels.length-1, stored))).getId());
                recreate();
            }
        });
        card.addView(group);
    }
    private boolean put(String key, int value) {
        try {
            if (Settings.Secure.putInt(getContentResolver(), key, value)) return true;
        } catch (SecurityException unavailable) { }
        Toast.makeText(this, R.string.native_page_unavailable, Toast.LENGTH_LONG).show();
        return false;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);
    }
}
