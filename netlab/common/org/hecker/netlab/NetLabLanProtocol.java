// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Bounded, versioned records from the fixed native helper; never a shell grammar. */
public final class NetLabLanProtocol {
    private NetLabLanProtocol() {}
    public static String ipv4(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}"))
            throw new IllegalArgumentException("invalid_ip");
        for (String part : value.split("\\.")) if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("invalid_ip");
        return value;
    }
    public static String mac(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5}")
                || (Integer.parseInt(value.substring(0, 2), 16) & 1) != 0
                || value.equals("00:00:00:00:00:00")) throw new IllegalArgumentException("invalid_mac");
        return value.toLowerCase(Locale.ROOT);
    }
    public static long number(String value, long maximum) {
        if (value == null || !value.matches("[0-9]{1,19}")) throw new IllegalArgumentException("invalid_number");
        try { long n = Long.parseLong(value); if (n > maximum) throw new IllegalArgumentException("invalid_number"); return n; }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("invalid_number"); }
    }
    public static String readLine(InputStream input) throws IOException {
        byte[] bytes = new byte[1024]; int count = 0, value;
        while ((value = input.read()) != -1) {
            if (value == '\n') return new String(bytes, 0, count, StandardCharsets.US_ASCII);
            if (value < 32 || value > 126 || count == bytes.length) throw new IOException("invalid_helper_output");
            bytes[count++] = (byte) value;
        }
        if (count != 0) throw new IOException("truncated_helper_output");
        return null;
    }
    public static String[] decode(String line) {
        if (line == null || line.length() > 1024) throw new IllegalArgumentException("invalid_helper_output");
        String[] w = line.split(" ", -1); boolean valid = false;
        switch (w[0]) {
            case "READY": valid = w.length == 2 && w[1].equals("1"); break;
            case "STARTED": case "STOPPING": valid = w.length == 1; break;
            case "LINK": if (w.length == 3) { mac(w[1]); mac(w[2]); valid = true; } break;
            case "PEER": if (w.length == 3) { ipv4(w[1]); mac(w[2]); valid = true; } break;
            case "RESTORED": if (w.length == 2) { ipv4(w[1]); valid = true; } break;
            case "STAT":
                if (w.length == 9 && (w[2].equals("BLOCK") || w[2].equals("LIMIT") || w[2].equals("PASS"))) {
                    ipv4(w[1]); number(w[3], 1_024_000); number(w[4], 1_024_000);
                    for (int i = 5; i < 9; i++) number(w[i], Long.MAX_VALUE); valid = true;
                } break;
            case "END": case "ERROR": valid = w.length == 2 && w[1].matches("[a-z0-9_]{1,80}"); break;
            default: break;
        }
        if (!valid) throw new IllegalArgumentException("invalid_helper_output"); return w;
    }
}
