// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.*;
import android.provider.Settings;
import java.io.*;

/** A separate owned Home pair. Automatic migration never replaces a chosen Home wallpaper. */
public final class HomeAppearance {
    public static final String ID="ca2aca3da00000000000000000000003";
    public static final String KEY="hecker_home_depth_wallpaper_id";
    private static final String ASSETS="wallpapers/caracal-harbor/";
    public static boolean apply(Context context,boolean requested)throws IOException {
        Context storage=context.createDeviceProtectedStorageContext();
        android.content.SharedPreferences prefs=storage.getSharedPreferences("hecker_default_appearance",Context.MODE_PRIVATE);
        if(!requested&&prefs.getBoolean("applied_home_v3",false))return false;
        WallpaperManager manager=WallpaperManager.getInstance(context);
        String selected=Settings.Secure.getString(context.getContentResolver(),KEY);
        if(selected==null)selected=Settings.Secure.getString(context.getContentResolver(),DepthWallpaperProvider.ID);
        int before=manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        boolean owned=DefaultAppearance.ID.equals(selected)&&before>0
            &&before==Settings.Secure.getInt(context.getContentResolver(),DefaultAppearance.HOME_WALLPAPER_ID,-1)
            &&manager.getWallpaperInfo()==null;
        if(!requested&&!owned){prefs.edit().putBoolean("applied_home_v3",true).commit();return false;}
        boolean lockOwned=Settings.Secure.getInt(context.getContentResolver(),DefaultAppearance.WALLPAPER_ID,-1)
                ==DefaultAppearance.wallpaperId(context);
        Bitmap back=decode(context,"background"),front=decode(context,"foreground");
        if(back.getWidth()!=front.getWidth()||back.getHeight()!=front.getHeight()||!front.hasAlpha())
            throw new IOException("Home layers do not align");
        float ratio=Math.min(1,1600f/Math.max(back.getWidth(),back.getHeight()));
        int w=Math.max(1,Math.round(back.getWidth()*ratio)),h=Math.max(1,Math.round(back.getHeight()*ratio));
        back=Bitmap.createScaledBitmap(back,w,h,true);front=Bitmap.createScaledBitmap(front,w,h,true);
        File directory=new File(new File(storage.getFilesDir(),"depth"),ID);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create Home layers");
        write(new File(directory,"background.png"),back);write(new File(directory,"foreground.png"),front);
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.drawBitmap(back,0,0,null);canvas.drawBitmap(front,0,0,null);
        if(!requested&&(manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)!=before||manager.getWallpaperInfo()!=null))return false;
        // Android retains the previous shared system image on LOCK when setting SYSTEM alone.
        manager.setBitmap(image,null,true,WallpaperManager.FLAG_SYSTEM);
        if(lockOwned)Settings.Secure.putInt(context.getContentResolver(),DefaultAppearance.WALLPAPER_ID,DefaultAppearance.wallpaperId(context));
        boolean saved=Settings.Secure.putInt(context.getContentResolver(),DefaultAppearance.HOME_WALLPAPER_ID,manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
            &&Settings.Secure.putInt(context.getContentResolver(),DefaultAppearance.HOME_DEPTH,requested?1:Settings.Secure.getInt(context.getContentResolver(),DefaultAppearance.HOME_DEPTH,1))
            &&Settings.Secure.putString(context.getContentResolver(),KEY,ID);
        if(!saved)throw new IOException("Cannot publish Home pair");
        prefs.edit().putBoolean("applied_home_v3",true).commit();return true;
    }
    private static Bitmap decode(Context c,String layer)throws IOException {
        try(InputStream input=c.getAssets().open(ASSETS+layer+".webp")) {
            Bitmap image=BitmapFactory.decodeStream(input);
            if(image==null||image.getWidth()<=0||image.getHeight()<=0||Math.max(image.getWidth(),image.getHeight())>4096)
                throw new IOException("Invalid Home image");
            return image;
        }
    }
    private static void write(File target,Bitmap image)throws IOException {
        android.util.AtomicFile atomic=new android.util.AtomicFile(target);FileOutputStream out=atomic.startWrite();
        try{if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Home encode failed");atomic.finishWrite(out);}
        catch(IOException|RuntimeException e){atomic.failWrite(out);throw e;}
    }
    private HomeAppearance() {}
}
