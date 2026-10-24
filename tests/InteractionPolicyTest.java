// SPDX-License-Identifier: Apache-2.0
import com.android.internal.hecker.HeckerHomeDoubleTap;
import com.android.internal.hecker.HeckerMemoryRules;

public final class InteractionPolicyTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        for(int mask=0;mask<=15;mask++){
            check(HeckerMemoryRules.validated(mask)==mask);
            check(HeckerMemoryRules.clean(mask)==((mask&8)==0));
            check(!HeckerMemoryRules.shouldClean(mask,100,500,false,false));
            check(!HeckerMemoryRules.shouldClean(mask,950,500,true,false));
            check(HeckerMemoryRules.shouldClean(mask,950,900,false,false)==((mask&8)==0));
            check(HeckerMemoryRules.shouldClean(mask,600,500,false,true)==((mask&2)!=0&&(mask&8)==0));
            check(!HeckerMemoryRules.blockStart(mask,true));
            check(HeckerMemoryRules.blockStart(mask,false)==((mask&1)!=0));
        }
        for(int mask:new int[]{-1,-16,16,31,Integer.MAX_VALUE})check(HeckerMemoryRules.validated(mask)==0);
        check(HeckerMemoryRules.protectedPackage("com.android.systemui"));
        check(HeckerMemoryRules.protectedPackage("com.google.android.apps.nexuslauncher"));
        check(!HeckerMemoryRules.protectedPackage("example.music"));
        HeckerHomeDoubleTap tap=new HeckerHomeDoubleTap();
        tap.down(100,20,20,true);check(!tap.up(150,20,20,true,300,40));
        tap.down(200,22,22,true);check(tap.up(250,22,22,true,300,40));
        tap.down(300,20,20,true);check(!tap.up(350,20,20,true,300,40)); // Third tap cannot repeat sleep.
        tap.cancel();tap.down(400,20,20,true);tap.move(100,20,8,false);check(!tap.up(450,100,20,true,300,40));
        tap.down(500,20,20,true);check(!tap.up(550,20,20,true,300,40));
        tap.down(600,20,20,false);check(!tap.up(650,20,20,false,300,40)); // Icon/widget area.
        tap.down(700,20,20,true);check(!tap.up(750,20,20,true,300,40));
        tap.down(800,20,20,true);tap.move(20,20,8,true);check(!tap.up(850,20,20,true,300,40)); // Pinch.
        tap.down(900,20,20,true);check(!tap.up(1300,20,20,true,300,40)); // Long press.
        tap.down(1400,20,20,true);check(!tap.up(1450,20,20,true,300,40));
        tap.down(1800,20,20,true);check(!tap.up(1850,20,20,true,300,40)); // Expired tap.
        tap.down(1900,200,200,true);check(!tap.up(1950,200,200,true,300,40)); // Different spot.
        tap.cancel();tap.down(2000,20,20,true);check(!tap.up(1999,20,20,true,300,40));
        System.out.println("Memory flag combinations, resident precedence and home double-tap/drag/widget/pinch/timeout regressions: PASS");
    }
}
