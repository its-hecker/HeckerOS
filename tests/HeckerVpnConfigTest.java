// SPDX-License-Identifier: Apache-2.0
import org.hecker.vpn.VpnConfig;
import java.util.List;
import org.hecker.vpn.VpnInput;
import org.hecker.vpn.VpnUnderlay;
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
        String imported = parsed.render("all", List.of());
        VpnConfig session = VpnConfig.parse(parsed.forConnection("include", List.of("com.android.chrome")));
        if (!"25".equals(session.peers.get(0).get("PersistentKeepalive")) || !session.mode.equals("include")
                || !session.applications.equals(List.of("com.android.chrome")))
            throw new AssertionError("Idle app-only connection cannot initiate peer contact");
        if (!imported.equals(parsed.render("all", List.of()))) throw new AssertionError("Connection rewrote the saved profile");
        int[] resolutions={0};
        String onNetwork=parsed.forNetwork("include",List.of("com.android.chrome"),host->{
            if(!host.equals("vpn.example.org"))throw new AssertionError("Wrong endpoint host");resolutions[0]++;
            return List.of("2001:db8::5","192.0.2.5");});
        VpnConfig resolved=VpnConfig.parse(onNetwork);
        if(resolutions[0]!=1||!resolved.peers.get(0).get("Endpoint").equals("[2001:db8::5]:51820")
                ||!resolved.peers.get(0).get("PersistentKeepalive").equals("25")||!resolved.mode.equals("include"))
            throw new AssertionError("Physical-network resolution lost endpoint, keepalive or app policy");
        if(!imported.equals(parsed.render("all",List.of())))throw new AssertionError("Numeric connection address leaked into stored profile");
        VpnConfig numeric=VpnConfig.parse(CONFIG.replace("vpn.example.org","192.0.2.8"));
        numeric.forNetwork("all",List.of(),host->{throw new AssertionError("Numeric endpoint reached DNS");});
        try{parsed.forNetwork("all",List.of(),host->List.of());throw new AssertionError("Missing DNS results accepted");}catch(IllegalArgumentException expected){}
        try{parsed.forNetwork("all",List.of(),host->List.of("192.0.2.5;id"));throw new AssertionError("Non-IP DNS result accepted");}catch(IllegalArgumentException expected){}
        if(!VpnUnderlay.addresses(List.of("2001:db8::5","192.0.2.5"),true,false,null).equals(List.of("192.0.2.5")))
            throw new AssertionError("IPv4 Wi-Fi retained an unreachable IPv6 endpoint");
        String nat64=VpnUnderlay.synthesize("192.0.2.5","64:ff9b::/96");
        if(!java.net.InetAddress.getByName(nat64).equals(java.net.InetAddress.getByName("64:ff9b::c000:205")))
            throw new AssertionError("Numeric IPv4 endpoint cannot use carrier NAT64");
        if(!VpnUnderlay.addresses(List.of("192.0.2.5"),false,true,"64:ff9b::/96").equals(List.of(nat64,"192.0.2.5")))
            throw new AssertionError("IPv6-only mobile endpoint order lost NAT64 or CLAT fallback");
        if(!VpnUnderlay.addresses(List.of("192.0.2.5"),true,true,"64:ff9b::/96").equals(List.of("192.0.2.5")))
            throw new AssertionError("Dual-stack endpoint was unnecessarily translated");
        for(int prefix:List.of(32,40,48,56,64,96)){
            byte[] result=java.net.InetAddress.getByName(VpnUnderlay.synthesize("192.0.2.5","2001:db8::/"+prefix)).getAddress();
            int position=prefix/8;for(int value:List.of(192,0,2,5)){if(position==8)position++;
                if((result[position++]&255)!=value)throw new AssertionError("RFC6052 IPv4 placement");}
            if(result[8]!=0)throw new AssertionError("RFC6052 reserved octet was overwritten");
        }
        VpnConfig numericOnMobile=VpnConfig.parse(numeric.forUnderlay("all",List.of(),host->List.of(nat64)));
        if(!numericOnMobile.peers.get(0).get("Endpoint").equals("["+nat64+"]:51820"))
            throw new AssertionError("Numeric profile skipped mobile endpoint handling");
        for (String keepalive : List.of("0", "120")) {
            VpnConfig explicit = VpnConfig.parse(CONFIG + "PersistentKeepalive=" + keepalive + "\n");
            if (!keepalive.equals(VpnConfig.parse(explicit.forConnection("all", List.of())).peers.get(0).get("PersistentKeepalive")))
                throw new AssertionError("Explicit keepalive preference was changed");
        }
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
        if (!org.hecker.vpn.VpnConnectionState.classify(true, 0, 100, 44999).equals("waiting_peer")
                || !org.hecker.vpn.VpnConnectionState.classify(true, 0, 100, 45000).equals("peer_timeout")
                || !org.hecker.vpn.VpnConnectionState.classify(false, 0, 100, 45000).equals("disconnected")
                || !org.hecker.vpn.VpnConnectionState.classify(true, 100, 110, 60000).equals("active")
                || !org.hecker.vpn.VpnConnectionState.classify(true, 100, 300, 60000).equals("idle")
                || !org.hecker.vpn.VpnConnectionState.classify(true, 0, -50000, 45000).equals("peer_timeout"))
            throw new AssertionError("First-contact timeout or late-handshake recovery is incorrect");
        System.out.println("HeckerVPN config, dual-stack import, app selection and privilege-boundary regressions: PASS");
    }
}
