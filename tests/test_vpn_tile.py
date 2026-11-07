"""Exercise the production tile with delayed Binder replies and controller events."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

STUBS = {
    'android/os/Bundle.java': '''package android.os;
import java.util.HashMap;
public class Bundle {
 private final HashMap<String,Object> values=new HashMap<>();
 public Bundle(){} public Bundle(Bundle other){putAll(other);}
 public void putAll(Bundle other){values.putAll(other.values);}
 public boolean containsKey(String key){return values.containsKey(key);}
 public void putString(String key,String value){values.put(key,value);}
 public String getString(String key,String fallback){Object value=values.get(key);return value instanceof String?(String)value:fallback;}
 public void putBoolean(String key,boolean value){values.put(key,value);}
 public boolean getBoolean(String key){return getBoolean(key,false);}
 public boolean getBoolean(String key,boolean fallback){Object value=values.get(key);return value instanceof Boolean?(Boolean)value:fallback;}
 public void putLong(String key,long value){values.put(key,value);}
 public long getLong(String key){Object value=values.get(key);return value instanceof Long?(Long)value:0;}
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
 public static final int RECEIVER_EXPORTED=2;
 public BroadcastReceiver receiver; public String receiverPermission; public int receiverFlags;
 public void registerReceiver(BroadcastReceiver receiver,IntentFilter filter,String permission,Object handler,int flags){this.receiver=receiver;receiverPermission=permission;receiverFlags=flags;}
 public void unregisterReceiver(BroadcastReceiver receiver){if(this.receiver!=receiver)throw new AssertionError("receiver leak");this.receiver=null;}
 public String getString(int id){return org.hecker.suite.R.value(id);}
}''',
    'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context context,Intent intent);}',
    'android/content/IntentFilter.java': 'package android.content; public class IntentFilter {public IntentFilter(String action){}}',
    'android/content/Intent.java': '''package android.content;
import android.os.Bundle;
public class Intent {
 private String action; private Bundle extras;
 public Intent(String action){this.action=action;} public Intent(Context context,Class<?> type){}
 public String getAction(){return action;} public Bundle getExtras(){return extras;}
 public Intent putExtras(Bundle extras){this.extras=new Bundle(extras);return this;}
}''',
    'android/app/PendingIntent.java': '''package android.app;
public class PendingIntent {
 public static final int FLAG_IMMUTABLE=1,FLAG_UPDATE_CURRENT=2;
 public static PendingIntent getActivity(android.content.Context context,int id,android.content.Intent intent,int flags){return new PendingIntent();}
}''',
    'android/service/quicksettings/Tile.java': '''package android.service.quicksettings;
public class Tile {
 public static final int STATE_UNAVAILABLE=0,STATE_INACTIVE=1,STATE_ACTIVE=2;
 public int state=STATE_INACTIVE,updates; public String subtitle,description;
 public void setLabel(String label){} public void setSubtitle(String subtitle){this.subtitle=subtitle;}
 public void setStateDescription(String description){this.description=description;}
 public int getState(){return state;} public void setState(int state){this.state=state;}
 public void updateTile(){updates++;}
}''',
    'android/service/quicksettings/TileService.java': '''package android.service.quicksettings;
public class TileService extends android.content.Context {
 public final Tile tile=new Tile(); public int opened;
 public Tile getQsTile(){return tile;} public void onStartListening(){} public void onStopListening(){}
 public void onDestroy(){} public void onClick(){} public void unlockAndRun(Runnable action){action.run();}
 public void startActivityAndCollapse(android.app.PendingIntent intent){opened++;}
}''',
    'org/hecker/suite/HeckerVpnClient.java': '''package org.hecker.suite;
import android.os.Bundle; import java.util.*; import java.util.function.Consumer;
final class HeckerVpnClient {
 static final List<HeckerVpnClient> clients=new ArrayList<>();
 static final class Call {final Bundle request;final Consumer<Bundle> callback;Call(Bundle r,Consumer<Bundle> c){request=r;callback=c;}}
 final List<Call> calls=new ArrayList<>(); boolean closed;
 HeckerVpnClient(android.content.Context context){clients.add(this);}
 void call(Bundle request,Consumer<Bundle> callback){if(closed)throw new AssertionError("closed client reused");calls.add(new Call(new Bundle(request),callback));}
 void close(){closed=true;}
 static Bundle request(String op){Bundle request=new Bundle();request.putString("op",op);return request;}
}''',
}

PROBE = '''package org.hecker.suite;
import android.os.Bundle; import android.content.Intent; import android.service.quicksettings.Tile;
import org.hecker.vpn.VpnTileState;
public final class VpnTileProbe {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static Bundle state(String status,boolean enabled,boolean configured,long version){
  Bundle state=new Bundle();state.putString("status",status);state.putString("error","");
  state.putBoolean("desired",enabled);state.putBoolean("configured",configured);
  state.putBoolean("controller_available",true);state.putLong(VpnTileState.VERSION,version);return state;
 }
 static HeckerVpnClient client(){return HeckerVpnClient.clients.get(HeckerVpnClient.clients.size()-1);}
 static void event(HeckerVpnTileService tile,Bundle state){tile.receiver.onReceive(tile,new Intent(VpnTileState.ACTION_CHANGED).putExtras(state));}
 public static void main(String[] args){
  HeckerVpnTileService tile=new HeckerVpnTileService();tile.onStartListening();HeckerVpnClient client=client();
  check(VpnTileState.CONTROL_PERMISSION.equals(tile.receiverPermission)&&tile.receiverFlags==2,"state receiver must require the signature permission");
  tile.onClick();
  check(tile.tile.state==Tile.STATE_ACTIVE&&tile.tile.subtitle.equals(R.value(R.string.vpn_connecting)),"enable tap must paint before any Binder response");
  check(client.calls.size()==2&&client.calls.get(1).request.getString("op","").equals("set_enabled")&&client.calls.get(1).request.getBoolean("enabled"),"tap must submit one explicit enable without a state preflight");
  client.calls.get(0).callback.accept(state("disconnected",false,false,1));
  check(tile.tile.state==Tile.STATE_ACTIVE,"late initial state must not undo the tap");
  event(tile,state("waiting_peer",true,true,2));client.calls.get(1).callback.accept(state("waiting_peer",true,true,2));
  check(tile.tile.state==Tile.STATE_ACTIVE&&tile.tile.subtitle.equals(R.value(R.string.vpn_waiting_peer)),"enabled routing must retain the honest waiting-for-peer subtitle");
  event(tile,state("active",true,true,3));
  check(tile.tile.subtitle.equals(R.value(R.string.vpn_active))&&client.calls.size()==2,"handshake must update an open QS panel without polling or another tap");
  event(tile,state("disconnected",false,false,1));
  check(tile.tile.state==Tile.STATE_ACTIVE,"old broadcasts must not roll back newer controller state");
  tile.onClick();
  check(tile.tile.state==Tile.STATE_INACTIVE&&tile.tile.subtitle.equals(R.value(R.string.vpn_disconnecting)),"disable tap must paint before cleanup finishes");
  event(tile,state("active",true,true,4));
  check(tile.tile.state==Tile.STATE_INACTIVE,"an older in-flight enable must not override a pending disable");
  client.calls.get(2).callback.accept(state("disconnected",false,false,5));
  check(tile.tile.state==Tile.STATE_INACTIVE&&tile.tile.subtitle.equals(R.value(R.string.vpn_disconnected)),"cleanup completion must settle the subtitle");
  tile.onStopListening();check(tile.receiver==null&&client.closed,"hidden tile must release its receiver and idle Binder client");

  tile.onStartListening();client=client();client.calls.get(0).callback.accept(state("disconnected",false,false,6));
  tile.onClick();tile.onClick();
  check(client.calls.get(1).request.getBoolean("enabled")&&!client.calls.get(2).request.getBoolean("enabled"),"rapid taps must submit explicit opposite targets");
  client.calls.get(1).callback.accept(state("active",true,true,7));
  check(tile.tile.state==Tile.STATE_INACTIVE&&tile.tile.subtitle.equals(R.value(R.string.vpn_disconnecting)),"late first mutation result must not undo the second tap");
  tile.onStopListening();check(!client.closed,"closing QS must not cancel an accepted mutation during binding/setup");
  client.calls.get(2).callback.accept(state("disconnected",false,false,8));
  check(client.closed&&tile.opened==0,"last completion must release the hidden tile without opening an activity");

  tile.onStartListening();client=client();client.calls.get(0).callback.accept(state("active",true,true,9));
  tile.onClick();Bundle busy=new Bundle();busy.putString("error","busy");busy.putBoolean("controller_available",true);
  client.calls.get(1).callback.accept(busy);
  check(tile.tile.state==Tile.STATE_ACTIVE&&tile.tile.subtitle.equals(R.value(R.string.vpn_error)),"partial timeout must not pretend an owned tunnel stopped");
  tile.onClick();check(!client.calls.get(2).request.getBoolean("enabled"),"retry after uncertain cleanup must still request disable");
  Bundle failed=state("error",false,false,10);failed.putBoolean("cleanup_required",true);failed.putString("error","cleanup failed");
  client.calls.get(2).callback.accept(failed);
  check(tile.tile.state==Tile.STATE_ACTIVE&&tile.tile.subtitle.equals(R.value(R.string.vpn_error)),"failed cleanup must show enabled ownership and attention");
  check(tile.tile.description.equals(tile.tile.subtitle),"accessibility must describe the real transition/connection status");
  tile.onDestroy();check(client.closed&&tile.receiver==null,"destroy must release the receiver and binding");
  System.out.println("VPN tile immediate feedback, ordered state events, rapid taps, cleanup failure and lifecycle: PASS");
 }
}'''


class VpnTileTests(unittest.TestCase):
    def test_production_tile_transitions_and_lifecycle(self):
        with tempfile.TemporaryDirectory(prefix='hecker-vpn-tile-') as temporary:
            out = Path(temporary)
            for relative, source in STUBS.items():
                path = out / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            values = {}
            for path in sorted((ROOT / 'app/res/values').glob('vpn*.xml')):
                for node in ET.parse(path).getroot().findall('string'):
                    values[node.get('name')] = ''.join(node.itertext())
            fields = '\n'.join(f'public static final int {name}={i};' for i, name in enumerate(values, 1))
            cases = '\n'.join(f'case {i}:return {json.dumps(value, ensure_ascii=True)};' for i, value in enumerate(values.values(), 1))
            (out / 'org/hecker/suite/R.java').write_text('package org.hecker.suite;public final class R {public static final class string {'+fields+'} public static String value(int id){switch(id){'+cases+'default:throw new AssertionError(id);}}}')
            activity = (ROOT / 'app/src/org/hecker/suite/HeckerVpnActivity.java').read_text()
            method = activity[activity.index('    static int statusLabel('):activity.index('    private Button action(')]
            (out / 'org/hecker/suite/HeckerVpnActivity.java').write_text('package org.hecker.suite;public final class HeckerVpnActivity {'+method+'}')
            (out / 'org/hecker/suite/VpnTileProbe.java').write_text(PROBE)
            production = Path(os.environ.get('HECKER_VPN_TILE_SOURCE', ROOT / 'app/src/org/hecker/suite/HeckerVpnTileService.java'))
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(out / 'classes'),
                            *map(str, out.rglob('*.java')), str(production),
                            str(ROOT / 'vpn/common/org/hecker/vpn/VpnTileState.java')], check=True)
            subprocess.run(['java', '-cp', str(out / 'classes'), 'org.hecker.suite.VpnTileProbe'], check=True)

    def test_controller_can_send_permission_protected_events(self):
        ns = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT / 'vpn/AndroidManifest.xml').getroot()
        permission = 'org.hecker.vpn.permission.CONTROL'
        self.assertTrue(any(node.get(ns + 'name') == permission and node.get(ns + 'protectionLevel') == 'signature'
                            for node in manifest.findall('permission')))
        self.assertTrue(any(node.get(ns + 'name') == permission for node in manifest.findall('uses-permission')))


if __name__ == '__main__':
    unittest.main()
