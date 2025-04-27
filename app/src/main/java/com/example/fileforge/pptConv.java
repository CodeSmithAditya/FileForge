package com.example.fileforge;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class pptConv extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ppt_conv);

        TextView fileNameTextView = findViewById(R.id.fileNameTextView);

        // Get the file URI from the intent
        String fileUriString = getIntent().getStringExtra("fileUri");

        if (fileUriString != null) {
            Uri fileUri = Uri.parse(fileUriString);

            // Get the file name from the URI
            String fileName = getFileName(fileUri);

            // Display the file name in the TextView
            fileNameTextView.setText(fileName);

        } else {
            fileNameTextView.setText("No file selected.");
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            // For content URIs, try to get the display name from the content resolver
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if (uri.getScheme().equals("file")) {
            // For file URIs, get the file name from the path
            File file = new File(uri.getPath());
            fileName = file.getName();
        }
        return fileName;
    }
}