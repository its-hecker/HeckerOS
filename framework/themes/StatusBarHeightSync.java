// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.server.theme;

import android.content.Context;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

/** Mirrors the foreground user's height into system_server/SystemUI's framework resources.
 * Runs on the theme service worker, including when switching to a user whose switch is off.
 * Preferences stay per-user; only these three built-in overlays are touched.
 */
final class StatusBarHeightSync {
    private static final String[] PACKAGES={"org.hecker.statusbar.height.compact",
        "org.hecker.statusbar.height.balanced","org.hecker.statusbar.height.tall"};
    private static final int SYSTEM_USER=0; // UserHandle.USER_SYSTEM (hidden from the system SDK)
    private final Context context;
    StatusBarHeightSync(Context context){this.context=context;}
    void sync(int user){
        int preset=Math.max(0,Math.min(2,Settings.Secure.getIntForUser(
                context.getContentResolver(),"hecker_dual_statusbar_height",0,user)));
        int mode=Settings.Secure.getIntForUser(context.getContentResolver(),
                "hecker_dual_statusbar",0,user)!=0?preset:-1;
        try{
            OverlayManager manager=context.getSystemService(OverlayManager.class);
            if(manager==null)return;
            int[] targets=user==SYSTEM_USER?new int[]{user}:new int[]{SYSTEM_USER,user};
            OverlayManagerTransaction.Builder tx=new OverlayManagerTransaction.Builder();
            boolean correct=true;
            for(int target:targets)for(int i=0;i<PACKAGES.length;i++){
                OverlayInfo info=manager.getOverlayInfo(PACKAGES[i],UserHandle.of(target));
                if(info==null||!"android".equals(info.getTargetPackageName()))return;
                correct &= info.isEnabled()==(i==mode);
                tx.setEnabled(new OverlayIdentifier(PACKAGES[i]),i==mode,target);
            }
            if(!correct)manager.commit(tx.build());
        }catch(RuntimeException error){Log.w("HeckerStatusBarHeight","Cannot synchronize native height",error);}
    }
}
