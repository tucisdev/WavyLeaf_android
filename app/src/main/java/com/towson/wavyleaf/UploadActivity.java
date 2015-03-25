package com.towson.wavyleaf;

import android.app.ProgressDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.util.Log;
import com.actionbarsherlock.app.SherlockActivity;
import com.towson.wavyleaf.data.Point;
import com.towson.wavyleaf.data.PointsDatabase;
import com.towson.wavyleaf.data.upload.UploadPoints;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

/**
 * Activity wrapper around uploading data
 *
 * This is required for having some context for sending progress/updates to the UI/user and is used as the intent for
 * the notification reminder for trip points.
 */
public class UploadActivity extends SherlockActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_uploadactivity);

        // upload any saved points
        uploadPoints();

        // TODO onpostexecute of task, toast
        // Toast.makeText(getApplicationContext(), "Sightings uploaded", Toast.LENGTH_SHORT).show();
    }

    /**
     * Attempt to upload all locally saved points
     */
    protected void uploadPoints()
    {
        PointsDatabase pointsDatabase = new PointsDatabase(this);
        SQLiteDatabase db = pointsDatabase.getWritableDatabase();
        List<Point> points = new LinkedList<Point>();

        // I want to look at the entire database
        Cursor cursor = db.rawQuery("SELECT * FROM " + PointsDatabase.TABLE_NAME, null);

        // ensure db was available
        if (cursor != null)
        {
            // get all saved points, if any
            while (cursor.moveToNext())
            {
                // get point ID
                int idColumn = cursor.getColumnIndex(BaseColumns._ID); // column to get ID from
                int id = cursor.getInt(idColumn);

                // get the point data
                int dataColumn = cursor.getColumnIndex(PointsDatabase.ITEM_NAME); // column to get from
                String result = cursor.getString(dataColumn); // get point string(formatted as json)
                points.add(new Point(id, stringToJSON(result))); // use as json
            }

            // upload points if we found any
            if (!points.isEmpty())
            {
                // create progress dialog to give user feedback
                final ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); // horizontal for progress bar
                progressDialog.setCancelable(false); // stay on top until finished
                progressDialog.setMax(points.size()); // max points is max progress
                progressDialog.setMessage("Uploading points..."); // message
                progressDialog.show(); // show it!

                // save activity so we can ask it to go away once done uploading
                final UploadActivity thisActivity = this;

                // upload points
                new UploadPoints(this, true)
                {
                    @Override
                    protected void onProgressUpdate(Integer... progress)
                    {
                        super.onProgressUpdate(progress);

                        // update current progress
                        progressDialog.incrementProgressBy(progress.length == 1 ? progress[0] : 1);
                    }

                    @Override
                    protected void onPostExecute(Boolean result)
                    {
                        super.onPostExecute(result);

                        // finally inform user of success
                        progressDialog.setMessage(result ? "All points uploaded!" : "Unable to reach server!\nTrying later...");
                        progressDialog.setCancelable(true); // allow it to be cancelled now

                        // automatically dismiss progress dialog after delay and end activity
                        (new Handler(Looper.getMainLooper())).postDelayed(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                progressDialog.dismiss();
                                thisActivity.finish();
                            }
                        }, 2500);
                    }
                }.execute(points.toArray(new Point[]{}));
            }
        }

        // close connection to database
        db.close();
    }

    /**
     * Convert a JSON formatted String to a JSON object
     *
     * @param s
     *         JSON formatted String
     *
     * @return JSON object from String
     */
    protected JSONObject stringToJSON(String s)
    {
        JSONObject jo = null;
        try
        {
            jo = new JSONObject(s);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        return jo;
    }
}