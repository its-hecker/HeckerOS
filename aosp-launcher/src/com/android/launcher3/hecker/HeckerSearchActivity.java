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
    private boolean ready, resumed, shortcutsAvailable;
    private Runnable stopGameLibrary;

    private static final class Entry {
        final ComponentName component; final UserHandle user; final String label, packageName;
        final ShortcutInfo shortcut; Drawable icon;
        Entry(ComponentName component, UserHandle user, String label, String packageName, ShortcutInfo shortcut) {
            this.component = component; this.user = user; this.label = label; this.packageName = packageName; this.shortcut = shortcut;
        }
    }
    private static final class ResultRow { ImageView icon; TextView title, kind; Entry entry; }
    private int dp(int n) { return HeckerStudio.dp(this,n); }
    private ImageButton iconButton(int icon,String label,Runnable click) {
        ImageButton button=new ImageButton(this);button.setImageResource(icon);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(0xffc7bfce));
        button.setBackgroundColor(0);button.setContentDescription(HeckerStudio.text(this,label));
        button.setOnClickListener(v->click.run());return button;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); launcher=getSystemService(LauncherApps.class);
        LinearLayout page=HeckerStudio.column(this),header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(iconButton(HeckerStudio.res(this,"drawable","hs_search_back"),"search_back",this::finish),new LinearLayout.LayoutParams(dp(48),dp(56)));
        TextView title=HeckerStudio.label(this,HeckerStudio.text(this,"universal_search"),22);
        title.setAccessibilityHeading(true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(iconButton(android.R.drawable.ic_menu_preferences,"search_settings",()->HeckerStudio.open(this,"search")),new LinearLayout.LayoutParams(dp(48),dp(48)));
        page.addView(header);
        LinearLayout field=new LinearLayout(this);field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(16),0,dp(4),0);field.setBackground(HeckerStudio.panel(0xff29252f,dp(28),0x264b4452));
        ImageView mark=new ImageView(this);mark.setImageResource(android.R.drawable.ic_menu_search);
        mark.setImageTintList(android.content.res.ColorStateList.valueOf(0xffc7bfce));field.addView(mark,new LinearLayout.LayoutParams(dp(22),dp(22)));
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(0xfff5f0f7);search.setHintTextColor(0xffc7bfce);
        search.setHint(HeckerStudio.text(this,"search_hint"));search.setTextSize(16);search.setBackgroundColor(0);search.setPadding(dp(12),0,0,0);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.addView(search,new LinearLayout.LayoutParams(0,dp(54),1));
        ImageButton clear=iconButton(android.R.drawable.ic_menu_close_clear_cancel,"search_clear",()->search.setText(""));
        clear.setVisibility(View.INVISIBLE);field.addView(clear,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(field);
        status=HeckerStudio.label(this,HeckerStudio.text(this,"loading"),12);status.setTextColor(0xffc7bfce);page.addView(status);
        ListView list=new ListView(this);list.setDivider(null);list.setVerticalScrollBarEnabled(false);
        adapter=new BaseAdapter(){
            public int getCount(){return results.size();}
            public Object getItem(int p){return results.get(p);}
            public long getItemId(int p){return p;}
            public View getView(int position,View convert,ViewGroup parent){
                Entry entry=results.get(position);LinearLayout row=convert instanceof LinearLayout?(LinearLayout)convert:null;
                ResultRow holder;
                if(row==null){
                    row=new LinearLayout(HeckerSearchActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(16),dp(12));
                    row.setBackground(new android.graphics.drawable.InsetDrawable(HeckerStudio.panel(0xff19161d,dp(20),0),0,dp(3),0,dp(3)));
                    holder=new ResultRow();holder.icon=new ImageView(HeckerSearchActivity.this);row.addView(holder.icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
                    LinearLayout labels=HeckerStudio.column(HeckerSearchActivity.this);labels.setPadding(dp(14),0,0,0);
                    holder.title=HeckerStudio.label(HeckerSearchActivity.this,"",16);holder.title.setPadding(0,0,0,0);holder.title.setSingleLine();holder.title.setEllipsize(TextUtils.TruncateAt.END);
                    holder.kind=HeckerStudio.label(HeckerSearchActivity.this,"",12);holder.kind.setTextColor(0xffc7bfce);holder.kind.setPadding(0,dp(3),0,0);
                    labels.addView(holder.title);labels.addView(holder.kind);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.setTag(holder);
                }else holder=(ResultRow)row.getTag();
                holder.entry=entry;holder.title.setText(entry.label);holder.kind.setText(HeckerStudio.text(HeckerSearchActivity.this,entry.shortcut==null?"app":"shortcut"));
                final ResultRow bound=holder;
                holder.icon.setImageDrawable(entry.shortcut!=null?entry.icon:HeckerIconStudio.icon(HeckerSearchActivity.this,entry.component,entry.user,entry.icon,
                        ()->{if(bound.entry==entry&&!isDestroyed())adapter.notifyDataSetChanged();},row,true));return row;
            }
        };
        list.setAdapter(adapter);list.setOnItemClickListener((parent,view,p,id)->launch(results.get(p)));
        page.addView(list,new LinearLayout.LayoutParams(-1,0,1));page.setBackgroundColor(0xff000000);setContentView(page);
        page.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime()|WindowInsets.Type.displayCutout());
            v.setPadding(dp(16)+i.left,i.top,dp(16)+i.right,i.bottom);return WindowInsets.CONSUMED;
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int count,int after){}
            public void onTextChanged(CharSequence s,int a,int before,int count){clear.setVisibility(s.length()==0?View.INVISIBLE:View.VISIBLE);if(ready)query();}
            public void afterTextChanged(Editable s){}
        });
        String initial=getIntent().getStringExtra("query");if(initial!=null&&initial.length()<=200){search.setText(initial);search.setSelection(search.length());}
        search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH&&!results.isEmpty()){launch(results.get(0));return true;}return false;});
        search.requestFocus();search.post(()->getSystemService(InputMethodManager.class).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT));
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
                index.clear(); index.addAll(entries); ready = true; shortcutsAvailable = shortcutsAllowed;
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
            String normalized=query.trim().toLowerCase(Locale.getDefault());
            found.sort(Comparator.comparingInt((Entry e)->e.label.toLowerCase(Locale.getDefault()).equals(normalized)?0:e.label.toLowerCase(Locale.getDefault()).startsWith(normalized)?1:2)
                    .thenComparing(e->e.label,java.text.Collator.getInstance()));
            runOnUiThread(() -> { if (!isDestroyed() && request == generation) {
                results.clear(); results.addAll(found); adapter.notifyDataSetChanged();
                if(!query.isBlank())status.setText(HeckerStudio.text(this,found.isEmpty()?"search_empty":"search_results"));
                else status.setText(HeckerStudio.text(this,shortcutsAvailable?"search_local_short":"search_home_required"));
            } });
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
