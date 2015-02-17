package com.towson.wavyleaf;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * This class is based off of PA4 from the Android Development class
 * Not sure if this is the same number in the class now, but you get the idea.
 */
public class DatabaseListJSONData extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "points.db";
    private static final int DATABASE_VERSION = 1;

    /**
     * Create a helper object for the points database
     */
    public DatabaseListJSONData(Context ctx)
    {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // TABLE_NAME comes from DatabaseConstants.java
    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL(
                "CREATE TABLE " + DatabaseConstants.TABLE_NAME +
                        " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + DatabaseConstants.ITEM_NAME +
                        " TEXT NOT NULL);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseConstants.TABLE_NAME);
        onCreate(db);
    }

}
