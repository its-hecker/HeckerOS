// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.LinearLayout;
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
        LinearLayout charge = Ui.card(this);
        SuiteAnimation art = new SuiteAnimation(this, charge, 112);
        art.show(R.raw.suite_charge, true);
        android.widget.Switch charging = new android.widget.Switch(this);
        charging.setText(R.string.charging_energy_title);
        charging.setTextColor(getColor(R.color.hecker_foreground));
        charging.setMinHeight(Ui.dp(this, 56));
        charging.setChecked(Settings.Secure.getInt(getContentResolver(), "hecker_charging_animation", 1) != 0);
        charging.setOnCheckedChangeListener((button, checked) -> Settings.Secure.putInt(
                getContentResolver(), "hecker_charging_animation", checked ? 1 : 0));
        charge.addView(charging);
        charge.addView(Ui.text(this, getString(R.string.charging_energy_summary), 14, false));
        page.addView(charge);
        Ui.scroll(this,page);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);
    }
}
