// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.Process;

/** One atomic opt-in for Pixel Quickstep's handle and its reserved framework inset. */
final class GestureNavigationController {
    private static final String[] PACKAGES = {
        "org.hecker.overlay.launcher.hidepill", "org.hecker.overlay.framework.navspace"
    };
    private static final String[] TARGETS = {"com.google.android.apps.nexuslauncher", "android"};

    static boolean available(Context context) {
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            if (manager == null) return false;
            for (int i = 0; i < PACKAGES.length; i++) {
                OverlayInfo info = manager.getOverlayInfo(PACKAGES[i], Process.myUserHandle());
                if (info == null || !TARGETS[i].equals(info.getTargetPackageName())) return false;
            }
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }

    static boolean enabled(Context context) {
        if (!available(context)) return false;
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            for (String name : PACKAGES) {
                if (!manager.getOverlayInfo(name, Process.myUserHandle()).isEnabled()) return false;
            }
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }

    static boolean apply(Context context, boolean enabled) {
        if (!available(context)) return false;
        try {
            OverlayManagerTransaction.Builder transaction = new OverlayManagerTransaction.Builder();
            for (String name : PACKAGES) {
                transaction.setEnabled(new OverlayIdentifier(name), enabled,
                        Process.myUserHandle().getIdentifier());
            }
            context.getSystemService(OverlayManager.class).commit(transaction.build());
            return enabled(context) == enabled;
        } catch (RuntimeException unavailable) { return false; }
    }

    private GestureNavigationController() {}
}
