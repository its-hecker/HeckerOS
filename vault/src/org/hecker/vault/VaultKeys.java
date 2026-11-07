// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;

/** One vault password, wrapped locally by Keystore. Portable archives still need that password. */
final class VaultKeys {
    // Reuse the previous scheduling alias so existing installations can migrate without losing keys.
    private static final String ALIAS="HeckerVaultScheduleV1";
    static boolean configured(Context c){return VaultStorage.prefs(c).contains("vault_secret")
            ||VaultStorage.prefs(c).contains("schedule_secret");}
    private static KeyStore store()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);return s;}
    private static String wrap(char[] pass)throws Exception{
        if(pass.length<12||pass.length>1024)throw new GeneralSecurityException("Invalid vault password length");
        KeyStore store=store();
        if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUnlockedDeviceRequired(true).build());generator.generateKey();}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,store.getKey(ALIAS,null));
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        ByteBuffer encoded=StandardCharsets.UTF_8.encode(CharBuffer.wrap(pass));byte[] plain=new byte[encoded.remaining()];encoded.get(plain);
        try{byte[] encrypted=cipher.doFinal(plain);byte[] envelope=ByteBuffer.allocate(12+encrypted.length).put(cipher.getIV()).put(encrypted).array();
            return Base64.encodeToString(envelope,Base64.NO_WRAP);}
        finally{Arrays.fill(plain,(byte)0);if(encoded.hasArray())Arrays.fill(encoded.array(),(byte)0);}
    }
    private static char[] unwrap(String wrapped)throws Exception{
        if(wrapped==null||wrapped.length()>8192)throw new GeneralSecurityException("Set up Vault encryption first");
        byte[] envelope=Base64.decode(wrapped,Base64.NO_WRAP);if(envelope.length<28)throw new GeneralSecurityException("Invalid saved vault key");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,store().getKey(ALIAS,null),new GCMParameterSpec(128,envelope,0,12));cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        byte[] plain=cipher.doFinal(envelope,12,envelope.length-12);
        try{CharBuffer text=StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));char[] pass=new char[text.remaining()];text.get(pass);
            if(text.hasArray())Arrays.fill(text.array(),'\0');return pass;}finally{Arrays.fill(plain,(byte)0);}
    }
    static synchronized char[] recalled(Context c)throws Exception{
        android.content.SharedPreferences p=VaultStorage.prefs(c);
        String secret=p.getString("vault_secret",null);
        if(secret==null){secret=p.getString("schedule_secret",null);
            if(secret!=null&&!p.edit().putString("vault_secret",secret).remove("schedule_secret").commit())
                throw new GeneralSecurityException("Cannot save vault encryption settings");}
        return unwrap(secret);
    }
    private static JSONArray previous(Context c)throws Exception{
        JSONArray old=new JSONArray(VaultStorage.prefs(c).getString("vault_previous_secrets","[]"));
        if(old.length()>32)throw new GeneralSecurityException("Too many saved archive passwords");return old;
    }
    static synchronized void remember(Context c,char[] pass)throws Exception{
        android.content.SharedPreferences p=VaultStorage.prefs(c);JSONArray old=previous(c);
        if(configured(c)){
            char[] before=recalled(c);
            try{if(Arrays.equals(pass,before))return;
                if(old.length()>=32)throw new GeneralSecurityException("Saved archive password limit reached");
                old.put(p.getString("vault_secret",null));}
            finally{Arrays.fill(before,'\0');}
        }
        if(!p.edit().putString("vault_secret",wrap(pass)).putString("vault_previous_secrets",old.toString())
                .remove("schedule_secret").commit())throw new GeneralSecurityException("Cannot save vault encryption settings");
    }
    static synchronized List<char[]> candidates(Context c)throws Exception{
        List<char[]> result=new ArrayList<>();
        try{result.add(recalled(c));JSONArray old=previous(c);for(int i=old.length()-1;i>=0;i--)result.add(unwrap(old.getString(i)));return result;}
        catch(Exception e){for(char[] pass:result)Arrays.fill(pass,'\0');throw e;}
    }
    static synchronized void rememberImported(Context c,char[] pass)throws Exception{
        List<char[]> known=candidates(c);
        try{for(char[] existing:known)if(Arrays.equals(existing,pass))return;}
        finally{for(char[] existing:known)Arrays.fill(existing,'\0');}
        JSONArray old=previous(c);if(old.length()>=32)throw new GeneralSecurityException("Saved archive password limit reached");
        old.put(wrap(pass));if(!VaultStorage.prefs(c).edit().putString("vault_previous_secrets",old.toString()).commit())
            throw new GeneralSecurityException("Cannot remember archive password");
    }
}
