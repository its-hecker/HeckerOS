package android.app;
public interface IActivityManager {
 String getSpoofGamePropsConfig() throws android.os.RemoteException;
 String getSpoofTrickyStoreTarget() throws android.os.RemoteException;
 String getSpoofTrickyStoreKeyBox() throws android.os.RemoteException;
 String getSpoofTrickyStorePatch() throws android.os.RemoteException;
 String getHeckerSpoofConfig(int kind) throws android.os.RemoteException;
 void setHeckerSpoofConfig(int kind,String value) throws android.os.RemoteException;
}
