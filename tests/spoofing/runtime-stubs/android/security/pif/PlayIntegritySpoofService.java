/* SPDX-License-Identifier: Apache-2.0 */
package android.security.pif;
public final class PlayIntegritySpoofService {
    private static final PlayIntegritySpoofService INSTANCE = new PlayIntegritySpoofService();
    public static PlayIntegritySpoofService getInstance() { return INSTANCE; }
    public static final java.util.Map<String,String> fields = new java.util.HashMap<>();
    public boolean isSpoofBuildEnabled() { return !fields.isEmpty(); }
    public java.util.Map<String,String> getBuildFields() { return fields; }
}
