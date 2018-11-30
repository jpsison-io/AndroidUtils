package com.isbx.androidtools.networking.s3;

import java.io.InputStream;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.Multipart;
import retrofit2.http.POST;

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
        @Field("file") InputStream file,
        @Field("Content-Type") String contentType
    );
}