// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

public final class FaceIslandStateTest {
    private static int checks;
    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        FaceIslandState state = new FaceIslandState();
        state.environment(true, true, true, 10);
        state.running(true, true);
        expect(state.state() == FaceIslandState.SCANNING, "Face scanning on visible keyguard");
        expect(!state.authenticated(false, 10, true), "Fingerprint cannot signal face success");
        expect(!state.authenticated(true, 0, true), "Another user's face cannot signal success");
        expect(!state.authenticated(true, 10, false), "Strong-auth requirements still apply");
        state.failed(true);
        expect(state.state() == FaceIslandState.HIDDEN, "Failure clears feedback");
        state.running(true, true);
        expect(state.authenticated(true, 10, true), "Native current-user success");
        state.running(true, false);
        expect(state.state() == FaceIslandState.SUCCESS, "Sensor stopping preserves completion animation");
        expect(!state.authenticated(true, 10, true), "Duplicate callbacks do not replay success");
        state.expire();
        state.running(true, true);
        expect(state.state() == FaceIslandState.HIDDEN, "No replay after expiration in same session");
        state.environment(true, false, true, 10);
        expect(!state.authenticated(true, 10, true), "No success over an unlocked app");
        state.environment(true, true, true, 10);
        expect(state.authenticated(true, 10, true), "Next keyguard session can animate");
        state.environment(true, true, true, 11);
        expect(state.state() == FaceIslandState.HIDDEN, "User switch clears old success");
        expect(!state.authenticated(true, 10, true), "Old user cannot authenticate new session");
        state.environment(true, true, false, 11);
        state.running(true, true);
        expect(!state.authenticated(true, 11, true), "No face island on dozing or sleeping display");
        expect(state.state() == FaceIslandState.HIDDEN, "Sleeping remains hidden");
        state.environment(false, true, true, 11);
        state.running(true, true);
        expect(state.state() == FaceIslandState.HIDDEN, "Disabled preference applies immediately");
        expect(!state.authenticated(true, 11, true), "Disabled success remains hidden");
        System.out.println("Face island: " + checks + " callback, user, strong-auth and lifecycle checks passed");
    }
}
