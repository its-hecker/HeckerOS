// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;

/** Kernel counters are parsed independently of Android for regression tests. */
public final class GameTelemetry {
    private long lastTotal=-1,lastIdle;
    public int cpu(String line){
        try{
            String[] fields=line.trim().split("\\s+");if(fields.length<5||!fields[0].equals("cpu"))return -1;
            long total=0,idle=Long.parseLong(fields[4]);
            for(int i=1;i<Math.min(fields.length,9);i++){long n=Long.parseLong(fields[i]);if(n<0)return -1;total+=n;}
            if(fields.length>5)idle+=Long.parseLong(fields[5]);
            long dt=total-lastTotal,di=idle-lastIdle;boolean first=lastTotal<0;lastTotal=total;lastIdle=idle;
            return first||dt<=0||di<0||di>dt?-1:(int)Math.round((dt-di)*100d/dt);
        }catch(RuntimeException e){return -1;}
    }
    public static int gpu(String line){
        try{String[] values=line.trim().split("\\s+");if(values.length!=2)return -1;
            long busy=Long.parseLong(values[0]),total=Long.parseLong(values[1]);
            return total<=0||busy<0||busy>total?-1:(int)Math.round(busy*100d/total);
        }catch(RuntimeException e){return -1;}
    }
    public static int ram(long total,long available){return total<=0||available<0||available>total?-1:(int)Math.round((total-available)*100d/total);}
    public void reset(){lastTotal=-1;lastIdle=0;}
}
