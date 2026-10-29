// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;

/** Built-in platform-signed height RROs update native window, app, shade and keyguard insets together. */
final class StatusBarHeightController {
    private static final String[] PACKAGES={"org.hecker.statusbar.height.compact",
        "org.hecker.statusbar.height.balanced","org.hecker.statusbar.height.tall"};
    static final String ENABLED="hecker_dual_statusbar", HEIGHT="hecker_dual_statusbar_height";
    static boolean available(Context context){
        OverlayManager manager=context.getSystemService(OverlayManager.class);
        if(manager==null)return false;
        try{
            for(String name:PACKAGES){OverlayInfo info=manager.getOverlayInfo(name,Process.myUserHandle());
                if(info==null||!"android".equals(info.getTargetPackageName()))return false;}
            return true;
        }catch(RuntimeException unavailable){return false;}
    }
    private static void overlays(Context context,boolean enabled,int height){
        OverlayManager manager=context.getSystemService(OverlayManager.class);
        if(manager==null||!available(context))throw new IllegalStateException("Missing built-in status bar heights");
        OverlayManagerTransaction.Builder tx=new OverlayManagerTransaction.Builder();
        int user=Process.myUserHandle().getIdentifier();
        for(int i=0;i<PACKAGES.length;i++)tx.setEnabled(new OverlayIdentifier(PACKAGES[i]),enabled&&i==height,user);
        manager.commit(tx.build());
    }
    static boolean apply(Context context,boolean enabled,int height){
        height=Math.max(0,Math.min(2,height));
        int oldEnabled=Settings.Secure.getInt(context.getContentResolver(),ENABLED,0);
        int oldHeight=Math.max(0,Math.min(2,Settings.Secure.getInt(context.getContentResolver(),HEIGHT,0)));
        try{
            if(!available(context))return false;
            if(!Settings.Secure.putInt(context.getContentResolver(),HEIGHT,height)
                    ||!Settings.Secure.putInt(context.getContentResolver(),ENABLED,enabled?1:0))
                throw new SecurityException("Cannot save status bar layout");
            overlays(context,enabled,height);
            return true;
        }catch(RuntimeException unavailable){
            try{Settings.Secure.putInt(context.getContentResolver(),HEIGHT,oldHeight);
                Settings.Secure.putInt(context.getContentResolver(),ENABLED,oldEnabled);
                if(available(context))overlays(context,oldEnabled!=0,oldHeight);
            }catch(RuntimeException ignored){}
            return false;
        }
    }
    private StatusBarHeightController(){}
}
