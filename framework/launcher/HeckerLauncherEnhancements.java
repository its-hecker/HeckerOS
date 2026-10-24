// SPDX-License-Identifier: Apache-2.0
package com.android.internal.hecker;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** @hide Framework-owned, guarded view adapters; no Xposed/runtime injection or HOME swap. */
public final class HeckerLauncherEnhancements {
    private static final String PIXEL="com.google.android.apps.nexuslauncher";
    private static final WeakHashMap<Activity,Session> SESSIONS=new WeakHashMap<>();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static float offsetX=.5f,offsetY=.5f,zoomOut;
    private HeckerLauncherEnhancements(){}
    public static void attach(Activity activity){
        if(!PIXEL.equals(activity.getPackageName()))return;
        try{
            Class.forName("com.android.launcher3.Launcher",false,activity.getClassLoader()).cast(activity);
            detach(activity);Session session=new Session(activity);SESSIONS.put(activity,session);session.start();
        }catch(ReflectiveOperationException|RuntimeException unsupported){android.util.Log.w("HeckerPixel","Unrecognized Pixel Launcher; keeping stock behavior",unsupported);}
    }
    public static void detach(Activity activity){Session old=SESSIONS.remove(activity);if(old!=null)old.stop();}
    public static boolean touch(Activity activity,MotionEvent event){
        Session session=SESSIONS.get(activity);if(session==null)return false;try{return session.touch(event);}catch(RuntimeException unsupported){detach(activity);return false;}
    }
    public static void wallpaperOffsets(Context c,float x,float y){if(PIXEL.equals(c.getPackageName())){offsetX=clamp(x);offsetY=clamp(y);invalidate();}}
    public static void wallpaperZoom(Context c,float zoom){if(PIXEL.equals(c.getPackageName())){zoomOut=clamp(zoom);invalidate();}}
    private static float clamp(float n){return Float.isFinite(n)?Math.max(0,Math.min(1,n)):.5f;}
    private static void invalidate(){if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(HeckerLauncherEnhancements::invalidate);return;}for(Session s:SESSIONS.values())s.invalidate();}
    private static IHeckerLauncherService service(){return IHeckerLauncherService.Stub.asInterface(ServiceManager.getService("hecker_launcher"));}
    private static final class Session implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<Activity> activity;
        final WeakHashMap<View,Drawable> originals=new WeakHashMap<>();
        final HeckerHomeDoubleTap taps=new HeckerHomeDoubleTap();
        final Object normal,allApps;final Method stateMethod;final Object stateManager;
        Bundle config;Bitmap wallpaper;boolean stopped,dirty=true,wasVisible;
        final ViewTreeObserver.OnGlobalLayoutListener layout=()->dirty=true;
        Session(Activity a)throws ReflectiveOperationException{
            activity=new WeakReference<>(a);stateManager=a.getClass().getMethod("getStateManager").invoke(a);
            stateMethod=stateManager.getClass().getMethod("getState");
            Class<?> states=Class.forName("com.android.launcher3.LauncherState",false,a.getClassLoader());
            normal=states.getField("NORMAL").get(null);allApps=states.getField("ALL_APPS").get(null);
        }
        void start(){
            Activity a=activity.get();if(a==null)return;
            View root=a.getWindow().getDecorView();root.getViewTreeObserver().addOnPreDrawListener(this);root.getViewTreeObserver().addOnGlobalLayoutListener(layout);
            WORKER.execute(()->{
                Bundle settings=null;Bitmap image=null;
                try{
                    IHeckerLauncherService bridge=service();if(bridge!=null){settings=bridge.configuration();
                        try(ParcelFileDescriptor file=bridge.wallpaper()){
                            if(file!=null){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                                BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(),null,bounds);
                                android.system.Os.lseek(file.getFileDescriptor(),0,android.system.OsConstants.SEEK_SET);
                                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/1440);
                                options.inPreferredConfig=Bitmap.Config.ARGB_8888;image=BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(),null,options);}
                        }
                    }
                }catch(Exception unavailable){/* Live/inaccessible wallpaper retains translucent glass. */}
                final Bundle resolved=settings;final Bitmap bitmap=image;
                MAIN.post(()->{if(stopped)return;config=resolved;wallpaper=bitmap;dirty=true;invalidate();});
            });
        }
        Object state(){try{return stateMethod.invoke(stateManager);}catch(ReflectiveOperationException|RuntimeException unavailable){return null;}}
        void stop(){
            stopped=true;taps.cancel();Activity a=activity.get();
            if(a!=null){ViewTreeObserver observer=a.getWindow().getDecorView().getViewTreeObserver();if(observer.isAlive()){observer.removeOnPreDrawListener(this);observer.removeOnGlobalLayoutListener(layout);}}
            restore();wallpaper=null;
        }
        void restore(){for(Map.Entry<View,Drawable> e:new ArrayList<>(originals.entrySet()))if(e.getKey()!=null&&e.getKey().getBackground() instanceof Glass)try{e.getKey().setBackground(e.getValue());}catch(RuntimeException ignored){}originals.clear();}
        void invalidate(){for(View view:new ArrayList<>(originals.keySet()))if(view!=null)view.invalidate();}
        @Override public boolean onPreDraw(){try{return refresh();}catch(RuntimeException unsupported){stop();return true;}}
        private boolean refresh(){
            Activity a=activity.get();if(a==null||stopped||config==null)return true;
            Object current=state();boolean visible=current==normal||current==allApps;
            if(visible!=wasVisible){dirty=true;wasVisible=visible;if(!visible){restore();taps.cancel();}}
            if(visible&&dirty){dirty=false;int mode=Math.max(0,Math.min(2,config.getInt("glass",1)));if(mode==0)restore();else scan(a.getWindow().getDecorView(),mode,new int[]{0});}
            return true;
        }
        void scan(View view,int mode,int[] visited){
            if(++visited[0]>3000||view.getVisibility()!=View.VISIBLE)return;
            String name=view.getClass().getName(),id="";
            try{if(view.getId()!=View.NO_ID)id=view.getResources().getResourceEntryName(view.getId());}catch(RuntimeException ignored){}
            int target=name.endsWith("Hotseat")?1:name.endsWith(".folder.Folder")?4
                    :name.equals("android.appwidget.AppWidgetHostView")||inherits(view,"android.appwidget.AppWidgetHostView")?8
                    :id.equals("qsb_container")||id.equals("search_container_workspace")||id.equals("search_container_all_apps")?2:0;
            if(target!=0&&(config.getInt("glass_targets",15)&target)!=0&&!originals.containsKey(view)){
                originals.put(view,view.getBackground());view.setBackground(new Glass(view,this,mode));
            }
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)scan(group.getChildAt(i),mode,visited);}
        }
        boolean touch(MotionEvent event){
            Activity a=activity.get();if(a==null||stopped||config==null||config.getInt("double_tap",1)==0||state()!=normal
                    ||a.getSystemService(AccessibilityManager.class).isTouchExplorationEnabled()){taps.cancel();return false;}
            ViewConfiguration vc=ViewConfiguration.get(a);float x=event.getRawX(),y=event.getRawY();boolean empty=empty(a.getWindow().getDecorView(),x,y);
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:taps.down(event.getEventTime(),x,y,empty);break;
                case MotionEvent.ACTION_MOVE:case MotionEvent.ACTION_POINTER_DOWN:taps.move(x,y,vc.getScaledTouchSlop(),event.getPointerCount()>1);break;
                case MotionEvent.ACTION_CANCEL:taps.cancel();break;
                case MotionEvent.ACTION_UP:
                    if(taps.up(event.getEventTime(),x,y,empty,ViewConfiguration.getDoubleTapTimeout(),vc.getScaledDoubleTapSlop())){
                        WORKER.execute(()->{try{IHeckerLauncherService bridge=service();if(bridge!=null)bridge.sleep();}catch(RemoteException|RuntimeException ignored){}});return true;
                    }
            }return false;
        }
        boolean empty(View root,float x,float y){
            Rect rect=new Rect();if(!root.isShown()||!root.getGlobalVisibleRect(rect)||!rect.contains((int)x,(int)y))return false;
            // Hit-test every overlapping child, so widgets/icons above a CellLayout cannot leak through.
            boolean[] workspace={false};return emptyBranch(root,x,y,workspace)&&workspace[0];
        }
        boolean emptyBranch(View view,float x,float y,boolean[] workspace){
            Rect rect=new Rect();if(!view.isShown()||!view.getGlobalVisibleRect(rect)||!rect.contains((int)x,(int)y))return true;
            String name=view.getClass().getName();
            if(inherits(view,"com.android.launcher3.Workspace"))workspace[0]=true;
            if(inherits(view,"com.android.launcher3.folder.Folder")||name.contains("BubbleTextView")||name.contains("FolderIcon")||inherits(view,"android.appwidget.AppWidgetHostView")
                    ||name.endsWith("Hotseat")||view instanceof android.widget.TextView&&view.isClickable())return false;
            if(view.isClickable()&&!inherits(view,"com.android.launcher3.Workspace")&&!inherits(view,"com.android.launcher3.CellLayout")
                    &&!name.endsWith("DragLayer")&&!name.endsWith("LauncherRootView")&&!name.equals("com.android.internal.policy.DecorView"))return false;
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--)if(!emptyBranch(group.getChildAt(i),x,y,workspace))return false;}
            return true;
        }
    }
    private static boolean inherits(View view,String name){for(Class<?> c=view.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals(name))return true;return false;}
    private static final class Glass extends Drawable {
        private final WeakReference<View> host;private final Session owner;private final int mode;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final RectF bounds=new RectF();
        private RuntimeShader shader;private int alpha=255;
        private static final String PROGRAM="uniform shader image;uniform float2 size;uniform float4 radii;uniform float frosted;"
                +"half4 main(float2 xy){float2 p=xy-size*.5;float r=p.x<0?(p.y<0?radii.x:radii.w):(p.y<0?radii.y:radii.z);"
                +"float2 q=abs(p)-size*.5+r;float d=length(max(q,0.))+min(max(q.x,q.y),0.)-r;float coverage=1.-smoothstep(-1.,.8,d);"
                +"float2 n=normalize(p+float2(.001));float edge=1.-smoothstep(0.,12.,abs(d));float2 uv=xy+n*edge*(1.-frosted)*7.;"
                +"half4 c=image.eval(uv);if(frosted>.5){c=(c+image.eval(uv+float2(12.,0.))+image.eval(uv-float2(12.,0.))"
                +"+image.eval(uv+float2(0.,12.))+image.eval(uv-float2(0.,12.))+image.eval(uv+float2(9.,9.))"
                +"+image.eval(uv-float2(9.,9.))+image.eval(uv+float2(9.,-9.))+image.eval(uv+float2(-9.,9.)))/9.;}"
                +"float rim=(1.-smoothstep(0.,2.,abs(d)))*(.06+.13*max(0.,dot(n,normalize(float2(-.7,-1.)))));"
                +"return half4(c.rgb*.83+half3(.025,.045,.07)+rim,1.)*coverage;}";
        Glass(View view,Session session,int mode){host=new WeakReference<>(view);owner=session;this.mode=mode;}
        @Override public void draw(Canvas canvas){
            View view=host.get();if(view==null)return;bounds.set(getBounds());if(bounds.isEmpty())return;
            float density=view.getResources().getDisplayMetrics().density,limit=Math.min(bounds.width(),bounds.height())/2;
            float[] radii=new float[8];String[] keys={"corner_tl","corner_tr","corner_br","corner_bl"};
            for(int i=0;i<4;i++)radii[i*2]=radii[i*2+1]=Math.min(limit,Math.max(0,Math.min(48,owner.config.getInt(keys[i],28)))*density);
            path.reset();path.addRoundRect(bounds,radii,Path.Direction.CW);paint.setAlpha(alpha);paint.setColor(0x70344D65);
            Bitmap image=owner.wallpaper;
            try{
                if(image!=null&&!image.isRecycled()){
                    int[] position=new int[2];view.getLocationOnScreen(position);View root=view.getRootView();
                    float sw=root.getWidth(),sh=root.getHeight();float maximum=1.1f;
                    int resource=view.getResources().getIdentifier("config_wallpaperMaxScale","dimen","android");
                    if(resource!=0)maximum=view.getResources().getFloat(resource);
                    float scale=Math.max(sw/image.getWidth(),sh/image.getHeight())*(maximum+(1-maximum)*zoomOut);
                    Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate((sw-image.getWidth()*scale)*offsetX-position[0],(sh-image.getHeight()*scale)*offsetY-position[1]);
                    BitmapShader backdrop=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);backdrop.setLocalMatrix(matrix);backdrop.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                    if(shader==null)shader=new RuntimeShader(PROGRAM);shader.setInputShader("image",backdrop);shader.setFloatUniform("size",bounds.width(),bounds.height());
                    shader.setFloatUniform("radii",radii[0],radii[2],radii[4],radii[6]);shader.setFloatUniform("frosted",mode==1?1:0);paint.setShader(shader);
                }
            }catch(RuntimeException unsupported){paint.setShader(null);}
            canvas.drawPath(path,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(0x35FFFFFF);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
