package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.project.megamatch.megamaCreate.Megama;
import com.project.megamatch.utils.SupabaseStorageUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

public class MegamaAttachments extends AppCompatActivity {
    private static final String TAG = "MegamaAttachments";

    // UI components
    private TextView greetingText, megamaText;
    private Button backButton;
    private MaterialButton createMegamaButton, addUrlImageButton;
    private ImageButton expandImageSectionButton;
    private EditText imageUrlInput;
    private LinearLayout imageSection;
    private FrameLayout addImageButton;
    private RecyclerView selectedImagesRecyclerView;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseFirestore fireDB;

    // Data
    private String schoolId;
    private String username;
    private String megamaName;
    private String megamaDescription;
    private boolean requiresExam;
    private boolean requiresGradeAvg;
    private int requiredGradeAvg;
    private ArrayList<String> customConditions;
    private ArrayList<Uri> selectedImageUris = new ArrayList<>();
    private ArrayList<String> uploadedImageUrls = new ArrayList<>();

    // Image handling
    private Uri currentPhotoUri;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ImagesAdapter imagesAdapter;
    
    /**
     * Helper class to prevent rapid multiple clicks
     */
    private static class DebounceClickListener implements View.OnClickListener {
        private static final long DEBOUNCE_INTERVAL_MS = 800; // 800 milliseconds
        private final View.OnClickListener clickListener;
        private long lastClickTime = 0;
        
        DebounceClickListener(View.OnClickListener clickListener) {
            this.clickListener = clickListener;
        }
        
        @Override
        public void onClick(View v) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime > DEBOUNCE_INTERVAL_MS) {
                lastClickTime = currentTime;
                clickListener.onClick(v);
            } else {
                Log.d("DebounceClick", "Click ignored, too soon after previous click");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.megama_attachments);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.megamaAttachments), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase
        fireDB = FirebaseFirestore.getInstance();

        // Initialize UI components
        initializeViews();
        setupImagePickers();
        setupStorageAndDatabase();
        setupListeners();
        
        // Check if we're in update mode with existing images
        boolean isUpdate = getIntent().getBooleanExtra("isUpdate", false);
        if (isUpdate) {
            // Set button text to update
            createMegamaButton.setText("עדכון מגמה");
            
            // Load existing images
            ArrayList<String> imageUrls = getIntent().getStringArrayListExtra("imageUrls");
            if (imageUrls != null && !imageUrls.isEmpty()) {
                for (String imageUrl : imageUrls) {
                    uploadedImageUrls.add(imageUrl);
                }
                imagesAdapter.notifyDataSetChanged();
                updateImageCountText();
                
                // Automatically expand the image section when there are existing images
                imageSection.setVisibility(View.VISIBLE);
                expandImageSectionButton.setImageResource(android.R.drawable.arrow_up_float);
            }
        }
    }

    private void initializeViews() {
        greetingText = findViewById(R.id.greetingText);
        megamaText = findViewById(R.id.megamaText);
        backButton = findViewById(R.id.backButton);
        createMegamaButton = findViewById(R.id.createMegamaButton);
        expandImageSectionButton = findViewById(R.id.expandImageSectionButton);
        imageSection = findViewById(R.id.imageSection);
        addImageButton = findViewById(R.id.addImageButton);
        imageUrlInput = findViewById(R.id.imageUrlInput);
        addUrlImageButton = findViewById(R.id.addUrlImageButton);
        selectedImagesRecyclerView = findViewById(R.id.selectedImagesRecyclerView);
        progressBar = findViewById(R.id.progressBar);

        // Setup RecyclerView
        selectedImagesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        imagesAdapter = new ImagesAdapter(selectedImageUris);
        selectedImagesRecyclerView.setAdapter(imagesAdapter);
    }

    private void setupImagePickers() {
        // Image picker launcher (Gallery)
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            addImageToSelection(imageUri);
                        }
                    }
                });

        // Camera launcher
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && currentPhotoUri != null) {
                        addImageToSelection(currentPhotoUri);
                    }
                });
    }

    private void setupStorageAndDatabase() {
        // Get data from intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            schoolId = extras.getString("schoolId", "");
            username = extras.getString("username", "");
            megamaName = extras.getString("megamaName", "");
            megamaDescription = extras.getString("megamaDescription", "");
            requiresExam = extras.getBoolean("requiresExam", false);
            requiresGradeAvg = extras.getBoolean("requiresGradeAvg", false);
            requiredGradeAvg = extras.getInt("requiredGradeAvg", 0);
            customConditions = extras.getStringArrayList("customConditions");

            Log.d(TAG, "Initial data from intent - megamaName: " + megamaName);
            
            // Always load rakaz details to ensure we have the correct megama name
            loadRakazDetails();
        }
    }

    // Load rakaz details to get the correct megama name
    private void loadRakazDetails() {
        Log.d(TAG, "Loading rakaz details for username: " + username);
        fireDB.collection("schools").document(schoolId)
            .collection("rakazim").document(username)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // טעינת שם פרטי
                    String firstName = documentSnapshot.getString("firstName");
                    if (firstName != null && !firstName.isEmpty()) {
                        greetingText.setText("שלום " + firstName);
                    } else {
                        greetingText.setText("שלום " + username);
                    }

                    // Get the megama name from the rakaz document - this is the source of truth
                    String rakazMegamaName = documentSnapshot.getString("megama");
                    Log.d(TAG, "Rakaz document has megama name: " + rakazMegamaName);
                    
                    if (rakazMegamaName != null && !rakazMegamaName.isEmpty()) {
                        // Use the megama name from the rakaz document
                        megamaName = rakazMegamaName;
                        Log.d(TAG, "Setting megamaName to: " + megamaName);
                        
                        // Don't set the text yet, we'll do that in updateUIWithMegamaDetails
                        // after checking if the megama exists in Firestore
                        
                        // Fetch the megama details
                        fetchMegamaDataFromFirestore(megamaName);
                    } else {
                        megamaText.setText("יצירת מגמה חדשה!");
                        // Continue with UI update
                        updateUIWithMegamaDetails();
                    }
                } else {
                    Log.e(TAG, "Rakaz document does not exist for username: " + username);
                    greetingText.setText("שלום " + username);
                    megamaText.setText("יצירת מגמה חדשה!");
                    updateUIWithMegamaDetails();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading rakaz details: " + e.getMessage());
                greetingText.setText("שלום " + username);
                megamaText.setText("יצירת מגמה חדשה!");
                updateUIWithMegamaDetails();
            });
    }

    private void fetchMegamaDataFromFirestore(String megamaName) {
        Log.d(TAG, "Fetching megama data for: " + megamaName);
        
        if (megamaName == null || megamaName.isEmpty()) {
            Log.e(TAG, "Cannot fetch megama data: megamaName is null or empty");
            updateUIWithMegamaDetails();
            return;
        }
        
        fireDB.collection("schools").document(schoolId)
              .collection("megamot").document(megamaName)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      Log.d(TAG, "Successfully fetched megama document");
                      
                      // Get data from Firestore document
                      if (megamaDescription.isEmpty()) {
                          String description = documentSnapshot.getString("description");
                          if (description != null) {
                              megamaDescription = description;
                              Log.d(TAG, "Updated megamaDescription: " + megamaDescription);
                          }
                      }
                      
                      // Get requires exam value
                      Boolean examValue = documentSnapshot.getBoolean("requiresExam");
                      if (examValue != null) {
                          requiresExam = examValue;
                          Log.d(TAG, "Updated requiresExam: " + requiresExam);
                      }
                      
                      // Get requires grade avg value
                      Boolean gradeAvgValue = documentSnapshot.getBoolean("requiresGradeAvg");
                      if (gradeAvgValue != null) {
                          requiresGradeAvg = gradeAvgValue;
                          Log.d(TAG, "Updated requiresGradeAvg: " + requiresGradeAvg);
                      }
                      
                      if (requiredGradeAvg == 0 && requiresGradeAvg) {
                          Long requiredGradeAvgLong = documentSnapshot.getLong("requiredGradeAvg");
                          requiredGradeAvg = requiredGradeAvgLong != null ? requiredGradeAvgLong.intValue() : 0;
                          Log.d(TAG, "Updated requiredGradeAvg: " + requiredGradeAvg);
                      }
                      
                      if (customConditions == null || customConditions.isEmpty()) {
                          customConditions = (ArrayList<String>) documentSnapshot.get("customConditions");
                          Log.d(TAG, "Updated customConditions count: " + 
                                (customConditions != null ? customConditions.size() : 0));
                      }
                      
                      // Now get image URLs if they aren't already set
                      ArrayList<String> fetchedImageUrls = (ArrayList<String>) documentSnapshot.get("imageUrls");
                      if (fetchedImageUrls != null && !fetchedImageUrls.isEmpty() && uploadedImageUrls.isEmpty()) {
                          uploadedImageUrls.addAll(fetchedImageUrls);
                          imagesAdapter.notifyDataSetChanged();
                          updateImageCountText();
                          Log.d(TAG, "Updated uploadedImageUrls count: " + uploadedImageUrls.size());
                      }
                  } else {
                      Log.w(TAG, "Megama document does not exist for name: " + megamaName);
                  }
                  
                  // Update UI after fetching data
                  updateUIWithMegamaDetails();
              })
              .addOnFailureListener(e -> {
                  Log.e(TAG, "Error fetching megama data: " + e.getMessage());
                  // Continue with UI update even if fetch fails
                  updateUIWithMegamaDetails();
              });
    }

    private void updateUIWithMegamaDetails() {
        // Update the UI with the current megama details
        Log.d(TAG, "Updating UI with current megama details");
        
        // We need to check if the megama document actually exists in Firestore
        if (megamaName != null && !megamaName.isEmpty()) {
            fireDB.collection("schools").document(schoolId)
                  .collection("megamot").document(megamaName)
                  .get()
                  .addOnSuccessListener(documentSnapshot -> {
                      if (documentSnapshot.exists()) {
                          // If megama exists, set to "update"
                          createMegamaButton.setText("עדכון מגמה");
                          megamaText.setText("עדכון מגמת " + megamaName);
                      } else {
                          // If megama doesn't exist yet, set to "create"
                          createMegamaButton.setText("יצירת מגמה");
                          megamaText.setText("יצירת מגמת " + megamaName);
                      }
                  })
                  .addOnFailureListener(e -> {
                      // On failure, default to create
                      createMegamaButton.setText("יצירת מגמה");
                      megamaText.setText("יצירת מגמת " + megamaName);
                  });
        } else {
            createMegamaButton.setText("יצירת מגמה");
            megamaText.setText("יצירת מגמה חדשה!");
        }
        
        // If we have images, expand the image section
        if (!uploadedImageUrls.isEmpty()) {
            imageSection.setVisibility(View.VISIBLE);
            expandImageSectionButton.setImageResource(android.R.drawable.arrow_up_float);
            updateImageCountText();
        }
    }

    private void setupListeners() {
        // Back button
        backButton.setOnClickListener(v -> {
            goBackToMegamaCreate();
        });

        // Expand/collapse image section
        expandImageSectionButton.setOnClickListener(v -> {
            if (imageSection.getVisibility() == View.VISIBLE) {
                imageSection.setVisibility(View.GONE);
                expandImageSectionButton.setImageResource(android.R.drawable.arrow_down_float);
            } else {
                imageSection.setVisibility(View.VISIBLE);
                expandImageSectionButton.setImageResource(android.R.drawable.arrow_up_float);
            }
        });

        // Add image button (shows bottom sheet dialog)
        addImageButton.setOnClickListener(v -> {
            showImageSourceOptions();
        });

        // Add URL image button
        addUrlImageButton.setOnClickListener(v -> {
            String imageUrl = imageUrlInput.getText().toString().trim();
            if (!imageUrl.isEmpty()) {
                if (isValidImageUrl(imageUrl)) {
                    // Show loading indicator
                    Toast.makeText(this, "מוסיף תמונה מ-URL...", Toast.LENGTH_SHORT).show();
                    
                    // For URLs, we'll just add them directly to our list
                    // You might want to download and reupload them for consistency
                    uploadedImageUrls.add(imageUrl);
                    imagesAdapter.notifyDataSetChanged();
                    updateImageCountText();
                    
                    // Clear input
                    imageUrlInput.setText("");
                    Toast.makeText(this, "התמונה נוספה בהצלחה", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "נא להזין כתובת תקינה של תמונה", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "נא להזין כתובת לתמונה", Toast.LENGTH_SHORT).show();
            }
        });

        // Create megama button with debounce to prevent multiple clicks
        createMegamaButton.setOnClickListener(new DebounceClickListener(v -> createNewMegama()));
    }

    private void showImageSourceOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_image_picker, null);
        dialog.setContentView(bottomSheetView);

        View cameraOption = bottomSheetView.findViewById(R.id.camera_option);
        View galleryOption = bottomSheetView.findViewById(R.id.gallery_option);

        cameraOption.setOnClickListener(v -> {
            dialog.dismiss();
            openCamera();
        });

        galleryOption.setOnClickListener(v -> {
            dialog.dismiss();
            openGallery();
        });

        dialog.show();
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Error creating image file", ex);
                Toast.makeText(this, "שגיאה ביצירת קובץ תמונה", Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        "com.project.megamatch.fileprovider",
                        photoFile);
                takePictureLauncher.launch(currentPhotoUri);
            }
        } else {
            Toast.makeText(this, "אין אפליקציית מצלמה זמינה", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void addImageToSelection(Uri imageUri) {
        selectedImageUris.add(imageUri);
        imagesAdapter.notifyItemInserted(selectedImageUris.size() - 1);
        updateImageCountText();
        
        // Upload image to Supabase storage
        uploadImageToSupabase(imageUri);
    }
    
    /**
     * Upload an image to Supabase storage
     * @param imageUri The URI of the image to upload
     */
    private void uploadImageToSupabase(Uri imageUri) {
        if (imageUri == null) {
            Log.e(TAG, "Cannot upload null image URI");
            return;
        }
        
        // Show loading indicator
        Toast.makeText(this, "מעלה תמונה...", Toast.LENGTH_SHORT).show();
        
        // Upload image to Supabase using our utility
        SupabaseStorageUtil.uploadImage(
                this, 
                imageUri, 
                schoolId, 
                username, 
                new SupabaseStorageUtil.UploadCallback() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        // Run on UI thread
                        runOnUiThread(() -> {
                            Log.d(TAG, "Image uploaded successfully: " + imageUrl);
                            // Add the URL to our uploadedImageUrls list
                            uploadedImageUrls.add(imageUrl);
                            // Update the UI
                            imagesAdapter.notifyDataSetChanged();
                            updateImageCountText();
                            Toast.makeText(MegamaAttachments.this, "התמונה הועלתה בהצלחה", Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        // Run on UI thread
                        runOnUiThread(() -> {
                            Log.e(TAG, "Error uploading image: " + errorMessage);
                            Toast.makeText(MegamaAttachments.this, 
                                    "שגיאה בהעלאת תמונה: " + errorMessage, 
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private boolean isValidImageUrl(String url) {
        // Basic validation for image URLs
        return url.matches(".*\\.(jpeg|jpg|png|gif|bmp)$") || 
               url.startsWith("http") || 
               url.startsWith("https");
    }

    private void goBackToMegamaCreate() {
        Intent intent = new Intent();
        intent.putExtra("shouldPreserveData", true);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void createNewMegama() {
        // No need to validate image URLs anymore since they're optional
        
        // Check for required fields
        if (megamaName == null || megamaName.isEmpty()) {
            Log.e(TAG, "Error: megamaName is null or empty in createNewMegama");
            Toast.makeText(this, "שגיאה: שם מגמה חסר", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Creating/updating megama with name: " + megamaName);
        
        if (megamaDescription == null || megamaDescription.isEmpty()) {
            Toast.makeText(this, "שגיאה: תיאור מגמה חסר", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (username == null || username.isEmpty() || schoolId == null || schoolId.isEmpty()) {
            Toast.makeText(this, "שגיאה: פרטי משתמש או בית ספר חסרים", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // שינוי כפתור והוספת לואדר
        createMegamaButton.setEnabled(false);
        createMegamaButton.setText("");
        progressBar.setVisibility(View.VISIBLE);
        
        // Hide the icon when loading
        createMegamaButton.setIcon(null);

        // Setup timeout handler - 10 seconds max
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (progressBar.getVisibility() == View.VISIBLE) {
                // If still loading after timeout, reset the UI
                Log.e(TAG, "Operation timed out after 10 seconds");
                progressBar.setVisibility(View.GONE);
                createMegamaButton.setText("עדכון מגמה");
                createMegamaButton.setEnabled(true);
                createMegamaButton.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_save));
                Toast.makeText(MegamaAttachments.this, "פעולה נכשלה - נסה שוב", Toast.LENGTH_SHORT).show();
            }
        }, 10000); // 10 seconds timeout
        
        // Check if the megama already exists in Firestore before proceeding
        Log.d(TAG, "Processing megama with name: " + megamaName);
        
        // Create Megama object with all parameters
        Megama megama = new Megama();
        megama.setName(megamaName);
        megama.setDescription(megamaDescription);
        megama.setImageUrls(uploadedImageUrls);
        megama.setRequiresExam(requiresExam);
        megama.setRequiresGradeAvg(requiresGradeAvg);
        megama.setRequiredGradeAvg(requiredGradeAvg);
        megama.setCustomConditions(customConditions);
        megama.setRakazUsername(username); // Set the rakaz username
        
        // Log the megama object for debugging
        Log.d(TAG, "Creating/updating megama with name: " + megama.getName());
        Log.d(TAG, "Megama description: " + megama.getDescription());
        Log.d(TAG, "Megama rakaz username: " + megama.getRakazUsername());
        Log.d(TAG, "Megama requires exam: " + megama.isRequiresExam());
        Log.d(TAG, "Megama requires grade avg: " + megama.isRequiresGradeAvg());
        Log.d(TAG, "Megama required grade avg: " + megama.getRequiredGradeAvg());
        Log.d(TAG, "Megama custom conditions count: " + (megama.getCustomConditions() != null ? megama.getCustomConditions().size() : 0));
        Log.d(TAG, "Megama image URLs count: " + (megama.getImageUrls() != null ? megama.getImageUrls().size() : 0));

        // Save to Firestore
        CollectionReference megamotRef = fireDB.collection("schools").document(schoolId)
                                              .collection("megamot");
        
        // Directly update the megama document
        Log.d(TAG, "Updating megama document at path: schools/" + schoolId + "/megamot/" + megamaName);
        megamotRef.document(megamaName)
            .set(megama)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Successfully updated megama document");
                // No need to update rakaz-megama reference since it should already be set
                finishMegamaCreation();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to update megama document: " + e.getMessage());
                progressBar.setVisibility(View.GONE);
                createMegamaButton.setText("עדכן מגמה");
                createMegamaButton.setEnabled(true);
                createMegamaButton.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_save));
                Toast.makeText(MegamaAttachments.this, "שגיאה בעדכון מגמה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    // New method to finalize the process and return to rakazPage
    private void finishMegamaCreation() {
        Log.d(TAG, "Finishing megama creation/update process");
        progressBar.setVisibility(View.GONE);
        
        // Determine if this is a new megama creation or an update based on button text
        boolean isUpdate = createMegamaButton.getText().toString().contains("עדכון");
        String successMessage = isUpdate ? "מגמה עודכנה בהצלחה!" : "מגמה נוספה בהצלחה!";
        
        // Create custom styled dialog
        showCustomSuccessDialog(successMessage);
    }
    
    /**
     * Show a custom styled success dialog
     * @param message The success message to display
     */
    private void showCustomSuccessDialog(String message) {
        // Create a dialog
        Dialog customDialog = new Dialog(this);
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        customDialog.setCancelable(false);
        
        // Set the custom layout
        customDialog.setContentView(R.layout.megama_success_dialog);
        
        // Get window to set layout parameters
        Window window = customDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            // Add custom animation
            window.setWindowAnimations(R.style.DialogAnimation);
        }
        
        // Set the success message
        TextView messageView = customDialog.findViewById(R.id.dialogMessage);
        if (messageView != null) {
            messageView.setText(message);
        }
        
        // Set the success icon based on message (different for update vs create)
        ImageView iconView = customDialog.findViewById(R.id.successIcon);
        if (iconView != null) {
            // Set background oval shape with green color
            iconView.setBackgroundResource(R.drawable.circle_background);
            
            if (message.contains("עודכנה")) {
                // Use edit icon for updates
                iconView.setImageResource(R.drawable.ic_edit);
            } else {
                // Use checkmark icon for new creations
                iconView.setImageResource(R.drawable.ic_checkmark);
            }
            // No need to tint as we'll color the background instead
        }
        
        // Set up the close button
        MaterialButton closeButton = customDialog.findViewById(R.id.closeButton);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                customDialog.dismiss();
                // Navigate back to rakaz page
                Intent intent = new Intent(MegamaAttachments.this, rakazPage.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }
        
        // Show the dialog
        customDialog.show();
    }

    private void updateImageCountText() {
        int totalImages = selectedImageUris.size() + uploadedImageUrls.size();
        String imageCountText = "תמונות נבחרות: " + totalImages;
        
        // Find the TextView by its text content since we don't have a direct reference
        for (int i = 0; i < ((ViewGroup) imageSection).getChildCount(); i++) {
            View child = ((ViewGroup) imageSection).getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                if (textView.getText().toString().startsWith("תמונות נבחרות")) {
                    textView.setText(imageCountText);
                    break;
                }
            }
        }
        
        // Show empty state message if no images
        if (totalImages == 0) {
            // We could add an empty state view here if needed
        }
    }

    // Adapter for the images RecyclerView
    private class ImagesAdapter extends RecyclerView.Adapter<ImagesAdapter.ImageViewHolder> {
        private final List<Uri> imageUris;
        private final List<String> uploadedUrls;

        public ImagesAdapter(List<Uri> imageUris) {
            this.imageUris = imageUris;
            this.uploadedUrls = uploadedImageUrls; // Reference the class member
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_selected_image, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            if (position < imageUris.size()) {
                // This is a local Uri from selectedImageUris
                Uri imageUri = imageUris.get(position);
                
                // Load image using Glide
                Glide.with(MegamaAttachments.this)
                        .load(imageUri)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(holder.imageView);
                
                // Remove image button
                holder.removeButton.setOnClickListener(v -> {
                    int adapterPosition = holder.getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < imageUris.size()) {
                        imageUris.remove(adapterPosition);
                        notifyItemRemoved(adapterPosition);
                        updateImageCountText();
                    }
                });
            } else {
                // This is a remote URL from uploadedImageUrls
                int uploadedPosition = position - imageUris.size();
                if (uploadedPosition < uploadedUrls.size()) {
                    String imageUrl = uploadedUrls.get(uploadedPosition);
                    
                    // Load image using Glide
                    Glide.with(MegamaAttachments.this)
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .centerCrop()
                            .into(holder.imageView);
                    
                    // Remove image button
                    holder.removeButton.setOnClickListener(v -> {
                        int adapterPosition = holder.getAdapterPosition();
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            int uploadedIndex = adapterPosition - imageUris.size();
                            if (uploadedIndex >= 0 && uploadedIndex < uploadedUrls.size()) {
                                uploadedUrls.remove(uploadedIndex);
                                notifyItemRemoved(adapterPosition);
                                updateImageCountText();
                            }
                        }
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return imageUris.size() + uploadedUrls.size();
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            ImageButton removeButton;

            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.selected_image);
                removeButton = itemView.findViewById(R.id.remove_image_button);
            }
        }
    }
} 