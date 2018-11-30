package com.isbx.androidtools.networking.s3;

import java.io.InputStream;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface S3Service {

    @POST("/")
    @Multipart
    Call<ResponseBody> uploadFile(
        @Field("key") String key,
        @Field("AWSAccessKeyId") String accessKeyId,
        @Field("policy") String policy,
        @Field("signature") String signature,
        @Field("success_action_status") Integer successStatus,
        @Field("acl") String acl,
        @Part MultipartBody.Part filePart,
        @Field("Content-Type") String contentType
    );
}