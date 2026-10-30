// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Launcher-owned Studio. All changes use the same preferences consumed by native Launcher3. */
public final class HeckerStudioActivity extends Activity {
    private LinearLayout page;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int widgetId;
    private String section;
    private ComponentName component;
    private UserHandle user;
    private HeckerClockView clock;
    private boolean pickingWidget;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        pickingWidget = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
        if (pickingWidget) setResult(RESULT_CANCELED);
        if (pickingWidget) {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
            if (info == null || !new ComponentName(this, HeckerClockWidget.class).equals(info.provider)) { finish(); return; }
        }
        section = getIntent().getStringExtra("section"); if (section == null) section = pickingWidget ? "clock" : "root";
        component = getIntent().getParcelableExtra("component", ComponentName.class);
        user = getIntent().getParcelableExtra("user", UserHandle.class); if (user == null) user = android.os.Process.myUserHandle();
        if (saved != null && saved.containsKey("section")) section = saved.getString("section");
        render();
    }
    private String t(String key) { return HeckerStudio.text(this, key); }
    private void render() {
        page = HeckerStudio.column(this);
        String title = section.equals("root") ? "launcher_studio" : section.equals("app") ? "app_customization" : section;
        TextView heading = HeckerStudio.label(this, "‹  " + t(title), 28); heading.setOnClickListener(v -> finish()); page.addView(heading);
        switch (section) {
            case "root": root(); break;
            case "home": home(); break;
            case "appearance": appearance(); break;
            case "privacy": privacy(); break;
            case "glass": glass(); break;
            case "clock": clocks(); break;
            case "icons": icons(); break;
            case "search": search(); break;
            case "app": app(); break;
            default: root();
        }
        HeckerStudio.showPage(this, page);
    }
    private void root() {
        page.addView(HeckerStudio.label(this, t("studio_summary"), 15));
        category("clock", "clock_summary"); category("home", "home_summary");
        category("appearance", "appearance_summary"); category("privacy", "privacy_summary");
        button("universal_search", v -> HeckerStudio.open(this, "search"));
    }
    private void category(String key, String summary) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); int d = HeckerStudio.dp(this, 16);
        row.setPadding(d, d, d, d); row.setBackground(HeckerStudio.panel(0xff1b2832, HeckerStudio.dp(this, 24), 0xff314650));
        HeckerCategoryArt art = new HeckerCategoryArt(this, key); row.addView(art, new LinearLayout.LayoutParams(HeckerStudio.dp(this, 86), HeckerStudio.dp(this, 86)));
        LinearLayout labels = HeckerStudio.column(this); labels.setPadding(d, 0, 0, 0);
        labels.addView(HeckerStudio.label(this, t(key), 21)); labels.addView(HeckerStudio.label(this, t(summary), 13));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> HeckerStudio.open(this, key)); row.setContentDescription(t(key) + ". " + t(summary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = HeckerStudio.dp(this, 14); page.addView(row, params);
    }
    private void home() {
        page.addView(HeckerStudio.label(this, t("layout_migration"), 14));
        toggle("home_depth", true); toggle("home_depth_wake", true);
        page.addView(HeckerStudio.label(this, t("home_depth_summary"), 14));
        slider("home_columns", 5, 3, 7, false); slider("home_rows", 5, 4, 9, false);
        slider("drawer_columns", 5, 3, 8, false); slider("drawer_rows", 6, 3, 9, false);
        slider("home_spacing", 0, 0, 18, false); slider("drawer_spacing", 0, 0, 18, false); slider("dock_count", 5, 3, 7, false);
        Switch addApps = new Switch(this); addApps.setText(t("add_new_apps")); addApps.setTextColor(0xfff5f2e8);
        addApps.setChecked(com.android.launcher3.SessionCommitReceiver.isEnabled(
                this, android.os.Process.myUserHandle()));
        addApps.setOnCheckedChangeListener((button, checked) -> HeckerStudio.prefs(this).edit()
                .putBoolean(com.android.launcher3.SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY, checked).apply()); page.addView(addApps);
        toggle("layout_lock", false); toggle("hecker_double_tap_sleep", true, false); toggle("hecker_swipe_down", true, false);
        button("universal_search", v -> HeckerStudio.open(this, "search"));
    }
    private void appearance() {
        button("flow_preview_apply",v->{HeckerStudio.prefs(this).edit()
            .putBoolean(HeckerStudio.PREFIX+"glass_icons",true).putInt(HeckerStudio.PREFIX+"glass_icon_style",1)
            .putBoolean(HeckerStudio.PREFIX+"dock_search",true).putBoolean(HeckerStudio.PREFIX+"google_search",false)
            .putInt(HeckerStudio.PREFIX+"dock_search_style",1).putString("hecker_glass","1")
            .putInt("hecker_glass_opacity",82).putInt("hecker_glass_frost",24).putInt("hecker_glass_tint",0xff232535)
            .putInt("hecker_glass_border",2).apply();HeckerIconStudio.clear();});
        button("glass", v -> HeckerStudio.open(this, "glass")); button("icons", v -> HeckerStudio.open(this, "icons"));
    }
    private void glass() {
        HeckerGlassPreview preview = new HeckerGlassPreview(this); page.addView(preview, new LinearLayout.LayoutParams(-1, HeckerStudio.dp(this, 160)));
        choice("glass_presets", new String[]{t("clear"), t("frosted"), t("smoked"), t("violet")}, selected -> {
            int[] opacity = {25, 72, 82, 66}, frost = {0, 18, 22, 18}, tint = {0xff30232e, 0xff30232e, 0xff19151e, 0xff49303f};
            HeckerStudio.prefs(this).edit().putString("hecker_glass", "1").putInt("hecker_glass_opacity", opacity[selected])
                    .putInt("hecker_glass_frost", frost[selected]).putInt("hecker_glass_tint", tint[selected]).putInt("hecker_glass_border", 1).apply(); render();
        });
        choice("glass_mode", new String[]{t("off"), t("frosted"), t("liquid")}, selected -> HeckerStudio.prefs(this).edit().putString("hecker_glass", Integer.toString(selected)).apply());
        glassSlider("opacity", 72, 0, 100); glassSlider("frost", 18, 0, 32); glassSlider("border", 1, 0, 4);
        choice("tint", new String[]{t("ice"), t("mint"), t("violet"), t("smoked"), t("warm")}, selected -> {
            int[] colors = {0xffaac8da, 0xff86c9b2, 0xff8974b7, 0xff15232f, 0xffd9bba0};
            HeckerStudio.prefs(this).edit().putInt("hecker_glass_tint", colors[selected]).apply();
        });
        // Full RGB tint control also allows colors outside the presets.
        int tint = HeckerStudio.prefs(this).getInt("hecker_glass_tint", 0xff30232e);
        for (int channel = 0; channel < 3; channel++) {
            final int shift = (2 - channel) * 8; final String name = new String[]{"red", "green", "blue"}[channel];
            rawSlider(name, tint >> shift & 255, 0, 255, value -> {
                int color = HeckerStudio.prefs(this).getInt("hecker_glass_tint", 0xff30232e);
                HeckerStudio.prefs(this).edit().putInt("hecker_glass_tint", (color & ~(255 << shift)) | value << shift | 0xff000000).apply();
            }, true);
        }
        for (String target : new String[]{"dock", "folders", "search", "widgets"}) toggle("hecker_glass_" + target, !target.equals("dock"), false);
        for (String position : new String[]{"tl", "tr", "br", "bl"}) {
            int current; try { current = Integer.parseInt(HeckerStudio.prefs(this).getString("hecker_glass_corner_" + position, "28")); } catch (RuntimeException bad) { current = 28; }
            rawSlider("corner_" + position, current, 0, 48, value -> HeckerStudio.prefs(this).edit().putString("hecker_glass_corner_" + position, Integer.toString(value)).apply(), true);
        }
    }
    private void clocks() {
        clock = new HeckerClockView(this, widgetId);
        Spinner picker = new Spinner(this); String[] names = new String[StudioPolicy.CLOCKS.length]; int selected = 0;
        for (int i = 0; i < names.length; i++) { names[i] = t("clock_" + StudioPolicy.CLOCKS[i]); if (clock.style().equals(StudioPolicy.CLOCKS[i])) selected = i; }
        ArrayAdapter<String> options = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        picker.setAdapter(options); picker.setSelection(selected); picker.setContentDescription(t("clock")); page.addView(picker);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String style = StudioPolicy.CLOCKS[position];
                if (clock.style().equals(style)) return;
                HeckerStudio.prefs(HeckerStudioActivity.this).edit().putString(HeckerStudio.PREFIX + (pickingWidget ? "clock_widget_" + widgetId : "clock_style"), style).apply();
                clock.setStyle(style);
            }
        });
        page.addView(clock, new LinearLayout.LayoutParams(-1, HeckerStudio.dp(this, 240)));
        if (!pickingWidget) {
            toggle("clock_home", true);
            page.addView(HeckerStudio.label(this, t("clock_home_summary"), 14));
        }
        choice("clock_palette", new String[]{t("aurora"), t("sunrise"), t("lagoon"), t("orchid"), t("classic")},
                selectedPalette -> HeckerStudio.prefs(this).edit().putInt(HeckerStudio.PREFIX + "clock_palette", selectedPalette).apply());
        toggle("clock_animation", true); toggle("clock_messages", true); toggle("clock_weather", true);
        page.addView(HeckerStudio.label(this, t("clock_weather_summary"), 13));
        page.addView(HeckerStudio.label(this, t("clock_metrics"), 13));
        button("apply_clock", v -> {
            if (pickingWidget) {
                new HeckerClockWidget().onUpdate(this, AppWidgetManager.getInstance(this), new int[]{widgetId});
                setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish();
            } else {
                HeckerStudio.prefs(this).edit().putBoolean(HeckerStudio.PREFIX + "clock_home", true).apply();
                Toast.makeText(this, t("clock_applied"), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void icons() {
        toggle("glass_icons", true);
        choice("glass_icon_style",new String[]{t("glass_icon_smoked"),t("glass_icon_flow")},i->{HeckerStudio.prefs(this).edit().putInt(HeckerStudio.PREFIX+"glass_icon_style",i).apply();HeckerIconStudio.clear();});
        page.addView(HeckerStudio.label(this, t("glass_icons_summary"), 14));
        button("icon_pack", v -> choosePack(pack -> { HeckerIconStudio.clear(); HeckerStudio.prefs(this).edit().putString(HeckerStudio.PREFIX + "icon_pack", pack).apply(); }));
        choice("icon_shape", new String[]{t("system"), t("circle"), t("squircle"), t("rounded_square")}, selected -> HeckerStudio.prefs(this).edit().putInt(HeckerStudio.PREFIX + "icon_shape", selected).apply());
        slider("icon_size", 100, 65, 125, false);
        toggle("home_labels", true); slider("home_label_size", 13, 9, 20, false);
        toggle("drawer_labels", true); slider("drawer_label_size", 13, 9, 20, false);
        toggle("badges", true); button("notification_access", v -> startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        page.addView(HeckerStudio.label(this, t("badges_summary"), 13));
        button("individual_icons", v -> chooseApp(false));
    }
    private void app() {
        if (component == null) return;
        String key = HeckerStudio.appKey(this, component, user);
        page.addView(HeckerStudio.label(this, component.getPackageName(), 14));
        EditText label = new EditText(this); label.setSingleLine(true); label.setTextColor(0xfff5f2e8); label.setHintTextColor(0xff9daeb6);
        label.setHint(t("renamed_label")); label.setText(HeckerStudio.value(this, "label_" + key, "")); page.addView(label);
        button("save_label", v -> HeckerStudio.prefs(this).edit().putString(HeckerStudio.PREFIX + "label_" + key, label.getText().toString().trim()).apply());
        button("choose_icon", v -> choosePack(pack -> { if (pack.isEmpty()) HeckerStudio.prefs(this).edit().putString(HeckerStudio.PREFIX + "icon_" + key, "@system").apply(); else chooseDrawable(pack, key); }));
        button("reset_app", v -> { HeckerStudio.prefs(this).edit().remove(HeckerStudio.PREFIX + "label_" + key).remove(HeckerStudio.PREFIX + "icon_" + key).apply(); render(); });
        button("app_lock", v -> HeckerStudio.appLock(this, component, user));
        Switch hidden = new Switch(this); hidden.setText(t("hide_app")); hidden.setTextColor(0xfff5f2e8); hidden.setChecked(HeckerStudio.hidden(this, component, user));
        hidden.setOnCheckedChangeListener((button, checked) -> HeckerStudio.hide(this, component, user, checked)); page.addView(hidden);
        page.addView(HeckerStudio.label(this, t("hide_summary"), 13));
    }
    private void privacy() {
        button("app_lock", v -> HeckerStudio.appLock(this, null, user));
        button("hidden_apps", v -> chooseApp(true)); page.addView(HeckerStudio.label(this, t("hide_summary"), 14));
    }
    private void search() {
        toggle("dock_search", true); toggle("google_search", false);
        choice("dock_search_style",new String[]{t("dock_search_full"),t("dock_search_flow")},i->HeckerStudio.prefs(this).edit().putInt(HeckerStudio.PREFIX+"dock_search_style",i).apply());
        page.addView(HeckerStudio.label(this, t("google_search_summary"), 14));
        toggle("universal_search", true); toggle("search_shortcuts", true);
        page.addView(HeckerStudio.label(this, t("search_local"), 14)); button("try_search", v -> HeckerStudio.search(this));
    }
    private interface Pick { void accept(String value); }
    private void choosePack(Pick chosen) {
        worker.execute(() -> {
            List<ResolveInfo> packs = HeckerIconStudio.packs(this); String[] names = new String[packs.size() + 1]; names[0] = t("system");
            for (int i = 0; i < packs.size(); i++) names[i + 1] = packs.get(i).loadLabel(getPackageManager()).toString();
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this).setTitle(t("icon_pack")).setItems(names, (d, which) -> chosen.accept(which == 0 ? "" : packs.get(which - 1).activityInfo.packageName)).show(); });
        });
    }
    private void chooseDrawable(String pack, String appKey) {
        worker.execute(() -> {
            List<String> names = new ArrayList<>(new TreeSet<>(HeckerIconStudio.filter(this, pack).values()));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (names.isEmpty()) { Toast.makeText(this, t("pack_empty"), Toast.LENGTH_LONG).show(); return; }
                LinearLayout content = HeckerStudio.column(this); EditText filter = new EditText(this); filter.setHint(t("search_icons")); content.addView(filter);
                ListView list = new ListView(this); ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names) {
                    @Override public View getView(int position, View convert, ViewGroup parent) {
                        TextView row = (TextView) super.getView(position, convert, parent); String name = getItem(position); row.setTag(name);
                        row.setCompoundDrawables(null, null, null, null); row.setCompoundDrawablePadding(HeckerStudio.dp(HeckerStudioActivity.this, 12));
                        worker.execute(() -> {
                            android.graphics.drawable.Drawable icon = null;
                            try { android.content.res.Resources r = getPackageManager().getResourcesForApplication(pack); int id = r.getIdentifier(name, "drawable", pack); if (id != 0) icon = r.getDrawable(id, null); } catch (RuntimeException | PackageManager.NameNotFoundException ignored) { }
                            final android.graphics.drawable.Drawable loaded = icon;
                            runOnUiThread(() -> { if (!isDestroyed() && name.equals(row.getTag()) && loaded != null) { int size = HeckerStudio.dp(HeckerStudioActivity.this, 36); loaded.setBounds(0, 0, size, size); row.setCompoundDrawables(loaded, null, null, null); } });
                        }); return row;
                    }
                }; list.setAdapter(adapter);
                content.addView(list, new LinearLayout.LayoutParams(-1, HeckerStudio.dp(this, 380)));
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle(t("choose_icon")).setView(content).setNegativeButton(android.R.string.cancel, null).create();
                filter.addTextChangedListener(new android.text.TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(android.text.Editable e) {} public void onTextChanged(CharSequence s, int a, int b, int c) { adapter.getFilter().filter(s); } });
                list.setOnItemClickListener((parent, view, position, id) -> { HeckerIconStudio.clear(); HeckerStudio.prefs(this).edit().putString(HeckerStudio.PREFIX + "icon_" + appKey, pack + "|" + adapter.getItem(position)).apply(); dialog.dismiss(); }); dialog.show();
            });
        });
    }
    private void chooseApp(boolean onlyHidden) {
        worker.execute(() -> {
            List<LauncherActivityInfo> apps = new ArrayList<>(); LauncherApps launcher = getSystemService(LauncherApps.class);
            for (UserHandle profile : launcher.getProfiles()) try { for (LauncherActivityInfo app : launcher.getActivityList(null, profile)) if (!onlyHidden || HeckerStudio.hidden(this, app.getComponentName(), profile)) apps.add(app); } catch (RuntimeException ignored) { }
            apps.sort((a, b) -> java.text.Collator.getInstance().compare(a.getLabel().toString(), b.getLabel().toString()));
            String[] names = new String[apps.size()]; for (int i = 0; i < names.length; i++) names[i] = apps.get(i).getLabel() + " · " + apps.get(i).getComponentName().getPackageName();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this).setTitle(t(onlyHidden ? "hidden_apps" : "individual_icons")).setItems(names, (d, which) -> HeckerStudio.editIcon(this, apps.get(which).getComponentName(), apps.get(which).getUser())).setNegativeButton(android.R.string.cancel, null).show();
            });
        });
    }
    private Button button(String key, View.OnClickListener clicked) {
        Button b = new Button(this); b.setText(t(key)); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(0xffe9f4ff); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(HeckerStudio.dp(this, 56));
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setMaxLines(3); b.setStateListAnimator(null);
        int pad = HeckerStudio.dp(this, 18); b.setPadding(pad, HeckerStudio.dp(this, 14), pad, HeckerStudio.dp(this, 14));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x407db6ed),
                HeckerStudio.panel(0xff203140, HeckerStudio.dp(this, 18), 0xff3c5267), null));
        b.setOnClickListener(clicked);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = HeckerStudio.dp(this, 12);
        page.addView(b, params); return b;
    }
    private void toggle(String key, boolean fallback) { toggle(key, fallback, true); }
    private void toggle(String key, boolean fallback, boolean studio) {
        String name = studio ? HeckerStudio.PREFIX + key : key;
        Switch v = new Switch(this); v.setText(t(key)); v.setTextColor(0xfff5f2e8); v.setMinHeight(HeckerStudio.dp(this, 52));
        v.setChecked(HeckerStudio.prefs(this).getBoolean(name, fallback));
        v.setOnCheckedChangeListener((button, checked) -> HeckerStudio.prefs(this).edit().putBoolean(name, checked).apply()); page.addView(v);
    }
    private interface NumberChange { void accept(int value); }
    private void choice(String key, String[] options, java.util.function.IntConsumer picked) {
        button(key, v -> new AlertDialog.Builder(this).setTitle(t(key)).setItems(options, (dialog, which) -> picked.accept(which)).show());
    }
    private void slider(String key, int fallback, int min, int max, boolean live) {
        rawSlider(key, HeckerStudio.number(this, key, fallback, min, max), min, max, value -> HeckerStudio.prefs(this).edit().putInt(HeckerStudio.PREFIX + key, value).apply(), live);
    }
    private void glassSlider(String key, int fallback, int min, int max) {
        rawSlider(key, HeckerStudio.prefs(this).getInt("hecker_glass_" + key, fallback), min, max, value -> HeckerStudio.prefs(this).edit().putInt("hecker_glass_" + key, value).apply(), true);
    }
    private void rawSlider(String key, int current, int min, int max, NumberChange changed, boolean live) {
        TextView label = HeckerStudio.label(this, t(key) + " · " + current, 16); page.addView(label);
        SeekBar bar = new SeekBar(this); bar.setMax(max - min); bar.setProgress(StudioPolicy.clamp(current, min, max) - min); bar.setContentDescription(t(key)); page.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar seek) { }
            public void onProgressChanged(SeekBar seek, int value, boolean fromUser) { label.setText(t(key) + " · " + (value + min)); if (fromUser && live) changed.accept(value + min); }
            public void onStopTrackingTouch(SeekBar seek) { if (!live) changed.accept(seek.getProgress() + min); }
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("section", section); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
