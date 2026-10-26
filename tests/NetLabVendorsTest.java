// SPDX-License-Identifier: Apache-2.0
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import org.hecker.netlab.NetLabVendors;
import org.hecker.netlab.NetLabIdentity;

public final class NetLabVendorsTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    public static void main(String[] args)throws Exception {
        NetLabVendors fixture=NetLabVendors.read(new StringReader("001122\tLarge\n0011223\tMedium\n001122334\tSmall\n"));
        check(fixture.lookup("00:11:22:33:44:55").equals("Small"));
        check(fixture.lookup("00:11:22:3F:44:55").equals("Medium"));
        check(fixture.lookup("001122AA4455").equals("Large"));
        check(fixture.lookup("00-11-22-AA-44-55").equals("Large"));
        for(String invalid:new String[]{"02:11:22:33:44:55","01:11:22:33:44:55","FF:FF:FF:FF:FF:FF","00:00:00:00:00:00","","001122","00:11-22:33:44:55","XX:11:22:33:44:55"})check(fixture.lookup(invalid).isEmpty());
        check(fixture.lookup(null).isEmpty());check(NetLabVendors.privateMac("B6:62:6D:00:00:01"));
        check(!NetLabVendors.privateMac("24:11:45:00:00:01"));
        for(String malformed:new String[]{"00112\tVendor\n","001122\t\n","GG1122\tVendor\n","001122\tA\n001122\tB\n"}){
            try{NetLabVendors.read(new StringReader(malformed));throw new AssertionError("Malformed registry accepted");}catch(IOException expected){checks++;}
        }
        try(Reader reader=new InputStreamReader(new GZIPInputStream(Files.newInputStream(Paths.get(args[0]))),StandardCharsets.UTF_8)){
            NetLabVendors actual=NetLabVendors.read(reader);
            // Public OUI prefixes from the feedback, with synthetic device-specific suffixes.
            check(actual.lookup("FC:A6:CD:00:00:01").toLowerCase().contains("fiberhome"));
            check(actual.lookup("24:11:45:00:00:01").toLowerCase().contains("xiaomi"));
            for(String mac:new String[]{"B6:62:6D:00:00:01","6A:00:DA:00:00:01","E2:8C:8D:00:00:01","9A:63:11:00:00:01"})check(actual.lookup(mac).isEmpty());
        }
        check(NetLabIdentity.brand("Home","Pixel 9","").equals("Google"));
        check(NetLabIdentity.brand("Work","iPhone15,2","").equals("Apple"));
        check(NetLabIdentity.brand("Phone","SM-S918B","").equals("Samsung"));
        check(NetLabIdentity.brand("Untitled","","").isEmpty());
        check(NetLabIdentity.brand("Home","","Acme\u202e\n").equals("Acme"));
        System.out.println("NetLab vendor registry: "+checks+" prefix, private-MAC, malformed-input, real-image and hint checks PASS");
    }
}
