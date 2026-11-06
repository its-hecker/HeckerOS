package android.app.backup;
import android.content.pm.PackageInfo;import android.os.ParcelFileDescriptor;
public class BackupTransport{
public static final int TRANSPORT_OK=0,NO_MORE_DATA=-1,TRANSPORT_ERROR=-1000,TRANSPORT_PACKAGE_REJECTED=-1002,TRANSPORT_QUOTA_EXCEEDED=-1005,TRANSPORT_NON_INCREMENTAL_BACKUP_REQUIRED=-1006;
public static final int FLAG_USER_INITIATED=1,FLAG_INCREMENTAL=2,FLAG_NON_INCREMENTAL=4,FLAG_DATA_NOT_CHANGED=8;
public String name(){return null;}public String transportDirName(){return null;}public String currentDestinationString(){return null;}
public long requestBackupTime(){return 0;}public long requestFullBackupTime(){return 0;}public long getBackupQuota(String p,boolean f){return 0;}public int getTransportFlags(){return 0;}
public int initializeDevice(){return -1;}public boolean isAppEligibleForBackup(PackageInfo p,boolean f){return false;}public int clearBackupData(PackageInfo p){return -1;}
public int performBackup(PackageInfo p,ParcelFileDescriptor f,int flags){return -1;}public int performFullBackup(PackageInfo p,ParcelFileDescriptor f,int flags){return -1;}
public int checkFullBackupSize(long size){return -1;}public int sendBackupData(int bytes){return -1;}public int finishBackup(){return -1;}public void cancelFullBackup(){}
public RestoreSet[] getAvailableRestoreSets(){return null;}public long getCurrentRestoreSet(){return 0;}public int startRestore(long token,PackageInfo[] packages){return -1;}
public RestoreDescription nextRestorePackage(){return null;}public int getRestoreData(ParcelFileDescriptor fd){return -1;}public int getNextFullRestoreDataChunk(ParcelFileDescriptor fd){return -1;}
public int abortFullRestore(){return -1;}public void finishRestore(){}
}
