// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Runs production crypto/archive code, including malformed/untrusted archives. */
public final class VaultArchiveTest {
    private static final char[] PASSWORD="HeckerVault test password 2026".toCharArray();
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    interface Action{void run()throws Exception;}
    private static void rejected(Action action)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed);}
    private static File directory()throws IOException{return Files.createTempDirectory("heckervault-test-").toFile();}
    private static byte[] archive(File root)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();VaultArchive.write(root,out,PASSWORD,n->{});return out.toByteArray();}
    private static void read(byte[] bytes,char[] pass,long limit)throws Exception{File root=directory();try{VaultArchive.read(new ByteArrayInputStream(bytes),root,pass,limit,n->{});}finally{VaultArchive.delete(root);}}
    private static byte[] encryptedZip(String name,byte[] content)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(VaultCipher.encrypt(out,PASSWORD))){zip.putNextEntry(new ZipEntry(name));zip.write(content);zip.closeEntry();}return out.toByteArray();}
    public static void main(String[] args)throws Exception{
        File root=directory(),restored=directory();
        try{Files.writeString(new File(root,"snapshot.json").toPath(),"{\"id\":\"private metadata\"}");
            File app=new File(root,"apps/org.example.app");check(app.mkdirs());byte[] payload=new byte[900123];new Random(2).nextBytes(payload);Files.write(new File(app,"base.apk").toPath(),payload);
            Files.createDirectories(new File(root,"data/org.example.app").toPath());Files.writeString(new File(root,"data/org.example.app/full").toPath(),"private app data — Unicode حافظہ");
            byte[] archive=archive(root);check(archive.length>3*VaultCipher.CHUNK);check(!Arrays.equals(archive,archive(root)));
            VaultArchive.read(new ByteArrayInputStream(archive),restored,PASSWORD,16*1024*1024,n->{});
            check(Arrays.equals(payload,Files.readAllBytes(new File(restored,"apps/org.example.app/base.apk").toPath())));
            check(Files.readString(new File(restored,"data/org.example.app/full").toPath()).contains("حافظہ"));
            check(VaultArchive.size(root)>payload.length);
            for(int level:new int[]{VaultArchive.FASTEST,VaultArchive.BALANCED,VaultArchive.SMALLEST}){
                File copy=directory();try{ByteArrayOutputStream out=new ByteArrayOutputStream();long[] copied={0};
                    VaultArchive.write(root,out,PASSWORD,level,n->{check(n>=copied[0]);copied[0]=n;});
                    check(copied[0]==VaultArchive.size(root));VaultArchive.read(new ByteArrayInputStream(out.toByteArray()),copy,PASSWORD,16*1024*1024,n->{});
                    check(Arrays.equals(payload,Files.readAllBytes(new File(copy,"apps/org.example.app/base.apk").toPath())));
                }finally{VaultArchive.delete(copy);}}
            rejected(()->VaultArchive.write(root,new ByteArrayOutputStream(),PASSWORD,42,n->{}));
            rejected(()->read(archive,"incorrect password 12345".toCharArray(),16*1024*1024));
            for(int position:new int[]{0,8,24,32,39,44,archive.length/2,archive.length-1}){byte[] changed=archive.clone();changed[position]^=0x11;rejected(()->read(changed,PASSWORD,16*1024*1024));}
            for(int end:new int[]{0,7,39,40,44,100,archive.length/2,archive.length-1,archive.length-20}){
                byte[] truncated=Arrays.copyOf(archive,end);rejected(()->read(truncated,PASSWORD,16*1024*1024));}
            byte[] appended=Arrays.copyOf(archive,archive.length+1);rejected(()->read(appended,PASSWORD,16*1024*1024));
            // Identical-sized chunks swapped: the counter is authenticated in AAD and nonce.
            int frame=4+VaultCipher.CHUNK+16;byte[] reordered=archive.clone();System.arraycopy(archive,40+frame,reordered,40,frame);System.arraycopy(archive,40,reordered,40+frame,frame);
            rejected(()->read(reordered,PASSWORD,16*1024*1024));
            rejected(()->read(archive,PASSWORD,1000));
            for(String name:new String[]{"../escaped","/absolute","a/../../escaped","a\\escaped","a//b","a/./b","a/../b","a/"}){
                byte[] bad=encryptedZip(name,new byte[]{1});rejected(()->read(bad,PASSWORD,1024));}
            byte[] noIndex=encryptedZip("snapshot.json",new byte[]{1});rejected(()->read(noIndex,PASSWORD,1024));
            rejected(()->{ByteArrayOutputStream out=new ByteArrayOutputStream();VaultArchive.write(root,out,"short".toCharArray(),n->{});});
            Files.createSymbolicLink(new File(root,"link").toPath(),new File(root,"snapshot.json").toPath());rejected(()->archive(root));Files.delete(new File(root,"link").toPath());
            rejected(()->VaultArchive.read(new ByteArrayInputStream(archive),restored,PASSWORD,16*1024*1024,n->{}));
            check(VaultArchive.safe("data/@pm@/kv").equals("data/@pm@/kv"));
            // Cancellation is propagated through the real streaming exporter.
            rejected(()->{ByteArrayOutputStream out=new ByteArrayOutputStream();VaultArchive.write(root,out,PASSWORD,n->{throw new IOException("cancelled");});});
            check(!Files.exists(root.toPath().getParent().resolve("escaped")));
        }finally{VaultArchive.delete(root);VaultArchive.delete(restored);}
        System.out.println("HeckerVault production encryption/archive adversarial checks: "+checks+" PASS");
    }
}
