//package com.project.megamatch.utils;
//
///*
//import android.content.Context;
//import android.net.Uri;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.util.concurrent.TimeUnit;
//
//import okhttp3.Call;
//import okhttp3.Callback;
//import okhttp3.MediaType;
//import okhttp3.MultipartBody;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//
///**
// * Utility class to handle Supabase storage operations
// */
//public class SupabaseStorageManager {
//    private static final String TAG = "SupabaseStorageManager";
//
//    // Get configuration from SupabaseConfig
//    private static final String SUPABASE_URL = SupabaseConfig.SUPABASE_URL;
//    private static final String SUPABASE_API_KEY = SupabaseConfig.SUPABASE_API_KEY;
//    private static final String BUCKET_NAME = SupabaseConfig.BUCKET_NAME;
//
//    // OkHttpClient for API calls
//    private final OkHttpClient client;
//
//    // Singleton instance
//    private static SupabaseStorageManager instance;
//
//    /**
//     * Get singleton instance of SupabaseStorageManager
//     * @return The singleton instance
//     */
//    public static synchronized SupabaseStorageManager getInstance() {
//        if (instance == null) {
//            instance = new SupabaseStorageManager();
//        }
//        return instance;
//    }
//
//    /**
//     * Private constructor to enforce singleton pattern
//     */
//    private SupabaseStorageManager() {
//        client = new OkHttpClient.Builder()
//                .connectTimeout(30, TimeUnit.SECONDS)
//                .readTimeout(30, TimeUnit.SECONDS)
//                .writeTimeout(30, TimeUnit.SECONDS)
//                .build();
//    }
//
//    /**
//     * Upload a file to Supabase Storage
//     * @param context Application context
//     * @param fileUri URI of the file to upload
//     * @param path Path in the storage bucket (e.g., "schools/123456/photos/image.jpg")
//     * @param listener Callback for upload result
//     */
//    public void uploadFile(Context context, Uri fileUri, String path, FileUploadListener listener) {
//        try {
//            // Get file from URI
//            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
//            if (inputStream == null) {
//                listener.onFailure("Cannot read file from URI");
//                return;
//            }
//
//            // Create temp file
//            File tempFile = File.createTempFile("upload", null, context.getCacheDir());
//            copyInputStreamToFile(inputStream, tempFile);
//
//            // Determine MIME type
//            String mimeType = context.getContentResolver().getType(fileUri);
//            if (mimeType == null) {
//                mimeType = "application/octet-stream";
//            }
//
//            // Create request body
//            RequestBody requestBody = new MultipartBody.Builder()
//                    .setType(MultipartBody.FORM)
//                    .addFormDataPart("file", path.substring(path.lastIndexOf('/') + 1),
//                            RequestBody.create(tempFile, MediaType.parse(mimeType)))
//                    .build();
//
//            // Create request
//            Request request = new Request.Builder()
//                    .url(SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + path)
//                    .post(requestBody)
//                    .addHeader("apikey", SUPABASE_API_KEY)
//                    .addHeader("Authorization", "Bearer " + SUPABASE_API_KEY)
//                    .build();
//
//            // Execute request asynchronously
//            client.newCall(request).enqueue(new Callback() {
//                @Override
//                public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                    Log.e(TAG, "Upload failed", e);
//                    tempFile.delete();
//                    listener.onFailure("Upload failed: " + e.getMessage());
//                }
//
//                @Override
//                public void onResponse(@NonNull Call call, @NonNull Response response) {
//                    tempFile.delete();
//                    if (response.isSuccessful()) {
//                        Log.d(TAG, "Upload successful");
//                        String publicUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + path;
//                        listener.onSuccess(publicUrl);
//                    } else {
//                        try {
//                            String errorBody = response.body() != null ? response.body().string() : "Unknown error";
//                            Log.e(TAG, "Upload failed: " + errorBody);
//                            listener.onFailure("Upload failed: " + errorBody);
//                        } catch (IOException e) {
//                            Log.e(TAG, "Error reading error response", e);
//                            listener.onFailure("Upload failed: " + e.getMessage());
//                        }
//                    }
//                }
//            });
//        } catch (IOException e) {
//            Log.e(TAG, "Error preparing upload", e);
//            listener.onFailure("Error preparing upload: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Download a file from Supabase Storage
//     * @param context Application context
//     * @param path Path in the storage bucket (e.g., "schools/123456/photos/image.jpg")
//     * @param destinationFile Local file to save the downloaded content
//     * @param listener Callback for download result
//     */
//    public void downloadFile(Context context, String path, File destinationFile, FileDownloadListener listener) {
//        // Create request
//        Request request = new Request.Builder()
//                .url(SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + path)
//                .get()
//                .addHeader("apikey", SUPABASE_API_KEY)
//                .addHeader("Authorization", "Bearer " + SUPABASE_API_KEY)
//                .build();
//
//        // Execute request asynchronously
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                Log.e(TAG, "Download failed", e);
//                listener.onFailure("Download failed: " + e.getMessage());
//            }
//
//            @Override
//            public void onResponse(@NonNull Call call, @NonNull Response response) {
//                if (response.isSuccessful() && response.body() != null) {
//                    try (InputStream inputStream = response.body().byteStream();
//                         OutputStream outputStream = new FileOutputStream(destinationFile)) {
//                        byte[] buffer = new byte[4096];
//                        int bytesRead;
//                        while ((bytesRead = inputStream.read(buffer)) != -1) {
//                            outputStream.write(buffer, 0, bytesRead);
//                        }
//                        outputStream.flush();
//                        Log.d(TAG, "Download successful");
//                        listener.onSuccess(destinationFile);
//                    } catch (IOException e) {
//                        Log.e(TAG, "Error saving downloaded file", e);
//                        listener.onFailure("Error saving downloaded file: " + e.getMessage());
//                    }
//                } else {
//                    try {
//                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
//                        Log.e(TAG, "Download failed: " + errorBody);
//                        listener.onFailure("Download failed: " + errorBody);
//                    } catch (IOException e) {
//                        Log.e(TAG, "Error reading error response", e);
//                        listener.onFailure("Download failed: " + e.getMessage());
//                    }
//                }
//            }
//        });
//    }
//
//    /**
//     * Get public URL for a file in Supabase Storage
//     * @param path Path in the storage bucket (e.g., "schools/123456/photos/image.jpg")
//     * @return Public URL of the file
//     */
//    public String getPublicUrl(String path) {
//        return SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + path;
//    }
//
//    /**
//     * Delete a file from Supabase Storage
//     * @param path Path in the storage bucket (e.g., "schools/123456/photos/image.jpg")
//     * @param listener Callback for deletion result
//     */
//    public void deleteFile(String path, FileOperationListener listener) {
//        // Create request
//        Request request = new Request.Builder()
//                .url(SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + path)
//                .delete()
//                .addHeader("apikey", SUPABASE_API_KEY)
//                .addHeader("Authorization", "Bearer " + SUPABASE_API_KEY)
//                .build();
//
//        // Execute request asynchronously
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                Log.e(TAG, "Delete failed", e);
//                listener.onFailure("Delete failed: " + e.getMessage());
//            }
//
//            @Override
//            public void onResponse(@NonNull Call call, @NonNull Response response) {
//                if (response.isSuccessful()) {
//                    Log.d(TAG, "Delete successful");
//                    listener.onSuccess();
//                } else {
//                    try {
//                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
//                        Log.e(TAG, "Delete failed: " + errorBody);
//                        listener.onFailure("Delete failed: " + errorBody);
//                    } catch (IOException e) {
//                        Log.e(TAG, "Error reading error response", e);
//                        listener.onFailure("Delete failed: " + e.getMessage());
//                    }
//                }
//            }
//        });
//    }
//
//    /**
//     * Copy input stream to file
//     * @param inputStream Input stream to copy from
//     * @param outputFile Output file to copy to
//     * @throws IOException If an I/O error occurs
//     */
//    private void copyInputStreamToFile(InputStream inputStream, File outputFile) throws IOException {
//        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = inputStream.read(buffer)) != -1) {
//                outputStream.write(buffer, 0, bytesRead);
//            }
//            outputStream.flush();
//        }
//    }
//
//    /**
//     * Interface for file upload callbacks
//     */
//    public interface FileUploadListener {
//        void onSuccess(String publicUrl);
//        void onFailure(String errorMessage);
//    }
//
//    /**
//     * Interface for file download callbacks
//     */
//    public interface FileDownloadListener {
//        void onSuccess(File downloadedFile);
//        void onFailure(String errorMessage);
//    }
//
//    /**
//     * Interface for general file operation callbacks
//     */
//    public interface FileOperationListener {
//        void onSuccess();
//        void onFailure(String errorMessage);
//    }
//}
//*/
//
//// Temporarily disabled Supabase Storage Manager
//public class SupabaseStorageManager {
//    // Empty stub to maintain class references
//    private static SupabaseStorageManager instance;
//
//    public static synchronized SupabaseStorageManager getInstance() {
//        if (instance == null) {
//            instance = new SupabaseStorageManager();
//        }
//        return instance;
//    }
//}