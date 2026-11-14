// SPDX-License-Identifier: Apache-2.0
// Hidden platform declarations for the Suite SDK check.
package android.os;

public final class SystemProperties {
    public static String get(String key) { return ""; }
    public static String get(String key, String fallback) { return fallback; }
    public static boolean getBoolean(String key, boolean fallback) { return fallback; }
    public static int getInt(String key, int fallback) { return fallback; }
    public static void set(String key, String value) {}
}
