package com.example.fileforge;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler; // Added
import android.os.Looper;  // Added
import android.util.Log;
import android.view.View;    // Added
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.DecimalFormat; // For formatting file size

public class DownScr extends AppCompatActivity {

    private TextView fileNameTextView;
    private Button saveButton;
    private ImageView fileIconImageView;
    private ProgressBar downloadProgressBar; // This is your in-app ProgressBar
    private TextView downloadProgressText;   // This is your in-app TextView for progress

    private String downloadUrl;
    private String filename;

    private DownloadManager downloadManager;
    private long downloadID = -1L;

    private static final String TAG = "DownScr";

    // For progress polling
    private Handler progressHandler;
    private Runnable progressRunnable;
    private static final int PROGRESS_UPDATE_INTERVAL = 500; // Update every 500ms

    // Moved BroadcastReceiver declaration to be a member variable
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadID == id && id != -1L) {
                Log.i(TAG, "BroadcastReceiver: Download completed or failed for ID: " + id);
                stopPollingProgress(); // Stop polling as we got a final status
                queryDownloadStatusAndUpdateUI(id); // Update UI based on final status
            } else if (id != -1L) {
                Log.d(TAG, "BroadcastReceiver: Received download complete for an unknown ID: " + id);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_down_scr);

        fileNameTextView = findViewById(R.id.convertedFileNameTextView);
        saveButton = findViewById(R.id.saveButton);
        fileIconImageView = findViewById(R.id.fileIconImageView);
        downloadProgressBar = findViewById(R.id.downloadProgressBar);
        downloadProgressText = findViewById(R.id.downloadProgressText);

        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        progressHandler = new Handler(Looper.getMainLooper()); // Initialize handler

        Intent intent = getIntent();
        downloadUrl = intent.getStringExtra("downloadUrl");
        filename = intent.getStringExtra("filename");

        if (downloadUrl == null || filename == null || downloadUrl.isEmpty() || filename.isEmpty()) {
            Log.e(TAG, "Error: File information missing in Intent.");
            Toast.makeText(this, "Error: File information missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.i(TAG, "Activity Started. Filename: " + filename);
        Log.i(TAG, ">>> Download URL: " + downloadUrl + " <<<");

        fileNameTextView.setText(filename);
        setDynamicFileIcon(filename);
        clearPendingDownloadInfo();

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(this, onDownloadComplete, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Receiver registered using ContextCompat.");

        // Hide progress UI initially
        downloadProgressBar.setVisibility(View.GONE);
        downloadProgressText.setVisibility(View.GONE);

        saveButton.setOnClickListener(v -> {
            Log.d(TAG, "Save Button Clicked.");
            if (saveButton.getText().toString().equalsIgnoreCase("Download Complete")) {
                Toast.makeText(DownScr.this, "File already downloaded.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (downloadUrl != null && downloadManager != null) {
                // Check if a download isn't already active by this activity instance or previously completed
                if (downloadID == -1L) {
                    startDownload(downloadUrl, filename);
                } else {
                    // If downloadID is not -1, it implies a download was started.
                    // We can query its status or rely on the fact that the button state would reflect completion/failure.
                    // For simplicity, if downloadID is set, we assume it's either running (and poller will update)
                    // or completed/failed (and button text would reflect that).
                    Toast.makeText(DownScr.this, "Download already initiated.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Cannot start download - URL or DownloadManager is null.");
                Toast.makeText(DownScr.this, "Download service not available.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearPendingDownloadInfo() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(Constants.KEY_PENDING_DOWNLOAD_URL);
        editor.remove(Constants.KEY_PENDING_FILENAME);
        editor.apply();
        Log.i(TAG, "Pending download info cleared from SharedPreferences.");
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private void setDynamicFileIcon(String currentFilename) {
        if (currentFilename == null || fileIconImageView == null) return;
        String extension = getFileExtension(currentFilename);
        int iconResId = R.drawable.image_placeholder; // Default icon
        switch (extension) {
            case "pdf": iconResId = R.drawable.pdf_icon; break;
            case "doc": case "docx": iconResId = R.drawable.word_icon; break;
            case "ppt": case "pptx": iconResId = R.drawable.ppt_icon; break;
            case "xls": case "xlsx": iconResId = R.drawable.excel_icon; break;
            case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp":
                iconResId = R.drawable.image_icon; break;
            case "txt": iconResId = R.drawable.word_icon; break;
        }
        try {
            fileIconImageView.setImageResource(iconResId);
        } catch (Exception e) {
            Log.e(TAG, "Error setting image resource for icon: " + iconResId + ". Using default.", e);
            fileIconImageView.setImageResource(R.drawable.image_error);
        }
    }

    private void startDownload(String url, String title) {
        Log.d(TAG, "Attempting to start download for URL: " + url);
        DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(Uri.parse(url));
        } catch (Exception e) {
            Log.e(TAG, "Invalid Download URL format: " + url, e);
            Toast.makeText(this, "Error: Invalid download link.", Toast.LENGTH_LONG).show();
            saveButton.setEnabled(true);
            saveButton.setText(getString(R.string.save_to_downloads)); // Assuming you have this string resource
            return;
        }
        request.setTitle(title);
        request.setDescription("Downloading converted file via FileForge...");
        String mimeType = getContentResolver().getType(Uri.parse(url));
        if (mimeType != null) request.setMimeType(mimeType);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(true);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileForge");
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + outputDir.getAbsolutePath());
                Toast.makeText(this, R.string.error_create_download_folder, Toast.LENGTH_SHORT).show();
                saveButton.setEnabled(true);
                saveButton.setText(getString(R.string.save_to_downloads));
                return;
            }
        }
        String subPath = "FileForge" + File.separator + title;
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subPath);

        try {
            downloadID = downloadManager.enqueue(request);
            Log.i(TAG, "Download enqueued. ID: " + downloadID);
            Toast.makeText(this, R.string.download_started_check_notifications, Toast.LENGTH_LONG).show();
            saveButton.setEnabled(false);
            saveButton.setText(R.string.downloading_text);

            downloadProgressBar.setVisibility(View.VISIBLE);
            downloadProgressText.setVisibility(View.VISIBLE);
            downloadProgressBar.setProgress(0);
            downloadProgressBar.setIndeterminate(true);
            downloadProgressText.setText(R.string.starting_download_text);
            startPollingProgress();
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue download request.", e);
            Toast.makeText(this, R.string.error_could_not_start_download, Toast.LENGTH_LONG).show();
            saveButton.setEnabled(true);
            saveButton.setText(getString(R.string.save_to_downloads));
            downloadID = -1L;
        }
    }

    private void startPollingProgress() {
        if (downloadID == -1L) return;
        Log.d(TAG, "Starting to poll progress for download ID: " + downloadID);
        progressRunnable = () -> queryCurrentDownloadProgress(downloadID); // Call the renamed method
        progressHandler.post(progressRunnable);
    }

    private void stopPollingProgress() {
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            Log.d(TAG, "Stopped polling download progress.");
        }
    }

    // Renamed for clarity to distinguish from the BroadcastReceiver's final status update
    private void queryCurrentDownloadProgress(long currentDownloadIDToPoll) {
        if (currentDownloadIDToPoll == -1L || downloadManager == null) {
            stopPollingProgress();
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(currentDownloadIDToPoll);
        Cursor cursor = null;
        try {
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int bytesDownloadedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalBytesColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                int status = cursor.getInt(statusColumn);
                long bytesDownloaded = cursor.getLong(bytesDownloadedColumn);
                long totalBytes = cursor.getLong(totalBytesColumn);

                // Post UI updates to the main thread
                final long finalBytesDownloaded = bytesDownloaded;
                final long finalTotalBytes = totalBytes;
                runOnUiThread(() -> {
                    if (status == DownloadManager.STATUS_RUNNING) {
                        if (finalTotalBytes > 0) {
                            final int progress = (int) ((finalBytesDownloaded * 100L) / finalTotalBytes);
                            downloadProgressBar.setIndeterminate(false);
                            downloadProgressBar.setProgress(progress);
                            downloadProgressText.setText(progress + "% (" + formatFileSize(finalBytesDownloaded) + " / " + formatFileSize(finalTotalBytes) + ")");
                        } else {
                            downloadProgressBar.setIndeterminate(true);
                            downloadProgressText.setText(getString(R.string.downloading_progress_unknown_size, formatFileSize(finalBytesDownloaded)));
                        }
                    } else if (status == DownloadManager.STATUS_PENDING) {
                        downloadProgressBar.setIndeterminate(true);
                        downloadProgressText.setText(R.string.download_pending_text);
                    } else { // SUCCESSFUL, FAILED, PAUSED
                        stopPollingProgress(); // Stop polling if not running or pending
                        // Final UI update for progress bar might be handled by queryDownloadStatusAndUpdateUI via BroadcastReceiver
                        // Or update here if you want immediate visual feedback from polling before receiver acts
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            downloadProgressBar.setIndeterminate(false);
                            downloadProgressBar.setProgress(100);
                            downloadProgressText.setText(getString(R.string.download_complete_percentage, formatFileSize(finalTotalBytes > 0 ? finalTotalBytes : finalBytesDownloaded)));
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            downloadProgressBar.setIndeterminate(false);
                            downloadProgressBar.setProgress(0);
                            downloadProgressText.setText(R.string.download_failed_text);
                        }
                    }
                });

                // Continue polling only if running or pending and handler is still valid
                if ((status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) &&
                        progressHandler != null && progressRunnable != null) {
                    progressHandler.postDelayed(progressRunnable, PROGRESS_UPDATE_INTERVAL);
                } else {
                    stopPollingProgress(); // Ensure polling stops for other states too
                }

            } else {
                Log.w(TAG, "Query for download ID " + currentDownloadIDToPoll + " returned no results. Stopping poll.");
                stopPollingProgress();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying download progress for ID " + currentDownloadIDToPoll, e);
            stopPollingProgress();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // This method is called by the BroadcastReceiver for the FINAL status
    private void queryDownloadStatusAndUpdateUI(long id) {
        stopPollingProgress(); // Ensure polling is stopped

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        Cursor cursor = null;
        try {
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                int titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int totalBytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                int status = cursor.getInt(statusCol);
                String titleFromDownload = cursor.getString(titleCol);
                long totalBytes = cursor.getLong(totalBytesCol);
                String toastMessage;

                switch (status) {
                    case DownloadManager.STATUS_SUCCESSFUL:
                        toastMessage = "'" + titleFromDownload + "' " + getString(R.string.downloaded_successfully_toast);
                        saveButton.setText(R.string.download_complete_button_text);
                        saveButton.setEnabled(false);
                        downloadProgressBar.setIndeterminate(false);
                        downloadProgressBar.setProgress(100);
                        downloadProgressText.setText(getString(R.string.download_complete_percentage, formatFileSize(totalBytes)));
                        break;
                    case DownloadManager.STATUS_FAILED:
                        int reason = cursor.getInt(reasonCol);
                        toastMessage = getString(R.string.download_failed_toast_format, titleFromDownload, getReasonText(reason));
                        saveButton.setText(R.string.download_failed_retry_button_text);
                        saveButton.setEnabled(true);
                        downloadID = -1L; // Allow retry
                        downloadProgressBar.setIndeterminate(false);
                        downloadProgressBar.setProgress(0);
                        downloadProgressText.setText(R.string.download_failed_text);
                        break;
                    default:
                        toastMessage = getString(R.string.download_ended_status_toast_format, status, titleFromDownload);
                        saveButton.setText(getString(R.string.save_to_downloads));
                        saveButton.setEnabled(true);
                        downloadID = -1L;
                        downloadProgressText.setText(getString(R.string.download_status_generic_text, status));
                        break;
                }
                Toast.makeText(DownScr.this, toastMessage, Toast.LENGTH_LONG).show();
                Log.i(TAG, "Final Download Status from Receiver: " + toastMessage);

            } else {
                Log.w(TAG, "Download ID " + id + " not found in DownloadManager query (from BroadcastReceiver).");
                Toast.makeText(DownScr.this, R.string.download_status_could_not_be_verified_toast, Toast.LENGTH_SHORT).show();
                saveButton.setText(getString(R.string.save_to_downloads));
                saveButton.setEnabled(true);
                downloadID = -1L;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in queryDownloadStatusAndUpdateUI for ID " + id, e);
            Toast.makeText(DownScr.this, R.string.error_querying_download_status_toast, Toast.LENGTH_SHORT).show();
            saveButton.setText(getString(R.string.save_to_downloads));
            saveButton.setEnabled(true);
            downloadID = -1L;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private String getReasonText(int reason) {
        // Same as before
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME: return getString(R.string.download_error_cannot_resume);
            case DownloadManager.ERROR_DEVICE_NOT_FOUND: return getString(R.string.download_error_device_not_found);
            // ... (add more string resources for other reasons) ...
            default: return getString(R.string.download_error_code_format, reason);
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1; // Safety for extremely large sizes
        if (digitGroups < 0) digitGroups = 0; // Safety for size < 1
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPollingProgress();
        try {
            unregisterReceiver(onDownloadComplete);
            Log.d(TAG, "Activity Destroyed, Receiver Unregistered.");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered or already unregistered: " + e.getMessage());
        }
    }
}