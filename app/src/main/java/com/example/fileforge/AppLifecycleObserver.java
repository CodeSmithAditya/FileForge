package com.example.fileforge;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * A simple observer that tracks whether the entire app is in the
 * foreground or background using ProcessLifecycleOwner.
 */
public class AppLifecycleObserver implements DefaultLifecycleObserver {

    private static final String TAG = "AppLifecycleObserver";
    private static boolean isAppInForeground = false;

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // App came to FOREGROUND (or was already foreground)
        Log.d(TAG, "App came to FOREGROUND");
        isAppInForeground = true;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        // App went to BACKGROUND
        Log.d(TAG, "App went to BACKGROUND");
        isAppInForeground = false;
    }

    /**
     * Checks if the app is currently considered to be in the foreground.
     * @return true if the app is in the foreground, false otherwise.
     */
    public static boolean isAppInForeground() {
        return isAppInForeground;
    }
}