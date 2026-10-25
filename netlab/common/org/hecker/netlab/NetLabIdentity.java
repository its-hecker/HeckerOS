// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.util.Locale;

/** Peer-supplied hints, never an authoritative OS fingerprint or a guessed MAC vendor. */
public final class NetLabIdentity {
    public static String clean(String value) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        return text.substring(0, Math.min(80, text.length()));
    }
    public static String kind(String name, String model, String service) {
        String text = (clean(model) + " " + clean(name)).trim().toLowerCase(Locale.ROOT);
        String type = service == null ? "" : service.toLowerCase(Locale.ROOT);
        if (text.contains("iphone") || text.contains("ipad") || text.contains("ipod")) return "ios";
        if (text.contains("macbook") || text.contains("imac") || text.contains("macmini") || text.contains("macpro")) return "macos";
        if (type.startsWith("_adb-tls-") || type.startsWith("_androidtvremote") || text.contains("android") || text.contains("pixel ") || text.contains("sm-") || text.contains("oneplus")) return "android";
        if (text.startsWith("desktop-") || text.startsWith("laptop-") || text.contains("windows")) return "windows";
        if (text.contains("ubuntu") || text.contains("debian") || text.contains("linux")) return "linux";
        if (type.startsWith("_airplay") || type.startsWith("_companion-link")) return "apple";
        if (type.startsWith("_googlecast")) return "media";
        return "unknown";
    }
    private NetLabIdentity() {}
}
