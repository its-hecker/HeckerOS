# SPDX-License-Identifier: Apache-2.0
"""Exercise production credential migration/reset and actual encrypted archive recovery on the host."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import sys
import json

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from compile_sdk import fetch

class VaultCredentialsTest(unittest.TestCase):
    def test_password_setup_reset_migration_and_failed_save(self):
        sources={
            'android/content/Context.java':'package android.content; public class Context {}',
            'android/content/SharedPreferences.java':'''package android.content;
public interface SharedPreferences {
 boolean contains(String key);String getString(String key,String fallback);Editor edit();
 interface Editor {Editor putString(String key,String value);Editor remove(String key);boolean commit();}
}''',
            'android/util/Base64.java':'''package android.util; public class Base64 {
 public static final int NO_WRAP=2;
 public static String encodeToString(byte[] value,int flags){return java.util.Base64.getEncoder().encodeToString(value);}
 public static byte[] decode(String value,int flags){return java.util.Base64.getDecoder().decode(value);}
}''',
            'org/hecker/vault/VaultStorage.java':'''package org.hecker.vault;
import android.content.*;import java.util.*;
final class VaultStorage {
 static final Prefs data=new Prefs();static SharedPreferences prefs(Context c){return data;}
 static class Prefs implements SharedPreferences {
  final Map<String,String> values=new HashMap<>();boolean refuse;
  public boolean contains(String key){return values.containsKey(key);}
  public String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
  public Editor edit(){return new Editor(){Map<String,String> changes=new HashMap<>();Set<String> removed=new HashSet<>();
   public Editor putString(String key,String value){changes.put(key,value);return this;}
   public Editor remove(String key){removed.add(key);return this;}
   public boolean commit(){if(refuse)return false;for(String key:removed)values.remove(key);values.putAll(changes);return true;}
  };}
 }
}''',
            'org/hecker/vault/CredentialsProbe.java':'''package org.hecker.vault;
import android.content.Context;import java.io.*;import java.security.*;import java.security.cert.Certificate;
import java.util.*;import javax.crypto.*;import javax.crypto.spec.SecretKeySpec;
public final class CredentialsProbe {
 public static final class Provider extends java.security.Provider {
  public Provider(){super("AndroidKeyStore",1.0,"Host Keystore boundary");put("KeyStore.AndroidKeyStore",MemoryStore.class.getName());}
 }
 public static final class MemoryStore extends KeyStoreSpi {
  static final Key key=new SecretKeySpec(new byte[32],"AES");
  public Key engineGetKey(String alias,char[] password){return key;}
  public Certificate[] engineGetCertificateChain(String alias){return null;}
  public Certificate engineGetCertificate(String alias){return null;}
  public Date engineGetCreationDate(String alias){return new Date();}
  public void engineSetKeyEntry(String alias,Key key,char[] pass,Certificate[] cert){}
  public void engineSetKeyEntry(String alias,byte[] key,Certificate[] cert){}
  public void engineSetCertificateEntry(String alias,Certificate cert){}
  public void engineDeleteEntry(String alias){}
  public Enumeration<String> engineAliases(){return Collections.enumeration(List.of("HeckerVaultScheduleV1"));}
  public boolean engineContainsAlias(String alias){return alias.equals("HeckerVaultScheduleV1");}
  public int engineSize(){return 1;}
  public boolean engineIsKeyEntry(String alias){return true;}
  public boolean engineIsCertificateEntry(String alias){return false;}
  public String engineGetCertificateAlias(Certificate cert){return null;}
  public void engineStore(OutputStream out,char[] pass){}
  public void engineLoad(InputStream in,char[] pass){}
 }
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void same(char[] a,char[] b){check(Arrays.equals(a,b));Arrays.fill(a,'\\0');}
 public static void main(String[] args)throws Exception {
  Security.addProvider(new Provider());Context context=new Context();
  char[] original="original vault password".toCharArray(),replacement="replacement vault password".toCharArray();
  check(!VaultKeys.configured(context));VaultKeys.remember(context,original);check(VaultKeys.configured(context));same(VaultKeys.recalled(context),original);
  check(!VaultStorage.data.values.toString().contains(new String(original)));
  ByteArrayOutputStream archive=new ByteArrayOutputStream();try(OutputStream cipher=VaultCipher.encrypt(archive,original)){cipher.write("saved backup".getBytes());}
  VaultKeys.remember(context,replacement);same(VaultKeys.recalled(context),replacement);
  List<char[]> known=VaultKeys.candidates(context);check(known.size()==2);
  same(known.get(0),replacement);
  try(InputStream cipher=VaultCipher.decrypt(new ByteArrayInputStream(archive.toByteArray()),known.get(1))){check(new String(cipher.readAllBytes()).equals("saved backup"));}
  Arrays.fill(known.get(1),'\\0');
  VaultKeys.rememberImported(context,"imported older password".toCharArray());
  VaultKeys.rememberImported(context,"imported older password".toCharArray());
  known=VaultKeys.candidates(context);check(known.size()==3);for(char[] pass:known)Arrays.fill(pass,'\\0');same(VaultKeys.recalled(context),replacement);
  Map<String,String> before=new HashMap<>(VaultStorage.data.values);VaultStorage.data.refuse=true;
  try{VaultKeys.remember(context,"must not become current".toCharArray());throw new AssertionError();}catch(GeneralSecurityException expected){}
  check(VaultStorage.data.values.equals(before));VaultStorage.data.refuse=false;same(VaultKeys.recalled(context),replacement);
  // Migration of the old optional scheduler password must retain archive recovery.
  VaultStorage.data.values.put("schedule_secret",VaultStorage.data.values.remove("vault_secret"));
  same(VaultKeys.recalled(context),replacement);check(!VaultStorage.data.values.containsKey("schedule_secret"));
  String good=VaultStorage.data.values.get("vault_secret");byte[] bad=java.util.Base64.getDecoder().decode(good);bad[bad.length-1]^=1;
  VaultStorage.data.values.put("vault_secret",java.util.Base64.getEncoder().encodeToString(bad));
  try{VaultKeys.recalled(context);throw new AssertionError();}catch(GeneralSecurityException expected){}
  System.out.println("Vault password reset preserves archive recovery, migration, deduplication, authenticated wrapping and atomic save: PASS");
 }
}'''
        }
        sdk=ROOT/'generated/sdk';sdk.mkdir(parents=True,exist_ok=True)
        fetch(sdk,'android-system.jar',json.loads((ROOT/'tests/sdk-inputs.lock.json').read_text())['android-system.jar'])
        fetch(sdk,'xmorph-json-20240303.jar',json.loads((ROOT/'tests/xmorph-inputs.lock.json').read_text())['xmorph-json-20240303.jar'])
        with tempfile.TemporaryDirectory(prefix='vault-credentials-') as temporary:
            folder=Path(temporary)
            for name,content in sources.items():
                path=folder/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
            cp=str(sdk/'xmorph-json-20240303.jar')+':'+str(sdk/'android-system.jar')
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',cp,'-d',str(folder/'classes'),
                *map(str,folder.rglob('*.java')),str(ROOT/'vault/src/org/hecker/vault/VaultKeys.java'),
                str(ROOT/'vault/core/org/hecker/vault/VaultCipher.java')],check=True)
            subprocess.run(['java','-cp',str(folder/'classes')+':'+cp,'org.hecker.vault.CredentialsProbe'],check=True,timeout=30)

if __name__=='__main__':unittest.main()
