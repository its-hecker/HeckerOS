// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public final class LauncherSettingsActivity extends Activity {
    private LauncherController controller;
    private boolean switching;
    private SuiteAnimation animation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        controller = new LauncherController(this);
    }

    @Override protected void onResume() { super.onResume(); render(); }

    private void render() {
        if (animation != null) animation.stop();
        LinearLayout page = Ui.page(this, R.string.launcher_title);
        LinearLayout hero = Ui.card(this);
        animation = new SuiteAnimation(this, hero);
        hero.addView(Ui.text(this, getString(R.string.hecker_launcher), 28, true));
        hero.addView(Ui.text(this, getString(R.string.launcher_intro), 15, false));
        page.addView(hero);
        String current = controller.current();
        String label = LauncherController.HECKER.equals(current) ? getString(R.string.hecker_launcher)
                : LauncherController.PIXEL.equals(current) ? getString(R.string.pixel_launcher)
                : getString(R.string.launcher_other);
        page.addView(Ui.text(this, getString(R.string.launcher_current, label), 18, true));
        choice(page, LauncherController.HECKER, R.string.hecker_launcher, R.string.hecker_launcher_features);
        choice(page, LauncherController.PIXEL, R.string.pixel_launcher, R.string.pixel_launcher_features);
        if (!LauncherController.primaryUser()) {
            page.addView(Ui.text(this, getString(R.string.launcher_owner_only), 15, false));
        } else if (!controller.available()) {
            page.addView(Ui.text(this, getString(R.string.launcher_build_missing), 15, false));
        }
        if (controller.restartNeeded()) {
            LinearLayout restart = Ui.card(this);
            restart.addView(Ui.text(this, getString(R.string.launcher_restart_needed), 19, true));
            restart.addView(Ui.text(this, getString(R.string.launcher_restart_body), 15, false));
            Button button = Ui.button(this);
            button.setText(R.string.launcher_restart);
            button.setEnabled(!switching);
            button.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(R.string.launcher_restart).setMessage(R.string.launcher_restart_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.launcher_restart, (dialog, which) -> {
                        try { controller.restart(() -> getSystemService(PowerManager.class).reboot(null)); }
                        catch (RuntimeException failure) {
                            Toast.makeText(this, R.string.launcher_restart_manual, Toast.LENGTH_LONG).show();
                        }
                    }).show());
            restart.addView(button);
            page.addView(restart);
        }
        Ui.actionCard(this, page, R.string.launcher_customize, R.string.launcher_customize_summary,
                v -> open(new Intent("android.intent.action.APPLICATION_PREFERENCES")
                        .setClassName(LauncherController.HECKER, "com.android.launcher3.settings.SettingsActivity")));
        Ui.actionCard(this, page, R.string.launcher_system_home, R.string.launcher_system_home_summary,
                v -> open(new Intent(Settings.ACTION_HOME_SETTINGS)));
        Ui.scroll(this, page);
        animation.show(R.raw.suite_orbit, true);
    }

    private void choice(LinearLayout page, String packageName, int title, int summary) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, getString(title), 23, true));
        card.addView(Ui.text(this, getString(summary), 15, false));
        Button select = Ui.button(this);
        boolean installed = controller.installed(packageName);
        boolean selected = packageName.equals(controller.current()) && controller.matches(packageName);
        select.setText(!installed ? R.string.launcher_not_installed : switching ? R.string.launcher_switching
                : selected ? R.string.launcher_selected : R.string.launcher_select);
        select.setEnabled(installed && !switching && !selected && LauncherController.primaryUser()
                && controller.available());
        select.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(getString(title))
                .setMessage(R.string.launcher_switch_explanation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.launcher_select, (dialog, which) -> {
                    switching = true;
                    render();
                    controller.select(packageName, success -> {
                        switching = false;
                        if (isDestroyed() || isFinishing()) return;
                        render();
                        if (!success) Toast.makeText(this, R.string.launcher_switch_failed, Toast.LENGTH_LONG).show();
                    });
                }).show());
        card.addView(select);
        page.addView(card);
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.launcher_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onStop() {
        if (animation != null) animation.stop();
        super.onStop();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
