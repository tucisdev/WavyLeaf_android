package com.towson.wavyleaf.data.upload;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.towson.wavyleaf.Settings;
import com.towson.wavyleaf.data.Point;
import com.towson.wavyleaf.data.PointsDatabase;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Async task to upload points
 */
public class UploadPoints extends UploadData<Point>
{
    private PointsDatabase pointsDB; // points database
    private UploadUser uploadUser; // upload user task to be executed before any points
    private boolean reupload; // whether this is a reattempted upload(do not save to local storage, already there)

    /**
     * Initialize upload task
     *
     * @param context
     *         appplication context
     * @param reupload
     *         whether this is a reattempted upload
     */
    public UploadPoints(Context context, boolean reupload)
    {
        super(context);

        this.reupload = reupload;
        this.uploadUser = null;

        // initialize database
        pointsDB = new PointsDatabase(this.context);
    }

    /**
     * Initialize upload task
     *
     * @param context
     *         appplication context
     */
    public UploadPoints(Context context)
    {
        this(context, false);
    }

    /**
     * Check for upload success and perform cleanup of local storage/prefs if successful
     *
     * @param result
     *         result from server
     *
     * @return point upload success
     */
    @Override
    protected boolean submitSuccessful(String result)
    {
        JSONObject jsonResult;
        boolean resultSuccess = false;
        String resultMessage = "";

        // extract information from json result string
        try
        {
            jsonResult = new JSONObject(result); // create json from result string
            resultSuccess = jsonResult.getInt(ResultFlag.SUCCESS.toString()) == 1; // result success
            resultMessage = jsonResult.getString(ResultFlag.MESSAGE.toString()); // result message
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

        // data sent successfully
        if (resultSuccess)
        {
            // if point submitted is part of a trip, increment trip tally
            if ((context.getClass() + "").contains("Trip"))
            {
                preferencesEditor
                        .putInt(Settings.KEY_TRIP_TALLY, sharedPreferences.getInt(Settings.KEY_TRIP_TALLY, 0) + 1);
            }
            // if point submitted is single point, increment point tally
            else if ((context.getClass() + "").contains("Report") ||
                    (context.getClass() + "").contains("Sighting"))
            {
                preferencesEditor.putInt(Settings.KEY_SINGLE_TALLY,
                                         sharedPreferences.getInt(Settings.KEY_SINGLE_TALLY, 0) + 1);
            }

            /// commit changes to preferences
            preferencesEditor.commit();
        }
        else
        {
            Log.d("UploadData", "Point upload failed, result message: " + resultMessage);
        }

        return resultSuccess;
    }

    @Override
    protected String getPostPath()
    {
        // TODO verify SUBMIT_POINT_WITH_PICTURE was meant to be used for all points
        // this was how all points were submitted prior to refactor
        return UploadConstants.SUBMIT_POINT_WITH_PICTURE;
    }

    /**
     * Perform upload of JSON data
     *
     * @param points
     *         JSON objects to upload
     *
     * @return result of upload task
     */
    @Override
    protected Boolean doInBackground(Point... points)
    {
        String userID = null;

        // ensure we have something to upload
        if (points.length > 0)
        {
            Log.d("UploadData", "Uploading " + points.length + " points...");
            for (Point point : points)
            {

                // save point to local storage before sending
                if (!reupload)
                {
                    // set ID of point for removal later
                    point = new Point((int) submitToLocalStorage(point.pointJSON.toString()), point.pointJSON);
                }

                // if we have a user to upload, do that
                if (uploadUser != null)
                {
                    try
                    {
                        // wait for user to upload and grab user ID
                        if (!uploadUser.get(1000, TimeUnit.MILLISECONDS) ||
                                (userID = sharedPreferences.getString(Settings.KEY_USER_ID, "Unknown")).equals("Unknown"))
                        {
                            Log.e("UploadData", "Unable to upload user data before point upload!");
                            return false;
                        }
                        uploadUser = null; // done with uploading

                        // remove flag to upload user
                        preferencesEditor.putBoolean(Settings.KEY_UPLOAD_USER, false);
                        preferencesEditor.commit();
                    }
                    catch (Exception e)
                    {
                        Log.e("UploadData", "Unable to upload user data before point upload!");
                        return false;
                    }
                }

                // update user ID if we need to
                if (userID != null)
                {
                    // update user ID in the point's JSON
                    try
                    {
                        point.pointJSON.put(UploadConstants.ARG_USER_ID, userID);
                    }
                    catch (JSONException e)
                    {
                        Log.e("UploadData", "Unable to update User ID before upload!");
                        return false;
                    }
                }


                // upload point
                boolean success = uploadData(point.pointJSON);

                // upload failed, stop attempting to upload the rest of the points
                if (!success)
                {
                    Log.e("UploadData", "Upload failed!");
                    return false;
                }

                // successful submit, delete local point
                deleteEntry(point.id);

                publishProgress(1);
            }
        }

        return true;
    }

    /**
     * Check that we have a valid user to upload points from
     *
     * @see UploadUser#onPostExecute(Boolean)
     */
    @Override
    protected void onPreExecute()
    {
        if (sharedPreferences.getBoolean(Settings.KEY_UPLOAD_USER, false))
        {
            Log.d("UploadData", "Re-attempting User Upload...");
            uploadUser = UploadUser.uploadUser(context);
        }
    }

    /**
     * Verify success and perform cleanup
     *
     * @param result
     *         boolean indicated if all JSON objects were uploaded successfully
     */
    @Override
    protected void onPostExecute(Boolean result)
    {
        pointsDB.close();
    }

    /**
     * Inserts a single JSON string into the database
     */
    protected long submitToLocalStorage(String JSONString)
    {
        SQLiteDatabase db = pointsDB.getWritableDatabase();

        // insert string
        ContentValues values = new ContentValues();
        values.put(PointsDatabase.ITEM_NAME, JSONString);
        return db.insertOrThrow(PointsDatabase.TABLE_NAME, null, values);
    }

    /**
     * Delete entry in database
     */
    protected void deleteEntry(int id)
    {
        SQLiteDatabase db = pointsDB.getWritableDatabase();

        String deleteSQL = "DELETE FROM " + PointsDatabase.TABLE_NAME +
                " WHERE " + PointsDatabase._ID + " = " + id + ";";

        db.execSQL(deleteSQL);
    }
}
