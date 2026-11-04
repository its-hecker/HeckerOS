// SPDX-License-Identifier: Apache-2.0
import org.hecker.vpn.VpnConfig;
import java.util.List;
import org.hecker.vpn.VpnInput;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
public final class HeckerVpnConfigTest {
    private static final String KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
    private static final String CONFIG = "[Interface]\nPrivateKey=" + KEY + "\nAddress=10.1.0.2/32,fd00::2/128\nDNS=10.1.0.1,fd00::1\n[Peer]\nPublicKey=" + KEY
            + "\nAllowedIPs=0.0.0.0/0,::/0\nEndpoint=vpn.example.org:51820\n";
    private static void rejected(String config) {
        try { VpnConfig.parse(config); throw new AssertionError("Accepted an invalid configuration"); }
        catch (IllegalArgumentException expected) {}
    }
    public static void main(String[] arguments) throws Exception {
        if (!VpnInput.accountNumber("0000 0000-0000\u00a00000").equals("0000000000000000"))
            throw new AssertionError("Formatted Mullvad account was truncated");
        for (String invalid : List.of("000000000000000", "00000000000000000", "000000000000000a")) {
            try { VpnInput.accountNumber(invalid); throw new AssertionError("Invalid account accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        if (!VpnInput.profileName(null).equals("VPN profile") || !VpnInput.profileName("test.CONF").equals("test"))
            throw new AssertionError("Missing document metadata prevents import");
        for (String encoding : List.of("UTF-8", "UTF-16", "UTF-16LE", "UTF-16BE")) {
            byte[] bytes = CONFIG.replace("\n", "\r\n").getBytes(encoding);
            if (encoding.equals("UTF-16LE") || encoding.equals("UTF-16BE")) {
                byte[] withBom = new byte[bytes.length + 2];
                withBom[0] = (byte) (encoding.endsWith("LE") ? 0xff : 0xfe);
                withBom[1] = (byte) (encoding.endsWith("LE") ? 0xfe : 0xff);
                System.arraycopy(bytes, 0, withBom, 2, bytes.length); bytes = withBom;
            }
            VpnConfig.parse(VpnInput.configuration(new ByteArrayInputStream(bytes)));
        }
        for (byte[] invalid : List.of(new byte[]{(byte) 0xff, 0, 1}, new byte[65537], "not a config".getBytes(StandardCharsets.UTF_8))) {
            try { VpnInput.configuration(new ByteArrayInputStream(invalid)); throw new AssertionError("Bad document accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        VpnConfig parsed = VpnConfig.parse(CONFIG);
        if (!parsed.render("all", List.of()).equals(VpnConfig.parse(parsed.render("all", List.of())).render("all", List.of()))) throw new AssertionError("Unstable normalization");
        String selected = parsed.render("include", List.of("org.telegram.messenger", "com.android.chrome"));
        if (!selected.contains("IncludedApplications=org.telegram.messenger,com.android.chrome\n")) throw new AssertionError("Missing selected-app policy");
        if (!parsed.render("exclude", List.of("com.example.game")).contains("ExcludedApplications=com.example.game\n")) throw new AssertionError("Missing bypass policy");
        try { parsed.render("include", List.of()); throw new AssertionError("Empty include became all apps"); } catch (IllegalArgumentException expected) {}
        try { parsed.render("include", List.of("x;id")); throw new AssertionError("Unsafe package"); } catch (IllegalArgumentException expected) {}
        if (!VpnConfig.parse(CONFIG.replace("DNS=", "IncludedApplications=com.example.test\nDNS=")).mode.equals("include")) throw new AssertionError("Import lost app selection");
        rejected(CONFIG.replace("DNS=", "IncludedApplications=com.example.a\nExcludedApplications=com.example.b\nDNS="));
        rejected(CONFIG.replace("DNS=", "IncludedApplications=\nDNS="));
        for (String hook : List.of("PreUp", "PostUp", "PreDown", "PostDown", "Table", "SaveConfig", "FwMark")) rejected(CONFIG.replace("DNS=", hook + "=true\nDNS="));
        rejected(CONFIG.replace("vpn.example.org", "vpn.example.org;id"));
        rejected(CONFIG.replace("vpn.example.org", "$(id)"));
        rejected(CONFIG.replace("vpn.example.org", "`id`"));
        rejected(CONFIG.replace("10.1.0.1", "dns.example.org"));
        rejected(CONFIG.replace("10.1.0.1", "999.1.2.3"));
        rejected(CONFIG.replace("51820", "65536"));
        rejected(CONFIG.replace("Address=", "PrivateKey=" + KEY + "\nAddress="));
        rejected(CONFIG.replace("Address=10.1.0.2/32,fd00::2/128\n", ""));
        rejected(CONFIG.replace(KEY, "invalid"));
        rejected(CONFIG + "\u0000"); rejected("x".repeat(65537));
        VpnConfig.parse(CONFIG.replace("vpn.example.org:51820", "[2001:db8::1]:51820"));
        VpnConfig.parse(CONFIG.replace("Address=10.1.0.2/32,fd00::2/128", "Address=10.1.0.2/32\nAddress=fd00::2/128"));
        if (!org.hecker.vpn.VpnConnectionState.classify(false, 100, 110).equals("disconnected")
                || !org.hecker.vpn.VpnConnectionState.classify(true, 0, 110).equals("waiting_peer")
                || !org.hecker.vpn.VpnConnectionState.classify(true, 100, 110).equals("active")
                || !org.hecker.vpn.VpnConnectionState.classify(true, 100, 300).equals("idle")) throw new AssertionError("Handshake freshness semantics");
        System.out.println("HeckerVPN config, dual-stack import, app selection and privilege-boundary regressions: PASS");
    }
}
