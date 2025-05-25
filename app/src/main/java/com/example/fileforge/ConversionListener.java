package com.example.fileforge;

/**
 * Interface for receiving callbacks during the CloudConvert conversion process.
 * Activities using CloudConvertHelper will implement this to update their UI.
 */
public interface ConversionListener {

    /**
     * Called when the CloudConvert job has been successfully created.
     * @param jobId The ID of the created job.
     */
    void onJobCreated(String jobId);

    /**
     * Called periodically during the file upload process.
     * @param progress The upload progress (scaled, e.g., 0-60).
     */
    void onUploadProgress(int progress);

    /**
     * Called periodically during the conversion polling process.
     * @param progress The conversion progress (scaled, e.g., 61-99).
     */
    void onConversionProgress(int progress);

    /**
     * Called when the entire conversion process is successful and the
     * download URL is available.
     * @param downloadUrl The URL to download the converted file.
     * @param filename The name of the converted file.
     */
    void onSuccess(String downloadUrl, String filename);

    /**
     * Called if any error occurs during the process.
     * @param message A descriptive error message.
     */
    void onError(String message);

    /**
     * Called if the job polling times out.
     */
    void onTimeout();

}