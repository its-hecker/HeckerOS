// SPDX-License-Identifier: Apache-2.0
import org.hecker.netlab.NetLabIdentity;
public final class NetLabIdentityTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        check(NetLabIdentity.kind("", "", "_smb._tcp").equals("unknown"));
        check(NetLabIdentity.kind("Living room", "", "_googlecast._tcp").equals("media"));
        check(NetLabIdentity.kind("Sam’s phone", "", "_companion-link._tcp").equals("apple"));
        check(NetLabIdentity.kind("Sam’s iPhone", "iPhone15,2", "_companion-link._tcp").equals("ios"));
        check(NetLabIdentity.kind("Work", "MacBookPro18,1", "_device-info._tcp").equals("macos"));
        check(NetLabIdentity.kind("android-123", "", "").equals("android"));
        check(NetLabIdentity.kind("DESKTOP-123", "", "").equals("windows"));
        check(NetLabIdentity.kind("Random123", "", "").equals("unknown"));
        check(NetLabIdentity.kind("Office", "Mac14,2", "_device-info._tcp").equals("macos"));
        check(NetLabIdentity.kind("Workstation", "", "_workstation._tcp").equals("computer"));
        check(NetLabIdentity.kind("Printer", "LaserJet", "_ipp._tcp").equals("printer"));
        check(NetLabIdentity.clean("a\u202eb\nc").equals("abc"));
        check(NetLabIdentity.clean("x".repeat(500)).length()==80);
        System.out.println("NetLab: bounded names and uncertain OS/service hints PASS");
    }
}
