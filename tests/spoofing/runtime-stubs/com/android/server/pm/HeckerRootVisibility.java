/* SPDX-License-Identifier: Apache-2.0 */
package com.android.server.pm;
public final class HeckerRootVisibility {
    public static java.util.Map<String,Integer> flags = new java.util.HashMap<>();
    public static int getCompatibilityFlags(String name,int user) {
        return user == 0 ? flags.getOrDefault(name,0) : 0;
    }
}
