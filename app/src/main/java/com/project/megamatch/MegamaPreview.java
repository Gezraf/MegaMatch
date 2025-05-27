package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class MegamaPreview extends AppCompatActivity {

    private static final String TAG = "MegamaPreview";

    // UI components
    private TextView greetingText, megamaTitle, megamaDescription, imageCounter;
    private TextView requirementExam, requirementGrade, noRequirementsText, noImagesText;
    private Button backButton;
    private ImageButton prevImageButton, nextImageButton;
    private ViewPager2 imageViewPager;
    private LinearLayout customRequirementsContainer;

    // Firebase
    private FirebaseFirestore fireDB;

    // Data
    private String schoolId;
    private String username;
    private String megamaName;
    private String megamaDocId; // The document ID for the megama (can be the megama name or rakaz username)
    private List<String> imageUrls = new ArrayList<>();
    private int currentImagePosition = 0;
    private boolean isManager = false; // Flag to identify if the user is a manager
    private ImageButton deleteButton; // Delete button for managers

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.megama_preview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.megamaPreview), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase
        fireDB = FirebaseFirestore.getInstance();

        // Initialize UI components
        initializeViews();

        // Get data from intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            schoolId = extras.getString("schoolId", "");
            username = extras.getString("username", "");
            megamaName = extras.getString("megamaName", "");
            megamaDocId = extras.getString("megamaDocId", "");
            isManager = extras.getBoolean("isManager", false);
            
            // For backward compatibility
            if (megamaDocId == null || megamaDocId.isEmpty()) {
                megamaDocId = megamaName; // Default to megama name if not specified
            }
            
            Log.d(TAG, "Got from intent - megamaName: " + megamaName + ", megamaDocId: " + megamaDocId + ", isManager: " + isManager);

            // If we have the document ID directly, load the megama details
            if (megamaDocId != null && !megamaDocId.isEmpty()) {
                loadMegamaDetailsDirect();
            } else {
                // Otherwise, load via rakaz document first
                loadMegamaData();
            }
        } else {
            Toast.makeText(this, "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Setup click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        greetingText = findViewById(R.id.greetingText);
        megamaTitle = findViewById(R.id.megamaTitle);
        megamaDescription = findViewById(R.id.megamaDescription);
        imageCounter = findViewById(R.id.imageCounter);
        requirementExam = findViewById(R.id.requirementExam);
        requirementGrade = findViewById(R.id.requirementGrade);
        noRequirementsText = findViewById(R.id.noRequirementsText);
        noImagesText = findViewById(R.id.noImagesText);
        backButton = findViewById(R.id.backButton);
        prevImageButton = findViewById(R.id.prevImageButton);
        nextImageButton = findViewById(R.id.nextImageButton);
        imageViewPager = findViewById(R.id.imageViewPager);
        customRequirementsContainer = findViewById(R.id.customRequirementsContainer);

        // Immediately handle manager UI if needed
        if (isManager) {
            // Hide greeting right away to prevent flicker
            if (greetingText != null) {
                greetingText.setVisibility(View.GONE);
            }
        }

        // Setup ViewPager
        ImageSliderAdapter sliderAdapter = new ImageSliderAdapter();
        imageViewPager.setAdapter(sliderAdapter);
        
        // If user is a manager, replace greeting with delete button
        if (isManager) {
            Log.d(TAG, "Manager mode detected, setting up delete button");
            setupDeleteButton();
            
            // Alternative approach - directly modify the layout
            if (greetingText != null) {
                // Replace greeting text with a button
                ViewGroup rootLayout = findViewById(R.id.megamaPreview);
                if (rootLayout != null) {
                    Log.d(TAG, "Adding delete button to root layout as a backup approach");
                    
                    // Create another delete button as a fallback
                    ImageButton backupDeleteButton = new ImageButton(this);
                    backupDeleteButton.setImageResource(android.R.drawable.ic_menu_delete);
                    backupDeleteButton.setBackgroundColor(Color.RED);
                    backupDeleteButton.setColorFilter(Color.WHITE);
                    
                    // Make it fixed size
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            dpToPx(60),
                            dpToPx(60)
                    );
                    
                    // Position it at the top right
                    params.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                    params.setMargins(0, dpToPx(16), dpToPx(16), 0);
                    backupDeleteButton.setLayoutParams(params);
                    
                    // Add click listener
                    backupDeleteButton.setOnClickListener(v -> showDeleteConfirmation());
                    
                    // Add to layout
                    rootLayout.addView(backupDeleteButton);
                }
            }
        }
        
        // Setup ViewPager page change listener
        imageViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentImagePosition = position;
                updateImageCounter();
            }
        });
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Image navigation buttons
        prevImageButton.setOnClickListener(v -> {
            if (currentImagePosition > 0) {
                imageViewPager.setCurrentItem(currentImagePosition - 1);
            }
        });

        nextImageButton.setOnClickListener(v -> {
            if (currentImagePosition < imageUrls.size() - 1) {
                imageViewPager.setCurrentItem(currentImagePosition + 1);
            }
        });
    }

    private void loadMegamaData() {
        // First get the megama name from the rakaz document
        fireDB.collection("schools").document(schoolId)
              .collection("rakazim").document(username)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      // Get rakaz name and greeting (only for non-managers)
                      if (!isManager) {
                          String firstName = documentSnapshot.getString("firstName");
                          if (firstName != null && !firstName.isEmpty()) {
                              greetingText.setText("שלום " + firstName);
                          } else {
                              greetingText.setText("שלום " + username);
                          }
                      }

                      // Get megama name
                      megamaName = documentSnapshot.getString("megama");
                      if (megamaName != null && !megamaName.isEmpty()) {
                          // Now get the megama details
                          loadMegamaDetails();
                      } else {
                          Toast.makeText(this, "לא נמצאה מגמה", Toast.LENGTH_SHORT).show();
                          finish();
                      }
                  } else {
                      Toast.makeText(this, "לא נמצאו פרטי רכז", Toast.LENGTH_SHORT).show();
                      finish();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e(TAG, "Error loading rakaz data: " + e.getMessage());
                  Toast.makeText(this, "שגיאה בטעינת פרטי רכז", Toast.LENGTH_SHORT).show();
                  finish();
              });
    }

    private void loadMegamaDetailsDirect() {
        // If this is a manager view, don't set up greeting at all
        if (isManager) {
            // Ensure greeting is not visible for managers
            if (greetingText != null) {
                Log.d(TAG, "Manager view - transforming greeting to a delete button");
                
                // Third approach - transform the TextView into a delete button visual
                greetingText.setVisibility(View.VISIBLE);
                greetingText.setText("");  // Clear text
                greetingText.setBackgroundColor(Color.RED);
                greetingText.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_delete, 0);
                greetingText.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                greetingText.setOnClickListener(v -> {
                    Log.d(TAG, "Delete button (text view) clicked");
                    showDeleteConfirmation();
                });
            }
        }
        // Set rakaz greeting if username is available and not in manager mode
        else if (username != null && !username.isEmpty()) {
            fireDB.collection("schools").document(schoolId)
                  .collection("rakazim").document(username)
                  .get()
                  .addOnSuccessListener(documentSnapshot -> {
                      if (documentSnapshot.exists()) {
                          String firstName = documentSnapshot.getString("firstName");
                          if (firstName != null && !firstName.isEmpty()) {
                              greetingText.setText("שלום " + firstName);
                          } else {
                              greetingText.setText("שלום " + username);
                          }
                      } else {
                          greetingText.setText("שלום");
                      }
                  })
                  .addOnFailureListener(e -> {
                      Log.e(TAG, "Error loading rakaz details for greeting: " + e.getMessage());
                      greetingText.setText("שלום");
                  });
        } else {
            greetingText.setText("שלום");
        }
        
        // Now load the megama details using megamaDocId
        Log.d(TAG, "Loading megama with document ID: " + megamaDocId);
        fireDB.collection("schools").document(schoolId)
              .collection("megamot").document(megamaDocId)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      // If megamaName is not set yet, get it from the document
                      if (megamaName == null || megamaName.isEmpty()) {
                          megamaName = documentSnapshot.getString("name");
                      }
                      displayMegamaDetails(documentSnapshot);
                  } else {
                      Log.e(TAG, "Megama document does not exist: " + megamaDocId);
                      Toast.makeText(this, "לא נמצאו פרטי מגמה", Toast.LENGTH_SHORT).show();
                      finish();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e(TAG, "Error loading megama details: " + e.getMessage());
                  Toast.makeText(this, "שגיאה בטעינת פרטי מגמה", Toast.LENGTH_SHORT).show();
                  finish();
              });
    }

    private void loadMegamaDetails() {
        // Now megamaName has been loaded from the rakaz document,
        // use it as the document ID for backward compatibility
        megamaDocId = megamaName;
        Log.d(TAG, "Loading megama with name as document ID: " + megamaDocId);
        
        fireDB.collection("schools").document(schoolId)
              .collection("megamot").document(megamaDocId)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      displayMegamaDetails(documentSnapshot);
                  } else {
                      Log.e(TAG, "Megama document does not exist with megamaName: " + megamaDocId);
                      Toast.makeText(this, "לא נמצאו פרטי מגמה", Toast.LENGTH_SHORT).show();
                      finish();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e(TAG, "Error loading megama details: " + e.getMessage());
                  Toast.makeText(this, "שגיאה בטעינת פרטי מגמה", Toast.LENGTH_SHORT).show();
                  finish();
              });
    }

    private void displayMegamaDetails(DocumentSnapshot document) {
        // Set title
        megamaTitle.setText("מגמת " + megamaName);

        // Set description
        String description = document.getString("description");
        if (description != null && !description.isEmpty()) {
            megamaDescription.setText(description);
        } else {
            megamaDescription.setText("אין תיאור מגמה");
        }

        // Load images
        List<String> images = (List<String>) document.get("imageUrls");
        if (images != null && !images.isEmpty()) {
            imageUrls.addAll(images);
            ((ImageSliderAdapter) imageViewPager.getAdapter()).notifyDataSetChanged();
            updateImageCounter();
            
            // Show/hide navigation buttons based on number of images
            boolean hasMultipleImages = imageUrls.size() > 1;
            prevImageButton.setVisibility(hasMultipleImages ? View.VISIBLE : View.GONE);
            nextImageButton.setVisibility(hasMultipleImages ? View.VISIBLE : View.GONE);
            noImagesText.setVisibility(View.GONE);
        } else {
            // No images available
            imageCounter.setVisibility(View.GONE);
            prevImageButton.setVisibility(View.GONE);
            nextImageButton.setVisibility(View.GONE);
            noImagesText.setVisibility(View.VISIBLE);
        }

        // Show requirements
        boolean hasRequirements = false;
        
        // Check if exam is required
        Boolean requiresExamValue = document.getBoolean("requiresExam");
        if (requiresExamValue != null && requiresExamValue) {
            requirementExam.setVisibility(View.VISIBLE);
            hasRequirements = true;
        } else {
            requirementExam.setVisibility(View.GONE);
        }
        
        // Check if grade average is required
        Boolean requiresGradeAvgValue = document.getBoolean("requiresGradeAvg");
        if (requiresGradeAvgValue != null && requiresGradeAvgValue) {
            Long requiredGradeAvgValue = document.getLong("requiredGradeAvg");
            if (requiredGradeAvgValue != null) {
                requirementGrade.setText("נדרש ממוצע ציונים של: " + requiredGradeAvgValue);
                requirementGrade.setVisibility(View.VISIBLE);
                hasRequirements = true;
            }
        } else {
            requirementGrade.setVisibility(View.GONE);
        }
        
        // Add custom requirements
        List<String> customConditions = (List<String>) document.get("customConditions");
        if (customConditions != null && !customConditions.isEmpty()) {
            customRequirementsContainer.removeAllViews();
            for (String condition : customConditions) {
                addCustomRequirement(condition);
            }
            hasRequirements = true;
        }
        
        // Show "no requirements" message if needed
        noRequirementsText.setVisibility(hasRequirements ? View.GONE : View.VISIBLE);
    }

    private void addCustomRequirement(String requirement) {
        TextView textView = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16); // bottom margin
        textView.setLayoutParams(params);
        
        textView.setText(requirement);
        textView.setTextSize(16);
        textView.setTextColor(getResources().getColor(R.color.white));
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.checkbox_on_background, 0);
        textView.setCompoundDrawablePadding(8);
        
        customRequirementsContainer.addView(textView);
    }

    private void updateImageCounter() {
        if (imageUrls.size() > 0) {
            imageCounter.setText((currentImagePosition + 1) + "/" + imageUrls.size());
            imageCounter.setVisibility(View.VISIBLE);
        } else {
            imageCounter.setVisibility(View.GONE);
        }
    }

    // Setup delete button for managers
    private void setupDeleteButton() {
        // Instead of removing the greeting text, let's convert it into a container for our button
        // This ensures we maintain the same layout positioning
        
        // First make sure the greeting text is hidden
        greetingText.setVisibility(View.GONE);
        
        // Get the parent layout that contains the greeting text
        ViewGroup parent = (ViewGroup) greetingText.getParent();
        if (parent != null) {
            Log.d(TAG, "Parent view found, adding delete button");
            
            // Create delete button programmatically with very clear styling
            deleteButton = new ImageButton(this);
            deleteButton.setId(View.generateViewId()); // Give it a unique ID
            deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
            deleteButton.setBackgroundColor(Color.RED);
            deleteButton.setColorFilter(Color.WHITE); // White icon
            
            // Make sure the button is visible with clear dimensions
            deleteButton.setVisibility(View.VISIBLE);
            
            // Set button size and style (make it larger and more visible)
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dpToPx(60), // 60dp width - bigger to be more visible
                    dpToPx(60)  // 60dp height - bigger to be more visible
            );
            params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8)); // Add margins all around
            deleteButton.setLayoutParams(params);
            
            // Add the button directly to the parent layout
            parent.addView(deleteButton);
            
            // Set click listener
            deleteButton.setOnClickListener(v -> {
                Log.d(TAG, "Delete button clicked");
                showDeleteConfirmation();
            });
            
            Log.d(TAG, "Delete button added successfully");
        } else {
            Log.e(TAG, "Could not find parent view for greeting text");
        }
    }
    
    // Convert dp to pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    // Show delete confirmation dialog with cooldown
    private void showDeleteConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("מחיקת מגמה");
        builder.setMessage("פעולה זו תמחק לצמיתות את המגמה. להמשיך?");
        
        // Create cancel button
        builder.setNegativeButton("בטל", (dialog, which) -> dialog.dismiss());
        
        // Create delete button with cooldown
        final int[] countdown = {3}; // 3 second countdown
        final TextView[] deleteButtonText = new TextView[1]; // For reference to button text
        
        builder.setPositiveButton("מחק", null); // We'll override this below
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            deleteButton.setTextColor(Color.RED);
            deleteButton.setEnabled(false);
            deleteButtonText[0] = (TextView) deleteButton;
            
            // Set initial transparency
            deleteButton.setAlpha(0.3f);
            
            // Start countdown
            new CountDownTimer(3000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    countdown[0] = (int) (millisUntilFinished / 1000) + 1;
                    deleteButton.setText("מחק (" + countdown[0] + ")");
                }
                
                @Override
                public void onFinish() {
                    deleteButton.setEnabled(true);
                    deleteButton.setAlpha(1.0f);
                    deleteButton.setText("מחק");
                    
                    // Set click listener for actual delete
                    deleteButton.setOnClickListener(v -> {
                        dialog.dismiss();
                        deleteMegama();
                    });
                }
            }.start();
        });
        
        dialog.show();
    }
    
    // Delete the megama from Firestore
    private void deleteMegama() {
        // Show progress
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("מוחק מגמה...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Delete megama document
        fireDB.collection("schools").document(schoolId)
            .collection("megamot").document(megamaDocId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                progressDialog.dismiss();
                Toast.makeText(MegamaPreview.this, "המגמה נמחקה בהצלחה", Toast.LENGTH_SHORT).show();
                // Return to previous screen
                finish();
            })
            .addOnFailureListener(e -> {
                progressDialog.dismiss();
                Log.e(TAG, "Error deleting megama", e);
                Toast.makeText(MegamaPreview.this, "שגיאה במחיקת המגמה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    // ViewPager adapter for image slider
    private class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder> {

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image_slider, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String imageUrl = imageUrls.get(position);
            
            // Load image using Glide
            Glide.with(MegamaPreview.this)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .error(R.drawable.gradient_background) // Fallback if image fails to load
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.sliderImageView);
            }
        }
    }
} 