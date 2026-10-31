# SPDX-License-Identifier: Apache-2.0
"""Run the production card glow with a small Android lifecycle/Canvas facade."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class AboutPhoneTest(unittest.TestCase):
    def test_shared_glow_and_visible_lifecycle(self):
        sources = {
            'android/util/AttributeSet.java': 'package android.util; public interface AttributeSet {}',
            'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
            'android/os/Handler.java': 'package android.os; public class Handler {public Handler(Looper looper){}}',
            'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long now;public static long elapsedRealtime(){return now;}}',
            'android/os/PowerManager.java': 'package android.os; public class PowerManager {public static final String ACTION_POWER_SAVE_MODE_CHANGED="power";public boolean saving;public boolean isPowerSaveMode(){return saving;}}',
            'android/database/ContentObserver.java': 'package android.database; public class ContentObserver {public ContentObserver(android.os.Handler h){}public void onChange(boolean change){}}',
            'android/content/ContentResolver.java': '''package android.content;
public class ContentResolver {
 public int observed;public android.database.ContentObserver observer;
 public void registerContentObserver(Object uri,boolean descendants,android.database.ContentObserver observer){observed++;this.observer=observer;}
 public void unregisterContentObserver(android.database.ContentObserver observer){observed=0;this.observer=null;}
}''',
            'android/content/Intent.java': 'package android.content; public class Intent {}',
            'android/content/IntentFilter.java': 'package android.content; public class IntentFilter {public IntentFilter(String action){}}',
            'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
            'android/content/Context.java': '''package android.content;
public class Context {
 public static final int RECEIVER_NOT_EXPORTED=4;public final ContentResolver resolver=new ContentResolver();
 public final android.os.PowerManager power=new android.os.PowerManager();public BroadcastReceiver receiver;
 public ContentResolver getContentResolver(){return resolver;}
 public <T>T getSystemService(Class<T> type){return type.cast(power);}
 public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){this.receiver=receiver;return null;}
 public void unregisterReceiver(BroadcastReceiver receiver){this.receiver=null;}
}''',
            'android/provider/Settings.java': '''package android.provider; public class Settings {
 public static class Global {public static final String ANIMATOR_DURATION_SCALE="scale";public static Object getUriFor(String key){return key;}}
 public static class Secure {public static int flow=1;public static Object getUriFor(String key){return key;}public static int getInt(android.content.ContentResolver r,String key,int fallback){return flow;}}
}''',
            'android/animation/ValueAnimator.java': '''package android.animation;
public class ValueAnimator {
 public static final int INFINITE=-1;public static boolean allowed=true;public static final java.util.Set<ValueAnimator> running=new java.util.HashSet<>();
 public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator animator);}
 public final java.util.List<AnimatorUpdateListener> listeners=new java.util.ArrayList<>();
 public static boolean areAnimatorsEnabled(){return allowed;}public static ValueAnimator ofFloat(float... values){return new ValueAnimator();}
 public void setDuration(long duration){}public void setInterpolator(Object interpolator){}public void setRepeatCount(int count){}
 public void addUpdateListener(AnimatorUpdateListener listener){listeners.add(listener);}public void removeAllUpdateListeners(){listeners.clear();}
 public void start(){running.add(this);}public void cancel(){running.remove(this);}
 public static void tick(){for(ValueAnimator animator:running.toArray(new ValueAnimator[0]))for(AnimatorUpdateListener listener:animator.listeners)listener.onAnimationUpdate(animator);}
}''',
            'android/view/animation/LinearInterpolator.java': 'package android.view.animation;public class LinearInterpolator {}',
            'android/graphics/Color.java': 'package android.graphics;public class Color {public static final int TRANSPARENT=0;}',
            'android/graphics/Matrix.java': '''package android.graphics;public class Matrix {
 public float radius,x,y;public void setScale(float x,float y){radius=x;this.x=this.y=0;}
 public void postTranslate(float x,float y){this.x=x;this.y=y;}
}''',
            'android/graphics/Shader.java': '''package android.graphics;public class Shader {
 public enum TileMode {CLAMP}public float radius,x,y;
 public void setLocalMatrix(Matrix matrix){radius=matrix.radius;x=matrix.x;y=matrix.y;}
}''',
            'android/graphics/RadialGradient.java': '''package android.graphics;public class RadialGradient extends Shader {
 public final int[] colors;public RadialGradient(float x,float y,float radius,int[] colors,float[] stops,TileMode mode){this.colors=colors;}
}''',
            'android/graphics/LinearGradient.java': 'package android.graphics;public class LinearGradient extends Shader {public LinearGradient(float x,float y,float a,float b,int c,int d,TileMode mode){}}',
            'android/graphics/Paint.java': 'package android.graphics;public class Paint {public static final int ANTI_ALIAS_FLAG=1;public Shader shader;public Paint(int flags){}public void setShader(Shader shader){this.shader=shader;}}',
            'android/graphics/Canvas.java': '''package android.graphics;public class Canvas {
 public final java.util.List<Shader> draws=new java.util.ArrayList<>();public void drawRect(float l,float t,float r,float b,Paint paint){draws.add(paint.shader);}
}''',
            'android/view/View.java': '''package android.view;public class View {
 public static final int VISIBLE=0;public boolean attached,shown=true,focus,clipped;public int window=0,invalidations;
 private final android.content.Context context;public View(android.content.Context context){this.context=context;}
 public android.content.Context getContext(){return context;}public void setWillNotDraw(boolean value){}public void setClipToOutline(boolean value){clipped=value;}
 public boolean isAttachedToWindow(){return attached;}public boolean isShown(){return shown;}public boolean hasWindowFocus(){return focus;}
 public int getWindowVisibility(){return window;}public int getWidth(){return 324;}public int getHeight(){return 112;}
 public void invalidate(){invalidations++;}protected void onAttachedToWindow(){attached=true;}protected void onDetachedFromWindow(){attached=false;}
 protected void onVisibilityChanged(View view,int visibility){shown=visibility==0;}protected void onWindowVisibilityChanged(int visibility){window=visibility;}
 public void onWindowFocusChanged(boolean focus){this.focus=focus;}protected void onSizeChanged(int w,int h,int a,int b){}protected void onDraw(android.graphics.Canvas canvas){}
}''',
            'android/widget/FrameLayout.java': 'package android.widget;public class FrameLayout extends android.view.View {public FrameLayout(android.content.Context c,android.util.AttributeSet a){super(c);}}',
            'AboutGlowProbe.java': '''import android.animation.ValueAnimator;import android.content.Context;
import android.graphics.*;import android.os.SystemClock;import android.provider.Settings;
import com.android.settings.hecker.HeckerAboutGlowLayout;import com.android.settings.hecker.HeckerAboutPhoneValues;
import com.android.systemui.hecker.MediaCardGeometry;import org.hecker.motion.MediaGlowSpec;
public class AboutGlowProbe {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void call(Object view,String name,Class<?>[] types,Object... args)throws Exception {
  java.lang.reflect.Method method=HeckerAboutGlowLayout.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(view,args);
 }
 static void running(int count){check(ValueAnimator.running.size()==count);}
 public static void main(String[] args)throws Exception {
  Context context=new Context();HeckerAboutGlowLayout view=new HeckerAboutGlowLayout(context,null);
  check(view.clipped);view.setActive(true);running(0);
  call(view,"onAttachedToWindow",new Class<?>[0]);check(context.resolver.observed==2&&context.receiver!=null);running(0);
  view.onWindowFocusChanged(true);running(1);view.setActive(true);running(1);
  view.onWindowFocusChanged(false);running(0);view.onWindowFocusChanged(true);running(1);
  call(view,"onVisibilityChanged",new Class<?>[]{android.view.View.class,int.class},view,8);running(0);
  call(view,"onVisibilityChanged",new Class<?>[]{android.view.View.class,int.class},view,0);running(1);
  call(view,"onWindowVisibilityChanged",new Class<?>[]{int.class},8);running(0);
  call(view,"onWindowVisibilityChanged",new Class<?>[]{int.class},0);running(1);
  context.power.saving=true;context.receiver.onReceive(context,null);running(0);
  context.power.saving=false;context.receiver.onReceive(context,null);running(1);
  ValueAnimator.allowed=false;context.resolver.observer.onChange(false);running(0);
  ValueAnimator.allowed=true;context.resolver.observer.onChange(false);running(1);
  Settings.Secure.flow=0;context.resolver.observer.onChange(false);running(0);
  Settings.Secure.flow=1;context.resolver.observer.onChange(false);running(1);
  SystemClock.now=3600;ValueAnimator.tick();
  call(view,"onSizeChanged",new Class<?>[]{int.class,int.class,int.class,int.class},324,112,0,0);
  Canvas canvas=new Canvas();call(view,"onDraw",new Class<?>[]{Canvas.class},canvas);check(canvas.draws.size()==5);
  float phase=MediaGlowSpec.phase(3600);
  for(int i=0;i<3;i++){
   Shader shader=canvas.draws.get(i+1);check(shader instanceof RadialGradient);
   check(Math.abs(shader.x-324*MediaGlowSpec.centerX(i,phase))<.001);
   check(Math.abs(shader.y-112*MediaGlowSpec.centerY(i,phase))<.001);
   check(Math.abs(shader.radius-324*MediaGlowSpec.radius(i,phase))<.001);
   check(((RadialGradient)shader).colors[0]==MediaGlowSpec.centerColor(i));
  }
  for(long t=0;t<30000;t+=17){
   check(Math.abs(MediaGlowSpec.phase(t)-MediaCardGeometry.glowPhase(t))<.00001);
   check(Math.abs(MediaGlowSpec.phase(t)-MediaGlowSpec.phase(t+7200))<.00001);
  }
  check(HeckerAboutPhoneValues.processor("SM8150","other").equals("Snapdragon 855"));
  check(HeckerAboutPhoneValues.processor("Other chip","other").equals("Other chip"));
  check(HeckerAboutPhoneValues.processor("unknown","reported board").equals("reported board"));
  view.setActive(false);running(0);view.setActive(true);running(1);
  call(view,"onDetachedFromWindow",new Class<?>[0]);running(0);check(context.resolver.observed==0&&context.receiver==null);
  int after=view.invalidations;ValueAnimator.tick();check(view.invalidations==after);
  System.out.println("About glow: shared media geometry/phase/colors, frame cleanup, focus/visibility, saver, reduced motion and real hardware formatting PASS");
 }
}''',
        }
        with tempfile.TemporaryDirectory(prefix='hecker-about-glow-') as temporary:
            out = Path(temporary)
            for name, content in sources.items():
                path = out / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            production = [ROOT / 'framework/qs/MediaGlowSpec.java', ROOT / 'framework/qs/MediaCardGeometry.java',
                          ROOT / 'settings/src/com/android/settings/hecker/HeckerAboutGlowLayout.java',
                          ROOT / 'settings/src/com/android/settings/hecker/HeckerAboutPhoneValues.java']
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(out / 'classes'),
                            *[str(out / name) for name in sources], *map(str, production)], check=True)
            subprocess.run(['java', '-cp', str(out / 'classes'), 'AboutGlowProbe'], check=True)


if __name__ == '__main__':
    unittest.main()
