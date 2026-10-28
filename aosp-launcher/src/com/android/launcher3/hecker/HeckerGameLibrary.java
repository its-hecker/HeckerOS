// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.view.View;
import java.util.*;
import java.util.concurrent.*;

/** A presentation filter. It never disables packages or deletes workspace records. */
public final class HeckerGameLibrary {
    private static final Uri URI=Uri.parse("content://org.hecker.suite.games/library");
    private static final Object LOCK=new Object();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final List<Runnable> listeners=new ArrayList<>();
    private static final WeakHashMap<View,Entry> views=new WeakHashMap<>();
    private static volatile Set<String> games=Collections.emptySet();
    private static Context app;private static int generation;private static boolean observing;
    private static final ContentObserver observer=new ContentObserver(MAIN){@Override public void onChange(boolean selfChange){refresh();}};
    private static final class Entry {
        final ComponentName component;final UserHandle user;int original;boolean masked;
        Entry(ComponentName component,UserHandle user){this.component=component;this.user=user;}
    }
    private static boolean valid(String pkg){return pkg!=null&&pkg.length()<=255&&pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")&&!pkg.equals("org.hecker.suite")&&!pkg.equals("com.android.launcher3");}
    private static Set<String> clean(Set<String> packages){Set<String> result=new HashSet<>();for(String pkg:packages)if(valid(pkg))result.add(pkg);return Collections.unmodifiableSet(result);}
    private static void ensure(Context context){
        synchronized(LOCK){
            if(app!=null)return;app=context.getApplicationContext();
            try{games=clean(app.getSharedPreferences("hecker_game_library",Context.MODE_PRIVATE).getStringSet("packages",Collections.emptySet()));}catch(ClassCastException ignored){}
            BroadcastReceiver changed=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){refresh();}};
            IntentFilter unlocked=new IntentFilter(Intent.ACTION_USER_UNLOCKED);app.registerReceiver(changed,unlocked,Context.RECEIVER_NOT_EXPORTED);
            IntentFilter packages=new IntentFilter();packages.addDataScheme("package");packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
            app.registerReceiver(changed,packages,Context.RECEIVER_NOT_EXPORTED);
            refresh();
        }
    }
    private static void refresh(){
        final Context context;final int epoch;
        synchronized(LOCK){context=app;epoch=++generation;}
        if(context==null)return;
        IO.execute(()->{
            synchronized(LOCK){if(epoch!=generation)return;}
            // The provider may have been unavailable before user unlock or during an update.
            if(!observing)try{context.getContentResolver().registerContentObserver(URI,false,observer);observing=true;}catch(RuntimeException unavailable){}
            Set<String> found=new HashSet<>();
            try(Cursor cursor=context.getContentResolver().query(URI,new String[]{"package"},null,null,null)){
                if(cursor==null)return;
                while(cursor.moveToNext()){
                    if(found.size()>=4096)return;String pkg=cursor.getString(0);if(valid(pkg))found.add(pkg);
                }
            }catch(RuntimeException unavailable){return;}
            Set<String> next=Collections.unmodifiableSet(found);
            MAIN.post(()->{
                List<Runnable> callbacks;List<View> affected;
                synchronized(LOCK){if(epoch!=generation)return;if(next.equals(games))return;
                    games=next;context.getSharedPreferences("hecker_game_library",Context.MODE_PRIVATE).edit().putStringSet("packages",next).apply();
                    callbacks=new ArrayList<>(listeners);affected=new ArrayList<>(views.keySet());}
                for(View view:affected){Entry entry; synchronized(LOCK){entry=views.get(view);}if(entry!=null)apply(view,entry);view.invalidate();}
                for(Runnable callback:callbacks)callback.run();
            });
        });
    }
    public static boolean hidden(Context context,ComponentName component,UserHandle user){
        ensure(context);return component!=null&&android.os.Process.myUserHandle().equals(user)&&games.contains(component.getPackageName());
    }
    public static Runnable observe(Context context,Runnable changed){
        ensure(context);synchronized(LOCK){listeners.add(changed);}
        return ()->{synchronized(LOCK){listeners.remove(changed);}};
    }
    public static void watch(View view){
        ensure(view.getContext());synchronized(LOCK){if(!views.containsKey(view))views.put(view,new Entry(null,null));}
    }
    public static void bind(View view,ComponentName component,UserHandle user){
        ensure(view.getContext());Entry entry=new Entry(component,user);
        synchronized(LOCK){Entry previous=views.put(view,entry);if(previous!=null&&previous.masked&&view.getVisibility()==View.INVISIBLE)view.setVisibility(previous.original);}
        apply(view,entry);
    }
    private static void apply(View view,Entry entry){
        if(entry.component==null)return;
        boolean hide=android.os.Process.myUserHandle().equals(entry.user)&&games.contains(entry.component.getPackageName());
        if(hide&&!entry.masked&&view.getVisibility()!=View.GONE){entry.original=view.getVisibility();entry.masked=true;view.setVisibility(View.INVISIBLE);}
        else if(!hide&&entry.masked){if(view.getVisibility()==View.INVISIBLE)view.setVisibility(entry.original);entry.masked=false;}
    }
    private HeckerGameLibrary(){}
}
