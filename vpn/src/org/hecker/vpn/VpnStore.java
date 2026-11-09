// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credential-encrypted Android storage plus a non-exportable per-install wrapping key. */
final class VpnStore {
    private final AtomicFile file;
    final JSONObject data;
    VpnStore(Context context) throws Exception {
        file = new AtomicFile(new File(context.getFilesDir(), "profiles.enc"));
        if (file.getBaseFile().exists()) {
            byte[] encrypted = file.readFully();
            if (encrypted.length < 29 || encrypted.length > 4 * 1024 * 1024) throw new Exception("Invalid profile store");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOf(encrypted, 12)));
            data = new JSONObject(new String(cipher.doFinal(encrypted, 12, encrypted.length - 12), StandardCharsets.UTF_8));
            if (removeLegacyTcpProfiles(data)) save();
        } else data = new JSONObject().put("profiles", new JSONArray()).put("selected", "");
    }

    static boolean removeLegacyTcpProfiles(JSONObject data) throws JSONException {
        boolean changed = data.remove("tcp_catalog") != null;
        changed |= data.remove("tcp_catalog_time") != null;
        JSONArray profiles = data.optJSONArray("profiles");
        if (profiles == null) return changed;

        String selected = data.optString("selected");
        boolean selectionRemoved = false;
        for (int i = profiles.length() - 1; i >= 0; i--) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile != null && "tcp".equals(profile.optString("kind"))) {
                selectionRemoved |= selected.equals(profile.optString("id"));
                profiles.remove(i);
                changed = true;
            }
        }
        if (selectionRemoved) {
            data.put("selected", profiles.length() == 0 ? "" : profiles.getJSONObject(0).getString("id"));
        }
        return changed;
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias("heckervpn.profiles.v1")) return (SecretKey) store.getKey("heckervpn.profiles.v1", null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder("heckervpn.profiles.v1",
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    void save() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] clear = data.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try { encrypted = cipher.doFinal(clear); } finally { Arrays.fill(clear, (byte) 0); }
        FileOutputStream output = file.startWrite();
        try { output.write(cipher.getIV()); output.write(encrypted); file.finishWrite(output); }
        catch (Exception failure) { file.failWrite(output); throw failure; }
    }
    JSONObject profile(String id) throws Exception {
        JSONArray profiles = data.getJSONArray("profiles");
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.getJSONObject(i);
            if (profile.getString("id").equals(id)) return profile;
        }
        throw new IllegalArgumentException("Select a saved profile first");
    }
}
