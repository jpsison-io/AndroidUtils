package com.isbx.androidtools.networking.s3;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface S3Service {

    @POST("/")
    @Multipart
    Call<ResponseBody> uploadFile(
        @Part("key") RequestBody key,
        @Part("AWSAccessKeyId") RequestBody accessKeyId,
        @Part("policy") RequestBody policy,
        @Part("signature") RequestBody signature,
        @Part("success_action_status") RequestBody successStatus,
        @Part("acl") RequestBody acl,
        @Part("Content-Type") RequestBody contentType,
        @Part MultipartBody.Part filePart
    );
}