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
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

public class pptRes extends AppCompatActivity {

    private Button byQualityButton, bySizeButton, byPageRangeButton, optimizeImagesButton, removeAnimationsButton, processFileButton;
    private LinearLayout qualityLayout, sizeLayout, pageRangeLayout, optimizeImagesLayout, removeAnimationsLayout;
    private RadioGroup qualityRadioGroup, removeAnimationsRadioGroup;
    private RadioButton mediumCompressionRadio, removeAllAnimationsRadio, removeObjTransitionsRadio;
    private TextView fileNameTextView;
    private ScrollView scrollView;
    private EditText sizeEditText, pageRangeEditText;
    private Spinner unitSpinner;
    private CheckBox reduceResolutionCheckBox, convertToGrayscaleCheckBox;

    private String fileUriString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ppt_res);

        // Initialize UI elements
        scrollView = findViewById(R.id.scrollView);
        fileNameTextView = findViewById(R.id.fileNameTextView);
        byQualityButton = findViewById(R.id.byQualityButton);
        bySizeButton = findViewById(R.id.bySizeButton);
        byPageRangeButton = findViewById(R.id.byPageRangeButton);
        optimizeImagesButton = findViewById(R.id.optimizeImagesButton);
        removeAnimationsButton = findViewById(R.id.removeAnimationsButton);
        processFileButton = findViewById(R.id.processFileButton);

        qualityLayout = findViewById(R.id.qualityLayout);
        sizeLayout = findViewById(R.id.sizeLayout);
        pageRangeLayout = findViewById(R.id.pageRangeLayout);
        optimizeImagesLayout = findViewById(R.id.optimizeImagesLayout);
        removeAnimationsLayout = findViewById(R.id.removeAnimationsLayout);

        qualityRadioGroup = findViewById(R.id.qualityRadioGroup);
        mediumCompressionRadio = findViewById(R.id.mediumCompression);

        removeAnimationsRadioGroup = findViewById(R.id.removeAnimationsRadioGroup);
        removeAllAnimationsRadio = findViewById(R.id.removeAllAnimations);

        sizeEditText = findViewById(R.id.sizeEditText);
        pageRangeEditText = findViewById(R.id.pageRangeEditText);
        unitSpinner = findViewById(R.id.unitSpinner);
        reduceResolutionCheckBox = findViewById(R.id.reduceResolutionCheckBox);
        convertToGrayscaleCheckBox = findViewById(R.id.convertToGrayscaleCheckBox);
        removeObjTransitionsRadio = findViewById(R.id.removeObjTransitions);

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
        removeAnimationsButton.setOnClickListener(v -> switchMode(removeAnimationsLayout, removeAnimationsButton));

        // Process file button
        // Process file button
        processFileButton.setOnClickListener(v -> {
            Toast.makeText(this, "Processing your PowerPoint file...", Toast.LENGTH_SHORT).show();
            processPresentation();
        });

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
        removeAnimationsLayout.setVisibility(View.GONE);

        // Reset button styles
        byQualityButton.setBackgroundResource(R.drawable.button_unselected);
        bySizeButton.setBackgroundResource(R.drawable.button_unselected);
        byPageRangeButton.setBackgroundResource(R.drawable.button_unselected);
        optimizeImagesButton.setBackgroundResource(R.drawable.button_unselected);
        removeAnimationsButton.setBackgroundResource(R.drawable.button_unselected);

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
            DocumentFile file = DocumentFile.fromSingleUri(this, uri);
            if (file != null) {
                result = file.getName();
            }
        }
        return (result != null) ? result : "Unknown File";
    }

    /**
     * Process the PPT file based on selected settings
     */
    private void processPresentation() {
        String selectedMode = "";

        if (qualityLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Quality Compression";
            processByQuality();
        } else if (sizeLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "File Size";
            processBySize();
        } else if (pageRangeLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Slide Range";
            processByPageRange();
        } else if (optimizeImagesLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Optimize Images";
            processByOptimizeImages();
        } else if (removeAnimationsLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Remove Animations";
            processByRemoveAnimations();
        } else {
            Log.w("pptRes", "No processing mode selected.");
            Toast.makeText(this, "Please select a processing mode.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("pptRes", "Processing started with mode: " + selectedMode + " for file: " + fileNameTextView.getText());
    }

    /**
     * Process PPT by quality compression
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

        Log.d("pptRes", "Processing PPT with " + compressionLevel + " compression.");
    }

    /**
     * Process PPT by size
     */
    private void processBySize() {
        String sizeValue = sizeEditText.getText().toString().trim();
        if (sizeValue.isEmpty()) {
            Toast.makeText(this, "Please enter a target file size.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double size = Double.parseDouble(sizeValue);
            if (size <= 0) {
                Toast.makeText(this, "Size must be greater than zero.", Toast.LENGTH_SHORT).show();
                return;
            }
            String unit = unitSpinner.getSelectedItem().toString();
            Log.d("pptRes", "Processing PPT with target size: " + size + " " + unit);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid size format.", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Process document by page range (supports non-continuous pages)
     */
    private void processByPageRange() {
        String pageRangeInput = ((EditText) findViewById(R.id.pageRangeEditText)).getText().toString().trim();

        if (pageRangeInput.isEmpty()) {
            Log.d("pptRes", "No slide range input provided.");
            return;
        }

        // Validate and parse page ranges
        List<Integer> selectedPages = parsePageRanges(pageRangeInput);

        if (selectedPages.isEmpty()) {
            Log.d("pptRes", "Invalid slides range format.");
            return;
        }

        Log.d("pptRes", "Processing document for slides: " + selectedPages);
    }

    /**
     * Parses a page range string (e.g., "1-5,6,8-10") into a list of integers.
     */
    private List<Integer> parsePageRanges(String rangeInput) {
        List<Integer> pages = new ArrayList<>();
        String[] parts = rangeInput.split(",");

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        if (start > 0 && end > 0 && start <= end) {
                            for (int i = start; i <= end; i++) {
                                pages.add(i);
                            }
                        } else {
                            Toast.makeText(this, "Invalid slide range: " + part, Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid format in range: " + part, Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                try {
                    int page = Integer.parseInt(part);
                    if (page > 0) {
                        pages.add(page);
                    } else {
                        Toast.makeText(this, "Slide number must be positive: " + part, Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid slide number: " + part, Toast.LENGTH_SHORT).show();
                }
            }
        }

        return pages;
    }

    /**
     * Process PPT by optimizing images
     */
    private void processByOptimizeImages() {
        boolean reduceResolution = reduceResolutionCheckBox.isChecked();
        boolean convertToGrayscale = convertToGrayscaleCheckBox.isChecked();

        Log.d("pptRes", "Optimizing images: Reduce Resolution - " + reduceResolution + ", Convert to Grayscale - " + convertToGrayscale);
    }

    /**
     * Process PPT by removing animations
     */
    private void processByRemoveAnimations() {
        int selectedAnimationOption = removeAnimationsRadioGroup.getCheckedRadioButtonId();
        String animationOption = "";

        if (selectedAnimationOption == R.id.removeAllAnimations) {
            animationOption = "Remove All Animations";
        } else if (selectedAnimationOption == R.id.removeTransitions) {
            animationOption = "Remove Slide Transition Animations (Keep Object Animations)";
        } else if (selectedAnimationOption == R.id.removeObjTransitions) {
            animationOption = "Remove Text & Image Animations (Keep Slide Transitions)";
        }
        Log.d("pptRes", "Processing PPT: " + animationOption);
    }

}
