/* SPDX-License-Identifier: Apache-2.0 */
package android.security.spoof;

/** API surface for SDK-only UI compilation; the real implementation is checked separately. */
public final class HeckerSpoofConfig {
    public static final int GAMES=1, TARGETS=2, KEYBOX=3, PATCH=4, OPTIONS=5, MAX_BYTES=256*1024;
    public static String get(int kind) { throw new UnsupportedOperationException(); }
    public static void put(int kind, String value) { throw new UnsupportedOperationException(); }
}
