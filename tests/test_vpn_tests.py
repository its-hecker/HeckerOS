"""Run the production batch runner against deterministic Android/network fixtures."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ConnectionTesting(unittest.TestCase):
    def test_real_runner(self):
        sys.path.insert(0, str(ROOT / 'tools'))
        from compile_sdk import fetch
        lock = json.loads((ROOT/'tests/xmorph-inputs.lock.json').read_text())
        jar = fetch(ROOT/'generated/sdk', 'xmorph-json-20240303.jar', lock['xmorph-json-20240303.jar'])
        fixtures = {
            'android/content/Context.java': '''package android.content;
public class Context {public String getPackageName(){return "org.hecker.vpn";}}''',
            'android/os/Bundle.java': '''package android.os;
import java.util.*;
public class Bundle extends HashMap<String,Object>{
 public Bundle(){} public Bundle(Bundle b){super(b);}
 public void putString(String k,String v){put(k,v);} public String getString(String k){return getString(k,"");}
 public String getString(String k,String d){return (String)getOrDefault(k,d);}
 public void putBoolean(String k,boolean v){put(k,v);} public boolean getBoolean(String k){return (boolean)getOrDefault(k,false);}
 public void putInt(String k,int v){put(k,v);} public int getInt(String k){return (int)getOrDefault(k,0);}
 public void putLong(String k,long v){put(k,v);} public long getLong(String k){return (long)getOrDefault(k,0L);}
 public void putDouble(String k,double v){put(k,v);} public double getDouble(String k){return (double)getOrDefault(k,0.0);}
 public <T> void putParcelableArrayList(String k,ArrayList<T> v){put(k,v);}
 @SuppressWarnings("unchecked") public <T> ArrayList<T> getParcelableArrayList(String k,Class<T> c){return (ArrayList<T>)get(k);}
}''',
            'android/os/SystemClock.java': '''package android.os;
public class SystemClock {static long now;public static synchronized long elapsedRealtime(){return now+=android.net.Network.fail?4000:1;}}''',
            'android/net/NetworkCapabilities.java': '''package android.net;
public class NetworkCapabilities {public static final int TRANSPORT_WIFI=1,TRANSPORT_CELLULAR=2;public boolean hasTransport(int t){return t==1;}}''',
            'android/net/LinkAddress.java': '''package android.net;
public class LinkAddress {public java.net.InetAddress getAddress(){try{return java.net.InetAddress.getByName("192.0.2.2");}catch(Exception e){throw new AssertionError(e);}}}''',
            'android/net/LinkProperties.java': '''package android.net;
public class LinkProperties {public java.util.List<LinkAddress> getLinkAddresses(){return java.util.List.of(new LinkAddress());}public Object getNat64Prefix(){return null;}}''',
            'android/net/ConnectivityManager.java': '''package android.net;
public class ConnectivityManager {public Network active=new Network(100);public Network getActiveNetwork(){return active;}public NetworkCapabilities getNetworkCapabilities(Network n){return new NetworkCapabilities();}public LinkProperties getLinkProperties(Network n){return new LinkProperties();}}''',
            'android/net/Network.java': '''package android.net;
import java.io.*;import java.net.*;
public class Network {
 public static boolean fail,change;public static ConnectivityManager manager;final int id;
 public Network(int id){this.id=id;}public int getNetId(){return id;}
 public long getNetworkHandle(){return ((long)id<<32)|0xcafed00dL;}
 public static Network fromNetworkHandle(long h){if((h&0xffffffffL)!=0xcafed00dL)throw new AssertionError();return new Network((int)(h>>>32));}
 public boolean equals(Object o){return o instanceof Network && ((Network)o).id==id;}public int hashCode(){return id;}
 public InetAddress[] getAllByName(String host)throws Exception{return new InetAddress[]{InetAddress.getByName("203.0.113.2")};}
 public URLConnection openConnection(URL url,Proxy proxy){
  if(id!=5000||proxy!=Proxy.NO_PROXY)throw new AssertionError("Probe bypassed test tunnel");
  return new HttpURLConnection(url){
   public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
   public int getResponseCode(){if(getInstanceFollowRedirects()||getUseCaches())throw new AssertionError("Probe cache/redirect");if(change)manager.active=new Network(101);return fail?500:url.getHost().equals("speed.cloudflare.com")?200:204;}
   public InputStream getInputStream(){return new ByteArrayInputStream(new byte[1048576]);}
  };
 }
}''',
            'org/hecker/vpn/KernelChannel.java': '''package org.hecker.vpn;
import org.json.*;import java.util.*;import java.util.concurrent.*;
final class KernelChannel implements AutoCloseable {
 static boolean failNext,handshake=true,block;static CountDownLatch entered=new CountDownLatch(1);
 static final List<String> commands=Collections.synchronizedList(new ArrayList<>());boolean aborted;
 static final class Failure extends IllegalArgumentException{final boolean cleanupRequired=false;Failure(){super("Could not configure the VPN peer.");}}
 JSONObject call(String command)throws Exception{
  commands.add(command);
  if(command.startsWith("UP\\n")){
   entered.countDown();if(block){while(!aborted)Thread.sleep(5);throw new InterruptedException();}
   if(failNext){failNext=false;throw new Failure();}
   if(!command.contains("IncludedApplications=org.hecker.vpn\\n"))throw new AssertionError("WG test routed other apps");
  }else if(!command.equals("STATUS")&&!command.equals("DOWN"))throw new AssertionError("Unexpected tunnel command");
  if(aborted)throw new InterruptedException();
  return new JSONObject().put("ok",true).put("configured",true).put("netid",5000).put("handshake",handshake?10L:0L);
 }
 synchronized void abort(){aborted=true;}public void close(){}
}''',
            'org/hecker/vpn/RunnerProbe.java': r'''package org.hecker.vpn;
import android.content.*;import android.net.*;import android.os.*;import java.util.*;import java.util.concurrent.*;
public class RunnerProbe {
 static final String KEY="AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
 static final String WG="[Interface]\nPrivateKey="+KEY+"\nAddress=10.0.0.2/32\nDNS=1.1.1.1\n[Peer]\nPublicKey="+KEY+"\nEndpoint=203.0.113.2:51820\nAllowedIPs=0.0.0.0/0\n";
 static final VpnTestRunner.Target wg=new VpnTestRunner.Target("wg","Saved WG",WG);
 static final VpnTestRunner.Target alternate=new VpnTestRunner.Target("second","Second WG",WG.replace(":51820",":51821"));
 static void check(boolean b){if(!b)throw new AssertionError();}
 static ArrayList<Bundle> rows(Bundle b){return b.getParcelableArrayList("rows",Bundle.class);}
 static Bundle run(List<VpnTestRunner.Target> targets,boolean speed)throws Exception{
  ConnectivityManager cm=new ConnectivityManager();Network.manager=cm;CountDownLatch done=new CountDownLatch(1);
  VpnTestRunner runner=new VpnTestRunner(new Context(),cm,cm.active,targets,speed,done::countDown);runner.start();
  check(done.await(5,TimeUnit.SECONDS));Bundle b=runner.snapshot();check(!b.getBoolean("running"));
  check(!b.toString().contains(KEY));return b;
 }
 public static void main(String[] args)throws Exception{
  Bundle all=run(List.of(wg,alternate),true);check(all.getInt("completed")==2);
  for(Bundle row:rows(all)){check(row.getString("result").equals("working"));check(row.getDouble("mbps")>0);}
  check(KernelChannel.commands.stream().filter(c->c.equals("DOWN")).count()==2);
  KernelChannel.failNext=true;Bundle startup=run(List.of(wg,alternate),false);
  check(rows(startup).get(0).getString("result").equals("setup_failed"));
  check(rows(startup).get(0).getString("detail").equals("Could not configure the VPN peer."));
  check(rows(startup).get(1).getString("result").equals("working"));
  KernelChannel.handshake=false;Bundle noPeer=run(List.of(wg),false);
  check(rows(noPeer).get(0).getString("result").equals("no_handshake"));
  KernelChannel.handshake=true;Network.fail=true;Bundle noInternet=run(List.of(wg),false);
  check(rows(noInternet).get(0).getString("result").equals("internet_failed"));
  KernelChannel.handshake=true;Network.fail=false;Network.change=true;
  Bundle changed=run(List.of(wg,alternate),false);check(changed.getString("ending").equals("network_changed"));check(changed.getInt("completed")==0);
  Network.change=false;KernelChannel.block=true;KernelChannel.entered=new CountDownLatch(1);
  ConnectivityManager cm=new ConnectivityManager();Network.manager=cm;CountDownLatch done=new CountDownLatch(1);
  VpnTestRunner cancel=new VpnTestRunner(new Context(),cm,cm.active,List.of(wg,alternate),false,done::countDown);
  cancel.start();check(KernelChannel.entered.await(3,TimeUnit.SECONDS));cancel.cancel();check(done.await(3,TimeUnit.SECONDS));
  check(cancel.snapshot().getString("ending").equals("cancelled"));check(cancel.snapshot().getInt("completed")==0);
  System.out.println("Production batch runner: isolated routes, tunnel-bound HTTPS, speed cap, failure continuation, fresh handshake, network change, cancellation and credential-free results PASS");
 }
}''',
        }
        with tempfile.TemporaryDirectory(prefix='hecker-vpn-batch-') as temporary:
            out = Path(temporary)
            paths = []
            for name, text in fixtures.items():
                path = out/name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text); paths.append(path)
            paths += [ROOT/'vpn/common/org/hecker/vpn'/name for name in ['VpnConfig.java', 'VpnUnderlay.java']]
            paths.append(ROOT/'vpn/src/org/hecker/vpn/VpnTestRunner.java')
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', str(jar), '-d', str(out/'classes'), *map(str, paths)], check=True)
            subprocess.run(['java', '-cp', str(out/'classes')+':'+str(jar), 'org.hecker.vpn.RunnerProbe'], check=True, timeout=20)


if __name__ == '__main__': unittest.main()
