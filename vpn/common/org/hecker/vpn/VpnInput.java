// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Document contents and account numbers are independent of document-provider metadata. */
public final class VpnInput {
    public static String accountNumber(String text) {
        String number = text == null ? "" : text.replaceAll("[\\s\\p{Zs}-]", "");
        if (!number.matches("[0-9]{16}"))
            throw new IllegalArgumentException("Enter your 16-digit Mullvad account number");
        return number;
    }

    public static String profileName(String displayName) {
        String name = displayName == null ? "" : displayName.replaceFirst("(?i)\\.conf$", "").trim();
        name = name.replaceAll("\\p{Cntrl}", "");
        return name.isEmpty() ? "VPN profile" : name.substring(0, Math.min(80, name.length()));
    }

    public static String configuration(InputStream input) throws IOException {
        if (input == null) throw new IOException("Could not open the selected document");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (output.size() + size > VpnConfig.MAX_BYTES)
                throw new IllegalArgumentException("Choose a configuration no larger than 64 KB");
            output.write(buffer, 0, size);
        }
        byte[] bytes = output.toByteArray();
        Charset encoding = StandardCharsets.UTF_8;
        int offset = 0;
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            encoding = StandardCharsets.UTF_16LE; offset = 2;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            encoding = StandardCharsets.UTF_16BE; offset = 2;
        }
        try {
            String text = encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            VpnConfig.parse(text); // Report a config error before asking the user to name it.
            return text;
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Choose a text WireGuard .conf file");
        } finally { java.util.Arrays.fill(bytes, (byte) 0); }
    }
    private VpnInput() {}
}
