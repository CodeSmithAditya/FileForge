package com.example.fileforge;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationUtils {

    public static void showCompletionNotification(Context context, String title, String message) {
        String channelId = "fileforge_channel";
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "FileForge Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)  // Replace with your app's icon
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true);

        notificationManager.notify(1001, builder.build());
    }
}
