// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.android.internal.hecker.HeckerSandboxRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

/** The sandboxed-app portion of Axion/Infinity-X, integrated with native process creation. */
public final class SandboxActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<ApplicationInfo> apps = new ArrayList<>();
    private LinearLayout list;
    private String query = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.sandbox_title);
        Ui.hero(this, page, R.raw.suite_sandbox, R.string.sandbox_description);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setHint(R.string.search_apps);
        search.setTextColor(getColor(R.color.hecker_foreground));
        search.setHintTextColor(getColor(R.color.hecker_secondary));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().toLowerCase(Locale.ROOT); render();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        page.addView(search);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list); Ui.scroll(this, page);
        worker.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> result = new ArrayList<>();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                String[] shared = pm.getPackagesForUid(app.uid);
                if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0
                        && !app.packageName.equals(getPackageName())
                        && shared != null && shared.length == 1) result.add(app);
            }
            result.sort(Comparator.comparing(app -> app.loadLabel(pm).toString(), String.CASE_INSENSITIVE_ORDER));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                apps.clear(); apps.addAll(result); render();
            });
        });
    }

    private JSONObject configuration() throws JSONException {
        String raw = Settings.Secure.getString(getContentResolver(), HeckerSandboxRules.SETTING);
        return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        JSONObject config;
        try { config = configuration(); }
        catch (JSONException invalid) {
            list.addView(Ui.text(this, getString(R.string.sandbox_invalid), 15, false)); return;
        }
        PackageManager pm = getPackageManager();
        int displayed = 0;
        for (ApplicationInfo app : apps) {
            String label = app.loadLabel(pm).toString();
            if (!label.toLowerCase(Locale.ROOT).contains(query)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            int mask = config.optInt(app.packageName, 0);
            LinearLayout card = Ui.card(this);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this); icon.setImageDrawable(app.loadIcon(pm));
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(Ui.dp(this, 14), 0, 0, 0);
            labels.addView(Ui.text(this, label, 18, true));
            labels.addView(Ui.text(this, getString(mask == 0
                    ? R.string.sandbox_default : R.string.sandbox_active), 13, false));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(row); card.setFocusable(true); card.setClickable(true);
            card.setOnClickListener(view -> options(app, label, mask));
            list.addView(card); displayed++;
        }
        if (displayed == 0) list.addView(Ui.text(this, getString(R.string.app_lock_empty), 15, false));
    }

    private void options(ApplicationInfo app, String label, int mask) {
        String[] labels = {getString(R.string.sandbox_data), getString(R.string.sandbox_network),
                getString(R.string.sandbox_storage)};
        boolean[] checked = {(mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0};
        new AlertDialog.Builder(this).setTitle(label)
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setPositiveButton(R.string.sandbox_apply, (dialog, which) -> {
                    int updated = (checked[0] ? 1 : 0) | (checked[1] ? 2 : 0) | (checked[2] ? 4 : 0);
                    if (updated != mask) save(app.packageName, updated);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void save(String packageName, int mask) {
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(packageName, 0);
            String[] shared = getPackageManager().getPackagesForUid(app.uid);
            if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    || shared == null || shared.length != 1 || !HeckerSandboxRules.valid(mask)) {
                throw new SecurityException("Unsupported sandbox target");
            }
            JSONObject config = configuration();
            if (mask == 0) config.remove(packageName); else config.put(packageName, mask);
            if (!Settings.Secure.putString(getContentResolver(), HeckerSandboxRules.SETTING, config.toString())) {
                throw new SecurityException("Could not save sandbox settings");
            }
            getSystemService(ActivityManager.class).forceStopPackage(packageName);
            Toast.makeText(this, R.string.sandbox_changed, Toast.LENGTH_LONG).show();
            render();
        } catch (JSONException | PackageManager.NameNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.sandbox_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
