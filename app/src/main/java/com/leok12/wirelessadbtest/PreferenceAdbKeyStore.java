package com.leok12.wirelessadbtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;

public class PreferenceAdbKeyStore {
    private static final String TAG = PreferenceAdbKeyStore.class.getSimpleName();
    private static final String SHARED_PREFERENCES_NAME = "adb_key_store";
    public static final String PREFERENCE_KEY = "adbkey";
    private static SharedPreferences mSharedPreferences;
    private static PreferenceAdbKeyStore mPreferenceAdbKeyStore;

    public void put(byte[] data){
        Log.d(TAG, "PreferenceAdbKeyStore : data = " + data);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        if (editor == null){
            Log.e(TAG, "editor should not be null ");
            return;
        }

        editor.putString(PREFERENCE_KEY,
                new String(Base64.encode(data, Base64.NO_WRAP), StandardCharsets.UTF_8));
        editor.apply();
    }

    public byte[] get(){
        if (!mSharedPreferences.contains(PREFERENCE_KEY)){
            Log.e(TAG, "failed to find share preference for " + PREFERENCE_KEY);
            return null;
        }

        return Base64.decode(mSharedPreferences.getString(PREFERENCE_KEY, null), Base64.NO_WRAP);
    }

    public static synchronized PreferenceAdbKeyStore getPreferenceAdbKeyStore(Context context){
        if (mPreferenceAdbKeyStore != null){
            return mPreferenceAdbKeyStore;
        }

        mPreferenceAdbKeyStore = new PreferenceAdbKeyStore(context);
        return mPreferenceAdbKeyStore;
    }

    PreferenceAdbKeyStore(Context context){
        mSharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
