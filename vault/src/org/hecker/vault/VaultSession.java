// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Only the local foreground worker can create a live transport session. Never reads public paths. */
final class VaultSession {
    static volatile VaultSession current;
    final File root; final long token; final boolean restoring;
    final Set<String> packages;
    final Map<String,Integer> results=new ConcurrentHashMap<>();
    final Map<String,String> kinds=new ConcurrentHashMap<>();
    volatile boolean cancelled;
    boolean deviceTransfer=true;
    VaultSession(File root,long token,boolean restoring,Collection<String> packages){
        this.root=root;this.token=token;this.restoring=restoring;
        this.packages=Collections.unmodifiableSet(new HashSet<>(packages));
    }
    boolean accepts(String pkg){return !cancelled&&(pkg.equals("@pm@")||packages.contains(pkg))&&validPackage(pkg);}
    static boolean validPackage(String pkg){return pkg!=null&&(pkg.equals("@pm@")||pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"));}
    File data(String pkg,String suffix)throws IOException{
        if(!accepts(pkg)||!suffix.matches("kv|full|kv.part|full.part"))throw new IOException("Package outside backup session");
        File dir=new File(root,"data/"+pkg);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create backup staging");
        return new File(dir,suffix);
    }
    void check()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Operation cancelled");}
}
