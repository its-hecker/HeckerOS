// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.PathInterpolator;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Draws below native launcher scrims/icons; only clock views redraw the subject above their text. */
public final class HeckerHomeDepth {
    private static final String ID="hecker_depth_wallpaper_id",ENABLED="hecker_home_depth_wallpaper",
            WALLPAPER="hecker_home_depth_wallpaper_wallpaper_id";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final WeakHashMap<View,Scene> SCENES=new WeakHashMap<>();
    private static final class Pair {
        final Bitmap back,front;
        Pair(Bitmap back,Bitmap front){this.back=back;this.front=front;}
    }
    private static final class Scene implements View.OnAttachStateChangeListener,
            SharedPreferences.OnSharedPreferenceChangeListener {
        final Context context;final WeakReference<View> root;
        final Handler main=new Handler(Looper.getMainLooper());
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final RectF bounds=new RectF();final float[] transform=new float[3];
        final int[] rootLocation=new int[2],clockLocation=new int[2];
        final WeakHashMap<View,Boolean> clocks=new WeakHashMap<>();
        final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){refresh();}};
        final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
            String action=intent.getAction();
            if(Intent.ACTION_WALLPAPER_CHANGED.equals(action)||Intent.ACTION_USER_UNLOCKED.equals(action))refresh();
            else if(Intent.ACTION_SCREEN_OFF.equals(action)||PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)){settle();invalidate();}
            else if(pair==null)refresh();else reveal();
        }};
        Pair pair;ValueAnimator animator;float progress=1;volatile int generation;volatile boolean closed;
        boolean observerRegistered,receiverRegistered,preferencesRegistered;
        Scene(View root){
            context=root.getContext().getApplicationContext();this.root=new WeakReference<>(root);
            root.addOnAttachStateChangeListener(this);
            try {
            for(String key:new String[]{ID,ENABLED,WALLPAPER}){context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key),false,observer);observerRegistered=true;}
            IntentFilter filter=new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
            filter.addAction(Intent.ACTION_USER_UNLOCKED);filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);receiverRegistered=true;
            HeckerStudio.prefs(context).registerOnSharedPreferenceChangeListener(this);preferencesRegistered=true;refresh();
            } catch(RuntimeException unavailable) { onViewDetachedFromWindow(root); }
        }
        private String id(){return Settings.Secure.getString(context.getContentResolver(),ID);}
        private boolean enabled(){return HeckerStudio.enabled(context,"home_depth",true)
                &&Settings.Secure.getInt(context.getContentResolver(),ENABLED,0)==1;}
        private Bitmap decode(String id,String layer)throws Exception{
            Uri uri=Uri.parse("content://org.hecker.suite.depth/"+id+"/"+layer);
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream input=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(input,null,options);}
            if(options.outWidth<=0||options.outHeight<=0||Math.max(options.outWidth,options.outHeight)>1600)throw new IllegalArgumentException("Unbounded depth layer");
            options.inJustDecodeBounds=false;
            try(InputStream input=context.getContentResolver().openInputStream(uri)){return BitmapFactory.decodeStream(input,null,options);}
        }
        void refresh(){
            if(closed)return;int token=++generation;pair=null;settle();invalidate();
            IO.execute(()->{
                if(closed||token!=generation)return;Pair next=null;
                try{
                    WallpaperManager manager=WallpaperManager.getInstance(context);String selected=id();
                    int expected=Settings.Secure.getInt(context.getContentResolver(),WALLPAPER,-1);
                    if(HomeDepthPolicy.eligible(enabled(),manager.getWallpaperInfo()!=null,selected,expected,manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))){
                        Bitmap back=decode(selected,"background"),front=decode(selected,"foreground");
                        if(back!=null&&front!=null&&back.getWidth()==front.getWidth()&&back.getHeight()==front.getHeight()
                                &&selected.equals(id())&&enabled()&&expected==manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))next=new Pair(back,front);
                    }
                }catch(Exception|OutOfMemoryError unavailable){}
                Pair loaded=next;main.post(()->{if(!closed&&token==generation){pair=loaded;reveal();invalidate();}});
            });
        }
        private boolean canAnimate(){
            View view=root.get();PowerManager power=context.getSystemService(PowerManager.class);
            KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
            return view!=null&&view.isShown()&&view.getWindowVisibility()==View.VISIBLE
                    &&power!=null&&power.isInteractive()&&!power.isPowerSaveMode()
                    &&(keyguard==null||!keyguard.isKeyguardLocked())&&ValueAnimator.areAnimatorsEnabled()
                    &&HeckerStudio.enabled(context,"home_depth_wake",true);
        }
        void reveal(){
            settle();if(pair==null||!canAnimate())return;
            // Keyguard already finished the aligned wake crop. Re-starting a zoom here jumps
            // the subject edge at the unlock handoff; Living Flow keeps that final crop.
            if(Settings.Secure.getInt(context.getContentResolver(),"hecker_living_flow",1)!=0)return;
            progress=0;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(750);
            animator.setInterpolator(new PathInterpolator(.4f,0,.2f,1));
            animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();
        }
        void settle(){if(animator!=null){animator.cancel();animator=null;}progress=1;}
        void invalidate(){View view=root.get();if(view!=null)view.invalidate();for(View clock:new ArrayList<>(clocks.keySet()))if(clock!=null)clock.invalidate();}
        void draw(Canvas canvas,boolean foreground){
            View view=root.get();Pair current=pair;
            if(closed||view==null||current==null||view.getWidth()<=0||view.getHeight()<=0)return;
            Bitmap image=foreground?current.front:current.back;
            HomeDepthPolicy.crop(image.getWidth(),image.getHeight(),view.getWidth(),view.getHeight(),progress,transform);
            bounds.set(transform[1],transform[2],transform[1]+image.getWidth()*transform[0],transform[2]+image.getHeight()*transform[0]);
            canvas.drawBitmap(image,null,bounds,paint);
        }
        @Override public void onViewAttachedToWindow(View view){}
        @Override public void onViewDetachedFromWindow(View view){
            if(closed)return;closed=true;generation++;settle();pair=null;clocks.clear();main.removeCallbacksAndMessages(null);
            if(observerRegistered)context.getContentResolver().unregisterContentObserver(observer);
            if(receiverRegistered)context.unregisterReceiver(receiver);
            if(preferencesRegistered)HeckerStudio.prefs(context).unregisterOnSharedPreferenceChangeListener(this);
            view.removeOnAttachStateChangeListener(this);SCENES.remove(view);
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
            if(key!=null&&key.startsWith(HeckerStudio.PREFIX+"home_depth"))refresh();
        }
    }
    /** Called before the native DragLayer scrim and child rendering, without adding a touch target. */
    public static void background(View root,Canvas canvas){
        if(!root.isAttachedToWindow())return;
        Scene scene=SCENES.get(root);
        if(scene==null){scene=new Scene(root);if(!scene.closed)SCENES.put(root,scene);}
        scene.draw(canvas,false);scene.draw(canvas,true);
    }
    /** View-local clipping protects app icons, folders, weather and launcher interaction. */
    public static void foreground(View clock,Canvas canvas){
        ViewParent parent=clock.getParent();
        if(Math.abs(clock.getScaleX()-1)>.001f||Math.abs(clock.getScaleY()-1)>.001f||clock.getRotation()!=0)return;
        while(parent instanceof View){
            View view=(View)parent;
            if(Math.abs(view.getScaleX()-1)>.001f||Math.abs(view.getScaleY()-1)>.001f||view.getRotation()!=0)return;
            Scene scene=SCENES.get(view);
            if(scene!=null){
                scene.clocks.put(clock,true);view.getLocationInWindow(scene.rootLocation);clock.getLocationInWindow(scene.clockLocation);
                int save=canvas.save();canvas.clipRect(0,0,clock.getWidth(),clock.getHeight());
                canvas.translate(scene.rootLocation[0]-scene.clockLocation[0],scene.rootLocation[1]-scene.clockLocation[1]);
                scene.draw(canvas,true);canvas.restoreToCount(save);return;
            }
            parent=view.getParent();
        }
    }
    private HeckerHomeDepth(){}
}
