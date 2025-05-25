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
import android.widget.AdapterView; // Import AdapterView
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox; // Import CheckBox
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.bumptech.glide.Glide;

import java.util.HashMap;
import java.util.Map;

public class imgConv extends AppCompatActivity implements ConversionListener {

    private Uri fileUri;
    private TextView fileNameTextView;
    private ImageView selectedImageView;
    private Spinner formatSpinner;
    private Button processButton;
    private CheckBox ocrCheckBox; // Added OCR CheckBox
    private ProgressBar progressBar;
    private LinearLayout progressContainer;
    private TextView progressText;

    private CloudConvertHelper cloudConvertHelper;
    private static final String TAG = "imgConv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_img_conv);

        fileNameTextView = findViewById(R.id.fileNameTextView);
        selectedImageView = findViewById(R.id.selectedImageView);
        formatSpinner = findViewById(R.id.conversionOptionsSpinner);
        processButton = findViewById(R.id.processFileButton);
        ocrCheckBox = findViewById(R.id.ocrCheckBox); // Initialize CheckBox
        progressBar = findViewById(R.id.progressBar);
        progressContainer = findViewById(R.id.progressContainer);
        progressText = findViewById(R.id.progressText);

        cloudConvertHelper = new CloudConvertHelper(this);
        progressContainer.setVisibility(View.GONE);
        ocrCheckBox.setVisibility(View.GONE); // Initially hide OCR checkbox

        String fileUriString = getIntent().getStringExtra("fileUri");
        if (fileUriString != null) {
            fileUri = Uri.parse(fileUriString);
            displayFileNameAndImage(fileUri);
        } else {
            Toast.makeText(this, "No image file selected", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No fileUri passed to imgConv activity.");
            finish();
            return;
        }

        // Output formats suitable for Image input, including text-based for OCR
        String[] outputFormats = {
                "jpg", "jpeg", "png", "webp", "gif", "ico", "psd",
                "pdf", // Convert image to PDF
                "docx", "doc", "txt" // Text-based outputs where OCR is relevant
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                outputFormats
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(adapter);

        // Listener to show/hide OCR checkbox based on selected format
        formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedFormat = parent.getItemAtPosition(position).toString();
                if (selectedFormat.equalsIgnoreCase("docx") ||
                        selectedFormat.equalsIgnoreCase("doc") ||
                        selectedFormat.equalsIgnoreCase("txt")) {
                    ocrCheckBox.setVisibility(View.VISIBLE);
                } else {
                    ocrCheckBox.setVisibility(View.GONE);
                    ocrCheckBox.setChecked(false); // Uncheck if not applicable
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                ocrCheckBox.setVisibility(View.GONE);
                ocrCheckBox.setChecked(false);
            }
        });


        processButton.setOnClickListener(v -> {
            if (fileUri == null) {
                Toast.makeText(imgConv.this, "No image file selected.", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedFormat = formatSpinner.getSelectedItem().toString();
            boolean useOcr = ocrCheckBox.isChecked();

            Map<String, Object> conversionOptions = new HashMap<>();
            if (useOcr && (selectedFormat.equalsIgnoreCase("docx") ||
                    selectedFormat.equalsIgnoreCase("doc") ||
                    selectedFormat.equalsIgnoreCase("txt"))) {
                conversionOptions.put("ocr", true);
                conversionOptions.put("ocr_language", "eng"); // Default to English
                Log.d(TAG, "OCR enabled for image to " + selectedFormat + " conversion.");
            } else if (useOcr) {
                // This case might not be strictly necessary if the checkbox is hidden for other formats,
                // but good for robustness if it were ever visible and checked for a non-text format.
                Log.d(TAG, "OCR option checked, but output format " + selectedFormat + " is not DOCX, DOC, or TXT. OCR will not be applied for image.");
                Toast.makeText(imgConv.this, "OCR is intended for DOCX, DOC, or TXT output from images.", Toast.LENGTH_LONG).show();
            }

            Log.d(TAG, "Process button clicked. Converting image to: " + selectedFormat + " with options: " + conversionOptions);
            showProgressBarUI();
            cloudConvertHelper.startConversion(fileUri, selectedFormat, conversionOptions.isEmpty() ? null : conversionOptions, this);
        });
    }

    private void displayFileNameAndImage(Uri uri) {
        String fileName = getFileNameFromUriForDisplay(uri);
        fileNameTextView.setText(fileName != null ? fileName : "Unknown Image File");

        if (selectedImageView != null && uri != null) {
            Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.image_placeholder)
                    .error(R.drawable.image_error)
                    .into(selectedImageView);
        } else if (selectedImageView != null) {
            selectedImageView.setImageResource(R.drawable.image_placeholder);
        }
    }

    private String getFileNameFromUriForDisplay(Uri uri) {
        // Identical to other "conv" activities
        String result = null;
        if (uri == null) return "Unknown Image File";
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
        return result != null ? result : "Unknown Image File";
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
        // Re-evaluate OCR checkbox visibility based on current spinner selection
        String selectedFormat = formatSpinner.getSelectedItem() != null ? formatSpinner.getSelectedItem().toString() : "";
        if (selectedFormat.equalsIgnoreCase("docx") ||
                selectedFormat.equalsIgnoreCase("doc") ||
                selectedFormat.equalsIgnoreCase("txt")) {
            ocrCheckBox.setVisibility(View.VISIBLE);
        } else {
            ocrCheckBox.setVisibility(View.GONE);
        }
        progressContainer.setVisibility(View.GONE);
    }

    // --- ConversionListener Implementation (Identical structure) ---
    // ... (onJobCreated, onUploadProgress, onConversionProgress are identical) ...
    @Override public void onJobCreated(String jobId) { Log.i(TAG, "Job created: " + jobId); runOnUiThread(() -> progressText.setText("Job Created...")); }
    @Override public void onUploadProgress(int progress) { runOnUiThread(() -> { progressBar.setProgress(progress); progressText.setText("Uploading... (" + progress + "%)");}); }
    @Override public void onConversionProgress(int progress) { runOnUiThread(() -> { progressBar.setProgress(progress); progressText.setText("Converting... (" + progress + "%)");}); }


    @Override
    public void onSuccess(String downloadUrl, String filename) {
        Log.i(TAG, "Listener: Conversion successful! URL: " + downloadUrl);
        runOnUiThread(() -> {
            hideProgressBarUI();
            Toast.makeText(imgConv.this, filename + " ready!", Toast.LENGTH_SHORT).show();

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
                Log.i(TAG, "Saved pending download to SharedPreferences for Image conversion.");
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
            Toast.makeText(imgConv.this, "Error: " + message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onTimeout() {
        Log.e(TAG, "Listener: Conversion timed out.");
        runOnUiThread(() -> {
            hideProgressBarUI();
            Toast.makeText(imgConv.this, "Error: Conversion process timed out.", Toast.LENGTH_LONG).show();
        });
    }

    private void startDownScrActivity(String downloadUrl, String filename) {
        Intent intent = new Intent(imgConv.this, DownScr.class);
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
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 107, intent, flags); // Unique request code

        String channelId = MyApplication.CHANNEL_ID;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("FileForge: Image Conversion Complete")
                .setContentText("'" + filename + "' is ready to download.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        notificationManager.notify(Constants.NOTIFICATION_ID_CONVERSION_COMPLETE + 6, builder.build()); // Unique notification ID
        Log.d(TAG, "Image Conversion complete notification shown for: " + filename);
    }
}