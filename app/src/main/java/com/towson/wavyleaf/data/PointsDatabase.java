package com.towson.wavyleaf.data;

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
public class PointsDatabase extends SQLiteOpenHelper implements BaseColumns
{
    // database constants
    public static final String TABLE_NAME = "list_json";
    public static final String ITEM_NAME = "item_name";

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
                "CREATE TABLE " + TABLE_NAME +
                        " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + ITEM_NAME +
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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

}
