// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Owner metadata in credential-encrypted private app storage; discovery history is opt-in. */
final class NetLabPreferences {
    private final SharedPreferences preferences;
    private JSONObject metadata;
    NetLabPreferences(Context context) {
        preferences = context.getSharedPreferences("netlab_private", Context.MODE_PRIVATE);
        try { String raw=preferences.getString("metadata", "{}"); metadata=raw.length()>96*1024?new JSONObject():new JSONObject(raw); }
        catch (JSONException | ClassCastException invalid) { metadata=new JSONObject(); }
    }
    String label(String id, String fallback) { JSONObject item=metadata.optJSONObject(id);return item==null?fallback:item.optString("name",fallback); }
    boolean trusted(String id) { JSONObject item=metadata.optJSONObject(id);return item!=null&&item.optBoolean("trusted"); }
    boolean history() { return preferences.getBoolean("history_enabled",false); }
    void history(boolean enabled) { preferences.edit().putBoolean("history_enabled",enabled).apply();if(!enabled)clearHistory(); }
    void save(String id,String name,boolean trusted) {
        try { JSONObject item=metadata.optJSONObject(id);if(item==null)item=new JSONObject();
            item.put("name",name.trim().substring(0,Math.min(name.trim().length(),64)));item.put("trusted",trusted);
            metadata.put(id,item);trim();persist();
        } catch(JSONException ignored) { /* Only locally bounded strings/booleans are written. */ }
    }
    void seen(String id,String address,long when) {
        if(!history())return;
        try {JSONObject item=metadata.optJSONObject(id);if(item==null)item=new JSONObject();
            if(when-item.optLong("last")<30_000&&address.equals(item.optString("address")))return;
            item.put("last",when);item.put("address",address);metadata.put(id,item);trim();persist();
        }catch(JSONException ignored){}
    }
    List<android.os.Bundle> remembered() {
        List<android.os.Bundle> result=new ArrayList<>();if(!history())return result;
        long cutoff=System.currentTimeMillis()-7*24*60*60*1000L;
        for(Iterator<String> it=metadata.keys();it.hasNext();){String id=it.next();JSONObject item=metadata.optJSONObject(id);
            if(item==null||item.optLong("last")<cutoff)continue;
            android.os.Bundle device=new android.os.Bundle();device.putString("id",id);device.putBoolean("online",false);
            device.putBoolean("managed",id.startsWith("hotspot:"));device.putString("mac",id.startsWith("hotspot:")?id.substring(8):"");
            device.putString("state","history");device.putLong("last",item.optLong("last"));
            device.putStringArrayList("addresses",new ArrayList<>(Collections.singletonList(item.optString("address",""))));result.add(device);
        }return result;
    }
    void clearHistory(){
        for(Iterator<String> it=metadata.keys();it.hasNext();){JSONObject item=metadata.optJSONObject(it.next());if(item!=null){item.remove("last");item.remove("address");}}
        trim();persist();
    }
    void clearAll(){metadata=new JSONObject();preferences.edit().remove("metadata").putBoolean("history_enabled",false).apply();}
    private void trim(){
        List<String> remove=new ArrayList<>();long cutoff=System.currentTimeMillis()-7*24*60*60*1000L;
        for(Iterator<String> it=metadata.keys();it.hasNext();){String key=it.next();JSONObject item=metadata.optJSONObject(key);
            if(item==null)remove.add(key);else{if(item.optLong("last")<cutoff){item.remove("last");item.remove("address");}
                if(item.optString("name").isEmpty()&&!item.optBoolean("trusted")&&!item.has("last"))remove.add(key);}}
        for(String key:remove)metadata.remove(key);
        if(metadata.length()>128){List<String> keys=new ArrayList<>();for(Iterator<String> it=metadata.keys();it.hasNext();)keys.add(it.next());
            keys.sort(Comparator.<String>comparingInt(k->{JSONObject item=metadata.optJSONObject(k);return item.optBoolean("trusted")||!item.optString("name").isEmpty()?1:0;})
                    .thenComparingLong(k->metadata.optJSONObject(k).optLong("last")));
            for(int i=0;i<keys.size()-128;i++)metadata.remove(keys.get(i));}
    }
    private void persist(){preferences.edit().putString("metadata",metadata.toString()).apply();}
    int download(){return preferences.getInt("download_kbps",2048);}int upload(){return preferences.getInt("upload_kbps",512);}
    void preset(int down,int up){preferences.edit().putInt("download_kbps",down).putInt("upload_kbps",up).apply();}
}
