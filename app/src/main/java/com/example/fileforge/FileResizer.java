package com.example.fileforge;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class FileResizer extends AppCompatActivity {

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_resizer);

        LinearLayout selectFileCard = findViewById(R.id.SetFile);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            String mimeType = getMimeType(fileUri);
                            Log.d("FileResizer", "MIME Type: " + mimeType);

                            if (mimeType != null && mimeType.equals("application/pdf")) {
                                Intent intent = new Intent(this, pdfRes.class);
                                intent.putExtra("fileUri", fileUri.toString());
                                startActivity(intent);

                            } else if (mimeType != null && (mimeType.equals("application/msword") || mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") || mimeType.equals("text/plain"))) {
                                Intent intent = new Intent(this, docRes.class);
                                intent.putExtra("fileUri", fileUri.toString());
                                startActivity(intent);
                            } else if (mimeType != null && (mimeType.startsWith("image/"))) {
                                Intent intent = new Intent(this, imgRes.class);
                                intent.putExtra("fileUri", fileUri.toString());
                                startActivity(intent);
                            } else if (mimeType != null && (mimeType.equals("application/vnd.ms-excel") || mimeType.equals("text/csv") || mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) {
                                Intent intent = new Intent(this, xlsRes.class);
                                intent.putExtra("fileUri", fileUri.toString());
                                startActivity(intent);
                            } else if (mimeType != null && (mimeType.equals("application/vnd.ms-powerpoint") || mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation"))) {
                                Intent intent = new Intent(this, pptRes.class);
                                intent.putExtra("fileUri", fileUri.toString());
                                startActivity(intent);
                            } else {
                                Toast.makeText(this, "Unsupported File Format", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );

        selectFileCard.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    private String getMimeType(Uri uri) {
        String mimeType = null;
        if (uri.getScheme().equals("content")) {
            mimeType = getContentResolver().getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }
}