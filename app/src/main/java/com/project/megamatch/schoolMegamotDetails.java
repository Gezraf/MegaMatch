package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

/**
 * פעילות להצגת פרטי מגמות של בית ספר
 * מסך זה מציג את כל המגמות של בית הספר הנבחר
 */
public class schoolMegamotDetails extends AppCompatActivity {

    private TextView schoolTitleText;
    private Button backButton;
    private RecyclerView megamotRecyclerView;
    private SharedPreferences sharedPreferences;
    private String schoolId;
    private static final String TAG = "SchoolMegamotDetails";

    /**
     * נקרא בעת יצירת הפעילות
     * מאתחל את הממשק, טוען נתוני בית הספר ומגדיר את הרכיבים
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.school_megamot_details);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.schoolMegamotDetailsLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // אתחול הרכיבים
        schoolTitleText = findViewById(R.id.schoolTitleText);
        backButton = findViewById(R.id.backButton);
        megamotRecyclerView = findViewById(R.id.megamotRecyclerView);
        
        // פתיחת העדפות המשתמש
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        
        // קבלת נתוני בית הספר
        schoolId = sharedPreferences.getString("loggedInSchoolId", "");
        
        // בדיקת תקינות נתונים
        if (schoolId.isEmpty()) {
            goToLoginScreen();
            return;
        }
        
        // הצגת שם בית הספר
        if (schoolId.length() == 6) {
            int id = Integer.parseInt(schoolId);
            schoolsDB.School school = schoolsDB.getSchoolById(id);
            if (school != null) {
                schoolTitleText.setText("מגמות " + school.getSchoolName());
            }
        }
        
        // הגדרת RecyclerView
        megamotRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // TODO: אתחול אדפטר למגמות
        
        // עדכון הגדרת כפתור חזרה
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBackToSchoolSelect();
            }
        });
        
        // טעינת מגמות בית הספר
        loadSchoolMegamot();
    }
    
    /**
     * טוען את כל המגמות של בית הספר מהשרת
     */
    private void loadSchoolMegamot() {
        // TODO: טעינת נתוני מגמות מפיירבייס
        Log.d(TAG, "Loading all megamot for school: " + schoolId);
    }
    
    /**
     * חזרה למסך בחירת בית ספר
     */
    private void goBackToSchoolSelect() {
        Log.d(TAG, "Returning to school selection screen");
        // Just finish this activity to return to the previous schoolSelect activity in the stack
        finish();
    }
    
    /**
     * התנתקות מהמערכת ומחיקת נתוני התחברות
     */
    private void logout() {
        // ניקוי כל נתוני החיבור
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        
        // חזרה למסך כניסה
        goToLoginScreen();
    }
    
    /**
     * מעבר למסך כניסה
     */
    private void goToLoginScreen() {
        Intent intent = new Intent(this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * טיפול בלחיצה על כפתור חזרה של המערכת
     */
    @Override
    public void onBackPressed() {
        goBackToSchoolSelect();
    }
} 