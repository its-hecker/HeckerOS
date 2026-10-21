/* SPDX-License-Identifier: Apache-2.0 */
package android.util;
import java.io.*;import java.nio.file.*;
public class AtomicFile {private final File file,backup;public AtomicFile(File f){file=f;backup=new File(f+".old");}public File getBaseFile(){return file;}
public FileInputStream openRead() throws IOException{return new FileInputStream(file);}
public FileOutputStream startWrite() throws IOException {if(file.exists())Files.copy(file.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);return new FileOutputStream(file);}
public void finishWrite(FileOutputStream s){try{s.close();Files.deleteIfExists(backup.toPath());}catch(IOException e){throw new RuntimeException(e);}}
public void failWrite(FileOutputStream s){try{if(s!=null)s.close();if(backup.exists())Files.move(backup.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);else Files.deleteIfExists(file.toPath());}catch(IOException e){throw new RuntimeException(e);}}}
