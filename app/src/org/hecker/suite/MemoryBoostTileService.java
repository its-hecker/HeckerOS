// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IHeckerMemoryService;
import android.os.ServiceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One-key cleanup uses the same protected, per-user AMS policy as the Suite. */
public final class MemoryBoostTileService extends TileService {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean busy,destroyed,ready;
    @Override public void onStartListening(){
        if(busy){update(true);return;}
        worker.execute(()->{
            boolean available=false;
            try{IHeckerMemoryService service=IHeckerMemoryService.Stub.asInterface(ServiceManager.getService("hecker_memory"));
                Bundle state=service==null?null:service.state();available=state!=null&&state.getBoolean("available");}
            catch(android.os.RemoteException|RuntimeException ignored){}
            final boolean value=available;
            main.post(()->{if(!destroyed){ready=value;update(busy);}});
        });
    }
    private void update(boolean running){
        Tile tile=getQsTile();if(tile==null)return;
        tile.setLabel(getString(R.string.memory_boost));
        tile.setState(running||!ready?Tile.STATE_UNAVAILABLE:Tile.STATE_INACTIVE);tile.updateTile();
    }
    @Override public void onClick(){unlockAndRun(()->{
        if(busy||destroyed||!ready)return;busy=true;update(true);
        worker.execute(()->{
            Bundle result=null;
            try{IHeckerMemoryService service=IHeckerMemoryService.Stub.asInterface(ServiceManager.getService("hecker_memory"));
                if(service!=null)result=service.boost();}
            catch(android.os.RemoteException|RuntimeException ignored){}
            final Bundle outcome=result;
            main.post(()->{
                if(destroyed)return;busy=false;update(false);
                String text=outcome==null||!outcome.getBoolean("available")?getString(R.string.memory_failed)
                        :outcome.getInt("processes")==0?getString(R.string.memory_none)
                        :getString(R.string.memory_done,outcome.getInt("apps"),outcome.getInt("processes"));
                Toast.makeText(this,text,Toast.LENGTH_LONG).show();
            });
        });
    });}
    @Override public void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
