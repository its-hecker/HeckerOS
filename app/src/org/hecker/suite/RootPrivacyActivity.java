// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App compatibility. Keep the old component name and root key for upgrades. */
public final class RootPrivacyActivity extends Activity {
    static final String[] KEYS = {"hecker_root_private_apps", "hecker_allow_downgrade_apps",
            "hecker_hide_developer_options_apps", "hecker_bootloader_spoof_apps"};
    private static final int[] TITLES = {R.string.compat_root_title,
            R.string.compat_downgrade_title, R.string.compat_developer_title,
            R.string.compat_bootloader_title};
    private static final int[] BODIES = {R.string.compat_root_body,
            R.string.compat_downgrade_body, R.string.compat_developer_body,
            R.string.compat_bootloader_body};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Set<String>> selected = new ArrayList<>();
    private final List<App> apps = new ArrayList<>(), visible = new ArrayList<>();
    private final AppAdapter adapter = new AppAdapter();
    private EditText search;
    private TextView status;
    private boolean backendAvailable;
    private AlertDialog dialog;

    private static final class App {
        final ApplicationInfo info;
        final String label;
        App(ApplicationInfo info, String label) { this.info = info; this.label = label; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"coral".equals(Build.DEVICE)) { finish(); return; }
        backendAvailable = "true".equals(android.os.SystemProperties.get(
                "ro.hecker.coral_root_privacy", "false"))
                && android.os.SystemProperties.getInt("ro.hecker.app_compatibility", 0) >= 1;
        for (String key : KEYS) selected.add(new TreeSet<>());
        readSelections();
        LinearLayout header = Ui.page(this, R.string.root_privacy_title);
        header.setPadding(0, Ui.dp(this, 20), 0, 0);
        LinearLayout info = Ui.card(this);
        info.addView(Ui.text(this, getString(backendAvailable
                ? R.string.root_privacy_body : R.string.compat_backend_unavailable), 14, false));
        header.addView(info);
        search = new EditText(this);
        search.setHint(R.string.search_apps);
        search.setSingleLine(true);
        if (state != null) search.setText(state.getString("query", ""));
        header.addView(search);
        status = Ui.text(this, getString(R.string.compat_loading), 13, false);
        header.addView(status);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        list.addHeaderView(header, null, false);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item instanceof App) showOptions((App) item);
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { filter(); }
            public void afterTextChanged(Editable e) {}
        });
        Ui.content(this, list);
        worker.execute(() -> {
            List<App> result = new ArrayList<>();
            try {
                // Includes system and disabled packages installed for this Android user.
                for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
                    result.add(new App(app, app.loadLabel(getPackageManager()).toString()));
                }
                Collator collator = Collator.getInstance();
                result.sort((a, b) -> collator.compare(a.label, b.label));
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    apps.clear(); apps.addAll(result); filter();
                });
            } catch (RuntimeException failure) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing())
                        status.setText(R.string.compat_load_error);
                });
            }
        });
    }

    private void readSelections() {
        for (int i = 0; i < KEYS.length; i++) {
            selected.get(i).clear();
            String value = Settings.Secure.getString(getContentResolver(), KEYS[i]);
            if (value == null || value.length() > 65536) continue;
            for (String name : value.split(":")) {
                if (name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
                    selected.get(i).add(name);
            }
        }
    }

    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (App app : apps) {
            if (query.isEmpty() || (app.label + " " + app.info.packageName)
                    .toLowerCase(Locale.ROOT).contains(query)) visible.add(app);
        }
        status.setText(getString(R.string.compat_app_count, visible.size(), apps.size()));
        adapter.notifyDataSetChanged();
    }

    private void showOptions(App app) {
        readSelections();
        LinearLayout body = Ui.page(this, R.string.root_privacy_title);
        body.addView(Ui.text(this, app.info.packageName, 13, false));
        boolean eligible = app.info.uid % 100000 >= 10000
                && !getPackageName().equals(app.info.packageName);
        body.addView(Ui.text(this, getString(eligible ? R.string.compat_restart_body
                : R.string.compat_protected_app), 14, false));
        for (int i = 0; i < KEYS.length; i++) {
            final int option = i;
            LinearLayout card = Ui.card(this);
            Switch toggle = new Switch(this);
            toggle.setText(TITLES[i]);
            toggle.setContentDescription(getString(TITLES[i]));
            toggle.setChecked(selected.get(i).contains(app.info.packageName));
            toggle.setEnabled(backendAvailable && eligible);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                Set<String> names = new TreeSet<>(selected.get(option));
                if (checked) names.add(app.info.packageName); else names.remove(app.info.packageName);
                String serialized = String.join(":", names);
                boolean saved = false;
                try {
                    saved = serialized.length() <= 65536 && Settings.Secure.putString(
                            getContentResolver(), KEYS[option], serialized);
                } catch (RuntimeException denied) { /* Revert the visible switch below. */ }
                if (saved) {
                    selected.set(option, names);
                    adapter.notifyDataSetChanged();
                } else {
                    // Rebuild from stored values; reverting must not make a second write.
                    showSaveFailure(app);
                }
            });
            card.addView(toggle);
            card.addView(Ui.text(this, getString(BODIES[i]), 13, false));
            body.addView(card);
        }
        Ui.styleControls(this, body);
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(app.label)
                .setView(scroll).setPositiveButton(android.R.string.ok, null);
        if (eligible) builder.setNeutralButton(R.string.compat_close_app, (d, which) -> {
            try {
                getSystemService(ActivityManager.class).forceStopPackage(app.info.packageName);
                Toast.makeText(this, R.string.compat_app_closed, Toast.LENGTH_SHORT).show();
            } catch (RuntimeException denied) {
                Toast.makeText(this, R.string.compat_close_failed, Toast.LENGTH_LONG).show();
            }
        });
        dialog = builder.create(); dialog.show();
    }

    private void showSaveFailure(App app) {
        Toast.makeText(this, R.string.root_privacy_save_error, Toast.LENGTH_LONG).show();
        if (dialog != null) dialog.dismiss();
        showOptions(app);
    }

    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout row;
            if (recycled instanceof LinearLayout) row = (LinearLayout) recycled;
            else row = Ui.card(RootPrivacyActivity.this);
            row.removeAllViews(); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            App app = visible.get(position);
            ImageView icon = new ImageView(RootPrivacyActivity.this);
            icon.setImageDrawable(app.info.loadIcon(getPackageManager()));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(RootPrivacyActivity.this, 44),
                    Ui.dp(RootPrivacyActivity.this, 44)));
            LinearLayout copy = new LinearLayout(RootPrivacyActivity.this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(Ui.dp(RootPrivacyActivity.this, 12), 0, 0, 0);
            copy.addView(Ui.text(RootPrivacyActivity.this, app.label, 16, true));
            copy.addView(Ui.text(RootPrivacyActivity.this, app.info.packageName, 12, false));
            int count = 0;
            for (Set<String> names : selected) if (names.contains(app.info.packageName)) count++;
            copy.addView(Ui.text(RootPrivacyActivity.this,
                    getString(R.string.compat_option_count, count), 12, false));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            return row;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("query", search.getText().toString());
    }
    @Override protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        worker.shutdownNow(); super.onDestroy();
    }
}
