# SPDX-License-Identifier: Apache-2.0
"""Minimal simulated platform boundaries for the production broker's authorization/lifetime tests."""
SOURCES={
'android/Manifest.java':'''package android; public class Manifest {public static class permission {public static final String BACKUP="backup";}}''',
'android/content/Context.java':'''package android.content;
import android.content.pm.*; import android.os.*;
public class Context {public PackageManager packages=new PackageManager();public UserManager users=new UserManager();public boolean permission=true;
public void enforceCallingPermission(String p,String m){if(!permission)throw new SecurityException();}
public PackageManager getPackageManager(){return packages;}public <T>T getSystemService(Class<T> c){return c.cast(users);}}''',
'android/content/pm/ApplicationInfo.java':'''package android.content.pm;
public class ApplicationInfo {public static final int FLAG_SYSTEM=1;public int uid,flags;public String seInfo="default",credentialProtectedDataDir,deviceProtectedDataDir;}''',
'android/content/pm/PackageInfo.java':'''package android.content.pm;
public class PackageInfo {public ApplicationInfo applicationInfo;public String sharedUserId;public long version=10;public SigningInfo signingInfo=new SigningInfo();public long getLongVersionCode(){return version;}}''',
'android/content/pm/Signature.java':'''package android.content.pm; public class Signature {public byte[] toByteArray(){return new byte[]{1,2,3};}}''',
'android/content/pm/SigningInfo.java':'''package android.content.pm; public class SigningInfo {public Signature[] getApkContentsSigners(){return new Signature[]{new Signature()};}}''',
'android/content/pm/PackageManager.java':'''package android.content.pm;
public class PackageManager {public static final int GET_SIGNING_CERTIFICATES=1,SIGNATURE_MATCH=0;
public java.util.Map<String,PackageInfo> apps=new java.util.HashMap<>();public boolean signature=true;
public ApplicationInfo getApplicationInfo(String p,int f){return getPackageInfo(p,f).applicationInfo;}
public PackageInfo getPackageInfo(String p,int f){PackageInfo i=apps.get(p);if(i==null)throw new IllegalArgumentException();return i;}
public int checkSignatures(String a,String b){return signature?0:-1;}
public String[] getPackagesForUid(int uid){return apps.entrySet().stream().filter(e->e.getValue().applicationInfo.uid==uid).map(e->e.getKey()).toArray(String[]::new);}}''',
'android/os/IBinder.java':'''package android.os; public interface IBinder {int INTERFACE_TRANSACTION=1598968902;
interface DeathRecipient{void binderDied();}void linkToDeath(DeathRecipient d,int f)throws RemoteException;boolean unlinkToDeath(DeathRecipient d,int f);}''',
'android/os/Binder.java':'''package android.os; public class Binder implements IBinder {
public static int caller=10050;public java.util.List<DeathRecipient> deaths=new java.util.ArrayList<>();
public static int getCallingUid(){return caller;}public static long clearCallingIdentity(){return caller;}public static void restoreCallingIdentity(long id){}
public void linkToDeath(DeathRecipient d,int f){deaths.add(d);}public boolean unlinkToDeath(DeathRecipient d,int f){return deaths.remove(d);}
protected boolean onTransact(int code,Parcel d,Parcel r,int f)throws RemoteException{return false;}}''',
'android/os/RemoteException.java':'''package android.os;public class RemoteException extends Exception {}''',
'android/os/ServiceSpecificException.java':'''package android.os;public class ServiceSpecificException extends RuntimeException {public ServiceSpecificException(int c,String m){super(m);}}''',
'android/os/Parcel.java':'''package android.os;public class Parcel {public java.util.List<Object> values=new java.util.ArrayList<>();int at=0;
public void enforceInterface(String expected){if(!expected.equals(readString()))throw new SecurityException();}
public boolean readBoolean(){return (boolean)values.get(at++);}public String readString(){return (String)values.get(at++);}
public long readLong(){return (long)values.get(at++);}public String[] createStringArray(){return (String[])values.get(at++);}
public void enforceNoDataAvail(){if(at!=values.size())throw new SecurityException();}public void writeString(String s){}public void writeNoException(){}}''',
'android/os/ServiceManager.java':'''package android.os;public class ServiceManager {public static java.util.Map<String,IBinder> services=new java.util.HashMap<>();
public static IBinder checkService(String n){return services.get(n);}public static void addService(String n,IBinder b){services.put(n,b);}}''',
'android/os/UserManager.java':'''package android.os;public class UserManager {public boolean unlocked=true;public boolean isUserUnlocked(){return unlocked;}}''',
'android/os/IInstalld.java':'''package android.os;public interface IInstalld {interface IAppDataOperationCallback {
int STATUS_RUNNING=1,STATUS_SUCCESS=2,STATUS_FAILURE=3;void onStatusChanged(int s,String m,String[] f);
abstract class Stub implements IAppDataOperationCallback {}}}''',
'android/util/Slog.java':'''package android.util;public class Slog {public static int w(String t,String m){return 0;}}''',
'com/android/server/pm/PackageManagerService.java':'''package com.android.server.pm;public class PackageManagerService {
final android.content.Context mContext=new android.content.Context();final Installer mInstaller=new Installer();final PackageManagerTracedLock mInstallLock=new PackageManagerTracedLock();}''',
'com/android/server/pm/PackageManagerTracedLock.java':'''package com.android.server.pm;public class PackageManagerTracedLock implements AutoCloseable {
public PackageManagerTracedLock acquireLock(){return this;}public void close(){}}''',
'com/android/server/pm/PackageFreezer.java':'''package com.android.server.pm;public class PackageFreezer implements AutoCloseable {
public static volatile int active=0,total=0;public PackageFreezer(String p,int user,String reason,PackageManagerService pm,int exit,Object request,boolean killed,boolean stopped){if(!killed||!stopped)throw new AssertionError();active++;total++;}
public void close(){active--;}}''',
'com/android/server/pm/Installer.java':'''package com.android.server.pm;import android.os.IInstalld.IAppDataOperationCallback;
public class Installer {public java.util.List<Integer> flags=new java.util.ArrayList<>();public int failFlag;public volatile boolean hold=false;
public volatile IAppDataOperationCallback pending;
public void copyAppDataPath(String uuid,String from,String to,int user,int appId,String seInfo,int flag,int callerUid,IAppDataOperationCallback cb){
if(PackageFreezer.active!=1||user!=0)throw new AssertionError();flags.add(flag);
if(hold&&flag==0x40000000){pending=cb;return;}cb.onStatusChanged(flag==failFlag?3:2,null,null);}}''',
}
