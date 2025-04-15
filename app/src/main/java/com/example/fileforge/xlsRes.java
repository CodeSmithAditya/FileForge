package com.example.fileforge;

import android.app.ProgressDialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class xlsRes extends AppCompatActivity {

    private Button byQualityButton, bySizeButton, cleanSheetsButton, optimizeMediaButton,
            removeExtrasButton, clearCacheButton, processFileButton;
    private LinearLayout qualityLayout, sizeLayout, cleanSheetsLayout, optimizeMediaLayout,
            removeExtrasLayout, clearCacheLayout, sheetSelectionContainer;
    private ScrollView scrollView;
    private TextView fileNameTextView;
    private EditText sizeEditText;
    private Spinner unitSpinner;
    private CheckBox removeEmptyRowsColumns, removeUnusedNamedRanges, removeExternalLinks;
    private RadioGroup qualityRadioGroup, sheetRemovalOptions;
    private LinearLayout sheetCheckboxContainer;

    private ProgressBar sheetLoadingProgressBar;

    private String fileUriString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xls_res);

        // Initialize UI elements
        scrollView = findViewById(R.id.scrollView);
        fileNameTextView = findViewById(R.id.fileNameTextView);
        byQualityButton = findViewById(R.id.byQualityButton);
        bySizeButton = findViewById(R.id.bySizeButton);
        cleanSheetsButton = findViewById(R.id.cleanSheetsButton);
        optimizeMediaButton = findViewById(R.id.optimizeMediaButton);
        removeExtrasButton = findViewById(R.id.removeExtrasButton);
        clearCacheButton = findViewById(R.id.clearCacheButton);
        processFileButton = findViewById(R.id.processFileButton);

        qualityLayout = findViewById(R.id.qualityLayout);
        sizeLayout = findViewById(R.id.sizeLayout);
        cleanSheetsLayout = findViewById(R.id.cleanSheetsLayout);
        optimizeMediaLayout = findViewById(R.id.optimizeMediaLayout);
        removeExtrasLayout = findViewById(R.id.removeExtrasLayout);
        clearCacheLayout = findViewById(R.id.clearCacheLayout);

        sizeEditText = findViewById(R.id.sizeEditText);
        unitSpinner = findViewById(R.id.unitSpinner);
        removeEmptyRowsColumns = findViewById(R.id.removeEmptyRowsColumns);
        removeUnusedNamedRanges = findViewById(R.id.removeUnusedNamedRanges);
        removeExternalLinks = findViewById(R.id.removeExternalLinks);

        qualityRadioGroup = findViewById(R.id.qualityRadioGroup);
        sheetRemovalOptions = findViewById(R.id.sheetRemovalOptions);
        sheetSelectionContainer = findViewById(R.id.sheetSelectionContainer);
        sheetCheckboxContainer = findViewById(R.id.sheetCheckboxContainer);
        sheetLoadingProgressBar=findViewById(R.id.sheetLoadingProgressBar);

        preventNegativeInput(sizeEditText);

        // Get file URI from intent
        fileUriString = getIntent().getStringExtra("fileUri");

        if (fileUriString != null && !fileUriString.isEmpty()) {
            Uri fileUri = Uri.parse(fileUriString);
            fileNameTextView.setText(getFileName(fileUri));

            // 🔹 Show "By Quality" options by default
            switchMode(qualityLayout, byQualityButton);
        } else {
            fileNameTextView.setText("No file selected.");
            processFileButton.setEnabled(false); // Disable "Process" button
        }

        // Set button click listeners
        byQualityButton.setOnClickListener(v -> switchMode(qualityLayout, byQualityButton));
        bySizeButton.setOnClickListener(v -> switchMode(sizeLayout, bySizeButton));
        cleanSheetsButton.setOnClickListener(v -> switchMode(cleanSheetsLayout, cleanSheetsButton));
        optimizeMediaButton.setOnClickListener(v -> switchMode(optimizeMediaLayout, optimizeMediaButton));
        removeExtrasButton.setOnClickListener(v -> switchMode(removeExtrasLayout, removeExtrasButton));
        clearCacheButton.setOnClickListener(v -> switchMode(clearCacheLayout, clearCacheButton));

        processFileButton.setOnClickListener(v -> {
            ProgressDialog progressDialog = new ProgressDialog(xlsRes.this);
            progressDialog.setMessage("Processing file...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            Executors.newSingleThreadExecutor().execute(() -> {
                processExcelFile();
                runOnUiThread(progressDialog::dismiss);
            });
        });


        // Handle sheet removal option changes
        sheetRemovalOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.selectSheetsRadio) {
                sheetSelectionContainer.setVisibility(View.VISIBLE);
                loadSheetCheckboxes();
            } else {
                sheetSelectionContainer.setVisibility(View.GONE);
            }
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
     * Function to switch between different modes
     */
    private void switchMode(LinearLayout layoutToShow, Button selectedButton) {
        // Hide all layouts
        qualityLayout.setVisibility(View.GONE);
        sizeLayout.setVisibility(View.GONE);
        cleanSheetsLayout.setVisibility(View.GONE);
        optimizeMediaLayout.setVisibility(View.GONE);
        removeExtrasLayout.setVisibility(View.GONE);
        clearCacheLayout.setVisibility(View.GONE);

        // Reset button styles
        byQualityButton.setBackgroundResource(R.drawable.button_unselected);
        bySizeButton.setBackgroundResource(R.drawable.button_unselected);
        cleanSheetsButton.setBackgroundResource(R.drawable.button_unselected);
        optimizeMediaButton.setBackgroundResource(R.drawable.button_unselected);
        removeExtrasButton.setBackgroundResource(R.drawable.button_unselected);
        clearCacheButton.setBackgroundResource(R.drawable.button_unselected);

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
        if (uri == null) return "Unknown File";  // Null safety

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

        return (result != null && !result.isEmpty()) ? result : (uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "Unknown File");
    }

    /**
     * Process the Excel file based on selected settings
     */
    private void processExcelFile() {
        String selectedMode = "";

        if (qualityLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Quality Compression";
            processByQuality();
        } else if (sizeLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "File Size";
            processBySize();
        } else if (cleanSheetsLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Clean Sheets";
            processByCleanSheets();
        } else if (optimizeMediaLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Optimize Media";
            processByOptimizeMedia();
        } else if (removeExtrasLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Remove Extras";
            processByRemoveExtras();
        } else if (clearCacheLayout.getVisibility() == View.VISIBLE) {
            selectedMode = "Clear Cache";
            processByClearCache();
        }

        Log.d("xlsRes", "Processing Excel file with mode: " + selectedMode);
    }

    /**
     * Process Excel file by quality compression
     */
    private void processByQuality() {
        Log.d("xlsRes", "Processing Excel file with quality compression.");
    }

    /**
     * Process Excel file by size
     */
    private void processBySize() {
        Log.d("xlsRes", "Processing Excel file with target size.");
    }

    /**
     * Process Excel file by cleaning sheets
     */
    private void processByCleanSheets() {
        Log.d("xlsRes", "Cleaning up sheets.");
    }

    /**
     * Process Excel file by optimizing media
     */
    private void processByOptimizeMedia() {
        Log.d("xlsRes", "Optimizing media in Excel file.");
    }

    /**
     * Process Excel file by removing extras
     */
    private void processByRemoveExtras() {
        Log.d("xlsRes", "Removing extra elements from Excel file.");
    }

    /**
     * Process Excel file by clearing cache
     */
    private void processByClearCache() {
        Log.d("xlsRes", "Clearing cache in Excel file.");
    }

    /**
     * Load sample sheet names dynamically
     */
    private void loadSheetCheckboxes() {
        sheetCheckboxContainer.removeAllViews();
        sheetLoadingProgressBar.setVisibility(View.VISIBLE);  // Show ProgressBar

        Executors.newSingleThreadExecutor().execute(() -> {
            List<String> sheetNames = getSheetNamesFromExcel(fileUriString);

            runOnUiThread(() -> {
                sheetLoadingProgressBar.setVisibility(View.GONE);  // Hide ProgressBar

                if (sheetNames.isEmpty()) {
                    Toast.makeText(this, "Could not read sheets. Check file format.", Toast.LENGTH_SHORT).show();
                    processFileButton.setEnabled(false);  // Disable process button
                    return;
                }

                processFileButton.setEnabled(true);  // Enable process button if sheets are found

                for (String sheetName : sheetNames) {
                    CheckBox checkBox = new CheckBox(this);
                    checkBox.setText(sheetName);
                    checkBox.setTextSize(18);
                    sheetCheckboxContainer.addView(checkBox);
                }
            });
        });
    }

    private List<String> getSheetNamesFromExcel(String fileUriString) {
        List<String> sheetNames = new ArrayList<>();

        if (fileUriString == null || fileUriString.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "Invalid file path.", Toast.LENGTH_SHORT).show());
            return sheetNames;
        }

        try {
            Uri fileUri = Uri.parse(fileUriString);
            try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
                 Workbook workbook = WorkbookFactory.create(inputStream)) {

                int sheetCount = workbook.getNumberOfSheets();
                for (int i = 0; i < sheetCount; i++) {
                    sheetNames.add(workbook.getSheetAt(i).getSheetName());
                }

            }
        } catch (Exception e) {
            Log.e("xlsRes", "Error reading sheet names", e);
            runOnUiThread(() -> Toast.makeText(this, "Failed to read sheets. Unsupported format?", Toast.LENGTH_SHORT).show());
        }

        return sheetNames;
    }

}
