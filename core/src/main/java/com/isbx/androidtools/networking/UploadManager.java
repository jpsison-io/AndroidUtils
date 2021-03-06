package com.isbx.androidtools.networking;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;

import com.isbx.androidtools.networking.s3.S3Credentials;
import com.isbx.androidtools.networking.s3.S3CredentialsProvider;
import com.isbx.androidtools.networking.s3.S3Service;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * This class provides a simple interface for uploading an arbitrary number of files to Amazon S3.
 *
 * <p>
 * The class relies on a provided {@link S3CredentialsProvider} to configure the details of how
 * the upload requests should be made. For every upload, the credentials provider will be invoked to
 * retrieve a new {@link S3Credentials} instance. The information in this instance will then be used
 * to configure the target S3 url, set the AWS authentication parameters, and set the key property
 * for the uploaded file.
 * </p>
 *
 * <p>
 * The key property can be additionally configured by passing in a {@link SuffixRule} instance when
 * executing the upload. The resulting url will look like this:
 * </p>
 *
 * <code>https://{credentials.getBucket()}.s3.amazonaws.com/{credentials.getUniqueFilePrefix()}{suffixRule.getSuffix()}.jpg</code>
 *
 * @see S3CredentialsProvider
 * @see S3Credentials
 */
public class UploadManager {

    private static final String S3_URL_FORMAT = "https://%s.s3.amazonaws.com";
    private static final String DEFAULT_ACL = "public-read";
    private static final int DEFAULT_SUCCESS_STATUS = 201;
    private static final int UPLOAD_TIMEOUT_MS = 60000;

    /**
     * A convenience implementation of {@link SuffixRule} that will return a numerical suffix based
     * on the given file's index.
     */
    public static final SuffixRule SUFFIX_INCREMENTAL = new SuffixRule() {
        public String getSuffix(Uri uri, int index) {
            return ""+index;
        }
    };

    /**
     * A convenience implementation of {@link IndexedSuffixRule} that uses a suffix array containing
     * common dimension descriptors: "original", "large", "medium", and "small" in that order.
     */
    public static final SuffixRule SUFFIX_DIMENSIONS =
        new IndexedSuffixRule(new String[]{"original", "large", "medium", "small"});

    private Context context;
    private S3CredentialsProvider credentialsProvider;

    /**
     * Creates a new UploadManager instance with the given {@link S3CredentialsProvider} to
     * configure and authenticate upload requests.
     *
     * @param context The current {@link Context}
     * @param credentialsProvider An {@link S3CredentialsProvider} that will be used for
     *                            authentication and configuration for each upload request made
     *                            through this instance
     */
    public UploadManager(Context context, S3CredentialsProvider credentialsProvider) {
        this.context = context;
        this.credentialsProvider = credentialsProvider;
    }

    /**
     * Uploads an array of images to S3 in the background in series.
     *
     * @param imageUris An array {@link Uri}s representing the images to be uploaded
     * @param listener An {@link UploadListener} that will be notified of upload completion, error,
     *                 and progress events
     */
    public void uploadImages(Uri[] imageUris, UploadListener listener) {
        UploadTask uploadTask = new UploadTask(context, credentialsProvider, listener);
        uploadTask.execute(imageUris);
    }

    /**
     * Uploads an array of images to S3 in the background in series using the given
     * {@link SuffixRule} to configure the uploaded S3 keys.
     *
     * @param imageUris An array {@link Uri}s representing the images to be uploaded
     * @param suffixRule A {@link SuffixRule} that will be used to configure the uploaded S3 key for
     *                   each file
     * @param listener An {@link UploadListener} that will be notified of upload completion, error,
     *                 and progress events
     */
    public void uploadImages(Uri[] imageUris, SuffixRule suffixRule, UploadListener listener) {
        UploadTask uploadTask = new UploadTask(context, credentialsProvider, listener);
        uploadTask.suffixRule = suffixRule;
        uploadTask.execute(imageUris);
    }

    /**
     * Uploads an array of images to S3 in the background in series using the given
     * {@link SuffixRule} to configure the uploaded S3 keys with the ACL parameter (private/public).
     *
     * @param imageUris An array {@link Uri}s representing the images to be uploaded
     * @param suffixRule A {@link SuffixRule} that will be used to configure the uploaded S3 key for
     *                   each file
     * @param acl A string {@link String}s representing the ACL
     * @param listener An {@link UploadListener} that will be notified of upload completion, error,
     *                 and progress events
     */
    public void uploadImages(Uri[] imageUris, SuffixRule suffixRule, String acl, UploadListener listener) {
        UploadTask uploadTask = new UploadTask(context, credentialsProvider, listener);
        uploadTask.suffixRule = suffixRule;
        uploadTask.acl = acl;
        uploadTask.execute(imageUris);
    }

    /**
     * Uploads a media object to S3 in the background.
     *
     * @param mediaUri A {@link Uri} representing the media to be uploaded
     * @param listener An {@link UploadListener} that will be notified of upload completion, error,
     *                 and progress events
     */
    public void upload(Uri mediaUri, UploadListener listener) {
        UploadTask uploadTask = new UploadTask(context, credentialsProvider, listener);
        uploadTask.execute(mediaUri);
    }

    /**
     * Uploads a media object to S3 in the background using the given {@link SuffixRule} to configure the
     * uploaded S3 key.
     *
     * @param mediaUri A {@link Uri} representing the media to be uploaded
     * @param suffixRule A {@link SuffixRule} that will be used to configure the uploaded S3 key for
     *                   this file
     * @param listener An {@link UploadListener} that will be notified of upload completion, error,
     *                 and progress events
     */
    public void upload(Uri mediaUri, SuffixRule suffixRule, UploadListener listener) {
        UploadTask uploadTask = new UploadTask(context, credentialsProvider, listener);
        uploadTask.suffixRule = suffixRule;
        uploadTask.execute(mediaUri);
    }

    /**
     * Uploads a media object to S3 in the background using the given {@link SuffixRule} to configure the
     * uploaded S3 key with the ACL parameter (private/public)
     *
     * @param mediaUri A {@link Uri} representing the media to be uploaded
     * @param suffixRule A {@link SuffixRule} that will be used to configure the uploaded S3 key for
     *                   this file
     * @param acl A string {@link String}s representing the ACL
     * @param listener An {@link UploadListener} that will be notified of upload completion, error,
     *                 and progress events
     */
    public void upload(Uri mediaUri, SuffixRule suffixRule, String acl, UploadListener listener) {
        UploadTask uploadTask = new UploadTask(context, credentialsProvider, listener);
        uploadTask.suffixRule = suffixRule;
        uploadTask.acl = acl;
        uploadTask.execute(mediaUri);
    }


    /**
     * Implementation of {@link AsyncTask} that uploads an arbitrary number of {@link Uri}s to
     * S3 in series. The configuration specifying how the file should be uploaded (bucket name,
     * key, authentication, etc) is determined by an {@link S3CredentialsProvider} passed in to the
     * task.
     *
     * <p>
     * By default, the task uses the {@link UploadManager#SUFFIX_INCREMENTAL} instance as a
     * {@link SuffixRule} for modifying the S3 keys for successive uploads. This can be configured
     * with a different {@link SuffixRule} if custom naming conventions are required.
     * </p>
     *
     * <p>
     * If a single upload fails, the task will be cancelled and the remaining files will not be
     * uploaded.
     * </p>
     */
    private static class UploadTask extends AsyncTask<Uri, Integer, String[]> {
        private WeakReference<Context> context;
        private S3CredentialsProvider credentialsProvider;
        private UploadListener listener;
        private SuffixRule suffixRule = SUFFIX_INCREMENTAL;
        private String acl = DEFAULT_ACL;

        /**
         * Creates a new UploadTask instance. The instance will use {@code credentialsProvider}
         * for configuring it's upload parameters.
         *
         * @param context The current {@link Context}
         * @param credentialsProvider An {@link S3CredentialsProvider} that will provide
         *                            authentication and configuration information for each upload
         * @param listener A {@link UploadListener} to be notified of completion, error, and
         *                 progress events
         */
        public UploadTask(Context context, S3CredentialsProvider credentialsProvider, UploadListener listener) {
            this.context = new WeakReference<>(context);
            this.credentialsProvider = credentialsProvider;
            this.listener = listener;
        }

        @Override
        protected String[] doInBackground(Uri... uris) {
            S3Credentials credentials = credentialsProvider.getCredentials();
            if (credentials == null) {
                publishFailure(new IOException("Failed retrieving S3 credentials from provider"), 0);
                cancel(true);
                return null;
            }

            final String[] result = new String[uris.length];

            for (int i = 0; i < uris.length; i++) {
                if (isCancelled()) {
                    break;
                }

                Uri uri = uris[i];
                if (uri == null) {
                    publishFailure(new IllegalArgumentException("Uri cannot be null"), i);
                    cancel(true);
                    break;
                }

                Context ctx = context.get();
                if (ctx == null) {
                    publishFailure(new IllegalStateException("Context is dead"), i);
                    cancel(true);
                    break;
                }

                InputStream in = null;
                try {
                    in = ctx.getContentResolver().openInputStream(uri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    publishFailure(e, i);
                    cancel(true);
                    break;
                }

                String extension;
                if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                    String mimeType = ctx.getContentResolver().getType(uri);
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                } else {
                    extension = MimeTypeMap.getFileExtensionFromUrl(String.valueOf(Uri.fromFile(new File(uri.getPath()))));
                }

                if (in != null) {
                    final String key = credentials.getUniqueFilePrefix()+suffixRule.getSuffix(uri, i)+"."+extension;
                    final int index = i;
                    final String url = String.format(S3_URL_FORMAT, credentials.getBucket());
                    File file = new File(uri.getPath());
                    MultipartBody.Part filePart = MultipartBody.Part
                            .createFormData("file", file.getName(), RequestBody.create(MediaType.parse(credentials.getContentType()), file));
                    OkHttpClient okHttpClient = new OkHttpClient.Builder()
                            .connectTimeout(1, TimeUnit.MINUTES)
                            .writeTimeout(5, TimeUnit.MINUTES)
                            .readTimeout(3, TimeUnit.MINUTES)
                            .build();
                    S3Service service = new Retrofit.Builder()
                            .baseUrl(url)
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(okHttpClient)
                            .build()
                            .create(S3Service.class);
                    Call<ResponseBody> request = service.uploadFile(
                        RequestBody.create(MultipartBody.FORM, key),
                        RequestBody.create(MultipartBody.FORM, credentials.getAWSAccessKeyId()),
                        RequestBody.create(MultipartBody.FORM, credentials.getPolicy()),
                        RequestBody.create(MultipartBody.FORM, credentials.getSignature()),
                        RequestBody.create(MultipartBody.FORM, DEFAULT_SUCCESS_STATUS + ""),
                        RequestBody.create(MultipartBody.FORM, acl),
                        RequestBody.create(MultipartBody.FORM, credentials.getContentType()),
                        filePart
                    );
                    try {
                        Response<ResponseBody> response = request.execute();
                        if (response.isSuccessful()) {
                            result[index] = url + "/" + key;
                            publishProgress((int) (index /(float) result.length * 100));
                        } else {
                            publishFailure(new Exception(), index);
                            cancel(true);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        publishFailure(e, index);
                        cancel(true);
                    }
                }
            }

            return result;
        }

        private void publishFailure(final Throwable error, final int fileIndex) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onUploadFailed(error, fileIndex);
                    }
                }
            });
        }

        @Override
        protected void onPostExecute(String[] urls) {
            if (listener != null) {
                listener.onUploadComplete(urls);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (listener != null && values.length > 0) {
                listener.onProgress(values[0]);
            }
        }
    }


    /**
     * A listener interface to receive completion, error, and progress events during an upload
     * request
     */
    public interface UploadListener {
        /**
         * <p>
         * This method is invoked whenever an individual file has finished uploading. The progress
         * is an integer representation from 1 - 100 of how many files have been uploaded using the
         * following formula:
         * </p>
         *
         * <pre>
         * <code>progress = numberOfFilesUploaded / totalNumberOfFiles * 100;</code>
         * </pre>
         *
         * <p>
         * If only one file is being uploaded, this will only be called once with a
         * {@code progress} of 100.
         * </p>
         *
         * @param progress The completion progress of the current upload request, as an integer in
         *                 the range 1 - 100
         */
        void onProgress(int progress);

        /**
         * This method is invoked once all files in the current request have been successfully
         * uploaded. This will be called immediately after the last call to
         * {@link UploadListener#onProgress(int)}
         *
         * @param urls A String array containing the S3 urls for each uploaded file, in the same
         *             order that the files were provided to the UploadManager
         */
        void onUploadComplete(String[] urls);

        /**
         * This method is invoked when a file upload fails
         *
         * @param error A {@link Throwable} provided by the networking library indicating why the
         *              upload failed
         * @param failureIndex The index of the file that failed to upload
         */
        void onUploadFailed(Throwable error, int failureIndex);
    }

    /**
     * An interface for generating a string suffix to be appended to an uploaded file name given a
     * {@link Uri} for the original file and an index for the file's position in the current
     * upload request queue. This is helpful when uploading a list of files in a single request with
     * different S3 keys for each one.
     */
    public interface SuffixRule {
        /**
         * Returns a string suffix to be appended the S3 key for the given file.
         *
         * @param uri A {@link Uri} representing the file being uploaded
         * @param index The index of the file in the current upload request queue
         * @return A String to appended to be appended to the S3 key for the given file
         */
        String getSuffix(Uri uri, int index);
    }

    /**
     * An implementation of {@link SuffixRule} that maps an array of suffixes to the files being
     * uploaded.
     */
    public static class IndexedSuffixRule implements SuffixRule {
        private String[] suffixes;

        /**
         * Creates a new instance of this rule with the given array of suffixes.
         *
         * @param suffixes A String array to use when mapping suffixes to Uris
         */
        public IndexedSuffixRule(String[] suffixes) {
            this.suffixes = suffixes;
        }

        /**
         * Returns a suffix from the internal suffix area using the given index. If {@code index} is
         * greater than the length of the suffix array, it will be "wrapped" to the beginning of the
         * array such that a suffix is always returned.
         */
        @Override
        public String getSuffix(Uri uri, int index) {
            return suffixes[index % suffixes.length];
        }
    }
}
