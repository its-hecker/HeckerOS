// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Offline IEEE assignment hints, with longest-prefix matching and no private-MAC guesses. */
public final class NetLabVendors {
    private final Map<String,String> assignments;
    private NetLabVendors(Map<String,String> assignments){this.assignments=Collections.unmodifiableMap(assignments);}
    public static NetLabVendors empty(){return new NetLabVendors(Collections.emptyMap());}
    public static NetLabVendors read(Reader input) throws IOException {
        Map<String,String> entries=new HashMap<>();BufferedReader reader=new BufferedReader(input);String line;int bytes=0;
        while((line=reader.readLine())!=null){
            bytes+=line.length();if(bytes>12_000_000||entries.size()>120_000)throw new IOException("Registry exceeds bounds");
            if(line.isEmpty()||line.startsWith("#"))continue;
            int tab=line.indexOf('\t');if(tab!=6&&tab!=7&&tab!=9||line.length()>300)throw new IOException("Invalid assignment");
            String prefix=line.substring(0,tab),name=line.substring(tab+1);
            if(!prefix.matches("[0-9A-F]+")||name.isEmpty()||name.contains("\t")||entries.put(prefix,name)!=null)
                throw new IOException("Invalid or duplicate assignment");
        }
        return new NetLabVendors(entries);
    }
    public String lookup(String mac){
        String normalized=normalize(mac);if(normalized.isEmpty()||(Integer.parseInt(normalized.substring(0,2),16)&3)!=0
                ||normalized.equals("000000000000"))return "";
        for(int length:new int[]{9,7,6}){
            String name=assignments.get(normalized.substring(0,length));
            if(name!=null){String upper=name.toUpperCase(Locale.ROOT);
                return upper.equals("PRIVATE")||upper.contains("IEEE REGISTRATION AUTHORITY")?"":name;}
        }
        return "";
    }
    public static boolean privateMac(String mac){String normalized=normalize(mac);return !normalized.isEmpty()&&(Integer.parseInt(normalized.substring(0,2),16)&2)!=0;}
    private static String normalize(String mac){
        if(mac==null)return "";
        if(!mac.matches("(?i)([0-9a-f]{12}|[0-9a-f]{2}(:[0-9a-f]{2}){5}|[0-9a-f]{2}(-[0-9a-f]{2}){5})"))return "";
        return mac.replace(":","").replace("-","").toUpperCase(Locale.ROOT);
    }
}
