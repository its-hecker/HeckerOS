// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

/** Identity checks are independent: possession of a platform signature alone does not grant access. */
public final class NetLabAccess {
    private NetLabAccess() {}
    public static boolean allows(int user, String[] packages, boolean permission, boolean signature) {
        if (user != 0 || !permission || !signature || packages == null) return false;
        // A shared UID would give every package in it the controller, so refuse it.
        return packages.length == 1 && "org.hecker.suite".equals(packages[0]);
    }
}
