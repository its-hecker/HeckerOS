// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Select a snapshot of this Wi-Fi's peers without including the phone, gateway or old scans. */
public final class NetLabGroupPolicy {
    public static final int MAX_PEERS=32;
    public static final class Peer {
        final String id,address;final boolean online,hotspot;
        public Peer(String id,String address,boolean online,boolean hotspot){this.id=id;this.address=address;this.online=online;this.hotspot=hotspot;}
    }
    public static List<String> wifiIds(String scope,String gateway,Set<String> local,Collection<Peer> peers){
        Set<String> selected=new LinkedHashSet<>();if(scope==null||scope.isEmpty())return new ArrayList<>();
        for(Peer peer:peers)if(peer.online&&!peer.hotspot&&peer.address!=null&&!peer.address.isEmpty()
                &&!peer.address.equals(gateway)&&!local.contains(peer.address)
                &&("lan:"+scope+":"+peer.address).equals(peer.id))selected.add(peer.id);
        return new ArrayList<>(selected);
    }
    public static boolean withinLimit(int count){return count>0&&count<=MAX_PEERS;}
    private NetLabGroupPolicy(){}
}
