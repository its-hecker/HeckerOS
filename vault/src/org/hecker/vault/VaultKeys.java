// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Device-bound wrapping only for optional scheduling. Archive recovery always uses the user's password. */
final class VaultKeys {
    private static final String ALIAS="HeckerVaultScheduleV1";
    static void remember(Context c,char[] pass)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUnlockedDeviceRequired(true).build());generator.generateKey();}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,store.getKey(ALIAS,null));
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        ByteBuffer encoded=StandardCharsets.UTF_8.encode(CharBuffer.wrap(pass));byte[] plain=new byte[encoded.remaining()];encoded.get(plain);
        try{byte[] encrypted=cipher.doFinal(plain);byte[] envelope=ByteBuffer.allocate(12+encrypted.length).put(cipher.getIV()).put(encrypted).array();
            VaultStorage.prefs(c).edit().putString("schedule_secret",Base64.encodeToString(envelope,Base64.NO_WRAP)).commit();}
        finally{Arrays.fill(plain,(byte)0);if(encoded.hasArray())Arrays.fill(encoded.array(),(byte)0);}
    }
    static char[] recalled(Context c)throws Exception{
        String wrapped=VaultStorage.prefs(c).getString("schedule_secret",null);if(wrapped==null)throw new GeneralSecurityException("Set a password for scheduled backups");
        byte[] envelope=Base64.decode(wrapped,Base64.NO_WRAP);if(envelope.length<28)throw new GeneralSecurityException("Invalid scheduled-backup key");
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,store.getKey(ALIAS,null),new GCMParameterSpec(128,envelope,0,12));cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        byte[] plain=cipher.doFinal(envelope,12,envelope.length-12);
        try{CharBuffer text=StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));char[] pass=new char[text.remaining()];text.get(pass);
            if(text.hasArray())Arrays.fill(text.array(),'\0');return pass;}finally{Arrays.fill(plain,(byte)0);}
    }
    static void forget(Context c){VaultStorage.prefs(c).edit().remove("schedule_secret").apply();
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}catch(Exception ignored){} }
}
