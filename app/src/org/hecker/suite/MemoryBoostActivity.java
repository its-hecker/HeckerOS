// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IHeckerMemoryService;
import android.os.ServiceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.android.internal.hecker.HeckerMemoryRules;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Virtualized installed-app list; rules belong to this Android user. */
public final class MemoryBoostActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Entry> installed = new ArrayList<>(), visible = new ArrayList<>();
    private final Set<String> manageable = new HashSet<>();
    private final Apps adapter = new Apps();
    private JSONObject rules = new JSONObject();
    private String query = "";
    private boolean system, serviceReady;
    private Button boost;
    private static final class Entry {
        final ApplicationInfo app; final String label;
        Entry(ApplicationInfo a, String l) { app = a; label = l; }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, R.string.memory_title);
        page.addView(Ui.text(this, getString(R.string.memory_description), 14, false));
        boost = Ui.button(this); boost.setText(R.string.memory_boost);
        boost.setOnClickListener(v -> boost()); page.addView(boost);
        Switch show = new Switch(this); show.setText(R.string.memory_system);
        show.setTextColor(getColor(R.color.hecker_foreground));
        show.setChecked(state != null && state.getBoolean("system")); system = show.isChecked();
        show.setOnCheckedChangeListener((v, checked) -> { system = checked; filter(); }); page.addView(show);
        EditText search = new EditText(this); search.setSingleLine(true); search.setHint(R.string.search_apps);
        search.setTextColor(getColor(R.color.hecker_foreground)); search.setHintTextColor(getColor(R.color.hecker_secondary));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            public void afterTextChanged(Editable e) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { query=s.toString().toLowerCase(Locale.ROOT); filter(); }
        }); page.addView(search);
        ListView list = new ListView(this); list.setAdapter(adapter); list.setDivider(null);
        list.setOnItemClickListener((p, v, pos, id) -> options(visible.get(pos)));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.content(this, page);
        worker.execute(() -> {
            List<Entry> found = new ArrayList<>(); Set<String> allowed = new HashSet<>();
            PackageManager pm = getPackageManager();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) found.add(new Entry(app, app.loadLabel(pm).toString()));
            found.sort(Comparator.comparing(e -> e.label, String.CASE_INSENSITIVE_ORDER));
            boolean ready=false;
            try {
                IHeckerMemoryService service = service();
                Bundle status=service==null?null:service.state();
                ready=status!=null&&status.getBoolean("available");
                if(ready&&status.getStringArray("packages")!=null)Collections.addAll(allowed,status.getStringArray("packages"));
            } catch (android.os.RemoteException | RuntimeException failure) { android.util.Log.w("HeckerMemory", "App policy query failed", failure); }
            final boolean available=ready;
            runOnUiThread(() -> { if (isDestroyed()) return; serviceReady=available;boost.setEnabled(available);
                installed.addAll(found);manageable.addAll(allowed);loadRules();filter();
                if(!available)Toast.makeText(this,R.string.memory_failed,Toast.LENGTH_LONG).show(); });
        });
    }
    private IHeckerMemoryService service() { return IHeckerMemoryService.Stub.asInterface(ServiceManager.getService("hecker_memory")); }
    private void loadRules() {
        try { String raw = Settings.Secure.getString(getContentResolver(), HeckerMemoryRules.SETTING); rules = raw == null ? new JSONObject() : new JSONObject(raw); }
        catch (org.json.JSONException | RuntimeException bad) { rules = new JSONObject(); Toast.makeText(this, R.string.memory_save_failed, Toast.LENGTH_LONG).show(); }
    }
    private void filter() {
        visible.clear();
        for (Entry e : installed) {
            if (!system && (e.app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
            if (!query.isEmpty() && !e.label.toLowerCase(Locale.ROOT).contains(query) && !e.app.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            visible.add(e);
        }
        adapter.notifyDataSetChanged();
    }
    private void options(Entry e) {
        if(!serviceReady){Toast.makeText(this,R.string.memory_failed,Toast.LENGTH_LONG).show();return;}
        if (!manageable.contains(e.app.packageName)) { Toast.makeText(this, R.string.memory_protected, Toast.LENGTH_LONG).show(); return; }
        int mask = HeckerMemoryRules.validated(rules.optInt(e.app.packageName));
        int[] titles = {R.string.memory_start, R.string.memory_background, R.string.memory_clean_task, R.string.memory_resident};
        int[] descriptions = {R.string.memory_start_summary, R.string.memory_background_summary, R.string.memory_clean_task_summary, R.string.memory_resident_summary};
        boolean[] checks = new boolean[4];
        LinearLayout content = Ui.page(this, R.string.memory_title);
        for (int i=0; i<4; i++) {
            final int bit=i; checks[i]=(mask & (1<<i)) != 0;
            Switch control = new Switch(this); control.setText(titles[i]); control.setChecked(checks[i]);
            control.setTextColor(getColor(R.color.hecker_foreground));
            control.setOnCheckedChangeListener((button, checked) -> checks[bit]=checked);
            content.addView(control); content.addView(Ui.text(this, getString(descriptions[i]), 13, false));
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(e.label).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int value=0; for (int i=0;i<4;i++) if(checks[i]) value|=1<<i;
                    try {
                        // Re-read before saving, so another page cannot lose unrelated app rules.
                        String raw=Settings.Secure.getString(getContentResolver(),HeckerMemoryRules.SETTING);
                        JSONObject update=raw==null?new JSONObject():new JSONObject(raw);
                        if(value==0)update.remove(e.app.packageName);else update.put(e.app.packageName,value);
                        if(!Settings.Secure.putString(getContentResolver(),HeckerMemoryRules.SETTING,update.toString()))throw new IllegalStateException();
                        rules=update;adapter.notifyDataSetChanged();
                    } catch(org.json.JSONException|RuntimeException unavailable) { Toast.makeText(this,R.string.memory_save_failed,Toast.LENGTH_LONG).show(); }
                }).show();
    }
    private void boost() {
        boost.setEnabled(false);
        worker.execute(() -> {
            Bundle result=null;
            try { IHeckerMemoryService s=service();if(s!=null)result=s.boost(); } catch(android.os.RemoteException|RuntimeException ignored) {}
            final Bundle outcome=result;
            runOnUiThread(() -> {
                if(isDestroyed())return;boost.setEnabled(true);
                String text=outcome==null||!outcome.getBoolean("available")?getString(R.string.memory_failed)
                        :outcome.getInt("processes")==0?getString(R.string.memory_none)
                        :getString(R.string.memory_done,outcome.getInt("apps"),outcome.getInt("processes"));
                Toast.makeText(this,text,Toast.LENGTH_LONG).show();
            });
        });
    }
    private final class Apps extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int position){return visible.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View old,ViewGroup parent){
            Entry e=visible.get(position);LinearLayout row=Ui.card(MemoryBoostActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView icon=new ImageView(MemoryBoostActivity.this);icon.setImageDrawable(e.app.loadIcon(getPackageManager()));
            row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(MemoryBoostActivity.this,42),Ui.dp(MemoryBoostActivity.this,42)));
            LinearLayout labels=new LinearLayout(MemoryBoostActivity.this);labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(Ui.dp(MemoryBoostActivity.this,12),0,0,0);
            labels.addView(Ui.text(MemoryBoostActivity.this,e.label,16,true));
            labels.addView(Ui.text(MemoryBoostActivity.this,getString(!serviceReady?R.string.memory_failed:!manageable.contains(e.app.packageName)?R.string.memory_protected
                    :rules.optInt(e.app.packageName)==0?R.string.memory_default:R.string.memory_active),12,false));
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));return row;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("system",system);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
