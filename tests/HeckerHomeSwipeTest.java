// SPDX-License-Identifier: Apache-2.0
import com.android.launcher3.hecker.HeckerHomeSwipe;

public final class HeckerHomeSwipeTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }

    public static void main(String[] args) {
        HeckerHomeSwipe gesture = new HeckerHomeSwipe();
        check(!gesture.up(0, 80, 200, 32));
        gesture.down(10, 10, 100);
        check(!gesture.move(12, 25, 130, 8, 32)); // Initial touch slop.
        check(gesture.move(12, 45, 150, 8, 32));  // Opens before finger-up.
        check(!gesture.move(12, 90, 200, 8, 32)); // One-shot despite more MOVEs.
        check(!gesture.up(12, 90, 250, 32));

        gesture.down(10, 10, 100);
        check(gesture.up(12, 70, 300, 32)); // Fast flick fallback.
        check(!gesture.up(12, 90, 350, 32));

        gesture.down(10, 10, 100);
        check(!gesture.move(100, 35, 150, 8, 32)); // Horizontal workspace scroll.
        check(!gesture.up(12, 90, 200, 32));

        gesture.down(10, 100, 100);
        check(!gesture.move(12, 10, 130, 8, 32)); // Upward app-drawer gesture.
        check(!gesture.up(12, 90, 200, 32));

        gesture.down(10, 10, 100);
        check(!gesture.up(12, 30, 300, 32)); // Short accidental touch.
        gesture.down(10, 10, 100);
        gesture.cancel();
        check(!gesture.up(12, 90, 300, 32));

        gesture.down(10, 10, 100);
        check(!gesture.move(12, 90, 1700, 8, 32)); // Long hold.
        check(!gesture.up(12, 90, 1800, 32));
        gesture.down(10, 10, 100);
        check(!gesture.up(12, 90, 90, 32)); // Invalid event order.

        gesture.down(10, 10, 100);
        check(!gesture.move(12, 22, 130, 8, 32));
        check(gesture.up(12, 90, 300, 32));
        System.out.println("Home downward swipe: early MOVE, fallback UP, one-shot, direction and cancellation PASS");
    }
}
