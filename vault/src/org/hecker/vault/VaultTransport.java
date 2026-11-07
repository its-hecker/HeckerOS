// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.backup.*;
import android.content.pm.PackageInfo;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Native Android full/key-value transport; app sandboxes and signing checks remain native. */
final class VaultTransport extends BackupTransport {
    static final String NAME="org.hecker.vault/.VaultTransportService";
    static final long QUOTA=32L*1024*1024*1024;
    private VaultSession active;
    private ParcelFileDescriptor fullFd;
    private InputStream fullInput,restoreInput;
    private FileOutputStream fullOutput;
    private String fullPackage,restorePackage;
    private PackageInfo[] restorePackages;
    private int restorePosition;
    private long fullBytes;
    @Override public String name(){return NAME;}
    @Override public String transportDirName(){return "heckervault-v1";}
    @Override public String currentDestinationString(){return "HeckerVault encrypted local archive";}
    @Override public long requestBackupTime(){return VaultSession.current==null?3650L*86400000:0;}
    @Override public long requestFullBackupTime(){return requestBackupTime();}
    @Override public long getBackupQuota(String pkg,boolean full){return QUOTA;}
    @Override public int getTransportFlags(){
        VaultSession session=VaultSession.current;
        // Local migration archives use the app's device-transfer rules. Advertising
        // only cloud capabilities silently excludes data that is allowed for transfer.
        return BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED
                |(session==null||session.deviceTransfer?BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER:0);
    }
    private VaultSession backup()throws IOException{
        VaultSession s=VaultSession.current;if(s==null||s.restoring)throw new IOException("No active backup");s.check();return s;
    }
    @Override public synchronized int initializeDevice(){try{backup();return TRANSPORT_OK;}catch(IOException e){return TRANSPORT_ERROR;}}
    @Override public boolean isAppEligibleForBackup(PackageInfo info,boolean full){
        VaultSession s=VaultSession.current;return s!=null&&info!=null&&s.accepts(info.packageName);
    }
    @Override public synchronized int clearBackupData(PackageInfo info){
        // Never erase user archives in response to platform maintenance/transport initialization.
        return TRANSPORT_PACKAGE_REJECTED;
    }
    @Override public synchronized int performBackup(PackageInfo info,ParcelFileDescriptor fd,int flags){
        try{
            VaultSession s=backup();if(!s.accepts(info.packageName))return TRANSPORT_PACKAGE_REJECTED;
            if((flags&FLAG_INCREMENTAL)!=0)return TRANSPORT_NON_INCREMENTAL_BACKUP_REQUIRED;
            if((flags&FLAG_DATA_NOT_CHANGED)!=0)return s.data(info.packageName,"kv").isFile()?TRANSPORT_OK:TRANSPORT_NON_INCREMENTAL_BACKUP_REQUIRED;
            File temp=s.data(info.packageName,"kv.part");long size=0;
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor()));
                    FileOutputStream out=new FileOutputStream(temp)){
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){s.check();size+=n;if(size>QUOTA)throw new IOException("Quota exceeded");out.write(b,0,n);}out.getFD().sync();}
            File target=s.data(info.packageName,"kv");if(!temp.renameTo(target))throw new IOException("Cannot commit app backup");
            s.kinds.put(info.packageName,"kv");return TRANSPORT_OK;
        }catch(IOException|RuntimeException e){return TRANSPORT_ERROR;}
    }
    @Override public synchronized int performFullBackup(PackageInfo info,ParcelFileDescriptor fd,int flags){
        try{
            if(fullOutput!=null)return TRANSPORT_ERROR;
            VaultSession s=backup();if(!s.accepts(info.packageName))return TRANSPORT_PACKAGE_REJECTED;
            active=s;fullPackage=info.packageName;fullBytes=0;
            fullFd=ParcelFileDescriptor.dup(fd.getFileDescriptor());fullInput=new ParcelFileDescriptor.AutoCloseInputStream(fullFd);
            fullOutput=new FileOutputStream(s.data(fullPackage,"full.part"));return TRANSPORT_OK;
        }catch(IOException|RuntimeException e){closeFull();return TRANSPORT_ERROR;}
    }
    @Override public int checkFullBackupSize(long size){return size<0?TRANSPORT_ERROR:size>QUOTA?TRANSPORT_QUOTA_EXCEEDED:TRANSPORT_OK;}
    @Override public synchronized int sendBackupData(int bytes){
        if(fullOutput==null||bytes<=0)return TRANSPORT_ERROR;
        try{active.check();if((fullBytes+=bytes)>QUOTA)return TRANSPORT_QUOTA_EXCEEDED;
            byte[] b=new byte[Math.min(bytes,65536)];int left=bytes;while(left>0){active.check();int n=fullInput.read(b,0,Math.min(left,b.length));
                if(n<0)throw new EOFException();fullOutput.write(b,0,n);left-=n;}return TRANSPORT_OK;
        }catch(IOException|RuntimeException e){return TRANSPORT_ERROR;}
    }
    @Override public synchronized int finishBackup(){
        if(fullOutput==null){try{backup();return TRANSPORT_OK;}catch(IOException e){return TRANSPORT_ERROR;}}
        try{active.check();fullOutput.getFD().sync();fullOutput.close();fullOutput=null;
            if(!active.data(fullPackage,"full.part").renameTo(active.data(fullPackage,"full")))throw new IOException("Cannot commit app data");
            active.kinds.put(fullPackage,"full");return TRANSPORT_OK;
        }catch(IOException|RuntimeException e){return TRANSPORT_ERROR;}finally{closeFull();}
    }
    @Override public synchronized void cancelFullBackup(){
        try{if(active!=null&&fullPackage!=null){File f=active.data(fullPackage,"full.part");if(f.exists())f.delete();}}
        catch(IOException ignored){}finally{closeFull();}
    }
    private void closeFull(){try{if(fullInput!=null)fullInput.close();}catch(IOException ignored){}
        try{if(fullOutput!=null)fullOutput.close();}catch(IOException ignored){}
        fullInput=null;fullOutput=null;fullFd=null;fullPackage=null;active=null;}
    @Override public synchronized RestoreSet[] getAvailableRestoreSets(){VaultSession s=VaultSession.current;
        return s!=null&&s.restoring&&!s.cancelled?new RestoreSet[]{new RestoreSet("HeckerVault snapshot","HeckerOS",s.token,getTransportFlags())}:new RestoreSet[0];}
    @Override public long getCurrentRestoreSet(){VaultSession s=VaultSession.current;return s==null?0:s.token;}
    @Override public synchronized int startRestore(long token,PackageInfo[] packages){
        VaultSession s=VaultSession.current;if(s==null||!s.restoring||s.cancelled||s.token!=token)return TRANSPORT_ERROR;
        active=s;restorePackages=packages.clone();restorePosition=0;restorePackage=null;return TRANSPORT_OK;
    }
    @Override public synchronized RestoreDescription nextRestorePackage(){
        closeRestore();if(active==null||active.cancelled||restorePackages==null)return RestoreDescription.NO_MORE_PACKAGES;
        try{while(restorePosition<restorePackages.length){String pkg=restorePackages[restorePosition++].packageName;
            if(!active.accepts(pkg))continue;
            if(active.data(pkg,"kv").isFile()){restorePackage=pkg;return new RestoreDescription(pkg,RestoreDescription.TYPE_KEY_VALUE);}
            if(active.data(pkg,"full").isFile()){restorePackage=pkg;return new RestoreDescription(pkg,RestoreDescription.TYPE_FULL_STREAM);}
        }}catch(IOException ignored){}return RestoreDescription.NO_MORE_PACKAGES;
    }
    @Override public synchronized int getRestoreData(ParcelFileDescriptor fd){
        try{if(active==null||restorePackage==null)return TRANSPORT_ERROR;active.check();
            try(InputStream in=new FileInputStream(active.data(restorePackage,"kv"));
                    OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor()))){
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){active.check();out.write(b,0,n);}}
            return TRANSPORT_OK;
        }catch(IOException|RuntimeException e){return TRANSPORT_ERROR;}
    }
    @Override public synchronized int getNextFullRestoreDataChunk(ParcelFileDescriptor socket){
        try{if(active==null||restorePackage==null)return TRANSPORT_ERROR;active.check();
            if(restoreInput==null)restoreInput=new FileInputStream(active.data(restorePackage,"full"));
            byte[] b=new byte[65536];int n=restoreInput.read(b);if(n<0){closeRestore();return NO_MORE_DATA;}
            try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(socket.getFileDescriptor()))){out.write(b,0,n);}return n;
        }catch(IOException|RuntimeException e){closeRestore();return TRANSPORT_ERROR;}
    }
    private void closeRestore(){try{if(restoreInput!=null)restoreInput.close();}catch(IOException ignored){}restoreInput=null;}
    @Override public synchronized int abortFullRestore(){closeRestore();return TRANSPORT_OK;}
    @Override public synchronized void finishRestore(){closeRestore();active=null;restorePackages=null;restorePackage=null;}
    synchronized void closeStreams(){closeFull();finishRestore();}
}
