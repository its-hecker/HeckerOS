// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.util.Locale;

/** Peer-supplied hints, never an authoritative OS fingerprint. IEEE vendors are resolved separately. */
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
        if (text.contains("macbook") || text.contains("imac") || text.contains("macmini") || text.contains("macpro") || text.matches(".*\\bmac[0-9]+,[0-9]+\\b.*")) return "macos";
        if (type.startsWith("_adb-tls-") || type.startsWith("_androidtvremote") || text.contains("android") || text.contains("pixel ") || text.contains("sm-") || text.contains("oneplus")) return "android";
        if (text.startsWith("desktop-") || text.startsWith("laptop-") || text.contains("windows")) return "windows";
        if (text.contains("ubuntu") || text.contains("debian") || text.contains("linux")) return "linux";
        if (type.startsWith("_workstation.")) return "computer";
        if (type.startsWith("_ipp.") || type.startsWith("_ipps.")) return "printer";
        if (type.startsWith("_airplay") || type.startsWith("_companion-link")) return "apple";
        if (type.startsWith("_googlecast")) return "media";
        return "unknown";
    }
    public static String brand(String name,String model,String manufacturer){
        String supplied=clean(manufacturer);if(!supplied.isEmpty())return supplied;
        String text=(clean(model)+" "+clean(name)).toLowerCase(Locale.ROOT);
        if(text.matches(".*\\b(iphone|ipad|ipod|macbook|imac|macmini|macpro|appletv|homepod).*" )||text.matches(".*\\bmac[0-9]+,[0-9]+\\b.*"))return "Apple";
        if(text.matches(".*\\b(pixel[ -]?[0-9]|chromecast|google[ -]home).*"))return "Google";
        if(text.contains("samsung")||text.matches(".*\\bsm-[a-z0-9]+.*"))return "Samsung";
        if(text.contains("oneplus"))return "OnePlus";
        if(text.contains("nothing phone"))return "Nothing";
        if(text.contains("xiaomi")||text.contains("redmi")||text.contains("poco "))return "Xiaomi";
        if(text.contains("huawei"))return "Huawei";
        if(text.contains("motorola"))return "Motorola";
        if(text.contains("realme"))return "Realme";
        if(text.contains("oppo"))return "OPPO";
        if(text.contains("vivo")||text.contains("iqoo"))return "vivo";
        return "";
    }
    private NetLabIdentity() {}
}
