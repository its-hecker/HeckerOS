// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

public final class LyricsSettingsActivity extends Activity {
    private boolean restoring;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.lyrics_title);
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, getString(R.string.lyrics_title), 25, true));
        card.addView(Ui.text(this, getString(R.string.lyrics_description), 16, false));
        Switch enabled = new Switch(this);
        enabled.setText(R.string.lyrics_enable);
        enabled.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 20));
        enabled.setChecked(Settings.Secure.getInt(getContentResolver(), "hecker_lockscreen_lyrics", 0) == 1);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (restoring) return;
            try {
                if (!Settings.Secure.putInt(getContentResolver(), "hecker_lockscreen_lyrics", checked ? 1 : 0)) {
                    throw new SecurityException();
                }
            } catch (SecurityException unavailable) {
                restoring = true;
                button.setChecked(!checked);
                restoring = false;
                Toast.makeText(this, R.string.lyrics_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        card.addView(enabled);
        page.addView(card);
        page.addView(Ui.text(this, getString(R.string.lyrics_source), 14, false));
        Ui.scroll(this, page);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
