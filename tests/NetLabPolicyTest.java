// SPDX-License-Identifier: Apache-2.0
import org.hecker.netlab.NetLabPolicy;
import org.hecker.netlab.NetLabAddressPlan;
import org.hecker.netlab.NetLabAccess;
import java.io.IOException;
import java.util.*;

/** Runs the production ownership/rollback code, with failing native and journal boundaries. */
public final class NetLabPolicyTest {
    static final String A="02:11:22:33:44:55", B="04:11:22:33:44:66", EXTERNAL="06:11:22:33:44:77";
    static int checks;
    static void check(boolean value) { checks++; if(!value)throw new AssertionError("check "+checks); }
    static final class Env implements NetLabPolicy.Backend, NetLabPolicy.Journal {
        Set<String> clients=new LinkedHashSet<>(Arrays.asList(A,B));
        Set<String> blocked=new LinkedHashSet<>(),allowed=new LinkedHashSet<>();
        List<NetLabPolicy.Rule> saved=new ArrayList<>();
        boolean active=true, support=true, timer=true, saveFail, writeFail, ambiguous, readFail;
        long wall=1_000_000, elapsed=1000; int writes;
        public boolean active(){return active;} public boolean canBlock(){return support;}
        public boolean canSchedule(){return timer;} public long wall(){return wall;} public long elapsed(){return elapsed;}
        public Set<String> clients(){return new LinkedHashSet<>(clients);}
        public Set<String> blocked()throws IOException{if(readFail)throw new IOException();return new LinkedHashSet<>(blocked);}
        public Set<String> allowed(){return new LinkedHashSet<>(allowed);}
        public void writeBlocked(Set<String> before,Set<String> values)throws IOException{
            writes++; if(writeFail)throw new IOException();Set<String> removed=new HashSet<>(before);removed.removeAll(values);
            Set<String> added=new HashSet<>(values);added.removeAll(before);blocked.removeAll(removed);blocked.addAll(added);
            if(ambiguous){ambiguous=false;throw new IOException();}
        }
        public void save(Collection<NetLabPolicy.Rule> rules)throws IOException{
            if(saveFail)throw new IOException();saved=new ArrayList<>();for(NetLabPolicy.Rule r:rules)saved.add(r.copy());
        }
        NetLabPolicy make(){return new NetLabPolicy(this,this,saved);}
    }
    interface Action{void run()throws Exception;}
    static void fails(String code,Action action)throws Exception{
        try{action.run();throw new AssertionError("accepted "+code);}catch(NetLabPolicy.Failure failure){check(code.equals(failure.code));}
    }
    public static void main(String[] args)throws Exception{
        Env e=new Env();e.blocked.add(EXTERNAL);NetLabPolicy p=e.make();
        p.pause(A,30_000);check(e.blocked.containsAll(Arrays.asList(A,EXTERNAL)));check(e.saved.size()==1);
        check(p.rules().get(0).state.equals("applying"));
        e.clients.remove(A);p.maintain();check(p.rules().get(0).state.equals("enforced"));
        e.elapsed+=30_000;p.maintain();check(p.rules().isEmpty());check(e.blocked.equals(Collections.singleton(EXTERNAL)));
        fails("not_owned",()->p.restore(EXTERNAL));check(e.blocked.contains(EXTERNAL));
        e.clients.add(A);e.blocked.add(A);fails("external_policy",()->p.pause(A,0));e.blocked.remove(A);
        e.allowed.add(A);fails("external_policy",()->p.pause(A,0));e.allowed.clear();
        fails("not_connected_client",()->p.pause("08:11:22:33:44:99",0));
        for(String invalid:new String[]{"ff:ff:ff:ff:ff:ff","01:00:5e:00:00:01","00:00:00:00:00:00","not-mac",null})
            fails("invalid_mac",()->p.pause(invalid,0));
        fails("invalid_duration",()->p.pause(A,-1));fails("invalid_duration",()->p.pause(A,NetLabPolicy.MAX_DURATION+1));
        e.support=false;fails("hal_unsupported",()->p.pause(A,0));e.support=true;
        e.active=false;fails("hotspot_off",()->p.pause(A,0));e.active=true;
        e.timer=false;fails("timer_unavailable",()->p.pause(A,30_000));e.timer=true;
        int before=e.writes;e.saveFail=true;fails("journal_failed",()->p.pause(A,0));check(e.writes==before);check(p.rules().isEmpty());e.saveFail=false;
        e.writeFail=true;fails("native_failed",()->p.pause(A,0));check(p.rules().isEmpty());e.writeFail=false;
        e.ambiguous=true;fails("native_failed",()->p.pause(A,0));check(!e.blocked.contains(A));check(e.blocked.contains(EXTERNAL));
        p.pause(A,0);fails("already_owned",()->p.pause(A,0));e.writeFail=true;fails("restore_pending",()->p.restore(A));check(!p.rules().isEmpty());e.writeFail=false;p.maintain();check(p.rules().isEmpty());
        p.pause(A,0);p.pause(B,0);e.active=false;p.maintain();check(p.rules().isEmpty());check(e.blocked.equals(Collections.singleton(EXTERNAL)));e.active=true;
        p.pause(A,60_000);NetLabPolicy recovered=e.make();recovered.restoreAll();check(recovered.rules().isEmpty());check(e.blocked.equals(Collections.singleton(EXTERNAL)));
        p.maintain();
        p.pause(A,60_000);e.wall+=60_000;e.elapsed-=500;p.maintain();check(p.rules().isEmpty());
        p.pause(A,0);e.elapsed+=15_000;p.maintain();check(p.rules().isEmpty());check(!e.blocked.contains(A));
        p.pause(A,0);e.blocked.remove(A);p.maintain();check(p.rules().isEmpty());
        p.pause(A,0);e.saveFail=true;fails("restore_pending",()->p.restore(A));
        check(!e.blocked.contains(A)&&p.rules().size()==1);e.saveFail=false;p.maintain();check(p.rules().isEmpty());
        p.pause(A,0);e.support=false;p.restoreAll();check(e.blocked.equals(Collections.singleton(EXTERNAL)));e.support=true;
        e.readFail=true;fails("native_unavailable",()->p.pause(A,0));e.readFail=false;check(p.rules().isEmpty());
        List<String> small=NetLabAddressPlan.targets(new byte[]{(byte)192,(byte)168,1,5},30);
        check(small.equals(Collections.singletonList("192.168.1.6")));
        List<String> large=NetLabAddressPlan.targets(new byte[]{10,5,7,18},16);
        check(large.size()==255&&large.stream().allMatch(s->s.startsWith("10.5.7."))&&!large.contains("10.5.7.18"));
        check(NetLabAddressPlan.targets(new byte[]{8,8,8,8},24).isEmpty());
        check(NetLabAddressPlan.targets(new byte[]{10,0,0,1},8).isEmpty());
        check(NetLabAddressPlan.targets(new byte[16],64).isEmpty());
        check(NetLabAddressPlan.targets(new byte[]{(byte)172,31,0,1},24).size()==253);
        check(NetLabAccess.allows(0,new String[]{"org.hecker.suite"},true,true));
        check(!NetLabAccess.allows(10,new String[]{"org.hecker.suite"},true,true));
        check(!NetLabAccess.allows(0,new String[]{"org.hecker.suite"},false,true));
        check(!NetLabAccess.allows(0,new String[]{"org.hecker.suite"},true,false));
        check(!NetLabAccess.allows(0,new String[]{"com.android.settings"},true,true));
        check(!NetLabAccess.allows(0,new String[]{"org.hecker.suite","another.app"},true,true));
        check(!NetLabAccess.allows(0,null,true,true));
        System.out.println("NetLab production ownership, failure recovery, expiry and bounded discovery: "+checks+" checks passed");
    }
}
