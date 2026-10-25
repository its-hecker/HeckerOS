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
        String gateway(){for(RouteInfo route:properties.getRoutes())if(route.isDefaultRoute()&&route.hasGateway())return route.getGateway().getHostAddress();return "";}
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
    void scan(Link link,Result callback){
        List<String> targets=link.targets();CompletionService<Bundle> results=new ExecutorCompletionService<>(pool);
        for(String ip:targets)results.submit(()->probe(link,ip));
        new Thread(()->{List<Bundle> found=new ArrayList<>();int probed=0;
            try{for(int i=0;i<targets.size()&&!cancelled;i++){Future<Bundle> ready=results.poll(2,TimeUnit.SECONDS);if(ready==null){cancelled=true;break;}
                Bundle device=ready.get();probed++;if(device!=null)found.add(device);}}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();cancelled=true;}catch(ExecutionException failure){cancelled=true;}
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
        Bundle device=new Bundle();device.putString("id","lan:"+link.scope+":"+address);device.putBoolean("online",true);device.putBoolean("managed",false);
        device.putString("mac","");device.putString("state","observed");device.putLong("observed_wall",System.currentTimeMillis());device.putLong("observed_elapsed",android.os.SystemClock.elapsedRealtime());
        device.putStringArrayList("addresses",new ArrayList<>(Collections.singletonList(address)));return device;
    }
    static String gatewayMac(Link link){
        // Passive read only. Modern Android commonly denies this; do not bypass SELinux or claim ARP protection then.
        try{List<String> lines=Files.readAllLines(Paths.get("/proc/net/arp"));
            for(String line:lines){String[] fields=line.trim().split("\\s+");if(fields.length>=6&&fields[0].equals(link.gateway())
                    &&fields[5].equals(link.properties.getInterfaceName())&&Integer.decode(fields[2])==2)
                return org.hecker.netlab.NetLabPolicy.normalize(fields[3]);}
        }catch(IOException|IllegalArgumentException denied){}return "";
    }
    @Override public void close(){cancelled=true;for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}pool.shutdownNow();}
}
