// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.util.*;

/** Bounded, on-link RFC1918 discovery; never sweeps a public address or a whole /16. */
public final class NetLabAddressPlan {
    private NetLabAddressPlan() {}
    public static List<String> targets(byte[] ip, int prefix) {
        if (ip == null || ip.length != 4 || prefix < 16 || prefix > 30) return Collections.emptyList();
        long own = 0; for (byte b : ip) own = (own << 8) | (b & 255);
        boolean local = (own >>> 24) == 10 || (own >>> 20) == 0xac1 || (own >>> 16) == 0xc0a8;
        if (!local) return Collections.emptyList();
        long mask = (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        long network = own & mask, broadcast = network | (0xffffffffL ^ mask);
        // For larger LANs, inspect only the directly attached /24 containing this phone.
        long first = Math.max(network + 1, own & 0xffffff00L);
        long last = Math.min(broadcast - 1, (own & 0xffffff00L) + 255);
        List<String> result = new ArrayList<>();
        for (long n = first; n <= last; n++) if (n != own)
            result.add((n >>> 24) + "." + ((n >>> 16) & 255) + "." + ((n >>> 8) & 255) + "." + (n & 255));
        return result;
    }
}
