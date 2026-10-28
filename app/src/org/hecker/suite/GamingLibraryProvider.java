// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;

/** Read-only, same-user library bridge for the preinstalled platform-signed Hecker Launcher. */
public final class GamingLibraryProvider extends ContentProvider {
    public static final Uri URI=Uri.parse("content://org.hecker.suite.games/library");
    private static final String LAUNCHER="com.android.launcher3";
    private static final String READ="org.hecker.suite.permission.READ_GAME_LIBRARY";
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener preferences;
    private BroadcastReceiver packages;
    @Override public boolean onCreate(){
        Context c=getContext();if(c==null)return false;
        preferences=(p,key)->{if("added".equals(key)||"excluded".equals(key)||"automatic".equals(key)||"hide_from_launcher".equals(key))changed();};
        GamingPrefs.get(c).registerOnSharedPreferenceChangeListener(preferences);
        packages=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){changed();}};
        IntentFilter filter=new IntentFilter();filter.addDataScheme("package");
        for(String action:new String[]{Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_CHANGED,Intent.ACTION_PACKAGE_REPLACED})filter.addAction(action);
        c.registerReceiver(packages,filter,Context.RECEIVER_NOT_EXPORTED);return true;
    }
    private void changed(){Context c=getContext();if(c!=null)c.getContentResolver().notifyChange(URI,null);}
    private void enforce(){
        int uid=Binder.getCallingUid();if(uid==Process.myUid())return;
        Context c=getContext();if(c==null||!UserHandle.getUserHandleForUid(uid).equals(Process.myUserHandle()))throw new SecurityException("Different library user");
        c.enforceCallingPermission(READ,"Game library is private");
        try{
            PackageManager pm=c.getPackageManager();ApplicationInfo app=pm.getApplicationInfo(LAUNCHER,0);
            if(app.uid==uid&&(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0
                    &&pm.checkSignatures(c.getPackageName(),LAUNCHER)==PackageManager.SIGNATURE_MATCH)return;
        }catch(PackageManager.NameNotFoundException ignored){}
        throw new SecurityException("Only the preinstalled Hecker Launcher may read the game library");
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        enforce();if(!URI.equals(uri)||selection!=null||selectionArgs!=null||sortOrder!=null
                ||projection!=null&&!(projection.length==1&&"package".equals(projection[0])))throw new IllegalArgumentException("Read-only game library");
        MatrixCursor result=new MatrixCursor(new String[]{"package"});long identity=Binder.clearCallingIdentity();
        try{
            Context c=getContext();
            if(c!=null&&GamingPrefs.get(c).getBoolean("hide_from_launcher",true))
                for(GamingCatalog.App app:GamingCatalog.load(c,true))result.addRow(new Object[]{app.info.packageName});
            if(c!=null)result.setNotificationUri(c.getContentResolver(),URI);return result;
        }finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public String getType(Uri uri){if(!URI.equals(uri))throw new IllegalArgumentException("Unknown library URI");return "vnd.android.cursor.dir/vnd.hecker.games";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only library");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read-only library");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only library");}
    @Override public void shutdown(){Context c=getContext();if(c!=null){if(preferences!=null)GamingPrefs.get(c).unregisterOnSharedPreferenceChangeListener(preferences);if(packages!=null)c.unregisterReceiver(packages);}super.shutdown();}
}
