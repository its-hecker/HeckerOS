// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** One owned tunnel at a time. Only this controller UID is routed during tests. */
final class VpnTestRunner {
    static final class Target {
        final String id, name, config;
        Target(String id, String name, String config) {
            this.id = id; this.name = name; this.config = config;
        }
    }
    private final Context context;
    private final ConnectivityManager connectivity;
    private final Network underlay;
    private final List<Target> targets;
    private final boolean speed;
    private final Runnable finished;
    private final KernelChannel channel = new KernelChannel();
    private final ArrayList<Bundle> rows = new ArrayList<>();
    private final long startedAt = System.currentTimeMillis();
    private final String networkLabel;
    private volatile boolean cancelled;
    private volatile HttpURLConnection currentRequest;
    private Thread thread;
    private boolean running = true;
    private String phase = "queued", ending = "";
    private int completed;

    VpnTestRunner(Context context, ConnectivityManager connectivity, Network underlay,
            List<Target> targets, boolean speed, Runnable finished) {
        this.context = context; this.connectivity = connectivity; this.underlay = underlay;
        this.targets = List.copyOf(targets); this.speed = speed; this.finished = finished;
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(underlay);
        networkLabel = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                : caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Mobile data" : "Network";
        for (Target target : targets) {
            Bundle row = new Bundle(); row.putString("id", target.id); row.putString("name", target.name);
            row.putString("result", "queued"); rows.add(row);
        }
    }
    void start() { thread = new Thread(this::run, "HeckerVPN-tests"); thread.start(); }
    void cancel() {
        cancelled = true; channel.abort();
        HttpURLConnection request = currentRequest;
        if (request != null) request.disconnect();
        Thread runningThread = thread; if (runningThread != null) runningThread.interrupt();
    }
    synchronized Bundle snapshot() {
        Bundle state = new Bundle(); state.putBoolean("running", running);
        state.putInt("completed", completed); state.putInt("total", targets.size());
        state.putString("phase", phase); state.putString("ending", ending);
        state.putString("network", networkLabel); state.putLong("started", startedAt);
        state.putBoolean("network_current", underlay.equals(connectivity.getActiveNetwork()));
        ArrayList<Bundle> copy = new ArrayList<>(); for (Bundle row : rows) copy.add(new Bundle(row));
        state.putParcelableArrayList("rows", copy); return state;
    }
    private synchronized void phase(int index, String value) {
        phase = value; rows.get(index).putString("result", value);
    }
    private void checkNetwork() throws Exception {
        if (cancelled) throw new InterruptedException();
        if (!underlay.equals(connectivity.getActiveNetwork())) throw new NetworkChanged();
    }
    private static final class NetworkChanged extends Exception {}
    private void run() {
        try {
            for (int i = 0; i < targets.size(); i++) {
                checkNetwork(); Target target = targets.get(i); phase(i, "starting");
                String outcome = "setup_failed", detail = ""; long latency = -1; double mbps = -1;
                try {
                    JSONObject state = up(target);
                    checkNetwork(); int netid = state.optInt("netid");
                    if (!state.optBoolean("configured") || netid < 4096 || netid > 65534)
                        throw new IllegalArgumentException("Missing test network");
                    // AOSP Network handles retain the low 32-bit handle marker.
                    // Use the public factory rather than its hidden netId constructor.
                    Network tunnel = Network.fromNetworkHandle(((long) netid << 32)
                            | (underlay.getNetworkHandle() & 0xffffffffL));
                    phase(i, "checking");
                    long deadline = SystemClock.elapsedRealtime() + 15000;
                    int probe = 0;
                    while (latency < 0 && SystemClock.elapsedRealtime() < deadline) {
                        checkNetwork();
                        String endpoint = probe++ % 2 == 0 ? "https://www.gstatic.com/generate_204" : "https://cp.cloudflare.com/generate_204";
                        try { latency = https(tunnel, endpoint, deadline, false); break; }
                        catch (java.io.IOException unavailable) { /* Try a second independent probe host. */ }
                        long left = deadline - SystemClock.elapsedRealtime();
                        if (left > 0) Thread.sleep(Math.min(500, left));
                    }
                    checkNetwork();
                    state = channel.call("STATUS");
                    boolean handshake = state.optLong("handshake") > 0;
                    if (latency >= 0 && handshake) {
                        outcome = "working";
                        if (speed) {
                            phase(i, "speed");
                            try {
                                long elapsed = https(tunnel, "https://speed.cloudflare.com/__down?bytes=1048576",
                                        SystemClock.elapsedRealtime() + 15000, true);
                                mbps = 1048576.0 * 8 / Math.max(1, elapsed) / 1000;
                            } catch (java.io.IOException unavailable) { /* Connectivity still passed; speed is unavailable. */ }
                        }
                    } else outcome = !handshake ? "no_handshake" : "internet_failed";
                } catch (NetworkChanged changed) { throw changed; }
                catch (InterruptedException stopped) { throw stopped; }
                catch (KernelChannel.Failure failure) {
                    // The daemon supplies bounded, credential-free errors, never arbitrary parser output.
                    detail = failure.getMessage();
                    if (failure.cleanupRequired) throw failure;
                } catch (IllegalArgumentException invalid) { outcome = "invalid_configuration"; }
                catch (Exception unavailable) { outcome = "setup_failed"; }
                finally {
                    if (!cancelled) channel.call("DOWN");
                    channel.close();
                }
                checkNetwork();
                synchronized (this) {
                    Bundle row = rows.get(i); row.putString("result", outcome); row.putString("detail", detail);
                    row.putBoolean("speed_requested", speed);
                    row.putLong("latency_ms", latency); row.putDouble("mbps", mbps); completed++;
                }
            }
        } catch (NetworkChanged changed) { ending = "network_changed"; }
        catch (InterruptedException stopped) { ending = "cancelled"; }
        catch (Exception failed) { ending = cancelled ? "cancelled" : "cleanup_failed"; }
        finally {
            channel.close();
            synchronized (this) {
                if (cancelled && ending.isEmpty()) ending = "cancelled";
                running = false; phase = "finished";
                for (Bundle row : rows) if (List.of("queued", "starting", "checking", "speed").contains(row.getString("result")))
                    row.putString("result", ending.equals("network_changed") ? "network_changed" : "cancelled");
            }
            finished.run();
        }
    }
    private JSONObject up(Target target) throws Exception {
        LinkProperties link = connectivity.getLinkProperties(underlay);
        boolean v4 = false, v6 = false;
        if (link != null) for (android.net.LinkAddress address : link.getLinkAddresses()) {
            v4 |= address.getAddress() instanceof Inet4Address;
            v6 |= address.getAddress() instanceof Inet6Address && !address.getAddress().isLinkLocalAddress();
        }
        final boolean ipv4 = v4, ipv6 = v6;
        final String prefix = link == null || link.getNat64Prefix() == null ? null : link.getNat64Prefix().toString();
        java.util.function.Function<String, List<String>> resolve = host -> {
            try {
                ArrayList<String> found = new ArrayList<>();
                for (InetAddress address : underlay.getAllByName(host))
                    if (!address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isLinkLocalAddress() && !address.isMulticastAddress())
                        found.add(address.getHostAddress());
                return VpnUnderlay.addresses(found, ipv4, ipv6, prefix);
            } catch (Exception failure) { throw new IllegalArgumentException("Test endpoint DNS failed"); }
        };
        List<String> apps = List.of(context.getPackageName());
        String config = VpnConfig.parse(target.config).forUnderlay("include", apps, host -> {
            List<String> addresses = resolve.apply(host);
            if (addresses.isEmpty()) throw new IllegalArgumentException("No usable test endpoint");
            return List.of(addresses.get(0));
        });
        VpnConfig rendered = VpnConfig.parse(config);
        if (!rendered.network.containsKey("MTU")) rendered.network.put("MTU", "1280");
        checkNetwork(); return channel.call("UP\n" + underlay.getNetId() + "\n" + rendered.render("include", apps));
    }
    private long https(Network network, String endpoint, long deadline, boolean download) throws Exception {
        checkNetwork(); long began = SystemClock.elapsedRealtime();
        int remaining = (int)Math.min(7500, deadline - began);
        if (remaining <= 0) throw new java.net.SocketTimeoutException();
        HttpURLConnection connection = (HttpURLConnection) network.openConnection(new URL(endpoint), Proxy.NO_PROXY);
        currentRequest = connection;
        try {
            connection.setConnectTimeout(remaining); connection.setReadTimeout(remaining);
            connection.setUseCaches(false); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Cache-Control", "no-cache");
            if (connection.getResponseCode() != (download ? 200 : 204)) throw new java.io.IOException("Probe rejected");
            if (download) {
                int count = 0; byte[] buffer = new byte[8192];
                try (InputStream input = connection.getInputStream()) {
                    while (count < 1048576) {
                        checkNetwork(); long left = deadline - SystemClock.elapsedRealtime();
                        if (left <= 0) throw new java.net.SocketTimeoutException();
                        connection.setReadTimeout((int)Math.min(7500, left));
                        int n = input.read(buffer, 0, Math.min(buffer.length, 1048576 - count));
                        if (n < 0) throw new java.io.IOException("Short download");
                        count += n;
                    }
                }
            }
            if (SystemClock.elapsedRealtime() > deadline) throw new java.net.SocketTimeoutException();
            return SystemClock.elapsedRealtime() - began;
        } finally { currentRequest = null; connection.disconnect(); }
    }
}
