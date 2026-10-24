// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native Suite UI for the pinned Lunaris theme pack. No network or APK installer is used. */
public final class ThemePackActivity extends Activity {
    private static final String KEY = "theme_engine_data";
    private static final String NATIVE_KEY = "theme_customization_overlay_packages";
    private static final String[] CATEGORIES = {"wifi_icons", "battery_style"};
    private static final String[] ENGINE = {"statusbar_wifi", "battery_style"};
    private JSONArray catalog;
    private LinearLayout list;
    private int category;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try (java.io.InputStream stream = getAssets().open("theme_catalog.json")) {
            catalog = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("themes");
        } catch (Exception missing) { Toast.makeText(this, R.string.themes_unavailable, Toast.LENGTH_LONG).show(); finish(); return; }
        category = getIntent().getIntExtra("category", 1) == 3 ? 1 : 0;
        LinearLayout page = Ui.page(this, category == 0 ? R.string.theme_wifi : R.string.theme_battery);
        Ui.hero(this, page, R.raw.suite_status, R.string.status_styles_summary);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list);
        show();
        Ui.scroll(this, page);
    }

    private JSONObject config() throws org.json.JSONException {
        String raw = Settings.Secure.getString(getContentResolver(), KEY);
        return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }
    private void show() {
        list.removeAllViews();
        Ui.actionCard(this, list, R.string.theme_default, R.string.theme_default_summary, v -> apply(null));
        String selected = "";
        try { selected = config().optJSONObject("selectedStyles").optString(CATEGORIES[category]); } catch (Exception ignored) { }
        for (int n = 0; n < catalog.length(); n++) {
            JSONObject theme = catalog.optJSONObject(n);
            if (theme == null || !CATEGORIES[category].equals(theme.optString("category"))) continue;
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, theme.optString("name") + (theme.optString("id").equals(selected) ? " ✓" : ""), 20, true));
            card.addView(Ui.text(this, theme.optString("description") + "\n" + theme.optString("author"), 13, false));
            preview(card, theme);
            android.widget.Button choose = Ui.button(this); choose.setText(R.string.theme_apply);
            choose.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(theme.optString("name"))
                    .setMessage(R.string.theme_apply_message).setPositiveButton(R.string.theme_apply, (d, which) -> apply(theme))
                    .setNegativeButton(android.R.string.cancel, null).show());
            card.addView(choose); list.addView(card);
        }
    }
    private void preview(LinearLayout card, JSONObject theme) {
        try {
            String pkg = theme.getJSONArray("overlays").getJSONObject(0).getString("packageName");
            Resources res = getPackageManager().getResourcesForApplication(pkg);
            LinearLayout row = new LinearLayout(this);
            String[] names = category == 0 ? new String[]{"ic_wifi_signal_0", "ic_wifi_signal_2", "ic_wifi_signal_4"} : new String[0];
            for (String name : names) {
                int id = res.getIdentifier(name, "drawable", pkg);
                if (id == 0) continue;
                ImageView icon = new ImageView(this); icon.setImageDrawable(res.getDrawable(id, null));
                android.util.TypedValue color = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.textColorPrimary, color, true);
                icon.setColorFilter(color.resourceId == 0 ? color.data : getColor(color.resourceId));
                int size = Ui.dp(this, 42); row.addView(icon, new LinearLayout.LayoutParams(size, size));
            }
            card.addView(row);
        } catch (Exception missing) { /* The Apply action reports a missing product resource. */ }
    }
    private void trusted(JSONArray overlays) throws Exception {
        for (int n = 0; n < overlays.length(); n++) {
            String pkg = overlays.getJSONObject(n).getString("packageName");
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || getPackageManager().checkSignatures("android", pkg) != PackageManager.SIGNATURE_MATCH) {
                throw new SecurityException("Theme resources must be built and signed by HeckerOS");
            }
        }
    }
    private void apply(JSONObject theme) {
        try {
            JSONObject data = config();
            JSONArray overlays = theme == null ? null : theme.getJSONArray("overlays");
            if (overlays != null) trusted(overlays);
            JSONObject themes = data.optJSONObject("themes"); if (themes == null) themes = new JSONObject();
            JSONObject categories = data.optJSONObject("categoryThemes"); if (categories == null) categories = new JSONObject();
            JSONObject styles = data.optJSONObject("selectedStyles"); if (styles == null) styles = new JSONObject();
            String key = ENGINE[category];
            if (theme == null) { themes.remove(key); categories.remove(key); }
            else {
                String pkg = overlays.getJSONObject(0).getString("packageName");
                themes.put(key, new JSONObject().put("enabled", true).put("packageName", pkg));
                categories.put(key, pkg);
            }
            if (theme == null) styles.remove(CATEGORIES[category]); else styles.put(CATEGORIES[category], theme.getString("id"));
            data.put("themes", themes).put("categoryThemes", categories).put("selectedStyles", styles);
            if (!Settings.Secure.putString(getContentResolver(), KEY, data.toString())) throw new SecurityException();
            android.util.Log.i("HeckerThemes", "Applied " + CATEGORIES[category] + ": "
                    + (theme == null ? "default" : theme.optString("id")));
            show();
        } catch (Exception unavailable) {
            android.util.Log.e("HeckerThemes", "Cannot apply " + CATEGORIES[category], unavailable);
            Toast.makeText(this, R.string.theme_failed, Toast.LENGTH_LONG).show();
        }
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
