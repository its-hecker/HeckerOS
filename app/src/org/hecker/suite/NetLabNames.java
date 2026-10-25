// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.hecker.netlab.NetLabIdentity;

/** A short, user-started mDNS discovery on the selected Wi-Fi Network only. */
final class NetLabNames implements AutoCloseable {
    private static final String[] TYPES = {"_device-info._tcp", "_companion-link._tcp",
            "_airplay._tcp", "_adb-tls-connect._tcp", "_androidtvremote2._tcp",
            "_googlecast._tcp", "_smb._tcp"};
    private final NsdManager nsd;
    private final Executor executor;
    private final List<NsdManager.DiscoveryListener> discoveries = new CopyOnWriteArrayList<>();
    private final Set<NsdManager.ResolveListener> resolutions = ConcurrentHashMap.newKeySet();
    private final Map<String, Bundle> names = new ConcurrentHashMap<>();
    private final AtomicInteger count = new AtomicInteger();
    private volatile boolean closed;

    NetLabNames(Context context, NetLabDiscovery.Link link) {
        executor = context.getMainExecutor();
        nsd = context.getSystemService(NsdManager.class);
        if (nsd == null) return;
        for (String type : TYPES) {
            NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
                public void onDiscoveryStarted(String type) { }
                public void onDiscoveryStopped(String type) { }
                public void onStartDiscoveryFailed(String type, int error) { discoveries.remove(this); }
                public void onStopDiscoveryFailed(String type, int error) { }
                public void onServiceLost(NsdServiceInfo info) { }
                public void onServiceFound(NsdServiceInfo info) {
                    if (closed || count.incrementAndGet() > 64) return;
                    info.setNetwork(link.network);
                    NsdManager.ResolveListener resolve = new NsdManager.ResolveListener() {
                        public void onResolveFailed(NsdServiceInfo value, int error) { resolutions.remove(this); }
                        public void onServiceResolved(NsdServiceInfo value) {
                            resolutions.remove(this);
                            if (closed || value.getNetwork() != null && !link.network.equals(value.getNetwork())) return;
                            String name = NetLabIdentity.clean(value.getServiceName());
                            String model = attribute(value, "model", "md", "ty");
                            String kind = NetLabIdentity.kind(name, model, value.getServiceType());
                            for (InetAddress address : value.getHostAddresses()) {
                                if (address.isLoopbackAddress() || address.isAnyLocalAddress()) continue;
                                String ip = address.getHostAddress();
                                Bundle entry = names.computeIfAbsent(ip, ignored -> new Bundle());
                                synchronized (entry) {
                                    // Keep stronger evidence from another advertised service.
                                    if (!model.isEmpty()) entry.putString("model", model);
                                    if (!name.isEmpty() && !value.getServiceType().startsWith("_adb-")) entry.putString("name", name);
                                    if (!kind.equals("unknown") && (entry.getString("os", "unknown").equals("unknown")
                                            || !kind.equals("apple") && !kind.equals("media"))) entry.putString("os", kind);
                                    entry.putString("identity_source", "mdns");
                                }
                            }
                        }
                    };
                    resolutions.add(resolve);
                    try { nsd.resolveService(info, executor, resolve); }
                    catch (RuntimeException unavailable) { resolutions.remove(resolve); }
                }
            };
            discoveries.add(listener);
            try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, link.network, executor, listener); }
            catch (RuntimeException unavailable) { discoveries.remove(listener); }
        }
    }
    private static String attribute(NsdServiceInfo info, String... keys) {
        for (String key : keys) {
            byte[] value = info.getAttributes().get(key);
            if (value != null && value.length <= 256) return NetLabIdentity.clean(new String(value, StandardCharsets.UTF_8));
        }
        return "";
    }
    Map<String, Bundle> snapshot() {
        Map<String, Bundle> copy = new HashMap<>();
        names.forEach((ip, entry) -> { synchronized (entry) { copy.put(ip, new Bundle(entry)); } });
        return copy;
    }
    @Override public void close() {
        closed = true;
        if (nsd != null) {
            for (NsdManager.DiscoveryListener listener : discoveries)
                try { nsd.stopServiceDiscovery(listener); } catch (RuntimeException ignored) { }
            for (NsdManager.ResolveListener listener : resolutions)
                try { nsd.stopServiceResolution(listener); } catch (RuntimeException ignored) { }
        }
        discoveries.clear(); resolutions.clear();
    }
}
