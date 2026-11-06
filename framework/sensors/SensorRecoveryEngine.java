// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;

import java.util.concurrent.Executor;

/** Fixed init requests, bounded polling and per-user results. No su or arbitrary shell command. */
public final class SensorRecoveryEngine {
    public static final String PREFIX="sys.hecker.sensor_recovery.";
    private static final String[] ACTIONS={"multihal20","multihal21","hal20"};
    public interface Platform {
        String readiness(int user);
        String get(String property);
        void set(String property,String value);
        long now();
        void pause() throws InterruptedException;
    }
    public static final class State {
        public final String status;
        public final long request;
        public final boolean busy;
        public final int user;
        State(String status,long request,boolean busy,int user){
            this.status=status;this.request=request;this.busy=busy;this.user=user;
        }
    }
    private final Platform platform;
    private final Executor worker;
    private State state=new State("idle",0,false,-1);
    private long lastStart=Long.MIN_VALUE,sequence;
    public SensorRecoveryEngine(Platform platform,Executor worker){this.platform=platform;this.worker=worker;}
    public synchronized State state(int user){return state.user==user?state:new State("idle",0,false,user);}
    private static State reply(String status,int user){return new State(status,0,false,user);}
    private boolean isRunning(int target) {
        return "running".equals(platform.get(PREFIX + "state." + ACTIONS[target]));
    }
    public synchronized State request(int user){
        String readiness=platform.readiness(user);
        if(!"ready".equals(readiness))return reply(readiness,user);
        if(state.busy)return reply("busy",user);
        long now=platform.now();
        if(lastStart!=Long.MIN_VALUE&&now-lastStart<30000)return reply("cooldown",user);
        int target=-1;
        for(int i=0;i<ACTIONS.length;i++)if(isRunning(i)){target=i;break;}
        if(target<0)return reply("unsupported_service",user);
        long id=Math.max(++sequence,now+1);sequence=id;
        state=new State("running",id,true,user);lastStart=now;
        final int selected=target;
        try{worker.execute(()->recover(user,id,selected));}
        catch(RuntimeException rejected){state=new State("unavailable",id,false,user);}
        return state;
    }
    private void recover(int user,long id,int target){
        String outcome="restart_not_verified";
        boolean requested=false;
        try{
            String ready=platform.readiness(user);
            if(!"ready".equals(ready)){outcome=ready;return;}
            if(!isRunning(target)){outcome="unsupported_service";return;}
            platform.set(PREFIX+"token",Long.toString(id));
            platform.set(PREFIX+"request",ACTIONS[target]);requested=true;
            for(int n=0;n<20;n++){
                platform.pause();
                // Init alone reads its protected debug-PID properties and publishes a private receipt.
                long receipt=number(platform.get(PREFIX+"ack_token"));
                long before=number(platform.get(PREFIX+"before")),after=number(platform.get(PREFIX+"after"));
                if(receipt==id&&before>1&&after>1&&after!=before&&isRunning(target)){
                    outcome="service_restarted";break;
                }
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();outcome="restart_not_verified";}
        catch(RuntimeException denied){outcome="unavailable";}
        finally{
            if(requested)try{platform.set(PREFIX+"request","clear");}catch(RuntimeException ignored){}
            synchronized(this){state=new State(outcome,id,false,user);}
        }
    }
    private static long number(String value){try{return Long.parseLong(value);}catch(NumberFormatException ignored){return 0;}}
}
