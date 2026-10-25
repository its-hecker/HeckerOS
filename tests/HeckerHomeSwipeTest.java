// SPDX-License-Identifier: Apache-2.0
import com.android.launcher3.hecker.HeckerHomeSwipe;
public final class HeckerHomeSwipeTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        HeckerHomeSwipe gesture=new HeckerHomeSwipe();
        check(!gesture.up(0,80,200,48));
        gesture.down(10,10,100);check(gesture.up(12,70,300,48));
        check(!gesture.up(12,90,350,48));
        gesture.down(10,10,100);check(!gesture.up(100,70,300,48));
        gesture.down(10,100,100);check(!gesture.up(12,10,300,48));
        gesture.down(10,10,100);check(!gesture.up(12,40,300,48));
        gesture.down(10,10,100);gesture.cancel();check(!gesture.up(12,90,300,48));
        gesture.down(10,10,100);check(!gesture.up(12,90,1200,48));
        gesture.down(10,10,100);check(!gesture.up(12,90,90,48));
        gesture.down(10,10,100);gesture.move(100,12,8);check(!gesture.up(12,90,300,48));
        gesture.down(10,10,100);gesture.move(12,40,8);check(gesture.up(12,90,300,48));
        System.out.println("Home downward swipe: direction, distance, cancellation, timeout and one-shot handling PASS");
    }
}
