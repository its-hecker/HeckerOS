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
    private Switch customClock, depthToggle;
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
        Ui.actionCard(this,page,R.string.home_depth_picker_title,R.string.home_depth_picker_summary,
            v->startActivity(new Intent(this,DepthWallpaperActivity.class).putExtra("home_only",true)));
        Ui.actionCard(this,page,R.string.lockscreen_wallpaper_title,R.string.lockscreen_wallpaper_summary,v->SuitePages.wallpaper(this));
        LinearLayout clock = Ui.card(this);
        toggle(clock, R.string.custom_clock_title, R.string.custom_clock_summary,
                "hecker_custom_lockscreen_clock", 0);
        int previousStyle = Settings.Secure.getInt(getContentResolver(), "hecker_lockscreen_clock_style", 0);
        if (previousStyle >= 2 && previousStyle <= 4) put("hecker_lockscreen_clock_style", 6);
        choices(clock, "hecker_lockscreen_clock_style", 0,
                new int[]{R.string.lock_clock_geometric, R.string.lock_clock_word,
                        R.string.lock_clock_living_bold, R.string.xmorph_title}, new int[]{0, 1, 5, 6});
        Ui.actionCard(this, clock, R.string.xmorph_title, R.string.xmorph_summary,
                v -> { imported = true; startActivity(new Intent(this, XMorphClockActivity.class)); });
        android.widget.Button applyClock = Ui.button(this); applyClock.setText(R.string.lock_clock_apply);
        applyClock.setOnClickListener(v -> { if (put("hecker_custom_lockscreen_clock",1)) {
            if(customClock!=null)customClock.setChecked(true);
            Toast.makeText(this,R.string.lock_clock_applied,Toast.LENGTH_SHORT).show();
        }}); clock.addView(applyClock);
        clock.addView(Ui.text(this, getString(R.string.lock_clock_living_help), 14, false));
        range(clock, R.string.lock_clock_size, "hecker_lockscreen_clock_scale", 70, 110, 100);
        range(clock, R.string.aod_clock_size, "hecker_aod_clock_scale", 70, 200, 140);
        clock.addView(Ui.text(this, getString(R.string.aod_clock_size_summary), 14, false));
        range(clock, R.string.lock_clock_horizontal, "hecker_lockscreen_clock_x", -100, 100, 0);
        range(clock, R.string.lock_clock_vertical, "hecker_lockscreen_clock_y", -100, 100, 0);
        choices(clock, "hecker_lockscreen_clock_color", 0,
                new int[]{R.string.flow_white, R.string.flow_lilac, R.string.flow_sand, R.string.flow_mint, R.string.flow_blue});
        android.widget.SeekBar opacity = new android.widget.SeekBar(this); opacity.setMin(15); opacity.setMax(100);
        opacity.setProgress(Settings.Secure.getInt(getContentResolver(), "hecker_lockscreen_clock_opacity", 76));
        clock.addView(Ui.text(this, getString(R.string.lock_clock_opacity), 16, true));
        opacity.setContentDescription(getString(R.string.lock_clock_opacity));
        opacity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean user) {
                if (user) put("hecker_lockscreen_clock_opacity", value);
            }
        }); clock.addView(opacity);
        page.addView(clock);
        LinearLayout depth = Ui.card(this);
        toggle(depth, R.string.depth_wallpaper_title, R.string.depth_wallpaper_summary,
                "hecker_depth_wallpaper", 0);
        toggle(depth, R.string.depth_wallpaper_wake, R.string.depth_wallpaper_wake_summary,
                "hecker_depth_wallpaper_wake", 1);
        toggle(depth, R.string.depth_auto_title, R.string.depth_auto_summary, DepthWallpaperJob.AUTO, 0);
        Ui.actionCard(this, depth, R.string.depth_current_title, R.string.depth_current_summary, v -> {
            v.setEnabled(false);
            Toast.makeText(this, R.string.depth_extract_working, Toast.LENGTH_SHORT).show();
            AppearanceBootReceiver.WORKER.execute(() -> {
                boolean success;
                try { success = DepthWallpaperJob.extractCurrent(this); }
                catch (Exception | OutOfMemoryError failure) {
                    android.util.Log.w("HeckerDepth", "Current wallpaper extraction failed", failure);
                    success = false;
                }
                final boolean applied = success;
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) {
                    v.setEnabled(true);
                    Toast.makeText(this, applied ? R.string.depth_wallpaper_applied : R.string.depth_current_failed,
                            Toast.LENGTH_LONG).show();
                    if (applied) recreate();
                } });
            });
        });
        page.addView(depth);
        Ui.actionCard(this, page, R.string.depth_wallpaper_import, R.string.depth_wallpaper_import_help,
                v -> { imported = true; startActivity(new Intent(this, DepthWallpaperActivity.class)); });
        LinearLayout charge = Ui.card(this);
        int defaultAodId=getResources().getIdentifier("config_dozeAlwaysOnEnabled","bool","android");
        toggle(charge, R.string.always_on_title, R.string.always_on_summary,
                Settings.Secure.DOZE_ALWAYS_ON, defaultAodId!=0&&getResources().getBoolean(defaultAodId)?1:0);
        SuiteAnimation art = new SuiteAnimation(this, charge, 112);
        art.show(R.raw.suite_charge, true);
        toggle(charge, R.string.aod_charging_title, R.string.aod_charging_summary,
                "hecker_charging_on_aod", 1);
        toggle(charge, R.string.ambient_charging_title, R.string.ambient_charging_summary,
                "hecker_charging_on_ambient", 1);
        toggle(charge, R.string.charging_animation_title, R.string.charging_animation_summary,
                "hecker_charging_animation", 1);
        charge.addView(Ui.text(this, getString(R.string.charging_animation_style), 18, true));
        choices(charge, "hecker_charging_animation_style", 0,
                new int[]{R.string.charging_energy_title, R.string.charging_plasma_title,
                        R.string.charging_gauge_title});
        charge.addView(Ui.text(this, getString(R.string.aod_charging_info_title), 18, true));
        choices(charge, "hecker_aod_charging_style", 1,
                new int[]{R.string.aod_charging_info_off, R.string.aod_charging_info_detailed,
                        R.string.aod_charging_info_minimal});
        charge.addView(Ui.text(this, getString(R.string.aod_charging_info_summary), 14, false));
        page.addView(charge);
        Ui.scroll(this,page);
    }
    @Override protected void onResume() {
        super.onResume();
        // Import can enable depth. Refresh controls while preserving the selected clock.
        if (imported) { imported = false; recreate(); }
    }
    private boolean imported;
    private void range(LinearLayout card, int title, String key, int min, int max, int fallback) {
        android.widget.TextView label = Ui.text(this, getString(title), 16, true);
        card.addView(label);
        android.widget.SeekBar slider = new android.widget.SeekBar(this);
        slider.setMax(max - min);
        int initial = Math.max(min, Math.min(max, Settings.Secure.getInt(getContentResolver(), key, fallback)));
        slider.setProgress(initial - min);
        label.setText(getString(title) + " · " + initial);
        slider.setContentDescription(getString(title));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean user) {
                int selected = value + min;
                label.setText(getString(title) + " · " + selected);
                if (user && !put(key, selected)) bar.setProgress(
                        Settings.Secure.getInt(getContentResolver(), key, fallback) - min);
            }
        });
        card.addView(slider);
    }
    private void toggle(LinearLayout card, int title, int summary, String key, int fallback) {
        Switch button = new Switch(this);
        button.setText(title); button.setTextColor(getColor(R.color.hecker_foreground));
        button.setMinHeight(Ui.dp(this, 56));
        button.setChecked(Settings.Secure.getInt(getContentResolver(), key, fallback) != 0);
        if ("hecker_custom_lockscreen_clock".equals(key)) customClock = button;
        if ("hecker_depth_wallpaper".equals(key)) depthToggle = button;
        button.setOnClickListener(v -> {
            if (!put(key, button.isChecked() ? 1 : 0)) button.setChecked(!button.isChecked());
            if ((DepthWallpaperJob.AUTO.equals(key) || "hecker_depth_wallpaper".equals(key)) && button.isChecked()) {
                put("hecker_depth_wallpaper",1);
                if(depthToggle!=null)depthToggle.setChecked(true);
            }
            if (DepthWallpaperJob.AUTO.equals(key) || "hecker_depth_wallpaper".equals(key)) DepthWallpaperJob.schedule(this);
        });
        card.addView(button);
        card.addView(Ui.text(this, getString(summary), 14, false));
    }
    private void choices(LinearLayout card, String key, int fallback, int[] labels) {
        int[] values = new int[labels.length]; for (int i = 0; i < values.length; i++) values[i] = i;
        choices(card, key, fallback, labels, values);
    }
    private void choices(LinearLayout card, String key, int fallback, int[] labels, int[] values) {
        RadioGroup group = new RadioGroup(this);
        int selected = Settings.Secure.getInt(getContentResolver(), key, fallback);

        for (int i = 0; i < labels.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(android.view.View.generateViewId()); button.setTag(values[i]);
            button.setText(labels[i]); button.setTextColor(getColor(R.color.hecker_foreground));
            button.setMinHeight(Ui.dp(this, 48)); group.addView(button);
            if (values[i] == selected) group.check(button.getId());
        }
        group.setOnCheckedChangeListener((buttons, id) -> {
            RadioButton button = buttons.findViewById(id);
            if (button != null && "hecker_lockscreen_clock_style".equals(key)
                    && put("hecker_custom_lockscreen_clock", 1) && customClock != null) customClock.setChecked(true);
            if (button != null && !put(key, (Integer) button.getTag())) {
                int stored = Settings.Secure.getInt(getContentResolver(), key, fallback);
                group.setOnCheckedChangeListener(null);
                for (int i = 0; i < values.length; i++) if (values[i] == stored) group.check(group.getChildAt(i).getId());
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
