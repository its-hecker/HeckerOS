// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.util.*;

/** Private UI index. Restore always verifies the encrypted archive, never this cache. */
final class VaultHistory {
    static synchronized JSONObject load(Context c){
        android.content.SharedPreferences p=VaultStorage.prefs(c);
        if(!p.getString("history_folder","").equals(p.getString("folder","")))return new JSONObject();
        try{return new JSONObject(p.getString("app_history","{}"));}catch(JSONException ignored){return new JSONObject();}
    }
    static synchronized void record(Context c,Uri archive,JSONObject snapshot){
        try{JSONObject history=load(c);JSONArray apps=snapshot.getJSONArray("apps");long time=snapshot.getLong("created");
            for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");JSONObject old=history.optJSONObject(pkg);
                if(old!=null&&old.optLong("time")>time)continue;
                history.put(pkg,new JSONObject().put("version",app.getLong("version")).put("time",time)
                        .put("uri",archive.toString()).put("data",!app.optString("data").isEmpty()));}
            save(c,history);
        }catch(JSONException ignored){/* The completed archive is authoritative if this optional index fails. */}
    }
    static synchronized void forget(Context c,Uri archive){
        JSONObject history=load(c);List<String> remove=new ArrayList<>();Iterator<String> keys=history.keys();
        while(keys.hasNext()){String pkg=keys.next();if(archive.toString().equals(history.optJSONObject(pkg).optString("uri")))remove.add(pkg);}
        for(String pkg:remove)history.remove(pkg);save(c,history);
    }
    static synchronized void reconcile(Context c,List<VaultStorage.Archive> archives){
        Set<String> available=new HashSet<>();for(VaultStorage.Archive a:archives)available.add(a.uri.toString());
        JSONObject history=load(c);List<String> remove=new ArrayList<>();Iterator<String> keys=history.keys();
        while(keys.hasNext()){String pkg=keys.next();if(!available.contains(history.optJSONObject(pkg).optString("uri")))remove.add(pkg);}
        for(String pkg:remove)history.remove(pkg);save(c,history);
    }
    private static void save(Context c,JSONObject value){VaultStorage.prefs(c).edit().putString("app_history",value.toString())
            .putString("history_folder",VaultStorage.prefs(c).getString("folder","")).apply();}
}
