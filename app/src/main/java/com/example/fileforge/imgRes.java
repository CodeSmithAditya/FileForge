package com.example.fileforge;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.Locale;

public class imgRes extends AppCompatActivity {

    private Button byDimensionsButton, bySizeButton, asPercentageButton, socialMediaButton, processFileButton;
    private LinearLayout dimensionsLayout, percentageLayout, sizeLayout, socialMediaLayout;
    private EditText widthEditText, heightEditText, sizeEditText;
    private SeekBar percentageSeekBar;
    private TextView percentageTextView, calculatedDimensionsTextView;
    private Spinner socialMediaSpinner, dimensionUnit;
    private ImageView selectedImageView;
    private CheckBox lockAspectRatioCheckBox;
    private String fileUriString;
    private int originalWidth, originalHeight;

    private boolean isUpdating = false;

    private String[] fullUnitNames = {"pixel", "centimeter", "millimeter", "inch"};
    private String[] shortUnitNames = {"px", "cm", "mm", "in"};
    private float[] conversionFactors = {1f, 0.0264583f, 0.264583f, 0.0104167f}; // px to cm, mm, in

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_img_res);

        // Initialize UI Elements
        selectedImageView = findViewById(R.id.selectedImageView);
        byDimensionsButton = findViewById(R.id.byDimensionsButton);
        bySizeButton = findViewById(R.id.bySizeButton);
        asPercentageButton = findViewById(R.id.asPercentageButton);
        socialMediaButton = findViewById(R.id.socialMediaButton);
        processFileButton = findViewById(R.id.processFileButton);

        dimensionsLayout = findViewById(R.id.dimensionsLayout);
        percentageLayout = findViewById(R.id.percentageLayout);
        sizeLayout = findViewById(R.id.sizeLayout);
        socialMediaLayout = findViewById(R.id.socialMediaLayout);

        widthEditText = findViewById(R.id.widthEditText);
        heightEditText = findViewById(R.id.heightEditText);
        sizeEditText = findViewById(R.id.sizeEditText);
        percentageSeekBar = findViewById(R.id.percentageSeekBar);
        percentageTextView = findViewById(R.id.percentageTextView);
        socialMediaSpinner = findViewById(R.id.socialMediaSpinner);
        calculatedDimensionsTextView = findViewById(R.id.calculatedDimensionsTextView);
        lockAspectRatioCheckBox = findViewById(R.id.lockAspectRatioCheckBox);
        dimensionUnit = findViewById(R.id.dimensionUnit);

        preventNegativeInput(widthEditText);
        preventNegativeInput(heightEditText);
        preventNegativeInput(sizeEditText);
        validateSizeInput();

        setupAspectRatioLock();
        lockAspectRatioCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                heightEditText.setText(""); // Clear height when unlocking aspect ratio
            }
        });

        socialMediaButton.setOnClickListener(v -> switchMode(socialMediaLayout, socialMediaButton));

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.social_media_sizes,
                R.layout.spinner_text);  // Custom Layout

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        socialMediaSpinner.setAdapter(adapter);

        // Set up spinner with UnitAdapter
        UnitAdapter unitAdapter = new UnitAdapter(this, android.R.layout.simple_spinner_dropdown_item, fullUnitNames, shortUnitNames);
        dimensionUnit.setAdapter(unitAdapter);

        // Get file URI
        fileUriString = getIntent().getStringExtra("fileUri");

        // Load image if selected
        if (fileUriString != null) {
            Uri fileUri = Uri.parse(fileUriString);
            Glide.with(this)
                    .asBitmap()
                    .load(fileUri)
                    .placeholder(R.drawable.image_placeholder) // Show while loading
                    .error(R.drawable.image_error) // If loading fails
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            selectedImageView.setImageBitmap(resource);
                            originalWidth = resource.getWidth();
                            originalHeight = resource.getHeight();

                            if (originalWidth > 0 && originalHeight > 0) {
                                updateEditTextDimensions(originalWidth, originalHeight);
                            }
                            calculateAndDisplayDimensions(percentageSeekBar.getProgress());

                            switchMode(dimensionsLayout, byDimensionsButton);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        } else {
            selectedImageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Button Click Listeners
        byDimensionsButton.setOnClickListener(v -> switchMode(dimensionsLayout, byDimensionsButton));
        bySizeButton.setOnClickListener(v -> switchMode(sizeLayout, bySizeButton));
        asPercentageButton.setOnClickListener(v -> switchMode(percentageLayout, asPercentageButton));
        socialMediaButton.setOnClickListener(v -> switchMode(socialMediaLayout, socialMediaButton));

        // SeekBar Listener for Percentage Resizing
        percentageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                percentageTextView.setText(progress + "%");
                calculateAndDisplayDimensions(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Process Image Button Click
        processFileButton.setOnClickListener(v -> processImage());

        // Spinner Item Selected Listener
        dimensionUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateEditTextDimensions(originalWidth, originalHeight);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupAspectRatioLock() {
        widthEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (originalWidth == 0 || originalHeight == 0) return;
                if (!isUpdating && lockAspectRatioCheckBox.isChecked() && !s.toString().isEmpty()) {
                    try {
                        isUpdating = true;
                        double width = Double.parseDouble(s.toString());
                        double height = width / ((double) originalWidth / originalHeight);

                        String selectedUnit = dimensionUnit.getSelectedItem().toString();
                        if (selectedUnit.equals("pixel")) {
                            heightEditText.setText(String.valueOf((int) height));
                        } else {
                            heightEditText.setText(String.format(Locale.US, "%.2f", height));
                        }
                    } catch (NumberFormatException ignored) {}
                    finally { isUpdating = false; }
                }
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        heightEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdating && lockAspectRatioCheckBox.isChecked() && !s.toString().isEmpty()) {
                    try {
                        isUpdating = true;
                        double height = Double.parseDouble(s.toString());
                        double width = height * ((double) originalWidth / originalHeight);

                        String selectedUnit = dimensionUnit.getSelectedItem().toString();
                        if (selectedUnit.equals("pixel")) {
                            widthEditText.setText(String.valueOf((int) width));
                        } else {
                            widthEditText.setText(String.format(Locale.US, "%.2f", width));
                        }
                    } catch (NumberFormatException ignored) {}
                    finally { isUpdating = false; }
                }
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void preventNegativeInput(EditText editText) {
        editText.setFilters(new InputFilter[]{
                (source, start, end, dest, dstart, dend) -> {
                    String newInput = dest.toString().substring(0, dstart) + source + dest.toString().substring(dend);
                    if (!newInput.isEmpty()) {
                        try {
                            double value = Double.parseDouble(newInput);
                            if (!newInput.matches("\\d*\\.?\\d*")) return ""; // Prevent negative input
                        } catch (NumberFormatException e) {
                            return ""; // Prevent invalid input
                        }
                    }
                    return null; // Accept input
                }
        });
    }

    private void validateSizeInput() {
        sizeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    double size = Double.parseDouble(s.toString());
                    if (size < 10) { // Prevent too-small sizes
                        sizeEditText.setError("Size too small (min: 10 KB)");
                    } else if (size > 5000) { // Prevent excessive sizes
                        sizeEditText.setError("Size too large (max: 5000 KB)");
                    }
                } catch (NumberFormatException ignored) {}
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    /**
     * Function to switch between resize modes
     */
    private void switchMode(LinearLayout layoutToShow, Button selectedButton) {
        if (layoutToShow.getVisibility() == View.VISIBLE) return; // Prevent redundant calls
        // Hide all layouts
        dimensionsLayout.setVisibility(View.GONE);
        percentageLayout.setVisibility(View.GONE);
        sizeLayout.setVisibility(View.GONE);
        socialMediaLayout.setVisibility(View.GONE);

        // Reset button states
        byDimensionsButton.setBackgroundResource(R.drawable.button_unselected);
        bySizeButton.setBackgroundResource(R.drawable.button_unselected);
        asPercentageButton.setBackgroundResource(R.drawable.button_unselected);
        socialMediaButton.setBackgroundResource(R.drawable.button_unselected);

        // Show selected layout and highlight button
        layoutToShow.setVisibility(View.VISIBLE);
        selectedButton.setBackgroundResource(R.drawable.button_selected);
    }

    private float getImageDPI() {
        try {
            Uri fileUri = Uri.parse(fileUriString);
            ExifInterface exif = new ExifInterface(getContentResolver().openInputStream(fileUri));

            int xDpi = exif.getAttributeInt(ExifInterface.TAG_X_RESOLUTION, 0);
            int yDpi = exif.getAttributeInt(ExifInterface.TAG_Y_RESOLUTION, 0);

            // If no valid DPI found, fallback to 300 DPI
            float dpi = (xDpi > 0) ? xDpi : ((yDpi > 0) ? yDpi : 300);
            return dpi;
        } catch (Exception e) {
            e.printStackTrace();
            return 300; // Default to 300 DPI if metadata is missing
        }
    }

    /**
     * Update the dimensions based on selected image and unit
     */
    private void updateEditTextDimensions(int widthPixels, int heightPixels) {
        float dpi = getImageDPI(); // Get the actual DPI from the image (fallback to 300)

        double width = widthPixels;
        double height = heightPixels;

        switch (dimensionUnit.getSelectedItem().toString()) {
            case "inch":
                width = widthPixels / dpi;
                height = heightPixels / dpi;
                break;
            case "centimeter":
                width = (widthPixels / dpi) * 2.54;
                height = (heightPixels / dpi) * 2.54;
                break;
            case "millimeter":
                width = (widthPixels / dpi) * 25.4;
                height = (heightPixels / dpi) * 25.4;
                break;
            case "pixel":
            default:
                width = widthPixels;
                height = heightPixels;
        }

        if (dimensionUnit.getSelectedItem().toString().equals("pixel")) {
            widthEditText.setText(String.valueOf((int) width));
            heightEditText.setText(String.valueOf((int) height));
        } else {
            widthEditText.setText(String.format(Locale.US, "%.2f", width));
            heightEditText.setText(String.format(Locale.US, "%.2f", height));
        }
    }

    /**
     * Calculate new dimensions based on percentage resize
     */
    private void calculateAndDisplayDimensions(int percentage) {
        if (originalWidth == 0 || originalHeight == 0) {
            calculatedDimensionsTextView.setText("Image size unavailable.");
            return;
        }
        int newWidth = (int) (originalWidth * (percentage / 100.0));
        int newHeight = (int) (originalHeight * (percentage / 100.0));
        calculatedDimensionsTextView.setText("Make my image " + percentage + "% of original size (" + newWidth + "x" + newHeight + " px)");
    }

    private boolean resizeImage(double width, double height) {
        try {
            // Load the image from the URI
            Uri fileUri = Uri.parse(fileUriString);
            Bitmap originalBitmap = Glide.with(this)
                    .asBitmap()
                    .load(fileUri)
                    .submit()
                    .get(); // Load synchronously

            if (originalBitmap == null) return false;

            // Scale the image to new dimensions
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, (int) width, (int) height, true);

            // Set the resized image in the ImageView
            selectedImageView.setImageBitmap(resizedBitmap);

            // TODO: Save the resized image to file (if needed)
            return true; // Success
        } catch (Exception e) {
            e.printStackTrace();
            return false; // Failed
        }
    }

    /**
     * Process Image based on selected mode
     */
    private void processImage() {
        if (dimensionsLayout.getVisibility() == View.VISIBLE) {
            try {
                double width = Double.parseDouble(widthEditText.getText().toString());
                double height = Double.parseDouble(heightEditText.getText().toString());

                if (width <= 0 || height <= 0) {
                    Toast.makeText(this, "Width and height must be positive numbers!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Log.d("imgRes", "Resize by Dimensions: " + width + "x" + height);
                // TODO: Implement actual image resizing logic here
                boolean success = resizeImage(width, height);
                if (success) {
                    Toast.makeText(this, "Image resized to: " + width + "x" + height, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Resizing failed!", Toast.LENGTH_SHORT).show();
                }

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid dimensions entered!", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (percentageLayout.getVisibility() == View.VISIBLE) {
            int percentage = percentageSeekBar.getProgress();
            if (percentage <= 0) {
                Toast.makeText(this, "Percentage must be greater than 0!", Toast.LENGTH_SHORT).show();
                return;
            }
            int newWidth = (int) (originalWidth * (percentage / 100.0));
            int newHeight = (int) (originalHeight * (percentage / 100.0));

            Log.d("imgRes", "Resize by Percentage: " + percentage + "% (" + newWidth + "x" + newHeight + ")");
            Toast.makeText(this, "Resizing image to " + newWidth + "x" + newHeight, Toast.LENGTH_SHORT).show();

        } else if (sizeLayout.getVisibility() == View.VISIBLE) {
            try {
                int targetSize = Integer.parseInt(sizeEditText.getText().toString());

                if (targetSize < 10 || targetSize > 5000) {
                    Toast.makeText(this, "Size must be between 10KB and 5000KB!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Log.d("imgRes", "Resize by File Size: " + targetSize + "KB");
                Toast.makeText(this, "Resizing to approx " + targetSize + " KB", Toast.LENGTH_SHORT).show();

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid file size entered!", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    }

}
