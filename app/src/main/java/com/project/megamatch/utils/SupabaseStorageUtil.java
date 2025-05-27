package com.project.megamatch.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.project.megamatch.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Utility class for handling Supabase Storage operations
 */
public class SupabaseStorageUtil {
    private static final String TAG = "SupabaseStorageUtil";
    
    // Constants for folder structure
    private static final String SCHOOLS_FOLDER = "schools";
    private static final String RAKAZIM_FOLDER = "rakazim";
    
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    /**
     * Upload an image to Supabase Storage
     * 
     * @param context Context for accessing content resolver
     * @param imageUri URI of the image to upload
     * @param schoolId School ID for folder path
     * @param username Username for folder path
     * @param callback Callback to handle the upload result
     */
    public static void uploadImage(Context context, Uri imageUri, String schoolId, 
                                  String username, UploadCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting image upload process");
                
                // Get Supabase configuration from resources
                String supabaseUrl = context.getString(R.string.supabase_url);
                String supabaseApiKey = context.getString(R.string.supabase_api_key);
                String bucketName = context.getString(R.string.supabase_bucket_name);
                
                Log.d(TAG, "Using Supabase URL: " + supabaseUrl);
                Log.d(TAG, "Using bucket: " + bucketName);
                Log.d(TAG, "API Key (first 10 chars): " + 
                      (supabaseApiKey.length() > 10 ? supabaseApiKey.substring(0, 10) + "..." : "Invalid key"));
                
                // First check bucket permissions
                if (!checkBucketPermission(supabaseUrl, supabaseApiKey, bucketName)) {
                    if (callback != null) {
                        callback.onError("No permission to access storage bucket. Please verify Supabase configuration.");
                    }
                    return;
                }
                
                // Create folder path - ensure path is properly formatted
                String folderPath = SCHOOLS_FOLDER + "/" + schoolId + "/" + RAKAZIM_FOLDER + "/" + username;
                
                // Generate a unique filename for the image
                String filename = UUID.randomUUID().toString() + ".jpg";
                
                // Create the complete path including filename
                String fullPath = folderPath + "/" + filename;
                
                // Convert Uri to File
                File imageFile = uriToFile(context, imageUri);
                if (imageFile == null || !imageFile.exists()) {
                    Log.e(TAG, "Failed to create image file from URI");
                    if (callback != null) {
                        callback.onError("Failed to process image file");
                    }
                    return;
                }
                
                Log.d(TAG, "Image file created: " + imageFile.length() + " bytes");
                
                // Try using the POST upload endpoint instead of PUT
                String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName;
                
                // Create multipart request for the POST endpoint
                MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename,
                        RequestBody.create(imageFile, MediaType.parse("image/jpeg")))
                    .addFormDataPart("bucket", bucketName)
                    .addFormDataPart("path", folderPath);
                
                RequestBody requestBody = multipartBuilder.build();
                
                // Build request
                Request request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("apikey", supabaseApiKey)
                    .addHeader("Authorization", "Bearer " + supabaseApiKey)
                    .post(requestBody)
                    .build();
                
                Log.d(TAG, "Executing request to " + uploadUrl);
                
                // Execute request
                Response response = client.newCall(request).execute();
                
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Upload successful: " + responseBody);
                    
                    // Generate public URL for the image
                    String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fullPath;
                    
                    Log.d(TAG, "Generated public URL: " + publicUrl);
                    
                    // Pass the result back on the main thread
                    final String finalPublicUrl = publicUrl;
                    if (callback != null) {
                        callback.onSuccess(finalPublicUrl);
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Upload failed with code " + response.code() + ": " + errorBody);
                    
                    // Try using the direct image endpoint as fallback
                    useDirectUploadFallback(context, imageUri, schoolId, username, callback, 
                                          supabaseUrl, supabaseApiKey, bucketName, filename, imageFile);
                }
                
                // Clean up temporary file
                if (imageFile != null && imageFile.exists()) {
                    if (imageFile.delete()) {
                        Log.d(TAG, "Temp file deleted successfully");
                    } else {
                        Log.w(TAG, "Failed to delete temp file");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error uploading image", e);
                if (callback != null) {
                    callback.onError("Error uploading image: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Check if we have permission to access the bucket
     */
    private static boolean checkBucketPermission(String supabaseUrl, String apiKey, String bucketName) {
        try {
            // We'll try listing buckets to check permissions
            String bucketListUrl = supabaseUrl + "/storage/v1/bucket";
            
            Request request = new Request.Builder()
                .url(bucketListUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();
                
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "Successfully fetched bucket list, we have permissions");
                return true;
            } else {
                Log.e(TAG, "Failed to list buckets: " + response.code());
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "Error details: " + errorBody);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking bucket permissions", e);
            return false;
        }
    }
    
    /**
     * Fallback upload method using direct PUT request
     */
    private static void useDirectUploadFallback(
            Context context, Uri imageUri, String schoolId, String username, 
            UploadCallback callback, String supabaseUrl, String supabaseApiKey, 
            String bucketName, String filename, File imageFile) {
        
        try {
            Log.d(TAG, "Trying fallback upload method...");
            
            // Create folder path
            String folderPath = SCHOOLS_FOLDER + "/" + schoolId + "/" + RAKAZIM_FOLDER + "/" + username;
            String fullPath = folderPath + "/" + filename;
            
            // Build direct Supabase Storage URL
            String directUploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fullPath;
            
            Log.d(TAG, "Fallback upload URL: " + directUploadUrl);
            
            // Create request body
            RequestBody requestBody = RequestBody.create(imageFile, MediaType.parse("image/jpeg"));
            
            // Build direct PUT request with minimal headers
            Request request = new Request.Builder()
                    .url(directUploadUrl)
                    .addHeader("apikey", supabaseApiKey)
                    .addHeader("Authorization", "Bearer " + supabaseApiKey)
                    .put(requestBody)
                    .build();
            
            // Execute request
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Fallback upload successful: " + responseBody);
                
                // Generate public URL for the image
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fullPath;
                
                Log.d(TAG, "Generated public URL: " + publicUrl);
                
                if (callback != null) {
                    callback.onSuccess(publicUrl);
                }
            } else {
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "Fallback upload failed with code " + response.code() + ": " + errorBody);
                
                // Provide a user-friendly error message
                String errorMessage;
                switch (response.code()) {
                    case 401:
                        errorMessage = "Authentication failed. Check your API key.";
                        break;
                    case 403:
                        errorMessage = "Permission denied. Check storage bucket policies.";
                        break;
                    case 404:
                        errorMessage = "Storage bucket or path not found.";
                        break;
                    case 413:
                        errorMessage = "Image file too large.";
                        break;
                    default:
                        errorMessage = "Upload failed: " + response.code() + " - " + errorBody;
                }
                
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in fallback upload", e);
            if (callback != null) {
                callback.onError("Error uploading image: " + e.getMessage());
            }
        }
    }
    
    /**
     * Convert a content URI to a File
     */
    private static File uriToFile(Context context, Uri uri) throws IOException {
        // Create temp file
        File tempFile = File.createTempFile("upload", ".jpg", context.getCacheDir());
        
        // Copy data from URI to file
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            
            if (inputStream == null) {
                throw new IOException("Failed to open input stream");
            }
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
        
        return tempFile;
    }
    
    /**
     * Callback interface for image upload operations
     */
    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onError(String errorMessage);
    }
} 