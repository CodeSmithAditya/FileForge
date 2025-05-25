package com.example.fileforge;

import android.app.Application;
import android.app.NotificationChannel; // <-- Add this import
import android.app.NotificationManager; // <-- Add this import
import android.os.Build;                 // <-- Add this import
import android.util.Log;                 // <-- Add this import (optional, for logging)

import androidx.lifecycle.ProcessLifecycleOwner;

/**
 * Custom Application class to register app-wide components like
 * our lifecycle observer and create notification channels.
 */
public class MyApplication extends Application {

    // --- THIS IS THE CONSTANT THAT WAS MISSING ---
    public static final String CHANNEL_ID = "fileforge_channel";
    // ---------------------------------------------
    private static final String TAG = "MyApplication"; // For logging

    @Override
    public void onCreate() {
        super.onCreate();

        // Register our AppLifecycleObserver to the Process Lifecycle.
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleObserver());

        // Create notification channel(s)
        createNotificationChannel();
        Log.d(TAG, "MyApplication onCreate: Lifecycle observer registered and notification channel creation attempted.");
    }

    private void createNotificationChannel() {
        // Notification channels are only available on Android Oreo (API 26) and higher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use the CHANNEL_ID constant here
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, // Using the static final String
                    "FileForge Notifications", // User-visible name of the channel
                    NotificationManager.IMPORTANCE_HIGH // Or IMPORTANCE_DEFAULT
            );
            channel.setDescription("Channel for FileForge app conversion and download notifications"); // User-visible description

            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.i(TAG, "Notification channel created: " + CHANNEL_ID);
            } else {
                Log.e(TAG, "NotificationManager is null, channel '" + CHANNEL_ID + "' not created.");
            }
        } else {
            Log.d(TAG, "Notification channels not required for API level " + Build.VERSION.SDK_INT);
        }
    }
}