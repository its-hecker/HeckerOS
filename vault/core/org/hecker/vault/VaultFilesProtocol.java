// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

/** Shared by the private controller and its system-server broker. No arbitrary path API. */
public final class VaultFilesProtocol {
    public static final String SERVICE = "hecker_vault_files";
    public static final String DESCRIPTOR = "org.hecker.vault.files.v1";
    public static final int CALL = 1;
    public static final int CAPTURE = 0x40000000, RESTORE = 0x20000000, RECOVER = 0x10000000;
    public static boolean packageName(String text) {
        return text != null && text.length() <= 255 && text.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
    }
    public static boolean session(String text) {
        return text != null && text.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    private VaultFilesProtocol() {}
}
