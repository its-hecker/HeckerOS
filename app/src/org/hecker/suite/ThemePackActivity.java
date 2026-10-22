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
    private static final String[] CATEGORIES = {"signal_icons", "wifi_icons", "icon_packs", "battery_style", "charging_animation"};
    private static final String[] ENGINE = {"statusbar_signal", "statusbar_wifi", "system_icons", "battery_style", "charging_animation"};
    private JSONArray catalog;
    private LinearLayout list;
    private int category;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try (java.io.InputStream stream = getAssets().open("theme_catalog.json")) {
            catalog = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("themes");
        } catch (Exception missing) { Toast.makeText(this, R.string.themes_unavailable, Toast.LENGTH_LONG).show(); finish(); return; }
        LinearLayout page = Ui.page(this, R.string.themes_title);
        page.addView(Ui.text(this, getString(R.string.themes_description), 15, false));
        Spinner selector = new Spinner(this);
        selector.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.theme_signal), getString(R.string.theme_wifi), getString(R.string.theme_icons), getString(R.string.theme_battery), getString(R.string.theme_charging)}));
        page.addView(selector);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list);
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int index, long id) { category = index; show(); }
        });
        selector.setSelection(Math.max(0, Math.min(4, getIntent().getIntExtra("category", 0))));
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
            String[] names = category == 0 ? new String[]{"ic_signal_cellular_0_4_bar", "ic_signal_cellular_2_4_bar", "ic_signal_cellular_4_4_bar"}
                    : category == 1 ? new String[]{"ic_wifi_signal_0", "ic_wifi_signal_2", "ic_wifi_signal_4"}
                    : category == 2 ? new String[]{"ic_audio_media", "ic_brightness_full", "ic_camera"} : new String[0];
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
    private void iconOverlays(JSONArray selected, String previous) throws Exception {
        JSONObject data = previous == null || previous.isEmpty() ? new JSONObject() : new JSONObject(previous);
        Map<String, Object> current = new LinkedHashMap<>();
        for (java.util.Iterator<String> keys = data.keys(); keys.hasNext();) {
            String key = keys.next(); current.put(key, data.get(key));
        }
        Map<String, String> overlays = new LinkedHashMap<>();
        if (selected != null) for (int n = 0; n < selected.length(); n++) {
            JSONObject overlay = selected.getJSONObject(n);
            overlays.put(overlay.getString("componentId"), overlay.getString("packageName"));
        }
        // ThemeOverlayController owns these RROs. Persist its native category map
        // so wallpaper/dynamic-color refreshes and reboot keep the same choice.
        String value = new JSONObject(NativeIconPackSelection.apply(current, overlays)).toString();
        if (!Settings.Secure.putString(getContentResolver(), NATIVE_KEY, value)) {
            throw new SecurityException("Could not persist native icon overlays");
        }
    }
    private void apply(JSONObject theme) {
        String previousIcons = null;
        boolean iconsChanged = false;
        try {
            JSONObject data = config();
            JSONArray overlays = theme == null ? null : theme.getJSONArray("overlays");
            if (overlays != null) trusted(overlays);
            JSONObject themes = data.optJSONObject("themes"); if (themes == null) themes = new JSONObject();
            JSONObject categories = data.optJSONObject("categoryThemes"); if (categories == null) categories = new JSONObject();
            JSONObject styles = data.optJSONObject("selectedStyles"); if (styles == null) styles = new JSONObject();
            if (category == 2) {
                previousIcons = Settings.Secure.getString(getContentResolver(), NATIVE_KEY);
                iconOverlays(overlays, previousIcons);
                iconsChanged = true;
            } else {
                String key = ENGINE[category];
                if (theme == null) { themes.remove(key); categories.remove(key); }
                else {
                    String pkg = overlays.getJSONObject(0).getString("packageName");
                    themes.put(key, new JSONObject().put("enabled", true).put("packageName", pkg));
                    categories.put(key, pkg);
                }
            }
            if (theme == null) styles.remove(CATEGORIES[category]); else styles.put(CATEGORIES[category], theme.getString("id"));
            data.put("themes", themes).put("categoryThemes", categories).put("selectedStyles", styles);
            if (!Settings.Secure.putString(getContentResolver(), KEY, data.toString())) throw new SecurityException();
            android.util.Log.i("HeckerThemes", "Applied " + CATEGORIES[category] + ": "
                    + (theme == null ? "default" : theme.optString("id")));
            show();
        } catch (Exception unavailable) {
            android.util.Log.e("HeckerThemes", "Cannot apply " + CATEGORIES[category], unavailable);
            if (iconsChanged) try {
                Settings.Secure.putString(getContentResolver(), NATIVE_KEY, previousIcons);
            } catch (RuntimeException ignored) { }
            Toast.makeText(this, R.string.theme_failed, Toast.LENGTH_LONG).show();
        }
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
