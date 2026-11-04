// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** A single leased session; daemon restores networking when this socket closes. */
final class KernelChannel implements AutoCloseable {
    static final class Failure extends IllegalArgumentException {
        final boolean cleanupRequired;
        Failure(JSONObject response) { super(response.optString("error", "Kernel operation failed")); cleanupRequired = response.optBoolean("cleanup_required"); }
    }
    private LocalSocket socket;
    JSONObject call(String command) throws Exception {
        try {
            if (socket == null) {
                socket = new LocalSocket();
                socket.connect(new LocalSocketAddress("heckervpnd", LocalSocketAddress.Namespace.RESERVED));
                socket.setSoTimeout(90000);
            }
            byte[] request = command.getBytes(StandardCharsets.UTF_8);
            if (request.length > VpnConfig.MAX_BYTES + 8) throw new IllegalArgumentException("Request is too large");
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(request.length); output.write(request); output.flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            int size = input.readInt();
            if (size < 2 || size > 8192) throw new Exception("Invalid kernel response");
            byte[] response = new byte[size]; input.readFully(response);
            JSONObject result = new JSONObject(new String(response, StandardCharsets.UTF_8));
            if (!result.optBoolean("ok")) throw new Failure(result);
            return result;
        } catch (Exception failure) { close(); throw failure; }
    }
    @Override public void close() {
        if (socket != null) { try { socket.close(); } catch (Exception ignored) {} socket = null; }
    }
}
