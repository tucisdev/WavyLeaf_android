package com.towson.wavyleaf.data.upload;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import com.towson.wavyleaf.Settings;
import com.towson.wavyleaf.data.Point;
import com.towson.wavyleaf.data.PointsDatabase;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

/**
 * Async task to upload points
 */
public class UploadPoints extends AsyncTask<Point, String, Boolean>
{
    private PointsDatabase pointsDB; // points database
    private Context context; // application context
    private SharedPreferences sharedPreferences; // shared preferences
    private SharedPreferences.Editor preferencesEditor; // shared preferences editor
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
        this.context = context;
        this.reupload = reupload;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferencesEditor = sharedPreferences.edit();

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

    protected void onPreExecute()
    {
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
        boolean result = false;

        // ensure we have something to upload
        if (points.length > 0)
        {
            Log.d("UploadData", "Uploading " + points.length + " points...");
            for (Point point : points)
            {
                result = false; // assume unsuccessful upload until proven otherwise

                // save point to local storage before sending
                if (!reupload)
                {
                    // set ID of point for removal later
                    point = new Point((int) submitToLocalStorage(point.pointJSON.toString()), point.pointJSON);
                }

                // create a new HttpClient and Post Header
                HttpClient httpClient = new DefaultHttpClient();

                // TODO verify SUBMIT_POINT_WITH_PICTURE was meant to be used for all points
                // this was how all points were submitted prior to refactor
                HttpPost httpPost =
                        new HttpPost(UploadConstants.SERVER_URL + UploadConstants.SUBMIT_POINT_WITH_PICTURE);

                try
                {
                    StringEntity stringEntity = new StringEntity(point.pointJSON.toString(), "UTF-8");

                    // set appropriate headers
                    stringEntity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    httpPost.setHeader("Content-Type", "application/json");

                    // set data to send
                    httpPost.setEntity(stringEntity);

                    // execute the post
                    HttpResponse httpResponse = httpClient.execute(httpPost);

                    // ensure server sent response
                    if (httpResponse != null)
                    {
                        InputStream is = httpResponse.getEntity().getContent();
                        BufferedReader fromServer = new BufferedReader(new InputStreamReader(is));
                        StringBuilder sb = new StringBuilder();
                        String line;

                        // read response from server
                        try
                        {
                            while ((line = fromServer.readLine()) != null)
                            {
                                sb.append(line + "\n");
                            }
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                        finally
                        {
                            // close stream
                            try
                            {
                                fromServer.close();
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }

                        // form result string
                        String response = sb.toString();

                        // TODO verify progress via another method, then publish appropriate progress
                        publishProgress(response);

                        Log.d("UploadData", "Response: " + response);

                        // break out of loop early if cancelled
                        if (isCancelled())
                        {
                            break;
                        }

                        // if we weren't cancelled, we can assume we are still successful
                        result = true;

                        // successful submit, delete local point
                        deleteEntry(point.id);
                    }
                }
                catch (IllegalStateException e)
                {
                    e.printStackTrace();
                }
                catch (UnknownHostException e)
                {
                    Log.e("UploadData", "Unable to resolve host '" + UploadConstants.SERVER_URL + "'!");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    /**
     * Perform per-upload cleanup when progress is made
     *
     * Increment point counter and remove local point if it was successfully submitted
     *
     * @param result
     *         single data entry result
     */
    @Override
    protected void onProgressUpdate(String... result)
    {
        JSONObject jsonResult;
        boolean resultSuccess = false;
        String resultMessage = "";

        // extract information from json result string
        try
        {
            jsonResult = new JSONObject(result[0]); // create json from result string
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
            cancel(true); // failed to upload, kill current task
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
