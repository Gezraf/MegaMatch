//package com.project.megamatch;
//
///*
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.content.FileProvider;
//
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.net.Uri;
//import android.os.Bundle;
//import android.os.Environment;
//import android.provider.MediaStore;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.ImageView;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import com.bumptech.glide.Glide;
//import com.project.megamatch.utils.SupabaseStorageManager;
//
//import java.io.File;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.Locale;
//
//public class FileUploadActivity extends AppCompatActivity {
//    private static final String TAG = "FileUploadActivity";
//
//    private Button btnTakePhoto, btnChoosePhoto, btnUploadFile;
//    private ImageView imagePreview;
//    private ProgressBar progressBar;
//    private TextView tvStatus;
//
//    private Uri currentPhotoUri;
//    private String currentPhotoPath;
//
//    private SharedPreferences sharedPreferences;
//    private String schoolId;
//    private String username;
//
//    private final ActivityResultLauncher<Intent> takePictureLauncher = registerForActivityResult(
//            new ActivityResultContracts.StartActivityForResult(),
//            result -> {
//                if (result.getResultCode() == RESULT_OK) {
//                    displayImage(currentPhotoUri);
//                }
//            });
//
//    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
//            new ActivityResultContracts.GetContent(),
//            uri -> {
//                if (uri != null) {
//                    currentPhotoUri = uri;
//                    displayImage(uri);
//                }
//            });
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_file_upload);
//
//        // Initialize views
//        btnTakePhoto = findViewById(R.id.btnTakePhoto);
//        btnChoosePhoto = findViewById(R.id.btnChoosePhoto);
//        btnUploadFile = findViewById(R.id.btnUploadFile);
//        imagePreview = findViewById(R.id.imagePreview);
//        progressBar = findViewById(R.id.progressBar);
//        tvStatus = findViewById(R.id.tvStatus);
//
//        // Get user info from SharedPreferences
//        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
//        schoolId = sharedPreferences.getString("loggedInSchoolId", "");
//        username = sharedPreferences.getString("loggedInUsername", "");
//
//        // Set button click listeners
//        btnTakePhoto.setOnClickListener(v -> takePhoto());
//        btnChoosePhoto.setOnClickListener(v -> choosePhotoFromGallery());
//        btnUploadFile.setOnClickListener(v -> uploadCurrentPhoto());
//
//        // Initially disable upload button
//        btnUploadFile.setEnabled(false);
//    }
//
//    private void takePhoto() {
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
//            // Create the File where the photo should go
//            File photoFile = null;
//            try {
//                photoFile = createImageFile();
//            } catch (IOException ex) {
//                Log.e(TAG, "Error creating image file", ex);
//                Toast.makeText(this, "שגיאה ביצירת קובץ תמונה", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            // Continue only if the File was successfully created
//            if (photoFile != null) {
//                currentPhotoUri = FileProvider.getUriForFile(this,
//                        "com.project.megamatch.fileprovider",
//                        photoFile);
//                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
//                takePictureLauncher.launch(takePictureIntent);
//            }
//        } else {
//            Toast.makeText(this, "אין אפליקציית מצלמה במכשיר זה", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private File createImageFile() throws IOException {
//        // Create an image file name
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
//        String imageFileName = "JPEG_" + timeStamp + "_";
//        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
//        File image = File.createTempFile(
//                imageFileName,  /* prefix */
//                ".jpg",         /* suffix */
//                storageDir      /* directory */
//        );
//
//        // Save a file path for use with ACTION_VIEW intents
//        currentPhotoPath = image.getAbsolutePath();
//        return image;
//    }
//
//    private void choosePhotoFromGallery() {
//        pickImageLauncher.launch("image/*");
//    }
//
//    private void displayImage(Uri imageUri) {
//        Glide.with(this)
//                .load(imageUri)
//                .centerCrop()
//                .into(imagePreview);
//
//        btnUploadFile.setEnabled(true);
//    }
//
//    private void uploadCurrentPhoto() {
//        if (currentPhotoUri == null) {
//            Toast.makeText(this, "יש לבחור תמונה תחילה", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Show progress
//        progressBar.setVisibility(View.VISIBLE);
//        tvStatus.setText("מעלה קובץ...");
//        btnUploadFile.setEnabled(false);
//
//        // Generate a unique path for the file in Supabase Storage
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
//        String storagePath = "schools/" + schoolId + "/uploads/" + username + "_" + timeStamp + ".jpg";
//
//        // Upload file to Supabase Storage
//        SupabaseStorageManager.getInstance().uploadFile(
//                this,
//                currentPhotoUri,
//                storagePath,
//                new SupabaseStorageManager.FileUploadListener() {
//                    @Override
//                    public void onSuccess(String publicUrl) {
//                        // Update UI on the main thread
//                        runOnUiThread(() -> {
//                            progressBar.setVisibility(View.GONE);
//                            tvStatus.setText("הקובץ הועלה בהצלחה");
//                            Toast.makeText(FileUploadActivity.this, "הקובץ הועלה בהצלחה", Toast.LENGTH_SHORT).show();
//
//                            // Save the URL to Firestore (optional)
//                            saveFileUrlToFirestore(publicUrl, storagePath);
//
//                            btnUploadFile.setEnabled(true);
//                        });
//                    }
//
//                    @Override
//                    public void onFailure(String errorMessage) {
//                        // Update UI on the main thread
//                        runOnUiThread(() -> {
//                            progressBar.setVisibility(View.GONE);
//                            tvStatus.setText("שגיאה בהעלאת הקובץ: " + errorMessage);
//                            Toast.makeText(FileUploadActivity.this, "שגיאה בהעלאת הקובץ", Toast.LENGTH_SHORT).show();
//                            btnUploadFile.setEnabled(true);
//                        });
//                    }
//                });
//    }
//
//    private void saveFileUrlToFirestore(String fileUrl, String storagePath) {
//        // Optional: Save the file URL to Firestore for future reference
//        // This is useful if you want to keep track of all uploaded files
//
//        if (schoolId.isEmpty() || username.isEmpty()) {
//            Log.e(TAG, "Cannot save file URL: schoolId or username is empty");
//            return;
//        }
//
//        // Create a data object to save
//        java.util.Map<String, Object> fileData = new java.util.HashMap<>();
//        fileData.put("url", fileUrl);
//        fileData.put("path", storagePath);
//        fileData.put("uploadedBy", username);
//        fileData.put("uploadedAt", new java.util.Date());
//        fileData.put("fileType", "image/jpeg");
//
//        // Save to Firestore
//        com.google.firebase.firestore.FirebaseFirestore.getInstance()
//                .collection("schools").document(schoolId)
//                .collection("uploads").add(fileData)
//                .addOnSuccessListener(documentReference -> {
//                    Log.d(TAG, "File URL saved to Firestore with ID: " + documentReference.getId());
//                })
//                .addOnFailureListener(e -> {
//                    Log.e(TAG, "Error saving file URL to Firestore", e);
//                });
//    }
//}
//*/
//
//// Temporarily disabled FileUploadActivity
//public class FileUploadActivity {
//    // Empty stub class to maintain references
//}