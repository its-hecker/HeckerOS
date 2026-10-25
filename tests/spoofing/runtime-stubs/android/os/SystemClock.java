/* SPDX-License-Identifier: Apache-2.0 */
package android.os;
public final class SystemClock {
    public static long elapsedRealtime() { return System.nanoTime() / 1000000; }
}
