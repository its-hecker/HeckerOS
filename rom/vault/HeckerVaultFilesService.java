// SPDX-License-Identifier: Apache-2.0
package com.android.server.pm;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInstalld;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.UserManager;
import java.io.File;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.hecker.vault.VaultFilesProtocol;

/** No general file API. Only the owner-user platform Vault can name a selected app/session. */
final class HeckerVaultFilesService extends Binder {
    private static final String VAULT = "org.hecker.vault";
    private final PackageManagerService pm;
    private final Context context;
    private final Object operations = new Object();
    private HeckerVaultFilesService(PackageManagerService pm) { this.pm = pm; context = pm.mContext; }
    static void publish(PackageManagerService pm) {
        if (ServiceManager.checkService(VaultFilesProtocol.SERVICE) == null)
            ServiceManager.addService(VaultFilesProtocol.SERVICE, new HeckerVaultFilesService(pm));
    }
    private ApplicationInfo authorize() throws Exception {
        context.enforceCallingPermission(Manifest.permission.BACKUP, "HeckerVault private files");
        int uid = Binder.getCallingUid();
        ApplicationInfo vault = context.getPackageManager().getApplicationInfo(VAULT, 0);
        if (uid < 10000 || uid >= 100000 || uid != vault.uid
                || (vault.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                || context.getPackageManager().checkSignatures("android", VAULT) != PackageManager.SIGNATURE_MATCH)
            throw new SecurityException("Owner-user platform HeckerVault only");
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) throw new IllegalStateException("Unlock before using private app data");
        return vault;
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(VaultFilesProtocol.DESCRIPTOR); return true; }
        if (code != VaultFilesProtocol.CALL) return super.onTransact(code, data, reply, flags);
        try {
            ApplicationInfo vault = authorize(); data.enforceInterface(VaultFilesProtocol.DESCRIPTOR);
            boolean restoring = data.readBoolean(); String pkg = data.readString(), id = data.readString();
            long version = data.readLong(); String[] signers = data.createStringArray(); data.enforceNoDataAvail();
            if (!VaultFilesProtocol.packageName(pkg) || !VaultFilesProtocol.session(id) || pkg.equals(VAULT) || version < 0
                    || signers == null || signers.length == 0 || signers.length > 32) throw new SecurityException("Invalid private-file selection");
            for (String signer : signers) if (signer == null || !signer.matches("[0-9a-f]{64}")) throw new SecurityException("Invalid signer");
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (operations) {
                    checkedPackage(vault, restoring, pkg, version, signers);
                    try (PackageFreezer frozen = new PackageFreezer(pkg, 0, "HeckerVault private files", pm, 0, null, true, true);
                         PackageManagerTracedLock lock = pm.mInstallLock.acquireLock()) {
                        transfer(vault, restoring, pkg, id, version, signers);
                    }
                }
            } finally { Binder.restoreCallingIdentity(identity); }
            reply.writeNoException(); return true;
        } catch (SecurityException denied) { throw denied; }
        catch (Exception failed) {
            // Do not expose native error strings: they may contain private filenames.
            android.util.Slog.w("HeckerVaultFiles", "Private operation failed: " + failed.getClass().getSimpleName());
            throw new ServiceSpecificException(1, "Private app-file operation failed");
        }
    }
    private void transfer(ApplicationInfo vault, boolean restore, String pkg, String id, long version, String[] signers) throws Exception {
        PackageInfo app = checkedPackage(vault, restore, pkg, version, signers);
        ApplicationInfo info = app.applicationInfo;
        String ce = "/data/user/0/" + pkg, de = "/data/user_de/0/" + pkg;
        String staging = vault.credentialProtectedDataDir + "/no_backup/staging/" + id;
        String captured = staging + "/file-capture/" + pkg;
        String restored = staging + "/file-restore/" + pkg;
        // Native traversal validates the actual staging path. system_server has
        // no DAC access to Vault's private directories and must not stat them.
        copy(restored, ce, info.uid, info.seInfo, VaultFilesProtocol.RECOVER, vault.uid);
        if (restore) {
            try { copy(restored, ce, info.uid, info.seInfo, VaultFilesProtocol.RESTORE, vault.uid); }
            catch (Exception failure) {
                try { copy(restored, ce, info.uid, info.seInfo, VaultFilesProtocol.RECOVER, vault.uid); }
                catch (Exception recovery) { failure.addSuppressed(recovery); }
                throw failure;
            }
        } else {
            copy(ce, captured + "/ce", vault.uid, vault.seInfo, VaultFilesProtocol.CAPTURE, info.uid);
            if (new File(de).exists()) copy(de, captured + "/de", vault.uid, vault.seInfo, VaultFilesProtocol.CAPTURE, info.uid);
        }
    }
    private PackageInfo checkedPackage(ApplicationInfo vault, boolean restore, String pkg, long version, String[] signers) throws Exception {
        PackageInfo app = context.getPackageManager().getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
        ApplicationInfo info = app.applicationInfo;
        String[] uidPackages = info == null ? null : context.getPackageManager().getPackagesForUid(info.uid);
        if (info == null || info.uid < 10000 || info.uid >= 100000 || info.uid == vault.uid || app.sharedUserId != null
                || uidPackages == null || uidPackages.length != 1 || !pkg.equals(uidPackages[0])
                || (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !pkg.equals("com.android.launcher3")
                || restore && app.getLongVersionCode() < version || !restore && app.getLongVersionCode() != version)
            throw new SecurityException("Package does not support private-file transfer");
        if (!signers(app).equals(new TreeSet<>(Arrays.asList(signers)))) throw new SecurityException("App signer changed");
        String ce = "/data/user/0/" + pkg, de = "/data/user_de/0/" + pkg;
        if (!ce.equals(info.credentialProtectedDataDir) || !de.equals(info.deviceProtectedDataDir)
                || !"/data/user/0/org.hecker.vault".equals(vault.credentialProtectedDataDir))
            throw new SecurityException("Only internal owner app storage is supported");
        return app;
    }
    private static Set<String> signers(PackageInfo info) throws Exception {
        if (info.signingInfo == null) throw new SecurityException("No signing identity");
        Set<String> values = new TreeSet<>();
        for (Signature signature : info.signingInfo.getApkContentsSigners()) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()); StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255)); values.add(hex.toString());
        }
        return values;
    }
    private void copy(String from, String to, int uid, String seInfo, int flags, int sourceUid) throws Exception {
        IBinder daemon = ServiceManager.checkService("installd");
        if (daemon == null) throw new IllegalStateException("Installer unavailable");
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        IBinder.DeathRecipient death = () -> done.completeExceptionally(new IllegalStateException("Installer stopped"));
        daemon.linkToDeath(death, 0);
        try {
            pm.mInstaller.copyAppDataPath(null, from, to, 0, uid, seInfo, flags, sourceUid,
                    new IInstalld.IAppDataOperationCallback.Stub() {
                        @Override public void onStatusChanged(int status, String message, String[] failedFiles) {
                            if (status == IInstalld.IAppDataOperationCallback.STATUS_SUCCESS) done.complete(true);
                            else if (status == IInstalld.IAppDataOperationCallback.STATUS_FAILURE) done.complete(false);
                        }
                    });
            // The native API is asynchronous and has no cancellation API. Keep the package
            // frozen until its callback or daemon death, even if the controller is cancelled.
            boolean interrupted = false;
            try {
                while (true) try { if (!done.get()) throw new IllegalStateException("Native copy failed"); return; }
                catch (InterruptedException cancelled) { interrupted = true; }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        } finally { daemon.unlinkToDeath(death, 0); }
    }
}
