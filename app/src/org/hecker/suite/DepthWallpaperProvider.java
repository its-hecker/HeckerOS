// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Read-only active layers for SystemUI, this app and the same-user platform Hecker Launcher. */
public final class DepthWallpaperProvider extends ContentProvider {
    public static final String ID = "hecker_depth_wallpaper_id";
    @Override public boolean onCreate() { return true; }
    private void enforceReader() {
        int caller = Binder.getCallingUid();
        if (caller == getContext().getApplicationInfo().uid) return;
        String[] packages = getContext().getPackageManager().getPackagesForUid(caller);
        if (packages != null) for (String name : packages) {
            if ("com.android.systemui".equals(name) && getContext().getPackageManager()
                    .checkSignatures("android", name) == android.content.pm.PackageManager.SIGNATURE_MATCH) return;
            if ("com.android.launcher3".equals(name) && getContext().getPackageManager()
                    .checkSignatures("android", name) == android.content.pm.PackageManager.SIGNATURE_MATCH) {
                try {
                    android.content.pm.ApplicationInfo app = getContext().getPackageManager().getApplicationInfo(name, 0);
                    if (app.uid == caller && (app.flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM
                            | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                            && android.os.UserHandle.getUserHandleForUid(caller).equals(android.os.UserHandle.getUserHandleForUid(getContext().getApplicationInfo().uid))) return;
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
            }
        }
        throw new SecurityException("Depth assets are private to SystemUI and the preinstalled Hecker Launcher");
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceReader();
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        List<String> parts = uri.getPathSegments();
        if (parts.size() != 2 || !parts.get(0).matches("[a-f0-9]{32}")
                || !("background".equals(parts.get(1)) || "foreground".equals(parts.get(1))))
            throw new FileNotFoundException("Unknown depth asset");
        String active = Settings.Secure.getString(getContext().getContentResolver(), ID);
        if (!parts.get(0).equals(active)) throw new FileNotFoundException("Inactive depth pair");
        android.content.Context storage = ("ca2aca1da00000000000000000000001".equals(parts.get(0))
                || "ca2aca2da00000000000000000000002".equals(parts.get(0)))
                ? getContext().createDeviceProtectedStorageContext() : getContext();
        File file = new File(new File(new File(storage.getFilesDir(), "depth"), parts.get(0)),
                parts.get(1) + ".png");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { enforceReader(); return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] arguments, String sort) { enforceReader(); return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] arguments) {
        throw new UnsupportedOperationException();
    }
}
