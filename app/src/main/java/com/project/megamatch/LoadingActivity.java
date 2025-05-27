package com.project.megamatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class LoadingActivity extends AppCompatActivity {

    private static final String TAG = "LoadingActivity";
    private static final int MIN_LOADING_TIME = 1000; // Minimum time to show loading screen (milliseconds)
    
    private TextView loadingText;
    private FirebaseFirestore fireDB;
    private String schoolId;
    private String username;
    private long startTime;
    private boolean dataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        // Initialize views
        loadingText = findViewById(R.id.loadingText);
        
        // Initialize Firestore
        fireDB = FirebaseFirestore.getInstance();
        
        // Get user credentials from shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        schoolId = sharedPreferences.getString("loggedInSchoolId", "");
        username = sharedPreferences.getString("loggedInUsername", "");
        
        // Validate credentials
        if (schoolId.isEmpty() || username.isEmpty()) {
            goToLoginScreen();
            return;
        }
        
        // Record start time
        startTime = System.currentTimeMillis();
        
        // Start loading data
        loadUserData();
    }
    
    private void loadUserData() {
        // Load user data from Firestore
        fireDB.collection("schools").document(schoolId)
              .collection("rakazim").document(username)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      Log.d(TAG, "User data loaded successfully");
                      
                      // Mark data as loaded
                      dataLoaded = true;
                      
                      // Check if minimum loading time has passed
                      checkNavigationConditions();
                  } else {
                      // User document doesn't exist
                      Log.e(TAG, "User document not found");
                      goToLoginScreen();
                  }
              })
              .addOnFailureListener(e -> {
                  // Error loading user data
                  Log.e(TAG, "Error loading user data: " + e.getMessage());
                  goToLoginScreen();
              });
    }
    
    private void checkNavigationConditions() {
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        if (elapsedTime >= MIN_LOADING_TIME && dataLoaded) {
            // Both conditions met, proceed to main screen
            goToMainScreen();
        } else {
            // Wait for the remaining time
            long remainingTime = Math.max(0, MIN_LOADING_TIME - elapsedTime);
            new Handler().postDelayed(this::goToMainScreen, remainingTime);
        }
    }
    
    private void goToMainScreen() {
        Intent intent = new Intent(this, rakazPage.class);
        startActivity(intent);
        finish();
    }
    
    private void goToLoginScreen() {
        Intent intent = new Intent(this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 