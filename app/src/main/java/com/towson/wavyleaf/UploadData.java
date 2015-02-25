package com.towson.wavyleaf;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
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

/**
 * Async task to upload data
 */
public class UploadData extends AsyncTask<JSONObject, Void, String>
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

    private DatabaseListJSONData m_dbListData;
    private Context context; // application context
    private Task task; // type of upload task being performed

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
        this.context = context;
        this.task = task;
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
    protected String doInBackground(JSONObject... jsonObjects)
    {

        String result = "";

        // verify that we know which PHP script to submit to
        if (task != null)
        {

            // read in the JSONObject from the JSONObject array
            if (jsonObjects.length > 0)
            {
                final JSONObject json = jsonObjects[0];

                // save point to local storage before sending
                if (task == Task.SUBMIT_POINT)
                {
                    submitToLocalStorage(json.toString());
                }

                // create a new HttpClient and Post Header
                HttpClient hc = new DefaultHttpClient();
                HttpPost hp = new HttpPost(SERVER_URL + getHttpPost());

                try
                {
                    StringEntity se = new StringEntity(json.toString(), "UTF-8");

                    // set appropriate headers
                    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    hp.setHeader("Content-Type", "application/json");

                    // set data to send
                    hp.setEntity(se);

                    // execute the post
                    HttpResponse response = hc.execute(hp);

                    // ensure server sent response
                    if (response != null)
                    {
                        InputStream is = response.getEntity().getContent();
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
                        result = sb.toString();
                    }
                }
                catch (IllegalStateException e)
                {
                    e.printStackTrace();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        else
        {
            result = "ERROR: bad script destination";
        }

        return result;
    }

    /**
     * Verify success and perform cleanup after upload
     *
     * @param result
     *         JSON-formatted result string
     */
    @Override
    protected void onPostExecute(String result)
    {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.context);
        Editor preferencesEditor = sp.edit();

        JSONObject jsonResult;
        String resultSuccess = "", resultMessage = "";

        // extract information from json result string
        try
        {
            jsonResult = new JSONObject(result); // create json from result string
            resultSuccess = jsonResult.getString(Flag.SUCCESS.toString()); // result success
            resultMessage = jsonResult.getString(Flag.MESSAGE.toString()); // result message
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

        // data sent successfully
        if (resultSuccess.equalsIgnoreCase("1") || resultSuccess.contains("1"))
        {
            // succeeded in submitting point to server, delete locally saved copy
            if (task == Task.SUBMIT_POINT)
            {
                deleteFirstEntry(); // delete entry because it was successfully submitted

                // if point submitted is part of a trip, increment trip tally
                if ((context.getClass() + "").contains("Trip"))
                {
                    preferencesEditor.putInt(Settings.KEY_TRIP_TALLY, sp.getInt(Settings.KEY_TRIP_TALLY, 0) + 1);
                }
                // if point submitted is single point, increment point tally
                else if ((context.getClass() + "").contains("Report") ||
                        (context.getClass() + "").contains("Sighting"))
                {
                    preferencesEditor.putInt(Settings.KEY_SINGLE_TALLY, sp.getInt(Settings.KEY_SINGLE_TALLY, 0) + 1);
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
        // unsuccessful result, not submitted successfully
//		else
//			Toast.makeText(this.context, "Error submitting. Saved for later.", Toast.LENGTH_LONG).show();

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
        // initialize database
        m_dbListData = new DatabaseListJSONData(this.context);
        SQLiteDatabase db = m_dbListData.getWritableDatabase();

        // insert string
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.ITEM_NAME, JSONString);
        db.insertOrThrow(DatabaseConstants.TABLE_NAME, null, values);
    }

    /**
     * Delete first(oldest?) entry in database
     *
     * TODO: Verify this is intended. Seems it could result in unreliable behavior
     *
     * For Example:
     *  Submit point 1, not successful
     *  Submit point 2, successful, deletes point 1 from db
     *  Point 1 never submitted
     */
    protected void deleteFirstEntry()
    {
        DatabaseListJSONData m_dbListData = new DatabaseListJSONData(this.context);
        SQLiteDatabase db = m_dbListData.getWritableDatabase();

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
