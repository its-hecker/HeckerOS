// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-selected manager-package privacy; does not modify root grants. */
public final class RootPrivacyActivity extends Activity {
    private static final String SETTING = "hecker_root_private_apps";
    private static final Set<String> MANAGERS = Set.of(
            "com.rifsxd.ksunext", "me.weishu.kernelsu", "com.topjohnwu.magisk", "me.bmax.apatch");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> selected = new TreeSet<>();
    private List<ApplicationInfo> apps = new ArrayList<>();
    private LinearLayout list;
    private EditText search;
    private boolean restoring;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"coral".equals(Build.DEVICE)) { finish(); return; }
        LinearLayout page = Ui.page(this, R.string.root_privacy_title);
        LinearLayout info = Ui.card(this);
        info.addView(Ui.text(this, getString(R.string.root_privacy_body), 14, false));
        page.addView(info);
        search = new EditText(this);
        search.setHint(R.string.search_apps);
        search.setSingleLine(true);
        page.addView(search);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        Ui.scroll(this, page);
        String value = Settings.Secure.getString(getContentResolver(), SETTING);
        if (value != null && !value.isEmpty()) selected.addAll(Arrays.asList(value.split(":")));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { render(); }
            public void afterTextChanged(Editable e) {}
        });
        worker.execute(() -> {
            List<ApplicationInfo> result = new ArrayList<>();
            for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                        && !MANAGERS.contains(app.packageName)) result.add(app);
            }
            Collator collator = Collator.getInstance();
            result.sort((a, b) -> collator.compare(a.loadLabel(getPackageManager()).toString(),
                    b.loadLabel(getPackageManager()).toString()));
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                apps = result;
                render();
            });
        });
    }

    private void render() {
        list.removeAllViews();
        String filter = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (ApplicationInfo app : apps) {
            String label = app.loadLabel(getPackageManager()).toString();
            if (!filter.isEmpty() && !(label + " " + app.packageName)
                    .toLowerCase(Locale.ROOT).contains(filter)) continue;
            LinearLayout row = Ui.card(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.loadIcon(getPackageManager()));
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
            copy.addView(Ui.text(this, label, 16, true));
            copy.addView(Ui.text(this, app.packageName, 12, false));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Switch toggle = new Switch(this);
            toggle.setContentDescription(label);
            toggle.setChecked(selected.contains(app.packageName));
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (restoring) return;
                if (checked) selected.add(app.packageName); else selected.remove(app.packageName);
                if (!Settings.Secure.putString(getContentResolver(), SETTING,
                        String.join(":", selected))) {
                    if (checked) selected.remove(app.packageName); else selected.add(app.packageName);
                    restoring = true;
                    button.setChecked(!checked);
                    restoring = false;
                    Toast.makeText(this, R.string.root_privacy_save_error, Toast.LENGTH_LONG).show();
                }
            });
            row.addView(toggle);
            list.addView(row);
        }
    }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
