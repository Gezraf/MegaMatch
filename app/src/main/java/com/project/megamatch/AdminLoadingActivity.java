package com.project.megamatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class AdminLoadingActivity extends AppCompatActivity {

    private static final String TAG = "AdminLoadingActivity";
    private static final int MIN_LOADING_TIME = 1000; // Minimum time to show loading screen (milliseconds)
    
    private TextView loadingText;
    private FirebaseFirestore fireDB;
    private String adminUsername;
    private long startTime;
    private boolean dataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        // Initialize views
        loadingText = findViewById(R.id.loadingText);
        loadingText.setText("טוען נתוני מנהל מערכת...");
        
        // Initialize Firestore
        fireDB = FirebaseFirestore.getInstance();
        
        // Get admin credentials from shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        adminUsername = sharedPreferences.getString("loggedInAdmin", "");
        
        // Validate credentials
        if (adminUsername.isEmpty()) {
            goToLoginScreen();
            return;
        }
        
        // Record start time
        startTime = System.currentTimeMillis();
        
        // Start loading data
        loadAdminData();
    }
    
    private void loadAdminData() {
        // Load admin data from Firestore
        fireDB.collection("admins").document(adminUsername)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists() && Boolean.TRUE.equals(documentSnapshot.getBoolean("admin"))) {
                      Log.d(TAG, "Admin data loaded successfully");
                      
                      // Mark data as loaded
                      dataLoaded = true;
                      
                      // Check if minimum loading time has passed
                      checkNavigationConditions();
                  } else {
                      // Admin document doesn't exist or user is not an admin
                      Log.e(TAG, "Admin document not found or user is not an admin");
                      goToLoginScreen();
                  }
              })
              .addOnFailureListener(e -> {
                  // Error loading admin data
                  Log.e(TAG, "Error loading admin data: " + e.getMessage());
                  goToLoginScreen();
              });
    }
    
    private void checkNavigationConditions() {
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        if (elapsedTime >= MIN_LOADING_TIME && dataLoaded) {
            // Both conditions met, proceed to admin hub
            goToAdminHub();
        } else {
            // Wait for the remaining time
            long remainingTime = Math.max(0, MIN_LOADING_TIME - elapsedTime);
            new Handler().postDelayed(this::goToAdminHub, remainingTime);
        }
    }
    
    private void goToAdminHub() {
        Intent intent = new Intent(this, adminHub.class);
        startActivity(intent);
        finish();
    }
    
    private void goToLoginScreen() {
        // Clear the admin session
        getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE)
            .edit()
            .remove("loggedInAdmin")
            .apply();
            
        Intent intent = new Intent(this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 