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
        Ui.actionCard(this,page,R.string.theme_signal,R.string.signal_theme_summary,v->SuitePages.theme(this,0));
        Ui.actionCard(this,page,R.string.theme_wifi,R.string.wifi_theme_summary,v->SuitePages.theme(this,1));
        Ui.actionCard(this,page,R.string.theme_battery,R.string.battery_theme_summary,v->SuitePages.theme(this,3));
        Ui.scroll(this,page);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);
    }
}
