// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.net.*;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.hecker.netlab.NetLabAddressPlan;

/** User-started, cancellable discovery bound to the actual Wi-Fi Network, including under a VPN. */
final class NetLabDiscovery implements AutoCloseable {
    static final class Link {
        final Network network; final LinkProperties properties; final String scope;
        Link(Network network,LinkProperties properties){this.network=network;this.properties=properties;scope=network.getNetworkHandle()+":"+properties.getInterfaceName();}
        String gateway(){for(RouteInfo route:properties.getRoutes())if(route.isDefaultRoute()&&route.hasGateway()&&route.getGateway() instanceof Inet4Address)return route.getGateway().getHostAddress();return "";}
        List<String> targets(){for(LinkAddress address:properties.getLinkAddresses())if(address.getAddress() instanceof Inet4Address)
            return NetLabAddressPlan.targets(address.getAddress().getAddress(),address.getPrefixLength());return Collections.emptyList();}
    }
    static Link wifi(Context context){
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);if(manager==null)return null;
        Network active=manager.getActiveNetwork();List<Network> networks=new ArrayList<>(Arrays.asList(manager.getAllNetworks()));
        if(active!=null){networks.remove(active);networks.add(0,active);}
        for(Network network:networks){NetworkCapabilities c=manager.getNetworkCapabilities(network);LinkProperties p=manager.getLinkProperties(network);
            if(c!=null&&p!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))return new Link(network,p);}
        return null;
    }
    interface Result {void complete(List<Bundle> devices,int probed,boolean cancelled);}
    private final ExecutorService pool=Executors.newFixedThreadPool(8);
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;
    private NetLabNames names;
    NetLabDiscovery(Context context, Link link) { names = new NetLabNames(context, link); }
    void scan(Link link,Result callback){
        long began = android.os.SystemClock.elapsedRealtime();
        List<String> targets=link.targets();CompletionService<Bundle> results=new ExecutorCompletionService<>(pool);
        for(String ip:targets)results.submit(()->probe(link,ip));
        new Thread(()->{List<Bundle> found=new ArrayList<>();int probed=0;
            try{for(int i=0;i<targets.size()&&!cancelled;i++){Future<Bundle> ready=results.poll(2,TimeUnit.SECONDS);if(ready==null){cancelled=true;break;}
                Bundle device=ready.get();probed++;if(device!=null)found.add(device);}}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();cancelled=true;}catch(ExecutionException failure){cancelled=true;}
            // Give local service advertisements a short bounded opportunity to arrive.
            try { while (!cancelled && android.os.SystemClock.elapsedRealtime()-began<3500) Thread.sleep(100); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); cancelled=true; }
            Map<String,Bundle> metadata=names.snapshot();Map<String,String> macs=neighbors(link);
            Set<String> seen=new HashSet<>();
            for(Bundle device:found){String ip=device.getStringArrayList("addresses").get(0);seen.add(ip);
                if(metadata.containsKey(ip))device.putAll(metadata.get(ip));device.putString("mac",macs.getOrDefault(ip,""));}
            // A scoped mDNS reply is also evidence of presence when TCP port 80 is silent.
            for(String ip:metadata.keySet())if(targets.contains(ip)&&seen.add(ip)){
                Bundle device=device(link,ip);device.putAll(metadata.get(ip));device.putString("mac",macs.getOrDefault(ip,""));found.add(device);}
            found.sort(Comparator.comparing(b->b.getString("id")));callback.complete(found,probed,cancelled);close();
        },"NetLabScanResults").start();
    }
    private Bundle probe(Link link,String address){
        if(cancelled)return null;boolean reachable=false;
        try(Socket socket=link.network.getSocketFactory().createSocket()){
            sockets.add(socket);try{socket.connect(new InetSocketAddress(InetAddress.getByName(address),80),350);reachable=true;}
            catch(ConnectException refused){Throwable cause=refused.getCause();reachable=cause instanceof ErrnoException&&((ErrnoException)cause).errno==OsConstants.ECONNREFUSED
                    ||refused.getMessage()!=null&&refused.getMessage().toLowerCase(Locale.ROOT).contains("refused");}
            finally{sockets.remove(socket);}
        }catch(IOException unavailable){return null;}
        if(!reachable||cancelled)return null;
        return device(link,address);
    }
    private static Bundle device(Link link,String address){
        Bundle device=new Bundle();device.putString("id","lan:"+link.scope+":"+address);device.putBoolean("online",true);device.putBoolean("managed",false);
        device.putString("mac","");device.putString("state","observed");device.putLong("observed_wall",System.currentTimeMillis());device.putLong("observed_elapsed",android.os.SystemClock.elapsedRealtime());
        device.putStringArrayList("addresses",new ArrayList<>(Collections.singletonList(address)));return device;
    }
    static String gatewayMac(Link link){
        return neighbors(link).getOrDefault(link.gateway(), "");
    }
    private static Map<String,String> neighbors(Link link){
        Map<String,String> values=new HashMap<>();
        // Passive read only. Modern Android commonly denies this; do not bypass SELinux or claim ARP protection then.
        try{List<String> lines=Files.readAllLines(Paths.get("/proc/net/arp"));
            for(String line:lines){String[] fields=line.trim().split("\\s+");if(fields.length>=6
                    &&fields[5].equals(link.properties.getInterfaceName())&&Integer.decode(fields[2])==2)
                values.put(fields[0],org.hecker.netlab.NetLabPolicy.normalize(fields[3]));}
        }catch(IOException|IllegalArgumentException denied){}return values;
    }
    @Override public void close(){cancelled=true;if(names!=null)names.close();for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}pool.shutdownNow();}
}
