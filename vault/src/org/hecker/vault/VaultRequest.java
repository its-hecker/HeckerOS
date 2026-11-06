// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.net.Uri;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Passwords and restore requests stay in process memory, never in Intent extras or saved UI state. */
final class VaultRequest {
    static final Map<String,VaultRequest> pending=new ConcurrentHashMap<>();
    String preparedId;
    final String operation;final char[] password;final List<String> packages;
    final Set<String> categories;final Uri archive;final String mode;final boolean scheduled;
    final Set<VaultExternal.Part> externalParts;
    VaultRequest(String operation,char[] password,Collection<String> packages,Collection<String> categories,Uri archive,String mode,boolean scheduled){
        this(operation,password,packages,categories,archive,mode,scheduled,Collections.emptySet());
    }
    VaultRequest(String operation,char[] password,Collection<String> packages,Collection<String> categories,
            Uri archive,String mode,boolean scheduled,Collection<VaultExternal.Part> externalParts){
        this.operation=operation;this.password=password;this.packages=new ArrayList<>(packages);this.categories=new HashSet<>(categories);
        this.archive=archive;this.mode=mode;this.scheduled=scheduled;
        this.externalParts=Set.copyOf(externalParts);
    }
    String enqueue(){String id=UUID.randomUUID().toString();pending.put(id,this);return id;}
    void clear(){Arrays.fill(password,'\0');}
}
