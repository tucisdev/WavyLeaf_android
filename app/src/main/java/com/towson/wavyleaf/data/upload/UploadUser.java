package com.towson.wavyleaf.data.upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import com.towson.wavyleaf.Settings;
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
 * Async task to upload user
 */
public class UploadUser extends AsyncTask<JSONObject, Void, String>
{
    private Context context; // application context
    private SharedPreferences sharedPreferences; // shared preferences
    private SharedPreferences.Editor preferencesEditor; // shared preferences editor

    /**
     * Initialize upload task
     *
     * @param context
     *         appplication context
     */
    public UploadUser(Context context)
    {
        this.context = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferencesEditor = sharedPreferences.edit();
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

        // ensure we have something to upload
        if (jsonObjects.length > 0)
        {
            JSONObject json = jsonObjects[0]; // we only upload 1 user at a time

            // create a new HttpClient and Post Header
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(UploadConstants.SERVER_URL + UploadConstants.SUBMIT_USER);

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
                    result = sb.toString();
                    Log.d("UploadData", "Response: " + result);
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

        return result;
    }

    /**
     * Check for upload success and perform cleanup
     *
     * @param result
     *         single data entry result
     */
    @Override
    protected void onPostExecute(String result)
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
            // save user ID
            preferencesEditor.putString(Settings.KEY_USER_ID, resultMessage).commit();

            /// commit changes to preferences
            preferencesEditor.commit();
        }
        // FIXME failure state here means user is just never sent...
    }
}
