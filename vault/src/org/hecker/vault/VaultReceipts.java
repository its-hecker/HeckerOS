// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;

/** Bounded per-record restore receipts avoid loading a large message history into preferences. */
final class VaultReceipts extends SQLiteOpenHelper {
    VaultReceipts(Context c){super(c,"restore-receipts.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE mms_receipts (source TEXT PRIMARY KEY, target INTEGER NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported receipt schema");}
    long target(String source){try(Cursor cursor=getReadableDatabase().query("mms_receipts",new String[]{"target"},"source=?",new String[]{source},null,null,null)){
        return cursor.moveToFirst()?cursor.getLong(0):-1;}}
    void put(String source,long target){ContentValues values=new ContentValues();values.put("source",source);values.put("target",target);
        getWritableDatabase().insertWithOnConflict("mms_receipts",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
}
