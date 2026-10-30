// SPDX-License-Identifier: Apache-2.0
import com.android.systemui.hecker.DoubleTapSleepGesture;

public final class DoubleTapSleepGestureTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static DoubleTapSleepGesture gesture() { return new DoubleTapSleepGesture(40, 300, 8, 48); }
    private static void first(DoubleTapSleepGesture g) {
        g.down(0, 20, 20); check(!g.up(45, 20, 20));
    }
    public static void main(String[] args) {
        DoubleTapSleepGesture g = gesture();
        first(g); g.down(140, 22, 21); check(g.up(185, 22, 21));
        check(!g.up(190, 22, 21)); // No duplicate sleep on repeated UP.
        g.down(220, 22, 21); check(!g.up(260, 22, 21)); // Third tap is not a second pair.
        g = gesture(); first(g); g.down(400, 20, 20); check(!g.up(445, 20, 20));
        g = gesture(); first(g); g.down(60, 20, 20); check(!g.up(95, 20, 20));
        g = gesture(); first(g); g.down(140, 100, 20); check(!g.up(185, 100, 20));
        g = gesture(); g.down(0, 20, 20); g.move(20, 50); check(!g.up(45, 20, 20));
        g.down(140, 20, 20); check(!g.up(185, 20, 20));
        g = gesture(); first(g); g.down(140, 20, 20); g.move(20, 50); check(!g.up(185, 20, 20));
        g = gesture(); first(g); g.down(140, 20, 20); check(!g.up(185, 40, 20));
        g = gesture(); first(g); g.down(140, 20, 20); check(!g.up(600, 20, 20));
        g = gesture(); first(g); g.reset(); g.down(140, 20, 20); check(!g.up(185, 20, 20));
        g = gesture(); first(g); g.down(140, 20, 20); g.reset(); check(!g.up(185, 20, 20));
        g = gesture(); g.down(100, 20, 20); check(!g.up(99, 20, 20));
        g = gesture(); first(g); g.down(140, 20, 20); g.down(160, 20, 20); check(!g.up(185, 20, 20));
        // Native min/max interval and movement bounds.
        g = gesture(); first(g); g.down(85, 20, 20); check(g.up(130, 28, 20));
        g = gesture(); first(g); g.down(345, 68, 20); check(g.up(390, 68, 20));
        System.out.println("Double-tap pairs, bounds, drags, long press, cancel and duplicate events: PASS");
    }
}
