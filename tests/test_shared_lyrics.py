# SPDX-License-Identifier: Apache-2.0
"""Production shared provider exercised with deterministic media/HTTP Android facades."""
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]
SOURCES={
"android/app/ActivityManager.java":"""package android.app;public class ActivityManager{public static int user;public static int getCurrentUser(){return user;}}""",
"android/net/Uri.java":"""package android.net;public class Uri{public final String key;public Uri(String k){key=k;}}""",
"android/os/Looper.java":"package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}",
"android/os/Handler.java":"""package android.os;public class Handler{public static java.util.Queue<Runnable> work=new java.util.concurrent.ConcurrentLinkedQueue<>();public Handler(Looper l){}public boolean post(Runnable r){work.add(r);return true;}public static void drain(){Runnable r;while((r=work.poll())!=null)r.run();}}""",
"android/os/SystemClock.java":"package android.os;public class SystemClock{public static long now=100000;public static long elapsedRealtime(){return now;}}",
"android/os/UserHandle.java":"package android.os;public class UserHandle{public static final int USER_ALL=-1;public int id;public static UserHandle of(int i){UserHandle h=new UserHandle();h.id=i;return h;}}",
"android/database/ContentObserver.java":"package android.database;public abstract class ContentObserver{public ContentObserver(android.os.Handler h){}public void onChange(boolean b){}}",
"android/content/ContentResolver.java":"""package android.content;public class ContentResolver{
public static class Entry{public String key;public android.database.ContentObserver observer;Entry(String k,android.database.ContentObserver o){key=k;observer=o;}}
public java.util.List<Entry> observers=new java.util.ArrayList<>();
public void registerContentObserver(android.net.Uri u,boolean b,android.database.ContentObserver o,int user){observers.add(new Entry(u.key,o));}
public void unregisterContentObserver(android.database.ContentObserver o){observers.removeIf(e->e.observer==o);}
public void notifyKey(String k){for(Entry e:new java.util.ArrayList<>(observers))if(e.key.equals(k))e.observer.onChange(false);}
}""",
"android/content/Intent.java":"""package android.content;public class Intent{public static final String ACTION_USER_SWITCHED="switch";public String action;public Intent(String a){action=a;}}""",
"android/content/IntentFilter.java":"package android.content;public class IntentFilter{public IntentFilter(String a){}}",
"android/content/BroadcastReceiver.java":"package android.content;public abstract class BroadcastReceiver{public abstract void onReceive(Context c,Intent i);}",
"android/content/Context.java":"""package android.content;public class Context{
public static final int RECEIVER_NOT_EXPORTED=4;public int user;
public static ContentResolver resolver=new ContentResolver();
public static java.util.List<BroadcastReceiver> receivers=new java.util.ArrayList<>();
public Context getApplicationContext(){return this;}public ContentResolver getContentResolver(){return resolver;}
public Context createContextAsUser(android.os.UserHandle h,int f){Context c=new Context();c.user=h.id;return c;}
public <T>T getSystemService(Class<T> c){return c.cast(android.media.session.MediaSessionManager.forUser(user));}
public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,String p,android.os.Handler h,int flags){receivers.add(r);return null;}
public void unregisterReceiver(BroadcastReceiver r){receivers.remove(r);}
public static void switched(){for(BroadcastReceiver r:new java.util.ArrayList<>(receivers))r.onReceive(new Context(),new Intent(Intent.ACTION_USER_SWITCHED));}
}""",
"android/provider/Settings.java":"""package android.provider;public class Settings{public static class Secure{
public static java.util.Map<String,Integer> values=new java.util.HashMap<>();
public static android.net.Uri getUriFor(String k){return new android.net.Uri(k);}
public static int getIntForUser(android.content.ContentResolver r,String k,int d,int user){return values.getOrDefault(user+":"+k,d);}
public static void set(int user,String k,int v){values.put(user+":"+k,v);android.content.Context.resolver.notifyKey(k);}
}}""",
"android/media/MediaMetadata.java":"""package android.media;public class MediaMetadata{
public static final String METADATA_KEY_TITLE="title",METADATA_KEY_ARTIST="artist",METADATA_KEY_DURATION="duration";
public String title;public MediaMetadata(String t){title=t;}
public String getString(String k){return k.equals("title")?title:"Artist";}public long getLong(String k){return 60000;}
}""",
"android/media/session/PlaybackState.java":"""package android.media.session;public class PlaybackState{
public static final int STATE_PLAYING=3,STATE_PAUSED=2;public int state;public long position,updated=100000;public float speed=1;
public PlaybackState(int s,long p){state=s;position=p;}
public int getState(){return state;}public long getPosition(){return position;}public long getLastPositionUpdateTime(){return updated;}public float getPlaybackSpeed(){return speed;}
}""",
"android/media/session/MediaController.java":"""package android.media.session;public class MediaController{
public String token;public android.media.MediaMetadata metadata;public PlaybackState state=new PlaybackState(3,0);
public java.util.List<Callback> callbacks=new java.util.ArrayList<>();
public MediaController(String t,String title){token=t;metadata=new android.media.MediaMetadata(title);}
public String getSessionToken(){return token;}public String getPackageName(){return "test.player";}
public android.media.MediaMetadata getMetadata(){return metadata;}public PlaybackState getPlaybackState(){return state;}
public void registerCallback(Callback c,android.os.Handler h){callbacks.add(c);}public void unregisterCallback(Callback c){callbacks.remove(c);}
public static class Callback{public void onMetadataChanged(android.media.MediaMetadata m){}public void onPlaybackStateChanged(PlaybackState s){}public void onSessionDestroyed(){}}
public void seek(long p,int s){state=new PlaybackState(s,p);for(Callback c:new java.util.ArrayList<>(callbacks))c.onPlaybackStateChanged(state);}
public void track(String t){metadata=new android.media.MediaMetadata(t);for(Callback c:new java.util.ArrayList<>(callbacks))c.onMetadataChanged(metadata);}
}""",
"android/media/session/MediaSessionManager.java":"""package android.media.session;public class MediaSessionManager{
public static java.util.Map<Integer,MediaSessionManager> managers=new java.util.HashMap<>();
public static MediaSessionManager forUser(int u){return managers.computeIfAbsent(u,k->new MediaSessionManager());}
public java.util.List<MediaController> controllers=new java.util.ArrayList<>();
public java.util.Set<OnActiveSessionsChangedListener> listeners=new java.util.HashSet<>();
public interface OnActiveSessionsChangedListener{void onActiveSessionsChanged(java.util.List<MediaController> a);}
public void addOnActiveSessionsChangedListener(OnActiveSessionsChangedListener l,Object n,android.os.Handler h){listeners.add(l);}
public void removeOnActiveSessionsChangedListener(OnActiveSessionsChangedListener l){listeners.remove(l);}
public java.util.List<MediaController> getActiveSessions(Object n){return controllers;}
}""",
"android/util/LruCache.java":"""package android.util;public class LruCache<K,V>{
public java.util.LinkedHashMap<K,V> cache=new java.util.LinkedHashMap<>();final int max;
public LruCache(int n){max=n;}public V get(K k){return cache.get(k);}public V put(K k,V v){V old=cache.put(k,v);while(cache.size()>max)cache.remove(cache.keySet().iterator().next());return old;}
}""",
"org/json/JSONArray.java":"""package org.json;public class JSONArray{
final String text;public JSONArray(String s){text=s;}public int length(){return text.equals("NONE")?0:1;}
public JSONObject getJSONObject(int i){return new JSONObject(text);}
}""",
"org/json/JSONObject.java":r"""package org.json;public class JSONObject{
final String text;public JSONObject(String s){text=s;}
public String optString(String k){if(k.equals("artistName"))return "Artist";if(k.equals("trackName"))return text;return "";}
public String optString(String k,String d){return "[00:00.00]"+text+" first\n[00:01.00]second\n[00:02.00]\n[00:03.00]last";}
public double optDouble(String k,double d){return 60;}public boolean optBoolean(String k){return false;}
}""",
"com/android/systemui/hecker/SharedLyricsProbe.java":r'''package com.android.systemui.hecker;
import android.content.*;import android.media.session.*;import android.provider.Settings;import android.os.*;
import java.net.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class SharedLyricsProbe{
static final AtomicInteger requests=new AtomicInteger();static volatile boolean slow;
static CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
static final List<LyricsController> clients=new ArrayList<>();
static LyricsController.Snapshot lock,status;
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
static void waitFor(java.util.function.BooleanSupplier done)throws Exception{
 long end=System.nanoTime()+3_000_000_000L;
 while(!done.getAsBoolean()){Handler.drain();if(System.nanoTime()>end)throw new AssertionError("Timed out");Thread.sleep(2);}
 Handler.drain();
}
static void settled()throws Exception{
 var field=LyricsController.class.getDeclaredField("shared");field.setAccessible(true);Object engine=field.get(null);
 var request=engine.getClass().getDeclaredField("request");request.setAccessible(true);
 var future=(java.util.concurrent.Future<?>)request.get(engine);
 if(future!=null)try{future.get(2,TimeUnit.SECONDS);}catch(CancellationException ignored){}
 Handler.drain();
}
static LyricsController client(Context c,int surface,boolean visible,java.util.function.Consumer<LyricsController.Snapshot> listener){
 LyricsController x=new LyricsController(c,surface,visible,listener);clients.add(x);return x;
}
static class Api extends HttpURLConnection{
 final String title;final boolean blocked;
 Api(URL u){super(u);String query=URLDecoder.decode(u.getQuery().substring(2),java.nio.charset.StandardCharsets.UTF_8);
  title=query.substring("Artist ".length());blocked=slow;requests.incrementAndGet();}
 public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}
 public InputStream getInputStream(){
  if(blocked){started.countDown();while(release.getCount()>0)try{release.await(5,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}}
  return new ByteArrayInputStream(title.getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
}
public static void main(String[]args)throws Exception{
 URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){
  protected URLConnection openConnection(URL u){return new Api(u);}
 }:null);
 Context context=new Context();MediaSessionManager manager=MediaSessionManager.forUser(0);
 MediaController player=new MediaController("u0","Song One");manager.controllers.add(player);
 try{
 LyricsController lc=client(context,LyricsController.LOCKSCREEN,true,s->lock=s);
 LyricsController sb=client(context,LyricsController.STATUS_BAR,true,s->status=s);
 check(requests.get()==0,"Opted-out subscribers fetched lyrics");
 Settings.Secure.set(0,LyricsController.STATUS_SETTING,1);
 waitFor(()->status!=null&&!status.lines.isEmpty());
 check(requests.get()==1,"First lookup not unique");check(lock.lines.isEmpty(),"Status toggle enabled lock lyrics");
 Settings.Secure.set(0,LyricsController.SETTING,1);Handler.drain();
 check(lock==status,"Surfaces do not share one synchronized snapshot");check(requests.get()==1,"Two displays duplicated fetching");
 lc.setVisible(false);check(manager.listeners.size()==1,"Unlocked subscriber lost active media");
 player.seek(1500,PlaybackState.STATE_PLAYING);
 check(StatusBarLyricsPolicy.frame(status,SystemClock.now).text.equals("second"),"Seek timing");
 player.seek(2200,PlaybackState.STATE_PLAYING);check(StatusBarLyricsPolicy.frame(status,SystemClock.now).text.isEmpty(),"Instrumental gap retained old line");
 player.seek(1500,PlaybackState.STATE_PAUSED);check(StatusBarLyricsPolicy.frame(status,SystemClock.now).text.isEmpty(),"Paused line visible");
 check(StatusBarLyricsPolicy.frame(status,SystemClock.now).delayMs<0,"Paused polling");
 player.seek(60000,PlaybackState.STATE_PLAYING);check(StatusBarLyricsPolicy.frame(status,SystemClock.now).text.isEmpty(),"Finished track visible");
 player.seek(0,PlaybackState.STATE_PLAYING);
 sb.setVisible(false);check(manager.listeners.isEmpty(),"Invisible-only service stayed subscribed");
 sb.setVisible(true);Handler.drain();check(requests.get()==1&&!status.lines.isEmpty(),"Cache not reused across unlock/visibility");
 Settings.Secure.set(0,LyricsController.STATUS_SETTING,0);check(status.lines.isEmpty(),"Opt-out did not clear status lyrics");
 check(manager.listeners.isEmpty(),"Lock off-screen kept fetching");
 Settings.Secure.set(0,LyricsController.STATUS_SETTING,1);Handler.drain();check(requests.get()==1,"Toggle caused duplicate positive lookup");
 slow=true;player.track("Stale Song");waitFor(()->started.getCount()==0);
 slow=false;player.track("Intermediate Song");player.track("Newest Song");release.countDown();
 waitFor(()->!status.lines.isEmpty()&&status.lines.get(0).text.startsWith("Newest Song"));
 check(!status.lines.get(0).text.startsWith("Stale"),"Stale request won");
 player.track("NONE");waitFor(()->requests.get()>=4);settled();waitFor(()->status.lines.isEmpty());Handler.drain();
 int noLyricsCount=requests.get();sb.setVisible(false);sb.setVisible(true);Handler.drain();
 check(requests.get()==noLyricsCount,"Negative cache refetched immediately");
 SystemClock.now+=300001;sb.setVisible(false);sb.setVisible(true);
 waitFor(()->requests.get()>noLyricsCount);
 MediaSessionManager second=MediaSessionManager.forUser(1);second.controllers.add(new MediaController("u1","Other User"));
 Settings.Secure.set(1,LyricsController.STATUS_SETTING,1);
 android.app.ActivityManager.user=1;Context.switched();
 waitFor(()->!status.lines.isEmpty()&&status.lines.get(0).text.startsWith("Other User"));
 check(lock.lines.isEmpty(),"Old lock opt-in leaked across users");
 check(manager.listeners.isEmpty()&&second.listeners.size()==1,"User media binding leaked");
 check(Context.receivers.size()==1&&Context.resolver.observers.size()==2,"Duplicated provider observers");
 lc.close();LyricsController.Snapshot before=status;player.seek(500,PlaybackState.STATE_PLAYING);check(status==before,"Detached old user updated shared state");
 for(LyricsController c:clients)c.close();Handler.drain();
 check(Context.receivers.isEmpty()&&Context.resolver.observers.isEmpty(),"Provider lifecycle leak");
 check(second.listeners.isEmpty(),"Session listener leak");
 System.out.println("Shared production LRCLIB engine: opt-in independence, one fetch/snapshot, unlocked continuation, seek/pause/gap/end, visibility/cache, stale skip, negative TTL, user isolation and disposal PASS");
 }finally{release.countDown();for(LyricsController c:clients)c.close();}
}
}'''
}
class SharedLyricsTests(unittest.TestCase):
    def test_shared_provider_and_status_timing(self):
        with tempfile.TemporaryDirectory(prefix="hecker-shared-lyrics-") as temporary:
            work=Path(temporary)
            for name,source in SOURCES.items():
                p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
            sources=list(work.rglob("*.java"))+list((ROOT/"framework/lyrics").glob("*.java"))
            subprocess.run(["java","-m","jdk.compiler/com.sun.tools.javac.Main","-d",str(work/"classes"),*map(str,sources)],check=True)
            subprocess.run(["java","-cp",str(work/"classes"),"com.android.systemui.hecker.SharedLyricsProbe"],check=True,timeout=25)
if __name__=="__main__":unittest.main()
