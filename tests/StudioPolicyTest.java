// SPDX-License-Identifier: Apache-2.0
import com.android.launcher3.hecker.StudioPolicy;

public final class StudioPolicyTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(StudioPolicy.clamp(Integer.MIN_VALUE, 3, 7) == 3);
        check(StudioPolicy.clamp(Integer.MAX_VALUE, 3, 7) == 7);
        check(StudioPolicy.clock("missing").equals("signature"));
        check(StudioPolicy.clock(null).equals("signature"));
        check(StudioPolicy.clock("caracal").equals("caracal"));
        check(StudioPolicy.badge(-1).isEmpty()); check(StudioPolicy.badge(0).isEmpty());
        check(StudioPolicy.badge(1).equals("1")); check(StudioPolicy.badge(99).equals("99"));
        check(StudioPolicy.badge(100).equals("99+")); check(StudioPolicy.badge(Integer.MAX_VALUE).equals("99+"));
        check(StudioPolicy.battery(-1, 100) < 0); check(StudioPolicy.battery(50, 0) < 0);
        check(StudioPolicy.battery(0, 100) == 0); check(StudioPolicy.battery(250, 100) == 1);
        check(StudioPolicy.battery(78, 100) == .78f);
        check(StudioPolicy.nextMinute(0) == 60000); check(StudioPolicy.nextMinute(59999) == 1);
        check(StudioPolicy.nextMinute(-1) == 1); check(StudioPolicy.nextMinute(60000) == 60000);
        check(StudioPolicy.matches("pixel cam", "Pixel Camera", "com.google.camera"));
        check(StudioPolicy.matches("  CAMERA  ", "Pixel Camera", "com.google.camera"));
        check(StudioPolicy.matches("", "Clock", "com.android.deskclock"));
        check(!StudioPolicy.matches("clock camera", "Clock", "com.android.deskclock"));
        check(StudioPolicy.matches("žinutės", "Žinutės", "org.example.messages"));
        System.out.println("Studio bounds, unavailable telemetry, badges, time boundaries and local multiword search: PASS");
    }
}
