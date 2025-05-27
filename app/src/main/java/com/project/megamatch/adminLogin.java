package com.project.megamatch;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class adminLogin extends AppCompatActivity {

    private static final String TAG = "AdminLogin";
    private EditText usernameEditText;
    private EditText idEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        try {
            setContentView(R.layout.admin_login);
            Log.d(TAG, "adminLogin setContentView successful");
            
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.adminLoginLayout), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
    
            // Initialize Firestore with offline persistence
            db = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
    
            // Initialize UI elements
            usernameEditText = findViewById(R.id.usernameEditText);
            idEditText = findViewById(R.id.idEditText);
            passwordEditText = findViewById(R.id.passwordEditText);
            loginButton = findViewById(R.id.loginButton);
            progressBar = findViewById(R.id.progressBar);
    
            loginButton.setOnClickListener(v -> attemptLogin());
            Log.d(TAG, "adminLogin onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in adminLogin onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בטעינת הדף: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void attemptLogin() {
        try {
            String username = usernameEditText.getText().toString().trim();
            String id = idEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
    
            // Validate inputs
            if (username.isEmpty() || id.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "כל השדות חייבים להיות מלאים", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate ID is 9 digits
            if (id.length() != 9) {
                Toast.makeText(this, "תעודת זהות חייבת להיות באורך 9 ספרות", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate ID contains only digits
            if (!id.matches("\\d+")) {
                Toast.makeText(this, "תעודת זהות יכולה להכיל רק ספרות", Toast.LENGTH_SHORT).show();
                return;
            }
    
            // Show progress bar
            progressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
    
            Log.d(TAG, "Attempting to login with username: " + username);
    
            // Check if admin exists in Firestore
            db.collection("admins").document(username)
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            Log.d(TAG, "Document exists: " + (document != null && document.exists()));
                            
                            if (document.exists()) {
                                // Verify credentials
                                String storedId = document.getString("id");
                                String storedPassword = document.getString("password");
                                Boolean isAdmin = document.getBoolean("admin");
    
                                Log.d(TAG, "Stored ID: " + storedId + ", Input ID: " + id);
                                Log.d(TAG, "Stored Password length: " + (storedPassword != null ? storedPassword.length() : 0));
                                Log.d(TAG, "Is Admin: " + isAdmin);
    
                                // Check if the admin field exists and is true
                                if (isAdmin == null || !isAdmin) {
                                    Toast.makeText(adminLogin.this, "משתמש זה אינו מנהל מערכת", Toast.LENGTH_SHORT).show();
                                    progressBar.setVisibility(View.GONE);
                                    loginButton.setEnabled(true);
                                    return;
                                }
    
                                if (id.equals(storedId) && password.equals(storedPassword)) {
                                    // Login successful
                                    Toast.makeText(adminLogin.this, "התחברת בהצלחה", Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "Login successful, will navigate to loading screen");
                                    
                                    // Store admin info in shared preferences
                                    saveAdminSession(username);
                                    
                                    // Navigate to loading screen
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try {
                                            Log.d(TAG, "Now navigating to AdminLoadingActivity");
                                            Intent intent = new Intent(adminLogin.this, AdminLoadingActivity.class);
                                            startActivity(intent);
                                            finish();
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error navigating to loading screen: " + e.getMessage(), e);
                                            Toast.makeText(adminLogin.this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            progressBar.setVisibility(View.GONE);
                                            loginButton.setEnabled(true);
                                        }
                                    }, 1000);
                                } else {
                                    // Provide more specific error message
                                    if (!id.equals(storedId)) {
                                        Toast.makeText(adminLogin.this, "תעודת זהות שגויה", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(adminLogin.this, "סיסמה שגויה", Toast.LENGTH_SHORT).show();
                                    }
                                    Log.d(TAG, "Invalid credentials");
                                    progressBar.setVisibility(View.GONE);
                                    loginButton.setEnabled(true);
                                }
                            } else {
                                // Admin not found - more specific message
                                Toast.makeText(adminLogin.this, "שם משתמש לא קיים במערכת", Toast.LENGTH_SHORT).show();
                                Log.d(TAG, "Admin not found");
                                progressBar.setVisibility(View.GONE);
                                loginButton.setEnabled(true);
                            }
                        } else {
                            // Error occurred - provide a more specific message for network issues
                            String errorMessage = "שגיאה בהתחברות";
                            if (task.getException() != null) {
                                Log.e(TAG, "Login error: " + task.getException().getMessage(), task.getException());
                                if (task.getException().getMessage() != null && 
                                    task.getException().getMessage().contains("offline")) {
                                    errorMessage = "אין חיבור לאינטרנט. בדוק את החיבור שלך ונסה שוב.";
                                } else {
                                    errorMessage += ": " + task.getException().getMessage();
                                }
                            }
                            Toast.makeText(adminLogin.this, errorMessage, Toast.LENGTH_LONG).show();
                            progressBar.setVisibility(View.GONE);
                            loginButton.setEnabled(true);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception in login task completion: " + e.getMessage(), e);
                        Toast.makeText(adminLogin.this, "שגיאה בעיבוד נתונים: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(View.GONE);
                        loginButton.setEnabled(true);
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Exception in attemptLogin: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בתהליך ההתחברות: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
        }
    }
    
    private void saveAdminSession(String username) {
        try {
            // Save admin session data in SharedPreferences
            Log.d(TAG, "Saving admin session for: " + username);
            getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE)
                .edit()
                .putString("loggedInAdmin", username)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving admin session: " + e.getMessage(), e);
        }
    }

    public void goBack(View view) {
        onBackPressed();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "adminLogin onResume called");
        
        // Re-enable login button if needed
        if (loginButton != null) {
            loginButton.setEnabled(true);
        }
        
        // Hide progress bar if visible
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }
} 