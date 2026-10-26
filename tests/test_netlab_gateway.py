# SPDX-License-Identifier: Apache-2.0
"""Execute the production gateway selector against mixed IPv4/IPv6 route orders."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]

class NetLabGatewayTests(unittest.TestCase):
    def test_actual_selector_keeps_ipv4_controller_and_master_router_exclusion_aligned(self):
        source=(ROOT/'app/src/org/hecker/suite/NetLabDiscovery.java').read_text()
        start=source.index('String gateway()');opening=source.index('{',start);depth=1;end=opening+1
        while depth:
            depth+=(source[end]=='{')-(source[end]=='}');end+=1
        method=source[start:end]
        harness='''import java.net.*;import java.util.*;
class RouteInfo {
 final boolean defaults,has;final InetAddress address;
 RouteInfo(boolean d,boolean h,InetAddress a){defaults=d;has=h;address=a;}
 boolean isDefaultRoute(){return defaults;}boolean hasGateway(){return has;}InetAddress getGateway(){return address;}
}
class Properties {List<RouteInfo> routes;List<RouteInfo> getRoutes(){return routes;}}
class Probe {Properties properties=new Properties();Probe(RouteInfo...routes){properties.routes=Arrays.asList(routes);}
'''+method+'''}
public class GatewayTest {
 static void check(String want,RouteInfo...routes){String actual=new Probe(routes).gateway();if(!want.equals(actual))throw new AssertionError("Expected "+want+" got "+actual);}
 public static void main(String[] args)throws Exception {
  InetAddress v4=InetAddress.getByAddress(new byte[]{(byte)192,(byte)168,1,1});
  byte[] six=new byte[16];six[0]=(byte)0xfe;six[1]=(byte)0x80;six[15]=1;InetAddress v6=InetAddress.getByAddress(six);
  RouteInfo default4=new RouteInfo(true,true,v4),default6=new RouteInfo(true,true,v6),local4=new RouteInfo(false,true,v4);
  check("192.168.1.1",default6,default4);check("192.168.1.1",default4,default6);
  check("192.168.1.1",local4,default6,default4);check("",default6);check("",local4);check("");
  check("192.168.1.1",new RouteInfo(true,false,null),default4);check("",new RouteInfo(true,true,null));
  System.out.println("NetLab actual gateway selector: 8 dual-stack, route-order and missing-gateway checks PASS");
 }
}
'''
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp);(path/'GatewayTest.java').write_text(harness)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',temp,str(path/'GatewayTest.java')],check=True)
            subprocess.run(['java','-cp',temp,'GatewayTest'],check=True)

if __name__=='__main__':unittest.main()
