# SPDX-License-Identifier: Apache-2.0
"""Run the production launcher/provider bridge across live updates and caller boundaries."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class GameLibraryTests(unittest.TestCase):
    def test_live_library_cache_workspace_restore_and_reader_identity(self):
        sources = {
            'android/net/Uri.java': '''package android.net;public record Uri(String value){public static Uri parse(String s){return new Uri(s);}}''',
            'android/os/Looper.java': 'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
            'android/os/Handler.java': '''package android.os;public class Handler{
static final java.util.Queue<Runnable> pending=new java.util.concurrent.ConcurrentLinkedQueue<>();
public Handler(Looper l){}public boolean post(Runnable r){pending.add(r);return true;}
public static void drain(){Runnable r;while((r=pending.poll())!=null)r.run();}}''',
            'android/os/UserHandle.java': '''package android.os;public record UserHandle(int id){public static UserHandle getUserHandleForUid(int uid){return new UserHandle(uid/100000);}}''',
            'android/os/Process.java': 'package android.os;public class Process{public static int myUid(){return 100;}public static UserHandle myUserHandle(){return new UserHandle(0);}}',
            'android/os/Binder.java': '''package android.os;public class Binder{public static int uid=200,cleared,restored;
public static int getCallingUid(){return uid;}public static long clearCallingIdentity(){cleared++;return 5;}
public static void restoreCallingIdentity(long token){if(token!=5)throw new AssertionError();restored++;}}''',
            'android/database/ContentObserver.java': 'package android.database;public abstract class ContentObserver{public ContentObserver(android.os.Handler h){}public void onChange(boolean b){}}',
            'android/database/Cursor.java': '''package android.database;public interface Cursor extends AutoCloseable{boolean moveToNext();String getString(int i);void close();}''',
            'android/database/MatrixCursor.java': '''package android.database;public class MatrixCursor implements Cursor{
final java.util.List<Object[]> rows=new java.util.ArrayList<>();int position=-1;public MatrixCursor(String[] columns){}
public void addRow(Object[] row){rows.add(row);}public boolean moveToNext(){return ++position<rows.size();}
public String getString(int i){return (String)rows.get(position)[i];}public void close(){}
public void setNotificationUri(android.content.ContentResolver r,android.net.Uri u){} }''',
            'android/content/SharedPreferences.java': '''package android.content;public class SharedPreferences{
final java.util.Map<String,Object> data=new java.util.HashMap<>();final java.util.List<OnSharedPreferenceChangeListener> listeners=new java.util.ArrayList<>();
public interface OnSharedPreferenceChangeListener{void onSharedPreferenceChanged(SharedPreferences p,String k);}
@SuppressWarnings("unchecked") public java.util.Set<String> getStringSet(String k,java.util.Set<String> d){return (java.util.Set<String>)data.getOrDefault(k,d);}
public boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}public Editor edit(){return new Editor();}
public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}
public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.remove(l);}
public class Editor{final java.util.Map<String,Object> values=new java.util.HashMap<>();
public Editor putStringSet(String k,java.util.Set<String> v){values.put(k,new java.util.HashSet<>(v));return this;}
public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}
public void apply(){data.putAll(values);for(String k:values.keySet())for(var l:new java.util.ArrayList<>(listeners))l.onSharedPreferenceChanged(SharedPreferences.this,k);}}}''',
            'android/content/ComponentName.java': 'package android.content;public record ComponentName(String pkg,String cls){public String getPackageName(){return pkg;}}',
            'android/content/Intent.java': '''package android.content;public class Intent{
public static final String ACTION_USER_UNLOCKED="unlock",ACTION_PACKAGE_ADDED="added",ACTION_PACKAGE_REMOVED="removed",ACTION_PACKAGE_REPLACED="replaced",ACTION_PACKAGE_CHANGED="changed";}''',
            'android/content/IntentFilter.java': 'package android.content;public class IntentFilter{public IntentFilter(){}public IntentFilter(String a){}public void addAction(String a){}public void addDataScheme(String s){}}',
            'android/content/BroadcastReceiver.java': 'package android.content;public abstract class BroadcastReceiver{public abstract void onReceive(Context c,Intent i);}',
            'android/content/ContentValues.java': 'package android.content;public class ContentValues{}',
            'android/content/ContentProvider.java': '''package android.content;public abstract class ContentProvider{
public Context context;public Context getContext(){return context;}public abstract boolean onCreate();public void shutdown(){}
public abstract android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String sort);
public abstract String getType(android.net.Uri u);public abstract android.net.Uri insert(android.net.Uri u,ContentValues v);
public abstract int update(android.net.Uri u,ContentValues v,String s,String[] a);public abstract int delete(android.net.Uri u,String s,String[] a);}''',
            'android/content/ContentResolver.java': '''package android.content;public class ContentResolver{
public ContentProvider provider;public boolean available=true,registrationAllowed=false;public int notifications;
final java.util.List<android.database.ContentObserver> observers=new java.util.ArrayList<>();
public void registerContentObserver(android.net.Uri u,boolean d,android.database.ContentObserver o){if(!registrationAllowed)throw new IllegalStateException();observers.add(o);}
public android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String sort){if(!available)throw new IllegalStateException();return provider.query(u,p,s,a,sort);}
public void notifyChange(android.net.Uri u,Object o){notifications++;for(var observer:observers)observer.onChange(false);}}''',
            'android/content/pm/ApplicationInfo.java': '''package android.content.pm;public class ApplicationInfo{public static final int FLAG_SYSTEM=1,FLAG_UPDATED_SYSTEM_APP=128;public int uid=200,flags=1;public String packageName;}''',
            'android/content/pm/PackageManager.java': '''package android.content.pm;public class PackageManager{
public static final int SIGNATURE_MATCH=0;public boolean signed=true;public final ApplicationInfo launcher=new ApplicationInfo();
public ApplicationInfo getApplicationInfo(String p,int f)throws NameNotFoundException{return launcher;}
public int checkSignatures(String a,String b){return signed?0:-1;}public static class NameNotFoundException extends Exception{}}''',
            'android/content/Context.java': '''package android.content;public class Context{
public static final int MODE_PRIVATE=0,RECEIVER_NOT_EXPORTED=4;public boolean permission=true;public String name="org.hecker.suite";
public ContentResolver resolver=new ContentResolver();public final android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
public final java.util.List<BroadcastReceiver> receivers=new java.util.ArrayList<>();final java.util.Map<String,SharedPreferences> prefs=new java.util.HashMap<>();
public Context getApplicationContext(){return this;}public ContentResolver getContentResolver(){return resolver;}
public SharedPreferences getSharedPreferences(String n,int m){return prefs.computeIfAbsent(n,k->new SharedPreferences());}
public android.content.pm.PackageManager getPackageManager(){return pm;}public String getPackageName(){return name;}
public void enforceCallingPermission(String p,String message){if(!permission)throw new SecurityException();}
public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receivers.add(r);return null;}
public void unregisterReceiver(BroadcastReceiver r){receivers.remove(r);}public void broadcast(){for(var r:receivers)r.onReceive(this,new Intent());}}''',
            'android/view/View.java': '''package android.view;public class View{
public static final int VISIBLE=0,INVISIBLE=4,GONE=8;final android.content.Context context;int visibility;public int invalidations;
public View(android.content.Context c){context=c;}public android.content.Context getContext(){return context;}
public int getVisibility(){return visibility;}public void setVisibility(int value){visibility=value;}public void invalidate(){invalidations++;}}''',
            'org/hecker/suite/GamingPrefs.java': '''package org.hecker.suite;public class GamingPrefs{
public static final android.content.SharedPreferences prefs=new android.content.SharedPreferences();public static android.content.SharedPreferences get(android.content.Context c){return prefs;}}''',
            'org/hecker/suite/GamingCatalog.java': '''package org.hecker.suite;public class GamingCatalog{
public static final java.util.List<App> library=new java.util.ArrayList<>();public static class App{
public android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo();public App(String name){info.packageName=name;}}
public static java.util.List<App> load(android.content.Context c,boolean gamesOnly){if(!gamesOnly)throw new AssertionError();return library;}}''',
            'LibraryProbe.java': r'''import android.content.*;import android.os.*;import android.view.View;
import org.hecker.suite.*;import com.android.launcher3.hecker.HeckerGameLibrary;import java.util.*;
import java.util.concurrent.*;
public class LibraryProbe{
static void check(boolean ok){if(!ok)throw new AssertionError();}interface Attempt{void run();}
static void rejected(Attempt a,Class<? extends RuntimeException> type){try{a.run();throw new AssertionError("Accepted");}catch(RuntimeException e){if(!type.isInstance(e))throw e;}}
static ExecutorService io()throws Exception{var f=HeckerGameLibrary.class.getDeclaredField("IO");f.setAccessible(true);return (ExecutorService)f.get(null);}
static void settle()throws Exception{io().submit(()->{}).get(3,TimeUnit.SECONDS);Handler.drain();}
static Set<String> query(GamingLibraryProvider p){Set<String> out=new HashSet<>();try(var c=p.query(GamingLibraryProvider.URI,new String[]{"package"},null,null,null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
public static void main(String[] args)throws Exception{
Context suite=new Context(),launcher=new Context();launcher.name="com.android.launcher3";launcher.resolver=suite.resolver;
GamingLibraryProvider provider=new GamingLibraryProvider();provider.context=suite;suite.resolver.provider=provider;check(provider.onCreate());
GamingCatalog.library.add(new GamingCatalog.App("game.one"));check(query(provider).equals(Set.of("game.one")));
Binder.uid=300;rejected(()->query(provider),SecurityException.class);Binder.uid=1000200;rejected(()->query(provider),SecurityException.class);Binder.uid=200;
suite.permission=false;rejected(()->query(provider),SecurityException.class);suite.permission=true;
suite.pm.signed=false;rejected(()->query(provider),SecurityException.class);suite.pm.signed=true;
suite.pm.launcher.flags=0;rejected(()->query(provider),SecurityException.class);suite.pm.launcher.flags=128;check(query(provider).contains("game.one"));suite.pm.launcher.flags=1;
rejected(()->provider.query(GamingLibraryProvider.URI,new String[]{"label"},null,null,null),IllegalArgumentException.class);
rejected(()->provider.query(android.net.Uri.parse("content://org.hecker.suite.games/other"),null,null,null,null),IllegalArgumentException.class);
rejected(()->provider.query(GamingLibraryProvider.URI,null,"package=?",new String[]{"game.one"},null),IllegalArgumentException.class);
rejected(()->provider.delete(GamingLibraryProvider.URI,null,null),UnsupportedOperationException.class);
rejected(()->provider.insert(GamingLibraryProvider.URI,new ContentValues()),UnsupportedOperationException.class);
rejected(()->provider.update(GamingLibraryProvider.URI,new ContentValues(),null,null),UnsupportedOperationException.class);
check(Binder.cleared==Binder.restored);
launcher.getSharedPreferences("hecker_game_library",0).edit().putStringSet("packages",Set.of("game.cached","org.hecker.suite","com.android.launcher3","bad/name")).apply();
ComponentName game=new ComponentName("game.one","Main"),cached=new ComponentName("game.cached","Main");UserHandle owner=new UserHandle(0),work=new UserHandle(10);
check(HeckerGameLibrary.hidden(launcher,cached,owner));check(!HeckerGameLibrary.hidden(launcher,new ComponentName("org.hecker.suite","GameSpaceLauncher"),owner));
int[] updates={0};Runnable stop=HeckerGameLibrary.observe(launcher,()->updates[0]++);settle();
check(HeckerGameLibrary.hidden(launcher,game,owner));check(!HeckerGameLibrary.hidden(launcher,game,work));check(!HeckerGameLibrary.hidden(launcher,cached,owner));check(updates[0]==1);
View home=new View(launcher),folder=new View(launcher),gone=new View(launcher),workIcon=new View(launcher);gone.setVisibility(View.GONE);
HeckerGameLibrary.bind(home,game,owner);HeckerGameLibrary.bind(gone,game,owner);HeckerGameLibrary.bind(workIcon,game,work);HeckerGameLibrary.watch(folder);
check(home.getVisibility()==View.INVISIBLE&&gone.getVisibility()==View.GONE&&workIcon.getVisibility()==View.VISIBLE);
// Retry a failed observer registration after user unlock/package recovery.
suite.resolver.registrationAllowed=true;launcher.broadcast();settle();
GamingPrefs.prefs.edit().putBoolean("hide_from_launcher",false).apply();settle();
check(home.getVisibility()==View.VISIBLE&&!HeckerGameLibrary.hidden(launcher,game,owner));check(folder.invalidations>0&&updates[0]==2);
GamingPrefs.prefs.edit().putBoolean("hide_from_launcher",true).apply();settle();check(home.getVisibility()==View.INVISIBLE);
// Recycling a previously hidden workspace view must restore its visibility.
HeckerGameLibrary.bind(home,new ComponentName("regular.app","Main"),owner);check(home.getVisibility()==View.VISIBLE);
HeckerGameLibrary.bind(home,game,owner);check(home.getVisibility()==View.INVISIBLE);
suite.resolver.available=false;GamingCatalog.library.clear();GamingPrefs.prefs.edit().putBoolean("automatic",false).apply();settle();
check(HeckerGameLibrary.hidden(launcher,game,owner)&&home.getVisibility()==View.INVISIBLE);
suite.resolver.available=true;GamingPrefs.prefs.edit().putBoolean("automatic",true).apply();settle();
check(!HeckerGameLibrary.hidden(launcher,game,owner)&&home.getVisibility()==View.VISIBLE);
check(launcher.getSharedPreferences("hecker_game_library",0).getStringSet("packages",Set.of("bad")).isEmpty());
int previous=updates[0];stop.run();GamingCatalog.library.add(new GamingCatalog.App("game.two"));GamingPrefs.prefs.edit().putBoolean("automatic",false).apply();settle();check(updates[0]==previous);
int notifications=suite.resolver.notifications;suite.broadcast();check(suite.resolver.notifications>notifications);provider.shutdown();
check(suite.receivers.isEmpty());check(Binder.cleared==Binder.restored);io().shutdownNow();
System.out.println("Production game-library bridge: caller checks, live updates, opt-out/removal restore, offline cache, view recycling, observer retry and profile isolation PASS");
}}''',
        }
        with tempfile.TemporaryDirectory(prefix='hecker-game-library-') as temporary:
            work = Path(temporary)
            files = []
            for name, source in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
                files.append(str(path))
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(work / 'classes'),
                            *files, str(ROOT / 'app/src/org/hecker/suite/GamingLibraryProvider.java'),
                            str(ROOT / 'aosp-launcher/src/com/android/launcher3/hecker/HeckerGameLibrary.java')], check=True)
            subprocess.run(['java', '-cp', str(work / 'classes'), 'LibraryProbe'], check=True, timeout=15)
