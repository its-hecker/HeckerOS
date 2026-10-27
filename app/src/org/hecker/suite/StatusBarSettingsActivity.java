// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;
import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
public final class StatusBarSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=Ui.page(this,R.string.status_bar_title);
        Ui.hero(this, page, R.raw.suite_status, R.string.status_styles_summary);
        LinearLayout calling = Ui.card(this);
        calling.addView(Ui.text(this, getString(R.string.ims_icons_title), 18, true));
        calling.addView(Ui.text(this, getString(R.string.ims_icons_summary), 14, false));
        toggle(calling, R.string.show_volte_icon, "hecker_show_volte_icon");
        toggle(calling, R.string.show_vowifi_icon, "hecker_show_vowifi_icon");
        page.addView(calling);
        Ui.actionCard(this,page,R.string.theme_wifi,R.string.wifi_theme_summary,v->SuitePages.theme(this,1));
        Ui.actionCard(this,page,R.string.theme_battery,R.string.battery_theme_summary,v->SuitePages.theme(this,3));
        LinearLayout battery = Ui.card(this);
        battery.addView(Ui.text(this, getString(R.string.battery_appearance_title), 18, true));
        android.widget.Switch colors = new android.widget.Switch(this);
        colors.setText(R.string.battery_colors_title); colors.setTextColor(getColor(R.color.hecker_foreground));
        colors.setMinHeight(Ui.dp(this, 48));
        colors.setChecked(android.provider.Settings.Secure.getInt(getContentResolver(), "hecker_battery_colors", 1) != 0);
        colors.setOnCheckedChangeListener((view, checked) -> android.provider.Settings.Secure.putInt(getContentResolver(), "hecker_battery_colors", checked ? 1 : 0));
        battery.addView(colors);
        toggle(battery, R.string.battery_charging_animation, "hecker_battery_charging_animation");
        toggle(battery, R.string.battery_bolt_title, "hecker_battery_bolt");
        battery.addView(Ui.text(this, getString(R.string.battery_bolt_style), 14, false));
        android.widget.Spinner boltStyle = new android.widget.Spinner(this);
        boltStyle.setContentDescription(getString(R.string.battery_bolt_style));
        boltStyle.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.battery_bolt_classic), getString(R.string.battery_bolt_split), getString(R.string.battery_bolt_spark)}));
        boltStyle.setSelection(Math.max(0, Math.min(2, android.provider.Settings.Secure.getInt(
                getContentResolver(), "hecker_battery_bolt_style", 0))));
        boltStyle.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (android.provider.Settings.Secure.getInt(getContentResolver(), "hecker_battery_bolt_style", 0) != position)
                    android.provider.Settings.Secure.putInt(getContentResolver(), "hecker_battery_bolt_style", position);
            }
        });
        battery.addView(boltStyle);
        android.widget.TextView size = Ui.text(this, "", 14, false);
        int current = Math.max(80, Math.min(140, android.provider.Settings.Secure.getInt(getContentResolver(), "hecker_battery_scale", 100)));
        size.setText(getString(R.string.battery_size_value, current)); battery.addView(size);
        android.widget.SeekBar scale = new android.widget.SeekBar(this); scale.setMax(12); scale.setProgress((current - 80) / 5);
        scale.setContentDescription(getString(R.string.battery_size_title));
        scale.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean user) {
                int percent = 80 + progress * 5;
                size.setText(getString(R.string.battery_size_value, percent));
                if (user) android.provider.Settings.Secure.putInt(getContentResolver(), "hecker_battery_scale", percent);
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            public void onStopTrackingTouch(android.widget.SeekBar bar) {}
        });
        battery.addView(scale); page.addView(battery);
        Ui.actionCard(this, page, R.string.sysui_restart_title, R.string.sysui_restart_summary,
                v -> new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.sysui_restart_title)
                        .setMessage(R.string.sysui_restart_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.sysui_restart_title, (dialog, which) ->
                                sendBroadcast(new android.content.Intent("org.hecker.suite.RESTART_SYSTEMUI")
                                        .setPackage("com.android.systemui")))
                        .show());
        Ui.scroll(this,page);
    }
    private void toggle(LinearLayout parent, int title, String key) {
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setText(title); toggle.setTextColor(getColor(R.color.hecker_foreground));
        toggle.setMinHeight(Ui.dp(this, 48));
        toggle.setChecked(android.provider.Settings.Secure.getInt(getContentResolver(), key, 1) != 0);
        toggle.setOnCheckedChangeListener((view, enabled) ->
                android.provider.Settings.Secure.putInt(getContentResolver(), key, enabled ? 1 : 0));
        parent.addView(toggle);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);
    }
}
