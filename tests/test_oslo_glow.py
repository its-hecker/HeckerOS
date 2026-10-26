# SPDX-License-Identifier: Apache-2.0
"""Exercise the real glow host against a small window/view lifecycle facade."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class OsloGlowTests(unittest.TestCase):
    def test_native_glow_keeps_one_attached_window_through_gesture_callbacks(self):
        files = {
            'android/content/Context.java': '''package android.content;
public class Context { public Object service; public String getPackageName(){return "com.google.oslo";}
 public Context createWindowContext(int type,Object options){return this;}
 public <T> T getSystemService(Class<T> type){return type.cast(service);} }''',
            'android/view/Display.java': 'package android.view;public class Display {public static final int STATE_UNKNOWN=0,STATE_OFF=1,STATE_ON=2,STATE_DOZE=3,STATE_DOZE_SUSPEND=4;}',
            'android/opengl/GLSurfaceView.java': 'package android.opengl;public class GLSurfaceView extends android.view.View {public int renders;public GLSurfaceView(android.content.Context c){super(c);}public void requestRender(){renders++;}}',
            'com/android/systemui/plugins/statusbar/StatusBarStateController.java': r'''package com.android.systemui.plugins.statusbar;
public interface StatusBarStateController {boolean isDozing();boolean isPulsing();void addCallback(StateListener l);void removeCallback(StateListener l);
interface StateListener {default void onDozingChanged(boolean v){} default void onPulsingChanged(boolean v){}}}''',
            'com/google/oslo/ui/FakeGlow.java': r'''package com.google.oslo.ui;
public class FakeGlow extends android.view.View implements com.android.systemui.plugins.statusbar.StatusBarStateController.StateListener {
 public boolean dozing,pulsing;public int dozeCalls,pulseCalls;
 public FakeGlow(android.content.Context c){super(c);}
 public void onDozingChanged(boolean v){dozing=v;dozeCalls++;}public void onPulsingChanged(boolean v){pulsing=v;pulseCalls++;}}
''',
            'com/google/oslo/ui/glow/GlowFeedbackView.java': r'''package com.google.oslo.ui.glow;
public class GlowFeedbackView extends com.google.oslo.ui.FakeGlow {
 public interface Callback {void onAnimationStart();void onAnimationEnd();}
 private int mCurrAnim;public int starts;
 private Callback mCallback=new Callback(){public void onAnimationStart(){starts++;}public void onAnimationEnd(){}};
 public GlowFeedbackView(android.content.Context c){super(c);}
 public void setCallback(Callback c){mCallback=c;}
 public void animate(int kind){mCallback.onAnimationStart();mCurrAnim=kind;}
}''',
            'android/graphics/PixelFormat.java': 'package android.graphics; public class PixelFormat { public static final int TRANSLUCENT=-3; }',
            'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long now=10000;public static long elapsedRealtime(){return now;}}',
            'android/os/Looper.java': 'package android.os; public class Looper {static final Looper MAIN=new Looper(); public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return MAIN;}}',
            'android/os/Handler.java': 'package android.os; public class Handler {public Handler(Looper l){} public void post(Runnable r){r.run();} public static final java.util.ArrayDeque<Runnable> delayed=new java.util.ArrayDeque<>();public void postDelayed(Runnable r,long ms){delayed.add(r);}public static void drain(){while(!delayed.isEmpty())delayed.remove().run();} public void removeCallbacksAndMessages(Object o){}}',
            'android/util/Log.java': 'package android.util; public class Log {public static int w(String t,String m,Throwable e){return 0;}}',
            'android/view/Gravity.java': 'package android.view; public class Gravity {public static final int TOP=48;}',
            'android/view/View.java': '''package android.view; import android.content.Context;
public class View { public Object parent; final Context context; public View(Context c){context=c;}
 public Context getContext(){return context;} public Object getParent(){return parent;}
 public static final int VISIBLE=0;public int getVisibility(){return VISIBLE;}
 public static final java.util.ArrayDeque<Runnable> posted=new java.util.ArrayDeque<>();public boolean post(Runnable r){posted.add(r);return true;}public static void drain(){while(!posted.isEmpty())posted.remove().run();}
 public boolean attached;public boolean isAttachedToWindow(){return attached;}
 public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public final java.util.List<OnAttachStateChangeListener> listeners=new java.util.ArrayList<>();
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}
 public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
 public void attach(){attached=true;for(var l:new java.util.ArrayList<>(listeners))l.onViewAttachedToWindow(this);}
 }''',
            'android/view/ViewGroup.java': '''package android.view; import android.content.Context; import java.util.*;
public class ViewGroup extends View {public final List<View> children=new ArrayList<>(); public ViewGroup(Context c){super(c);}
 public static class LayoutParams{public LayoutParams(int w,int h){}}
 public void addView(View v){addView(v,new LayoutParams(-1,-1));}
 public void addView(View v,LayoutParams p){if(v.parent!=null)throw new AssertionError("two parents");children.add(v);v.parent=this;if(attached)v.attach();onViewAdded(v);}
 public void removeView(View v){children.remove(v);v.parent=null;onViewRemoved(v);}
 public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}
 public void onViewAdded(View v){}public void onViewRemoved(View v){}
 @Override public void attach(){super.attach();for(View v:children)v.attach();}
 public void setClipChildren(boolean b){} public void setClipToPadding(boolean b){} }''',
            'android/widget/FrameLayout.java': 'package android.widget; public class FrameLayout extends android.view.ViewGroup {public FrameLayout(android.content.Context c){super(c);}}',
            'android/view/WindowManager.java': '''package android.view;
public interface WindowManager {void addView(View view,LayoutParams p);void removeViewImmediate(View view);
 class LayoutParams {public static final int FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCHABLE=16,FLAG_LAYOUT_IN_SCREEN=256,LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS=3;
 public boolean trusted;public void setTrustedOverlay(){trusted=true;}
 public int gravity,layoutInDisplayCutoutMode,type,flags; public LayoutParams(int w,int h,int type,int flags,int format){this.type=type;this.flags=flags;}
 public void setFitInsetsTypes(int t){} public void setTitle(String t){} } }''',
            'com/android/systemui/plugins/OverlayPlugin.java': '''package com.android.systemui.plugins;
public interface OverlayPlugin {boolean holdStatusBarOpen(); interface Callback {void onHoldStatusBarOpenChange();}}''',
            'com/android/systemui/statusbar/policy/KeyguardStateController.java': '''package com.android.systemui.statusbar.policy;
public interface KeyguardStateController {boolean isShowing();void addCallback(Callback c);void removeCallback(Callback c);interface Callback {default void onKeyguardShowingChanged(){}}}''',
            'com/google/oslo/OsloOverlay.java': '''package com.google.oslo;
public class OsloOverlay implements com.android.systemui.plugins.OverlayPlugin {public boolean held; public boolean holdStatusBarOpen(){return held;}}''',
            'GlowTest.java': '''import android.view.*;import android.content.Context;import java.util.*;
import com.android.systemui.hecker.HeckerOsloOverlayHost;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.plugins.OverlayPlugin;
import com.google.oslo.OsloOverlay;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
public class GlowTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static class Windows implements WindowManager {int adds,removes;boolean fail;View current;LayoutParams last;
 public void addView(View v,LayoutParams p){if(fail){fail=false;throw new IllegalStateException("unavailable");}check(current==null&&v.parent==null);adds++;current=v;last=p;v.parent=new Object();}
 public void removeViewImmediate(View v){check(current==v);removes++;current=null;v.parent=null;v.attached=false;} }
 static class Lock implements KeyguardStateController {boolean showing=true;Set<Callback> callbacks=new HashSet<>();
 public boolean isShowing(){return showing;}public void addCallback(Callback c){callbacks.add(c);}public void removeCallback(Callback c){callbacks.remove(c);}
 void show(boolean b){showing=b;for(var c:new ArrayList<>(callbacks))c.onKeyguardShowingChanged();}}
 static class Status implements StatusBarStateController {boolean dozing=true,pulsing;Set<StateListener> callbacks=new HashSet<>();
 public boolean isDozing(){return dozing;}public boolean isPulsing(){return pulsing;}
 public void addCallback(StateListener c){callbacks.add(c);}public void removeCallback(StateListener c){callbacks.remove(c);}}
 public static void main(String[] args){
 Context context=new Context();Windows windows=new Windows();context.service=windows;Lock lock=new Lock();Status status=new Status();ViewGroup root=new ViewGroup(context);OsloOverlay plugin=new OsloOverlay();
 View host=HeckerOsloOverlayHost.rootFor(plugin,context,root,lock,status);
 check(host!=root&&windows.current==host&&root.children.isEmpty());
 check(((ViewGroup)host).children.size()==1);
 int[] setup={0};HeckerOsloOverlayHost.whenAttached(plugin,host,()->{check(host.isAttachedToWindow());setup[0]++;});
 check(setup[0]==0);host.attach();check(setup[0]==1&&host.listeners.isEmpty());
 int[] forwarded={0};var callback=HeckerOsloOverlayHost.callbackFor(plugin,()->forwarded[0]++);
 plugin.held=true;callback.onHoldStatusBarOpenChange();
 check(HeckerOsloOverlayHost.holdsNativeShade(plugin));
 check(windows.last.type==2041&&windows.last.trusted&&(windows.last.flags&24)==24);
 com.google.oslo.ui.glow.GlowFeedbackView feedback=new com.google.oslo.ui.glow.GlowFeedbackView(context);
 android.opengl.GLSurfaceView shader=new android.opengl.GLSurfaceView(context);
 ((ViewGroup)host).addView(shader);((ViewGroup)host).addView(feedback);
 check(feedback.dozing&&!feedback.pulsing&&feedback.dozeCalls==1&&feedback.pulseCalls==1);
 int[] pulses={0};HeckerOsloOverlayHost.attachDoze(()->{pulses[0]++;return 1;});
 feedback.animate(5);View.drain();check(pulses[0]==1&&feedback.starts==1);
 feedback.animate(5);View.drain();check(pulses[0]==1); // Debounce repeated callbacks.
 android.os.SystemClock.now+=3000;feedback.animate(14);View.drain();check(pulses[0]==1); // AOD fades never repulse.
 status.pulsing=true;feedback.animate(3);View.drain();check(pulses[0]==1);status.pulsing=false;
 lock.show(false);feedback.animate(3);View.drain();check(pulses[0]==1);lock.show(true);
 HeckerOsloOverlayHost.attachDoze(()->{pulses[0]++;return pulses[0]<4?2:1;});
 feedback.animate(3);View.drain();android.os.Handler.drain();check(pulses[0]==4);
 android.os.SystemClock.now+=3000;HeckerOsloOverlayHost.attachDoze(()->{pulses[0]++;return 0;});
 feedback.animate(5);View.drain();android.os.Handler.drain();check(pulses[0]==5); // Suppression isn't retried.
 int frames=shader.renders;HeckerOsloOverlayHost.onDozeScreenState(Display.STATE_OFF);HeckerOsloOverlayHost.dozeTimeTick();check(shader.renders==frames);
 HeckerOsloOverlayHost.onDozeScreenState(Display.STATE_ON);check(shader.renders>frames);frames=shader.renders;
 HeckerOsloOverlayHost.onDozeScreenState(Display.STATE_DOZE);HeckerOsloOverlayHost.dozeTimeTick();check(shader.renders>frames);
 check(feedback.dozeCalls==1&&feedback.pulseCalls==1); // Ticks redraw, without replaying animation state.
 for(int i=0;i<10;i++){lock.show(i%2==0);plugin.held=i%3==0;callback.onHoldStatusBarOpenChange();check(host.isAttachedToWindow()&&windows.current==host);check(HeckerOsloOverlayHost.holdsNativeShade(plugin)==(lock.showing&&plugin.held));}
 check(windows.adds==1&&windows.removes==0);
 int beforeDisconnect=pulses[0];
 HeckerOsloOverlayHost.disconnect(plugin);feedback.animate(5);View.drain();check(pulses[0]==beforeDisconnect);
 HeckerOsloOverlayHost.detachDoze();check(windows.current==null&&lock.callbacks.isEmpty()&&status.callbacks.isEmpty());
 int before=forwarded[0];callback.onHoldStatusBarOpenChange();check(forwarded[0]==before);
 View pending=HeckerOsloOverlayHost.rootFor(plugin,context,root,lock,status);
 HeckerOsloOverlayHost.whenAttached(plugin,pending,()->{throw new AssertionError("setup after disconnect");});
 HeckerOsloOverlayHost.disconnect(plugin);pending.attach();check(pending.listeners.isEmpty());
 windows.fail=true;check(HeckerOsloOverlayHost.rootFor(plugin,context,root,lock,status)==root);
 OverlayPlugin other=()->true;check(HeckerOsloOverlayHost.rootFor(other,context,root,lock,status)==root);
 HeckerOsloOverlayHost.disconnect(plugin);check(windows.adds==windows.removes);
 System.out.println("Native Oslo glow: setup waits for attachment; no reparent during callbacks; initial doze seed, native pulse/tick GL redraw, locked-only shade hold; pending setup cancelled; failure fallback and disconnect cleanup PASS");
 }}''',
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = []
            for name, source in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
                paths.append(str(path))
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root / 'classes'),
                            *paths, *map(str, (ROOT / 'framework/glow').glob('*.java'))], check=True)
            subprocess.run(['java', '-cp', str(root / 'classes'), 'GlowTest'], check=True)
