// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import java.net.InetAddress;
import java.util.*;

/** Select only usable endpoint families; support numeric IPv4 endpoints on NAT64 mobile networks. */
public final class VpnUnderlay {
    private VpnUnderlay() {}
    public static List<String> addresses(List<String> resolved,boolean ipv4,boolean ipv6,String nat64)throws Exception {
        LinkedHashSet<String> preferred=new LinkedHashSet<>(),fallback=new LinkedHashSet<>();
        for(String address:resolved){
            VpnConfig.ip(address);
            if(address.indexOf(':')>=0){if(ipv6)preferred.add(address);}
            else if(ipv4)preferred.add(address);
            else if(ipv6){
                String translated=synthesize(address,nat64);
                if(translated!=null)preferred.add(translated);
                // Some carriers expose IPv4 through stacked CLAT while the physical
                // link has only IPv6 addresses. Keep IPv4 as a bounded fallback.
                fallback.add(address);
            }
        }
        preferred.addAll(fallback);return new ArrayList<>(preferred);
    }
    public static String synthesize(String ipv4,String prefix)throws Exception {
        if(prefix==null)return null;
        int slash=prefix.lastIndexOf('/');if(slash<=0)return null;
        int bits=Integer.parseInt(prefix.substring(slash+1));
        if(!Set.of(32,40,48,56,64,96).contains(bits))return null;
        VpnConfig.ip(ipv4);if(ipv4.indexOf(':')>=0)throw new IllegalArgumentException("Expected IPv4 endpoint");
        String base=prefix.substring(0,slash);VpnConfig.ip(base);
        byte[] address=InetAddress.getByName(base).getAddress(),v4=InetAddress.getByName(ipv4).getAddress();
        if(address.length!=16)return null;
        Arrays.fill(address,bits/8,address.length,(byte)0);
        int cursor=bits/8;
        for(byte value:v4){if(cursor==8)cursor++;address[cursor++]=value;}
        return InetAddress.getByAddress(address).getHostAddress();
    }
}
