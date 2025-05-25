package com.example.fileforge;

import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface CloudConvertApi {

    @POST("v2/jobs")
    Call<Map<String, Object>> createJob(
            @Header("Authorization") String apiKey,
            @Body Map<String, Object> jobPayload
    );

    @Multipart
    @POST
    Call<Map<String, Object>> uploadFile(
            @Url String uploadUrl,
            @Part MultipartBody.Part file
    );

    @GET("v2/jobs/{job_id}")
    Call<Map<String, Object>> getJob(
            @Header("Authorization") String apiKey,
            @Path("job_id") String jobId
    );
}
