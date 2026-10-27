#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Execute the actual media selector against controlled Android session callbacks."""
from pathlib import Path
import unittest
from test_motion_island import java_run
ROOT = Path(__file__).resolve().parents[1]

STUBS = {
'android/app/ActivityManager.java': '''package android.app;public class ActivityManager {public static int user;public static int getCurrentUser(){return user;}}''',
'android/os/Looper.java': '''package android.os;public class Looper {public static Looper getMainLooper(){return null;}}''',
'android/os/Handler.java': '''package android.os;public class Handler {public Handler(Looper l){}}''',
'android/os/SystemClock.java': '''package android.os;public class SystemClock {public static long elapsedRealtime(){return 10000;}}''',
'android/os/UserHandle.java': '''package android.os;public class UserHandle {public int id;public static UserHandle ALL=new UserHandle(),SYSTEM=new UserHandle();public static UserHandle of(int id){UserHandle u=new UserHandle();u.id=id;return u;}}''',
'android/content/BroadcastReceiver.java': '''package android.content;public abstract class BroadcastReceiver{public abstract void onReceive(Context c,Intent i);}''',
'android/content/IntentFilter.java': '''package android.content;public class IntentFilter{public IntentFilter(String s){}}''',
'android/content/Intent.java': '''package android.content;public class Intent{public static String ACTION_USER_SWITCHED="user";public Intent(String s){}public Intent setPackage(String p){return this;}public Intent putExtra(String k,Object o){return this;}}''',
'android/content/Context.java': '''package android.content;public class Context {
 public static int RECEIVER_NOT_EXPORTED=4;public android.media.session.MediaSessionManager manager=new android.media.session.MediaSessionManager();
 public BroadcastReceiver receiver;public int registrations;
 public <T>T getSystemService(Class<T> c){return c.cast(manager);}public java.util.concurrent.Executor getMainExecutor(){return Runnable::run;}
 public void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receiver=r;registrations++;}
 public void unregisterReceiver(BroadcastReceiver r){registrations--;}
 public String getPackageName(){return "com.android.systemui";}public void sendBroadcastAsUser(Intent i,android.os.UserHandle u){}
}''',
'android/graphics/Bitmap.java': '''package android.graphics;public class Bitmap{}''',
'android/media/AudioAttributes.java': '''package android.media;public class AudioAttributes{public int usage=1,content=2;public int getUsage(){return usage;}public int getContentType(){return content;}}''',
'android/media/AudioManager.java': '''package android.media;public class AudioManager{public void dispatchMediaKeyEvent(android.view.KeyEvent e){}}''',
'android/media/MediaMetadata.java': '''package android.media;public class MediaMetadata{
 public static String METADATA_KEY_TITLE="title",METADATA_KEY_ARTIST="artist",METADATA_KEY_ALBUM="album",METADATA_KEY_MEDIA_ID="id",METADATA_KEY_ALBUM_ART="art",METADATA_KEY_ART="art2",METADATA_KEY_DURATION="duration";
 public java.util.Map<String,String> values=new java.util.HashMap<>();public String getString(String k){return values.get(k);}public long getLong(String k){return 100000;}
 public java.util.Map<String,android.graphics.Bitmap> pictures=new java.util.HashMap<>();public android.graphics.Bitmap getBitmap(String k){return pictures.get(k);}public Description getDescription(){return new Description();}public static class Description{public android.graphics.Bitmap getIconBitmap(){return null;}}
}''',
'android/media/session/MediaSession.java': '''package android.media.session;public class MediaSession {public static class Token{}}''',
'android/media/session/PlaybackState.java': '''package android.media.session;public class PlaybackState{
 public static int STATE_PLAYING=3,STATE_PAUSED=2;public static long ACTION_SKIP_TO_NEXT=32,ACTION_SKIP_TO_PREVIOUS=16,ACTION_SEEK_TO=256;
 public int state=2;public int getState(){return state;}public long getPosition(){return 0;}public long getLastPositionUpdateTime(){return 0;}public float getPlaybackSpeed(){return 1;}public long getActions(){return 304;}public long getActiveQueueItemId(){return -1;}
}''',
'android/media/session/MediaController.java': '''package android.media.session;public class MediaController{
 public final MediaSession.Token token=new MediaSession.Token();public String pkg;public PlaybackState state=new PlaybackState();public android.media.MediaMetadata metadata=new android.media.MediaMetadata();public PlaybackInfo info=new PlaybackInfo();
 public java.util.List<Callback> callbacks=new java.util.ArrayList<>();public int plays,pauses;public MediaController(String p){pkg=p;}
 public MediaSession.Token getSessionToken(){return token;}public String getPackageName(){return pkg;}public PlaybackState getPlaybackState(){return state;}public android.media.MediaMetadata getMetadata(){return metadata;}public PlaybackInfo getPlaybackInfo(){return info;}
 public void registerCallback(Callback c,android.os.Handler h){callbacks.add(c);}public void unregisterCallback(Callback c){callbacks.remove(c);}
 public void playback(int value){state.state=value;for(Callback c:new java.util.ArrayList<>(callbacks))c.onPlaybackStateChanged(state);}
 public void attrs(int value){info.attrs.content=value;for(Callback c:new java.util.ArrayList<>(callbacks))c.onAudioInfoChanged(info);}
 public void destroy(){for(Callback c:new java.util.ArrayList<>(callbacks))c.onSessionDestroyed();}
 public TransportControls getTransportControls(){return new TransportControls();}public class TransportControls{public void play(){plays++;}public void pause(){pauses++;}public void skipToPrevious(){}public void skipToNext(){}public void seekTo(long p){}}
 public static class PlaybackInfo{public android.media.AudioAttributes attrs=new android.media.AudioAttributes();public android.media.AudioAttributes getAudioAttributes(){return attrs;}public int getCurrentVolume(){return 5;}public int getMaxVolume(){return 11;}}
 public static class Callback{public void onPlaybackStateChanged(PlaybackState s){}public void onMetadataChanged(android.media.MediaMetadata m){}public void onAudioInfoChanged(PlaybackInfo i){}public void onSessionDestroyed(){}}
}''',
'android/media/session/MediaSessionManager.java': '''package android.media.session;public class MediaSessionManager{
 public interface OnActiveSessionsChangedListener{void onActiveSessionsChanged(java.util.List<MediaController> s);}
 public java.util.List<MediaController> list=new java.util.ArrayList<>();public int requestedUser=-1;public java.util.List<OnActiveSessionsChangedListener> listeners=new java.util.ArrayList<>();public boolean switchDuringRead;
 public void addOnActiveSessionsChangedListener(Object n,android.os.UserHandle u,java.util.concurrent.Executor e,OnActiveSessionsChangedListener l){listeners.add(l);}
 public void removeOnActiveSessionsChangedListener(OnActiveSessionsChangedListener l){listeners.remove(l);}
 public void changed(){for(var l:new java.util.ArrayList<>(listeners))l.onActiveSessionsChanged(list);}
 public java.util.List<MediaController> getActiveSessionsForUser(Object n,android.os.UserHandle u){requestedUser=u.id;if(switchDuringRead){switchDuringRead=false;android.app.ActivityManager.user++;}return list;}
}''',
'android/view/KeyEvent.java': '''package android.view;public class KeyEvent{public static int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_MEDIA_PLAY=126;public KeyEvent(int a,int k){}}''',
'android/util/Log.java': '''package android.util;public class Log{public static int w(String a,String b,Throwable t){return 0;}}''',
}

class MusicSessionTests(unittest.TestCase):
    def test_actual_selector_tracks_paused_candidates_pins_user_and_cleans_up(self):
        sources = dict(STUBS)
        sources['com/android/systemui/hecker/SessionTest.java'] = r'''
package com.android.systemui.hecker;
import android.content.*;import android.media.session.*;import java.util.*;
public class SessionTest {
 static int checks;static HeckerQsMedia.Snapshot current;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  Context c=new Context();MediaController a=new MediaController("player.a"),b=new MediaController("player.b"),v=new MediaController("com.google.android.youtube");
  v.state.state=3;c.manager.list=Arrays.asList(v,a,b);android.app.ActivityManager.user=10;
  HeckerQsMedia m=new HeckerQsMedia(c,s->current=s,true,null);m.start();BroadcastReceiver userReceiver=c.receiver;
  check(current.active&&!current.playing&&current.token==a.token&&c.manager.requestedUser==10);
  check(a.callbacks.size()==1&&b.callbacks.size()==1&&v.callbacks.size()==1);
  b.playback(3);check(current.playing&&current.token==b.token); // No membership change.
  b.attrs(3);check(current.token==a.token&&!current.playing); // Music -> movie cannot linger.
  b.attrs(2);check(current.playing&&current.token==b.token);
  android.graphics.Bitmap firstCover=new android.graphics.Bitmap(),nextCover=new android.graphics.Bitmap();
  b.metadata.pictures.put(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART,firstCover);
  for(var callback:new ArrayList<>(b.callbacks))callback.onMetadataChanged(b.metadata);
  check(current.artwork==firstCover); // Capsule observer receives real session album art.
  b.metadata.pictures.put(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART,nextCover);
  for(var callback:new ArrayList<>(b.callbacks))callback.onMetadataChanged(b.metadata);
  check(current.artwork==nextCover); // Track/cover changes replace the thumbnail.

  check(m.snapshotFor(v.token).active==false&&m.snapshotFor(b.token).active);
  b.playback(2);a.playback(3);check(current.token==a.token);
  HeckerQsMedia pinned=new HeckerQsMedia(c,s->{},true,b.token);pinned.start();pinned.playPause();
  check(b.plays==1&&a.plays==0); // Expanded paused player cannot control a different app.
  pinned.stop();check(b.callbacks.size()==1);
  a.destroy();check(current.token==b.token&&a.callbacks.isEmpty());
  c.manager.list=List.of();c.manager.changed();
  check(!current.active&&b.callbacks.isEmpty()&&v.callbacks.isEmpty());
  c.manager.list=List.of(b);b.state.state=3;c.manager.switchDuringRead=true;
  c.manager.changed();check(!current.active&&b.callbacks.isEmpty());
  userReceiver.onReceive(c,new Intent("user"));check(current.token==b.token&&c.manager.requestedUser==11);
  m.stop();check(b.callbacks.isEmpty()&&c.manager.listeners.isEmpty()&&c.registrations==0);
  check(MusicSessionPolicy.eligible("unknown",1,2,false));
  check(!MusicSessionPolicy.eligible("unknown",1,0,false));
  check(MusicSessionPolicy.eligible("unknown",1,0,true));
  check(!MusicSessionPolicy.eligible("com.spotify.music",1,1,true));
  check(!MusicSessionPolicy.eligible("com.google.android.youtube",1,2,true));
  check(MusicSessionPolicy.eligible("com.google.android.apps.youtube.music",1,0,false));
  check(!MusicSessionPolicy.eligible("player",4,2,true));
  System.out.println("Music sessions: "+checks+" checks PASS");
 }
}'''
        java_run(sources, [ROOT/'framework/qs/HeckerQsMedia.java', ROOT/'framework/qs/MusicSessionPolicy.java'],
                 'com.android.systemui.hecker.SessionTest')
