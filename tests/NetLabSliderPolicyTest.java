// SPDX-License-Identifier: Apache-2.0
import org.hecker.netlab.NetLabSliderPolicy;
import org.hecker.netlab.NetLabTrafficEvidence;
public final class NetLabSliderPolicyTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        usingSliders();usingTrafficEvidence();
        System.out.println("NetLab sliders and policy evidence: "+checks+" cancellation, no-op, pending/live/mixed and traffic-proof checks PASS");
    }
    private static void usingSliders(){
        for(int original=0;original<=12;original++)for(int value=0;value<=12;value++){
            check(NetLabSliderPolicy.action(original,value,true,true)==NetLabSliderPolicy.Action.NONE);
            check(NetLabSliderPolicy.action(original,value,false,false)==NetLabSliderPolicy.Action.NONE);
            if(original==value)check(NetLabSliderPolicy.action(original,value,true,false)==NetLabSliderPolicy.Action.NONE);
            else check(NetLabSliderPolicy.action(original,value,true,false)==(value==0?NetLabSliderPolicy.Action.BLOCK:value==12?NetLabSliderPolicy.Action.RESTORE:NetLabSliderPolicy.Action.LIMIT));
        }
        check(NetLabSliderPolicy.position("applying","PASS","BLOCK",0,0)==0);
        check(NetLabSliderPolicy.position("active_unverified","BLOCK","BLOCK",0,0)==0);
        check(NetLabSliderPolicy.position("applying","LIMIT","BLOCK",2048,0)==0);
        check(NetLabSliderPolicy.position("applying","BLOCK","LIMIT",0,512)==4);
        check(NetLabSliderPolicy.position("relaying","LIMIT","LIMIT",2048,2048)==6);
        check(NetLabSliderPolicy.position("relaying","LIMIT","LIMIT",0,0)<12); // Upload-only cap can still slide right to restore.
        check(NetLabSliderPolicy.position("observed","PASS","BLOCK",0,0)==12);
        check(NetLabSliderPolicy.position("restoring","BLOCK","BLOCK",0,0)==12);
        check(NetLabSliderPolicy.action(6,12,true,false)==NetLabSliderPolicy.Action.RESTORE); // Mixed group can restore.
        check(NetLabSliderPolicy.rate(1)==64&&NetLabSliderPolicy.rate(11)==65536&&NetLabSliderPolicy.rate(12)==0);
        check(NetLabSliderPolicy.action(0,13,true,false)==NetLabSliderPolicy.Action.NONE);
        check(NetLabSliderPolicy.canEdit(true,true,false,"active",99,100));
        check(!NetLabSliderPolicy.canEdit(true,true,false,"active",100,100));
        check(!NetLabSliderPolicy.canEdit(true,true,false,"restoring",99,100));
        check(!NetLabSliderPolicy.canEdit(true,true,true,"active",99,100));
        check(!NetLabSliderPolicy.canEdit(false,true,false,"active",99,100));
        check(!NetLabSliderPolicy.canEdit(true,false,false,"active",99,100));
    }
    private static void usingTrafficEvidence(){
        NetLabTrafficEvidence proof=new NetLabTrafficEvidence();
        check(!proof.observe("BLOCK",0,0,1000,2000,1000,0));
        check(!proof.observe("BLOCK",0,0,2000,3000,1000,0)); // Previously forwarded traffic cannot prove a cut.
        check(proof.observe("BLOCK",0,0,2000,3000,1000,1)); // Either direction can establish drops.
        check(proof.observe("BLOCK",0,0,2000,3000,1000,1));
        check(!proof.observe("LIMIT",128,512,2000,3000,1000,1));
        check(!proof.observe("LIMIT",128,512,2000,3000,2000,2)); // Drops alone do not prove a working relay.
        check(proof.observe("LIMIT",128,512,2001,3000,2000,2));
        check(!proof.observe("LIMIT",128,1024,2001,3000,2000,2)); // Rate changes start a new proof window.
        check(proof.observe("LIMIT",128,1024,2001,3001,2000,2));
        check(!proof.observe("PASS",0,0,3000,4000,2000,2));
        check(!proof.observe("PASS",0,0,4000,5000,2000,2));
        check(!proof.observe("BLOCK",0,0,4000,5000,2000,2));
        check(proof.observe("BLOCK",0,0,4000,5000,2000,3));
        check(!proof.observe("BLOCK",0,0,0,0,0,0)); // Counter reset must not retain proof.
        check(!proof.observe("BLOCK",0,0,Long.MAX_VALUE,Long.MAX_VALUE,0,0));
        check(proof.observe("BLOCK",0,0,Long.MAX_VALUE,Long.MAX_VALUE,1,0));
    }
}
