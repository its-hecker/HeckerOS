// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Explicit, temporary rooted IPv4 control. The APK itself keeps its normal UID. */
final class NetLabLanSession {
    private final Context context;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> ticker;
    private final Map<String, Bundle> peers = new LinkedHashMap<>();
    private final Map<String, NetLabTrafficEvidence> evidence = new HashMap<>();
    private final NotificationManager notifications;
    private java.lang.Process child;
    private BufferedWriter input;
    private PowerManager.WakeLock wake;
    private Link selected;
    private String status = "idle", error = "", action = "", scope = "", gatewayMac = "";
    private boolean root, busy, active, stopped, ended;
    private long launched, deadline, stopAt, expires;
    private int sessionSeconds;
    private int helperExit=-1;
    private String lastEnd="";

    private static final class Link {
        String iface, own, gateway, scope, key;
        int prefix;
        String arguments() { return iface + " " + own + " " + prefix + " " + gateway; }
    }
    NetLabLanSession(Context context) {
        this.context = context; notifications = context.getSystemService(NotificationManager.class);
    }
    private Link link() {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        Network activeNetwork = manager.getActiveNetwork();
        NetworkCapabilities activeCaps = manager.getNetworkCapabilities(activeNetwork);
        if (activeCaps != null && activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) throw new IllegalArgumentException("vpn_active");
        Network chosen = null;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                chosen = network; if (network.equals(activeNetwork)) break;
            }
        }
        if (chosen == null) throw new IllegalArgumentException("wifi_unavailable");
        LinkProperties props = manager.getLinkProperties(chosen);
        if (props == null) throw new IllegalArgumentException("wifi_unavailable");
        Link result = new Link(); result.iface = props.getInterfaceName();
        if (result.iface == null || !result.iface.matches("(?:wlan|wifi)[0-9]{1,2}")) throw new IllegalArgumentException("invalid_link");
        for (LinkAddress address : props.getLinkAddresses()) if (address.getAddress() instanceof Inet4Address) {
            if (!NetLabAddressPlan.targets(address.getAddress().getAddress(), address.getPrefixLength()).isEmpty()) {
                result.own = NetLabLanProtocol.ipv4(address.getAddress().getHostAddress()); result.prefix = address.getPrefixLength(); break;
            }
        }
        for (RouteInfo route : props.getRoutes()) if (route.isDefaultRoute() && route.getGateway() instanceof Inet4Address) {
            result.gateway = NetLabLanProtocol.ipv4(route.getGateway().getHostAddress()); break;
        }
        if (result.own == null || result.gateway == null) throw new IllegalArgumentException("ipv4_unavailable");
        result.scope = chosen.getNetworkHandle() + ":" + result.iface;
        result.key = result.scope + ":" + result.own + "/" + result.prefix + ":" + result.gateway;
        return result;
    }
    synchronized Bundle state() {
        Bundle b = new Bundle(); b.putString("status", status); b.putString("error", error);
        b.putBoolean("root", root); b.putBoolean("busy", busy); b.putBoolean("active", NetLabSliderPolicy.canEdit(active,busy,stopped,status,SystemClock.elapsedRealtime(),expires));
        b.putString("scope", scope); b.putString("gateway_mac", gatewayMac);
        b.putString("action", action);
        b.putInt("helper_exit",helperExit);b.putString("last_end",lastEnd);
        ArrayList<Bundle> devices = new ArrayList<>();
        for (Bundle peer : peers.values()) {
            Bundle d = new Bundle(peer);
            d.putBoolean("session_member",busy&&action.equals("START"));
            if (d.getBoolean("lan_managed") && expires > 0) d.putLong("until", System.currentTimeMillis() + Math.max(0, expires - SystemClock.elapsedRealtime()));
            if (!d.getBoolean("lan_managed") && SystemClock.elapsedRealtime() - d.getLong("observed_elapsed") > 30_000) {
                d.putBoolean("online", false); d.putString("state", "last_scan");
            }
            devices.add(d);
        }
        b.putParcelableArrayList("devices", devices); return b;
    }
    String probe() { return begin("INFO", null, null, false, 0, 0, 0); }
    String scan() { return begin("SCAN", null, null, false, 0, 0, 0); }
    String start(String[] addresses, String[] hardware, boolean block, int up, int down, int seconds) {
        return begin("START", addresses, hardware, block, up, down, seconds);
    }
    private synchronized String begin(String operation, String[] addresses, String[] hardware, boolean block, int up, int down, int seconds) {
        if (busy) return "lan_busy";
        Link network;
        String command;
        try {
            network = operation.equals("INFO") ? null : link();
            command = operation.equals("INFO") ? "INFO\n" : operation + " " + network.arguments();
            if (operation.equals("START")) {
                if (addresses == null || hardware == null || addresses.length != hardware.length || addresses.length < 1 || addresses.length > 32
                        || seconds < 1 || seconds > 3600 || up < 0 || down < 0 || up > 1_024_000 || down > 1_024_000
                        || (!block && up == 0 && down == 0)) throw new IllegalArgumentException("invalid_request");
                if (selected == null || !selected.key.equals(network.key)) throw new IllegalArgumentException("scan_required");
                command += " " + seconds + " " + (block ? "BLOCK" : "LIMIT") + " " + (block ? 0 : up) + " " + (block ? 0 : down) + " " + addresses.length + "\n";
                Set<String> unique = new HashSet<>();
                for (int i = 0; i < addresses.length; i++) {
                    String address = NetLabLanProtocol.ipv4(addresses[i]), mac = NetLabLanProtocol.mac(hardware[i]);
                    Bundle cached = peers.get(address);
                    if (cached == null || !cached.getString("mac", "").equals(mac) || !unique.add(address)) throw new IllegalArgumentException("scan_required");
                    command += "PEER " + address + " " + mac + "\n";
                }
                command += "COMMIT\n";
            } else if (!operation.equals("INFO")) command += "\n";
        } catch (IllegalArgumentException | SecurityException failure) { return failure.getMessage(); }
        if (operation.equals("SCAN")) { peers.clear(); evidence.clear(); gatewayMac = ""; }
        if (operation.equals("START")) {
            evidence.clear();
            Set<String> chosen = new HashSet<>(Arrays.asList(addresses)); peers.keySet().retainAll(chosen);
            for (Bundle d : peers.values()) {
                d.putBoolean("lan_managed", true); d.putString("state", "applying");
                requestPolicy(d,block,up,down);
                for (String key : new String[]{"forwarded_up", "forwarded_down", "dropped_up", "dropped_down"}) d.putLong(key, 0);
            }
        }
        selected = network == null ? selected : network;
        scope = network == null ? scope : network.scope;
        action = operation; status = "requesting_root"; error = ""; busy = true; stopped = ended = active = false;
        helperExit=-1;lastEnd="";
        android.util.Log.i("HeckerNetLab","Request "+operation+" peers="+(addresses==null?0:addresses.length)+" mode="+(block?"BLOCK":"LIMIT"));
        sessionSeconds = seconds; expires = 0;
        launched = SystemClock.elapsedRealtime(); deadline = launched + seconds * 1000L + 30_000; stopAt = 0;
        ticker = clock.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
        final String request = command;
        io.execute(() -> run(request, operation, seconds)); return "ok";
    }
    private void requestPolicy(Bundle d,boolean block,int up,int down){
        d.putString("requested_mode",block?"BLOCK":"LIMIT");d.putInt("requested_up",block?0:up);d.putInt("requested_down",block?0:down);
        d.putBoolean("policy_pending",true);d.putBoolean("lan_managed",true);d.putString("state","applying");
        evidence.put(d.getStringArrayList("addresses").get(0),new NetLabTrafficEvidence());
    }
    synchronized String update(String[] addresses,String[] hardware,boolean block,int up,int down){
        if(!NetLabSliderPolicy.canEdit(active,busy,stopped,status,SystemClock.elapsedRealtime(),expires))return "not_active";
        try{if(selected==null||!selected.key.equals(link().key)){stop(false);return "link_changed";}}
        catch(IllegalArgumentException|SecurityException changed){stop(false);return changed.getMessage();}
        try{
            if(addresses==null||hardware==null||addresses.length!=hardware.length||addresses.length<1||addresses.length>32
                    ||up<0||down<0||up>1_024_000||down>1_024_000||!block&&up==0&&down==0)return "invalid_request";
            List<Bundle> chosen=new ArrayList<>();StringBuilder command=new StringBuilder();Set<String> unique=new HashSet<>();
            for(int i=0;i<addresses.length;i++){
                String address=NetLabLanProtocol.ipv4(addresses[i]),mac=NetLabLanProtocol.mac(hardware[i]);Bundle d=peers.get(address);
                if(d==null||!mac.equals(d.getString("mac"))||!unique.add(address))return "not_owned";
                chosen.add(d);command.append("SET ").append(address).append(block?" BLOCK 0 0":" LIMIT "+up+" "+down).append('\n');
            }
            if(!send(command.toString().trim()))return "controller_lost";
            for(Bundle d:chosen)requestPolicy(d,block,up,down);
            android.util.Log.i("HeckerNetLab","Update peers="+chosen.size()+" mode="+(block?"BLOCK":"LIMIT"));return "ok";
        }catch(IllegalArgumentException|SecurityException failure){return failure.getMessage();}
    }
    private void run(String request, String operation, int seconds) {
        java.lang.Process process = null;
        try {
            if (operation.equals("START")) prepareVisibleSession(seconds);
            // No user-controlled text enters this shell command. Configuration is stdin data only.
            process = new ProcessBuilder("su", "-c", "exec /system_ext/bin/hecker-netlab-lan")
                    .redirectError(ProcessBuilder.Redirect.to(new File("/dev/null"))).start();
            synchronized (this) {
                child = process; input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII));
                if (stopped) { input.close(); } else { input.write(request); input.flush(); }
            }
            String line;
            while ((line = NetLabLanProtocol.readLine(process.getInputStream())) != null) record(NetLabLanProtocol.decode(line));
            synchronized (this) { if (!ended && !stopped) error = "root_or_helper_unavailable"; }
        } catch (IOException | RuntimeException failure) {
            synchronized (this) { if (!stopped) error = "notification_unavailable".equals(failure.getMessage()) ? "notification_unavailable" : "root_or_helper_unavailable"; }
        } finally {
            synchronized (this) { send("STOP"); if (input != null) try { input.close(); } catch (IOException ignored) {} input = null; }
            if (process != null) {
                try { if (!process.waitFor(12, TimeUnit.SECONDS)) process.destroyForcibly();else synchronized(this){helperExit=process.exitValue();} }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); process.destroy(); }
            }
            synchronized (this) {
                child = null; busy = active = false;
                if (ticker != null) ticker.cancel(false); ticker = null;
                status = error.isEmpty() ? (root ? "ready" : "idle") : "failed";
                for (Bundle d : peers.values()) {
                    d.putBoolean("lan_managed", false); d.putString("state", "observed"); d.remove("until");
                    clearPolicy(d);
                }
                if (wake != null && wake.isHeld()) wake.release(); wake = null;
                notifications.cancel(1978);
                android.util.Log.i("HeckerNetLab","End action="+action+" reason="+lastEnd+" error="+error+" exit="+helperExit);
            }
        }
    }
    private void prepareVisibleSession(int seconds) {
        if (!notifications.areNotificationsEnabled()) throw new IllegalStateException("notification_unavailable");
        NotificationChannel channel = new NotificationChannel("netlab_lan", context.getString(R.string.netlab_lan_title), NotificationManager.IMPORTANCE_LOW);
        notifications.createNotificationChannel(channel);
        if (notifications.getNotificationChannel("netlab_lan").getImportance() == NotificationManager.IMPORTANCE_NONE)
            throw new IllegalStateException("notification_unavailable");
        PendingIntent stop = PendingIntent.getBroadcast(context, 3, new Intent(context, NetLabReceiver.class).setAction("org.hecker.netlab.LAN_STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note = new Notification.Builder(context, "netlab_lan").setSmallIcon(R.drawable.ic_netlab)
                .setContentTitle(context.getString(R.string.netlab_lan_title)).setContentText(context.getString(R.string.netlab_lan_active))
                .setOngoing(true).setContentIntent(stop).addAction(new Notification.Action.Builder(null, context.getString(R.string.netlab_lan_stop), stop).build()).build();
        notifications.notify(1978, note);
        synchronized (this) {
            wake = context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeckerNetLab:IPv4Relay");
            wake.acquire(seconds * 1000L + 45_000);
        }
    }
    private Bundle peer(String address, String mac) {
        Bundle b = new Bundle(); b.putString("id", "lan:" + scope + ":" + address); b.putString("mac", mac);
        b.putStringArrayList("addresses", new ArrayList<>(Collections.singletonList(address)));
        b.putString("name", ""); b.putString("os", "unknown"); b.putBoolean("online", true); b.putBoolean("managed", false);
        b.putBoolean("root_peer", true); b.putString("state", "observed");
        b.putLong("observed_elapsed", SystemClock.elapsedRealtime()); b.putLong("observed_wall", System.currentTimeMillis()); return b;
    }
    private void clearPolicy(Bundle d){
        d.putString("mode","PASS");d.putInt("up_kbps",0);d.putInt("down_kbps",0);d.putBoolean("policy_pending",false);
        d.remove("requested_mode");d.remove("requested_up");d.remove("requested_down");
    }
    private synchronized void record(String[] w) {
        switch (w[0]) {
            case "READY": root = true; ended = true; break;
            case "LINK": root = true; gatewayMac = w[2]; status = "resolving"; break;
            case "PEER":
                if (!action.equals("SCAN") || peers.size() >= 255) throw new IllegalArgumentException("invalid_helper_output");
                peers.put(w[1], peer(w[1], w[2])); break;
            case "STARTED":
                if (!action.equals("START")) throw new IllegalArgumentException("invalid_helper_output");
                active = true; status = "active_unverified";
                android.util.Log.i("HeckerNetLab","Helper started; waiting for selected IPv4 traffic");
                expires = SystemClock.elapsedRealtime() + sessionSeconds * 1000L;
                for (Bundle d : peers.values()) { d.putString("state", "active_unverified"); d.putLong("until", System.currentTimeMillis() + sessionSeconds * 1000L); } break;
            case "STAT":
                Bundle d = peers.get(w[1]); if (d == null || !action.equals("START")) throw new IllegalArgumentException("invalid_helper_output");
                d.putInt("up_kbps", Integer.parseInt(w[3])); d.putInt("down_kbps", Integer.parseInt(w[4]));
                d.putString("mode",w[2]);
                String[] names = {"forwarded_up", "forwarded_down", "dropped_up", "dropped_down"};long[] counts=new long[4];
                for (int i = 0; i < names.length; i++) { counts[i]=Long.parseLong(w[i+5]);d.putLong(names[i],counts[i]); }
                boolean matches=!d.getBoolean("policy_pending")||w[2].equals(d.getString("requested_mode"))
                        &&d.getInt("up_kbps")==d.getInt("requested_up")&&d.getInt("down_kbps")==d.getInt("requested_down");
                if(!matches)break;
                d.putBoolean("policy_pending",false);
                boolean traffic=evidence.computeIfAbsent(w[1],ignored->new NetLabTrafficEvidence()).observe(w[2],d.getInt("up_kbps"),d.getInt("down_kbps"),counts[0],counts[1],counts[2],counts[3]);
                boolean controlled = !w[2].equals("PASS"); d.putBoolean("lan_managed", controlled); d.putBoolean("online", true);
                if (!controlled) d.remove("until");
                d.putString("state", !controlled ? "observed" : !traffic ? "active_unverified" : w[2].equals("BLOCK") ? "blocking" : "relaying");
                if(active&&!stopped&&!status.equals("restoring")){boolean confirmed=false;for(Bundle peer:peers.values())confirmed|=Arrays.asList("blocking","relaying").contains(peer.getString("state"));status=confirmed?"active":"active_unverified";} break;
            case "RESTORED":
                Bundle restored = peers.get(w[1]); if (restored != null) { restored.putBoolean("lan_managed", false); restored.putString("state", "observed"); restored.remove("until");clearPolicy(restored); } break;
            case "STOPPING":
                active = false; status = "restoring";
                for (Bundle owned : peers.values()) if (owned.getBoolean("lan_managed")) owned.putString("state", "restoring"); break;
            case "ERROR": if (!stopped || !(w[1].equals("cancelled") || w[1].equals("controller_lost"))) error = w[1];lastEnd=w[1];android.util.Log.w("HeckerNetLab","Helper error="+w[1]); ended = true; break;
            case "END":
                ended = true; active = false;
                lastEnd=w[1];
                if (!Arrays.asList("scanned", "stopped", "expired_or_watchdog", "cancelled").contains(w[1]) && !stopped) error = w[1];
                if (w[1].endsWith("repair_unavailable")) error = w[1]; break;
            default: throw new IllegalArgumentException("invalid_helper_output");
        }
    }
    private void tick() {
        try {
            Link expected;
            synchronized (this) {
                if (!busy) return;
                if (stopped && child != null && SystemClock.elapsedRealtime() - stopAt > 12_000) { child.destroyForcibly(); return; }
                if(!active&&!status.equals("restoring")&&SystemClock.elapsedRealtime()-launched>25_000){error="startup_timeout";stop();return;}
                if(SystemClock.elapsedRealtime()>deadline){error="session_timeout";stop();return;}
                expected = action.equals("INFO") ? null : selected;
            }
            if (!action.equals("INFO") && context.getPackageManager().checkPermission("android.permission.ACCESS_LOCAL_NETWORK", "org.hecker.suite") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                synchronized (this) { error = "local_access_revoked"; stop(); } return;
            }
            boolean changed = false;
            if (expected != null) { try { changed = !expected.key.equals(link().key); } catch (RuntimeException lost) { changed = true; } }
            synchronized (this) {
                if (changed) { error = "link_changed"; stop(false); }
                else if (active && !stopped) send("PING");
            }
        } catch (RuntimeException failure) { synchronized (this) { error = "controller_lost"; stop(); } }
    }
    private boolean send(String command) {
        if (input == null) return false;
        try { input.write(command + "\n"); input.flush();return true; }
        catch (IOException lost) { try { input.close(); } catch (IOException ignored) {} input = null;if(!ended&&!stopped)error="controller_lost";return false; }
    }
    synchronized String restore(String address) {
        try { address = NetLabLanProtocol.ipv4(address); } catch (IllegalArgumentException bad) { return "invalid_request"; }
        Bundle d = peers.get(address);
        if (!active || stopped || d == null || !d.getBoolean("lan_managed")) return "not_owned";
        try{if(selected==null||!selected.key.equals(link().key)){stop(false);return "link_changed";}}
        catch(IllegalArgumentException|SecurityException changed){stop(false);return changed.getMessage();}
        if(!send("RESTORE " + address))return "controller_lost";d.putString("state", "restoring");
        boolean others=false;for(Bundle peer:peers.values())if(peer!=d&&peer.getBoolean("lan_managed"))others=true;
        if(!others)status="restoring";return "ok";
    }
    synchronized String stop() {
        boolean repair = true;
        if (busy && selected != null && !action.equals("INFO")) {
            try { repair = selected.key.equals(link().key); } catch (RuntimeException lost) { repair = false; }
        }
        return stop(repair);
    }
    private synchronized String stop(boolean repair) {
        if (!stopped) stopAt = SystemClock.elapsedRealtime();
        stopped = true;
        if (busy) {
            status = "restoring"; send(repair ? "STOP" : "ABORT");
            // EOF also cancels a root prompt or the helper's preflight, before any poisoning.
            if (input != null) try { input.close(); } catch (IOException ignored) {} input = null;
        }
        return "ok";
    }
}
