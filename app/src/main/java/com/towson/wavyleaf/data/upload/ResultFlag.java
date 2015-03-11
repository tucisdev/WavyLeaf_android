package com.towson.wavyleaf.data.upload;

/**
 * Flags that we want to receive from server after uploading data
 *
 * @author Taylor Becker
 */
public enum ResultFlag
{
    SUCCESS,
    MESSAGE;

    @Override
    public String toString()
    {
        return name().toLowerCase();
    }
}
