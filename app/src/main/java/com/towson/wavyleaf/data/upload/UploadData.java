package com.towson.wavyleaf.data.upload;

import android.content.Context;
import android.content.SharedPreferences;
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
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

/**
 * Base data upload class
 *
 * @author Taylor Becker
 */
public abstract class UploadData<Params> extends AsyncTask<Params, Integer, Boolean>
{
    protected Context context; // application context
    protected SharedPreferences sharedPreferences; // shared preferences
    protected SharedPreferences.Editor preferencesEditor; // shared preferences editor

    /**
     * Initialize upload task
     *
     * @param context
     *         appplication context
     */
    public UploadData(Context context)
    {
        this.context = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferencesEditor = sharedPreferences.edit();
    }

    /**
     * Helper for uploading a single data point
     *
     * @param data
     *         JSON data to be uploaded
     *
     * @return success as given by submitSuccessful
     */
    protected boolean uploadData(JSONObject data)
    {
        // create a new HttpClient and Post Header
        HttpClient httpClient = new DefaultHttpClient();

        // TODO verify SUBMIT_POINT_WITH_PICTURE was meant to be used for all points
        // this was how all points were submitted prior to refactor
        HttpPost httpPost =
                new HttpPost(UploadConstants.SERVER_URL + getPostPath());

        try
        {
            StringEntity stringEntity = new StringEntity(data.toString(), "UTF-8");

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
                Log.d("UploadData", "Response: " + response);

                return submitSuccessful(response); // check and return success
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

        return false;
    }

    /**
     * Check the result of a single upload for success
     *
     * @param result
     *         result from server
     *
     * @return success
     */
    protected abstract boolean submitSuccessful(String result);

    /**
     * Get the relative path to POST to
     *
     * @return relative path to POST to
     */
    protected abstract String getPostPath();
}
