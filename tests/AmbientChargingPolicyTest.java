/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
import com.android.systemui.hecker.AmbientChargingPolicy;

public final class AmbientChargingPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        AmbientChargingPolicy p = new AmbientChargingPolicy();
        check(!p.battery(true, false, true, 0), "Boot/sticky charging replay must not pulse");
        check(!p.pending(), "Sticky battery must not animate");
        p.battery(false, false, true, 10);
        check(p.battery(true, false, true, 20), "Off-screen plug must request native pulse");
        check(p.pending() && p.startedAt() == -1, "Do not consume intro while panel cannot refresh");
        check(p.visible(2000) && p.startedAt() == 2000, "Intro starts on visible pulse");
        check(!p.visible(2001), "Only one intro per connection");
        check(!p.battery(true, false, true, 2100) && p.startedAt() == 2000,
                "Telemetry broadcast must not restart intro");
        p.battery(false, false, true, 2200);
        check(!p.pending() && p.startedAt() == -1, "Unplug must cancel immediately");
        check(p.battery(true, false, true, 2300), "Reconnect starts a new session");
        p.battery(true, true, true, 2400);
        check(!p.visible(2401), "Power-button wake cancels pending animation");
        p.battery(false, true, true, 2500);
        check(!p.battery(true, true, true, 2600), "Plug while awake must not pulse doze");
        p.battery(false, false, true, 2700);
        p.battery(true, false, true, 2800);
        check(!p.visible(20000), "Pocket-suppressed intro must expire");
        p.battery(false, false, true, 20001); p.battery(true, false, true, 20002);
        check(p.expirePending(30003) && !p.pending(), "Suppressed pulse must not leave a blank panel");
        p.battery(false, false, false, 21000);
        check(p.battery(true, false, false, 22000) && !p.pending(),
                "Reduced-motion/info-only mode pulses without animation");
        check(Float.isNaN(AmbientChargingPolicy.percent(30, 0)), "Unknown scale is unknown");
        check(Math.abs(AmbientChargingPolicy.percent(7603, 10000) - 76.03f) < .001f,
                "Retain precision actually reported by the device");
        check(AmbientChargingPolicy.percent(76, 100) == 76f, "Never invent fractional readings");
        check(Double.isNaN(AmbientChargingPolicy.watts(Integer.MIN_VALUE, 4108)), "Missing current");
        check(Double.isNaN(AmbientChargingPolicy.watts(1000000, 0)), "Missing voltage");
        check(Math.abs(AmbientChargingPolicy.watts(-1000000, 4108) - 4.108) < .00001,
                "Battery-side power handles microamps, millivolts and current sign");
        check(Double.isNaN(AmbientChargingPolicy.watts(Integer.MAX_VALUE, 4108)), "Reject sentinels");
        System.out.println("Ambient charging sessions, reduced motion, unplug/wake, precision and power units: PASS");
    }
}
