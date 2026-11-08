// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import java.io.*;
import java.nio.file.*;
import org.json.*;

/** Owner-only private-file operations through a signature/UID checked system broker. */
final class VaultPrivateFiles {
    private static void call(Context context, File root, JSONObject app, boolean restoring) throws Exception {
        String id = root.getName(), pkg = app.getString("package");
        if (!VaultFilesProtocol.session(id) || !VaultFilesProtocol.packageName(pkg)
                || !root.getCanonicalFile().getParentFile().equals(new File(context.getNoBackupFilesDir(), "staging").getCanonicalFile()))
            throw new IOException("Invalid private file session");
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class)
                .invoke(null, VaultFilesProtocol.SERVICE);
        if (binder == null) throw new IOException("Private-file service is unavailable; install the matching HeckerOS build");
        JSONArray values = app.getJSONArray("signers"); String[] signers = new String[values.length()];
        for (int i = 0; i < signers.length; i++) signers[i] = values.getString(i);
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VaultFilesProtocol.DESCRIPTOR); data.writeBoolean(restoring);
            data.writeString(pkg); data.writeString(id); data.writeLong(app.getLong("version")); data.writeStringArray(signers);
            if (!binder.transact(VaultFilesProtocol.CALL, data, reply, 0)) throw new IOException("Private-file service protocol is unavailable");
            reply.readException();
        } catch (android.os.RemoteException | RuntimeException failure) {
            throw new IOException("Private-file " + (restoring ? "restore" : "backup") + " failed. Check HeckerVault and system package-manager logs.", failure);
        } finally { data.recycle(); reply.recycle(); }
    }
    static VaultFileData.Summary capture(Context context, File root, JSONObject app, VaultSession session) throws Exception {
        String pkg = app.getString("package");
        Path raw = root.toPath().resolve("file-capture/" + pkg), output = root.toPath().resolve("data/" + pkg + "/files");
        try {
            call(context, root, app, false); session.check();
            return VaultFileData.pack(raw, pkg, output, count -> session.check());
        } catch (Exception failure) { VaultEngine.cleanup(output.toFile()); throw failure; }
        finally { VaultEngine.cleanup(raw.toFile()); }
    }
    static void restore(Context context, File root, JSONObject app, VaultSession session) throws Exception {
        String pkg = app.getString("package"); Path raw = root.toPath().resolve("file-restore/" + pkg);
        try {
            VaultFileData.unpack(root.toPath().resolve("data/" + pkg + "/files"), pkg, raw, count -> session.check());
            session.check(); VaultPackages.checkInstalled(context, app); call(context, root, app, true);
        } finally { VaultEngine.cleanup(raw.toFile()); }
    }
}
