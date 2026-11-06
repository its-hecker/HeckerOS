// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned streaming format: every chunk and the final empty chunk are authenticated. */
public final class VaultCipher {
    public static final int CHUNK = 256 * 1024;
    private static final int ROUNDS = 310_000;
    private static final byte[] MAGIC = {'H','V','L','T',0,0,0,1};
    private VaultCipher() {}

    private static byte[] key(char[] password, byte[] salt) throws GeneralSecurityException {
        if (password == null || password.length < 12 || password.length > 1024)
            throw new GeneralSecurityException("Use a password with 12 to 1024 characters");
        PBEKeySpec spec = new PBEKeySpec(password, salt, ROUNDS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    private static byte[] crypt(int mode, byte[] key, byte[] header, long counter,
            byte[] bytes, int length, int plainLength) throws GeneralSecurityException, IOException {
        if (counter >= 0xffff_ffffL) throw new IOException("Archive is too large");
        byte[] nonce = ByteBuffer.allocate(12).put(header, 24, 8).putInt((int) counter).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(header);
        cipher.updateAAD(ByteBuffer.allocate(12).putLong(counter).putInt(plainLength).array());
        return cipher.doFinal(bytes, 0, length);
    }
    public static OutputStream encrypt(OutputStream destination, char[] password)
            throws IOException, GeneralSecurityException {
        byte[] salt = new byte[16], nonce = new byte[8];
        SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce);
        byte[] header = ByteBuffer.allocate(40).put(MAGIC).put(salt).put(nonce)
                .putInt(ROUNDS).putInt(CHUNK).array();
        byte[] derived = key(password, salt);
        try { destination.write(header); }
        catch (IOException e) { Arrays.fill(derived, (byte)0); throw e; }
        return new OutputStream() {
            final DataOutputStream out = new DataOutputStream(destination);
            final byte[] buffer = new byte[CHUNK]; int used; long sequence; boolean closed;
            void emit(int size) throws IOException {
                try {
                    byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, derived, header, sequence++, buffer, size, size);
                    out.writeInt(size); out.write(encrypted); Arrays.fill(encrypted,(byte)0);
                    Arrays.fill(buffer,0,used,(byte)0); used=0;
                } catch (GeneralSecurityException e) { throw new IOException("Cannot encrypt archive", e); }
            }
            @Override public void write(int b) throws IOException { write(new byte[]{(byte)b},0,1); }
            @Override public void write(byte[] b,int off,int len) throws IOException {
                if (closed) throw new IOException("Archive closed");
                if (off<0||len<0||off>b.length-len) throw new IndexOutOfBoundsException();
                while (len>0) { int n=Math.min(CHUNK-used,len); System.arraycopy(b,off,buffer,used,n);
                    off+=n;len-=n;used+=n;if(used==CHUNK)emit(used); }
            }
            @Override public void flush() throws IOException { out.flush(); }
            @Override public void close() throws IOException {
                if(closed)return;closed=true;
                try { if(used>0)emit(used);emit(0);out.flush(); }
                finally { Arrays.fill(derived,(byte)0);Arrays.fill(buffer,(byte)0);out.close(); }
            }
        };
    }
    /** Caller must consume to EOF: the final authenticated chunk detects truncation. */
    public static InputStream decrypt(InputStream source,char[] password)
            throws IOException,GeneralSecurityException {
        DataInputStream in=new DataInputStream(source);byte[] header=new byte[40];in.readFully(header);
        if(!MessageDigest.isEqual(MAGIC,Arrays.copyOf(header,8))
                ||ByteBuffer.wrap(header,32,8).getInt()!=ROUNDS
                ||ByteBuffer.wrap(header,36,4).getInt()!=CHUNK)throw new IOException("Unsupported HeckerVault archive");
        byte[] derived=key(password,Arrays.copyOfRange(header,8,24));
        return new InputStream(){
            byte[] plain=new byte[0];int position;long sequence;boolean ended,closed;
            boolean load()throws IOException{
                if(ended)return false;
                int length;try{length=in.readInt();}catch(EOFException e){throw new IOException("Incomplete archive",e);}
                if(length<0||length>CHUNK)throw new IOException("Invalid archive chunk");
                byte[] encrypted=new byte[length+16];in.readFully(encrypted);
                try{plain=crypt(Cipher.DECRYPT_MODE,derived,header,sequence++,encrypted,encrypted.length,length);}
                catch(GeneralSecurityException e){throw new IOException("Wrong password or damaged archive",e);}
                finally{Arrays.fill(encrypted,(byte)0);}
                position=0;
                if(length==0){ended=true;if(in.read()!=-1)throw new IOException("Unexpected archive tail");return false;}
                return true;
            }
            @Override public int read()throws IOException{byte[] b=new byte[1];return read(b)==-1?-1:b[0]&255;}
            @Override public int read(byte[] b,int off,int len)throws IOException{
                if(closed)throw new IOException("Archive closed");
                if(off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();if(len==0)return 0;
                if(position==plain.length){Arrays.fill(plain,(byte)0);if(!load())return -1;}
                int n=Math.min(len,plain.length-position);System.arraycopy(plain,position,b,off,n);position+=n;return n;
            }
            @Override public void close()throws IOException{if(closed)return;closed=true;
                Arrays.fill(derived,(byte)0);Arrays.fill(plain,(byte)0);in.close();}
        };
    }
}
