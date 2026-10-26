// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

/** A previous policy's cumulative traffic cannot establish that the new policy is working. */
public final class NetLabTrafficEvidence {
    private String policy="";
    private long[] baseline=new long[4],last=new long[4];
    private boolean confirmed;
    public boolean observe(String mode,int up,int down,long uploadForwarded,long downloadForwarded,long uploadDropped,long downloadDropped){
        String next=mode+":"+up+":"+down;
        long[] current={uploadForwarded,downloadForwarded,uploadDropped,downloadDropped};
        boolean reset=!next.equals(policy);for(int i=0;i<4;i++)reset|=current[i]<last[i];
        last=current.clone();
        if(reset){policy=next;baseline=current.clone();confirmed=false;return false;}
        confirmed|="BLOCK".equals(mode)?current[2]>baseline[2]||current[3]>baseline[3]
                :"LIMIT".equals(mode)&&(current[0]>baseline[0]||current[1]>baseline[1]);
        return !"PASS".equals(mode)&&confirmed;
    }
}
