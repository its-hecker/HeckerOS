// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.Process;

/** Hide Pixel Quickstep's gesture handle without changing navigation insets. */
final class GestureNavigationController {
    private static final String PILL_OVERLAY = "org.hecker.overlay.launcher.hidepill";
    private static final String PILL_TARGET = "com.google.android.apps.nexuslauncher";
    // Older builds also zeroed taskbar_stashed_size; never enable that override again.
    private static final String LEGACY_SPACE_OVERLAY = "org.hecker.overlay.framework.navspace";
    private static final String LEGACY_SPACE_TARGET = "android";

    static boolean available(Context context) {
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            if (manager == null) return false;
            OverlayInfo pill = manager.getOverlayInfo(PILL_OVERLAY, Process.myUserHandle());
            return pill != null && PILL_TARGET.equals(pill.getTargetPackageName());
        } catch (RuntimeException unavailable) { return false; }
    }

    static boolean enabled(Context context) {
        if (!available(context)) return false;
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            return manager.getOverlayInfo(PILL_OVERLAY, Process.myUserHandle()).isEnabled();
        } catch (RuntimeException unavailable) { return false; }
    }

    /** Restore the default framework inset for users migrating from older builds. */
    static boolean restoreLegacySpacing(Context context) {
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            if (manager == null) return false;
            OverlayInfo legacy = manager.getOverlayInfo(LEGACY_SPACE_OVERLAY, Process.myUserHandle());
            if (legacy == null) return true;
            if (!LEGACY_SPACE_TARGET.equals(legacy.getTargetPackageName())) return false;
            if (!legacy.isEnabled()) return true;
            OverlayManagerTransaction.Builder transaction = new OverlayManagerTransaction.Builder();
            transaction.setEnabled(new OverlayIdentifier(LEGACY_SPACE_OVERLAY), false,
                    Process.myUserHandle().getIdentifier());
            manager.commit(transaction.build());
            OverlayInfo updated = manager.getOverlayInfo(LEGACY_SPACE_OVERLAY, Process.myUserHandle());
            return updated == null || !updated.isEnabled();
        } catch (RuntimeException unavailable) { return false; }
    }

    static boolean apply(Context context, boolean hidePill) {
        if (!available(context)) return false;
        try {
            OverlayManager manager = context.getSystemService(OverlayManager.class);
            OverlayInfo legacy = manager.getOverlayInfo(LEGACY_SPACE_OVERLAY, Process.myUserHandle());
            if (legacy != null && !LEGACY_SPACE_TARGET.equals(legacy.getTargetPackageName())) {
                return false;
            }
            OverlayManagerTransaction.Builder transaction = new OverlayManagerTransaction.Builder();
            transaction.setEnabled(new OverlayIdentifier(PILL_OVERLAY), hidePill,
                    Process.myUserHandle().getIdentifier());
            // If an older build left the inset override installed, disable it atomically.
            if (legacy != null && legacy.isEnabled()) {
                transaction.setEnabled(new OverlayIdentifier(LEGACY_SPACE_OVERLAY), false,
                        Process.myUserHandle().getIdentifier());
            }
            manager.commit(transaction.build());
            return enabled(context) == hidePill && restoreLegacySpacing(context);
        } catch (RuntimeException unavailable) { return false; }
    }

    private GestureNavigationController() {}
}
