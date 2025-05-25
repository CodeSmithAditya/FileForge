package com.example.fileforge;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
// Import ImageView if you need to programmatically change the fileIcon based on actual input extension
// import android.widget.ImageView;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

public class docConv extends AppCompatActivity implements ConversionListener {

    private Uri fileUri;
    private TextView fileNameTextView;
    private Spinner formatSpinner;
    private Button processButton;
    private ProgressBar progressBar;
    private LinearLayout progressContainer;
    private TextView progressText;
    // private ImageView fileIconImageView; // If you want to set the icon dynamically

    private CloudConvertHelper cloudConvertHelper;
    private static final String TAG = "docConv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doc_conv); // Uses your existing activity_doc_conv.xml

        // fileIconImageView = findViewById(R.id.fileIcon);
        fileNameTextView = findViewById(R.id.fileNameTextView);
        formatSpinner = findViewById(R.id.conversionOptionsSpinner);
        processButton = findViewById(R.id.processFileButton);
        progressBar = findViewById(R.id.progressBar);
        progressContainer = findViewById(R.id.progressContainer);
        progressText = findViewById(R.id.progressText);

        cloudConvertHelper = new CloudConvertHelper(this);
        progressContainer.setVisibility(View.GONE);

        String fileUriStr = getIntent().getStringExtra("fileUri");
        if (fileUriStr != null) {
            fileUri = Uri.parse(fileUriStr);
            displayFileName(fileUri);
            // Optionally set a generic document icon or try to infer from filename if needed
            // fileIconImageView.setImageResource(R.drawable.word_icon); // Default from your XML
        } else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No fileUri passed to docConv activity.");
            finish();
            return;
        }

        // Output formats suitable for general document inputs (DOC, DOCX, ODT, RTF, TXT)
        String[] outputFormats = {
                "pdf", "docx", "doc", "txt", "html",
                "jpg", "png" // Convert document pages to images
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                outputFormats
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(adapter);

        processButton.setOnClickListener(v -> {
            if (fileUri == null) {
                Toast.makeText(docConv.this, "No file selected.", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedFormat = formatSpinner.getSelectedItem().toString();

            Log.d(TAG, "Process button clicked. Converting document to: " + selectedFormat);
            showProgressBarUI();
            // General documents usually don't have an "OCR" option for output, so pass null for options
            cloudConvertHelper.startConversion(fileUri, selectedFormat, null, this);
        });
    }

    private void displayFileName(Uri uri) {
        String fileName = getFileNameFromUriForDisplay(uri);
        fileNameTextView.setText(fileName != null ? fileName : "Unknown Document File");
    }

    private String getFileNameFromUriForDisplay(Uri uri) {
        String result = null;
        if (uri == null) return "Unknown Document File";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) { Log.w(TAG, "Error getting filename from URI for display", e); }
        }
        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                result = (cut != -1) ? path.substring(cut + 1) : path;
            }
        }
        return result != null ? result : "Unknown Document File";
    }

    private void showProgressBarUI() {
        processButton.setVisibility(View.GONE);
        formatSpinner.setEnabled(false);
        progressContainer.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        progressText.setText("Starting...");
    }

    private void hideProgressBarUI() {
        processButton.setVisibility(View.VISIBLE);
        formatSpinner.setEnabled(true);
        progressContainer.setVisibility(View.GONE);
    }

    // --- ConversionListener Implementation ---
    @Override
    public void onJobCreated(String jobId) {
        Log.i(TAG, "Listener: Job created: " + jobId);
        runOnUiThread(() -> progressText.setText("Job Created. Preparing upload..."));
    }

    @Override
    public void onUploadProgress(int progress) {
        Log.d(TAG, "Listener: Upload Progress: " + progress + "%");
        runOnUiThread(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setProgress(progress);
            progressText.setText("Uploading... (" + progress + "%)");
        });
    }

    @Override
    public void onConversionProgress(int progress) {
        Log.d(TAG, "Listener: Conversion Progress: " + progress + "%");
        runOnUiThread(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setProgress(progress);
            progressText.setText("Converting... (" + progress + "%)");
        });
    }

    @Override
    public void onSuccess(String downloadUrl, String filename) {
        Log.i(TAG, "Listener: Conversion successful! URL: " + downloadUrl);
        runOnUiThread(() -> {
            hideProgressBarUI();
            Toast.makeText(docConv.this, filename + " ready!", Toast.LENGTH_SHORT).show();

            if (AppLifecycleObserver.isAppInForeground()) {
                Log.d(TAG, "App is foreground, starting DownScr activity directly.");
                startDownScrActivity(downloadUrl, filename);
            } else {
                Log.d(TAG, "App is background, saving pending download info and showing notification.");
                SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(Constants.KEY_PENDING_DOWNLOAD_URL, downloadUrl);
                editor.putString(Constants.KEY_PENDING_FILENAME, filename);
                editor.apply();
                Log.i(TAG, "Saved pending download to SharedPreferences for docConv.");
                showConversionCompleteNotification(downloadUrl, filename);
            }
            finish();
        });
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "Listener: Conversion Error: " + message);
        runOnUiThread(() -> {
            hideProgressBarUI();
            Toast.makeText(docConv.this, "Error: " + message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onTimeout() {
        Log.e(TAG, "Listener: Conversion timed out.");
        runOnUiThread(() -> {
            hideProgressBarUI();
            Toast.makeText(docConv.this, "Error: Conversion process timed out.", Toast.LENGTH_LONG).show();
        });
    }

    private void startDownScrActivity(String downloadUrl, String filename) {
        Intent intent = new Intent(docConv.this, DownScr.class);
        intent.putExtra("downloadUrl", downloadUrl);
        intent.putExtra("filename", filename);
        startActivity(intent);
    }

    private void showConversionCompleteNotification(String downloadUrl, String filename) {
        Intent intent = new Intent(this, DownScr.class);
        intent.putExtra("downloadUrl", downloadUrl);
        intent.putExtra("filename", filename);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 101, intent, flags); // Request code 101

        String channelId = MyApplication.CHANNEL_ID;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("FileForge: Document Conversion Complete")
                .setContentText("'" + filename + "' is ready to download.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        notificationManager.notify(Constants.NOTIFICATION_ID_CONVERSION_COMPLETE, builder.build()); // Base ID
        Log.d(TAG, "Document Conversion complete notification shown for: " + filename);
    }
}