/* SPDX-License-Identifier: Apache-2.0 */
package android.os;
public final class SystemClock {
    public static long uptime;
    public static long uptimeMillis() { return uptime; }
    public static long elapsedRealtime() { return System.nanoTime() / 1000000; }
}
