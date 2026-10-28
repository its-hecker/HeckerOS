# SPDX-License-Identifier: Apache-2.0
"""Execute the production scan-enrichment scheduler with queued workers and a virtual clock."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]


def method(source, signature):
    start=source.index(signature);opening=source.index('{',start);depth=1;end=opening+1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start:end]


HARNESS=r'''
import java.util.*;
import org.hecker.netlab.*;
class SystemClock {static long now;static long elapsedRealtime(){return now;}}
class Clock {static void pause(long amount)throws InterruptedException{SystemClock.now+=amount;}}
class Bundle {
 final Map<String,Object> values=new HashMap<>();
 String getString(String key,String fallback){return (String)values.getOrDefault(key,fallback);}
 boolean getBoolean(String key){return Boolean.TRUE.equals(values.get(key));}
}
class Queue {
 final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
 void execute(Runnable r){jobs.add(r);}void post(Runnable r){jobs.add(r);}
 void drain(){while(!jobs.isEmpty())jobs.remove().run();}
}
class Preferences {
 boolean enabled=true;final NetLabSavedDevices names=new NetLabSavedDevices("");
 boolean onlineIdentity(){return enabled;}
 String customName(Bundle d){return names.label(d.getString("mac",""),"");}
}
public class IdentityLifecycleProbe {
 boolean started=true,identityBusy;volatile int identityEpoch;long identityUntil=30_000;
 String scope="wifi-A";final Set<String> identityAttempted=new HashSet<>();
 final List<Bundle> devices=new ArrayList<>();final Preferences preferences=new Preferences();
 final Queue identityWorker=new Queue(),main=new Queue();final List<String> sent=new ArrayList<>();
 final List<Long> times=new ArrayList<>();int refreshed,rendered;
 final NetLabOnlineVendors onlineVendors=new NetLabOnlineVendors("",text->{},prefix->{
  sent.add(prefix);times.add(SystemClock.now);return new NetLabOnlineVendors.Reply(200,"Example manufacturer",0);
 },()->1_700_000_000_000L+SystemClock.now);
 void mergeDevices(){refreshed++;}void updateHeader(){}void renderDevices(){rendered++;}
 // METHODS
 void peer(String mac){Bundle d=new Bundle();d.values.put("mac",mac);d.values.put("online",true);devices.add(d);}
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[]args){
  IdentityLifecycleProbe p=new IdentityLifecycleProbe();String first="00:11:22:33:44:55";
  switch(args[0]){
   case "eligible_only":
    p.peer(first);p.peer("02:11:22:33:44:55");p.peer("00:22:33:44:55:66");p.peer("");
    p.preferences.names.save("00:22:33:44:55:66","Saved",false);
    p.scheduleIdentification();check(p.identityWorker.jobs.size()==1,"Duplicate work");
    p.identityWorker.drain();p.main.drain();
    check(p.sent.equals(List.of("001122")),"Private, missing or named peer sent online: "+p.sent);
    check(p.refreshed==1,"Fresh callback not displayed");break;
   case "rename_in_flight":
    p.peer(first);p.scheduleIdentification();p.preferences.names.save(first,"My phone",false);
    p.identityWorker.drain();p.main.drain();
    check(p.preferences.names.label(first,p.onlineVendors.cached(first)).equals("My phone"),"Late lookup overwrote alias");break;
   case "cancel_after_response":
    p.peer(first);p.scheduleIdentification();p.identityWorker.drain();p.cancelIdentification();p.main.drain();
    check(p.refreshed==0&&p.rendered==0,"Cancelled callback touched UI");break;
   case "network_changed":
    p.peer(first);p.scheduleIdentification();p.identityWorker.drain();p.scope="wifi-B";p.main.drain();
    check(p.refreshed==0,"Result crossed network scope");break;
   case "stopped_before_work":
    p.peer(first);p.scheduleIdentification();p.started=false;p.cancelIdentification();p.identityWorker.drain();p.main.drain();
    check(p.sent.isEmpty()&&p.refreshed==0,"Stopped Activity leaked work");break;
   case "disabled":
    p.peer(first);p.preferences.enabled=false;p.scheduleIdentification();
    check(p.identityWorker.jobs.isEmpty(),"Disabled setting queued Internet access");break;
   case "bounded":
    for(int i=0;i<100;i++)p.peer(String.format("00%04X334455",i));
    p.scheduleIdentification();
    while(!p.identityWorker.jobs.isEmpty()){p.identityWorker.drain();p.main.drain();}
    check(p.sent.size()==32,"Scan exceeded budget: "+p.sent.size());
    for(int i=1;i<p.times.size();i++)check(p.times.get(i)-p.times.get(i-1)>=600,"Provider requests not paced");break;
   default:throw new AssertionError(args[0]);
  }
 }
}
'''


class NetLabIdentityLifecycleTests(unittest.TestCase):
    def test_real_scheduler_retains_names_and_rejects_stale_callbacks(self):
        source=(ROOT/'app/src/org/hecker/suite/NetworkLabActivity.java').read_text()
        methods='\n'.join(method(source,s) for s in ('private void scheduleIdentification(', 'private void cancelIdentification('))
        methods=methods.replace('Thread.sleep(600)','Clock.pause(600)')
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);java=path/'IdentityLifecycleProbe.java'
            java.write_text(HARNESS.replace('// METHODS',methods))
            common=ROOT/'netlab/common/org/hecker/netlab'
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(path),str(java),
                *[str(common/name) for name in ('NetLabIdentity.java','NetLabSavedDevices.java','NetLabOnlineVendors.java')]],check=True)
            for scenario in ('eligible_only','rename_in_flight','cancel_after_response','network_changed','stopped_before_work','disabled','bounded'):
                with self.subTest(scenario=scenario):
                    subprocess.run(['java','-cp',str(path),'IdentityLifecycleProbe',scenario],check=True,capture_output=True)


if __name__=='__main__':unittest.main()
