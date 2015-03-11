package com.towson.wavyleaf.data;

import org.json.JSONObject;

/**
 * Simple structure to hold a point's JSON data and its ID in the database
 *
 * TODO Replace pointJSON JSON object with primitive information and add a helper to export to JSON
 *
 * @author Taylor Becker
 */
public class Point
{
    public final int id; // pointJSON's ID in the database
    public final JSONObject pointJSON; // JSON information about pointJSON

    /**
     * Init pointJSON
     *
     * @param id
     *         pointJSON's ID in the database
     * @param pointJSON
     *         JSON information about the pointJSON
     */
    public Point(int id, JSONObject pointJSON)
    {
        this.id = id;
        this.pointJSON = pointJSON;
    }
}
