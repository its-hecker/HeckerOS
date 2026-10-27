// SPDX-License-Identifier: Apache-2.0
import com.android.internal.hecker.HeckerHomeDoubleTap;

public final class InteractionPolicyTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
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
        System.out.println("Home double-tap/drag/widget/pinch/timeout regressions: PASS");
    }
}
