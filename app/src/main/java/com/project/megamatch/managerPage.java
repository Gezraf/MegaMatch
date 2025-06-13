package com.project.megamatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * מחלקה זו מייצגת את דף הבית של המנהל.
 * היא מציגה את שם בית הספר ושם המנהל, ומספקת אפשרויות ניווט לניהול רכזים והתנתקות.
 */
public class managerPage extends AppCompatActivity {

    private static final String TAG = "ManagerPage";
    
    private TextView schoolNameTextView;
    private TextView managerNameTextView;
    private SharedPreferences sharedPreferences;
    private FirebaseFirestore db;
    
    private String schoolId;
    private String username;
    private String managerFullName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.manager_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.managerPageLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // אתחול רכיבי הממשק
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        managerNameTextView = findViewById(R.id.managerNameTextView);

        // אתחול Firestore
        db = FirebaseFirestore.getInstance();

        // קבלת פרטי המנהל השמורים
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        schoolId = sharedPreferences.getString("loggedInManagerSchoolId", "");
        username = sharedPreferences.getString("loggedInManagerUsername", "");

        // אם לא מחובר, הפנה לדף ההתחברות
        if (schoolId.isEmpty() || username.isEmpty()) {
            Intent intent = new Intent(managerPage.this, managerLogin.class);
            startActivity(intent);
            finish();
            return;
        }

        // טעינת פרטי המנהל
        loadManagerDetails();
    }

    /**
     * טוען את פרטי המנהל מפיירסטור ומעדכן את הממשק.
     */
    private void loadManagerDetails() {
        // טעינת פרטי המנהל מפיירסטור
        db.collection("schools").document(schoolId)
            .collection("managers").document(username)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // קבלת פרטי המנהל
                        managerFullName = document.getString("fullName");
                        
                        // עדכון ממשק המשתמש
                        runOnUiThread(() -> {
                            managerNameTextView.setText(managerFullName);
                            
                            // טעינת שם בית הספר
                            loadSchoolName();
                        });
                    } else {
                        Log.w(TAG, "מסמך המנהל לא קיים!");
                        showError("לא נמצאו פרטי מנהל");
                    }
                } else {
                    Log.e(TAG, "שגיאה בטעינת פרטי מנהל", task.getException());
                    showError("שגיאה בטעינת פרטי מנהל");
                }
            });
    }
    
    /**
     * טוען את שם בית הספר מה-CSV או מפיירסטור.
     */
    private void loadSchoolName() {
        // נסה למצוא בית ספר במסד הנתונים CSV תחילה
        for (schoolsDB.School school : schoolsDB.getAllSchools()) {
            if (String.valueOf(school.getSchoolId()).equals(schoolId)) {
                schoolNameTextView.setText(school.getSchoolName());
                return;
            }
        }
        
        // אם לא נמצא ב-CSV, נסה לקבל אותו מפיירסטור
        db.collection("schools").document(schoolId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // בדוק אם לבית הספר יש שדה "name"
                        String name = document.getString("name");
                        
                        if (name != null && !name.isEmpty()) {
                            schoolNameTextView.setText(name);
                        } else {
                            // השתמש במחזיק מקום עם המזהה
                            schoolNameTextView.setText("בית ספר " + schoolId);
                        }
                    } else {
                        schoolNameTextView.setText("בית ספר " + schoolId);
                    }
                } else {
                    schoolNameTextView.setText("בית ספר " + schoolId);
                }
            });
    }
    
    /**
     * מציג הודעת שגיאה ב-Toast.
     * @param message הודעת השגיאה להצגה.
     */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * מעביר למסך ניהול רכזים.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void manageRakazim(View view) {
        Intent intent = new Intent(managerPage.this, manageRakaz.class);
        intent.putExtra("schoolId", schoolId);
        startActivity(intent);
    }

    /**
     * מוחק את סשן המנהל בהעדפות המשותפות ומנתק את המשתמש.
     * מעביר לדף ההתחברות.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void logout(View view) {
        // ניקוי סשן המנהל
        sharedPreferences.edit()
            .remove("loggedInManagerSchoolId")
            .remove("loggedInManagerUsername")
            .apply();

        // הפניה לדף ההתחברות
        Intent intent = new Intent(managerPage.this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 