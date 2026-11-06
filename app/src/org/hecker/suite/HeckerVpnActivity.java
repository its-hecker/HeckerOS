// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

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
    private Button connect, countries, profiles;
    private boolean countriesTab = true, busy, started;
    private android.net.Uri pendingImport;

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
        hero.addView(connect); page.addView(hero);
        countries = Ui.button(this); countries.setText(R.string.vpn_countries);
        profiles = Ui.button(this); profiles.setText(R.string.vpn_profiles);
        countries.setOnClickListener(v -> { countriesTab = true; renderBody(); });
        profiles.setOnClickListener(v -> { countriesTab = false; renderBody(); });
        page.addView(Ui.chips(this, countries, profiles));
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); page.addView(body);
        renderBody(); Ui.scroll(this, page);
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
        started = false; main.removeCallbacks(poll); if (client != null) client.close(); client = null; busy = false; super.onStop();
    }
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
        String text = getString(R.string.vpn_status, "WireGuard kernel", Formatter.formatFileSize(this, state.getLong("rx")), Formatter.formatFileSize(this, state.getLong("tx")));
        ArrayList<Bundle> saved = state.getParcelableArrayList("profiles", Bundle.class);
        if (saved != null) for (Bundle profile : saved) if (profile.getString("id", "").equals(state.getString("selected"))) { text = profile.getString("name", "") + "\n" + text; break; }
        long handshake = state.getLong("handshake");
        if (handshake > 0) text += "\n" + getString(R.string.vpn_handshake, DateUtils.getRelativeTimeSpanString(handshake * 1000, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS));
        detail.setText(text);
        String error = state.getString("error", "");
        warning.setText(error.isEmpty() && state.containsKey("kernel") && !state.getBoolean("kernel") ? getString(R.string.vpn_kernel_missing) : error);
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
            case "waiting_peer": return R.string.vpn_waiting_peer;
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
        LinearLayout provider = Ui.card(this);
        provider.addView(Ui.text(this, getString(R.string.vpn_free_title), 20, true));
        provider.addView(Ui.text(this, getString(R.string.vpn_free_summary), 14, false));
        action(provider, R.string.vpn_proton_free, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://protonvpn.com/support/wireguard-configurations"))));
        action(provider, R.string.vpn_import, () -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 784));
        action(provider, R.string.vpn_paste, this::pasteConfigDialog);
        body.addView(provider);
    }
    private void renderProfiles() {
        LinearLayout header = Ui.card(this); action(header, R.string.vpn_import, () -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 784));
        action(header, R.string.vpn_paste, this::pasteConfigDialog);
        action(header, R.string.vpn_proton_free, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://protonvpn.com/support/wireguard-configurations"))));
        body.addView(header);
        ArrayList<Bundle> items = state.getParcelableArrayList("profiles", Bundle.class);
        if (items == null || items.isEmpty()) { body.addView(Ui.text(this, getString(R.string.vpn_no_profiles), 15, false)); return; }
        for (Bundle profile : items) {
            String id = profile.getString("id", ""), name = profile.getString("name", "");
            boolean selected = id.equals(state.getString("selected"));
            LinearLayout card = Ui.card(this); card.addView(Ui.text(this, name, 18, true));
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
        new AlertDialog.Builder(this).setTitle(R.string.vpn_routing).setItems(labels, (d, which) -> {
            if (which == 0) applyPolicy("all", new ArrayList<>()); else chooseApps(modes[which], profile.getStringArrayList("apps"));
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private void chooseApps(String mode, ArrayList<String> initial) {
        // Package enumeration and label loading stay off the main thread.
        new Thread(() -> {
            List<ApplicationInfo> installed = getPackageManager().getInstalledApplications(0);
            class App {
                ApplicationInfo info; String label; android.graphics.drawable.Drawable icon;
                App(ApplicationInfo i) { info = i; label = i.loadLabel(getPackageManager()).toString(); icon = i.loadIcon(getPackageManager()); }
            }
            ArrayList<App> apps = new ArrayList<>(); for (ApplicationInfo info : installed) apps.add(new App(info));
            apps.sort(Comparator.comparing(app -> app.label.toLowerCase(java.util.Locale.ROOT)));
            main.post(() -> {
                if (!started) return;
                LinkedHashSet<String> selected = new LinkedHashSet<>(initial == null ? List.of() : initial);
                LinearLayout page = Ui.card(this); EditText search = new EditText(this); search.setHint(R.string.vpn_search_apps);
                search.setTextColor(getColor(R.color.hecker_foreground)); page.addView(search);
                LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
                ScrollView scroll = new ScrollView(this); scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(this, 360)));
                Runnable render = () -> {
                    list.removeAllViews(); String query = search.getText().toString().toLowerCase(java.util.Locale.ROOT);
                    for (App app : apps) {
                        if (!app.label.toLowerCase(java.util.Locale.ROOT).contains(query) && !app.info.packageName.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                        CheckBox row = new CheckBox(this); row.setText(app.label + "\n" + app.info.packageName);
                        row.setTextColor(getColor(R.color.hecker_foreground)); row.setMinHeight(Ui.dp(this, 56)); row.setChecked(selected.contains(app.info.packageName));
                        android.graphics.drawable.Drawable icon = app.icon; icon.setBounds(0, 0, Ui.dp(this, 32), Ui.dp(this, 32));
                        row.setCompoundDrawablesRelative(icon, null, null, null); row.setCompoundDrawablePadding(Ui.dp(this, 8));
                        row.setOnCheckedChangeListener((button, checked) -> { if (checked) selected.add(app.info.packageName); else selected.remove(app.info.packageName); }); list.addView(row);
                    }
                }; render.run();
                search.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
                    public void afterTextChanged(Editable text) {}
                });
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle(routeLabel(mode)).setView(page)
                        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.vpn_apply, null).create();
                dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (mode.equals("include") && selected.isEmpty()) { Toast.makeText(this, R.string.vpn_select_apps_first, Toast.LENGTH_SHORT).show(); return; }
                    applyPolicy(mode, new ArrayList<>(selected)); dialog.dismiss();
                })); dialog.show();
            });
        }, "HeckerVPN-apps").start();
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
        EditText input = new EditText(this);
        input.setHint(R.string.vpn_paste_hint);
        input.setTextColor(getColor(R.color.hecker_foreground));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(8);
        input.setMaxLines(16);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.vpn_paste)
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
            importDialog(getString(R.string.vpn_pasted_name), config);
        }));
        dialog.show();
    }

    private void importDialog(String name, String config) {
        EditText input = new EditText(this); input.setText(name.length() > 80 ? name.substring(0, 80) : name);
        input.setHint(R.string.vpn_name); input.setTextColor(getColor(R.color.hecker_foreground));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.vpn_import).setView(input)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.vpn_save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Bundle request = HeckerVpnClient.request("import"); request.putString("name", input.getText().toString()); request.putString("config", config);
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
