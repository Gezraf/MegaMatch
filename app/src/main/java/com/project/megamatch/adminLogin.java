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

/**
 * מחלקה זו מייצגת את מסך ההתחברות של מנהל המערכת.
 * היא מאפשרת למנהל להתחבר למערכת באמצעות שם משתמש, תעודת זהות וסיסמה.
 * כוללת בדיקות תקינות וטיפול בשגיאות.
 */
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
    
            // אתחול פיירבייס עם שמירה מקומית
            db = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
    
            // אתחול רכיבי הממשק
            usernameEditText = findViewById(R.id.usernameEditText);
            idEditText = findViewById(R.id.idEditText);
            passwordEditText = findViewById(R.id.passwordEditText);
            loginButton = findViewById(R.id.loginButton);
            progressBar = findViewById(R.id.progressBar);
    
            loginButton.setOnClickListener(v -> attemptLogin());
            Log.d(TAG, "adminLogin onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "שגיאה קטלנית ב-adminLogin onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בטעינת הדף: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * מנסה להתחבר למערכת עם הפרטים שהוזנו
     */
    private void attemptLogin() {
        try {
            String username = usernameEditText.getText().toString().trim();
            String id = idEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
    
            // בדיקת תקינות הקלט
            if (username.isEmpty() || id.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "כל השדות חייבים להיות מלאים", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // בדיקה שתעודת הזהות היא 9 ספרות
            if (id.length() != 9) {
                Toast.makeText(this, "תעודת זהות חייבת להיות באורך 9 ספרות", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // בדיקה שתעודת הזהות מכילה רק ספרות
            if (!id.matches("\\d+")) {
                Toast.makeText(this, "תעודת זהות יכולה להכיל רק ספרות", Toast.LENGTH_SHORT).show();
                return;
            }
    
            // הצגת סרגל התקדמות
            progressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
    
            Log.d(TAG, "מנסה להתחבר עם שם משתמש: " + username);
    
            // בדיקה אם המנהל קיים בפיירבייס
            db.collection("admins").document(username)
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            Log.d(TAG, "המסמך קיים: " + (document != null && document.exists()));
                            
                            if (document.exists()) {
                                // אימות פרטי ההתחברות
                                String storedId = document.getString("id");
                                String storedPassword = document.getString("password");
                                Boolean isAdmin = document.getBoolean("admin");
    
                                Log.d(TAG, "תעודת זהות שמורה: " + storedId + ", תעודת זהות שהוזנה: " + id);
                                Log.d(TAG, "אורך סיסמה שמורה: " + (storedPassword != null ? storedPassword.length() : 0));
                                Log.d(TAG, "האם מנהל: " + isAdmin);
    
                                // בדיקה אם שדה המנהל קיים ומוגדר כ-true
                                if (isAdmin == null || !isAdmin) {
                                    Toast.makeText(adminLogin.this, "משתמש זה אינו מנהל מערכת", Toast.LENGTH_SHORT).show();
                                    progressBar.setVisibility(View.GONE);
                                    loginButton.setEnabled(true);
                                    return;
                                }
    
                                if (id.equals(storedId) && password.equals(storedPassword)) {
                                    // התחברות הצליחה
                                    Toast.makeText(adminLogin.this, "התחברת בהצלחה", Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "ההתחברות הצליחה, מנווט למסך הטעינה");
                                    
                                    // שמירת פרטי המנהל בהעדפות משותפות
                                    saveAdminSession(username);
                                    
                                    // ניווט למסך הטעינה
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try {
                                            Log.d(TAG, "מנווט כעת ל-AdminLoadingActivity");
                                            Intent intent = new Intent(adminLogin.this, AdminLoadingActivity.class);
                                            startActivity(intent);
                                            finish();
                                        } catch (Exception e) {
                                            Log.e(TAG, "שגיאה בניווט למסך הטעינה: " + e.getMessage(), e);
                                            Toast.makeText(adminLogin.this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            progressBar.setVisibility(View.GONE);
                                            loginButton.setEnabled(true);
                                        }
                                    }, 1000);
                                } else {
                                    // הצגת הודעת שגיאה ספציפית
                                    if (!id.equals(storedId)) {
                                        Toast.makeText(adminLogin.this, "תעודת זהות שגויה", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(adminLogin.this, "סיסמה שגויה", Toast.LENGTH_SHORT).show();
                                    }
                                    Log.d(TAG, "פרטי התחברות לא תקינים");
                                    progressBar.setVisibility(View.GONE);
                                    loginButton.setEnabled(true);
                                }
                            } else {
                                // מנהל לא נמצא - הודעה ספציפית
                                Toast.makeText(adminLogin.this, "שם משתמש לא קיים במערכת", Toast.LENGTH_SHORT).show();
                                Log.d(TAG, "המנהל לא נמצא");
                                progressBar.setVisibility(View.GONE);
                                loginButton.setEnabled(true);
                            }
                        } else {
                            // אירעה שגיאה - הצגת הודעה ספציפית לבעיות רשת
                            String errorMessage = "שגיאה בהתחברות";
                            if (task.getException() != null) {
                                Log.e(TAG, "שגיאת התחברות: " + task.getException().getMessage(), task.getException());
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
                        Log.e(TAG, "שגיאה בהשלמת משימת ההתחברות: " + e.getMessage(), e);
                        Toast.makeText(adminLogin.this, "שגיאה בעיבוד נתונים: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(View.GONE);
                        loginButton.setEnabled(true);
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בניסיון ההתחברות: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בתהליך ההתחברות: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
        }
    }
    
    /**
     * שומר את פרטי סשן המנהל בהעדפות המשותפות
     * @param username שם המשתמש של המנהל
     */
    private void saveAdminSession(String username) {
        try {
            // שמירת פרטי סשן המנהל בהעדפות משותפות
            Log.d(TAG, "שומר סשן מנהל עבור: " + username);
            getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE)
                .edit()
                .putString("loggedInAdmin", username)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בשמירת סשן המנהל: " + e.getMessage(), e);
        }
    }

    public void goBack(View view) {
        onBackPressed();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "adminLogin onResume נקרא");
        
        // הפעלה מחדש של כפתור ההתחברות אם נדרש
        if (loginButton != null) {
            loginButton.setEnabled(true);
        }
        
        // הסתרת סרגל ההתקדמות אם מוצג
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }
} 