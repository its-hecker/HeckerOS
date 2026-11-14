// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An explicit per-app selection list. The framework enforces this package-name
 * allowlist only for ordinary user apps; it never disables APK integrity checks.
 */
public final class SignatureTestAppsActivity extends Activity {
    private static final String TAG = "HeckerSignatureApps";
    private static final String KEY = "hecker_signature_allowed_packages";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<ApplicationInfo> apps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private LinearLayout list;
    private String search = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.capture_signature_title);
        LinearLayout description = Ui.card(this);
        description.addView(Ui.text(this,
                getString(R.string.capture_signature_summary), 15, false));
        page.addView(description);

        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint(R.string.capture_signature_search);
        query.setTextColor(getColor(R.color.hecker_foreground));
        query.setHintTextColor(getColor(R.color.hecker_secondary));
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                search = s.toString().toLowerCase(Locale.ROOT);
                render();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        page.addView(query);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(Ui.text(this, getString(R.string.capture_signature_loading), 14, false));
        page.addView(list);
        Ui.scroll(this, page);
        reloadSelection();

        worker.execute(() -> {
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> eligible = new ArrayList<>();
                for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                    if (eligible(pm, app)) eligible.add(app);
                }
                eligible.sort(Comparator.comparing(
                        app -> app.loadLabel(pm).toString(), String.CASE_INSENSITIVE_ORDER));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    apps.clear();
                    apps.addAll(eligible);
                    render();
                });
            } catch (RuntimeException failure) {
                Log.e(TAG, "Failed to enumerate user apps", failure);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    list.removeAllViews();
                    list.addView(Ui.text(this,
                            getString(R.string.native_page_unavailable), 15, false));
                });
            }
        });
    }

    private boolean eligible(PackageManager pm, ApplicationInfo app) {
        if ((app.flags & (ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                || app.uid < 10000  // Android application UID range begins at 10000.
                || getPackageName().equals(app.packageName)) return false;
        String[] shared = pm.getPackagesForUid(app.uid);
        return shared != null && shared.length == 1;
    }

    private JSONObject readConfiguration() throws JSONException {
        String value = Settings.Global.getString(getContentResolver(), KEY);
        return value == null || value.isEmpty() ? new JSONObject() : new JSONObject(value);
    }

    private void reloadSelection() {
        try {
            JSONObject config = readConfiguration();
            selected.clear();
            java.util.Iterator<String> keys = config.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                if (config.optBoolean(pkg, false)) selected.add(pkg);
            }
        } catch (RuntimeException | JSONException failure) {
            Log.e(TAG, "Cannot read per-app signature settings", failure);
            Toast.makeText(this, R.string.native_page_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        int visible = 0;
        for (ApplicationInfo app : apps) {
            String label = app.loadLabel(pm).toString();
            if (!label.toLowerCase(Locale.ROOT).contains(search)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(search)) continue;
            LinearLayout card = Ui.card(this);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.loadIcon(pm));
            row.addView(icon, new LinearLayout.LayoutParams(
                    Ui.dp(this, 42), Ui.dp(this, 42)));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 6), 0);
            labels.addView(Ui.text(this, label, 16, true));
            labels.addView(Ui.text(this, app.packageName, 12, false));
            labels.addView(Ui.text(this, getString(selected.contains(app.packageName)
                    ? R.string.capture_signature_enabled : R.string.capture_signature_disabled),
                    12, false));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            Switch toggle = new Switch(this);
            toggle.setChecked(selected.contains(app.packageName));
            toggle.setContentDescription(label);
            row.addView(toggle);
            card.addView(row);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> choose(app, label));
            toggle.setOnClickListener(v -> choose(app, label));
            Ui.styleControls(this, card);
            list.addView(card);
            visible++;
        }
        if (visible == 0) list.addView(Ui.text(this,
                getString(R.string.capture_signature_empty), 15, false));
    }

    private void choose(ApplicationInfo app, String label) {
        if (selected.contains(app.packageName)) {
            save(app.packageName, false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage(getString(R.string.capture_signature_warning, label))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> render())
                .setPositiveButton(R.string.capture_signature_confirm,
                        (dialog, which) -> save(app.packageName, true))
                .setOnCancelListener(dialog -> render())
                .show();
    }

    private void save(String packageName, boolean enabled) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
            if (!eligible(pm, app)) throw new SecurityException("Not an eligible user app");
            JSONObject config = readConfiguration();
            if (enabled) config.put(packageName, true);
            else config.remove(packageName);
            String wanted = config.toString();
            if (!Settings.Global.putString(getContentResolver(), KEY, wanted)) {
                throw new SecurityException("Global setting was not saved");
            }
            // Read back what Package Manager will see. Never present an unverified state.
            JSONObject readBack = readConfiguration();
            if (readBack.optBoolean(packageName, false) != enabled) {
                throw new SecurityException("Global setting readback mismatch");
            }
            reloadSelection();
            render();
        } catch (RuntimeException | JSONException | PackageManager.NameNotFoundException failure) {
            Log.e(TAG, "Failed to change test compatibility for " + packageName, failure);
            Toast.makeText(this, R.string.native_page_unavailable,
                    Toast.LENGTH_LONG).show();
            reloadSelection();
            render();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (list != null) { reloadSelection(); render(); }
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
