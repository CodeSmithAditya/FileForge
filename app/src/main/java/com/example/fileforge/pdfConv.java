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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
// Import ImageView if you use it
// import android.widget.ImageView;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

public class pdfConv extends AppCompatActivity implements ConversionListener {

    private Uri fileUri;
    private TextView fileNameTextView;
    private Spinner formatSpinner;
    private Button processButton;
    private CheckBox ocrCheckBox; // For OCR option
    private ProgressBar progressBar;
    private LinearLayout progressContainer;
    private TextView progressText;
    // private ImageView fileIconImageView; // If you need to reference it

    private CloudConvertHelper cloudConvertHelper;
    private static final String TAG = "pdfConv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_conv); // Use activity_pdf_conv.xml

        // Initialize views (ensure IDs match your XML)
        // ImageView fileIcon = findViewById(R.id.fileIcon); // Or pdfFileIcon
        // TextView appTitle = findViewById(R.id.appTitle); // Or pdfAppTitle
        fileNameTextView = findViewById(R.id.fileNameTextView);
        formatSpinner = findViewById(R.id.conversionOptionsSpinner);
        processButton = findViewById(R.id.processFileButton);
        ocrCheckBox = findViewById(R.id.ocrCheckBox); // Initialize CheckBox
        progressBar = findViewById(R.id.progressBar);
        progressContainer = findViewById(R.id.progressContainer);
        progressText = findViewById(R.id.progressText);

        cloudConvertHelper = new CloudConvertHelper(this);
        progressContainer.setVisibility(View.GONE);

        String fileUriStr = getIntent().getStringExtra("fileUri");
        if (fileUriStr != null) {
            fileUri = Uri.parse(fileUriStr);
            displayFileName(fileUri);
            // if (fileIconImageView != null) fileIconImageView.setImageResource(R.drawable.pdf_icon);
        } else {
            Toast.makeText(this, "No PDF file selected", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No fileUri passed to pdfConv activity.");
            finish();
            return;
        }

        // Output formats suitable for PDF input
        String[] outputFormats = {
                "docx", "doc", "txt", // Good for OCR
                "jpg", "png", "html", "ppt", "pptx",
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
                Toast.makeText(pdfConv.this, "No PDF file selected.", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedFormat = formatSpinner.getSelectedItem().toString();
            boolean useOcr = ocrCheckBox.isChecked();

            Map<String, Object> conversionOptions = new HashMap<>();
            if (useOcr && (selectedFormat.equalsIgnoreCase("doc") ||
                    selectedFormat.equalsIgnoreCase("docx") ||
                    selectedFormat.equalsIgnoreCase("txt"))) {
                conversionOptions.put("ocr", true);
                conversionOptions.put("ocr_language", "eng"); // Default to English
                Log.d(TAG, "OCR enabled for " + selectedFormat + " conversion.");
            } else if (useOcr) {
                Log.d(TAG, "OCR option checked, but output format " + selectedFormat + " is not DOC, DOCX or TXT. OCR will not be applied.");
                Toast.makeText(pdfConv.this, "OCR is primarily for DOC, DOCX, or TXT output from PDF.", Toast.LENGTH_LONG).show();
            }

            Log.d(TAG, "Process button clicked. Converting PDF to: " + selectedFormat + " with options: " + conversionOptions);
            showProgressBarUI();
            cloudConvertHelper.startConversion(fileUri, selectedFormat, conversionOptions, this);
        });
    }

    private void displayFileName(Uri uri) {
        String fileName = getFileNameFromUriForDisplay(uri);
        fileNameTextView.setText(fileName != null ? fileName : "Unknown PDF File");
    }

    private String getFileNameFromUriForDisplay(Uri uri) {
        // Identical to docConv's implementation
        String result = null;
        if (uri == null) return "Unknown PDF File";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) { result = cursor.getString(nameIndex); }
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
        return result != null ? result : "Unknown PDF File";
    }

    private void showProgressBarUI() {
        processButton.setVisibility(View.GONE);
        formatSpinner.setEnabled(false);
        ocrCheckBox.setEnabled(false); // Disable OCR checkbox during processing
        progressContainer.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        progressText.setText("Starting...");
    }

    private void hideProgressBarUI() {
        processButton.setVisibility(View.VISIBLE);
        formatSpinner.setEnabled(true);
        ocrCheckBox.setEnabled(true); // Re-enable OCR checkbox
        progressContainer.setVisibility(View.GONE);
    }

    // --- ConversionListener Implementation (Identical structure) ---
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
            Toast.makeText(pdfConv.this, filename + " ready!", Toast.LENGTH_SHORT).show();

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
                Log.i(TAG, "Saved pending download to SharedPreferences for PDF conversion.");
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
            Toast.makeText(pdfConv.this, "Error: " + message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onTimeout() {
        Log.e(TAG, "Listener: Conversion timed out.");
        runOnUiThread(() -> {
            hideProgressBarUI();
            Toast.makeText(pdfConv.this, "Error: Conversion process timed out.", Toast.LENGTH_LONG).show();
        });
    }

    private void startDownScrActivity(String downloadUrl, String filename) {
        Intent intent = new Intent(pdfConv.this, DownScr.class);
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
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 102, intent, flags); // Unique request code 102

        String channelId = MyApplication.CHANNEL_ID;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("FileForge: PDF Conversion Complete")
                .setContentText("'" + filename + "' is ready to download.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        notificationManager.notify(Constants.NOTIFICATION_ID_CONVERSION_COMPLETE + 1, builder.build()); // Unique notification ID
        Log.d(TAG, "PDF Conversion complete notification shown for: " + filename);
    }
}