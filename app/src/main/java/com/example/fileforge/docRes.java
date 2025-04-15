package com.example.fileforge;

import static android.content.Intent.getIntent;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class docRes extends AppCompatActivity {

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
        setContentView(R.layout.activity_doc_res);

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

            processFileButton.setEnabled(true);  // Ensure button is enabled
            switchMode(qualityLayout, byQualityButton);
        } else {
            fileNameTextView.setText("No file selected.");
            processFileButton.setEnabled(false);
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

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }

        if (result == null || result.trim().isEmpty()) {  // Prevent null or empty names
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                } else {
                    result = path;  // Use full path if unable to extract
                }
            } else {
                result = "Unknown_File";
            }
        }

        return result;
    }

    /**
     * Process the document based on selected settings
     */
    private void processDocument() {
        Toast.makeText(this, "Processing document...", Toast.LENGTH_SHORT).show();

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
    /**
     * Process document by size
     */
    private void processBySize() {
        String sizeValue = sizeEditText.getText().toString().trim();

        // Check if the input is empty or not a valid number
        if (sizeValue.isEmpty() || !sizeValue.matches("\\d+(\\.\\d+)?")) {
            sizeEditText.setError("Enter a valid number");
            Log.d("docRes", "Invalid size input.");
            return;
        }

        // Additional validation: Prevent leading zeros and invalid decimals
        if (sizeValue.matches("0\\d+") || sizeValue.matches("\\.\\d+")) {
            sizeEditText.setError("Invalid number format");
            Log.d("docRes", "Invalid number format: " + sizeValue);
            return;
        }

        String unit = unitSpinner.getSelectedItem().toString();
        Log.d("docRes", "Processing document with target size: " + sizeValue + " " + unit);
    }

    /**
     * Process document by page range (supports non-continuous pages)
     */
    /**
     * Process document by page range (supports non-continuous pages)
     */
    private void processByPageRange() {
        String input = pageRangeEditText.getText().toString().trim();
        // Validate the page range format
        if (!isValidPageRange(input)) {
            pageRangeEditText.setError("Invalid format! Use 1-5,6,8-10");
            Log.d("docRes", "Invalid page range format.");
            return;
        }
        // Use LinkedHashSet to store unique pages while preserving order
        Set<Integer> selectedPages = new LinkedHashSet<>(getPagesFromRange(input));

        Log.d("docRes", "Processing document for pages: " + selectedPages);
    }

    /**
     * Validate if the page range format is correct
     */
    private boolean isValidPageRange(String input) {
        String regex = "^(\\d+(-\\d+)?)(,\\d+(-\\d+)?)*$";
        if (!input.matches(regex)) return false;

        String[] parts = input.split(",");
        for (String part : parts) {
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                if (start > end) return false;  // Prevents cases like "10-5"
            }
        }
        return true;
    }


    /**
     * Convert page range input to a list of selected pages
     */
    private List<Integer> getPagesFromRange(String input) {
        List<Integer> pages = new ArrayList<>();
        String[] parts = input.split(",");

        for (String part : parts) {
            if (part.contains("-")) {  // Handle ranges like "1-5"
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);

                for (int i = start; i <= end; i++) {
                    pages.add(i);
                }
            } else {  // Handle single pages like "6"
                pages.add(Integer.parseInt(part));
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
