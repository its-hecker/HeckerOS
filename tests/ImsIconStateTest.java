// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

public final class ImsIconStateTest {
    private static void expect(int actual, int wanted, String message) {
        if (actual != wanted) throw new AssertionError(message + ": " + actual);
    }
    public static void main(String[] args) {
        ImsIconState first = new ImsIconState();
        expect(first.icon(true, true, false), ImsIconState.NONE, "No initial evidence");
        first.voice(true);
        expect(first.icon(true, true, false), ImsIconState.NONE, "Capabilities before registration");
        first.registered(ImsIconState.LTE);
        expect(first.icon(true, true, false), ImsIconState.LTE, "Voice available on LTE");
        expect(first.icon(false, true, false), ImsIconState.NONE, "VoLTE preference");
        expect(first.icon(true, true, true), ImsIconState.NONE, "Airplane must hide old LTE evidence");
        first.voice(false);
        expect(first.icon(true, true, false), ImsIconState.NONE, "SMS-only IMS is not voice");
        first.voice(true);
        first.registered(ImsIconState.WIFI);
        expect(first.icon(true, true, false), ImsIconState.WIFI, "Registered voice over Wi-Fi");
        expect(first.icon(true, true, true), ImsIconState.WIFI, "Wi-Fi calling in airplane mode");
        expect(first.icon(true, false, false), ImsIconState.NONE, "Hiding Wi-Fi must not invent LTE");
        first.clear();
        first.registered(ImsIconState.LTE);
        expect(first.icon(true, true, false), ImsIconState.NONE, "Reconnecting awaits voice evidence");
        first.voice(true);
        first.registered(ImsIconState.CROSS_SIM);
        expect(first.icon(true, true, false), ImsIconState.NONE, "Cross-SIM calling is not Wi-Fi calling");
        ImsIconState second = new ImsIconState();
        second.registered(ImsIconState.LTE);
        second.voice(true);
        first.registered(ImsIconState.WIFI);
        first.voice(true);
        first.clear();
        expect(first.icon(true, true, false), ImsIconState.NONE, "SIM removal/service loss clears state");
        expect(second.icon(true, true, false), ImsIconState.LTE, "Other SIM remains independent");
        System.out.println("IMS icon registration, voice, transport, preferences and dual-SIM policy: PASS");
    }
}
