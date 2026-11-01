// Compile-only schema constants reviewed at Launcher3 c612e6ece389f21c40f8cb9cd9a4b44239f00009.
package com.android.launcher3;
public final class LauncherSettings {
    public interface Favorites extends android.provider.BaseColumns {
        String TITLE="title", INTENT="intent", ITEM_TYPE="itemType", CONTAINER="container",
                SCREEN="screen", CELLX="cellX", CELLY="cellY", SPANX="spanX", SPANY="spanY",
                PROFILE_ID="profileId", RESTORED="restored";
        int ITEM_TYPE_APPLICATION=0, CONTAINER_DESKTOP=-100;
    }
}
