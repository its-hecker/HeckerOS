// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

public final class HeadphonesActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.headphones_title);
        Ui.hero(this, page, R.raw.suite_headphones, R.string.headphones_description);
        LinearLayout card = Ui.card(this);
        Switch enabled = new Switch(this);
        enabled.setText(R.string.headphones_popup);
        enabled.setTextColor(getColor(R.color.hecker_foreground));
        enabled.setMinHeight(Ui.dp(this, 56));
        enabled.setChecked(Settings.Secure.getInt(getContentResolver(), "hecker_headphone_popup", 1) != 0);
        enabled.setOnCheckedChangeListener((view, checked) -> Settings.Secure.putInt(
                getContentResolver(), "hecker_headphone_popup", checked ? 1 : 0));
        card.addView(enabled); page.addView(card);
        Ui.actionCard(this, page, R.string.headphones_airpods, R.string.headphones_airpods_summary,
                view -> {
                    Intent intent = getPackageManager().getLaunchIntentForPackage("me.kavishdevar.librepods");
                    try {
                        if (intent == null) throw new ActivityNotFoundException();
                        startActivity(intent);
                    } catch (ActivityNotFoundException | SecurityException unavailable) {
                        Toast.makeText(this, R.string.headphones_airpods_unavailable, Toast.LENGTH_LONG).show();
                    }
                });
        Ui.scroll(this, page);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
