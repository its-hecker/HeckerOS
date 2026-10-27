// SPDX-License-Identifier: Apache-2.0
import java.util.*;
import org.hecker.netlab.NetLabGroupPolicy;

public final class NetLabGroupPolicyTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    private static NetLabGroupPolicy.Peer peer(String scope,String address,boolean online,boolean hotspot){
        return new NetLabGroupPolicy.Peer("lan:"+scope+":"+address,address,online,hotspot);
    }
    public static void main(String[] args){
        String scope="101:wlan0";Set<String> own=Set.of("192.168.1.8");
        List<NetLabGroupPolicy.Peer> input=new ArrayList<>();
        for(int i=1;i<=28;i++)input.add(peer(scope,"192.168.1."+i,true,false));
        input.add(peer("102:wlan0","192.168.1.29",true,false));
        input.add(peer(scope,"192.168.1.30",false,false));
        input.add(peer(scope,"192.168.1.31",true,true));
        input.add(peer(scope,"192.168.1.12",true,false));
        input.add(new NetLabGroupPolicy.Peer("lan:"+scope+":192.168.1.33","192.168.1.34",true,false));
        List<String> selected=NetLabGroupPolicy.wifiIds(scope,"192.168.1.1",own,input);
        check(selected.size()==26);check(new HashSet<>(selected).size()==26);
        check(!selected.contains("lan:"+scope+":192.168.1.1"));check(!selected.contains("lan:"+scope+":192.168.1.8"));
        check(!selected.contains("lan:102:wlan0:192.168.1.29"));check(!selected.contains("lan:"+scope+":192.168.1.30"));
        check(!selected.contains("lan:"+scope+":192.168.1.31"));check(!selected.contains("lan:"+scope+":192.168.1.33"));
        check(NetLabGroupPolicy.wifiIds("","",own,input).isEmpty());check(NetLabGroupPolicy.wifiIds(null,"",own,input).isEmpty());
        check(NetLabGroupPolicy.withinLimit(26));check(NetLabGroupPolicy.withinLimit(32));
        check(!NetLabGroupPolicy.withinLimit(0));check(!NetLabGroupPolicy.withinLimit(33));check(!NetLabGroupPolicy.withinLimit(-1));
        // Selection is derived afresh for the current Network handle, including the same reused subnet.
        check(NetLabGroupPolicy.wifiIds("102:wlan0","192.168.1.1",own,input).equals(List.of("lan:102:wlan0:192.168.1.29")));
        System.out.println("NetLab master selection: "+checks+" scope, self/router, duplicate, offline and controller-limit checks PASS");
    }
}
