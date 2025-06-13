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

/**
 * מחלקה זו מייצגת את מסך מרכז הבקרה של האדמין (Admin Hub).
 * היא מאפשרת לאדמין לנווט בין פונקציות ניהוליות שונות כגון הוספת בתי ספר,
 * ניהול בתי ספר קיימים, גישה לדפי עזרה וקרדיטים, והתנתקות מהמערכת.
 */
public class adminHub extends AppCompatActivity {
    /**
     * תגית המשמשת לרישום הודעות לוג (Logcat).
     */
    private static final String TAG = "AdminHub";

    /**
     * נקודת הכניסה לפעילות. מאתחלת את רכיבי הממשק ומטפלת בהגדרות תצוגה (insets).
     * כוללת מנגנון לטיפול בשגיאות קריטיות במהלך יצירת הפעילות.
     * @param savedInstanceState אובייקט Bundle המכיל את מצב הפעילות שנשמר.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Log.d(TAG, "מתחיל את onCreate עבור adminHub");
            EdgeToEdge.enable(this);
            setContentView(R.layout.admin_hub);
            
            Log.d(TAG, "setContentView הושלם בהצלחה");
            
            // הגדרת insets של החלון עם טיפול בשגיאות
            try {
                View rootView = findViewById(R.id.adminHubLayout);
                if (rootView != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                        return insets;
                    });
                    Log.d(TAG, "insets של החלון הוגדרו בהצלחה");
                } else {
                    Log.e(TAG, "Root view (adminHubLayout) לא נמצא!");
                }
            } catch (Exception e) {
                Log.e(TAG, "שגיאה בהגדרת insets של החלון: " + e.getMessage(), e);
                // המשך עם הפעילות גם אם ה-insets נכשלים
            }
            
            Log.d(TAG, "adminHub onCreate הושלם בהצלחה");
        } catch (Exception e) {
            Log.e(TAG, "שגיאה קטלנית ב-adminHub onCreate: " + e.getMessage(), e);
            // הצגת הודעת שגיאה וניתוב מחדש למסך ההתחברות
            try {
                Toast.makeText(this, "שגיאה בטעינת הדף: " + e.getMessage(), Toast.LENGTH_LONG).show();
                goToLoginPage();
            } catch (Exception ex) {
                // מוצא אחרון, פשוט סיים את הפעילות
                Log.e(TAG, "טיפול בשגיאות נכשל: " + ex.getMessage(), ex);
                finish();
            }
        }
    }

    /**
     * מטפל בלחיצה על כפתור המעבר למסך הוספת בית ספר.
     * מנווט את האדמין למסך `addSchool`.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToAddSchool(View view) {
        try {
            Intent intent = new Intent(adminHub.this, addSchool.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בניווט ל-addSchool: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * מטפל בלחיצה על כפתור המעבר למסך בחירת בית ספר.
     * מנווט את האדמין למסך `schoolSelect` עם דגל המציין כניסת אדמין.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToSchoolSelect(View view) {
        try {
            Intent intent = new Intent(adminHub.this, schoolSelect.class);
            // העבר דגל המציין שכניסה זו היא של אדמין למסך בחירת בית ספר
            intent.putExtra("isAdmin", true);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בניווט ל-schoolSelect: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * מטפל בלחיצה על כפתור המעבר למסך העזרה.
     * מנווט את האדמין למסך `helpPage`.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToHelp(View view) {
        try {
            Intent i1 = new Intent(this, helpPage.class);
            startActivity(i1);
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בניווט ל-helpPage: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * מטפל בלחיצה על כפתור המעבר למסך הקרדיטים.
     * מנווט את האדמין למסך `creditsPage`.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToCredits(View view) {
        try {
            Intent i1 = new Intent(this, creditsPage.class);
            startActivity(i1);
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בניווט ל-creditsPage: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בניתוב: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * מטפל בלחיצה על כפתור ההתנתקות.
     * מנקה את סשן האדמין השמור ומנווט למסך ההתחברות.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void logout(View view) {
        try {
            // חזרה למסך ההתחברות
            goToLoginPage();
        } catch (Exception e) {
            Log.e(TAG, "שגיאה במהלך התנתקות: " + e.getMessage(), e);
            Toast.makeText(this, "שגיאה בהתנתקות: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish(); // סיים בכוח כמוצא אחרון
        }
    }
    
    /**
     * מנווט את המשתמש למסך ההתחברות (`loginPage`) ומנקה את סשן האדמין השמור.
     * מנקה את מחסנית הפעילויות כדי למנוע חזרה למסכי אדמין לאחר התנתקות.
     */
    private void goToLoginPage() {
        // נקה כל סשן אדמין
        getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE)
            .edit()
            .remove("loggedInAdmin")
            .apply();
            
        Intent intent = new Intent(adminHub.this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * נקרא כאשר הפעילות חוזרת לפעולה לאחר שהייתה מושהית.
     * משמש לרישום לוגים בלבד במחלקה זו.
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "adminHub onResume נקרא");
    }
} 