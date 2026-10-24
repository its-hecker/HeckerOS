// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.hecker;
import android.bluetooth.*;
import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;

/** One bounded foreground read. Closing the owner interrupts connection/read I/O. */
final class SoundcoreR50iReader implements Closeable {
    private volatile BluetoothSocket socket;
    private volatile boolean closed;
    private static final ScheduledExecutorService DEADLINE=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"HeckerEarbudDeadline");t.setDaemon(true);return t;});
    SoundcoreR50iCodec.State read(BluetoothDevice device) {
        ScheduledFuture<?> deadline=DEADLINE.schedule(this::close,7,TimeUnit.SECONDS);
        try {
            if(closed || !SoundcoreR50iCodec.matchesModel(device.getName())) return null;
            UUID selected=null;
            if(device.getUuids()!=null) for(var entry:device.getUuids()) {
                String uuid=entry.getUuid().toString();
                if(uuid.startsWith("0cf12d31-fac3-4553-bd80-d6832e7")){selected=entry.getUuid();break;}
                if(uuid.equals("00001101-0000-1000-8000-00805f9b34fb")) selected=entry.getUuid();
            }
            if(selected==null) return null;
            BluetoothSocket current=device.createRfcommSocketToServiceRecord(selected);
            socket=current;
            if(closed) return null;
            current.connect();
            current.getOutputStream().write(SoundcoreR50iCodec.requestState());current.getOutputStream().flush();
            byte[] buffer=new byte[512];SoundcoreR50iCodec parser=new SoundcoreR50iCodec();
            while(!closed) {int n=current.getInputStream().read(buffer);if(n<0)break;var state=parser.feed(buffer,n);if(state!=null)return state;}
        } catch(IOException | SecurityException unavailable) { }
        finally {deadline.cancel(false);close();}
        return null;
    }
    public void close(){closed=true;BluetoothSocket current=socket;socket=null;if(current!=null)try{current.close();}catch(IOException ignored){}}
}
