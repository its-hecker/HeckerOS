# SPDX-License-Identifier: Apache-2.0
"""Exercise the real glow host against a small window/view lifecycle facade."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class OsloGlowTests(unittest.TestCase):
    def test_native_glow_moves_between_shade_and_non_touchable_window_without_leaks(self):
        files = {
            'android/content/Context.java': '''package android.content;
public class Context { public Object service; public String getPackageName(){return "com.google.oslo";}
 public Context createWindowContext(int type,Object options){return this;}
 public <T> T getSystemService(Class<T> type){return type.cast(service);} }''',
            'android/graphics/PixelFormat.java': 'package android.graphics; public class PixelFormat { public static final int TRANSLUCENT=-3; }',
            'android/os/Looper.java': 'package android.os; public class Looper {static final Looper MAIN=new Looper(); public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return MAIN;}}',
            'android/os/Handler.java': 'package android.os; public class Handler {public Handler(Looper l){} public void post(Runnable r){r.run();} public void removeCallbacksAndMessages(Object o){}}',
            'android/util/Log.java': 'package android.util; public class Log {public static int w(String t,String m,Throwable e){return 0;}}',
            'android/view/Gravity.java': 'package android.view; public class Gravity {public static final int TOP=48;}',
            'android/view/View.java': '''package android.view; import android.content.Context;
public class View { public Object parent; final Context context; public View(Context c){context=c;}
 public Context getContext(){return context;} public Object getParent(){return parent;} }''',
            'android/view/ViewGroup.java': '''package android.view; import android.content.Context; import java.util.*;
public class ViewGroup extends View {public final List<View> children=new ArrayList<>(); public ViewGroup(Context c){super(c);}
 public static class LayoutParams{public LayoutParams(int w,int h){}}
 public void addView(View v){addView(v,new LayoutParams(-1,-1));}
 public void addView(View v,LayoutParams p){if(v.parent!=null)throw new AssertionError("two parents");children.add(v);v.parent=this;}
 public void removeView(View v){children.remove(v);v.parent=null;}
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
public class GlowTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static class Windows implements WindowManager {int adds,removes;boolean fail;View current;LayoutParams last;
 public void addView(View v,LayoutParams p){if(fail){fail=false;throw new IllegalStateException("unavailable");}check(current==null&&v.parent==null);adds++;current=v;last=p;v.parent=new Object();}
 public void removeViewImmediate(View v){check(current==v);removes++;current=null;v.parent=null;} }
 static class Lock implements KeyguardStateController {boolean showing=true;Set<Callback> callbacks=new HashSet<>();
 public boolean isShowing(){return showing;}public void addCallback(Callback c){callbacks.add(c);}public void removeCallback(Callback c){callbacks.remove(c);}
 void show(boolean b){showing=b;for(var c:new ArrayList<>(callbacks))c.onKeyguardShowingChanged();}}
 public static void main(String[] args){
 Context context=new Context();Windows windows=new Windows();context.service=windows;Lock lock=new Lock();ViewGroup root=new ViewGroup(context);OsloOverlay plugin=new OsloOverlay();
 View host=HeckerOsloOverlayHost.rootFor(plugin,context,root,lock);check(host!=root&&host.getParent()==root);check(((ViewGroup)host).children.size()==1);
 int[] forwarded={0};var callback=HeckerOsloOverlayHost.callbackFor(plugin,()->forwarded[0]++);
 lock.show(false);check(windows.current==null);plugin.held=true;callback.onHoldStatusBarOpenChange();
 check(!HeckerOsloOverlayHost.holdsNativeShade(plugin));
 check(windows.current==host&&windows.last.type==2041&&windows.last.trusted);check((windows.last.flags&24)==24);check(root.children.isEmpty());
 callback.onHoldStatusBarOpenChange();check(windows.adds==1);lock.show(true);check(windows.current==null&&host.getParent()==root);
 check(HeckerOsloOverlayHost.holdsNativeShade(plugin));
 lock.show(false);check(windows.adds==2);plugin.held=false;callback.onHoldStatusBarOpenChange();check(windows.current==null&&host.getParent()==root);
 windows.fail=true;plugin.held=true;callback.onHoldStatusBarOpenChange();check(windows.current==null&&host.getParent()==root);
 callback.onHoldStatusBarOpenChange();check(windows.current==host);int before=forwarded[0];HeckerOsloOverlayHost.disconnect(plugin);
 check(windows.current==null&&root.children.isEmpty()&&lock.callbacks.isEmpty());callback.onHoldStatusBarOpenChange();check(forwarded[0]==before);
 OverlayPlugin other=()->true;check(HeckerOsloOverlayHost.rootFor(other,context,root,lock)==root);
 HeckerOsloOverlayHost.disconnect(plugin);check(windows.adds==windows.removes);
 System.out.println("Native Oslo glow: locked/AOD host, unlocked non-touchable window, repeated callbacks, failure fallback and disconnect cleanup PASS");
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
                            *paths, str(ROOT / 'framework/glow/HeckerOsloOverlayHost.java')], check=True)
            subprocess.run(['java', '-cp', str(root / 'classes'), 'GlowTest'], check=True)
