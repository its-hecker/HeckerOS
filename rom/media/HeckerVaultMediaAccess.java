// SPDX-License-Identifier: Apache-2.0
package com.android.providers.media;

import android.content.Context;
import android.content.pm.PackageManager;

/** Local ROM backup access, restricted to package folders under the owner's Android/media. */
final class HeckerVaultMediaAccess {
    private static final String VAULT="org.hecker.vault";
    private static final String PREFIX="/storage/emulated/0/Android/media/";
    private HeckerVaultMediaAccess() {}

    static String owner(String path) {
        if(path==null||!path.startsWith(PREFIX)||path.indexOf('\\')>=0||path.indexOf('\0')>=0)return null;
        String relative=path.substring(PREFIX.length());
        String[] segments=relative.split("/",-1);
        if(segments.length==0||!segments[0].matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))return null;
        for(String segment:segments)if(segment.isEmpty()||segment.equals(".")||segment.equals(".."))return null;
        return segments[0];
    }
    static boolean allowed(Context context,int uid,String caller,String path) {
        String owner=owner(path);
        if(owner==null||!VAULT.equals(caller)||uid<10000||uid>=100000)return false;
        PackageManager pm=context.getPackageManager();
        if(context.checkPermission("android.permission.ACCESS_MTP",-1,uid)!=PackageManager.PERMISSION_GRANTED
                ||context.checkPermission("android.permission.BACKUP",-1,uid)!=PackageManager.PERMISSION_GRANTED
                ||pm.checkSignatures("android",VAULT)!=PackageManager.SIGNATURE_MATCH)return false;
        try{
            // A package-name claim is insufficient; check the calling UID and installed owner too.
            return pm.getPackageUid(VAULT,0)==uid&&pm.getApplicationInfo(owner,0)!=null;
        }catch(PackageManager.NameNotFoundException absent){return false;}
    }
}
