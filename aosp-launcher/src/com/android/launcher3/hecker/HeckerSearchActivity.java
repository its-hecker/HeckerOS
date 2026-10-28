// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Local, user-initiated app and shortcut search through the current HOME's LauncherApps access. */
public final class HeckerSearchActivity extends Activity {
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final List<Entry> index = new ArrayList<>(), results = new ArrayList<>();
    private EditText search;
    private TextView status;
    private BaseAdapter adapter;
    private LauncherApps launcher;
    private int generation;
    private Future<?> task;
    private boolean ready, resumed;
    private Runnable stopGameLibrary;

    private static final class Entry {
        final ComponentName component; final UserHandle user; final String label, packageName;
        final ShortcutInfo shortcut; Drawable icon;
        Entry(ComponentName component, UserHandle user, String label, String packageName, ShortcutInfo shortcut) {
            this.component = component; this.user = user; this.label = label; this.packageName = packageName; this.shortcut = shortcut;
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); launcher = getSystemService(LauncherApps.class);
        LinearLayout page = HeckerStudio.column(this);
        TextView back = HeckerStudio.label(this, "‹  " + HeckerStudio.text(this, "universal_search"), 25); back.setOnClickListener(v -> finish()); page.addView(back);
        search = new EditText(this); search.setSingleLine(true); search.setTextColor(0xfff4f4ee);
        search.setHintTextColor(0xffb8c6cc); search.setHint(HeckerStudio.text(this, "search_hint"));
        page.addView(search); status = HeckerStudio.label(this, HeckerStudio.text(this, "loading"), 13); page.addView(status);
        ListView list = new ListView(this); list.setDivider(null);
        adapter = new BaseAdapter() {
            public int getCount() { return results.size(); }
            public Object getItem(int p) { return results.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int position, View convert, ViewGroup parent) {
                Entry e = results.get(position);
                LinearLayout row = new LinearLayout(HeckerSearchActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, HeckerStudio.dp(HeckerSearchActivity.this, 8), 0, HeckerStudio.dp(HeckerSearchActivity.this, 8));
                ImageView image = new ImageView(HeckerSearchActivity.this); image.setImageDrawable(e.icon);
                row.addView(image, new LinearLayout.LayoutParams(HeckerStudio.dp(HeckerSearchActivity.this, 42), HeckerStudio.dp(HeckerSearchActivity.this, 42)));
                TextView label = HeckerStudio.label(HeckerSearchActivity.this, e.label + "\n" + (e.shortcut == null ? HeckerStudio.text(HeckerSearchActivity.this, "app") : HeckerStudio.text(HeckerSearchActivity.this, "shortcut")), 16);
                label.setPadding(HeckerStudio.dp(HeckerSearchActivity.this, 14), 0, 0, 0); row.addView(label); return row;
            }
        };
        list.setAdapter(adapter); list.setOnItemClickListener((parent, view, p, id) -> launch(results.get(p)));
        page.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        // ListView owns scrolling; a second ScrollView would break list measurement.
        page.setPadding(HeckerStudio.dp(this, 20), HeckerStudio.dp(this, 12), HeckerStudio.dp(this, 20), 0); page.setBackgroundColor(0xff10161c); setContentView(page);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(HeckerStudio.dp(this, 20) + i.left, i.top, HeckerStudio.dp(this, 20) + i.right, i.bottom); return insets;
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int count, int after) { }
            public void onTextChanged(CharSequence s, int a, int before, int count) { if (ready) query(); }
            public void afterTextChanged(Editable s) { }
        });
        String initial = getIntent().getStringExtra("query");
        if (initial != null && initial.length() <= 200) { search.setText(initial); search.setSelection(search.length()); }
        search.requestFocus(); search.post(() -> getSystemService(InputMethodManager.class).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT));
    }
    @Override protected void onResume() {
        super.onResume(); resumed=true;
        stopGameLibrary=HeckerGameLibrary.observe(this,()->{if(resumed)load();});load();
    }
    private void load() {
        ready = false; int request = ++generation; if (task != null) task.cancel(true);
        results.clear();adapter.notifyDataSetChanged();
        task = work.submit(() -> {
            List<Entry> entries = new ArrayList<>(); boolean shortcutsAllowed = launcher.hasShortcutHostPermission();
            for (UserHandle user : launcher.getProfiles()) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    if (getSystemService(UserManager.class).isQuietModeEnabled(user)) continue;
                    Set<String> visiblePackages = new HashSet<>();
                    for (LauncherActivityInfo app : launcher.getActivityList(null, user)) {
                        if (HeckerStudio.hidden(this, app.getComponentName(), user)) continue;
                        visiblePackages.add(app.getComponentName().getPackageName());
                        entries.add(new Entry(app.getComponentName(), user, HeckerIconStudio.label(this, app.getComponentName(), user, app.getLabel()).toString(), app.getComponentName().getPackageName(), null));
                    }
                    if (shortcutsAllowed && HeckerStudio.enabled(this, "search_shortcuts", true)) {
                        LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                        List<ShortcutInfo> shortcuts = launcher.getShortcuts(q, user);
                        if (shortcuts != null) for (ShortcutInfo shortcut : shortcuts) {
                            if (!shortcut.isEnabled() || !visiblePackages.contains(shortcut.getPackage()) || HeckerStudio.hidden(this, shortcut.getActivity(), user)) continue;
                            entries.add(new Entry(shortcut.getActivity(), user, String.valueOf(shortcut.getShortLabel()), shortcut.getPackage(), shortcut));
                        }
                    }
                } catch (SecurityException | IllegalStateException unavailable) { }
            }
            java.text.Collator collator = java.text.Collator.getInstance(); entries.sort((a, b) -> { int kind = Boolean.compare(a.shortcut != null, b.shortcut != null); return kind != 0 ? kind : collator.compare(a.label, b.label); });
            runOnUiThread(() -> {
                if (isDestroyed() || request != generation) return;
                index.clear(); index.addAll(entries); ready = true;
                status.setText(HeckerStudio.text(this, shortcutsAllowed ? "search_local" : "search_home_required")); query();
            });
        });
    }
    private void query() {
        int request = ++generation; String query = search.getText().toString(); List<Entry> snapshot = new ArrayList<>(index);
        if (task != null) task.cancel(true);
        task = work.submit(() -> {
            List<Entry> found = new ArrayList<>();
            for (Entry e : snapshot) {
                if (Thread.currentThread().isInterrupted()) return;
                if (!StudioPolicy.matches(query, e.label, e.packageName)) continue;
                if (e.shortcut != null && query.trim().isEmpty()) continue;
                if (e.icon == null) try {
                    e.icon = e.shortcut != null ? launcher.getShortcutIconDrawable(e.shortcut, getResources().getDisplayMetrics().densityDpi)
                            : launcher.getActivityList(e.packageName, e.user).stream().filter(a -> a.getComponentName().equals(e.component)).findFirst().map(a -> a.getBadgedIcon(getResources().getDisplayMetrics().densityDpi)).orElse(null);
                } catch (RuntimeException unavailable) { }
                found.add(e); if (found.size() == 100) break;
            }
            runOnUiThread(() -> { if (!isDestroyed() && request == generation) { results.clear(); results.addAll(found); adapter.notifyDataSetChanged(); } });
        });
    }
    private void launch(Entry e) {
        try {
            if (e.shortcut != null) launcher.startShortcut(e.packageName, e.shortcut.getId(), null, null, e.user);
            else launcher.startMainActivity(e.component, e.user, null, null);
            finish();
        } catch (RuntimeException unavailable) { Toast.makeText(this, HeckerStudio.text(this, "launch_unavailable"), Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onStop() {
        resumed=false;if(stopGameLibrary!=null){stopGameLibrary.run();stopGameLibrary=null;}
        generation++; if (task != null) task.cancel(true); super.onStop();
    }
    @Override protected void onDestroy() { work.shutdownNow(); super.onDestroy(); }
}
