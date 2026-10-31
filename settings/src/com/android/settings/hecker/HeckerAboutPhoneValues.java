// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

/** Formatting of reported hardware, without substituting a fabricated device configuration. */
public final class HeckerAboutPhoneValues {
    public static String processor(String model, String hardware) {
        String value = model == null || model.isBlank() || "unknown".equalsIgnoreCase(model)
                ? hardware : model;
        if (value == null || value.isBlank()) return "—";
        return value.matches("(?i)(SM8150|msmnile|(?:Qualcomm\\s+)?Snapdragon\\s+855)")
                ? "Snapdragon 855" : value;
    }
    private HeckerAboutPhoneValues() {}
}
