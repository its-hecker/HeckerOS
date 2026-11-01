// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Process;
import android.os.UserManager;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.ModelDbController;
import java.util.ArrayList;
import java.util.List;

/** Called only when Launcher3 creates a fresh default database. Never repins an existing layout. */
public final class HeckerDefaultWorkspace {
    public static void seed(Context context, ModelDbController database, int columns, int rows) {
        if (columns < 1 || rows < 1 || columns > 20 || rows > 20) return;
        ComponentName game = new ComponentName("org.hecker.suite","org.hecker.suite.GameSpaceLauncher");
        try {
            android.content.pm.ActivityInfo activity = context.getPackageManager().getActivityInfo(game,0);
            long profile = context.getSystemService(UserManager.class).getSerialNumberForUser(Process.myUserHandle());
            List<int[]> cells = new ArrayList<>(); int screen = Integer.MAX_VALUE;
            try (Cursor cursor = database.query(new String[]{Favorites.INTENT,Favorites.CONTAINER,Favorites.SCREEN,
                    Favorites.CELLX,Favorites.CELLY,Favorites.SPANX,Favorites.SPANY},
                    Favorites.PROFILE_ID+"=?",new String[]{Long.toString(profile)},null)) {
                while (cursor.moveToNext()) {
                    String intent = cursor.getString(0);
                    if (intent != null) try { if(game.equals(Intent.parseUri(intent,0).getComponent())) return; }
                    catch(java.net.URISyntaxException ignored) { }
                    if(cursor.getInt(1)!=Favorites.CONTAINER_DESKTOP || cursor.getInt(2)<0)continue;
                    int page=cursor.getInt(2); screen=Math.min(screen,page);
                    cells.add(new int[]{page,cursor.getInt(3),cursor.getInt(4),cursor.getInt(5),cursor.getInt(6)});
                }
            }
            if(screen==Integer.MAX_VALUE)screen=0;
            boolean[][] occupied=new boolean[rows][columns];
            for(int[] cell:cells)if(cell[0]==screen)
                for(int y=Math.max(0,cell[2]);y<Math.min(rows,cell[2]+cell[4]);y++)
                    for(int x=Math.max(0,cell[1]);x<Math.min(columns,cell[1]+cell[3]);x++)occupied[y][x]=true;
            int targetX=-1,targetY=-1;
            for(int y=rows-1;y>=0&&targetX<0;y--)for(int x=0;x<columns;x++)if(!occupied[y][x]){targetX=x;targetY=y;break;}
            if(targetX<0){screen=database.getNewScreenId();targetX=0;targetY=rows-1;}
            Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(game)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ContentValues values=new ContentValues();values.put(Favorites._ID,database.generateNewItemId());
            values.put(Favorites.TITLE,activity.loadLabel(context.getPackageManager()).toString());
            values.put(Favorites.INTENT,launch.toUri(0));values.put(Favorites.ITEM_TYPE,Favorites.ITEM_TYPE_APPLICATION);
            values.put(Favorites.CONTAINER,Favorites.CONTAINER_DESKTOP);values.put(Favorites.SCREEN,screen);
            values.put(Favorites.CELLX,targetX);values.put(Favorites.CELLY,targetY);values.put(Favorites.SPANX,1);values.put(Favorites.SPANY,1);
            values.put(Favorites.PROFILE_ID,profile);values.put(Favorites.RESTORED,0);database.insert(values);
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) {
            android.util.Log.w("HeckerLauncher","GameSpace default shortcut unavailable",unavailable);
        }
    }
    private HeckerDefaultWorkspace() { }
}
