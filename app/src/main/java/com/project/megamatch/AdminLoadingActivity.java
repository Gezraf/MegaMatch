package com.project.megamatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

/**
 * מחלקה זו מייצגת את מסך הטעינה של מנהל המערכת.
 * היא מבצעת טעינת נתונים מפיירבייס ומציגה מסך טעינה למשך זמן מינימלי.
 */
public class AdminLoadingActivity extends AppCompatActivity {

    private static final String TAG = "AdminLoadingActivity";
    private static final int MIN_LOADING_TIME = 1000; // זמן מינימלי להצגת מסך הטעינה (באלפיות השנייה)
    
    private TextView loadingText;
    private FirebaseFirestore fireDB;
    private String adminUsername;
    private long startTime;
    private boolean dataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        // אתחול רכיבי הממשק
        loadingText = findViewById(R.id.loadingText);
        loadingText.setText("טוען נתוני מנהל מערכת...");
        
        // אתחול פיירבייס
        fireDB = FirebaseFirestore.getInstance();
        
        // קבלת פרטי מנהל מהעדפות משותפות
        SharedPreferences sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        adminUsername = sharedPreferences.getString("loggedInAdmin", "");
        
        // בדיקת תקינות פרטי ההתחברות
        if (adminUsername.isEmpty()) {
            goToLoginScreen();
            return;
        }
        
        // רישום זמן התחלה
        startTime = System.currentTimeMillis();
        
        // התחלת טעינת נתונים
        loadAdminData();
    }
    
    /**
     * טוען את נתוני המנהל מפיירבייס
     */
    private void loadAdminData() {
        // טעינת נתוני מנהל מפיירבייס
        fireDB.collection("admins").document(adminUsername)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists() && Boolean.TRUE.equals(documentSnapshot.getBoolean("admin"))) {
                      Log.d(TAG, "נתוני המנהל נטענו בהצלחה");
                      
                      // סימון הנתונים כנטענו
                      dataLoaded = true;
                      
                      // בדיקה אם חלף זמן הטעינה המינימלי
                      checkNavigationConditions();
                  } else {
                      // מסמך המנהל לא קיים או המשתמש אינו מנהל
                      Log.e(TAG, "מסמך המנהל לא נמצא או המשתמש אינו מנהל");
                      goToLoginScreen();
                  }
              })
              .addOnFailureListener(e -> {
                  // שגיאה בטעינת נתוני המנהל
                  Log.e(TAG, "שגיאה בטעינת נתוני המנהל: " + e.getMessage());
                  goToLoginScreen();
              });
    }
    
    /**
     * בודק את תנאי הניווט ומחליט אם לעבור למסך הבא
     */
    private void checkNavigationConditions() {
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        if (elapsedTime >= MIN_LOADING_TIME && dataLoaded) {
            // שני התנאים התקיימו, המשך למרכז הבקרה
            goToAdminHub();
        } else {
            // המתנה לזמן הנותר
            long remainingTime = Math.max(0, MIN_LOADING_TIME - elapsedTime);
            new Handler().postDelayed(this::goToAdminHub, remainingTime);
        }
    }
    
    /**
     * מעבר למרכז הבקרה של המנהל
     */
    private void goToAdminHub() {
        Intent intent = new Intent(this, adminHub.class);
        startActivity(intent);
        finish();
    }
    
    /**
     * מעבר למסך ההתחברות וניקוי סשן המנהל
     */
    private void goToLoginScreen() {
        // ניקוי סשן המנהל
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