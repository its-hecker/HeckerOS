// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Serializes import, provider registration and kernel lifecycle on one owned worker. */
final class VpnEngine {
    private final Context context;
    private final Handler worker;
    private final ConnectivityManager connectivity;
    private final KernelChannel kernel = new KernelChannel();
    private VpnStore store;
    private String status = "disconnected", error = "";
    private boolean desired, configured, supported, cleanupRequired, probed;
    private long rx, tx, handshake, catalogTime;
    private long attemptStarted;
    private long lastDiagnostic;
    private String loggedStatus = "";
    private Network underlying;
    private int endpointAttempt,endpointChoices=1;
    private String underlyingProperties="";
    private List<JSONObject> catalog = List.of();

    VpnEngine(Context context) {
        this.context = context;
        HandlerThread thread = new HandlerThread("HeckerVPN"); thread.start(); worker = new Handler(thread.getLooper());
        connectivity = context.getSystemService(ConnectivityManager.class);
        connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { networkChanged(); }
            @Override public void onLost(Network network) { networkChanged(); }
            @Override public void onLinkPropertiesChanged(Network network,android.net.LinkProperties properties){
                worker.post(()->{
                    if(!desired||!network.equals(underlying)||!configured||fingerprint(properties).equals(underlyingProperties))return;
                    try{reconnect();}catch(Exception failure){failed(failure);}
                });
            }
        });
        IntentFilter packages = new IntentFilter(); packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED); packages.addDataScheme("package");
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                String name = intent.getData() == null ? "" : intent.getData().getSchemeSpecificPart();
                worker.post(() -> {
                    if (!desired || store == null) return;
                    try {
                        JSONObject profile = selected();
                        if (profile.optJSONArray("apps") != null && profile.getJSONArray("apps").toString().contains("\"" + name + "\"")) reconnect();
                    } catch (Exception failure) { failed(failure); }
                });
            }
        }, packages, Context.RECEIVER_NOT_EXPORTED);
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("heckervpn", context.getString(R.string.vpn_title), NotificationManager.IMPORTANCE_LOW));
    }
    private static String fingerprint(android.net.LinkProperties link){
        return link==null?"":link.getLinkAddresses().toString()+"/"+link.getNat64Prefix()+"/"+link.getMtu();
    }
    private void networkChanged() {
        worker.postDelayed(() -> {
            if (!desired) return;
            Network now = connectivity.getActiveNetwork();
            if (java.util.Objects.equals(now, underlying)) return;
            try { reconnect(); } catch (Exception failure) { failed(failure); }
        }, 1500);
    }
    Bundle call(Bundle request) {
        FutureTask<Bundle> task = new FutureTask<>(() -> execute(request)); worker.post(task);
        try { return task.get(110, TimeUnit.SECONDS); }
        catch (Exception timeout) { Bundle result = new Bundle(); result.putString("error", "HeckerVPN is busy. Wait for the current operation to finish."); return result; }
    }
    void enqueue(Bundle request) { worker.post(() -> execute(request)); }
    private Bundle execute(Bundle request) {
        try {
            if (store == null) store = new VpnStore(context);
            if (request == null) throw new IllegalArgumentException("Missing request");
            String op = request.getString("op", "state");
            if (!op.equals("state")) error = "";
            switch (op) {
                case "state": break;
                case "probe": snapshot(kernel.call("STATUS")); break;
                case "import": importProfile(request.getString("name", "VPN profile"), request.getString("config")); break;
                case "select":
                    if (desired) disconnect(); store.profile(request.getString("id", ""));
                    store.data.put("selected", request.getString("id")); store.save(); break;
                case "delete": delete(request.getString("id", "")); break;
                case "connect": endpointAttempt=0;connect(); break;
                case "reconnect": reconnect(); break;
                case "disconnect": disconnect(); break;
                case "policy": policy(request); break;
                case "catalog": catalog = MullvadProvider.servers(); catalogTime = System.currentTimeMillis(); break;
                case "account": account(VpnInput.accountNumber(request.getString("account", ""))); break;
                case "retry_account":
                    JSONObject savedAccount = store.data.optJSONObject("mullvad");
                    if (savedAccount == null) throw new IllegalArgumentException("Add your Mullvad account first");
                    account(savedAccount.getString("account")); break;
                case "logout": logout(); break;
                case "provider_connect": providerConnect(request.getString("host", "")); break;
                default: throw new IllegalArgumentException("Unsupported HeckerVPN operation");
            }
        } catch (Exception failure) {
            // Never log config, account, endpoint, package selection or exception text.
            Log.w("HeckerVPN", "Operation failed: " + failure.getClass().getSimpleName());
            if (failure instanceof KernelChannel.Failure) cleanupRequired |= ((KernelChannel.Failure) failure).cleanupRequired;
            error = message(failure);
            if (cleanupRequired) { status = "error"; notifyState(); }
            // Import and catalog errors must not tear down a healthy unrelated profile.
        }
        Bundle result = state();
        if (request != null && request.getString("op", "").equals("catalog")) {
            ArrayList<Bundle> servers = new ArrayList<>();
            for (JSONObject server : catalog) {
                Bundle row = new Bundle();
                for (String name : new String[]{"host", "country", "country_name", "city"}) row.putString(name, server.optString(name));
                servers.add(row);
            }
            result.putParcelableArrayList("servers", servers); result.putLong("catalog_time", catalogTime);
        }
        return result;
    }
    private static String message(Exception failure) {
        if (failure instanceof java.net.UnknownHostException)
            return "Could not resolve the VPN provider. Check your internet connection and DNS, then retry.";
        if (failure instanceof java.net.SocketTimeoutException)
            return "The VPN provider did not respond in time. Try again.";
        if (failure instanceof javax.net.ssl.SSLException)
            return "Could not establish a secure connection to the VPN provider. Check the phone's date and network.";
        return failure instanceof IllegalArgumentException ? failure.getMessage()
                : "Operation failed. Check network access and saved profile availability, then try again.";
    }
    private JSONObject selected() throws Exception { return store.profile(store.data.optString("selected")); }
    private void importProfile(String name, String text) throws Exception {
        if (name.trim().isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a profile name of 1–80 characters");
        VpnConfig config = VpnConfig.parse(text);
        JSONArray profiles = store.data.getJSONArray("profiles");
        if (profiles.length() >= 32) throw new IllegalArgumentException("Remove a profile before importing another (limit 32)");
        JSONObject profile = new JSONObject().put("id", UUID.randomUUID().toString()).put("name", name.trim())
                .put("config", config.render("all", List.of())).put("mode", config.mode)
                .put("apps", new JSONArray(config.applications)).put("source", "manual");
        profiles.put(profile);
        if (!desired) store.data.put("selected", profile.getString("id"));
        store.save();
    }
    private void delete(String id) throws Exception {
        store.profile(id);
        if (id.equals(store.data.optString("selected")) && desired) disconnect();
        JSONArray profiles = store.data.getJSONArray("profiles");
        for (int i = 0; i < profiles.length(); i++) if (profiles.getJSONObject(i).getString("id").equals(id)) { profiles.remove(i); break; }
        if (id.equals(store.data.optString("selected"))) store.data.put("selected", profiles.length() == 0 ? "" : profiles.getJSONObject(0).getString("id"));
        store.save();
    }
    private List<String> apps(JSONObject profile) throws Exception {
        JSONArray values = profile.getJSONArray("apps"); ArrayList<String> packages = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            String name = values.getString(i);
            try { context.getPackageManager().getApplicationInfo(name, 0); }
            catch (PackageManager.NameNotFoundException absent) { throw new IllegalArgumentException("A selected app is no longer installed. Update the app selection."); }
            packages.add(name);
        }
        return packages;
    }
    private void connect() throws Exception {
        JSONObject profile = selected();
        VpnConfig saved = VpnConfig.parse(profile.getString("config"));
        List<String> routedApps=apps(profile);
        android.net.NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))
            throw new IllegalArgumentException("Disconnect the other Android VPN before connecting HeckerVPN");
        snapshot(kernel.call("STATUS"));
        if (!supported) throw new IllegalArgumentException("This kernel does not expose WireGuard support");
        desired = true; underlying = connectivity.getActiveNetwork();
        if (underlying == null) { status = "waiting_network"; notifyState(); schedulePoll(); return; }
        attemptStarted = SystemClock.elapsedRealtime();
        status = "connecting"; notifyState();
        try {
            final Network network=underlying;
            android.net.LinkProperties link=connectivity.getLinkProperties(network);
            boolean v4=false,v6=false;
            if(link!=null)for(android.net.LinkAddress address:link.getLinkAddresses()){
                if(address.getAddress() instanceof java.net.Inet4Address)v4=true;
                if(address.getAddress() instanceof java.net.Inet6Address&&!address.getAddress().isLinkLocalAddress())v6=true;
            }
            final boolean ipv4=v4,ipv6=v6;final String prefix=link==null||link.getNat64Prefix()==null?null:link.getNat64Prefix().toString();
            if(!v4&&!v6){status="waiting_network";schedulePoll();notifyState();return;}
            underlyingProperties=fingerprint(link);endpointChoices=1;
            String config=saved.forUnderlay(profile.getString("mode"),routedApps,host->{
                ArrayList<String> addresses=new ArrayList<>();
                try{for(java.net.InetAddress address:network.getAllByName(host)){
                    if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress())continue;
                    String ip=address.getHostAddress();if(ip!=null&&ip.indexOf('%')<0)addresses.add(ip);
                }}catch(java.net.UnknownHostException failure){throw new IllegalArgumentException("Cannot resolve the VPN server on the current network. Check the profile or try another network.");}
                List<String> usable=VpnUnderlay.addresses(addresses,ipv4,ipv6,prefix);
                endpointChoices=Math.max(endpointChoices,usable.size());
                if(usable.isEmpty())throw new IllegalArgumentException("The VPN profile has no reachable address family on this network. Try a dual-stack profile.");
                return List.of(usable.get(endpointAttempt%usable.size()));
            });
            // Mobile MTUs often differ from Wi-Fi. Retain explicit imported MTUs.
            VpnConfig connection=VpnConfig.parse(config);
            if(!connection.network.containsKey("MTU"))connection.network.put("MTU",Integer.toString(
                    link!=null&&link.getMtu()>0?Math.max(1280,Math.min(1420,link.getMtu()-80)):1280));
            config=connection.render(profile.getString("mode"),routedApps);
            // A network switch during DNS must not create a tunnel pinned to the old network.
            if(!network.equals(connectivity.getActiveNetwork())){
                status="waiting_network";underlying=null;schedulePoll();notifyState();return;
            }
            snapshot(kernel.call("UP\n"+network.getNetId()+"\n"+config)); error = "";
        }
        catch (Exception failure) { failed(failure); throw failure; }
        schedulePoll(); notifyState();
    }
    private void reconnect() throws Exception {
        endpointAttempt=0;kernel.call("DOWN"); configured = false; rx = tx = handshake = 0; connect();
    }
    private void disconnect() throws Exception {
        desired = false; worker.removeCallbacks(poll);
        try { snapshot(kernel.call("DOWN")); cleanupRequired = false; }
        catch (Exception failure) {
            if (failure instanceof KernelChannel.Failure) cleanupRequired |= ((KernelChannel.Failure) failure).cleanupRequired;
            throw failure;
        }
        finally { kernel.close(); configured = false; status = cleanupRequired ? "error" : "disconnected"; rx = tx = handshake = 0; notifyState(); }
    }
    private void failed(Exception failure) {
        if (failure instanceof KernelChannel.Failure) cleanupRequired |= ((KernelChannel.Failure) failure).cleanupRequired;
        kernel.close(); desired = false; configured = false; status = "error"; error = message(failure);
        worker.removeCallbacks(poll); notifyState();
    }
    private void snapshot(JSONObject data) {
        probed = true;
        cleanupRequired = false;
        supported = data.optBoolean("kernel"); configured = data.optBoolean("configured");
        rx = data.optLong("rx"); tx = data.optLong("tx"); handshake = data.optLong("handshake");
        if (configured) status = VpnConnectionState.classify(true, handshake, System.currentTimeMillis() / 1000,
                Math.max(0, SystemClock.elapsedRealtime() - attemptStarted));
        else if (!desired) status = "disconnected";
    }
    private final Runnable poll = this::pollOnce;
    private void pollOnce() {
        if (!desired) return;
        try {
            if (connectivity.getActiveNetwork() == null) {
                if (configured) { kernel.call("DOWN"); configured = false; }
                status = "waiting_network";
            } else if (!java.util.Objects.equals(connectivity.getActiveNetwork(),underlying)) reconnect();
            else if (!configured) connect();
            else {
                snapshot(kernel.call("STATUS"));
                if (!configured) throw new IllegalArgumentException("Kernel tunnel stopped. Reconnect to try again.");
                if(handshake==0&&SystemClock.elapsedRealtime()-attemptStarted>=15000
                        &&endpointAttempt<Math.min(2,endpointChoices-1)){
                    // Try alternate DNS/NAT64 addresses at most twice. Provider failures
                    // eventually remain visible as peer_timeout, never a fake connection.
                    snapshot(kernel.call("DOWN"));endpointAttempt++;connect();
                }
            }
            notifyState(); schedulePoll();
        } catch (Exception failure) { failed(failure); }
    }
    private void schedulePoll() { worker.removeCallbacks(poll); worker.postDelayed(poll, configured && handshake <= 0 ? 3000 : 10000); }
    private void policy(Bundle request) throws Exception {
        JSONObject profile = selected(); String mode = request.getString("mode", "all");
        ArrayList<String> packages = request.getStringArrayList("apps"); if (packages == null) packages = new ArrayList<>();
        VpnConfig.parse(profile.getString("config")).render(mode, packages);
        boolean resume = desired; if (resume) disconnect();
        profile.put("mode", mode).put("apps", new JSONArray(packages)); store.save();
        if (resume) connect();
    }
    private void account(String account) throws Exception {
        if (!account.matches("[0-9]{16}")) throw new IllegalArgumentException("Enter your 16-digit Mullvad account number");
        JSONObject identity = store.data.optJSONObject("mullvad");
        if (identity != null && !identity.optString("account").equals(account)) throw new IllegalArgumentException("Remove the saved Mullvad account before adding another");
        if (identity == null) {
            JSONObject pair = kernel.call("KEYS");
            identity = new JSONObject().put("account", account).put("private", VpnConfig.key(pair.getString("private")))
                    .put("public", VpnConfig.key(pair.getString("public")));
            store.data.put("mullvad", identity); store.save(); // Retry with the same key after a lost provider response.
        }
        JSONObject device = MullvadProvider.register(account, identity.getString("public"));
        identity.put("device", device.getString("id")).put("ipv4", device.getString("ipv4_address"))
                .put("ipv6", device.getString("ipv6_address"));
        // Validate provider-assigned addresses without sending the private key to a server.
        MullvadProvider.config(identity, new JSONObject().put("key", identity.getString("public")).put("address", "192.0.2.1"));
        store.save();
    }
    private void logout() throws Exception {
        JSONObject identity = store.data.optJSONObject("mullvad"); if (identity == null) return;
        if (store.data.optString("selected").equals("mullvad") && desired) disconnect();
        if (identity.has("device")) MullvadProvider.remove(identity.getString("account"), identity.getString("device"));
        JSONArray profiles = store.data.getJSONArray("profiles");
        for (int i = profiles.length() - 1; i >= 0; i--) if (profiles.getJSONObject(i).optString("source").equals("mullvad")) profiles.remove(i);
        if (store.data.optString("selected").equals("mullvad")) store.data.put("selected", "");
        store.data.remove("mullvad"); store.save();
    }
    private void providerConnect(String host) throws Exception {
        JSONObject identity = store.data.optJSONObject("mullvad");
        if (identity == null || !identity.has("device")) throw new IllegalArgumentException("Add your Mullvad account first");
        if (catalog.isEmpty() || System.currentTimeMillis() - catalogTime > 24L * 60 * 60 * 1000) {
            catalog = MullvadProvider.servers(); catalogTime = System.currentTimeMillis();
        }
        JSONObject server = null; for (JSONObject row : catalog) if (row.optString("host").equals(host)) { server = row; break; }
        if (server == null) throw new IllegalArgumentException("That server is unavailable. Refresh the country list.");
        String config = MullvadProvider.config(identity, server);
        if (desired) disconnect();
        JSONObject profile;
        try { profile = store.profile("mullvad"); }
        catch (IllegalArgumentException absent) {
            if (store.data.getJSONArray("profiles").length() >= 32) throw new IllegalArgumentException("Remove a saved profile first");
            profile = new JSONObject().put("id", "mullvad").put("mode", "all").put("apps", new JSONArray()).put("source", "mullvad");
            store.data.getJSONArray("profiles").put(profile);
        }
        profile.put("name", server.getString("country_name") + " · " + server.getString("host")).put("config", config);
        store.data.put("selected", "mullvad"); store.save(); connect();
    }
    private Bundle state() {
        Bundle result = new Bundle(); result.putString("status", status); result.putString("error", error);
        result.putString("backend", "WireGuard kernel"); if (probed) result.putBoolean("kernel", supported);
        result.putBoolean("configured", configured); result.putBoolean("desired", desired);
        result.putBoolean("cleanup_required", cleanupRequired);
        result.putLong("rx", rx); result.putLong("tx", tx); result.putLong("handshake", handshake);
        result.putInt("underlying_netid",underlying==null?0:underlying.getNetId());
        ArrayList<Bundle> profiles = new ArrayList<>();
        if (store != null) {
            result.putString("selected", store.data.optString("selected"));
            JSONObject identity = store.data.optJSONObject("mullvad"); result.putBoolean("account", identity != null && identity.has("device"));
            result.putBoolean("account_pending", identity != null && !identity.has("device"));
            JSONArray values = store.data.optJSONArray("profiles");
            if (values != null) for (int i = 0; i < values.length(); i++) {
                JSONObject profile = values.optJSONObject(i); if (profile == null) continue;
                Bundle row = new Bundle();
                for (String key : new String[]{"id", "name", "mode", "source"}) row.putString(key, profile.optString(key));
                ArrayList<String> apps = new ArrayList<>(); JSONArray list = profile.optJSONArray("apps");
                if (list != null) for (int n = 0; n < list.length(); n++) apps.add(list.optString(n));
                row.putStringArrayList("apps", apps); profiles.add(row);
            }
        }
        result.putParcelableArrayList("profiles", profiles); return result;
    }
    private void notifyState() {
        long now=SystemClock.elapsedRealtime();
        if (!status.equals(loggedStatus)||(desired&&now-lastDiagnostic>=15000)) {
            Log.i("HeckerVPN", "state=" + status + " configured=" + configured
                    + " peerContact=" + (handshake > 0) + " rx=" + rx + " tx=" + tx
                    + " physicalNetwork="+(underlying==null?0:underlying.getNetId()));
            loggedStatus = status;lastDiagnostic=now;
        }
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (!desired && !status.equals("error")) { notifications.cancel(7841); return; }
        Intent open = new Intent().setClassName("org.hecker.suite", "org.hecker.suite.SuiteRouterActivity")
                .putExtra("org.hecker.suite.PAGE", "HeckerVpnActivity");
        PendingIntent content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(context, 1, new Intent(context, VpnService.class).setAction("disconnect"), PendingIntent.FLAG_IMMUTABLE);
        int label = status.equals("active") ? R.string.vpn_active : status.equals("idle") ? R.string.vpn_idle : status.equals("peer_timeout") ? R.string.vpn_peer_timeout : status.equals("waiting_peer") ? R.string.vpn_waiting_peer
                : status.equals("waiting_network") ? R.string.vpn_waiting_network : status.equals("error") ? R.string.vpn_error : R.string.vpn_connecting;
        notifications.notify(7841, new Notification.Builder(context, "heckervpn").setSmallIcon(R.drawable.ic_vpn)
                .setContentTitle(context.getString(R.string.vpn_title)).setContentText(context.getString(label))
                .setContentIntent(content).setOngoing(desired).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, context.getString(R.string.vpn_disconnect), stop).build()).build());
    }
}
