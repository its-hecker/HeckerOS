// SPDX-License-Identifier: Apache-2.0
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.hecker.netlab.NetLabLanProtocol;
public final class NetLabLanProtocolTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    private static void rejects(String value) { try { NetLabLanProtocol.decode(value); } catch (IllegalArgumentException expected) { check(true); return; } check(false); }
    public static void main(String[] args) throws Exception {
        for (String value : new String[]{"READY 1","STARTED","STOPPING","LINK 02:00:00:00:00:01 a2:bb:cc:dd:ee:ff", "PEER 192.168.10.3 02:00:00:00:00:03", "RESTORED 192.168.10.3", "STAT 192.168.10.3 LIMIT 128 512 0 1 2 3", "END stopped", "ERROR raw_socket_denied"}) check(NetLabLanProtocol.decode(value).length > 0);
        for (String value : new String[]{"", "READY 2", "STARTED extra", "PEER 127.1 02:00:00:00:00:03", "PEER 0192.168.1.3 02:00:00:00:00:03", "PEER 192.168.10.3 ff:ff:ff:ff:ff:ff", "LINK 00:00:00:00:00:00 02:00:00:00:00:01", "STAT 192.168.10.3 LIMIT 128 512 -1 2 3 4", "STAT 192.168.10.3 LIMIT 128 512 9223372036854775808 2 3 4", "STAT 192.168.10.3 LIMIT 1024001 512 0 0 0 0", "STAT 192.168.10.3 UNKNOWN 128 512 0 0 0 0", "ERROR java.lang.RuntimeException", "ERROR x\nSTARTED", "END  stopped", "STOP", "$(id)"}) rejects(value);
        byte[] sample = "READY 1\nEND stopped\n".getBytes(StandardCharsets.US_ASCII);
        InputStream input = new ByteArrayInputStream(sample);
        check(NetLabLanProtocol.readLine(input).equals("READY 1")); check(NetLabLanProtocol.readLine(input).equals("END stopped")); check(NetLabLanProtocol.readLine(input) == null);
        for (String value : new String[]{"partial", "A".repeat(1025)+"\n", "A\u0000B\n", "A\rB\n"}) {
            try { NetLabLanProtocol.readLine(new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII))); check(false); }
            catch (IOException expected) { check(true); }
        }
        check(NetLabLanProtocol.mac("A2:BB:CC:DD:EE:FF").equals("a2:bb:cc:dd:ee:ff"));
        check(NetLabLanProtocol.number("9223372036854775807",Long.MAX_VALUE)==Long.MAX_VALUE);
        System.out.println("NetLab bounded root protocol, malformed records and unsigned counters: " + checks + " checks PASS");
    }
}
