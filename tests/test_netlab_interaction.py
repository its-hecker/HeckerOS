# SPDX-License-Identifier: Apache-2.0
"""Run NetLab's real gesture callbacks with Android's pre-drag cancellation order.

AbsSeekBar inside a ScrollView can receive DOWN then CANCEL without either
tracking callback. The harness supplies that order rather than treating CANCEL
as an unconditional onStopTrackingTouch. No network/controller code is run.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


HARNESS = r'''
import java.util.*;
import org.hecker.netlab.NetLabSliderPolicy;
class MotionEvent {
    static final int ACTION_DOWN=0, ACTION_UP=1, ACTION_MOVE=2, ACTION_CANCEL=3;
    final int action;
    MotionEvent(int value){action=value;}
    int getActionMasked(){return action;}
}
class Parent {
    boolean disallowed;
    void requestDisallowInterceptTouchEvent(boolean value){disallowed=value;}
}
class SeekBar {
    interface Touch {boolean run(SeekBar view,MotionEvent event);}
    interface OnSeekBarChangeListener {
        void onStartTrackingTouch(SeekBar bar);
        void onProgressChanged(SeekBar bar,int value,boolean user);
        void onStopTrackingTouch(SeekBar bar);
    }
    int progress=12;
    boolean dragging;
    final Parent parent=new Parent();
    Touch touch;
    OnSeekBarChangeListener listener;
    void setOnTouchListener(Touch value){touch=value;}
    void setOnSeekBarChangeListener(OnSeekBarChangeListener value){listener=value;}
    Parent getParent(){return parent;}
    int getProgress(){return progress;}
    void setProgress(int value){progress=value;listener.onProgressChanged(this,value,false);}
    void setStateDescription(String value){}
    void event(int value){
        touch.run(this,new MotionEvent(value));
        // Relevant AbsSeekBar ordering: pre-drag CANCEL has no tracking callback.
        if(value==MotionEvent.ACTION_MOVE){
            if(!dragging){dragging=true;listener.onStartTrackingTouch(this);}
        }else if(value==MotionEvent.ACTION_UP){
            if(!dragging)listener.onStartTrackingTouch(this);
            listener.onStopTrackingTouch(this);dragging=false;
        }else if(value==MotionEvent.ACTION_CANCEL&&dragging){
            listener.onStopTrackingTouch(this);dragging=false;
        }
    }
    void change(int value){progress=value;listener.onProgressChanged(this,value,true);}
}
class Handler {
    final List<Runnable> callbacks=new ArrayList<>();
    void post(Runnable value){postDelayed(value,0);}
    void postDelayed(Runnable value,long delay){callbacks.add(value);}
    void removeCallbacks(Runnable value){callbacks.removeIf(item->item==value);}
    void drain(){while(!callbacks.isEmpty())callbacks.remove(0).run();}
}
class Bundle {
    final String id;
    boolean jailed;
    final Map<String,Object> values=new HashMap<>();
    Bundle(String value){id=value;}
    String getString(String key){return getString(key,null);}
    String getString(String key,String fallback){return key.equals("id")?id:(String)values.getOrDefault(key,fallback);}
    boolean getBoolean(String key){return Boolean.TRUE.equals(values.get(key));}
    int getInt(String key){return getInt(key,0);}
    int getInt(String key,int fallback){return (Integer)values.getOrDefault(key,fallback);}
    public String toString(){return "Bundle[mParcelledData.dataSize=100]";}
}
class LinearLayout {
    static final int VERTICAL=1;
    int rebuilds;
    LinearLayout(){}
    LinearLayout(Object context){}
    void setOrientation(int value){}
    void removeAllViews(){rebuilds++;}
    void addView(Object view){}
}
class NetLabStyle {
    static LinearLayout card(Object context){return new LinearLayout();}
    static Object text(Object context,String label,int size,boolean bold){return new Object();}
}
class Preferences {boolean trusted(String id){return false;}boolean trusted(Bundle peer){return false;}}
class R {static class string {
    static final int netlab_device_changed=1, netlab_ui_paused=2,
        netlab_ui_find_devices=3,netlab_no_devices=4,netlab_no_jailed=5,
        netlab_ui_scan_hint=6,netlab_page_count=7,netlab_show_more=8;
}}
class BaseActivity {
    Runnable delivered=()->{};
    public boolean dispatchTouchEvent(MotionEvent event){delivered.run();return true;}
}
public class NetLabInteractionProbe extends BaseActivity {
    final Handler main=new Handler();
    final List<Bundle> devices=new ArrayList<>();
    Runnable pendingRateChoice;
    boolean started=true, adjustingRate, touchingPage, acting;
    String scope="wifi",query="",gateway="",expandedDevice,lastList="";
    int tab,filter,limit=50;
    final LinearLayout list=new LinearLayout();
    final Preferences preferences=new Preferences();
    final Bundle root=new Bundle("root");
    int actions,headers,rootUpdates;
    Bundle actedOn;
    final Runnable afterTouch=this::finishTouch;
    String getString(int value,Object...args){return "text";}
    String rate(int value){return "rate";}
    static int speedRate(int value){return NetLabSliderPolicy.rate(value);}
    void updateHeader(){if(!touchingPage)headers++;}
    void updateRoot(){rootUpdates++;}
    Bundle rootState(){return root;}
    String label(Bundle peer){return peer.getString("name",peer.id);}
    String displayName(Bundle peer){return label(peer);}
    String addresses(Bundle peer){return peer.getString("ip","");}
    String expiryLabel(Bundle peer){return peer.getString("until_label","—");}
    boolean router(Bundle peer){return peer.getBoolean("router");}
    Object peerCard(Bundle peer){return new Object();}
    Object button(int title,java.util.function.Consumer<Object> click){return new Object();}
    void toast(int value){}
    boolean jailed(Bundle peer){return peer.jailed;}
    void confirmRestore(){actions++;}
    void controlAll(boolean limit,int rate){actions++;}
    void pausePeer(Bundle peer){actions++;actedOn=peer;}
    void restorePeer(Bundle peer){actions++;actedOn=peer;}
    void controlWifi(List<Bundle> peers,boolean limit,int rate){actions++;actedOn=peers.get(0);}
    // PRODUCTION_METHODS
    SeekBar slider(){SeekBar bar=new SeekBar();bindSpeed(bar,null,true,12);return bar;}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        NetLabInteractionProbe app=new NetLabInteractionProbe();
        SeekBar bar=app.slider();
        switch(args[0]){
            case "cancel_before_tracking":
                bar.event(MotionEvent.ACTION_DOWN);
                check(app.adjustingRate,"DOWN should hold the slider");
                bar.event(MotionEvent.ACTION_CANCEL);app.main.drain();
                check(!app.adjustingRate,"CANCEL before tracking leaves NetLab permanently frozen");
                check(app.actions==0,"A cancelled touch applied a network action");
                check(!bar.parent.disallowed,"CANCEL did not release the scrolling parent");break;
            case "drag":
                bar.event(MotionEvent.ACTION_DOWN);
                check(bar.parent.disallowed,"Slider DOWN must claim the gesture before ScrollView interception");
                bar.event(MotionEvent.ACTION_MOVE);bar.change(5);
                check(app.actions==0,"Dragging applied a limit before release");
                bar.event(MotionEvent.ACTION_UP);app.main.drain();
                check(app.actions==1,"A completed drag must open exactly one confirmation");
                check(!app.adjustingRate&&!bar.parent.disallowed,"UP did not release the interaction");
                check(bar.progress==12,"An unconfirmed proposed rate replaced the confirmed rate");break;
            case "drag_cancel":
                bar.event(MotionEvent.ACTION_DOWN);bar.event(MotionEvent.ACTION_MOVE);bar.change(0);
                bar.event(MotionEvent.ACTION_CANCEL);app.main.drain();
                check(app.actions==0&&!app.adjustingRate&&bar.progress==12,"Cancelled drag committed or stuck");break;
            case "unchanged":
                bar.event(MotionEvent.ACTION_DOWN);bar.event(MotionEvent.ACTION_UP);app.main.drain();
                check(app.actions==0&&!app.adjustingRate,"An unchanged tap committed or stuck");break;
            case "keyboard":
                bar.change(6);bar.change(5);bar.change(4);app.main.drain();
                check(app.actions==1&&!app.adjustingRate,"Keyboard/TalkBack changes should coalesce");break;
            case "scope_changed":
                bar.event(MotionEvent.ACTION_DOWN);bar.event(MotionEvent.ACTION_MOVE);bar.change(0);
                app.scope="different_wifi";bar.event(MotionEvent.ACTION_UP);app.main.drain();
                check(app.actions==0&&!app.adjustingRate,"Gesture crossed network scopes");break;
            case "poll_during_button_touch":
                app.delivered=()->check(app.touchingPage,"UI was not held before delivering DOWN");
                app.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN));
                app.delivered=()->{};
                app.updateHeader();app.renderDevices();
                check(app.list.rebuilds==0&&app.headers==0,"Polling replaced controls before their click");
                app.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP));app.main.drain();
                check(!app.touchingPage&&app.list.rebuilds==1&&app.headers==1,"Release did not render latest state");break;
            case "activity_cancel":
                app.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN));
                app.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL));app.main.drain();
                check(!app.touchingPage&&app.list.rebuilds==1,"Activity cancellation left refreshes held");break;
            case "stopped":
                bar.event(MotionEvent.ACTION_DOWN);bar.event(MotionEvent.ACTION_MOVE);bar.change(0);
                bar.event(MotionEvent.ACTION_UP);app.started=false;app.cancelRateChoice();app.main.drain();
                check(app.actions==0&&!app.adjustingRate,"Stopped Activity applied a pending rate");break;
            case "latest_peer":
                Bundle old=new Bundle("peer"),current=new Bundle("peer");current.jailed=true;
                app.devices.add(old);bar=new SeekBar();bind(app,bar,old);
                bar.event(MotionEvent.ACTION_DOWN);bar.event(MotionEvent.ACTION_MOVE);bar.change(0);
                app.devices.clear();app.devices.add(current);
                bar.event(MotionEvent.ACTION_UP);app.main.drain();
                check(app.actedOn==current,"Gesture used a stale peer snapshot");break;
            case "unchanged_poll":
                Bundle first=new Bundle("peer");first.values.put("state","observed");
                app.devices.add(first);app.renderDevices();app.renderDevices();
                int initial=app.list.rebuilds;
                Bundle fresh=new Bundle("peer");fresh.values.put("state","observed");
                fresh.values.put("forwarded_down",123456);app.devices.clear();app.devices.add(fresh);
                app.renderDevices();
                check(app.list.rebuilds==initial,"Counter-only polling unnecessarily replaced the cards");break;
            case "parcel_size_collision":
                Bundle before=new Bundle("peer");before.values.put("state","observed");
                app.devices.add(before);app.renderDevices();app.renderDevices();
                int previous=app.list.rebuilds;
                Bundle after=new Bundle("peer");after.values.put("state","blocking");
                check(before.toString().equals(after.toString()),"Harness must model equal-sized parcels");
                app.devices.clear();app.devices.add(after);app.renderDevices();
                check(app.list.rebuilds==previous+1,"Changed state hidden by Bundle's parcel description");break;
            case "peer_identity_changed":
                Bundle oldIdentity=new Bundle("peer"),newIdentity=new Bundle("peer");
                oldIdentity.values.put("mac","02:00:00:00:00:01");
                newIdentity.values.put("mac","02:00:00:00:00:02");
                app.devices.add(oldIdentity);bar=new SeekBar();bind(app,bar,oldIdentity);
                bar.event(MotionEvent.ACTION_DOWN);bar.event(MotionEvent.ACTION_MOVE);bar.change(0);
                app.devices.clear();app.devices.add(newIdentity);
                bar.event(MotionEvent.ACTION_UP);app.main.drain();
                check(app.actions==0&&!app.adjustingRate,"Gesture transferred to a different device at the same IP");break;
            default:throw new AssertionError(args[0]);
        }
    }
    static void bind(NetLabInteractionProbe app,SeekBar bar,Bundle peer){app.bindSpeed(bar,peer,false,12);}
}
'''


class NetLabInteractionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = (ROOT/'app/src/org/hecker/suite/NetworkLabActivity.java').read_text()
        methods = [method(source, 'private void bindSpeed('),
                   method(source, 'private void cancelRateChoice('),
                   method(source, 'private void renderDevices(')]
        # The baseline can run the cancellation reproducer before the fix exists.
        for signature, fallback in [
            ('public boolean dispatchTouchEvent(', ''),
            ('private void finishTouch(', 'private void finishTouch(){}'),
            ('private Bundle currentPeer(', 'private Bundle currentPeer(Bundle peer){return peer;}'),
            ('private String devicePresentation(', 'private String devicePresentation(){return devices.toString();}'),
            ('private static void presentationValues(', ''),
        ]:
            methods.append(method(source, signature) if signature in source else fallback)
        cls.temp = tempfile.TemporaryDirectory()
        path = Path(cls.temp.name)
        (path/'NetLabInteractionProbe.java').write_text(
            HARNESS.replace('// PRODUCTION_METHODS', '\n'.join(methods)))
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                        '-d', str(path), str(path/'NetLabInteractionProbe.java'),
                        str(ROOT/'netlab/common/org/hecker/netlab/NetLabSliderPolicy.java')], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_real_gesture_callbacks(self):
        for scenario in ('cancel_before_tracking', 'drag', 'drag_cancel', 'unchanged',
                         'keyboard', 'scope_changed', 'poll_during_button_touch',
                         'activity_cancel', 'stopped', 'latest_peer', 'unchanged_poll',
                         'parcel_size_collision', 'peer_identity_changed'):
            with self.subTest(scenario=scenario):
                result = subprocess.run(['java', '-cp', self.temp.name,
                                         'NetLabInteractionProbe', scenario],
                                        text=True, capture_output=True)
                self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main()
