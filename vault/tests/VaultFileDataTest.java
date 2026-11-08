// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

public final class VaultFileDataTest {
    interface Attempt {void run() throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Attempt attempt)throws Exception{try{attempt.run();throw new AssertionError("Expected rejection");}catch(IOException expected){}}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("vault-files-");String pkg="org.telegram.plus";
        try {
            Path raw=root.resolve("raw");Files.createDirectories(raw.resolve("ce/"+pkg+"/files/cache"));
            Files.createDirectories(raw.resolve("ce/"+pkg+"/databases"));Files.createDirectories(raw.resolve("ce/"+pkg+"/no_backup"));
            Files.createDirectories(raw.resolve("de/"+pkg+"/empty"));
            Files.writeString(raw.resolve("ce/"+pkg+"/databases/account 1 + 雪.db"),"database");
            Files.writeString(raw.resolve("ce/"+pkg+"/databases/account 1 + 雪.db-wal"),"write ahead log");
            Files.writeString(raw.resolve("ce/"+pkg+"/files/cache/keep.txt"),"nested cache is actual data");
            Files.writeString(raw.resolve("ce/"+pkg+"/no_backup/session.bin"),"private session");
            Files.writeString(raw.resolve("ce/"+pkg+"/files/run script"),"script");
            Files.setPosixFilePermissions(raw.resolve("ce/"+pkg+"/files/run script"),PosixFilePermissions.fromString("rwx------"));
            long modified=1600000000000L;Files.setLastModifiedTime(raw.resolve("ce/"+pkg+"/databases/account 1 + 雪.db"),FileTime.fromMillis(modified));
            Path data=root.resolve("packed");VaultFileData.Summary summary=VaultFileData.pack(raw,pkg,data,bytes->{});
            check(summary.files==5&&summary.bytes>0);check(!Files.exists(data.resolve("account 1 + 雪.db")));
            Path restored=root.resolve("restored");VaultFileData.unpack(data,pkg,restored,bytes->{});
            check(Files.readString(restored.resolve("ce/"+pkg+"/databases/account 1 + 雪.db")).equals("database"));
            check(Files.readString(restored.resolve("ce/"+pkg+"/databases/account 1 + 雪.db-wal")).equals("write ahead log"));
            check(Files.exists(restored.resolve("de/"+pkg+"/empty")));
            check(Files.getLastModifiedTime(restored.resolve("ce/"+pkg+"/databases/account 1 + 雪.db")).toMillis()==modified);
            check(Files.isExecutable(restored.resolve("ce/"+pkg+"/files/run script")));
            Path archive=root.resolve("archive");Files.createDirectories(archive.resolve("data/"+pkg));
            Files.move(data,archive.resolve("data/"+pkg+"/files"));Files.writeString(archive.resolve("snapshot.json"),"{}");
            ByteArrayOutputStream encrypted=new ByteArrayOutputStream();char[] password="private-file-test-password".toCharArray();
            VaultArchive.write(archive.toFile(),encrypted,password,bytes->{});Path imported=root.resolve("imported");Files.createDirectory(imported);
            VaultArchive.read(new ByteArrayInputStream(encrypted.toByteArray()),imported.toFile(),password,1000000,bytes->{});
            Path importedData=imported.resolve("data/"+pkg+"/files");check(VaultFileData.validate(importedData).files==5);
            byte[] index=Files.readAllBytes(importedData.resolve("index.bin"));
            Files.write(importedData.resolve("index.bin"),Arrays.copyOf(index,index.length-1));
            reject(()->VaultFileData.validate(importedData));Files.write(importedData.resolve("index.bin"),index);
            Files.writeString(importedData.resolve("blobs/99999999"),"undeclared");reject(()->VaultFileData.validate(importedData));Files.delete(importedData.resolve("blobs/99999999"));
            Files.delete(importedData.resolve("blobs/00000001"));Files.createSymbolicLink(importedData.resolve("blobs/00000001"),root.resolve("outside"));
            reject(()->VaultFileData.validate(importedData));
            Path bad=root.resolve("bad");Files.createDirectories(bad.resolve("blobs"));
            try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(bad.resolve("index.bin")))){
                out.writeInt(0x48565031);out.writeInt(2);byte[] name="ce/../../escape".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeShort(name.length);out.write(name);out.writeBoolean(true);out.writeBoolean(false);out.writeLong(0);out.writeInt(0);out.writeLong(0);
            }
            reject(()->VaultFileData.unpack(bad,pkg,root.resolve("must-not-exist"),bytes->{}));check(!Files.exists(root.resolve("must-not-exist")));
            check(VaultFilesProtocol.session("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));check(!VaultFilesProtocol.session("../other"));
            check(!VaultFilesProtocol.packageName("com.app/../android"));
            System.out.println("Private blobs: encrypted round-trip, SQLite WAL, Unicode names, empty folders, modes, tampering and traversal: PASS");
        }finally{
            try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
