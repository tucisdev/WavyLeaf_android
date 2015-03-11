package com.towson.wavyleaf;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * Local database of points
 *
 * Contains an ID and a JSON string
 *
 * TODO: switch local points data from JSON to be stored properly in SQL
 */
public class PointsDatabase extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "points.db"; // (SQLite) database filename
    private static final int DATABASE_VERSION = 1; // current database version

    /**
     * Initialize points database
     */
    public PointsDatabase(Context ctx)
    {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Create the database table
     *
     * Current structure is:
     * CREATE TABLE list_json (
     *   _id INTEGER PRIMARY KEY AUTOINCREMENT,
     *   item_name TEXT NOT NULL
     * );
     */
    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL(
                "CREATE TABLE " + DatabaseConstants.TABLE_NAME +
                        " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + DatabaseConstants.ITEM_NAME +
                        " TEXT NOT NULL);");
    }

    /**
     * Upgrade existing database to newer version
     * <p/>
     * TODO: Pull data out of old database before dropping it, then re-insert
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseConstants.TABLE_NAME);
        onCreate(db);
    }

}
