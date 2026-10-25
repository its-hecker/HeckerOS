// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.WallpaperManager;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import com.android.launcher3.LauncherFiles;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Launcher-owned containers only. Wallpaper decoding and frosting never run on the UI thread. */
public final class HeckerLauncherGlass {
    private static final WeakHashMap<View,Session> SESSIONS=new WeakHashMap<>();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    public static void attach(View workspace){detach(workspace);Session s=new Session(workspace);SESSIONS.put(workspace,s);s.start();}
    public static void detach(View workspace){Session s=SESSIONS.remove(workspace);if(s!=null)s.close();}
    public static void offset(View workspace,float offset){Session s=SESSIONS.get(workspace);if(s!=null&&Float.isFinite(offset)&&s.offset!=offset){s.offset=Math.max(0,Math.min(1,offset));s.invalidate();}}
    private static final class Session implements ViewTreeObserver.OnGlobalLayoutListener,SharedPreferences.OnSharedPreferenceChangeListener {
        final WeakReference<View> workspace;
        final SharedPreferences prefs;
        final Context app;
        final WeakHashMap<View,Drawable> originals=new WeakHashMap<>();
        Bitmap wallpaper,frosted;
        boolean stopped,queued;
        int generation;
        float offset=.5f;
        final BroadcastReceiver changed=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){load();}};
        final Runnable refresh=this::refresh;
        final Runnable refrost=this::refrost;
        int option(String key,int fallback,int min,int max){try{return Math.max(min,Math.min(max,prefs.getInt("hecker_glass_"+key,fallback)));}catch(ClassCastException bad){return fallback;}}
        void refresh(){queued=false;if(!stopped){restore();View v=workspace.get();if(v!=null&&mode()!=0)scan(v.getRootView(),new int[]{0});}}
        Session(View v){workspace=new WeakReference<>(v);app=v.getContext().getApplicationContext();prefs=app.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,Context.MODE_PRIVATE);}
        int mode(){try{return Math.max(0,Math.min(2,Integer.parseInt(prefs.getString("hecker_glass","1"))));}catch(NumberFormatException|ClassCastException bad){return 1;}}
        int corner(String position){try{return Math.max(0,Math.min(48,Integer.parseInt(prefs.getString("hecker_glass_corner_"+position,"28"))));}catch(NumberFormatException|ClassCastException bad){return 28;}}
        void start(){
            View v=workspace.get();if(v==null)return;
            v.getRootView().getViewTreeObserver().addOnGlobalLayoutListener(this);
            prefs.registerOnSharedPreferenceChangeListener(this);
            app.registerReceiver(changed,new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),Context.RECEIVER_NOT_EXPORTED);
            schedule();load();
        }
        void close(){stopped=true;generation++;MAIN.removeCallbacks(refresh);MAIN.removeCallbacks(refrost);prefs.unregisterOnSharedPreferenceChangeListener(this);
            app.unregisterReceiver(changed);View v=workspace.get();if(v!=null){ViewTreeObserver observer=v.getRootView().getViewTreeObserver();if(observer.isAlive())observer.removeOnGlobalLayoutListener(this);}
            restore();wallpaper=null;frosted=null;
        }
        void restore(){for(Map.Entry<View,Drawable> e:new ArrayList<>(originals.entrySet())){View v=e.getKey();if(v!=null&&v.getBackground() instanceof Glass)v.setBackground(e.getValue());}originals.clear();}
        void invalidate(){for(View v:new ArrayList<>(originals.keySet()))if(v!=null)v.invalidate();}
        void schedule(){if(!stopped&&!queued){queued=true;MAIN.post(refresh);}}
        @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){if(key!=null&&key.startsWith("hecker_glass")){schedule();if(key.equals("hecker_glass_frost")){MAIN.removeCallbacks(refrost);MAIN.postDelayed(refrost,120);}}}
        @Override public void onGlobalLayout(){
            if(stopped||mode()==0)return;
            // New folders/widgets are discovered after layout, not on every frame.
            View v=workspace.get();if(v!=null)scan(v.getRootView(),new int[]{0});
        }
        void scan(View v,int[] count){
            if(++count[0]>3000||v.getVisibility()!=View.VISIBLE)return;
            String name=v.getClass().getName(),id="";
            try{if(v.getId()!=View.NO_ID)id=v.getResources().getResourceEntryName(v.getId());}catch(RuntimeException ignored){}
            String target="hecker_glass_preview".equals(v.getTag())?"preview":name.endsWith(".Hotseat")?"dock":name.endsWith(".folder.Folder")?"folders"
                    :name.endsWith(".hecker.HeckerDockBar")||name.endsWith(".qsb.OseWidgetView")||id.equals("qsb_container")||id.equals("search_container_workspace")||id.equals("search_container_all_apps")?"search"
                    :v instanceof android.appwidget.AppWidgetHostView?"widgets":null;
            if(v instanceof android.appwidget.AppWidgetHostView && ((android.appwidget.AppWidgetHostView)v).getAppWidgetInfo()!=null
                    && ((android.appwidget.AppWidgetHostView)v).getAppWidgetInfo().provider.equals(new ComponentName(app,HeckerClockWidget.class))
                    && v instanceof ViewGroup && ((ViewGroup)v).getChildCount()>0 && ((ViewGroup)v).getChildAt(0) instanceof HeckerClockView
                    && !((HeckerClockView)((ViewGroup)v).getChildAt(0)).style().equals("glass"))target=null;
            if(target!=null&&prefs.getBoolean("hecker_glass_"+target,true)&&!originals.containsKey(v)){
                originals.put(v,v.getBackground());v.setBackground(new Glass(v,this));
            }
            if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)scan(group.getChildAt(i),count);}
        }
        void refrost(){
            Bitmap source=wallpaper;if(source==null||stopped)return;
            int request=++generation,radius=option("frost",14,0,32);
            WORKER.execute(()->{Bitmap soft=frost(source,radius);MAIN.post(()->{if(!stopped&&request==generation){frosted=soft;invalidate();}});});
        }
        void load(){
            final int request=++generation;
            WORKER.execute(()->{
                Bitmap image=null,blur=null;
                try{
                    WallpaperManager manager=WallpaperManager.getInstance(app);
                    // A live wallpaper has no static frame we can safely sample.
                    if(manager.getWallpaperInfo()==null)try(ParcelFileDescriptor file=manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)){
                        if(file!=null){
                            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                            BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(),null,bounds);
                            android.system.Os.lseek(file.getFileDescriptor(),0,android.system.OsConstants.SEEK_SET);
                            BitmapFactory.Options options=new BitmapFactory.Options();int sample=1;
                            while(Math.max(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2;
                            options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                            image=BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(),null,options);
                            if(image!=null)blur=frost(image,option("frost",14,0,32));
                        }
                    }
                }catch(Exception unavailable){android.util.Log.w("HeckerLauncher","Wallpaper glass uses translucent fallback",unavailable);}
                final Bitmap source=image,soft=blur;
                MAIN.post(()->{if(stopped||request!=generation)return;wallpaper=source;frosted=soft;invalidate();});
            });
        }
    }
    /** Small, separable blur of a bounded wallpaper copy; app/widget pixels are never captured. */
    private static Bitmap frost(Bitmap source,int radius){
        if(radius==0)return source;
        float factor=Math.min(1,512f/Math.max(source.getWidth(),source.getHeight()));
        Bitmap small=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*factor)),Math.max(1,Math.round(source.getHeight()*factor)),true);
        int w=small.getWidth(),h=small.getHeight();int[] pixels=new int[w*h],temp=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){blur(pixels,temp,w,h,true,radius);blur(temp,pixels,w,h,false,radius);}
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private static void blur(int[] input,int[] output,int w,int h,boolean horizontal,int radius){
        int span=radius*2+1,lines=horizontal?h:w,length=horizontal?w:h;
        for(int line=0;line<lines;line++){
            int red=0,green=0,blue=0;
            for(int k=-radius;k<=radius;k++){int pos=Math.max(0,Math.min(length-1,k));int color=input[horizontal?line*w+pos:pos*w+line];red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);}
            for(int i=0;i<length;i++){
                output[horizontal?line*w+i:i*w+line]=Color.rgb(red/span,green/span,blue/span);
                int before=Math.max(0,i-radius),after=Math.min(length-1,i+radius+1);
                int a=input[horizontal?line*w+before:before*w+line],b=input[horizontal?line*w+after:after*w+line];
                red+=Color.red(b)-Color.red(a);green+=Color.green(b)-Color.green(a);blue+=Color.blue(b)-Color.blue(a);
            }
        }
    }
    private static final class Glass extends Drawable {
        final WeakReference<View> host;final Session owner;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF bounds=new RectF();final Path path=new Path();
        RuntimeShader liquid;int alpha=255;
        private static final String PROGRAM="uniform shader image;uniform float2 size;uniform float4 radii;"
                +"half4 main(float2 xy){float2 p=xy-size*.5;float radius=p.x < 0.0 ? (p.y < 0.0 ? radii.x : radii.w) : (p.y < 0.0 ? radii.y : radii.z);float2 q=abs(p)-size*.5+radius;"
                +"float d=length(max(q,0.))+min(max(q.x,q.y),0.)-radius;"
                +"float2 n=normalize(p+float2(.001));float edge=1.-smoothstep(0.,14.,abs(d));"
                +"half4 c=image.eval(xy+n*edge*7.);return half4(c.rgb*.82+half3(.025,.04,.06)+edge*.06,1.);}";
        Glass(View view,Session owner){host=new WeakReference<>(view);this.owner=owner;}
        @Override public void draw(Canvas canvas){
            View view=host.get();if(view==null)return;bounds.set(getBounds());if(bounds.isEmpty())return;
            float density=view.getResources().getDisplayMetrics().density,limit=Math.min(bounds.width(),bounds.height())/2;
            float[] radii=new float[8];String[] positions={"tl","tr","br","bl"};
            for(int i=0;i<4;i++)radii[i*2]=radii[i*2+1]=Math.min(limit,owner.corner(positions[i])*density);
            path.reset();path.addRoundRect(bounds,radii,Path.Direction.CW);
            boolean night=(view.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int opacity=owner.option("opacity",60,0,100)*alpha/100;
            int tint=owner.option("tint",0xffaac8da,Integer.MIN_VALUE,Integer.MAX_VALUE);
            paint.setShader(null);paint.setColor(tint);paint.setAlpha(opacity);
            Bitmap image=owner.option("frost",14,0,32)>0?owner.frosted:owner.wallpaper;
            if(image==null)image=owner.wallpaper;
            if(image!=null&&!image.isRecycled())try{
                int[] position=new int[2];view.getLocationOnScreen(position);View root=view.getRootView();
                float w=root.getWidth(),h=root.getHeight(),zoom=1f;
                int maxScale=view.getResources().getIdentifier("config_wallpaperMaxScale","dimen","android");
                if(maxScale!=0)zoom=view.getResources().getFloat(maxScale);
                float scale=Math.max(w/image.getWidth(),h/image.getHeight())*zoom;
                Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate((w-image.getWidth()*scale)*owner.offset-position[0],(h-image.getHeight()*scale)*.5f-position[1]);
                BitmapShader texture=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);texture.setLocalMatrix(matrix);texture.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                paint.setAlpha(opacity);
                if(owner.mode()==2){if(liquid==null)liquid=new RuntimeShader(PROGRAM);liquid.setInputShader("image",texture);liquid.setFloatUniform("size",bounds.width(),bounds.height());liquid.setFloatUniform("radii",radii[0],radii[2],radii[4],radii[6]);paint.setShader(liquid);}else paint.setShader(texture);
            }catch(RuntimeException unsupported){paint.setShader(null);}
            canvas.drawPath(path,paint);paint.setShader(null);paint.setColor(tint);paint.setAlpha(opacity/2);canvas.drawPath(path,paint);
            int border=owner.option("border",1,0,4);
            if(border>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density*border);paint.setColor(night?0x85ffffff:0x851c3848);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
        }
        @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private HeckerLauncherGlass(){}
}
