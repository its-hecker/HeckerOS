// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;
import java.io.*;import java.nio.file.*;import java.util.*;
import android.os.ParcelFileDescriptor;import android.content.pm.PackageInfo;import android.app.backup.*;

/** Calls the production transport with host descriptor adapters; platform declarations compile separately. */
public final class VaultTransportTest{
static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
public static void main(String[] args)throws Exception{
File root=Files.createTempDirectory("vault-transport-").toFile(),input=new File(root,"input"),output=new File(root,"output");
byte[] payload=new byte[210123];new Random(7).nextBytes(payload);Files.write(input.toPath(),payload);
VaultTransport t=new VaultTransport();PackageInfo app=new PackageInfo("org.example.app"),other=new PackageInfo("org.other.app"),pm=new PackageInfo("@pm@");
try{
check(t.initializeDevice()==-1000);check(!t.isAppEligibleForBackup(app,true));check(t.requestBackupTime()>86400000);
VaultSession s=new VaultSession(root,123,false,Arrays.asList(app.packageName));VaultSession.current=s;
check(t.initializeDevice()==0);check(t.isAppEligibleForBackup(app,true));check(!t.isAppEligibleForBackup(other,true));check(t.isAppEligibleForBackup(pm,false));
try(ParcelFileDescriptor fd=new ParcelFileDescriptor(input,false)){
check(t.performBackup(other,fd,4)==-1002);check(t.performBackup(app,fd,2)==-1006);check(t.performBackup(app,fd,4)==0);}
check(Arrays.equals(payload,Files.readAllBytes(s.data(app.packageName,"kv").toPath())));check("kv".equals(s.kinds.get(app.packageName)));
check(t.finishBackup()==0);check(t.clearBackupData(app)==-1002);check(s.data(app.packageName,"kv").isFile());
try(ParcelFileDescriptor fd=new ParcelFileDescriptor(input,false)){check(t.performFullBackup(app,fd,1)==0);check(t.performFullBackup(app,fd,1)==-1000);
check(t.sendBackupData(65536)==0);check(t.sendBackupData(payload.length-65536)==0);check(t.finishBackup()==0);}
check(Arrays.equals(payload,Files.readAllBytes(s.data(app.packageName,"full").toPath())));check(!s.data(app.packageName,"full.part").exists());
try(ParcelFileDescriptor fd=new ParcelFileDescriptor(input,false)){check(t.performFullBackup(app,fd,1)==0);check(t.sendBackupData(50)==0);t.cancelFullBackup();}
check(!s.data(app.packageName,"full.part").exists());check(s.data(app.packageName,"full").isFile());check(t.checkFullBackupSize(VaultTransport.QUOTA+1)==-1005);
VaultSession restoring=new VaultSession(root,123,true,Arrays.asList(app.packageName));VaultSession.current=restoring;
check(t.getAvailableRestoreSets().length==1);check(t.startRestore(124,new PackageInfo[]{app})==-1000);check(t.startRestore(123,new PackageInfo[]{other,app})==0);
RestoreDescription next=t.nextRestorePackage();check(next.pkg.equals(app.packageName));check(next.type==1);
try(ParcelFileDescriptor fd=new ParcelFileDescriptor(output,true)){check(t.getRestoreData(fd)==0);}check(Arrays.equals(payload,Files.readAllBytes(output.toPath())));
check(t.nextRestorePackage()==RestoreDescription.NO_MORE_PACKAGES);t.finishRestore();
check(new File(root,"data/org.example.app/kv").delete());check(t.startRestore(123,new PackageInfo[]{app})==0);check(t.nextRestorePackage().type==2);
int total=0;try(ParcelFileDescriptor fd=new ParcelFileDescriptor(output,true)){int n;while((n=t.getNextFullRestoreDataChunk(fd))!=-1){check(n>0);total+=n;}}
check(total==payload.length);check(Arrays.equals(payload,Files.readAllBytes(output.toPath())));t.finishRestore();
restoring.cancelled=true;check(t.getAvailableRestoreSets().length==0);check(t.startRestore(123,new PackageInfo[]{app})==-1000);
VaultSession.current=null;check(t.getCurrentRestoreSet()==0);check(t.sendBackupData(1)==-1000);check(!VaultSession.validPackage("../../x"));check(VaultSession.validPackage("org.good.app"));
}finally{t.closeStreams();VaultSession.current=null;VaultArchive.delete(root);}
System.out.println("HeckerVault production native-transport protocol checks: "+checks+" PASS");
}}
