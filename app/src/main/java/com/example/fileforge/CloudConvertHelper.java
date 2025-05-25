package com.example.fileforge;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject; // For building JSON for OkHttp request

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
// import java.net.URLConnection; // Not strictly needed if mimeType is resolved otherwise
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import retrofit2.Call;
// Removed retrofit2.Callback as createJob will use okhttp3.Callback directly for rotation
// Keep it if other Retrofit calls might need it, but getJob will use its own.


public class CloudConvertHelper {

    private static final String TAG = "CloudConvertHelper";
    private Context context;
    private CloudConvertApi retrofitApi; // For getJob
    private List<String> apiKeysList;
    private OkHttpClient sharedOkHttpClient; // Re-use OkHttpClient for job creation

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final int POLL_INTERVAL_MS = 3000;

    interface ProgressUpdateListener {
        void onProgress(long bytesWritten, long contentLength);
    }

    public CloudConvertHelper(Context context) {
        this.context = context.getApplicationContext();
        this.retrofitApi = RetrofitClient.getApi(); // For getJob

        if (BuildConfig.CLOUDCONVERT_API_KEYS != null && BuildConfig.CLOUDCONVERT_API_KEYS.length > 0) {
            this.apiKeysList = new ArrayList<>(Arrays.asList(BuildConfig.CLOUDCONVERT_API_KEYS));
            Log.i(TAG, "Loaded " + this.apiKeysList.size() + " API keys.");
        } else {
            this.apiKeysList = new ArrayList<>();
            Log.e(TAG, "No API keys found in BuildConfig. CLOUDCONVERT_API_KEYS is empty or null.");
        }

        // Shared OkHttpClient for creating jobs, with timeouts
        this.sharedOkHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS) // Timeout for sending job creation request
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void startConversion(Uri fileUri, String targetFormat, Map<String, Object> convertOptions, ConversionListener listener) {
        Log.d(TAG, "Starting conversion for Uri: " + fileUri + " to " + targetFormat);
        if (fileUri == null || listener == null) {
            if (listener != null) mainHandler.post(() -> listener.onError("File Uri or Listener cannot be null."));
            return;
        }
        if (apiKeysList.isEmpty()) {
            Log.e(TAG, "API key list is empty. Cannot proceed.");
            mainHandler.post(() -> listener.onError("No API keys configured."));
            return;
        }
        // Start attempt with the first key (index 0)
        attemptCreateJobWithOkHttp(fileUri, targetFormat, convertOptions, listener, 0);
    }

    private void attemptCreateJobWithOkHttp(
            final Uri fileUri,
            final String targetFormat,
            final Map<String, Object> convertOptions,
            final ConversionListener listener,
            final int keyIndex) {

        if (keyIndex >= apiKeysList.size()) {
            Log.e(TAG, "All API keys tried and failed for job creation.");
            mainHandler.post(() -> listener.onError("Job creation failed: All API keys exhausted."));
            return;
        }

        final String currentApiKey = apiKeysList.get(keyIndex);
        Log.i(TAG, "Attempting job creation with API key index: " + keyIndex);

        String fileName = getFileNameFromUri(fileUri);
        Map<String, Object> importTask = new HashMap<>();
        importTask.put("operation", "import/upload");
        Map<String, Object> convertTaskMap = new HashMap<>();
        convertTaskMap.put("operation", "convert");
        convertTaskMap.put("input", "import-myfile");
        convertTaskMap.put("output_format", targetFormat);
        if (convertOptions != null && !convertOptions.isEmpty()) {
            convertTaskMap.putAll(convertOptions);
        }
        if (!convertTaskMap.containsKey("engine")) {
            String engine = (targetFormat.equals("jpg") || targetFormat.equals("png") || targetFormat.equals("jpeg")) ? "imagemagick" : "office";
            convertTaskMap.put("engine", engine);
        }
        Map<String, Object> exportTask = new HashMap<>();
        exportTask.put("operation", "export/url");
        exportTask.put("input", "convert-myfile");
        Map<String, Object> tasks = new HashMap<>();
        tasks.put("import-myfile", importTask);
        tasks.put("convert-myfile", convertTaskMap);
        tasks.put("export-myfile", exportTask);
        Map<String, Object> jobRequestPayloadMap = new HashMap<>();
        jobRequestPayloadMap.put("tasks", tasks);

        JSONObject jobDataJson = new JSONObject(jobRequestPayloadMap); // Convert Map to JSONObject for OkHttp

        RequestBody body = RequestBody.create(jobDataJson.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("https://api.cloudconvert.com/v2/jobs")
                .addHeader("Authorization", "Bearer " + currentApiKey)
                .post(body)
                .build();

        sharedOkHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                Log.e(TAG, "OkHttp: Job creation network error with key index " + keyIndex + ": ", e);
                // For network error, ideally retry with same key or have a strategy.
                // To match the spirit of rotation on any failure for this step:
                attemptCreateJobWithOkHttp(fileUri, targetFormat, convertOptions, listener, keyIndex + 1);
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                String responseBodyString = null;
                try {
                    if (response.body() != null) {
                        responseBodyString = response.body().string(); // Read once
                    }

                    if (response.isSuccessful() && responseBodyString != null) {
                        Log.d(TAG, "OkHttp: Job created successfully with key index " + keyIndex + ". Response: " + responseBodyString);
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBodyString);
                            JSONObject data = jsonResponse.optJSONObject("data");
                            if (data != null) {
                                String jobId = data.optString("id");
                                String uploadUrl = null;
                                Map<String, String> formFields = new HashMap<>();
                                org.json.JSONArray tasksArray = data.optJSONArray("tasks");
                                if (tasksArray != null) {
                                    for (int i = 0; i < tasksArray.length(); i++) {
                                        JSONObject taskJson = tasksArray.optJSONObject(i);
                                        if (taskJson != null && "import-myfile".equals(taskJson.optString("name"))) {
                                            JSONObject result = taskJson.optJSONObject("result");
                                            if (result != null) {
                                                JSONObject form = result.optJSONObject("form");
                                                if (form != null) {
                                                    uploadUrl = form.optString("url");
                                                    JSONObject parameters = form.optJSONObject("parameters");
                                                    if (parameters != null) {
                                                        for (java.util.Iterator<String> keys = parameters.keys(); keys.hasNext(); ) {
                                                            String paramKey = keys.next();
                                                            formFields.put(paramKey, parameters.optString(paramKey));
                                                        }
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }

                                if (jobId != null && !jobId.isEmpty() && uploadUrl != null && !uploadUrl.isEmpty() && !formFields.isEmpty()) {
                                    final String finalJobId = jobId;
                                    mainHandler.post(() -> listener.onJobCreated(finalJobId));
                                    uploadFileToCloudConvert(uploadUrl, formFields, fileUri, jobId, "Bearer " + currentApiKey, listener); // Pass the successful key
                                    return; // Success, stop recursion
                                } else {
                                    Log.e(TAG, "OkHttp: Critical data missing from successful job creation response.");
                                    mainHandler.post(() -> listener.onError("Failed to parse job creation response."));
                                }
                            } else {
                                Log.e(TAG, "OkHttp: 'data' field missing in job response.");
                                mainHandler.post(() -> listener.onError("'data' field missing in job response."));
                            }
                        } catch (org.json.JSONException e) {
                            Log.e(TAG, "OkHttp: JSON parsing error for job creation response.", e);
                            mainHandler.post(() -> listener.onError("Error parsing job response."));
                        }
                    } else {
                        int errorCode = response.code();
                        Log.w(TAG, "OkHttp: Job creation failed with key index " + keyIndex + ". Code: " + errorCode + ". Body: " + responseBodyString);
                        if (errorCode == 401 || errorCode == 403 || errorCode == 429 || errorCode == 402) { // Unauthorized, Forbidden, Too Many Req, Payment Req
                            attemptCreateJobWithOkHttp(fileUri, targetFormat, convertOptions, listener, keyIndex + 1); // Try next key
                        } else {
                            mainHandler.post(() -> listener.onError("Job creation failed (Code: " + errorCode + ") " + response.message()));
                        }
                    }
                } finally {
                    if (response != null) {
                        response.close(); // Ensure response body is closed
                    }
                }
            }
        });
    }


    // uploadFileToCloudConvert remains largely the same but receives the apiKeyUsedForJob
    private void uploadFileToCloudConvert(String uploadUrl, Map<String, String> formFields, Uri fileUri, String jobId, String apiKeyUsedForJob, ConversionListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String filePath = getFileFromUri(fileUri);
                if (filePath == null) {
                    mainHandler.post(() -> listener.onError("Failed to access file for upload."));
                    return;
                }
                File file = new File(filePath);

                // Use a dedicated OkHttpClient for upload with specific timeouts
                OkHttpClient uploadClient = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(120, TimeUnit.SECONDS) // Longer for actual file upload
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();

                MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
                for (Map.Entry<String, String> entry : formFields.entrySet()) {
                    builder.addFormDataPart(entry.getKey(), entry.getValue());
                }
                String mimeType = context.getContentResolver().getType(fileUri);
                if (mimeType == null) mimeType = "application/octet-stream";
                RequestBody fileBody = new ProgressRequestBody(file, mimeType, (bytesWritten, contentLength) -> {
                    final int scaledProgress = (int) (((100 * bytesWritten) / contentLength) * 0.60);
                    mainHandler.post(() -> listener.onUploadProgress(scaledProgress));
                });
                builder.addFormDataPart("file", file.getName(), fileBody);
                Request request = new Request.Builder().url(uploadUrl).post(builder.build()).build();
                Response response = uploadClient.newCall(request).execute();

                if (response.isSuccessful()) {
                    Log.d(TAG, "File upload successful. Starting poll for job: " + jobId);
                    mainHandler.post(() -> listener.onUploadProgress(60));
                    pollJobStatus(jobId, apiKeyUsedForJob, listener); // Pass the API key used for the job
                } else {
                    Log.e(TAG, "File upload failed with code: " + response.code() + " | " + response.message());
                    mainHandler.post(() -> listener.onError("File upload failed (Code: " + response.code() + ")"));
                }
                response.close();
            } catch (Exception e) {
                Log.e(TAG, "Upload error: ", e);
                mainHandler.post(() -> listener.onError("Upload error: " + e.getMessage()));
            }
        });
    }

    // pollJobStatus now receives the apiKey that was used to create this specific job
    private void pollJobStatus(String jobId, String apiKeyUsedForJob, ConversionListener listener) {
        final Runnable[] pollRunnable = new Runnable[1];
        final int[] attempts = {0};

        pollRunnable[0] = () -> {
            if (attempts[0]++ >= MAX_POLL_ATTEMPTS) {
                mainHandler.post(listener::onTimeout);
                return;
            }
            Log.d(TAG, "Polling job " + jobId + " (using key that created it), attempt " + attempts[0]);
            // Use the specific apiKeyUsedForJob for this job's polling
            retrofitApi.getJob(apiKeyUsedForJob, jobId).enqueue(new retrofit2.Callback<Map<String, Object>>() {
                @Override
                public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull retrofit2.Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> jobData = (Map<String, Object>) response.body().get("data");
                        if (jobData != null) {
                            String status = (String) jobData.get("status");
                            Log.d(TAG, "Job status: " + status);
                            if ("finished".equalsIgnoreCase(status)) {
                                mainHandler.post(() -> listener.onConversionProgress(100));
                                handleConversionSuccess(jobData, listener);
                            } else if ("error".equalsIgnoreCase(status)) {
                                Log.e(TAG, "Conversion job failed. Data: " + jobData);
                                mainHandler.post(() -> listener.onError("Conversion failed on server (during poll)."));
                            } else {
                                int convertingProgress = 61 + (int) ((attempts[0] / (float) MAX_POLL_ATTEMPTS) * 38);
                                if (convertingProgress > 99) convertingProgress = 99;
                                final int finalProgress = convertingProgress;
                                mainHandler.post(() -> listener.onConversionProgress(finalProgress));
                                mainHandler.postDelayed(pollRunnable[0], POLL_INTERVAL_MS);
                            }
                        } else {
                            mainHandler.post(() -> listener.onError("Job data missing during polling."));
                        }
                    } else {
                        // If polling fails with key-specific errors (401, 403, 429, 402) for THIS job,
                        // it means the key used to create the job became invalid/limited for THIS job.
                        // We should not rotate to other keys for an existing job. Report error.
                        Log.e(TAG, "Job status fetch failed with code: " + response.code() + " for job " + jobId);
                        mainHandler.post(() -> listener.onError("Job status fetch failed (Code: " + response.code() + ")"));
                    }
                }
                @Override
                public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                    Log.e(TAG, "Polling network error for job " + jobId + ": ", t);
                    mainHandler.post(() -> listener.onError("Polling network error: " + t.getMessage()));
                }
            });
        };
        mainHandler.post(pollRunnable[0]);
    }

    // handleConversionSuccess remains the same
    private void handleConversionSuccess(Map<String, Object> jobData, ConversionListener listener) {
        // ... (Same as before, extracts download URL and calls listener.onSuccess) ...
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) jobData.get("tasks");
        if (tasks != null) { /* ... find export/url task ... */ }
        // ... (call listener.onSuccess or listener.onError)
        // For brevity, assuming full implementation from previous version
        List<Map<String, Object>> tasksList = (List<Map<String, Object>>) jobData.get("tasks");
        if (tasksList != null) {
            for (Map<String, Object> task : tasksList) {
                if ("export/url".equals(task.get("operation")) && "finished".equals(task.get("status"))) {
                    try {
                        Map<String, Object> result = (Map<String, Object>) task.get("result");
                        if (result != null) {
                            List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("files");
                            if (files != null && !files.isEmpty()) {
                                Map<String, Object> file = files.get(0);
                                String downloadUrl = (String) file.get("url");
                                String filename = (String) file.get("filename");
                                if (downloadUrl != null && filename != null) {
                                    mainHandler.post(() -> listener.onSuccess(downloadUrl, filename));
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error extracting download URLs from jobData", e);
                        mainHandler.post(() -> listener.onError("Error processing finished job result."));
                        return;
                    }
                }
            }
        }
        mainHandler.post(() -> listener.onError("Could not find exported file URL in finished job."));
    }

    // --- Utility Methods (getFileNameFromUri, getFileFromUri, sanitizeFileName) are same as before ---
    private String getFileNameFromUri(Uri uri) { /* ... Same ... */ return "";}
    private String getFileFromUri(Uri uri) { /* ... Same ... */ return null;}
    private String sanitizeFileName(String input) { /* ... Same ... */ return "";}


    // --- ProgressRequestBody Inner Class is same as before ---
    private static class ProgressRequestBody extends RequestBody { /* ... Same ... */
        private final File file; private final String contentType; private final ProgressUpdateListener listener;
        private static final int DEFAULT_BUFFER_SIZE = 4096;
        public ProgressRequestBody(File file, String contentType, ProgressUpdateListener listener) {
            this.file = file; this.contentType = contentType; this.listener = listener;
        }
        @Override public MediaType contentType() { return MediaType.parse(contentType); }
        @Override public long contentLength() { return file.length(); }
        @Override public void writeTo(@NonNull BufferedSink sink) throws IOException { /* ... Same ... */ }
    }
}