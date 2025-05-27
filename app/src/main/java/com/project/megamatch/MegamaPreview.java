package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.os.Bundle;
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
    private List<String> imageUrls = new ArrayList<>();
    private int currentImagePosition = 0;

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

            // Load megama data
            loadMegamaData();
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

        // Setup ViewPager
        ImageSliderAdapter sliderAdapter = new ImageSliderAdapter();
        imageViewPager.setAdapter(sliderAdapter);
        
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
                      // Get rakaz name and greeting
                      String firstName = documentSnapshot.getString("firstName");
                      if (firstName != null && !firstName.isEmpty()) {
                          greetingText.setText("שלום " + firstName);
                      } else {
                          greetingText.setText("שלום " + username);
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

    private void loadMegamaDetails() {
        fireDB.collection("schools").document(schoolId)
              .collection("megamot").document(megamaName)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      displayMegamaDetails(documentSnapshot);
                  } else {
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