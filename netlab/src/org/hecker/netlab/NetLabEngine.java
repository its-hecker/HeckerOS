// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import android.app.*;
import android.content.*;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Serial, permission-limited Wi-Fi adapter. No su, packet injection, iptables, or shell commands. */
final class NetLabEngine implements NetLabPolicy.Backend, NetLabPolicy.Journal {
    private final Context context;
    private final WifiManager wifi;
    private final Handler worker;
    private final AlarmManager alarms;
    private final PendingIntent expiry, emergency;
    private final NotificationManager notifications;
    private final AtomicFile file;
    private final LinkedHashSet<String> connected = new LinkedHashSet<>();
    private final Map<String, List<String>> addresses = new HashMap<>();
    private final Map<String, String> hostnames = new HashMap<>();
    private final Map<String, Set<String>> perAp = new HashMap<>();
    private final ArrayDeque<Bundle> events = new ArrayDeque<>();
    private NetLabPolicy policy;
    private boolean hotspot, forceDisconnect, callbackReady, journalHealthy = true, recovering;
    private boolean wifiRegistered, tetherRegistered, receiversRegistered;
    private int offload = -1;
    private long nextAlarmAt;
    private String lastError = "", lastEvent = "";
    private final Runnable maintenance = this::maintain;

    NetLabEngine(Context owner) {
        context = owner.createDeviceProtectedStorageContext();
        wifi = context.getSystemService(WifiManager.class);
        alarms = context.getSystemService(AlarmManager.class);
        notifications = context.getSystemService(NotificationManager.class);
        file = new AtomicFile(new File(context.getFilesDir(), "netlab-owned-rules-v1.json"));
        HandlerThread thread = new HandlerThread("HeckerNetLab", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start(); worker = new Handler(thread.getLooper());
        expiry = PendingIntent.getBroadcast(context, 1, new Intent(context, NetLabReceiver.class)
                .setAction("org.hecker.netlab.EXPIRE"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        emergency = PendingIntent.getBroadcast(context, 2, new Intent(context, NetLabReceiver.class)
                .setAction("org.hecker.netlab.RESTORE"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        worker.post(this::start);
    }
    private void start() {
        List<NetLabPolicy.Rule> saved = new ArrayList<>();
        try {
            if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) {
                byte[] data = file.readFully();
                if (data.length > 48 * 1024) throw new IOException("journal_size");
                JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
                if (root.getInt("version") != 1) throw new IOException("journal_version");
                JSONArray list = root.getJSONArray("rules");
                if (list.length() > NetLabPolicy.MAX_RULES) throw new IOException("journal_size");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.getJSONObject(i);
                    saved.add(new NetLabPolicy.Rule(r.getString("mac"), r.getLong("wall"),
                            r.getLong("elapsed"), r.getLong("created"), "restoring"));
                }
            }
            policy = new NetLabPolicy(this, this, saved);
        } catch (IOException | JSONException | IllegalArgumentException failure) {
            journalHealthy = false; lastError = "journal_unreadable";
            policy = new NetLabPolicy(this, this, Collections.emptyList());
        }
        recovering = !policy.rules().isEmpty();
        registerNative();
        maintain();
    }
    private void registerNative() {
        if (wifi == null) { lastError = "wifi_unavailable"; maintain(); return; }
        try {
            if (!wifiRegistered) {
            wifi.registerSoftApCallback(command -> worker.post(command), new WifiManager.SoftApCallback() {
                @Override public void onCapabilityChanged(SoftApCapability capability) {
                    forceDisconnect = capability.areFeaturesSupported(SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT);
                    callbackReady = true; maintain();
                }
                @Override public void onStateChanged(SoftApState state) { setHotspot(state.getState()); }
                @Override public void onConnectedClientsChanged(List<WifiClient> clients) {
                    connected.clear(); for (WifiClient client : clients) connected.add(client.getMacAddress().toString());
                    maintain();
                }
                @Override public void onConnectedClientsChanged(SoftApInfo info, List<WifiClient> clients) {
                    String key = info.getBssid() == null ? "default" : info.getBssid().toString();
                    Set<String> values = new LinkedHashSet<>();
                    for (WifiClient client : clients) values.add(client.getMacAddress().toString());
                    perAp.put(key, values); connected.clear();
                    for (Set<String> set : perAp.values()) connected.addAll(set);
                    maintain();
                }
                @Override public void onInfoChanged(List<SoftApInfo> infos) {
                    Set<String> live = new HashSet<>();
                    for (SoftApInfo info : infos) live.add(info.getBssid() == null ? "default" : info.getBssid().toString());
                    perAp.keySet().retainAll(live);
                    connected.clear(); for (Set<String> set : perAp.values()) connected.addAll(set);
                    maintain();
                }
            });
            wifiRegistered = true;
            }
            TetheringManager tether = context.getSystemService(TetheringManager.class);
            if (tether != null && !tetherRegistered) {
            tether.registerTetheringEventCallback(command -> worker.post(command), new TetheringManager.TetheringEventCallback() {
                @Override public void onClientsChanged(Collection<TetheredClient> clients) {
                    addresses.clear(); hostnames.clear();
                    for (TetheredClient client : clients) if (client.getTetheringType() == TetheringManager.TETHERING_WIFI) {
                        ArrayList<String> ips = new ArrayList<>();
                        for (TetheredClient.AddressInfo address : client.getAddresses()) {
                            ips.add(address.getAddress().getAddress().getHostAddress());
                            String name = NetLabIdentity.clean(address.getHostname());
                            if (!name.isEmpty()) hostnames.put(client.getMacAddress().toString(), name);
                        }
                        addresses.put(client.getMacAddress().toString(), ips);
                    }
                }
                @Override public void onOffloadStatusChanged(int status) { offload = status; }
            });
            tetherRegistered = true;
            }
            if (!receiversRegistered) {
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { worker.post(() -> setHotspot(i.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_DISABLED))); }
            }, new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION), null, worker, Context.RECEIVER_EXPORTED);
            IntentFilter time = new IntentFilter(Intent.ACTION_TIME_CHANGED); time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() { public void onReceive(Context c, Intent i) { worker.post(NetLabEngine.this::maintain); } }, time, null, worker, Context.RECEIVER_NOT_EXPORTED);
            receiversRegistered = true;
            }
        } catch (RuntimeException unavailable) {
            if (!wifiRegistered) callbackReady = false;
            lastError = "wifi_callbacks_unavailable";
            worker.postDelayed(this::registerNative, 30_000);
        }
    }
    private void setHotspot(int state) {
        hotspot = state == WifiManager.WIFI_AP_STATE_ENABLED;
        if (!hotspot) { connected.clear(); perAp.clear(); addresses.clear(); hostnames.clear(); }
        maintain();
    }
    void signal(boolean restore) { worker.post(() -> { nextAlarmAt = 0; if (restore) restoreAll(); else maintain(); }); }
    Bundle call(String operation, String mac, long duration) {
        FutureTask<Bundle> task = new FutureTask<>(() -> {
            // Observing the dashboard must not postpone its expiry alarm or maintenance task.
            if (operation.equals("state")) { Bundle state = snapshot(); state.putString("result", "ok"); return state; }
            String result = "ok";
            try {
                if (operation.equals("pause")) {
                    if (!journalHealthy) throw new NetLabPolicy.Failure("journal_unreadable");
                    if (recovering) throw new NetLabPolicy.Failure("recovery_pending");
                    policy.pause(mac, duration); event("pause_requested");
                } else if (operation.equals("restore")) { policy.restore(mac); event("restored"); }
                else if (operation.equals("restore_all")) {
                    if (!journalHealthy) throw new NetLabPolicy.Failure("journal_unreadable");
                    policy.restoreAll(); recovering = false; event("restored_all");
                }
                else if (!operation.equals("state")) throw new NetLabPolicy.Failure("unsupported_operation");
            } catch (NetLabPolicy.Failure failure) { result = failure.code; lastError = result; event(result); }
            if (result.equals("ok")) lastError = "";
            if (!schedule() && operation.equals("pause")) { restoreAll(); result = "timer_unavailable"; }
            Bundle state = snapshot(); state.putString("result", result); return state;
        });
        worker.post(task);
        try { return task.get(6, TimeUnit.SECONDS); }
        catch (Exception timeout) { task.cancel(false); Bundle b = new Bundle(); b.putString("result", "service_busy"); return b; }
    }
    private void restoreAll() {
        if (policy == null) return;
        if (!journalHealthy) { lastError = "journal_unreadable"; event(lastError); return; }
        try { policy.restoreAll(); recovering = false; event("restored_all"); }
        catch (NetLabPolicy.Failure failure) { lastError = failure.code; event(lastError); }
        schedule();
    }
    private void maintain() {
        if (policy == null) return;
        boolean unconfirmed = false;
        for (NetLabPolicy.Rule rule : policy.rules())
            unconfirmed |= hotspot && rule.state.equals("applying") && elapsed() - rule.createdElapsed >= 15_000;
        try {
            if (recovering) { policy.restoreAll(); recovering = false; lastError = ""; event("startup_restored"); }
            else {
                policy.maintain();
                if (unconfirmed) { lastError = "native_unconfirmed"; event(lastError); }
            }
        }
        catch (NetLabPolicy.Failure failure) { lastError = failure.code; event(lastError); }
        schedule();
    }
    private boolean schedule() {
        worker.removeCallbacks(maintenance);
        List<NetLabPolicy.Rule> rules = policy.rules();
        if (rules.isEmpty()) {
            if (alarms != null) alarms.cancel(expiry); nextAlarmAt = 0;
            notifications.cancel(1977); return true;
        }
        long next = Long.MAX_VALUE; boolean applying = false;
        for (NetLabPolicy.Rule r : rules) { if (r.untilElapsed > 0) next = Math.min(next, r.untilElapsed); applying |= r.state.equals("applying"); }
        worker.postDelayed(maintenance, applying ? 1000 : 30_000);
        boolean scheduled = true;
        if (next != Long.MAX_VALUE) {
        try {
            if (!canSchedule()) throw new SecurityException();
            if (nextAlarmAt != next) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, Math.max(elapsed() + 1, next), expiry);
                nextAlarmAt = next;
            }
        } catch (RuntimeException denied) {
            scheduled = false; lastError = "timer_unavailable"; recovering = true;
            worker.removeCallbacks(maintenance); worker.postDelayed(maintenance, 1000);
        }
        }
        else { if (alarms != null) alarms.cancel(expiry); nextAlarmAt = 0; }
        NotificationChannel channel = new NotificationChannel("netlab_rules", context.getString(R.string.netlab_controller_title), NotificationManager.IMPORTANCE_LOW);
        try {
        notifications.createNotificationChannel(channel);
        Notification note = new Notification.Builder(context, "netlab_rules")
                .setSmallIcon(R.drawable.ic_netlab).setContentTitle(context.getString(R.string.netlab_active_rules, rules.size()))
                .setContentText(context.getString(R.string.netlab_restore_notification)).setOngoing(true)
                .setContentIntent(emergency).addAction(new Notification.Action.Builder(null, context.getString(R.string.netlab_restore_all), emergency).build()).build();
        notifications.notify(1977, note);
        } catch (RuntimeException denied) { event("notification_unavailable"); }
        return scheduled;
    }
    private Bundle snapshot() {
        Bundle state = new Bundle(); state.putBoolean("available", journalHealthy && wifi != null);
        state.putBoolean("hotspot", hotspot); state.putBoolean("can_pause", active() && canBlock() && journalHealthy && !recovering);
        state.putBoolean("hal_block", forceDisconnect); state.putBoolean("callbacks", callbackReady);
        state.putBoolean("can_time", canSchedule()); state.putBoolean("recovering", recovering);
        state.putBoolean("exact_alarm", alarms != null && alarms.canScheduleExactAlarms());
        state.putBoolean("idle_exempt", idleExempt());
        state.putBoolean("can_limit", false); state.putBoolean("router", false); state.putInt("offload", offload);
        state.putString("error", lastError); state.putString("backend", "phone_hotspot");
        Set<String> all = new LinkedHashSet<>(connected);
        Map<String, NetLabPolicy.Rule> rules = new HashMap<>();
        for (NetLabPolicy.Rule rule : policy.rules()) { all.add(rule.mac); rules.put(rule.mac, rule); }
        ArrayList<Bundle> devices = new ArrayList<>();
        for (String mac : all) {
            Bundle device = new Bundle(); device.putString("id", "hotspot:" + mac); device.putString("mac", mac);
            device.putBoolean("online", connected.contains(mac)); device.putBoolean("managed", true);
            device.putStringArrayList("addresses", new ArrayList<>(addresses.getOrDefault(mac, Collections.emptyList())));
            String hostname = hostnames.getOrDefault(mac, "");
            device.putString("name", hostname); device.putString("os", NetLabIdentity.kind(hostname, "", ""));
            if (!hostname.isEmpty()) device.putString("identity_source", "dhcp");
            NetLabPolicy.Rule rule = rules.get(mac);
            device.putString("state", rule == null ? "available" : rule.state);
            device.putLong("until", rule == null ? 0 : rule.untilWall); devices.add(device);
        }
        state.putParcelableArrayList("devices", devices); state.putParcelableArrayList("events", new ArrayList<>(events));
        return state;
    }
    private void event(String code) {
        if (code.equals(lastEvent)) return;
        lastEvent = code; Bundle event = new Bundle(); event.putLong("time", wall()); event.putString("code", code);
        if (events.size() == 32) events.removeFirst(); events.addLast(event);
    }
    @Override public boolean active() { return hotspot; }
    @Override public boolean canBlock() { return callbackReady && forceDisconnect; }
    private boolean idleExempt() {
        PowerManager power = context.getSystemService(PowerManager.class);
        return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
    }
    @Override public boolean canSchedule() { return alarms != null && alarms.canScheduleExactAlarms() && idleExempt(); }
    @Override public long wall() { return System.currentTimeMillis(); }
    @Override public long elapsed() { return SystemClock.elapsedRealtime(); }
    @Override public Set<String> clients() { return new LinkedHashSet<>(connected); }
    private SoftApConfiguration configuration() throws IOException {
        try { SoftApConfiguration config = wifi == null ? null : wifi.getSoftApConfiguration();
            if (config == null) throw new IOException("wifi_unavailable"); return config;
        } catch (RuntimeException unavailable) { throw new IOException("wifi_unavailable"); }
    }
    @Override public Set<String> blocked() throws IOException {
        Set<String> set = new LinkedHashSet<>(); for (MacAddress m : configuration().getBlockedClientList()) set.add(m.toString()); return set;
    }
    @Override public Set<String> allowed() throws IOException {
        Set<String> set = new LinkedHashSet<>(); for (MacAddress m : configuration().getAllowedClientList()) set.add(m.toString()); return set;
    }
    @Override public void writeBlocked(Set<String> before, Set<String> updated) throws IOException {
        try {
            SoftApConfiguration latest = configuration();
            Set<String> merged = new LinkedHashSet<>(); for (MacAddress address : latest.getBlockedClientList()) merged.add(address.toString());
            Set<String> removed = new LinkedHashSet<>(before); removed.removeAll(updated);
            Set<String> added = new LinkedHashSet<>(updated); added.removeAll(before);
            merged.removeAll(removed); merged.addAll(added);
            List<MacAddress> list = new ArrayList<>(); for (String mac : merged) list.add(MacAddress.fromString(NetLabPolicy.normalize(mac)));
            if (!wifi.setSoftApConfiguration(new SoftApConfiguration.Builder(latest).setBlockedClientList(list).build())) throw new IOException("native_rejected");
            if (!blocked().equals(merged)) throw new IOException("native_readback_failed");
        } catch (RuntimeException failure) { throw new IOException("native_rejected"); }
    }
    @Override public void save(Collection<NetLabPolicy.Rule> rules) throws IOException {
        FileOutputStream stream = null;
        try {
            JSONArray items = new JSONArray();
            for (NetLabPolicy.Rule r : rules) items.put(new JSONObject().put("mac", r.mac).put("wall", r.untilWall)
                    .put("elapsed", r.untilElapsed).put("created", r.createdElapsed));
            byte[] data = new JSONObject().put("version", 1).put("rules", items).toString().getBytes(StandardCharsets.UTF_8);
            stream = file.startWrite(); stream.write(data); file.finishWrite(stream);
        } catch (IOException | JSONException failure) { if (stream != null) file.failWrite(stream); throw new IOException("journal_failed"); }
    }
}
