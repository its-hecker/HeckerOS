// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.Method;

/** Binder death and a five-second lease let the platform restore settings if this app dies. */
final class GamingSession {
    private final IBinder token=new Binder();
    private Object remote;private Method update,end;
    Bundle update(Context c,int task){
        Bundle unavailable=new Bundle();unavailable.putInt("cpu",-1);unavailable.putInt("gpu",-1);unavailable.putInt("ram",-1);unavailable.putInt("bypass",-1);
        try{
            if(remote==null){
                IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"hecker_game");
                if(service==null)return unavailable;
                remote=Class.forName("android.os.IHeckerGameService$Stub").getMethod("asInterface",IBinder.class).invoke(null,service);
                Class<?> contract=Class.forName("android.os.IHeckerGameService");
                update=contract.getMethod("update",IBinder.class,int.class,int.class);end=contract.getMethod("end",IBinder.class);
            }
            android.content.SharedPreferences prefs=GamingPrefs.get(c);
            int flags=(prefs.getBoolean("block_navigation",false)?1:0)|(prefs.getBoolean("block_edge",false)?2:0)|
                    (prefs.getBoolean("block_assistant",false)?4:0)|(prefs.getBoolean("block_brightness",false)?8:0)|(prefs.getBoolean("bypass",false)?16:0);
            return (Bundle)update.invoke(remote,token,task,flags);
        }catch(ReflectiveOperationException|RuntimeException e){remote=null;return unavailable;}
    }
    void end(){try{if(remote!=null)end.invoke(remote,token);}catch(ReflectiveOperationException|RuntimeException ignored){}finally{remote=null;}}
}
