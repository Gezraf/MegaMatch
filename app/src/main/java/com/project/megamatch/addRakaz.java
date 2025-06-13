package com.project.megamatch;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * מחלקה זו אחראית על הוספת רכזים חדשים למערכת עבור בית ספר ספציפי.
 * היא מאפשרת למנהל להזין פרטי רכז (שם פרטי, שם משפחה, תעודת זהות, מגמה ודוא\"ל),
 * לבדוק אם הדוא\"ל כבר קיים, ולהוסיף את הרכז לרשימת הרכזים המורשים במסד הנתונים של Firestore.
 */
public class addRakaz extends AppCompatActivity {

    /**
     * תגית המשמשת לרישום הודעות לוג (Logcat).
     */
    private static final String TAG = "AddRakaz";
    
    /**
     * רכיב ה-TextView המציג את שם בית הספר אליו מוסיפים את הרכז.
     */
    private TextView schoolNameTextView;
    /**
     * רכיב ה-TextInputLayout עבור שדה השם הפרטי.
     */
    private TextInputLayout firstNameInputLayout;
    /**
     * רכיב ה-TextInputEditText עבור קלט השם הפרטי.
     */
    private TextInputEditText firstNameInput;
    /**
     * רכיב ה-TextInputLayout עבור שדה שם המשפחה.
     */
    private TextInputLayout lastNameInputLayout;
    /**
     * רכיב ה-TextInputEditText עבור קלט שם המשפחה.
     */
    private TextInputEditText lastNameInput;
    /**
     * רכיב ה-TextInputLayout עבור שדה תעודת הזהות.
     */
    private TextInputLayout idInputLayout;
    /**
     * רכיב ה-TextInputEditText עבור קלט תעודת הזהות.
     */
    private TextInputEditText idInput;
    /**
     * רכיב ה-TextInputLayout עבור שדה המגמה.
     */
    private TextInputLayout megamaInputLayout;
    /**
     * רכיב ה-TextInputEditText עבור קלט המגמה.
     */
    private TextInputEditText megamaInput;
    /**
     * רכיב ה-TextInputLayout עבור שדה הדוא\"ל.
     */
    private TextInputLayout emailInputLayout;
    /**
     * רכיב ה-TextInputEditText עבור קלט הדוא\"ל.
     */
    private TextInputEditText emailInput;
    /**
     * כפתור הוספת הרכז.
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
     * נקודת הכניסה לפעילות. מאתחלת את רכיבי הממשק, טוענת את שם בית הספר,
     * ומגדירה מאזיני טקסט לולידציה של שדות הקלט.
     * @param savedInstanceState אובייקט Bundle המכיל את מצב הפעילות שנשמר.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rakaz_add);
        
        // קבלת נתוני Intent
        schoolId = getIntent().getStringExtra("schoolId");
        if (schoolId == null || schoolId.isEmpty()) {
            Toast.makeText(this, "שגיאה: לא התקבל מזהה בית ספר", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // אתחול רכיבי ממשק המשתמש
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        firstNameInputLayout = findViewById(R.id.firstNameInputLayout);
        firstNameInput = findViewById(R.id.firstNameInput);
        lastNameInputLayout = findViewById(R.id.lastNameInputLayout);
        lastNameInput = findViewById(R.id.lastNameInput);
        idInputLayout = findViewById(R.id.idInputLayout);
        idInput = findViewById(R.id.idInput);
        megamaInputLayout = findViewById(R.id.megamaInputLayout);
        megamaInput = findViewById(R.id.megamaInput);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        emailInput = findViewById(R.id.emailInput);
        addButton = findViewById(R.id.addButton);
        progressBar = findViewById(R.id.progressBar);
        
        // אתחול Firestore
        db = FirebaseFirestore.getInstance();
        
        // טעינת שם בית הספר
        loadSchoolName();
        
        // הגדרת מאזיני טקסט לולידציה של כל השדות
        firstNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                validateFirstName();
            }
        });
        
        lastNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                validateLastName();
            }
        });
        
        idInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                validateId();
            }
        });
        
        megamaInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                validateMegama();
            }
        });
        
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                validateEmail();
            }
        });
    }
    
    /**
     * טוענת את שם בית הספר ממסד הנתונים (CSV או Firestore) ומציגה אותו ב-TextView המתאים.
     * קודם כל מנסה לאתר את בית הספר בקובץ ה-CSV, ואם לא נמצא, מנסה ב-Firestore.
     */
    private void loadSchoolName() {
        // נסה למצוא בית ספר במסד נתוני ה-CSV תחילה
        for (schoolsDB.School school : schoolsDB.getAllSchools()) {
            if (String.valueOf(school.getSchoolId()).equals(schoolId)) {
                schoolNameTextView.setText(school.getSchoolName());
                return;
            }
        }
        
        // אם לא נמצא ב-CSV, נסה לקבל אותו מ-Firestore
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
     * מבצע ולידציה על שדה השם הפרטי.
     * @return true אם השם הפרטי תקין (אינו ריק), false אחרת.
     */
    private boolean validateFirstName() {
        String firstName = firstNameInput.getText().toString().trim();
        
        if (firstName.isEmpty()) {
            firstNameInputLayout.setError("יש להזין שם פרטי");
            return false;
        } else {
            firstNameInputLayout.setError(null);
            return true;
        }
    }
    
    /**
     * מבצע ולידציה על שדה שם המשפחה.
     * @return true אם שם המשפחה תקין (אינו ריק), false אחרת.
     */
    private boolean validateLastName() {
        String lastName = lastNameInput.getText().toString().trim();
        
        if (lastName.isEmpty()) {
            lastNameInputLayout.setError("יש להזין שם משפחה");
            return false;
        } else {
            lastNameInputLayout.setError(null);
            return true;
        }
    }
    
    /**
     * מבצע ולידציה על שדה תעודת הזהות.
     * @return true אם תעודת הזהות תקינה (אינה ריקה ובאורך 9 ספרות), false אחרת.
     */
    private boolean validateId() {
        String id = idInput.getText().toString().trim();
        
        if (id.isEmpty()) {
            idInputLayout.setError("יש להזין תעודת זהות");
            return false;
        } else if (id.length() != 9) {
            idInputLayout.setError("תעודת זהות חייבת להיות 9 ספרות");
            return false;
        } else {
            idInputLayout.setError(null);
            return true;
        }
    }
    
    /**
     * מבצע ולידציה על שדה המגמה.
     * @return true אם המגמה תקינה (אינה ריקה), false אחרת.
     */
    private boolean validateMegama() {
        String megama = megamaInput.getText().toString().trim();
        
        if (megama.isEmpty()) {
            megamaInputLayout.setError("יש להזין מגמה");
            return false;
        } else {
            megamaInputLayout.setError(null);
            return true;
        }
    }
    
    /**
     * מבצע ולידציה על שדה הדוא\"ל.
     * @return true אם הדוא\"ל תקין (אינו ריק ובתבנית חוקית), false אחרת.
     */
    private boolean validateEmail() {
        String email = emailInput.getText().toString().trim();
        
        if (email.isEmpty()) {
            emailInputLayout.setError("יש להזין דוא\\\"ל");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError("יש להזין דוא\\\"ל תקין");
            return false;
        } else {
            emailInputLayout.setError(null);
            return true;
        }
    }
    
    /**
     * מטפל בלחיצה על כפתור הוספת הרכז.
     * מבצע ולידציה על כל שדות הקלט, ובמידה ותקינים, בודק אם הדוא\"ל כבר קיים ב-Firestore.
     * אם הדוא\"ל אינו קיים, הוא מוסיף את הרכז לרשימת הרכזים המורשים.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void addRakaz(View view) {
        // ולידציה של כל השדות
        boolean isFirstNameValid = validateFirstName();
        boolean isLastNameValid = validateLastName();
        boolean isIdValid = validateId();
        boolean isMegamaValid = validateMegama();
        boolean isEmailValid = validateEmail();
        
        if (!isFirstNameValid || !isLastNameValid || !isIdValid || !isMegamaValid || !isEmailValid) {
            return;
        }
        
        String firstName = firstNameInput.getText().toString().trim();
        String lastName = lastNameInput.getText().toString().trim();
        String id = idInput.getText().toString().trim();
        String megama = megamaInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        
        // הצגת סרגל התקדמות
        progressBar.setVisibility(View.VISIBLE);
        addButton.setEnabled(false);
        
        // בדיקה אם הדוא\"ל כבר קיים
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails").document(email)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // הדוא\"ל כבר קיים
                        progressBar.setVisibility(View.GONE);
                        addButton.setEnabled(true);
                        Toast.makeText(addRakaz.this, "דוא\\\"ל זה כבר קיים במערכת", Toast.LENGTH_SHORT).show();
                    } else {
                        // הוספת דוא\"ל חדש
                        addEmailToAllowedList(email);
                    }
                } else {
                    // שגיאה בבדיקה
                    progressBar.setVisibility(View.GONE);
                    addButton.setEnabled(true);
                    Toast.makeText(addRakaz.this, "שגיאה בבדיקת דוא\\\"ל", Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    /**
     * מוסיפה את כתובת הדוא\"ל של הרכז החדש לרשימת הרכזים המורשים ב-Firestore.
     * יוצרת מסמך חדש בקולקציית "allowedRakazEmails" עם הפרטים שהוזנו.
     * @param email כתובת הדוא\"ל של הרכז להוספה.
     */
    private void addEmailToAllowedList(String email) {
        // יצירת נתוני מסמך עם כל השדות בסדר שצוין
        Map<String, Object> data = new HashMap<>();
        
        // עיצוב התאריך הנוכחי כ-DD:MM:YYYY לעקביות עם שדות תאריך אחרים
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd:MM:yyyy", java.util.Locale.getDefault());
        String formattedDate = dateFormat.format(new java.util.Date());
        
        // הוספת שדות בסדר המבוקש
        data.put("addedAt", formattedDate);
        data.put("approved", true);
        data.put("registered", false);
        data.put("id", idInput.getText().toString().trim());
        data.put("firstName", firstNameInput.getText().toString().trim());
        data.put("lastName", lastNameInput.getText().toString().trim());
        data.put("megama", megamaInput.getText().toString().trim());
        
        // הוספת דוא\"ל לקולקציית allowedRakazEmails
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails").document(email)
            .set(data)
            .addOnSuccessListener(aVoid -> {
                progressBar.setVisibility(View.GONE);
                addButton.setEnabled(true);
                
                // הצגת דיאלוג הצלחה במקום טוסט
                showSuccessDialog("רכז נוסף בהצלחה!");
                
                // ניקוי כל שדות הקלט
                firstNameInput.setText("");
                lastNameInput.setText("");
                idInput.setText("");
                megamaInput.setText("");
                emailInput.setText("");
            })
            .addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                addButton.setEnabled(true);
                Log.e(TAG, "Error adding email", e);
                Toast.makeText(addRakaz.this, "שגיאה בהוספת דוא\\\"ל: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    /**
     * מציג דיאלוג הצלחה מותאם אישית לאחר הוספת רכז בהצלחה.
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
            titleView.setText("הוספת רכז");
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
     * מטפל בלחיצה על כפתור החזרה.
     * מסיים את הפעילות הנוכחית ומחזיר למסך הקודם.
     * @param view אובייקט ה-View של הכפתור שנלחץ.
     */
    public void goBack(View view) {
        onBackPressed();
    }
} 