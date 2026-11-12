// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.Process;
import android.os.UserHandle;

/**
 * Cosmetic pill hiding only. Never collapse the framework's stashed taskbar inset:
 * Quickstep and the navigation input consumer rely on its native geometry.
 */
final class GestureNavigationController {
    private static final String HANDLE = "org.hecker.overlay.launcher.hidepill";
    private static final String HANDLE_TARGET = "com.google.android.apps.nexuslauncher";
    private static final String LEGACY_INSET = "org.hecker.overlay.framework.navspace";
    private static final String INSET_TARGET = "android";

    private static OverlayInfo info(OverlayManager manager, String name, UserHandle user) {
        return manager.getOverlayInfo(name, user);
    }

    static boolean available(Context context) {
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            return manager != null
                    && hasTarget(info(manager, HANDLE, Process.myUserHandle()), HANDLE_TARGET);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static boolean hasTarget(OverlayInfo info, String expected) {
        return info != null && expected.equals(info.getTargetPackageName());
    }

    static boolean enabled(Context context) {
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            if (manager == null) return false;
            OverlayInfo handle = info(manager, HANDLE, Process.myUserHandle());
            return hasTarget(handle, HANDLE_TARGET) && handle.isEnabled();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Restores pre-overlay navigation geometry without changing the user's handle choice. */
    static boolean restoreLegacyInset(Context context) {
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            if (manager == null) return false;
            UserHandle user = Process.myUserHandle();
            OverlayInfo legacy = info(manager, LEGACY_INSET, user);
            if (legacy == null) return true; // Future builds may omit the legacy overlay.
            if (!hasTarget(legacy, INSET_TARGET)) return false;
            if (!legacy.isEnabled()) return true;
            OverlayManagerTransaction.Builder transaction = new OverlayManagerTransaction.Builder();
            transaction.setEnabled(new OverlayIdentifier(LEGACY_INSET), false, user.getIdentifier());
            manager.commit(transaction.build());
            OverlayInfo updated = info(manager, LEGACY_INSET, user);
            return updated != null && !updated.isEnabled();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static boolean apply(Context context, boolean enabled) {
        if (!available(context)) return false;
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            UserHandle user = Process.myUserHandle();
            OverlayInfo legacy = info(manager, LEGACY_INSET, user);
            // Reject mismatched overlay targets instead of disabling unrelated packages.
            if (legacy != null && !hasTarget(legacy, INSET_TARGET)) return false;
            OverlayManagerTransaction.Builder transaction = new OverlayManagerTransaction.Builder();
            transaction.setEnabled(new OverlayIdentifier(HANDLE), enabled, user.getIdentifier());
            // Always turn OFF the old 0dp taskbar inset. Keep this in the same
            // transaction so a toggle never enables the unsafe pair again.
            if (legacy != null) {
                transaction.setEnabled(new OverlayIdentifier(LEGACY_INSET), false,
                        user.getIdentifier());
            }
            manager.commit(transaction.build());
            return enabled(context) == enabled && restoreLegacyInset(context);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private GestureNavigationController() {}
}
