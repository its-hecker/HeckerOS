// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Toast;

/** Uses Android's HOME chooser. Pixel remains the single Recents provider. */
public final class LauncherSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout page=Ui.page(this,R.string.launcher_title);
        Ui.hero(this,page,R.raw.suite_orbit,R.string.hecker_launcher_native_summary);
        Ui.actionCard(this,page,R.string.hecker_launcher,R.string.hecker_launcher_native_settings,
                v->open(new Intent().setClassName("com.android.launcher3","com.android.launcher3.settings.SettingsActivity")));
        Ui.actionCard(this,page,R.string.launcher_system_home,R.string.launcher_native_home_summary,
                v->open(new Intent(Settings.ACTION_HOME_SETTINGS)));
        page.addView(Ui.text(this,getString(R.string.launcher_pixel_retained),14,false));
        Ui.scroll(this,page);
    }
    private void open(Intent intent){
        try{startActivity(intent);}
        catch(android.content.ActivityNotFoundException|SecurityException unavailable){
            Toast.makeText(this,R.string.launcher_open_failed,Toast.LENGTH_LONG).show();
        }
    }
}
