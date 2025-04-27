package com.example.fileforge;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.List;

public class FileConverter extends AppCompatActivity {

    private ActivityResultLauncher<Intent> filePickerLauncher;

    // Supported MIME types
    private final List<String> supportedMimeTypes = Arrays.asList(
            "image/jpeg", "image/png", "image/jpg", // images
            "application/pdf",                      // pdf
            "application/msword",                   // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.ms-excel",             // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "application/vnd.ms-powerpoint",        // .ppt
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
            "text/plain"                            // .txt
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_converter);

       LinearLayout selectFileCard = findViewById(R.id.SetFile);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            String mimeType = getContentResolver().getType(fileUri);

                            if (mimeType != null && supportedMimeTypes.contains(mimeType)) {
                                Intent intent = new Intent(this, pptConv.class);
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
}
