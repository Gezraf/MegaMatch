package com.project.megamatch;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * מחלקה זו אחראית על הוספת מנהלים חדשים למערכת עבור בית ספר ספציפי.
 * היא מאפשרת למנהל (אדמין) להזין פרטי מנהל (שם משתמש, תעודת זהות, סיסמה ושם מלא),
 * לבדוק אם שם המשתמש כבר קיים, ולהוסיף את המנהל לקולקציית המנהלים ב-Firestore.
 */
public class addManager extends AppCompatActivity {

    /**
     * רכיב ה-TextView המציג את שם בית הספר אליו מוסיפים את המנהל.
     */
    private TextView schoolNameTextView;
    /**
     * שדה קלט עבור שם המשתמש של המנהל.
     */
    private EditText usernameEditText;
    /**
     * שדה קלט עבור תעודת הזהות של המנהל.
     */
    private EditText idEditText;
    /**
     * שדה קלט עבור הסיסמה של המנהל.
     */
    private EditText passwordEditText;
    /**
     * שדה קלט עבור השם המלא של המנהל.
     */
    private EditText fullNameEditText;
    /**
     * כפתור הוספת המנהל.
     */
    private Button addButton;
    /**
     * סרגל התקדמות (Progress Bar) המוצג בזמן פעולות אסינכרוניות.
     */
    private ProgressBar progressBar;
    /**
     * מופע של FirebaseFirestore לגישה למסד הנתונים.
     */
    private FirebaseFirestore db;
    /**
     * מזהה בית הספר הנוכחי, שהועבר באמצעות Intent.
     */
    private String schoolId;
    /**
     * שם בית הספר הנוכחי, שהועבר באמצעות Intent.
     */
    private String schoolName;

    /**
     * נקודת הכניסה לפעילות. מאתחלת את רכיבי הממשק, מקבלת נתוני בית ספר מ-Intent,
     * מאתחלת את Firebase, ומגדירה מאזין לחיצה עבור כפתור ההוספה.
     * @param savedInstanceState אובייקט Bundle המכיל את מצב הפעילות שנשמר.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.add_manager);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addManagerLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // קבלת נתוני Intent
        Intent intent = getIntent();
        schoolId = intent.getStringExtra("schoolId");
        schoolName = intent.getStringExtra("schoolName");

        if (schoolId == null || schoolName == null) {
            Toast.makeText(this, "שגיאה: פרטי בית הספר חסרים", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // אתחול Firebase
        db = FirebaseFirestore.getInstance();

        // אתחול רכיבי ממשק המשתמש
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        usernameEditText = findViewById(R.id.usernameEditText);
        idEditText = findViewById(R.id.idEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        fullNameEditText = findViewById(R.id.fullNameEditText);
        addButton = findViewById(R.id.addButton);
        progressBar = findViewById(R.id.progressBar);

        // הגדרת שם בית הספר
        schoolNameTextView.setText(schoolName);

        // הגדרת מאזין לחיצה לכפתור הוספה
        addButton.setOnClickListener(v -> addManagerToSchool());
    }

    /**
     * מוסיפה מנהל חדש לקולקציית המנהלים ב-Firestore עבור בית הספר הנוכחי.
     * מבצעת ולידציה על שדות הקלט, בודקת אם המנהל כבר קיים,
     * ויוצרת מסמך חדש ב-Firestore עם פרטי המנהל.
     */
    private void addManagerToSchool() {
        String username = usernameEditText.getText().toString().trim();
        String id = idEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String fullName = fullNameEditText.getText().toString().trim();

        // ולידציה של קלט
        if (username.isEmpty() || id.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "יש למלא את כל השדות", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // ולידציה שאורך תעודת הזהות הוא 9 ספרות
        if (id.length() != 9 || !id.matches("\\d+")) {
            Toast.makeText(this, "תעודת זהות חייבת להיות באורך 9 ספרות", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // ולידציה שאורך הסיסמה הוא בין 3 ל-22 תווים
        if (password.length() < 3 || password.length() > 22) {
            Toast.makeText(this, "סיסמה חייבת להיות באורך 3-22 תווים", Toast.LENGTH_SHORT).show();
            return;
        }

        // הצגת סרגל התקדמות
        progressBar.setVisibility(View.VISIBLE);
        addButton.setEnabled(false);

        // בדיקה אם המנהל כבר קיים
        db.collection("schools").document(schoolId)
            .collection("managers").document(username)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    // המנהל כבר קיים
                    Toast.makeText(addManager.this, "מנהל עם שם משתמש זה כבר קיים", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    addButton.setEnabled(true);
                } else {
                    // יצירת מסמך מנהל
                    Map<String, Object> managerData = new HashMap<>();
                    managerData.put("id", id);
                    managerData.put("password", password);
                    managerData.put("fullName", fullName);
                    
                    // עיצוב התאריך הנוכחי כ-DD:MM:YYYY
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd:MM:yyyy", Locale.getDefault());
                    String formattedDate = dateFormat.format(new Date());
                    managerData.put("createdAt", formattedDate);

                    db.collection("schools").document(schoolId)
                        .collection("managers").document(username)
                        .set(managerData)
                        .addOnSuccessListener(aVoid -> {
                            // הצגת דיאלוג הצלחה במקום טוסט
                            showSuccessDialog("המנהל נוסף בהצלחה!");
                            clearFields();
                            progressBar.setVisibility(View.GONE);
                            addButton.setEnabled(true);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(addManager.this, "שגיאה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            addButton.setEnabled(true);
                        });
                }
            });
    }

    /**
     * מציג דיאלוג הצלחה מותאם אישית לאחר הוספת מנהל בהצלחה.
     * הדיאלוג כולל הודעת הצלחה, כותרת, אייקון אישור וכפתור סגירה.
     * @param message הודעת ההצלחה להצגה.
     */
    private void showSuccessDialog(String message) {
        // יצירת דיאלוג
        Dialog customDialog = new Dialog(this);
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        customDialog.setCancelable(false);
        
        // הגדרת פריסה מותאמת אישית
        customDialog.setContentView(R.layout.success_dialog);
        
        // קבלת חלון הדיאלוג כדי להגדיר פרמטרי פריסה
        Window window = customDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            // הוספת אנימציה מותאמת אישית
            window.setWindowAnimations(R.style.DialogAnimation);
        }
        
        // הגדרת הודעת ההצלחה
        TextView messageView = customDialog.findViewById(R.id.dialogMessage);
        if (messageView != null) {
            messageView.setText(message);
        }
        
        // הגדרת הכותרת
        TextView titleView = customDialog.findViewById(R.id.dialogTitle);
        if (titleView != null) {
            titleView.setText("הוספת מנהל");
        }
        
        // הגדרת אייקון ההצלחה
        ImageView iconView = customDialog.findViewById(R.id.successIcon);
        if (iconView != null) {
            // השתמש באייקון וי
            iconView.setImageResource(R.drawable.ic_checkmark);
        }
        
        // הגדרת כפתור הסגירה
        MaterialButton closeButton = customDialog.findViewById(R.id.closeButton);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                customDialog.dismiss();
            });
        }
        
        // הצגת הדיאלוג
        customDialog.show();
    }

    /**
     * מנקה את שדות הקלט במסך לאחר הוספת מנהל בהצלחה.
     */
    private void clearFields() {
        usernameEditText.setText("");
        idEditText.setText("");
        passwordEditText.setText("");
        fullNameEditText.setText("");
        usernameEditText.requestFocus();
    }

    /**
     * מטפל בלחיצה על כפתור החזרה.
     * מסיים את הפעילות הנוכחית ומחזיר למסך הקודם.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void goBack(View view) {
        onBackPressed();
    }
} 