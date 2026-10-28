// SPDX-License-Identifier: Apache-2.0
import org.hecker.netlab.NetLabSavedDevices;
public final class NetLabSavedDevicesTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        String mac="02:11:22:33:44:55";
        NetLabSavedDevices names=new NetLabSavedDevices("");
        check(names.save(mac,"  Ali’s phone 📱  ",false));
        check(names.label("021122334455","Online vendor").equals("Ali’s phone 📱"));
        names=new NetLabSavedDevices(names.encode());
        check(names.label("02-11-22-33-44-55","New scan IP").equals("Ali’s phone 📱"));
        check(names.label("02:11:22:33:44:56","Different peer at the same IP").equals("Different peer at the same IP"));
        check(names.save(mac,"Work phone",true));check(names.names().size()==1);
        names=new NetLabSavedDevices(names.encode());check(names.label(mac,"Apple").equals("Work phone")&&names.get(mac).trusted);
        check(names.save(mac,"",false));check(names.names().isEmpty());check(names.label(mac,"Detected model").equals("Detected model"));
        check(!names.save("ff:ff:ff:ff:ff:ff","Wrong",false));check(!names.save("00:00:00:00:00:00","Wrong",false));
        check(!names.save("192.168.1.2","Wrong",false));check(!names.save("01:11:22:33:44:55","Multicast",false));
        check(names.save(mac,"a\n\u202eb=\\c",false));names=new NetLabSavedDevices(names.encode());check(names.label(mac,"").equals("ab=\\c"));
        check(NetLabSavedDevices.name("📱".repeat(100)).codePointCount(0,NetLabSavedDevices.name("📱".repeat(100)).length())==64);
        names=new NetLabSavedDevices("bad=\\uZZZZ");check(names.names().isEmpty());
        for(int i=0;i<NetLabSavedDevices.LIMIT;i++)check(names.save(String.format("001122%06X",i),"Peer "+i,false));
        check(!names.save("00:33:44:55:66:77","Overflow",false));
        check(names.save("001122000001","Updated at capacity",false));
        NetLabSavedDevices restored=new NetLabSavedDevices(names.encode());check(restored.names().size()==NetLabSavedDevices.LIMIT);
        check(restored.label("001122000001","").equals("Updated at capacity"));
        System.out.println("Saved device names: restart/scan/IP independence, rename, manual priority, private MACs, removal, Unicode and capacity PASS");
    }
}
