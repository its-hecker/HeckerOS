// SPDX-License-Identifier: Apache-2.0
import org.hecker.netlab.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
public final class NetLabOnlineVendorsTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static final class Provider implements NetLabOnlineVendors.Transport {
        int calls,cancels;String prefix;NetLabOnlineVendors.Reply reply=new NetLabOnlineVendors.Reply(200,"Apple, Inc.",0);
        boolean fail;Runnable onCall=()->{};
        public NetLabOnlineVendors.Reply get(String prefix)throws IOException{calls++;this.prefix=prefix;onCall.run();if(fail)throw new IOException("Offline");return reply;}
        public void cancel(){cancels++;}
    }
    public static void main(String[] args)throws Exception{
        String mac="00:11:22:33:44:55";AtomicLong now=new AtomicLong(1_700_000_000_000L);AtomicReference<String> disk=new AtomicReference<>("");
        Provider provider=new Provider();NetLabOnlineVendors vendors=new NetLabOnlineVendors("",disk::set,provider,now::get);
        check(vendors.lookup(mac,()->false).equals("Apple, Inc."));check(provider.prefix.equals("001122")&&provider.calls==1);
        check(!disk.get().contains("334455"));check(!vendors.due("00:11:22:99:88:77"));
        vendors.lookup("00:11:22:99:88:77",()->false);check(provider.calls==1);
        vendors=new NetLabOnlineVendors(disk.get(),disk::set,provider,now::get);check(vendors.cached(mac).equals("Apple, Inc."));
        NetLabSavedDevices names=new NetLabSavedDevices("");names.save(mac,"My phone",false);
        check(names.label(mac,vendors.cached(mac)).equals("My phone"));
        for(String invalid:new String[]{"02:11:22:33:44:55","ff:ff:ff:ff:ff:ff","01:11:22:33:44:55","192.168.1.1",""}){
            check(NetLabOnlineVendors.prefix(invalid).isEmpty());vendors.lookup(invalid,()->false);}
        check(provider.calls==1);
        now.addAndGet(31L*86_400_000);check(vendors.due(mac));
        provider.reply=new NetLabOnlineVendors.Reply(200,"*NO COMPANY*",0);check(vendors.lookup(mac,()->false).isEmpty());check(!vendors.due(mac));
        now.addAndGet(86_400_001);check(vendors.due(mac));
        provider.reply=new NetLabOnlineVendors.Reply(429,"",3_600_000);vendors.lookup(mac,()->false);int count=provider.calls;
        check(!vendors.due("00:22:33:44:55:66"));vendors.lookup("00:22:33:44:55:66",()->false);check(provider.calls==count);
        vendors=new NetLabOnlineVendors(disk.get(),disk::set,provider,now::get);check(!vendors.due(mac));
        now.addAndGet(3_600_001);provider.reply=new NetLabOnlineVendors.Reply(200,"<html>upstream error</html>",0);
        check(vendors.lookup(mac,()->false).isEmpty());check(!vendors.due(mac));
        now.addAndGet(900_001);provider.reply=new NetLabOnlineVendors.Reply(200,"Samsung Electronics",0);
        AtomicBoolean cancelled=new AtomicBoolean();provider.onCall=()->cancelled.set(true);vendors.lookup(mac,cancelled::get);
        check(vendors.cached(mac).isEmpty());check(vendors.due(mac));provider.onCall=()->{};
        vendors.cancel();check(provider.cancels==1);vendors.close();count=provider.calls;
        vendors.lookup(mac,()->false);check(provider.calls==count);
        Provider offline=new Provider();offline.fail=true;vendors=new NetLabOnlineVendors("",disk::set,offline,now::get);
        check(vendors.lookup(mac,()->false).isEmpty());check(!vendors.due(mac));
        Provider missing=new Provider();missing.reply=new NetLabOnlineVendors.Reply(404,"",0);
        vendors=new NetLabOnlineVendors("",disk::set,missing,now::get);
        for(int i=0;i<520;i++)vendors.lookup(String.format("00%04X334455",i),()->false);
        Properties values=new Properties();values.load(new StringReader(disk.get()));
        check(values.stringPropertyNames().stream().filter(k->k.startsWith("vendor.")).count()==512);
        try{NetLabOnlineVendors.https().get("../../full-mac");throw new AssertionError();}catch(IOException expected){}
        System.out.println("Online identity: prefix-only requests, private/malformed skips, persistent cache/TTL, manual priority, rate limits, cancellation and offline fallback PASS");
    }
}
