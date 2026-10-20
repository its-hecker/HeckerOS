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
        Ui.actionCard(this,page,R.string.lyrics_title,R.string.lyrics_summary,v->startActivity(new Intent(this,LyricsSettingsActivity.class)));
        Ui.actionCard(this,page,R.string.lockscreen_wallpaper_title,R.string.lockscreen_wallpaper_summary,v->SuitePages.wallpaper(this));
        Ui.actionCard(this,page,R.string.theme_charging,R.string.charging_theme_summary,v->SuitePages.theme(this,4));
        Ui.actionCard(this,page,R.string.lockscreen_display_title,R.string.lockscreen_display_summary,v->SuitePages.settings(this,Settings.ACTION_DISPLAY_SETTINGS));
        Ui.scroll(this,page);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);
    }
}
