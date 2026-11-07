// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import org.hecker.ui.HeckerChoiceSheet;

import android.app.Activity;
import org.hecker.vpn.VpnInput;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Two profile sources, one connection and routing model. No keys are rendered or exported. */
public final class HeckerVpnActivity extends Activity {
    private HeckerVpnClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Bundle state = new Bundle();
    private LinearLayout body;
    private TextView status, detail, warning;
    private Button connect, retryPeer, countries, profiles;
    private boolean countriesTab = true, busy, started;
    private android.net.Uri pendingImport;
    private final java.util.concurrent.ExecutorService appLoader=java.util.concurrent.Executors.newSingleThreadExecutor();
    private List<RouteApp> routingApps=List.of();
    private boolean routingAppsLoaded,loadingRoutingApps;private int packageGeneration;
    private Runnable routingAppsChanged=()->{};
    private AlertDialog appPicker;
    private final android.util.LruCache<String,android.graphics.drawable.Drawable.ConstantState> appIcons=new android.util.LruCache<>(64);
    private final java.util.Set<String> loadingIcons=new java.util.HashSet<>();
    private static final class RouteApp {
        final ApplicationInfo info;final String label,search;
        RouteApp(ApplicationInfo info,String label){this.info=info;this.label=label;search=(label+" "+info.packageName).toLowerCase(java.util.Locale.ROOT);}
    }
    private final android.content.BroadcastReceiver packageChanges=new android.content.BroadcastReceiver(){
        @Override public void onReceive(android.content.Context context,Intent intent){packageGeneration++;routingAppsLoaded=false;appIcons.evictAll();loadRoutingApps();}
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (saved != null) countriesTab = saved.getBoolean("countries", true);
        LinearLayout page = Ui.page(this, R.string.vpn_title), hero = Ui.card(this);
        hero.addView(Ui.text(this, getString(R.string.vpn_title), 28, true));
        status = Ui.text(this, getString(R.string.vpn_disconnected), 18, true); hero.addView(status);
        detail = Ui.text(this, "", 13, false); hero.addView(detail);
        warning = Ui.text(this, "", 13, false); hero.addView(warning);
        connect = Ui.button(this); connect.setText(R.string.vpn_connect);
        connect.setOnClickListener(v -> {
            if (!state.getBoolean("controller_available", true)) {
                operation(HeckerVpnClient.request("state"), true);
            } else if (!state.getBoolean("desired") && !state.getBoolean("cleanup_required")
                    && state.getString("selected", "").isEmpty()) {
                countriesTab = false; renderBody();
                Toast.makeText(this, R.string.vpn_no_profiles, Toast.LENGTH_LONG).show();
            } else operation(HeckerVpnClient.request(state.getBoolean("desired") || state.getBoolean("cleanup_required") ? "disconnect" : "connect"), true);
        });
        hero.addView(connect);
        retryPeer = Ui.button(this); retryPeer.setText(R.string.vpn_retry_peer);
        retryPeer.setOnClickListener(v -> operation(HeckerVpnClient.request("reconnect"), true));
        hero.addView(retryPeer); page.addView(hero);
        countries = Ui.button(this); countries.setText(R.string.vpn_countries);
        profiles = Ui.button(this); profiles.setText(R.string.vpn_profiles);
        countries.setOnClickListener(v -> { countriesTab = true; renderBody(); });
        profiles.setOnClickListener(v -> { countriesTab = false; renderBody(); });
        page.addView(Ui.chips(this, countries, profiles));
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); page.addView(body);
        renderBody(); Ui.scroll(this, page);
        android.content.IntentFilter changes=new android.content.IntentFilter();changes.addAction(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REMOVED);changes.addAction(Intent.ACTION_PACKAGE_CHANGED);changes.addDataScheme("package");
        registerReceiver(packageChanges,changes,android.content.Context.RECEIVER_NOT_EXPORTED);loadRoutingApps();
    }
    @Override protected void onStart() {
        super.onStart(); started = true; client = new HeckerVpnClient(this);
        // Opening Settings must not wait on the privileged kernel socket. Connect probes it
        // when required; profile import and provider setup remain usable independently.
        operation(HeckerVpnClient.request("state"), true); main.postDelayed(poll, 3000);
    }
    @Override protected void onResume() {
        super.onResume(); consumeImport();
    }
    private void consumeImport() {
        if (!started || pendingImport == null) return;
        android.net.Uri uri = pendingImport; pendingImport = null; readImport(uri);
    }
    @Override protected void onStop() {
        if(appPicker!=null)appPicker.dismiss();
        started = false; main.removeCallbacks(poll); if (client != null) client.close(); client = null; busy = false; super.onStop();
    }
    @Override protected void onDestroy(){unregisterReceiver(packageChanges);appLoader.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle saved) { saved.putBoolean("countries", countriesTab); super.onSaveInstanceState(saved); }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (!busy && client != null) client.call(HeckerVpnClient.request("state"), result -> {
                boolean recovered = !state.getBoolean("controller_available", true) && result.getBoolean("controller_available");
                acceptState(result); renderState(); if (recovered) renderBody();
            });
            main.postDelayed(this, 3000);
        }
    };
    private void operation(Bundle request, boolean rebuild) {
        operation(request, rebuild, null);
    }
    private boolean operation(Bundle request, boolean rebuild, java.util.function.Consumer<Bundle> completed) {
        if (client == null || busy) {
            Toast.makeText(this, R.string.vpn_busy, Toast.LENGTH_SHORT).show(); return false;
        }
        busy = true; renderState();
        client.call(request, result -> {
            busy = false; acceptState(result);
            renderState(); if (rebuild) renderBody();
            if (completed != null) completed.accept(result);
        });
        return true;
    }
    private void acceptState(Bundle result) {
        // A transport failure or worker timeout says nothing about saved account/profile data.
        if (result.containsKey("profiles")) state = result;
        else { Bundle retained = new Bundle(state); retained.putAll(result); state = retained; }
    }
    private void renderState() {
        String label = getString(statusLabel(state.getString("status", "disconnected")));
        status.setText(busy ? getString(R.string.vpn_busy) : label);
        boolean tcp = "tcp".equals(state.getString("transport"));
        String text = getString(R.string.vpn_status, tcp ? getString(R.string.vpn_tcp_backend) : "WireGuard kernel", Formatter.formatFileSize(this, state.getLong("rx")), Formatter.formatFileSize(this, state.getLong("tx")));
        ArrayList<Bundle> saved = state.getParcelableArrayList("profiles", Bundle.class);
        if (saved != null) for (Bundle profile : saved) if (profile.getString("id", "").equals(state.getString("selected"))) { text = profile.getString("name", "") + "\n" + text; break; }
        long handshake = state.getLong("handshake");
        if (handshake > 0) text += "\n" + getString(R.string.vpn_handshake, DateUtils.getRelativeTimeSpanString(handshake * 1000, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS));
        detail.setText(text);
        String error = state.getString("error", "");
        boolean peerTimeout = "peer_timeout".equals(state.getString("status"));
        warning.setText(!error.isEmpty() ? error : peerTimeout ? getString(R.string.vpn_peer_help)
                : !tcp && state.containsKey("kernel") && !state.getBoolean("kernel") ? getString(R.string.vpn_kernel_missing) : "");
        retryPeer.setVisibility(peerTimeout && state.getBoolean("desired") ? View.VISIBLE : View.GONE);
        retryPeer.setEnabled(!busy);
        connect.setText(!state.getBoolean("controller_available", true) ? R.string.vpn_retry
                : state.getBoolean("desired") || state.getBoolean("cleanup_required") ? R.string.vpn_disconnect : R.string.vpn_connect);
        connect.setEnabled(!busy);
        setEnabled(body, !busy);
    }
    static int statusLabel(String value) {
        switch (value) {
            case "active": return R.string.vpn_active;
            case "idle": return R.string.vpn_idle;
            case "connecting": return R.string.vpn_connecting;
            case "disconnecting": return R.string.vpn_disconnecting;
            case "waiting_peer": return R.string.vpn_waiting_peer;
            case "peer_timeout": return R.string.vpn_peer_timeout;
            case "waiting_network": return R.string.vpn_waiting_network;
            case "error": return R.string.vpn_error;
            default: return R.string.vpn_disconnected;
        }
    }
    private Button action(LinearLayout parent, int label, Runnable callback) {
        Button button = Ui.button(this); button.setText(label); button.setOnClickListener(v -> callback.run()); parent.addView(button); return button;
    }
    private void renderBody() {
        body.removeAllViews(); countries.setSelected(countriesTab); profiles.setSelected(!countriesTab);
        if (android.os.UserHandle.getUserHandleForUid(android.os.Process.myUid()).getIdentifier() != 0) {
            body.addView(Ui.text(this, getString(R.string.vpn_owner_only), 16, false)); return;
        }
        if (countriesTab) renderCountries(); else renderProfiles();
        LinearLayout note = Ui.card(this);
        note.addView(Ui.text(this, getString(R.string.vpn_routing_scope), 13, false));
        note.addView(Ui.text(this, getString(R.string.vpn_session_note), 13, false)); body.addView(note);
        Ui.styleControls(this, body);
        setEnabled(body, !busy);
    }
    private void renderCountries() {
        LinearLayout tcp = Ui.card(this);
        tcp.addView(Ui.text(this, getString(R.string.vpn_tcp_title), 20, true));
        tcp.addView(Ui.text(this, getString(R.string.vpn_tcp_summary), 14, false));
        tcp.addView(Ui.text(this, getString(R.string.vpn_tcp_public_note), 13, false));
        action(tcp, R.string.vpn_tcp_refresh, () -> operation(HeckerVpnClient.request("tcp_catalog"), true));
        ArrayList<Bundle> servers = state.getParcelableArrayList("tcp_servers", Bundle.class);
        if (servers != null && !servers.isEmpty()) {
            tcp.addView(Ui.text(this, getString(R.string.vpn_tcp_available, servers.size()), 14, true));
            action(tcp, R.string.vpn_tcp_choose, () -> chooseTcpServer(servers));
        }
        action(tcp, R.string.vpn_tcp_paste, () -> pasteConfigDialog(true));
        action(tcp, R.string.vpn_tcp_source, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/iampedii/whitedns-sub"))));
        body.addView(tcp);
        LinearLayout provider = Ui.card(this);
        provider.addView(Ui.text(this, getString(R.string.vpn_free_title), 20, true));
        provider.addView(Ui.text(this, getString(R.string.vpn_free_summary), 14, false));
        action(provider, R.string.vpn_get_free_profile, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.vpnbook.com/freevpn/wireguard-vpn"))));
        action(provider, R.string.vpn_free_mirror, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://static.vpnbook.com/freevpn/wireguard-vpn"))));
        action(provider, R.string.vpn_import, () -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 784));
        action(provider, R.string.vpn_paste, this::pasteConfigDialog);
        body.addView(provider);
    }
    private void chooseTcpServer(ArrayList<Bundle> servers) {
        String[] labels = new String[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            Bundle row = servers.get(i);
            labels[i] = row.getString("name", "") + "\n" + getString(row.getBoolean("tls") ? R.string.vpn_tcp_tls : R.string.vpn_tcp_plain);
        }
        HeckerChoiceSheet.show(this, getString(R.string.vpn_tcp_choose), labels, -1, which -> {
            Bundle request = HeckerVpnClient.request("tcp_connect"); request.putString("id", servers.get(which).getString("id"));
            operation(request, true);
        });
    }
    private void renderProfiles() {
        LinearLayout header = Ui.card(this); action(header, R.string.vpn_import, () -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 784));
        action(header, R.string.vpn_paste, this::pasteConfigDialog);
        action(header, R.string.vpn_tcp_paste, () -> pasteConfigDialog(true));
        action(header, R.string.vpn_get_free_profile, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.vpnbook.com/freevpn/wireguard-vpn"))));
        action(header, R.string.vpn_free_mirror, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://static.vpnbook.com/freevpn/wireguard-vpn"))));
        body.addView(header);
        ArrayList<Bundle> items = state.getParcelableArrayList("profiles", Bundle.class);
        if (items == null || items.isEmpty()) { body.addView(Ui.text(this, getString(R.string.vpn_no_profiles), 15, false)); return; }
        for (Bundle profile : items) {
            String id = profile.getString("id", ""), name = profile.getString("name", "");
            boolean selected = id.equals(state.getString("selected"));
            LinearLayout card = Ui.card(this); card.addView(Ui.text(this, name, 18, true));
            if ("tcp".equals(profile.getString("kind"))) card.addView(Ui.text(this,
                    getString(profile.getBoolean("tls") ? R.string.vpn_tcp_tls : R.string.vpn_tcp_plain), 13, false));
            card.addView(Ui.text(this, getString(routeLabel(profile.getString("mode", "all"))), 14, false));
            if (selected) {
                card.addView(Ui.text(this, getString(R.string.vpn_selected), 13, true));
                action(card, R.string.vpn_routing, () -> routingDialog(profile));
            } else action(card, R.string.vpn_select, () -> { Bundle request = HeckerVpnClient.request("select"); request.putString("id", id); operation(request, true); });
            action(card, R.string.vpn_delete, () -> new AlertDialog.Builder(this).setMessage(getString(R.string.vpn_delete_confirm, name))
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.vpn_delete, (d, w) -> {
                        Bundle request = HeckerVpnClient.request("delete"); request.putString("id", id); operation(request, true);
                    }).show()); body.addView(card);
        }
    }
    private static int routeLabel(String mode) { return mode.equals("include") ? R.string.vpn_route_include : mode.equals("exclude") ? R.string.vpn_route_exclude : R.string.vpn_route_all; }
    private void routingDialog(Bundle profile) {
        String[] modes = {"all", "include", "exclude"};
        String[] labels = {getString(R.string.vpn_route_all), getString(R.string.vpn_route_include), getString(R.string.vpn_route_exclude)};
        int selected = Arrays.asList(modes).indexOf(profile.getString("mode", "all"));
        HeckerChoiceSheet.show(this, getString(R.string.vpn_routing), labels, selected, which -> {
            if (which == 0) applyPolicy("all", new ArrayList<>()); else chooseApps(modes[which], profile.getStringArrayList("apps"));
        });
    }
    private void loadRoutingApps() {
        if(routingAppsLoaded||loadingRoutingApps||isDestroyed())return;
        loadingRoutingApps=true;int generation=packageGeneration;
        appLoader.execute(()->{
            ArrayList<RouteApp> found=new ArrayList<>();boolean success=true;
            try {
                for(ApplicationInfo info:getPackageManager().getInstalledApplications(0)){
                    if(Thread.currentThread().isInterrupted())return;
                    // Resource overlays cannot originate network traffic. Loading their
                    // resources was also the largest source of slow and failed icon loads.
                    if((info.flags&ApplicationInfo.FLAG_HAS_CODE)==0)continue;
                    String label=info.packageName;
                    try{label=info.loadLabel(getPackageManager()).toString();}catch(RuntimeException unavailable){}
                    found.add(new RouteApp(info,label));
                }
                found.sort(Comparator.comparing(app->app.label,String.CASE_INSENSITIVE_ORDER));
            }catch(RuntimeException unavailable){success=false;}
            boolean loaded=success;
            main.post(()->{if(isDestroyed())return;loadingRoutingApps=false;if(generation!=packageGeneration){loadRoutingApps();return;}routingAppsLoaded=loaded;
                if(loaded)routingApps=List.copyOf(found);routingAppsChanged.run();});
        });
    }
    private void routeIcon(RouteApp app,android.widget.ImageView image) {
        String pkg=app.info.packageName;image.setTag(pkg);
        android.graphics.drawable.Drawable.ConstantState cached=appIcons.get(pkg);
        image.setImageDrawable(cached==null?getPackageManager().getDefaultActivityIcon():cached.newDrawable(getResources()));
        if(cached!=null||!loadingIcons.add(pkg)||isDestroyed())return;
        appLoader.execute(()->{
            android.graphics.drawable.Drawable loaded;
            try{loaded=app.info.loadIcon(getPackageManager());}catch(RuntimeException unavailable){loaded=getPackageManager().getDefaultActivityIcon();}
            android.graphics.drawable.Drawable icon=loaded;
            main.post(()->{loadingIcons.remove(pkg);if(isDestroyed())return;
                if(icon.getConstantState()!=null)appIcons.put(pkg,icon.getConstantState());
                if(pkg.equals(image.getTag()))image.setImageDrawable(icon);});
        });
    }
    private void chooseApps(String mode, ArrayList<String> initial) {
        if(appPicker!=null&&appPicker.isShowing())return;
        LinkedHashSet<String> selected=new LinkedHashSet<>(initial==null?List.of():initial);
        LinearLayout page=Ui.card(this);EditText search=new EditText(this);search.setSingleLine();
        search.setHint(R.string.vpn_search_apps);search.setTextColor(getColor(R.color.hecker_foreground));page.addView(search);
        TextView loading=Ui.text(this,getString(R.string.vpn_loading_apps),14,false);page.addView(loading);
        android.widget.ProgressBar progress=new android.widget.ProgressBar(this);page.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,32)));
        android.widget.ListView list=new android.widget.ListView(this);list.setDivider(null);list.setChoiceMode(android.widget.ListView.CHOICE_MODE_NONE);
        page.addView(list,new LinearLayout.LayoutParams(-1,Ui.dp(this,360)));
        ArrayList<RouteApp> filtered=new ArrayList<>();
        android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){
            public int getCount(){return filtered.size();}public Object getItem(int pos){return filtered.get(pos);}public long getItemId(int pos){return pos;}
            public View getView(int pos,View recycled,android.view.ViewGroup parent){
                RouteApp app=filtered.get(pos);LinearLayout row;
                if(recycled instanceof LinearLayout)row=(LinearLayout)recycled;
                else {
                    row=new LinearLayout(HeckerVpnActivity.this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(HeckerVpnActivity.this,4),Ui.dp(HeckerVpnActivity.this,6),0,Ui.dp(HeckerVpnActivity.this,6));
                    android.widget.ImageView image=new android.widget.ImageView(HeckerVpnActivity.this);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    row.addView(image,new LinearLayout.LayoutParams(Ui.dp(HeckerVpnActivity.this,36),Ui.dp(HeckerVpnActivity.this,36)));
                    CheckBox check=new CheckBox(HeckerVpnActivity.this);check.setTextColor(getColor(R.color.hecker_foreground));check.setMinHeight(Ui.dp(HeckerVpnActivity.this,56));check.setMaxLines(3);
                    row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
                }
                CheckBox check=(CheckBox)row.getChildAt(1);check.setOnCheckedChangeListener(null);
                check.setText(app.label+"\n"+app.info.packageName);check.setChecked(selected.contains(app.info.packageName));
                check.setOnCheckedChangeListener((button,on)->{if(on)selected.add(app.info.packageName);else selected.remove(app.info.packageName);});
                row.setOnClickListener(v->check.setChecked(!check.isChecked()));routeIcon(app,(android.widget.ImageView)row.getChildAt(0));return row;
            }
        };
        list.setAdapter(adapter);
        Runnable render=()->{
            filtered.clear();String query=search.getText().toString().toLowerCase(java.util.Locale.ROOT);
            for(RouteApp app:routingApps)if(app.search.contains(query))filtered.add(app);
            adapter.notifyDataSetChanged();progress.setVisibility(loadingRoutingApps?View.VISIBLE:View.GONE);
            loading.setText(loadingRoutingApps?R.string.vpn_loading_apps:routingAppsLoaded?R.string.vpn_no_matching_apps:R.string.vpn_apps_load_failed);
            loading.setVisibility(loadingRoutingApps||filtered.isEmpty()?View.VISIBLE:View.GONE);
        };
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(routeLabel(mode)).setView(page)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.vpn_apply,null).create();
        appPicker=dialog;routingAppsChanged=render;
        dialog.setOnDismissListener(d->{routingAppsChanged=()->{};appPicker=null;});
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}
            public void afterTextChanged(Editable text){}
        });
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!routingAppsLoaded){Toast.makeText(this,R.string.vpn_loading_apps,Toast.LENGTH_SHORT).show();return;}
            if(mode.equals("include")&&selected.isEmpty()){Toast.makeText(this,R.string.vpn_select_apps_first,Toast.LENGTH_SHORT).show();return;}
            applyPolicy(mode,new ArrayList<>(selected));dialog.dismiss();
        }));
        dialog.show();loadRoutingApps();render.run();
    }
    private void applyPolicy(String mode, ArrayList<String> apps) {
        Bundle request = HeckerVpnClient.request("policy"); request.putString("mode", mode); request.putStringArrayList("apps", apps); operation(request, true);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 784 || result != RESULT_OK || data == null || data.getData() == null) return;
        pendingImport = data.getData();
        try {
            int readGrant = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (readGrant != 0) getContentResolver().takePersistableUriPermission(pendingImport, readGrant);
        } catch (SecurityException | IllegalArgumentException unsupported) { /* Some providers only offer the activity grant. */ }
        // The picker result can arrive before onStart; wait for onResume and the new Binder client.
    }
    private void readImport(android.net.Uri uri) {
        new Thread(() -> {
            try {
                String name = null;
                try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (column >= 0) name = cursor.getString(column);
                    }
                } catch (Exception metadataUnavailable) { /* A readable stream need not have a display name. */ }
                String config;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    config = VpnInput.configuration(input);
                }
                String suggested = VpnInput.profileName(name);
                main.post(() -> { if (started) importDialog(suggested, config); });
            } catch (Exception failure) { main.post(() -> {
                if (!started) return;
                String error = failure instanceof IllegalArgumentException ? failure.getMessage() : getString(R.string.vpn_import_access);
                warning.setText(error); Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }); }
        }, "HeckerVPN-import").start();
    }
    /** Paste configuration locally; never place WireGuard private keys in logs or saved UI state. */
    private void pasteConfigDialog() {
        pasteConfigDialog(false);
    }
    private void pasteConfigDialog(boolean tcp) {
        EditText input = new EditText(this);
        input.setHint(tcp ? R.string.vpn_tcp_hint : R.string.vpn_paste_hint);
        input.setTextColor(getColor(R.color.hecker_foreground));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(tcp ? 2 : 8);
        input.setMaxLines(16);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(tcp ? R.string.vpn_tcp_paste : R.string.vpn_paste)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, (d, which) -> input.setText(""))
                .setPositiveButton(R.string.vpn_continue, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String config;
            try {
                byte[] bytes = input.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(bytes)) {
                    config = VpnInput.configuration(stream);
                } finally { java.util.Arrays.fill(bytes, (byte) 0); }
            } catch (java.io.IOException | IllegalArgumentException invalid) {
                input.setError(getString(R.string.vpn_import_error));
                return;
            }
            input.setText("");
            dialog.dismiss();
            importDialog(getString(R.string.vpn_pasted_name), config, tcp);
        }));
        dialog.show();
    }

    private void importDialog(String name, String config) {
        importDialog(name, config, false);
    }
    private void importDialog(String name, String config, boolean tcp) {
        EditText input = new EditText(this); input.setText(name.length() > 80 ? name.substring(0, 80) : name);
        input.setHint(R.string.vpn_name); input.setTextColor(getColor(R.color.hecker_foreground));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.vpn_import).setView(input)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.vpn_save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Bundle request = HeckerVpnClient.request(tcp ? "import_tcp" : "import"); request.putString("name", input.getText().toString()); request.putString("config", config);
            operation(request, true, result -> {
                if (result.getString("error", "").isEmpty()) dialog.dismiss();
                else input.setError(result.getString("error"));
            });
        })); dialog.show();
    }
    private static void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) setEnabled(group.getChildAt(i), enabled); }
    }
}
