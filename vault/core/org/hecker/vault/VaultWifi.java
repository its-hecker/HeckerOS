// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.*;
import java.util.*;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;

/** Reads only the Wi-Fi configurations contained in an authenticated local snapshot. */
final class VaultWifi {
    static final int MAX_BYTES=16*1024*1024,MAX_NETWORKS=4096;
    static final class Network {
        final String ssid,security;final boolean hidden;final char[] password;
        Network(String ssid,String security,boolean hidden,String password){
            this.ssid=unquote(ssid);this.security=security;this.hidden=hidden;this.password=unquote(password).toCharArray();
        }
        void clear(){Arrays.fill(password,'\0');}
    }
    private VaultWifi(){}
    static List<Network> read(File path)throws Exception{
        if(!path.isFile()||path.length()<=0||path.length()>MAX_BYTES)throw new IOException("Invalid Wi-Fi backup");
        try(InputStream input=new FileInputStream(path)){return parse(input);}
    }
    static List<Network> parse(InputStream input)throws Exception {
        byte[] data=input.readNBytes(MAX_BYTES+1);
        if(data.length==0||data.length>MAX_BYTES)throw new IOException("Invalid Wi-Fi backup size");
        Reader reader=new Reader();
        try {
            SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(false);
            XMLReader parser=factory.newSAXParser().getXMLReader();
            parser.setFeature("http://xml.org/sax/features/external-general-entities",false);
            parser.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            parser.setProperty("http://xml.org/sax/properties/lexical-handler",reader);
            parser.setEntityResolver(reader);parser.setContentHandler(reader);parser.setErrorHandler(reader);
            parser.parse(new InputSource(new ByteArrayInputStream(data)));
            if(!reader.root)throw new IOException("Unsupported Wi-Fi backup");
            return reader.networks;
        }catch(Exception failure){for(Network network:reader.networks)network.clear();throw failure;}
        finally{Arrays.fill(data,(byte)0);}
    }
    private static String unquote(String value){return value.length()>=2&&value.startsWith("\"")&&value.endsWith("\"")?value.substring(1,value.length()-1):value;}
    private static final class Reader extends DefaultHandler2 {
        final List<Network> networks=new ArrayList<>();final StringBuilder text=new StringBuilder();
        final List<String> wep=new ArrayList<>();
        boolean root,configuration,wepArray,hidden;int depth,wepIndex;String field="",ssid="",key="",configKey="";
        @Override public void startDTD(String name,String publicId,String systemId)throws SAXException{throw new SAXException("Wi-Fi backup must not contain a DTD");}
        @Override public InputSource resolveEntity(String publicId,String systemId)throws SAXException{throw new SAXException("External entities are not supported");}
        @Override public void fatalError(SAXParseException error)throws SAXException{throw error;}
        @Override public void startElement(String uri,String local,String name,Attributes attrs)throws SAXException{
            if(++depth>32)throw new SAXException("Wi-Fi backup nesting limit");
            if(depth==1){if(!name.equals("WifiBackupData"))throw new SAXException("Unsupported Wi-Fi backup");root=true;}
            if(name.equals("WifiConfiguration")){
                if(configuration)throw new SAXException("Nested Wi-Fi configuration");
                configuration=true;hidden=false;ssid=key=configKey="";wep.clear();wepIndex=0;
            }
            if(!configuration)return;
            String attribute=attrs.getValue("name");
            if(name.equals("string")){field=attribute==null?"":attribute;text.setLength(0);}
            if(name.equals("string-array")&&"WEPKeys".equals(attribute))wepArray=true;
            if(name.equals("item")&&wepArray){if(wep.size()>=4)throw new SAXException("Invalid WEP keys");String v=attrs.getValue("value");wep.add(v==null?"":v);}
            if("HiddenSSID".equals(attribute))hidden="true".equals(attrs.getValue("value"));
            if("WEPTxKeyIndex".equals(attribute))try{wepIndex=Integer.parseInt(attrs.getValue("value"));}catch(NumberFormatException invalid){throw new SAXException("Invalid WEP index");}
        }
        @Override public void characters(char[] value,int offset,int length)throws SAXException{
            if(!field.isEmpty()){if(text.length()+length>8192)throw new SAXException("Wi-Fi field limit");text.append(value,offset,length);}
        }
        @Override public void endElement(String uri,String local,String name)throws SAXException{
            if(name.equals("string")&&configuration){
                switch(field){case "SSID":ssid=text.toString();break;case "PreSharedKey":key=text.toString();break;case "ConfigKey":configKey=text.toString();break;default:break;}
                field="";text.setLength(0);
            }
            if(name.equals("string-array"))wepArray=false;
            if(name.equals("WifiConfiguration")){
                if(ssid.isEmpty()||networks.size()>=MAX_NETWORKS)throw new SAXException("Invalid Wi-Fi network list");
                String security="Open";
                if(configKey.endsWith("SAE"))security="WPA3";
                else if(configKey.endsWith("OWE"))security="Enhanced Open";
                else if(configKey.endsWith("WPA_PSK"))security="WPA/WPA2";
                else if(configKey.endsWith("WEP")){security="WEP";if(wepIndex>=0&&wepIndex<wep.size())key=wep.get(wepIndex);}
                else if(!key.isEmpty())security="Wi-Fi password";
                networks.add(new Network(ssid,security,hidden,key));configuration=false;ssid=key=configKey="";wep.clear();
            }
            depth--;
        }
    }
}
