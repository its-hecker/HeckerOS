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
        String stage = "connect";
        try {
            if (socket == null) {
                socket = new LocalSocket();
                socket.connect(new LocalSocketAddress("heckervpnd", LocalSocketAddress.Namespace.RESERVED));
                socket.setSoTimeout(90000);
            }
            stage = "exchange";
            byte[] request = command.getBytes(StandardCharsets.UTF_8);
            if (request.length > VpnConfig.MAX_BYTES + 16) throw new IllegalArgumentException("Request is too large");
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(request.length); output.write(request); output.flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            int size = input.readInt();
            if (size < 2 || size > 8192) throw new Exception("Invalid kernel response");
            byte[] response = new byte[size]; input.readFully(response);
            JSONObject result = new JSONObject(new String(response, StandardCharsets.UTF_8));
            if (!result.optBoolean("ok")) throw new Failure(result);
            return result;
        } catch (java.io.IOException failure) {
            close();
            // Never include configuration, keys or arbitrary helper output.
            throw new IllegalArgumentException(stage.equals("connect")
                    ? "Cannot reach the built-in WireGuard service. Reboot after updating the ROM; if this persists, check HeckerVPN service and SELinux logs."
                    : "The built-in WireGuard service stopped responding. Reconnect to retry; check HeckerVPN service logs if this persists.", failure);
        } catch (Exception failure) { close(); throw failure; }
    }
    @Override public void close() {
        if (socket != null) { try { socket.close(); } catch (Exception ignored) {} socket = null; }
    }
}
