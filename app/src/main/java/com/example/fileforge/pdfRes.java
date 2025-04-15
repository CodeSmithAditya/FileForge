package com.example.fileforge;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class pdfRes extends AppCompatActivity {

    private Button byQualityButton, bySizeButton, byPageRangeButton, optimizeImagesButton, processFileButton;
    private LinearLayout qualityLayout, sizeLayout, pageRangeLayout, optimizeImagesLayout;
    private RadioGroup qualityRadioGroup;
    private RadioButton mediumCompressionRadio;
    private TextView fileNameTextView;
    private ScrollView scrollView;
    private EditText sizeEditText, pageRangeEditText;
    private Spinner unitSpinner;
    private CheckBox reduceResolutionCheckBox, convertToGrayscaleCheckBox;

    private String fileUriString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_res);

        // Initialize UI elements
        scrollView = findViewById(R.id.scrollView);
        fileNameTextView = findViewById(R.id.fileNameTextView);
        byQualityButton = findViewById(R.id.byQualityButton);
        bySizeButton = findViewById(R.id.bySizeButton);
        byPageRangeButton = findViewById(R.id.byPageRangeButton);
        optimizeImagesButton = findViewById(R.id.optimizeImagesButton);
        processFileButton = findViewById(R.id.processFileButton);

        qualityLayout = findViewById(R.id.qualityLayout);
        sizeLayout = findViewById(R.id.sizeLayout);
        pageRangeLayout = findViewById(R.id.pageRangeLayout);
        optimizeImagesLayout = findViewById(R.id.optimizeImagesLayout);

        qualityRadioGroup = findViewById(R.id.qualityRadioGroup);
        mediumCompressionRadio = findViewById(R.id.mediumCompression); // Default selection

        sizeEditText = findViewById(R.id.sizeEditText);
        pageRangeEditText = findViewById(R.id.pageRangeEditText);
        unitSpinner = findViewById(R.id.unitSpinner);
        reduceResolutionCheckBox = findViewById(R.id.reduceResolutionCheckBox);
        convertToGrayscaleCheckBox = findViewById(R.id.convertToGrayscaleCheckBox);

        preventNegativeInput(sizeEditText);

        // Get file URI from intent
        fileUriString = getIntent().getStringExtra("fileUri");

        if (fileUriString != null && !fileUriString.isEmpty()) {
            Uri fileUri = Uri.parse(fileUriString);
            fileNameTextView.setText(getFileName(fileUri));

            // Auto-select "By Quality" mode & default "Medium Compression"
            switchMode(qualityLayout, byQualityButton);
            mediumCompressionRadio.setChecked(true);

            // Auto-scroll to "By Quality" (pre-selected)
            scrollView.post(() -> scrollView.smoothScrollTo(0, qualityLayout.getTop()));

        } else {
            fileNameTextView.setText("No file selected.");
            processFileButton.setEnabled(false); // Disable "Process" button
        }

        // Set button click listeners
        byQualityButton.setOnClickListener(v -> switchMode(qualityLayout, byQualityButton));
        bySizeButton.setOnClickListener(v -> switchMode(sizeLayout, bySizeButton));
        byPageRangeButton.setOnClickListener(v -> switchMode(pageRangeLayout, byPageRangeButton));
        optimizeImagesButton.setOnClickListener(v -> switchMode(optimizeImagesLayout, optimizeImagesButton));

        // Process file button
        processFileButton.setOnClickListener(v -> processDocument());
    }

    private void preventNegativeInput(EditText editText) {
        editText.setFilters(new InputFilter[]{
                (source, start, end, dest, dstart, dend) -> {
                    String newInput = dest.toString().substring(0, dstart) + source + dest.toString().substring(dend);
                    if (!newInput.isEmpty()) {
                        try {
                            double value = Double.parseDouble(newInput);
                            if (value < 0) return ""; // Prevent negative input
                        } catch (NumberFormatException e) {
                            return ""; // Prevent invalid input
                        }
                    }
                    return null; // Accept input
                }
        });
    }

    /**
     * Function to switch between different resize modes
     */
    private void switchMode(LinearLayout layoutToShow, Button selectedButton) {
        // Hide all layouts
        qualityLayout.setVisibility(View.GONE);
        sizeLayout.setVisibility(View.GONE);
        pageRangeLayout.setVisibility(View.GONE);
        optimizeImagesLayout.setVisibility(View.GONE);

        // Reset button styles
        byQualityButton.setBackgroundResource(R.drawable.button_unselected);
        bySizeButton.setBackgroundResource(R.drawable.button_unselected);
        byPageRangeButton.setBackgroundResource(R.drawable.button_unselected);
        optimizeImagesButton.setBackgroundResource(R.drawable.button_unselected);

        // Show selected layout & highlight button
        layoutToShow.setVisibility(View.VISIBLE);
        selectedButton.setBackgroundResource(R.drawable.button_selected);

        // Scroll to selected section smoothly
        scrollView.post(() -> scrollView.smoothScrollTo(0, layoutToShow.getTop()));
    }

    /**
     * Get file name from URI
     */
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = new File(uri.getPath()).getName(); // Fallback for file paths
        }
        return result;
    }


    /**
     * Process the document based on selected settings
     */
    private void processDocument() {
        String selectedMode = "";

        if (qualityLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Quality Compression";
            processByQuality();
        } else if (sizeLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "File Size";
            processBySize();
        } else if (pageRangeLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Page Range";
            processByPageRange();
        } else if (optimizeImagesLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Optimize Images";
            processByOptimizeImages();
        }

        Log.d("docRes", "Processing document with mode: " + selectedMode);
    }

    /**
     * Process document by quality compression
     */
    private void processByQuality() {
        int selectedQualityId = qualityRadioGroup.getCheckedRadioButtonId();
        String compressionLevel = "Medium"; // Default value

        if (selectedQualityId == R.id.highCompression) {
            compressionLevel = "High";
        } else if (selectedQualityId == R.id.mediumCompression) {
            compressionLevel = "Medium";
        } else if (selectedQualityId == R.id.lowCompression) {
            compressionLevel = "Low";
        }

        Log.d("docRes", "Processing document with " + compressionLevel + " compression.");
    }

    /**
     * Process document by size
     */
    private void processBySize() {
        String sizeValue = sizeEditText.getText().toString().trim();
        String unit = unitSpinner.getSelectedItem().toString();

        if (sizeValue.isEmpty()) {
            Toast.makeText(this, "Please enter a target file size.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("docRes", "Processing document with target size: " + sizeValue + " " + unit);
    }

    /**
     * Process document by page range (supports non-continuous pages)
     */
    private void processByPageRange() {
        String pageRangeInput = pageRangeEditText.getText().toString().trim();

        if (pageRangeInput.isEmpty()) {
            Toast.makeText(this, "Please enter a valid page range.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate and parse page ranges
        List<Integer> selectedPages = parsePageRanges(pageRangeInput);

        if (selectedPages.isEmpty()) {
            Toast.makeText(this, "Invalid page range format.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("pdfRes", "Processing document for pages: " + selectedPages);
    }

    /**
     * Parses a page range string (e.g., "1-5,6,8-10") into a list of integers.
     */
    private List<Integer> parsePageRanges(String rangeInput) {
        List<Integer> pages = new ArrayList<>();

        String[] parts = rangeInput.split(",");
        for (String part : parts) {
            if (part.contains("-")) {
                // Handle range (e.g., "1 - 5" or "1-5")
                String[] range = part.trim().split("\\s*-\\s*"); // Trim spaces around '-'
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        if (start <= end) {
                            for (int i = start; i <= end; i++) {
                                pages.add(i);
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                // Handle single page (e.g., "6")
                try {
                    pages.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return pages;
    }


    /**
     * Process document by optimizing images
     */
    private void processByOptimizeImages() {
        boolean reduceResolution = reduceResolutionCheckBox.isChecked();
        boolean convertToGrayscale = convertToGrayscaleCheckBox.isChecked();

        Log.d("docRes", "Optimizing images: Reduce Resolution - " + reduceResolution + ", Convert to Grayscale - " + convertToGrayscale);
    }
}
