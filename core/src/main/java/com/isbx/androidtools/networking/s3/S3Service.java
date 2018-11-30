package com.isbx.androidtools.networking.s3;

import java.io.InputStream;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface S3Service {

    @POST("/")
    @Multipart
    Call<ResponseBody> uploadFile(
        @Part("key") String key,
        @Part("AWSAccessKeyId") String accessKeyId,
        @Part("policy") String policy,
        @Part("signature") String signature,
        @Part("success_action_status") Integer successStatus,
        @Part("acl") String acl,
        @Part MultipartBody.Part filePart,
        @Part("Content-Type") String contentType
    );
}