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
 * מחלקה זו מייצגת מסך טעינה (Loading Screen) המשמש להצגת אנימציית טעינה
 * ולביצוע פעולות אתחול לפני המעבר למסך הראשי של רכז.
 * היא בודקת פרטי התחברות שמורים ומטעינה נתוני משתמש מ-Firestore.
 */
public class LoadingActivity extends AppCompatActivity {

    /**
     * תגית המשמשת לרישום הודעות לוג (Logcat).
     */
    private static final String TAG = "LoadingActivity";
    /**
     * משך הזמן המינימלי שמסך הטעינה יוצג (באלפיות השנייה).
     */
    private static final int MIN_LOADING_TIME = 1000; 
    
    /**
     * רכיב ה-TextView המציג הודעת טעינה.
     */
    private TextView loadingText;
    /**
     * מופע של FirebaseFirestore לגישה למסד הנתונים.
     */
    private FirebaseFirestore fireDB;
    /**
     * מזהה בית הספר של המשתמש המחובר.
     */
    private String schoolId;
    /**
     * שם המשתמש של המשתמש המחובר.
     */
    private String username;
    /**
     * זמן תחילת הטעינה, משמש לחישוב משך זמן הטעינה המינימלי.
     */
    private long startTime;
    /**
     * דגל המציין אם נתוני המשתמש נטענו בהצלחה.
     */
    private boolean dataLoaded = false;

    /**
     * נקודת הכניסה לפעילות. מאתחלת את רכיבי הממשק, Firestore, ומחזירה פרטי משתמש שמורים.
     * אם הפרטים תקפים, מתחילה בטעינת נתוני המשתמש.
     * @param savedInstanceState אובייקט Bundle המכיל את מצב הפעילות שנשמר.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        // אתחול רכיבי ממשק
        loadingText = findViewById(R.id.loadingText);
        
        // אתחול Firestore
        fireDB = FirebaseFirestore.getInstance();
        
        // קבלת פרטי משתמש מהעדפות משותפות (Shared Preferences)
        SharedPreferences sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        schoolId = sharedPreferences.getString("loggedInSchoolId", "");
        username = sharedPreferences.getString("loggedInUsername", "");
        
        // ולידציה של פרטי הכניסה
        if (schoolId.isEmpty() || username.isEmpty()) {
            goToLoginScreen();
            return;
        }
        
        // רישום זמן התחלה
        startTime = System.currentTimeMillis();
        
        // התחלת טעינת נתונים
        loadUserData();
    }
    
    /**
     * טוענת את נתוני המשתמש (רכז) מ-Firestore.
     * לאחר טעינה מוצלחת, בודקת את תנאי הניווט למסך הראשי.
     * במקרה של כשל בטעינה או אם המסמך לא קיים, מנווטת למסך ההתחברות.
     */
    private void loadUserData() {
        // טעינת נתוני משתמש מ-Firestore
        fireDB.collection("schools").document(schoolId)
              .collection("rakazim").document(username)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      Log.d(TAG, "נתוני משתמש נטענו בהצלחה");
                      
                      // סמן את הנתונים כנטענו
                      dataLoaded = true;
                      
                      // בדוק אם עבר זמן הטעינה המינימלי
                      checkNavigationConditions();
                  } else {
                      // מסמך משתמש לא קיים
                      Log.e(TAG, "מסמך משתמש לא נמצא");
                      goToLoginScreen();
                  }
              })
              .addOnFailureListener(e -> {
                  // שגיאה בטעינת נתוני משתמש
                  Log.e(TAG, "שגיאה בטעינת נתוני משתמש: " + e.getMessage());
                  goToLoginScreen();
              });
    }
    
    /**
     * בודקת את התנאים לניווט למסך הראשי: האם הנתונים נטענו והאם עבר זמן הטעינה המינימלי.
     * אם התנאים מתקיימים, מנווטת מיד; אחרת, ממתינה את הזמן הנותר.
     */
    private void checkNavigationConditions() {
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        if (elapsedTime >= MIN_LOADING_TIME && dataLoaded) {
            // שני התנאים התקיימו, המשך למסך הראשי
            goToMainScreen();
        } else {
            // המתן את הזמן שנותר
            long remainingTime = Math.max(0, MIN_LOADING_TIME - elapsedTime);
            new Handler().postDelayed(this::goToMainScreen, remainingTime);
        }
    }
    
    /**
     * מנווטת את המשתמש למסך הראשי של הרכז (`rakazPage`) ומסיימת את הפעילות הנוכחית.
     */
    private void goToMainScreen() {
        Intent intent = new Intent(this, rakazPage.class);
        startActivity(intent);
        finish();
    }
    
    /**
     * מנווטת את המשתמש למסך ההתחברות (`loginPage`) ומנקה את מחסנית הפעילויות.
     * משמשת במקרים של כשל בטעינת נתונים או פרטי כניסה לא תקפים.
     */
    private void goToLoginScreen() {
        Intent intent = new Intent(this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 