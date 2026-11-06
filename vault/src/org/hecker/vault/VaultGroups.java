// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Named, portable selections. No credentials or Android global settings are stored here. */
final class VaultGroups {
    static JSONObject load(Context c)throws Exception{
        JSONObject groups=new JSONObject(VaultStorage.prefs(c).getString("groups","{}"));validate(groups);return groups;
    }
    static void validate(JSONObject groups)throws Exception{
        if(groups==null)return;if(groups.length()>64)throw new IOException("Too many saved app groups");
        Iterator<String> names=groups.keys();int members=0;
        while(names.hasNext()){String name=names.next();if(!validName(name))throw new IOException("Invalid app group name");JSONObject group=groups.getJSONObject(name);
            JSONArray apps=group.getJSONArray("apps"),categories=group.getJSONArray("categories");
            if(apps.length()>10000||categories.length()>VaultSystemData.CATEGORIES.size()||(members+=apps.length())>50000)throw new IOException("App groups are too large");
            Set<String> seen=new HashSet<>();for(int i=0;i<apps.length();i++){String pkg=apps.getString(i);
                if(!VaultSession.validPackage(pkg)||pkg.equals("@pm@")||!seen.add(pkg))throw new IOException("Invalid app group member");}
            seen.clear();for(int i=0;i<categories.length();i++){String category=categories.getString(i);
                if(!VaultSystemData.CATEGORIES.contains(category)||!seen.add(category))throw new IOException("Invalid group category");}
        }
    }
    static boolean validName(String name){return name!=null&&name.equals(name.trim())&&!name.isEmpty()&&name.length()<=40&&!name.matches(".*[\\p{Cntrl}].*");}
    static void save(Context c,String name,Collection<String> apps,Collection<String> categories)throws Exception{
        if(!validName(name))throw new IOException("Use a group name of 1–40 characters");JSONObject groups=load(c);
        groups.put(name,new JSONObject().put("apps",new JSONArray(apps)).put("categories",new JSONArray(categories)));validate(groups);
        VaultStorage.prefs(c).edit().putString("groups",groups.toString()).apply();
    }
    static void remove(Context c,String name)throws Exception{JSONObject groups=load(c);groups.remove(name);VaultStorage.prefs(c).edit().putString("groups",groups.toString()).apply();}
    static void merge(Context c,JSONObject imported)throws Exception{
        if(imported==null)return;validate(imported);JSONObject current=load(c);Iterator<String> names=imported.keys();
        while(names.hasNext()){String name=names.next();if(!current.has(name))current.put(name,imported.getJSONObject(name));}
        validate(current);VaultStorage.prefs(c).edit().putString("groups",current.toString()).apply();
    }
}
