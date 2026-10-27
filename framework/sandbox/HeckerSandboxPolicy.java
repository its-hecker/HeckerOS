// Copyright (C) 2025-2026 AxionOS
// Copyright (C) 2026 HeckerOS
// SPDX-License-Identifier: Apache-2.0
package com.android.server.am;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import com.android.internal.hecker.HeckerSandboxRules;
import org.json.JSONException;
import org.json.JSONObject;

/** Per-user policy consumed before process creation, using native Axion isolation hooks. */
final class HeckerSandboxPolicy {
    private HeckerSandboxPolicy() {}

    static int mask(Context context, ApplicationInfo app, int userId) {
        if (app == null || !UserHandle.isApp(app.uid)
                || (app.flags & (ApplicationInfo.FLAG_SYSTEM
                        | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return 0;
        try {
            String value = Settings.Secure.getStringForUser(context.getContentResolver(),
                    HeckerSandboxRules.SETTING, userId);
            if (value == null || value.isEmpty()) return 0;
            int result = new JSONObject(value).optInt(app.packageName, 0);
            if (!HeckerSandboxRules.valid(result) || result == 0) return 0;
            String[] packages = context.getPackageManager().getPackagesForUid(app.uid);
            if (packages == null || packages.length != 1
                    || !app.packageName.equals(packages[0])) return 0;
            return result;
        } catch (JSONException | RuntimeException unavailable) {
            Slog.w("HeckerSandbox", "Cannot read policy for user " + userId);
            return 0;
        }
    }

    static boolean isolate(Context context, ApplicationInfo app, int userId) {
        return (mask(context, app, userId) & HeckerSandboxRules.ISOLATE_DATA) != 0;
    }
}
