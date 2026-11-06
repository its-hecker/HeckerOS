// SPDX-License-Identifier: Apache-2.0
import com.android.server.hecker.SensorRecoveryAccess;
import com.android.server.hecker.SensorRecoveryEngine;
import java.util.*;
import java.util.concurrent.Executor;

/** Run the production broker state machine without a phone, su, init or network. */
public class SensorRecoveryEngineTest {
    static final String P=SensorRecoveryEngine.PREFIX;
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static class Queue implements Executor {
        final List<Runnable> jobs=new ArrayList<>();
        public void execute(Runnable job){jobs.add(job);}
        void run(){jobs.remove(0).run();}
    }
    static class Platform implements SensorRecoveryEngine.Platform {
        final Map<String,String> values=new HashMap<>();
        final List<String> writes=new ArrayList<>();
        long clock=1000;int polls;String ready="ready",after="2222";
        boolean deny,stale,stopped,noReceipt;
        Platform(){values.put(P+"state.multihal20","running");}
        public String readiness(int user){return ready;}
        public String get(String name){
            check(name.startsWith(P));
            return values.getOrDefault(name,"");
        }
        public void set(String key,String value){
            check(key.equals(P+"request")||key.equals(P+"token"));
            if(deny)throw new SecurityException();
            writes.add(key+"="+value);values.put(key,value);
            if(key.equals(P+"request")&&!value.equals("clear")){
                values.put(P+"ack_token",stale?"999":values.get(P+"token"));
                values.put(P+"before","1111");values.put(P+"after","1111");
            }
        }
        public long now(){return clock;}
        public void pause(){
            polls++;clock+=1000;
            if(!noReceipt)values.put(P+"after",after);
            if(stopped)values.put(P+"state.multihal20","stopped");
        }
        long restartRequests(){return writes.stream().filter(s->s.startsWith(P+"request=")&&!s.endsWith("clear")).count();}
    }
    static SensorRecoveryEngine make(Platform p,Queue q){return new SensorRecoveryEngine(p,q);}
    public static void main(String[] args){
        Platform p=new Platform();Queue q=new Queue();SensorRecoveryEngine e=make(p,q);
        check(e.request(0).busy);check(e.request(0).status.equals("busy"));check(p.writes.isEmpty());
        check(e.state(10).status.equals("idle"));q.run();
        check(e.state(0).status.equals("service_restarted"));check(!e.state(0).busy);
        check(p.restartRequests()==1);check(p.writes.get(p.writes.size()-1).endsWith("request=clear"));
        check(e.request(0).status.equals("cooldown"));
        p.clock+=30000;check(e.request(0).busy);q.run();check(p.restartRequests()==2);

        for(String gate:new String[]{"blocked","wrong_user","unavailable","unsupported_device"}){
            p=new Platform();p.ready=gate;q=new Queue();e=make(p,q);
            check(e.request(0).status.equals(gate));check(q.jobs.isEmpty());check(p.writes.isEmpty());
        }
        p=new Platform();q=new Queue();e=make(p,q);e.request(0);p.ready="wrong_user";q.run();
        check(e.state(0).status.equals("wrong_user"));check(p.writes.isEmpty());
        p=new Platform();q=new Queue();e=make(p,q);e.request(0);p.values.clear();q.run();
        check(e.state(0).status.equals("unsupported_service"));check(p.writes.isEmpty());
        p=new Platform();p.values.clear();q=new Queue();e=make(p,q);
        check(e.request(0).status.equals("unsupported_service"));check(q.jobs.isEmpty());

        for(int fault=0;fault<5;fault++){
            p=new Platform();q=new Queue();e=make(p,q);
            if(fault==0)p.after="1111";
            if(fault==1)p.stale=true;
            if(fault==2)p.after="invalid";
            if(fault==3)p.stopped=true;
            if(fault==4)p.noReceipt=true;
            e.request(0);q.run();check(e.state(0).status.equals("restart_not_verified"));
            check(!e.state(0).busy);check(p.polls==20);check(p.restartRequests()==1);
        }
        p=new Platform();p.deny=true;q=new Queue();e=make(p,q);e.request(0);q.run();
        check(e.state(0).status.equals("unavailable"));check(!e.state(0).busy);
        p=new Platform();e=new SensorRecoveryEngine(p,job->{throw new IllegalStateException();});
        check(e.request(0).status.equals("unavailable"));check(p.writes.isEmpty());
        for(String action:new String[]{"multihal21","hal20"}) {
            p=new Platform();p.values.put(P+"state.multihal20","restarting");
            p.values.put(P+"state."+action,"running");
            q=new Queue();e=make(p,q);e.request(0);q.run();
            check(p.writes.contains(P+"request="+action));check(e.state(0).status.equals("service_restarted"));
            check(e.state(1).request==0);
        }

        check(SensorRecoveryAccess.suite(10123,0,10123,true));
        check(!SensorRecoveryAccess.suite(10123,0,10123,false));
        check(!SensorRecoveryAccess.suite(10124,0,10123,true));
        check(!SensorRecoveryAccess.suite(10123,10,10123,true));
        check(SensorRecoveryAccess.suite(1010123,10,1010123,true));
        for(int uid:new int[]{1000,1001,2001,10000,10123,1010123})check(!SensorRecoveryAccess.shell(uid));
        check(SensorRecoveryAccess.shell(0));check(SensorRecoveryAccess.shell(2000));
        System.out.println("Production sensor broker: "+checks+" checks PASS (authorization, foreground user, cooldown, bounded init receipt, stale/same/missing PID, timeout and failure).");
    }
}
