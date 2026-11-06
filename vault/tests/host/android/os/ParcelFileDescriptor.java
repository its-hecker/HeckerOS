package android.os;
import java.io.*;import java.util.*;
/** Host FD adapter only: duplicate wrappers preserve stream position and ownership. */
public class ParcelFileDescriptor implements AutoCloseable{
private static final Map<FileDescriptor,ParcelFileDescriptor> files=new IdentityHashMap<>();
final FileInputStream input;final FileOutputStream output;final boolean owner;
public ParcelFileDescriptor(File file,boolean write)throws IOException{input=write?null:new FileInputStream(file);output=write?new FileOutputStream(file):null;owner=true;files.put(getFileDescriptor(),this);}
private ParcelFileDescriptor(ParcelFileDescriptor original){input=original.input;output=original.output;owner=false;}
public FileDescriptor getFileDescriptor(){try{return input!=null?input.getFD():output.getFD();}catch(IOException e){throw new RuntimeException(e);}}
public static ParcelFileDescriptor dup(FileDescriptor fd)throws IOException{ParcelFileDescriptor original=files.get(fd);if(original==null)throw new IOException("Unregistered host FD");return new ParcelFileDescriptor(original);}
public void close()throws IOException{if(owner){files.remove(getFileDescriptor());if(input!=null)input.close();if(output!=null)output.close();}}
public static class AutoCloseInputStream extends InputStream{final ParcelFileDescriptor p;public AutoCloseInputStream(ParcelFileDescriptor p){this.p=p;}public int read()throws IOException{return p.input.read();}public int read(byte[] b,int o,int n)throws IOException{return p.input.read(b,o,n);}public void close()throws IOException{p.close();}}
public static class AutoCloseOutputStream extends OutputStream{final ParcelFileDescriptor p;public AutoCloseOutputStream(ParcelFileDescriptor p){this.p=p;}public void write(int b)throws IOException{p.output.write(b);}public void write(byte[] b,int o,int n)throws IOException{p.output.write(b,o,n);}public void close()throws IOException{p.close();}}
}
