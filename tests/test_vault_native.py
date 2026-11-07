# SPDX-License-Identifier: Apache-2.0
"""Exercise production native backup requests against a recording platform boundary."""
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]

class VaultNativeTest(unittest.TestCase):
    def test_each_full_only_snapshot_requests_package_metadata_and_restores_provider(self):
        sources={
            'android/content/Context.java':'package android.content; public class Context {}',
            'android/content/ComponentName.java':'package android.content; public class ComponentName {public ComponentName(Context c,Class<?> cls){}}',
            'android/content/SharedPreferences.java':'''package android.content;
public interface SharedPreferences {boolean getBoolean(String key,boolean fallback);String getString(String key,String fallback);Editor edit();
 interface Editor {Editor putBoolean(String key,boolean value);Editor putString(String key,String value);Editor remove(String key);boolean commit();}}
''',
            'android/app/backup/SelectBackupTransportCallback.java':'''package android.app.backup;
public class SelectBackupTransportCallback {public void onSuccess(String name){}public void onFailure(int reason){}}''',
            'android/app/backup/BackupObserver.java':'''package android.app.backup;
public class BackupObserver {public void onUpdate(String pkg,BackupProgress p){}public void onResult(String pkg,int code){}public void backupFinished(int code){}}''',
            'android/app/backup/BackupProgress.java':'package android.app.backup;public class BackupProgress {}',
            'android/app/backup/RestoreSet.java':'package android.app.backup;public class RestoreSet {public long token;}',
            'android/app/backup/RestoreObserver.java':'''package android.app.backup;
public class RestoreObserver {public void restoreSetsAvailable(RestoreSet[] sets){}public void onUpdate(int pos,String pkg){}public void restoreFinished(int code){}}''',
            'android/app/backup/RestoreSession.java':'''package android.app.backup;import java.util.Set;
public class RestoreSession {public int getAvailableRestoreSets(RestoreObserver o){return 0;}
public int restorePackages(long token,RestoreObserver o,Set<String> pkgs,Object monitor){return 0;}public void endRestoreSession(){}}''',
            'android/app/backup/BackupManager.java':'''package android.app.backup;import android.content.*;
public class BackupManager {public static final int SUCCESS=0,FLAG_NON_INCREMENTAL_BACKUP=1;
 public static String current="previous.android.provider";public static boolean enabled=true;public static String[] targets;public static int flags;
 public BackupManager(Context c){}public String getCurrentTransport(){return current;}
 public void selectBackupTransport(ComponentName c,SelectBackupTransportCallback callback){current="org.hecker.vault/.VaultTransportService";callback.onSuccess(current);}
 public String selectBackupTransport(String provider){current=provider;return provider;}
 public boolean isBackupEnabled(){return enabled;}
 public int requestBackup(String[] pkgs,BackupObserver o,Object monitor,int f){targets=pkgs;flags=f;for(String pkg:pkgs)o.onResult(pkg,0);o.backupFinished(0);return 0;}
 public void cancelBackups(){}public RestoreSession beginRestoreSession(){return null;}
}''',
            'org/hecker/vault/VaultTransport.java':'package org.hecker.vault;class VaultTransport {static final String NAME="org.hecker.vault/.VaultTransportService";}',
            'org/hecker/vault/VaultTransportService.java':'package org.hecker.vault;class VaultTransportService {}',
            'org/hecker/vault/VaultService.java':'package org.hecker.vault;class VaultService {static boolean busy;}',
            'org/hecker/vault/VaultStorage.java':'''package org.hecker.vault;import android.content.*;import java.util.*;
class VaultStorage {static final Prefs p=new Prefs();static SharedPreferences prefs(Context c){return p;}
 static class Prefs implements SharedPreferences {Map<String,Object> data=new HashMap<>();
  public boolean getBoolean(String k,boolean f){return (boolean)data.getOrDefault(k,f);}public String getString(String k,String f){return (String)data.getOrDefault(k,f);}
  public Editor edit(){return new Editor(){public Editor putBoolean(String k,boolean v){data.put(k,v);return this;}
   public Editor putString(String k,String v){data.put(k,v);return this;}public Editor remove(String k){data.remove(k);return this;}public boolean commit(){return true;}};}
 }}''',
            'org/hecker/vault/NativeProbe.java':'''package org.hecker.vault;import android.content.Context;import android.app.backup.BackupManager;import java.util.*;import java.io.*;
public class NativeProbe {static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args)throws Exception{Context c=new Context();
  for(int snapshot=1;snapshot<=2;snapshot++){
   VaultSession session=new VaultSession(new File("unused-private-stage"),snapshot,false,List.of("com.whatsapp"));
   try(VaultNative nativeBackup=new VaultNative(c,message->{})){nativeBackup.backup(session);
    check(Arrays.equals(BackupManager.targets,new String[]{"@pm@","com.whatsapp"}));check(BackupManager.flags==BackupManager.FLAG_NON_INCREMENTAL_BACKUP);check(session.results.get("com.whatsapp")==0);}
   check(BackupManager.current.equals("previous.android.provider"));check(!VaultStorage.p.getBoolean("transport_handoff",false));
  }
  BackupManager.enabled=false;try(VaultNative nativeBackup=new VaultNative(c,message->{})){
   try{nativeBackup.backup(new VaultSession(new File("unused"),3,false,List.of("com.whatsapp")));throw new AssertionError();}catch(IOException expected){}
  }
  check(BackupManager.current.equals("previous.android.provider"));
  System.out.println("Vault full-only snapshots include fresh package metadata every time and recover the original Android provider after success/failure: PASS");
 }
}'''
        }
        with tempfile.TemporaryDirectory(prefix='vault-native-') as temporary:
            folder=Path(temporary)
            for name,text in sources.items():
                path=folder/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(folder/'classes'),
                str(ROOT/'vault/src/org/hecker/vault/VaultNative.java'),str(ROOT/'vault/src/org/hecker/vault/VaultSession.java'),*map(str,folder.rglob('*.java'))],check=True)
            subprocess.run(['java','-cp',str(folder/'classes'),'org.hecker.vault.NativeProbe'],check=True,timeout=20)

if __name__=='__main__':unittest.main()
