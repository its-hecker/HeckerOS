// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Portable encrypted ZIP. Authenticated and fully checked in private staging before restore. */
public final class VaultArchive {
    public static final int MAX_FILES=25000;
    public static final long MAX_BYTES=128L*1024*1024*1024;
    private static final String INDEX="vault-index.properties";
    private VaultArchive(){}
    public interface Progress { void bytes(long count)throws IOException; }
    public static String hash(File file)throws IOException{
        MessageDigest md=digest();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;
            while((n=in.read(b))!=-1)md.update(b,0,n);}
        return hex(md.digest());
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    public static String safe(String name)throws IOException{
        if(name==null||name.isEmpty()||name.length()>512||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf('\0')>=0)
            throw new IOException("Unsafe archive path");
        for(String p:name.split("/",-1))if(p.isEmpty()||p.equals(".")||p.equals("..")||!p.matches("[A-Za-z0-9_@.=-]+"))
            throw new IOException("Unsafe archive path");
        return name;
    }
    private static void files(File root,File directory,List<File> result)throws IOException{
        File[] children=directory.listFiles();if(children==null)throw new IOException("Cannot read backup staging");
        Arrays.sort(children,Comparator.comparing(File::getName));
        for(File f:children){if(Files.isSymbolicLink(f.toPath()))throw new IOException("Links are not supported");
            if(f.isDirectory())files(root,f,result);else if(f.isFile()){
                safe(root.toPath().relativize(f.toPath()).toString());result.add(f);
                if(result.size()>MAX_FILES)throw new IOException("Too many backup files");
            }else throw new IOException("Unsupported backup file");}
    }
    public static void write(File root,OutputStream output,char[] password,Progress progress)throws Exception{
        List<File> files=new ArrayList<>();files(root,root,files);Properties index=new Properties();index.setProperty("format","1");
        long total=0;for(File f:files){String name=root.toPath().relativize(f.toPath()).toString();
            if(name.equals(INDEX))throw new IOException("Reserved archive path");total=Math.addExact(total,f.length());}
        if(total>MAX_BYTES)throw new IOException("Backup is too large");
        try(ZipOutputStream zip=new ZipOutputStream(VaultCipher.encrypt(output,password))){
            byte[] b=new byte[65536];long copied=0;
            for(File f:files){String name=safe(root.toPath().relativize(f.toPath()).toString());
                zip.putNextEntry(new ZipEntry(name));MessageDigest md=digest();long size=0;
                try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))!=-1){
                    zip.write(b,0,n);md.update(b,0,n);size+=n;copied+=n;if(copied>MAX_BYTES)throw new IOException("Backup exceeds limit");progress.bytes(copied);}}
                zip.closeEntry();index.setProperty(name,size+":"+hex(md.digest()));
            }
            ByteArrayOutputStream encodedIndex=new ByteArrayOutputStream();index.store(encodedIndex,"HeckerVault integrity index");
            if(encodedIndex.size()>4*1024*1024)throw new IOException("Backup index is too large");
            zip.putNextEntry(new ZipEntry(INDEX));encodedIndex.writeTo(zip);zip.closeEntry();
        }
    }
    public static void read(InputStream input,File destination,char[] password,long byteLimit,Progress progress)throws Exception{
        if(!destination.isDirectory()||Objects.requireNonNull(destination.list()).length!=0)
            throw new IOException("Restore needs empty private staging");
        long limit=Math.min(MAX_BYTES,byteLimit),total=0;Map<String,String> actual=new HashMap<>();byte[] indexBytes=null;
        // ZipInputStream stops before the encrypted final chunk. Explicitly drain the cipher afterwards.
        try(InputStream decoded=VaultCipher.decrypt(input,password)){
            ZipInputStream zip=new ZipInputStream(decoded);byte[] b=new byte[65536];ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){String name=safe(entry.getName());
                if(entry.isDirectory()||actual.containsKey(name)||name.equals(INDEX)&&indexBytes!=null)
                    throw new IOException("Duplicate or unsupported archive entry");
                if(actual.size()>=MAX_FILES)throw new IOException("Too many restore files");
                MessageDigest md=digest();long size=0;File target=new File(destination,name);
                if(!target.getCanonicalPath().startsWith(destination.getCanonicalPath()+File.separator))throw new IOException("Unsafe archive path");
                boolean isIndex=name.equals(INDEX);ByteArrayOutputStream index=isIndex?new ByteArrayOutputStream():null;
                if(!isIndex&&!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cannot prepare restore directory");
                try(OutputStream out=isIndex?index:new FileOutputStream(target)){int n;
                    while((n=zip.read(b))!=-1){size+=n;total+=n;
                        if(total>limit||isIndex&&size>4*1024*1024)throw new IOException("Restore exceeds available space or archive limit");
                        out.write(b,0,n);md.update(b,0,n);progress.bytes(total);}}
                zip.closeEntry();if(isIndex)indexBytes=index.toByteArray();else actual.put(name,size+":"+hex(md.digest()));
            }
            while(decoded.read(b)!=-1){} // Verify final GCM chunk and EOF before trusting ANY extracted file.
        }
        if(indexBytes==null||!actual.containsKey("snapshot.json"))throw new IOException("Incomplete HeckerVault snapshot");
        Properties expected=new Properties();expected.load(new ByteArrayInputStream(indexBytes));
        if(!"1".equals(expected.remove("format"))||expected.size()!=actual.size())throw new IOException("Invalid archive index");
        for(Map.Entry<String,String> e:actual.entrySet())if(!e.getValue().equals(expected.getProperty(e.getKey())))throw new IOException("Archive integrity check failed");
    }
    public static void delete(File file)throws IOException{
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("Refusing backup link");
        if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("Cannot inspect staging");for(File child:children)delete(child);}
        if(file.exists()&&!file.delete())throw new IOException("Cannot remove private staging");
    }
}
