# SPDX-License-Identifier: Apache-2.0
"""Exercise the production Binder client's failure/rebind lifecycle without a phone."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class VpnClientTests(unittest.TestCase):
    def test_retry_after_bind_failure_timeout_death_and_document_picker_close(self):
        sources = {
            'android/os/Bundle.java': '''package android.os;
public class Bundle extends java.util.HashMap<String,Object> {
 public void putString(String k,String v){put(k,v);} public String getString(String k){return (String)get(k);}
 public void putBoolean(String k,boolean v){put(k,v);} public boolean getBoolean(String k){return Boolean.TRUE.equals(get(k));}
}''',
            'android/os/IBinder.java': 'package android.os; public interface IBinder {}',
            'android/os/RemoteException.java': 'package android.os; public class RemoteException extends Exception {}',
            'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
            'android/os/Handler.java': '''package android.os;
public class Handler {
 static final java.util.List<Runnable> timers=new java.util.ArrayList<>();
 public Handler(Looper l){} public void post(Runnable r){r.run();}
 public void postDelayed(Runnable r,long ms){timers.add(r);}
 public void removeCallbacks(Runnable r){timers.removeIf(t->t==r);}
 public void removeCallbacksAndMessages(Object token){timers.clear();}
 public static void expire(){var pending=new java.util.ArrayList<>(timers);timers.clear();for(var r:pending)r.run();}
}''',
            'android/content/ComponentName.java': 'package android.content; public class ComponentName {}',
            'android/content/Intent.java': '''package android.content;
public class Intent {public Intent setClassName(String p,String c){if(!p.equals("org.hecker.vpn")||!c.endsWith(".VpnService"))throw new AssertionError();return this;}}''',
            'android/content/ServiceConnection.java': '''package android.content;
import android.os.IBinder;
public interface ServiceConnection {
 void onServiceConnected(ComponentName n,IBinder b);void onServiceDisconnected(ComponentName n);
 default void onNullBinding(ComponentName n){} default void onBindingDied(ComponentName n){}
}''',
            'android/content/Context.java': '''package android.content;
public class Context {
 public static final int BIND_AUTO_CREATE=1;public int binds,unbinds;public boolean available,denied;
 public ServiceConnection connection;
 public Context getApplicationContext(){return this;} public String getString(int id){return "message-"+id;}
 public boolean bindService(Intent i,ServiceConnection c,int flags){binds++;if(denied)throw new SecurityException();connection=c;return available;}
 public void unbindService(ServiceConnection c){unbinds++;}
}''',
            'org/hecker/vpn/IHeckerVpnService.java': '''package org.hecker.vpn;
import android.os.*;
public interface IHeckerVpnService {
 Bundle call(Bundle request) throws RemoteException;
 class Stub {public static IHeckerVpnService asInterface(IBinder binder){return (IHeckerVpnService)binder;}}
}''',
            'org/hecker/suite/R.java': '''package org.hecker.suite;
class R {static class string {static final int vpn_unavailable=1,vpn_controller_timeout=2,vpn_controller_failed=3,vpn_controller_denied=4;}}''',
            'org/hecker/suite/ClientProbe.java': '''package org.hecker.suite;
import android.content.*;import android.os.*;import org.hecker.vpn.IHeckerVpnService;
import java.util.concurrent.*;
public class ClientProbe {
 static class Remote implements IHeckerVpnService,IBinder {
  boolean dead;int calls;
  public Bundle call(Bundle request)throws RemoteException{
   calls++;if(dead)throw new RemoteException();Bundle b=new Bundle();b.putBoolean("account",true);return b;
  }
 }
 static class Result implements java.util.function.Consumer<Bundle> {
  CountDownLatch latch=new CountDownLatch(1);Bundle result;int count;
  public void accept(Bundle b){result=b;count++;latch.countDown();}
  Bundle get()throws Exception{if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("Callback lost");return result;}
 }
 static void check(boolean pass){if(!pass)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Context context=new Context();HeckerVpnClient client=new HeckerVpnClient(context);
  Result absent=new Result();client.call(HeckerVpnClient.request("state"),absent);
  check(absent.get().getString("status").equals("error")&&!absent.result.getBoolean("controller_available"));
  context.available=true;Result first=new Result();client.call(HeckerVpnClient.request("state"),first);
  Remote remote=new Remote();context.connection.onServiceConnected(null,remote);
  check(first.get().getBoolean("account")&&first.result.getBoolean("controller_available")&&context.binds==2);
  Handler.expire();check(first.count==1); // A stale bind timeout cannot poison a connected client.
  context.connection.onServiceDisconnected(null);
  Result resumed=new Result();client.call(HeckerVpnClient.request("state"),resumed);
  context.connection.onServiceConnected(null,remote);check(resumed.get().getBoolean("account"));
  remote.dead=true;Result died=new Result();client.call(HeckerVpnClient.request("account"),died);
  check(!died.get().getBoolean("controller_available"));int count=remote.calls;
  Result rebound=new Result();client.call(HeckerVpnClient.request("state"),rebound);
  Remote next=new Remote();context.connection.onServiceConnected(null,next);
  check(rebound.get().getBoolean("account")&&remote.calls==count&&next.calls==1);
  context.connection.onBindingDied(null);
  Result timedOut=new Result();client.call(HeckerVpnClient.request("state"),timedOut);Handler.expire();
  check(timedOut.get().getString("error").equals("message-2"));
  Result retried=new Result();client.call(HeckerVpnClient.request("state"),retried);
  context.connection.onNullBinding(null);check(!retried.get().getBoolean("controller_available"));
  context.denied=true;Result denied=new Result();client.call(HeckerVpnClient.request("state"),denied);
  check(denied.get().getString("error").equals("message-4"));context.denied=false;
  Result closing=new Result();client.call(HeckerVpnClient.request("state"),closing);client.close();
  context.connection.onServiceConnected(null,next);Handler.expire();check(closing.count==0);
  // A file picker stops the activity. The new client binds and imports after onResume.
  HeckerVpnClient afterPicker=new HeckerVpnClient(context);Result imported=new Result();
  afterPicker.call(HeckerVpnClient.request("import"),imported);context.connection.onServiceConnected(null,next);
  check(imported.get().getBoolean("controller_available"));afterPicker.close();
  System.out.println("HeckerVPN production Binder client: recoverable failures, no duplicate mutation and picker lifecycle PASS");
 }
}''',
        }
        with tempfile.TemporaryDirectory(prefix='hecker-vpn-client-') as temporary:
            folder = Path(temporary)
            for name, content in sources.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(folder / 'classes'),
                            str(ROOT / 'app/src/org/hecker/suite/HeckerVpnClient.java'),
                            *map(str, folder.rglob('*.java'))], check=True)
            subprocess.run(['java', '-cp', str(folder / 'classes'), 'org.hecker.suite.ClientProbe'], check=True, timeout=30)


if __name__ == '__main__':
    unittest.main()
