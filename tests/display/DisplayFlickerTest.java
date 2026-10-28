// SPDX-License-Identifier: Apache-2.0
package com.android.server.display.mode;

public final class DisplayFlickerTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void locked(NativeBrightnessObserver observer, int rate) {
        NativeBrightnessObserver.Vote vote = observer.vote();
        check(vote != null && vote.min == rate && vote.max == rate,
                "Expected fixed native physical vote at " + rate);
        check(observer.switchingBlocked(), "Native switching vote must be present");
    }

    private static void released(NativeBrightnessObserver observer) {
        check(observer.vote() == null && !observer.switchingBlocked(),
                "Native brightness votes must relinquish control");
    }

    public static void main(String[] args) {
        for (float lux : new float[]{-1, 0, 10, 62, 63, 64, 500, 10_000}) {
            for (int raw : new int[]{1, 2, 10, 16, 50, 73, 74}) {
                NativeBrightnessObserver coral = new NativeBrightnessObserver(74, 90, 4);
                coral.sample(raw, lux);
                locked(coral, 90);
            }
        }

        // Reproduce the inherited policy gap, then verify the new display-only row.
        NativeBrightnessObserver inherited = new NativeBrightnessObserver(74, 90, 0);
        inherited.mLowAmbientBrightnessThresholds = new float[]{63};
        inherited.sample(20, 500);
        released(inherited);
        NativeBrightnessObserver coral = new NativeBrightnessObserver(74, 90, 4);
        coral.sample(20, 500);
        locked(coral, 90);
        coral.sample(79, 500);
        released(coral);

        for (int[] panel : new int[][]{{74, 90}, {16, 60}}) {
            int entry = panel[0], rate = panel[1];
            NativeBrightnessObserver observer = new NativeBrightnessObserver(entry, rate, 4);
            observer.sample(entry + 1, 1000);
            released(observer); // Hysteresis never expands the entry threshold.
            observer.sample(entry, 1000);
            locked(observer, rate);
            for (int i = 0; i < 300; i++) {
                observer.sample(entry + (i % 5), i % 2 == 0 ? 0 : 10_000);
                locked(observer, rate); // Brightness/lux noise cannot chatter.
            }
            observer.sample(entry + 5, 0);
            released(observer);
            observer.sample(entry + 2, 0);
            released(observer);
            observer.sample(entry - 1, 0);
            locked(observer, rate);

            // Smooth Display off and a developer-forced peak retain native priority.
            observer.onRefreshRateSettingChangedLocked(0, 60);
            released(observer);
            observer.onRefreshRateSettingChangedLocked(90, 90);
            released(observer);
            observer.sample(entry + 1, 0);
            observer.onRefreshRateSettingChangedLocked(0, 90);
            released(observer); // Old exit margin must not leak across setting changes.
            observer.sample(entry, 0);
            locked(observer, rate);

            observer.onLowPowerModeEnabledLocked(true);
            released(observer);
            observer.sample(entry + 1, 0);
            released(observer);
            observer.onLowPowerModeEnabledLocked(false);
            released(observer);
            observer.sample(entry, 0);
            locked(observer, rate);

            // Power-state changes clear the latch; native AOD voting remains unchanged.
            observer.setDefaultDisplayState(NativeBrightnessObserver.Display.STATE_DOZE);
            observer.setDefaultDisplayState(NativeBrightnessObserver.Display.STATE_ON);
            observer.sample(entry + 1, 0);
            released(observer);
            observer.sample(entry, 0);
            observer.setDefaultDisplayState(NativeBrightnessObserver.Display.STATE_OFF);
            observer.setDefaultDisplayState(NativeBrightnessObserver.Display.STATE_ON);
            observer.sample(entry + 1, 0);
            released(observer);

            // Unopted devices retain exact legacy boundary behavior.
            NativeBrightnessObserver legacy = new NativeBrightnessObserver(entry, rate, 0);
            legacy.sample(entry, 0);
            locked(legacy, rate);
            legacy.sample(entry + 1, 0);
            released(legacy);
        }

        LowZoneBrightnessHysteresis guard = new LowZoneBrightnessHysteresis(4 / 254f);
        float[] threshold = {NativeBrightnessObserver.brightness(74)};
        check(guard.update(.1f, 500, threshold, new float[]{-1}), "Display-only entry");
        check(!guard.update(Float.NaN, 0, threshold, new float[]{-1}), "Invalid read resets");
        check(!guard.update(NativeBrightnessObserver.brightness(75), 0, threshold,
                new float[]{-1}), "Invalid read cannot retain old margin");
        check(!guard.update(-1, 0, threshold, new float[]{-1}), "Negative brightness");
        check(!guard.update(Float.POSITIVE_INFINITY, 0, threshold, new float[]{-1}), "Infinity");
        check(!guard.update(.1f, 500, threshold, new float[]{63}), "Paired lux is retained");
        check(guard.update(.1f, 20, threshold, new float[]{63}), "Paired entry");
        check(!guard.update(.1f, 500, threshold, new float[]{63}), "Paired lux exit");
        check(!guard.update(.1f, 0, new float[]{-1}, new float[]{-1}), "Disabled row");
        check(guard.update(.9f, 20, new float[]{-1}, new float[]{63}), "Lux-only row");
        guard.reset();
        check(!guard.update(.1f, 0, threshold, new float[]{}), "Mismatched arrays reset");
        check(!guard.update(.1f, 0, null, new float[]{-1}), "Missing config resets");

        System.out.println("Native low-brightness refresh policy: " + checks + " checks PASS");
    }
}
