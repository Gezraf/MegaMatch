package com.project.megamatch;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.view.MotionEvent;
import android.view.View;

import android.widget.ImageView;
import android.content.Intent;
import android.Manifest;
import android.widget.Button;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;

/**
 * מחלקה זו מייצגת את מסך ההתחברות הראשי של האפליקציה.
 * היא מאפשרת למשתמשים לנווט למסכים שונים בהתאם לתפקידם (מנהל בית ספר, רכז) או כאורחים.
 * בנוסף, מטפלת בהרשאות ובכניסת אדמין מיוחדת בלחיצה ארוכה על כפתור הייעודי.
 */
public class loginPage extends AppCompatActivity {
    /**
     * תגית המשמשת לרישום הודעות לוג (Logcat).
     */
    private static final String TAG = "LoginPage";
    /**
     * רכיב ImageView עבור תמונה כלשהי (כרגע לא בשימוש ישיר בקוד, ניתן להסרה אם לא נחוץ).
     */
    private ImageView image1;
    /**
     * כפתור מיוחד המיועד לכניסת אדמין בלחיצה ארוכה.
     */
    private Button adminButton;
    /**
     * משך הזמן הנדרש ללחיצה ארוכה על כפתור האדמין כדי להפעיל את הפונקציונליות (באלפיות השנייה).
     */
    private final long ADMIN_BUTTON_HOLD_TIME = 1000; // 1 second in milliseconds
    /**
     * אובייקט Handler לטיפול בהשהיות ובהפעלת קוד ב-UI thread.
     */
    private Handler handler = new Handler(Looper.getMainLooper());
    /**
     * אובייקט Runnable המכיל את הלוגיקה שתופעל לאחר לחיצה ארוכה על כפתור האדמין.
     */
    private Runnable adminButtonRunnable;
    /**
     * דגל המציין אם כפתור האדמין נלחץ והוחזק.
     */
    private boolean isButtonHeld = false;

    /**
     * נקודת הכניסה לפעילות. מאתחלת את רכיבי הממשק, בודקת אם קיים סשן אדמין שמור,
     * מטפלת בהרשאות (כמו SEND_SMS) ומגדירה את מאזיני הלחיצה עבור כפתורי הניווט וכפתור האדמין.
     * @param savedInstanceState אובייקט Bundle המכיל את מצב הפעילות שנשמר.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // בדיקה אם קיימת כניסת אדמין קיימת
        SharedPreferences sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        String savedAdminUsername = sharedPreferences.getString("loggedInAdmin", "");
        
        if (!savedAdminUsername.isEmpty()) {
            Log.d(TAG, "נמצא סשן אדמין שמור, מנתב לפעילות טעינת אדמין");
            Intent intent = new Intent(loginPage.this, AdminLoadingActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.login_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginPage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // בדיקת הרשאת שליחת SMS ובקשתה אם אינה קיימת
        if (!checkPermission(Manifest.permission.SEND_SMS)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, 1);
        }
        
        // אתחול כפתור האדמין
        adminButton = findViewById(R.id.adminButton);
        
        // הגדרת מאזין מגע לכפתור האדמין (לחיצה ארוכה)
        adminButtonRunnable = () -> {
            if (isButtonHeld) {
                // הצגת אישור קצר וניווט למסך האדמין
                adminButton.setAlpha(1.0f);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(loginPage.this, adminPage.class);
                    startActivity(intent);
                }, 300);
            }
        };
        
        adminButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isButtonHeld = true;
                    handler.postDelayed(adminButtonRunnable, ADMIN_BUTTON_HOLD_TIME);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isButtonHeld = false;
                    handler.removeCallbacks(adminButtonRunnable);
                    return true;
            }
            return false;
        });
    }


    /**
     * מטפל בלחיצה על כפתור המעבר למסך בחירת בית הספר.
     * מנווט את המשתמש למסך `schoolSelect`.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToSchoolSelect(View view)
    {
        Intent intent = new Intent(loginPage.this, schoolSelect.class);
        startActivity(intent);
    }

    /**
     * מטפל בלחיצה על כפתור המעבר למסך התחברות רכז.
     * מנווט את המשתמש למסך `rakazLogin`.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToRakazLogin(View view)
    {
        Intent i1 = new Intent(this, rakazLogin.class);
        startActivity(i1);
    }


    /**
     * מטפל בלחיצה על כפתור המעבר למסך העזרה.
     * מנווט את המשתמש למסך `helpPage`.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToHelp(View view)
    {
        Intent i1 = new Intent(this, helpPage.class);
        startActivity(i1);
    }

    /**
     * מטפל בלחיצה על כפתור המעבר למסך הקרדיטים.
     * מנווט את המשתמש למסך `creditsPage`.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void moveToCredits(View view)
    {
        Intent i1 = new Intent(this, creditsPage.class);
        startActivity(i1);
    }


    /**
     * בודק אם הרשאה ספציפית ניתנה לאפליקציה.
     * @param permission המחרוזת המייצגת את ההרשאה לבדיקה (לדוגמה: Manifest.permission.SEND_SMS).
     * @return true אם ההרשאה ניתנה, false אחרת.
     */
    public boolean checkPermission(String permission)
    {
        int check = ContextCompat.checkSelfPermission(this, permission);
        return (check == PackageManager.PERMISSION_GRANTED);
    }
}