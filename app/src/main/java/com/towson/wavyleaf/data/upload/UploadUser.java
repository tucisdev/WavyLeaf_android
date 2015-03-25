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
public class UploadUser extends UploadData<JSONObject>
{
    /**
     * Initialize upload task
     *
     * @param context
     *         appplication context
     */
    public UploadUser(Context context)
    {
        super(context);
    }

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
            // save user ID
            preferencesEditor.putString(Settings.KEY_USER_ID, resultMessage).commit();

            /// commit changes to preferences
            preferencesEditor.commit();
        }
        // FIXME failure state here means user is just never sent...

        return resultSuccess;
    }

    @Override
    protected String getPostPath()
    {
        return UploadConstants.SUBMIT_USER;
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
        // ensure we have a user to upload
        if (jsonObjects.length == 1)
        {
            return uploadData(jsonObjects[0]);
        }

        return false;
    }
}
