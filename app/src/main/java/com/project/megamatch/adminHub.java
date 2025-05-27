package com.project.megamatch;

import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class adminHub extends AppCompatActivity {
    private static final String TAG = "AdminHub";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Log.d(TAG, "Starting onCreate for adminHub");
            EdgeToEdge.enable(this);
            setContentView(R.layout.admin_hub);
            
            Log.d(TAG, "setContentView completed successfully");
            
            // Set up window insets with error catching
            try {
                View rootView = findViewById(R.id.adminHubLayout);
                if (rootView != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                        return insets;
                    });
                    Log.d(TAG, "Window insets set up successfully");
                } else {
                    Log.e(TAG, "Root view (adminHubLayout) not found!");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting up window insets: " + e.getMessage(), e);
                // Continue with the activity even if insets fail
            }
            
            Log.d(TAG, "adminHub onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in adminHub onCreate: " + e.getMessage(), e);
            // Show error toast and redirect to login page
            try {
                Toast.makeText(this, "שגיאה בטעינת הדף: " + e.getMessage(), Toast.LENGTH_LONG).show();
                goToLoginPage();
            } catch (Exception ex) {
                // Last resort, just finish the activity
                Log.e(TAG, "Error handling failed: " + ex.getMessage(), ex);
                finish();
            }
        }
    }

    public void moveToAddSchool(View view) {
        try {
            Intent intent = new Intent(adminHub.this, addSchool.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to addSchool: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void moveToSchoolSelect(View view) {
        try {
            Intent intent = new Intent(adminHub.this, schoolSelect.class);
            // Pass a flag to indicate that this is an admin accessing schoolSelect
            intent.putExtra("isAdmin", true);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to schoolSelect: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void moveToHelp(View view) {
        try {
            Intent i1 = new Intent(this, helpPage.class);
            startActivity(i1);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to helpPage: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void moveToCredits(View view) {
        try {
            Intent i1 = new Intent(this, creditsPage.class);
            startActivity(i1);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to creditsPage: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    public void logout(View view) {
        try {
            // Go back to login page
            goToLoginPage();
        } catch (Exception e) {
            Log.e(TAG, "Error during logout: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בהתנתקות: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish(); // Force finish as last resort
        }
    }
    
    private void goToLoginPage() {
        // Clear any admin session
        getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE)
            .edit()
            .remove("loggedInAdmin")
            .apply();
            
        Intent intent = new Intent(adminHub.this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "adminHub onResume called");
    }
} 