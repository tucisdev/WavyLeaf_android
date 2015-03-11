package com.towson.wavyleaf;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
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
 * Async task to upload data
 */
public class UploadData extends AsyncTask<JSONObject, String, Boolean>
{
    // server constants
    protected static final String SERVER_URL = "http://heron.towson.edu/";
    protected static final String SUBMIT_USER = "wavyleaf/submit_user.php";
    protected static final String SUBMIT_POINT = "wavyleaf/submit_point.php";
    protected static final String SUBMIT_POINT_WITH_PICTURE = "wavyleaf/submit_point_with_pic.php";

    // argument string constants for data being sent
    protected static final String ARG_AREA_TYPE = "areatype";
    protected static final String ARG_AREA_VALUE = "areavalue";
    protected static final String ARG_BIRTH_YEAR = "birthyear";
    protected static final String ARG_DATE = "date";
    protected static final String ARG_EDUCATION = "education";
    protected static final String ARG_GENERAL_PLANT_ID = "generalplantid";
    protected static final String ARG_LATITUDE = "latitude";
    protected static final String ARG_LONGITUDE = "longitude";
    protected static final String ARG_NAME = "name";
    protected static final String ARG_NOTES = "notes";
    protected static final String ARG_OUTDOOR_EXPERIENCE = "outdoorexperience";
    protected static final String ARG_PERCENT = "percent";
    protected static final String ARG_PICTURE = "picture";
    protected static final String ARG_TREATMENT = "treatment";
    protected static final String ARG_USER_ID = "user_id";
    protected static final String ARG_WAVYLEAF_ID = "wavyleafid";
    protected static final String ARG_EMAIL = "email";

    private PointsDatabase pointsDB; // points database
    private Context context; // application context
    private Task task; // type of upload task being performed
    private SharedPreferences sharedPreferences; // shared preferences
    private SharedPreferences.Editor preferencesEditor; // shared preferences editor
    private boolean reupload; // whether this is a reattempted upload(do not save to local storage, already there)

    /**
     * Initialize upload task
     *
     * @param context
     *         appplication context
     * @param task
     *         type of upload task
     * @param reupload
     *         whether this is a reattempted upload
     */
    public UploadData(Context context, Task task, boolean reupload)
    {
        this.context = context;
        this.task = task;
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
     * @param task
     *         type of upload task
     */
    public UploadData(Context context, Task task)
    {
        this(context, task, false);
    }

    protected void onPreExecute()
    {
    }

    /**
     * Perform upload of JSON data
     *
     * @param jsonObjects
     *         JSON objects to upload
     *
     * @return result of upload task
     */
    @Override
    protected Boolean doInBackground(JSONObject... jsonObjects)
    {
        boolean result = false;

        // verify that we know which PHP script to submit to
        if (task != null)
        {
            // ensure we have something to upload
            if (jsonObjects.length > 0)
            {
                Log.d("UploadData", "Uploading " + jsonObjects.length + " points...");
                for (JSONObject json : jsonObjects)
                {
                    result = false; // assume unsuccessful upload until proven otherwise

                    // save point to local storage before sending
                    if (!reupload && task == Task.SUBMIT_POINT)
                    {
                        submitToLocalStorage(json.toString());
                    }

                    // create a new HttpClient and Post Header
                    HttpClient httpClient = new DefaultHttpClient();
                    HttpPost httpPost = new HttpPost(SERVER_URL + getHttpPost());

                    try
                    {
                        StringEntity stringEntity = new StringEntity(json.toString(), "UTF-8");

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
                            publishProgress(response);
                            Log.d("UploadData", "Response: " + response);

                            // break out of loop early if cancelled
                            if (isCancelled())
                            {
                                break;
                            }

                            // if we weren't cancelled, we can assume we are still successful
                            result = true;
                        }
                    }
                    catch (IllegalStateException e)
                    {
                        e.printStackTrace();
                    }
                    catch (UnknownHostException e)
                    {
                        Log.e("UploadData", "Unable to resolve host '" + SERVER_URL + "'!");
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
        else
        {
            result = false;
        }

        return result;
    }

    /**
     * Perform per-upload cleanup when progress is made IE increment point counter and remove point if it was
     * successfully submitted
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
            resultSuccess = jsonResult.getInt(Flag.SUCCESS.toString()) == 1; // result success
            resultMessage = jsonResult.getString(Flag.MESSAGE.toString()); // result message
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

        // data sent successfully
        if (resultSuccess)
        {
            // succeeded in submitting point to server, delete locally saved copy
            if (task == Task.SUBMIT_POINT)
            {
                deleteFirstEntry(); // delete entry because it was successfully submitted

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
            }
            // save user ID to preferences
            else if (task == Task.SUBMIT_USER)
            {
                // save user ID
                preferencesEditor.putString(Settings.KEY_USER_ID, resultMessage).commit();
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
     * Get URL of where to send HTTP POST request
     *
     * @return HTTP POST URL
     */
    protected String getHttpPost()
    {
        switch (task)
        {
            case SUBMIT_POINT:
                return SUBMIT_POINT_WITH_PICTURE;
            case SUBMIT_USER:
                return UploadData.SUBMIT_USER;
            default:
                return null;
        }
    }

    /**
     * Inserts a single JSON string into the database
     */
    protected void submitToLocalStorage(String JSONString)
    {
        SQLiteDatabase db = pointsDB.getWritableDatabase();

        // insert string
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.ITEM_NAME, JSONString);
        db.insertOrThrow(DatabaseConstants.TABLE_NAME, null, values);
    }

    /**
     * Delete first entry in database
     */
    protected void deleteFirstEntry()
    {
        SQLiteDatabase db = pointsDB.getWritableDatabase();

        String deleteSQL = "delete from " + DatabaseConstants.TABLE_NAME +
                " where " + DatabaseConstants._ID +
                " in (select " + DatabaseConstants._ID +
                " from " + DatabaseConstants.TABLE_NAME + " order by _id LIMIT 1);";

        db.execSQL(deleteSQL);
    }

    /**
     * Types of upload tasks
     */
    public enum Task
    {
        SUBMIT_USER,
        SUBMIT_POINT;
    }

    /**
     * Message flags
     */
    public enum Flag
    {
        SUCCESS,
        MESSAGE;

        @Override
        public String toString()
        {
            return name().toLowerCase();
        }
    }
}
