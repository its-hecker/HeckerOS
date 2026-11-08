// SPDX-License-Identifier: Apache-2.0
package com.android.server.pm;
import android.content.pm.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.hecker.vault.VaultFilesProtocol;
public class HeckerVaultFilesServiceTest {
    static final String PKG="org.telegram.plus", ID="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    static String signer;
    interface Attempt{void run()throws Exception;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void denied(Attempt a)throws Exception{int before=PackageFreezer.total;try{a.run();throw new AssertionError("denial required");}catch(SecurityException expected){}check(PackageFreezer.total==before&&PackageFreezer.active==0);}
    static PackageInfo app(String pkg,int uid,int flags){PackageInfo a=new PackageInfo();a.applicationInfo=new ApplicationInfo();a.applicationInfo.uid=uid;a.applicationInfo.flags=flags;a.applicationInfo.credentialProtectedDataDir="/data/user/0/"+pkg;a.applicationInfo.deviceProtectedDataDir="/data/user_de/0/"+pkg;return a;}
    static void call(HeckerVaultFilesService service,boolean restore,String pkg,String id,long version,String[] signers)throws Exception{
        Parcel p=new Parcel();p.values.addAll(Arrays.asList(VaultFilesProtocol.DESCRIPTOR,restore,pkg,id,version,signers));
        service.onTransact(VaultFilesProtocol.CALL,p,new Parcel(),0);
    }
    static void call(HeckerVaultFilesService service,boolean restore)throws Exception{call(service,restore,PKG,ID,10,new String[]{signer});}
    public static void main(String[] args)throws Exception{
        StringBuilder hex=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3}))hex.append(String.format("%02x",b&255));signer=hex.toString();
        PackageManagerService pm=new PackageManagerService();var manager=pm.mContext.packages;
        manager.apps.put("org.hecker.vault",app("org.hecker.vault",10050,1));PackageInfo target=app(PKG,10051,0);manager.apps.put(PKG,target);
        Binder daemon=new Binder();ServiceManager.addService("installd",daemon);HeckerVaultFilesService.publish(pm);
        HeckerVaultFilesService service=(HeckerVaultFilesService)ServiceManager.checkService(VaultFilesProtocol.SERVICE);
        Binder.caller=10052;denied(()->call(service,false));Binder.caller=10050;
        pm.mContext.permission=false;denied(()->call(service,false));pm.mContext.permission=true;
        manager.signature=false;denied(()->call(service,false));manager.signature=true;
        manager.apps.get("org.hecker.vault").applicationInfo.flags=0;denied(()->call(service,false));manager.apps.get("org.hecker.vault").applicationInfo.flags=1;
        denied(()->call(service,false,PKG,"../escape",10,new String[]{signer}));
        denied(()->call(service,false,"android",ID,10,new String[]{signer}));
        denied(()->call(service,false,PKG,ID,11,new String[]{signer}));
        denied(()->call(service,true,PKG,ID,11,new String[]{signer}));
        denied(()->call(service,false,PKG,ID,10,new String[]{"0".repeat(64)}));
        target.sharedUserId="shared";denied(()->call(service,false));target.sharedUserId=null;
        manager.apps.put("org.other.shared",app("org.other.shared",10051,0));denied(()->call(service,false));manager.apps.remove("org.other.shared");
        target.applicationInfo.flags=1;denied(()->call(service,false));target.applicationInfo.flags=0;
        target.applicationInfo.uid=110051;denied(()->call(service,false));target.applicationInfo.uid=10051;
        target.applicationInfo.credentialProtectedDataDir="/data/other";denied(()->call(service,false));target.applicationInfo.credentialProtectedDataDir="/data/user/0/"+PKG;
        pm.mContext.users.unlocked=false;try{call(service,false);throw new AssertionError();}catch(ServiceSpecificException expected){}check(PackageFreezer.active==0);pm.mContext.users.unlocked=true;
        call(service,false);check(pm.mInstaller.flags.equals(List.of(VaultFilesProtocol.RECOVER,VaultFilesProtocol.CAPTURE)));check(PackageFreezer.active==0);
        pm.mInstaller.flags.clear();call(service,true);check(pm.mInstaller.flags.equals(List.of(VaultFilesProtocol.RECOVER,VaultFilesProtocol.RESTORE)));
        pm.mInstaller.flags.clear();pm.mInstaller.failFlag=VaultFilesProtocol.RESTORE;
        try{call(service,true);throw new AssertionError();}catch(ServiceSpecificException expected){}
        check(pm.mInstaller.flags.equals(List.of(VaultFilesProtocol.RECOVER,VaultFilesProtocol.RESTORE,VaultFilesProtocol.RECOVER)));check(PackageFreezer.active==0);
        pm.mInstaller.failFlag=0;pm.mInstaller.hold=true;AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(()->{try{call(service,false);}catch(Throwable t){failure.set(t);}});worker.start();
        long deadline=System.nanoTime()+5_000_000_000L;while(pm.mInstaller.pending==null&&System.nanoTime()<deadline)Thread.yield();
        check(pm.mInstaller.pending!=null&&PackageFreezer.active==1);worker.interrupt();check(PackageFreezer.active==1);
        pm.mInstaller.pending.onStatusChanged(2,null,null);worker.join(5000);check(!worker.isAlive()&&failure.get()==null&&PackageFreezer.active==0);
        System.out.println("Production Vault broker: caller/signature/target/path guards, version/signers, recovery and async freezer lifetime: PASS");
    }
}
