package com.example.fileforge;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat; // Use ContextCompat for checking permissions

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "MainActivity";

    private LinearLayout fileConverterCard;
    private LinearLayout fileResizerCard;

    private Runnable pendingAction;  // To store which activity to launch

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check for pending downloads from previous sessions
        checkForPendingDownload();

        fileConverterCard = findViewById(R.id.fileConverterCard);
        fileResizerCard = findViewById(R.id.fileResizerCard);

        fileConverterCard.setOnClickListener(v -> checkPermissionsAndProceed(() -> {
            Intent intent = new Intent(MainActivity.this, FileConverter.class);
            startActivity(intent);
        }));

        fileResizerCard.setOnClickListener(v -> checkPermissionsAndProceed(() -> {
            Intent intent = new Intent(MainActivity.this, FileResizer.class);
            startActivity(intent);
        }));
    }

    private void checkForPendingDownload() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String pendingUrl = prefs.getString(Constants.KEY_PENDING_DOWNLOAD_URL, null);
        String pendingFilename = prefs.getString(Constants.KEY_PENDING_FILENAME, null);

        if (pendingUrl != null && pendingFilename != null) {
            Log.i(TAG, "Pending download found on app start. URL: " + pendingUrl);

            // Cancel the notification that might have been shown
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(Constants.NOTIFICATION_ID_CONVERSION_COMPLETE);
                Log.d(TAG, "Cancelled notification ID: " + Constants.NOTIFICATION_ID_CONVERSION_COMPLETE);
            }

            // Start DownScr activity
            Intent intent = new Intent(MainActivity.this, DownScr.class);
            intent.putExtra("downloadUrl", pendingUrl);
            intent.putExtra("filename", pendingFilename);
            startActivity(intent);

            // MainActivity might stay in backstack or you can finish it.
            // finish(); // Optional: if you want DownScr to be the only screen in this flow
        } else {
            Log.i(TAG, "No pending download found.");
        }
    }


    // Inside MainActivity.java

    private void checkPermissionsAndProceed(Runnable action) {
        this.pendingAction = action;
        List<String> permissionsToRequest = new ArrayList<>();

        String[] permissionsToCheck;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+ (API 33)
            permissionsToCheck = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10, 11, 12 (API 29-32)
            permissionsToCheck = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
                    // On these versions, DownloadManager generally handles its own writes to public directories.
                    // WRITE_EXTERNAL_STORAGE has limited effect or requires requestLegacyExternalStorage for API 29.
            };
        } else { // Android 8, 8.1, 9 (API 26-28)
            permissionsToCheck = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE // Needed for DownloadManager to DIRECTORY_DOWNLOADS
            };
        }

        for (String permission : permissionsToCheck) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            boolean shouldShowRationale = false;
            for (String perm : permissionsToRequest) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                    shouldShowRationale = true;
                    break;
                }
            }

            if (shouldShowRationale) {
                new AlertDialog.Builder(this)
                        .setTitle("Permissions Needed")
                        .setMessage("This app requires certain permissions to function correctly. Please grant them.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            Toast.makeText(MainActivity.this, "Permissions denied. Some features may not work.", Toast.LENGTH_LONG).show();
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        } else {
            Log.d(TAG, "All necessary permissions already granted for this SDK version.");
            if (pendingAction != null) {
                pendingAction.run();
                this.pendingAction = null;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allCriticalPermissionsGranted = true;
            boolean notificationPermissionGranted = false;
            List<String> deniedCriticalPermissions = new ArrayList<>();

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                        notificationPermissionGranted = true;
                    }
                } else {
                    // If it's not the notification permission, then it's critical
                    if (!permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                        allCriticalPermissionsGranted = false;
                        deniedCriticalPermissions.add(permissions[i]);
                    }
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                }
            }

            if (allCriticalPermissionsGranted) {
                Log.d(TAG, "All critical permissions granted.");
                if (pendingAction != null) {
                    pendingAction.run();
                    pendingAction = null;
                }
                if (Arrays.asList(permissions).contains(Manifest.permission.POST_NOTIFICATIONS) && !notificationPermissionGranted) {
                    Toast.makeText(this, "Notification permission denied. You may miss updates.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Critical permissions denied: " + deniedCriticalPermissions);
                // Critical permissions were denied. Check if "Don't ask again" was selected for any.
                boolean shouldShowSettingsGuidance = false;
                for (String perm : deniedCriticalPermissions) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        shouldShowSettingsGuidance = true; // User selected "Don't ask again" for a critical permission
                        break;
                    }
                }

                if (shouldShowSettingsGuidance) {
                    Toast.makeText(this, "Required permissions were denied with 'Don't Ask Again'. Please enable them in app settings.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } else {
                    // User denied but didn't select "Don't Ask Again". You could ask again or disable features.
                    Toast.makeText(this, "Required permissions were denied. Some features may not work.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}