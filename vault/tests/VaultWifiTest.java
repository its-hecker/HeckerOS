// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class VaultWifiTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Wi-Fi check "+checks);}
    private static List<VaultWifi.Network> read(String xml)throws Exception{return VaultWifi.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));}
    private static String configuration(String ssid,String fields){return "<Network><WifiConfiguration><string name=\"SSID\">&quot;"+ssid+"&quot;</string>"+fields+"</WifiConfiguration></Network>";}
    private static String document(String networks){return "<?xml version=\"1.0\" encoding=\"utf-8\"?><WifiBackupData><float name=\"Version\" value=\"1.2\"/><NetworkList>"+networks+"</NetworkList></WifiBackupData>";}
    private static void rejects(String xml)throws Exception{try{read(xml);throw new AssertionError("Accepted invalid Wi-Fi backup");}catch(org.xml.sax.SAXException|IOException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        String secured=configuration("Test &amp; اردو", "<string name=\"ConfigKey\">&quot;Test&quot;WPA_PSK</string><string name=\"PreSharedKey\">&quot;a &amp; b password&quot;</string><boolean name=\"HiddenSSID\" value=\"true\"/>");
        String open=configuration("Guest", "<null name=\"PreSharedKey\"/><null name=\"WEPKeys\"/><string name=\"ConfigKey\">&quot;Guest&quot;NONE</string>");
        String wep=configuration("Legacy", "<string name=\"ConfigKey\">&quot;Legacy&quot;WEP</string><string-array name=\"WEPKeys\" num=\"4\"><item value=\"\"/><item value=\"1234567890\"/><item value=\"\"/><item value=\"\"/></string-array><int name=\"WEPTxKeyIndex\" value=\"1\"/>");
        List<VaultWifi.Network> networks=read(document(secured+open+wep));
        check(networks.size()==3);check(networks.get(0).ssid.equals("Test & اردو"));check(networks.get(0).security.equals("WPA/WPA2"));check(networks.get(0).hidden);
        check(new String(networks.get(0).password).equals("a & b password"));check(networks.get(1).password.length==0);check(!networks.get(1).hidden);
        check(networks.get(2).security.equals("WEP"));check(new String(networks.get(2).password).equals("1234567890"));
        for(VaultWifi.Network network:networks){network.clear();for(char c:network.password)check(c==0);}
        check(read(document("")).isEmpty());
        rejects("<Other/>\n");rejects(document(secured).replace("</NetworkList>",""));
        rejects("<!DOCTYPE WifiBackupData [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+document(secured).substring(document(secured).indexOf("<WifiBackupData>")));
        rejects("<!DOCTYPE WifiBackupData [<!ENTITY x 'expanded'>]>"+document(secured).substring(document(secured).indexOf("<WifiBackupData>")));
        rejects(document(configuration("x".repeat(8193),"")));
        rejects(document(secured.repeat(VaultWifi.MAX_NETWORKS+1)));
        rejects(document("<Network><WifiConfiguration><null name=\"SSID\"/></WifiConfiguration></Network>"));
        System.out.println("Vault saved Wi-Fi parser: "+checks+" checks PASS (Unicode, credentials, WEP, clearing, DTD rejection and bounds)");
    }
}
