# SPDX-License-Identifier: Apache-2.0
"""Run the exact MediaProvider adapter with hostile paths and caller identities."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from vault_storage_port import vault_storage_plan,MEDIA,ANCHOR,PATCH
from prepare import replace_guarded

class VaultMediaTest(unittest.TestCase):
    def test_guarded_integration_apply_twice_revert_and_upstream_change(self):
        with tempfile.TemporaryDirectory() as temporary:
            rom=Path(temporary);provider=rom/MEDIA/'MediaProvider.java';provider.parent.mkdir(parents=True)
            original='class MediaProvider {\n'+ANCHOR+'return false;\n}\n}\n';provider.write_text(original)
            def apply(changes):
                for path,text in changes.items():
                    if text is None:path.unlink()
                    else:path.write_text(text)
            apply(vault_storage_plan(rom,replace_guarded));self.assertIn(PATCH,provider.read_text())
            self.assertEqual(vault_storage_plan(rom,replace_guarded),{})
            apply(vault_storage_plan(rom,replace_guarded,True));self.assertEqual(provider.read_text(),original)
            self.assertFalse((rom/MEDIA/'HeckerVaultMediaAccess.java').exists())
            provider.write_text(original.replace('shouldBypassFuseRestrictions','changedUpstream'))
            with self.assertRaises(ValueError):vault_storage_plan(rom,replace_guarded)

    def test_only_trusted_owner_vault_can_access_installed_package_media(self):
        sources={
            'android/content/Context.java':'''package android.content;import android.content.pm.PackageManager;
public class Context {public boolean mtp=true,backup=true;public final PackageManager pm=new PackageManager();
 public PackageManager getPackageManager(){return pm;}
 public int checkPermission(String p,int pid,int uid){return p.endsWith("ACCESS_MTP")?(mtp?0:-1):(backup?0:-1);}
}''',
            'android/content/pm/PackageManager.java':'''package android.content.pm;
public class PackageManager {
 public static final int PERMISSION_GRANTED=0,SIGNATURE_MATCH=0;public boolean trusted=true,installed=true;public int vaultUid=10203;
 public int checkSignatures(String a,String b){return trusted?0:-1;}
 public int getPackageUid(String pkg,int flags)throws NameNotFoundException{return vaultUid;}
 public Object getApplicationInfo(String pkg,int flags)throws NameNotFoundException{if(!installed)throw new NameNotFoundException();return new Object();}
 public static class NameNotFoundException extends Exception{}
}''',
            'com/android/providers/media/MediaProbe.java':'''package com.android.providers.media;import android.content.Context;
public class MediaProbe {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  String base="/storage/emulated/0/Android/media/com.whatsapp",file=base+"/WhatsApp/Media/a photo.jpg";Context c=new Context();
  check(HeckerVaultMediaAccess.allowed(c,10203,"org.hecker.vault",base));check(HeckerVaultMediaAccess.allowed(c,10203,"org.hecker.vault",file));
  for(String path:new String[]{"/data/user/0/com.whatsapp",base.replace("/media/","/data/"),base.replace("/0/","/10/"),
   "/storage/1234-5678/Android/media/com.whatsapp",base+"/../com.example/a",base+"//a",base+"/./a",base+"/x\\\\a",base+"/x\\0a",
   "/storage/emulated/0/Android/media/",null})check(!HeckerVaultMediaAccess.allowed(c,10203,"org.hecker.vault",path));
  check(!HeckerVaultMediaAccess.allowed(c,10204,"org.hecker.vault",file));check(!HeckerVaultMediaAccess.allowed(c,10203,"com.fake.vault",file));
  check(!HeckerVaultMediaAccess.allowed(c,110203,"org.hecker.vault",file));c.pm.trusted=false;check(!HeckerVaultMediaAccess.allowed(c,10203,"org.hecker.vault",file));c.pm.trusted=true;
  c.mtp=false;check(!HeckerVaultMediaAccess.allowed(c,10203,"org.hecker.vault",file));c.mtp=true;c.backup=false;
  check(!HeckerVaultMediaAccess.allowed(c,10203,"org.hecker.vault",file));c.backup=true;c.pm.installed=false;
  check(!HeckerVaultMediaAccess.allowed(c,10203,"org.hecker.vault",file));
  System.out.println("Vault MediaProvider adapter: allowed owner media, rejected private paths, traversal, other users, forged identities and missing permissions: PASS");
 }
}'''
        }
        with tempfile.TemporaryDirectory(prefix='vault-media-') as temporary:
            folder=Path(temporary)
            for name,text in sources.items():
                path=folder/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(folder/'classes'),
                str(ROOT/'rom/media/HeckerVaultMediaAccess.java'),*map(str,folder.rglob('*.java'))],check=True)
            subprocess.run(['java','-cp',str(folder/'classes'),'com.android.providers.media.MediaProbe'],check=True,timeout=20)

if __name__=='__main__':unittest.main()
