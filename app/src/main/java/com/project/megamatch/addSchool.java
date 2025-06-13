package com.project.megamatch;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * מחלקה זו מאפשרת למשתמש להוסיף בתי ספר חדשים למערכת.
 * היא משתמשת בקובץ `schools.csv` ובמסד הנתונים של Firestore כדי לאמת את פרטי בית הספר,
 * ומוסיפה את בית הספר כקולקציה חדשה ב-Firestore, יחד עם קולקציות משנה נדרשות.
 */
public class addSchool extends AppCompatActivity {

    /**
     * תגית המשמשת לרישום הודעות לוג (Logcat).
     */
    private static final String TAG = "AddSchool";
    /**
     * שדה קלט עבור סמל המוסד או שם בית הספר.
     */
    private EditText schoolIdEditText;
    /**
     * רכיב ה-TextView המציג מידע כללי או הודעות למשתמש.
     */
    private TextView schoolInfoTextView;
    /**
     * רכיב ה-TextView המציג את שם בית הספר שנמצא.
     */
    private TextView schoolNameTextView;
    /**
     * רכיב ה-TextView המציג את עיר בית הספר שנמצא.
     */
    private TextView townTextView;
    /**
     * כרטיס המכיל את פרטי בית הספר שנמצא (שם ועיר).
     */
    private MaterialCardView schoolNameCard;
    /**
     * כפתור הוספת בית הספר.
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
     * רשימת כל בתי הספר שנטענו מקובץ ה-CSV.
     */
    private List<schoolsDB.School> allSchools;

    /**
     * נקודת הכניסה לפעילות. מאתחלת את רכיבי הממשק, טוענת את רשימת בתי הספר מה-CSV,
     * ומגדירה מאזין טקסט לשדה קלט מזהה בית הספר.
     * @param savedInstanceState אובייקט Bundle המכיל את מצב הפעילות שנשמר.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.add_school);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addSchoolLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // אתחול Firebase
        db = FirebaseFirestore.getInstance();

        // טעינת בתי ספר מקובץ ה-CSV
        schoolsDB.loadSchoolsFromCSV(this);
        allSchools = schoolsDB.getAllSchools();
        Log.d(TAG, "Loaded " + allSchools.size() + " schools from CSV");

        // אתחול רכיבי ממשק המשתמש
        schoolIdEditText = findViewById(R.id.schoolIdEditText);
        schoolInfoTextView = findViewById(R.id.schoolInfoTextView);
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        townTextView = findViewById(R.id.townTextView);
        schoolNameCard = findViewById(R.id.schoolNameCard);
        addButton = findViewById(R.id.addButton);
        progressBar = findViewById(R.id.progressBar);

        // הגדרת מאזין טקסט לשדה קלט מזהה בית הספר
        schoolIdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // לא בשימוש
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // לא בשימוש
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSchoolInfo(s.toString());
            }
        });

        addButton.setOnClickListener(v -> addSchoolToFirestore());
    }

    /**
     * מעדכנת את פרטי בית הספר המוצגים על המסך בהתאם לקלט המשתמש (סמל מוסד או שם בית ספר).
     * @param input הקלט של המשתמש (סמל מוסד או שם בית ספר).
     */
    private void updateSchoolInfo(String input) {
        // הסתר את הכרטיס כברירת מחדל
        schoolNameCard.setVisibility(View.GONE);
        
        // הצג טקסט הוראות כאשר השדה ריק
        if (input.isEmpty()) {
            schoolInfoTextView.setText("מידע אודות בית הספר יתמלא אוטומטית לפי סמל המוסד או שם בית הספר");
            schoolInfoTextView.setVisibility(View.VISIBLE);
            return;
        }
        
        // נסה למצוא בית ספר לפי מזהה תחילה
        if (input.matches("\\d+")) {
            try {
                int schoolId = Integer.parseInt(input);
                schoolsDB.School foundSchool = findSchoolById(schoolId);
                
                if (foundSchool != null) {
                    // בית ספר חוקי נמצא - הצג את הכרטיס
                    schoolNameTextView.setText(foundSchool.getSchoolName());
                    townTextView.setText(foundSchool.getTown());
                    schoolNameCard.setVisibility(View.VISIBLE);
                    
                    // הסתר את טקסט המידע כאשר הכרטיס מוצג
                    schoolInfoTextView.setVisibility(View.GONE);
                    return;
                }
            } catch (NumberFormatException e) {
                // לא מספר חוקי, המשך לחיפוש לפי שם
            }
        }
        
        // אם לא נמצא לפי מזהה או לא מספר, חפש לפי שם
        schoolsDB.School foundSchool = findSchoolByName(input);
        if (foundSchool != null) {
            // בית ספר חוקי נמצא - הצג את הכרטיס
            schoolNameTextView.setText(foundSchool.getSchoolName());
            townTextView.setText(foundSchool.getTown());
            schoolNameCard.setVisibility(View.VISIBLE);
            
            // הסתר את טקסט המידע כאשר הכרטיס מוצג
            schoolInfoTextView.setVisibility(View.GONE);
        } else {
            // בית ספר לא נמצא
            schoolInfoTextView.setText("לא נמצא בית ספר עם סמל מוסד או שם זה");
            schoolInfoTextView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * מוסיפה את בית הספר הנבחר למסד הנתונים של Firestore.
     * מבצעת ולידציה על הקלט, בודקת אם בית הספר כבר קיים,
     * ויוצרת את המסמך המתאים ב-Firestore יחד עם קולקציות משנה נדרשות.
     */
    private void addSchoolToFirestore() {
        String input = schoolIdEditText.getText().toString().trim();

        // ולידציה של קלט
        if (input.isEmpty()) {
            Toast.makeText(this, "נא להזין סמל מוסד או שם בית ספר", Toast.LENGTH_SHORT).show();
            return;
        }

        // נסה למצוא בית ספר לפי מזהה תחילה
        schoolsDB.School foundSchool = null;
        if (input.matches("\\d+")) {
            try {
                int schoolId = Integer.parseInt(input);
                foundSchool = findSchoolById(schoolId);
        } catch (NumberFormatException e) {
                // לא מספר חוקי, המשך לחיפוש לפי שם
            }
        }

        // אם לא נמצא לפי מזהה, חפש לפי שם
        if (foundSchool == null) {
            foundSchool = findSchoolByName(input);
        }

        if (foundSchool == null) {
            Toast.makeText(this, "לא נמצא בית ספר עם סמל מוסד או שם זה", Toast.LENGTH_SHORT).show();
            return;
        }

        // חילוץ פרטי בית הספר
        String schoolName = foundSchool.getSchoolName();
        String schoolSymbol = String.valueOf(foundSchool.getSchoolId());
        String schoolTown = foundSchool.getTown();

        // הצגת סרגל התקדמות
        progressBar.setVisibility(View.VISIBLE);
        addButton.setEnabled(false);

        // בדיקה אם בית הספר כבר קיים
        db.collection("schools").document(schoolSymbol)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    // בית ספר כבר קיים
                    Toast.makeText(addSchool.this, "בית ספר עם סמל זה כבר קיים במערכת", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    addButton.setEnabled(true);
                } else {
                    // יצירת מסמך בית הספר
                    Map<String, Object> schoolData = new HashMap<>();
                    schoolData.put("name", schoolName);
                    schoolData.put("town", schoolTown);
                    
                    // עיצוב התאריך הנוכחי כ-DD:MM:YYYY
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd:MM:yyyy", Locale.getDefault());
                    String formattedDate = dateFormat.format(new Date());
                    schoolData.put("createdAt", formattedDate);

                    db.collection("schools").document(schoolSymbol)
                        .set(schoolData)
                        .addOnSuccessListener(aVoid -> {
                            // יצירת כל קולקציות המשנה הנדרשות
                            createAllSubcollections(schoolSymbol);

                            // הצגת דיאלוג הצלחה במקום טוסט
                            showSuccessDialog("בית הספר " + schoolName + " נוסף בהצלחה!");
                            schoolIdEditText.setText("");
                            schoolNameCard.setVisibility(View.GONE);
                            schoolInfoTextView.setText("מידע אודות בית הספר יתמלא אוטומטית לפי סמל המוסד או שם בית הספר");
                            schoolInfoTextView.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);
                            addButton.setEnabled(true);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(addSchool.this, "שגיאה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            addButton.setEnabled(true);
                        });
                }
            });
    }

    /**
     * מוצא בית ספר ברשימת בתי הספר שנטענו מה-CSV לפי מזהה בית הספר.
     * @param schoolId מזהה בית הספר לחיפוש.
     * @return אובייקט School אם נמצא, אחרת null.
     */
    private schoolsDB.School findSchoolById(int schoolId) {
        for (schoolsDB.School school : allSchools) {
            if (school.getSchoolId() == schoolId) {
                return school;
            }
        }
        return null;
    }

    /**
     * מוצא בית ספר ברשימת בתי הספר שנטענו מה-CSV לפי שם בית הספר.
     * החיפוש אינו תלוי רישיות ובודק אם השם מכיל את מחרוזת החיפוש.
     * @param name שם בית הספר לחיפוש.
     * @return אובייקט School אם נמצא, אחרת null.
     */
    private schoolsDB.School findSchoolByName(String name) {
        String searchName = name.trim().toLowerCase();
        for (schoolsDB.School school : allSchools) {
            if (school.getSchoolName().toLowerCase().contains(searchName)) {
                return school;
            }
        }
        return null;
    }

    /**
     * יוצר את כל קולקציות המשנה הנדרשות עבור בית ספר חדש ב-Firestore.
     * קולקציות אלו כוללות: allowedRakazEmails, megamot, rakazim, ו-managers.
     * @param schoolId מזהה בית הספר שעבורו יש ליצור את קולקציות המשנה.
     */
    private void createAllSubcollections(String schoolId) {
        // יצירת כל קולקציות המשנה הנדרשות
        createEmptyCollection(schoolId, "allowedRakazEmails");
        createEmptyCollection(schoolId, "megamot");
        createEmptyCollection(schoolId, "rakazim");
        createEmptyCollection(schoolId, "managers");
    }

    /**
     * יוצר קולקציה ריקה (עם מסמך אתחול/דמה) ב-Firestore עבור בית ספר נתון.
     * @param schoolId מזהה בית הספר.
     * @param collectionName שם הקולקציה ליצירה.
     */
    private void createEmptyCollection(String schoolId, String collectionName) {
        Log.d(TAG, "Creating subcollection: " + collectionName + " for school: " + schoolId);
        CollectionReference collectionRef = db.collection("schools").document(schoolId)
                .collection(collectionName);
        
        // יצירת מסמך מתאים לסוג הקולקציה
        if (collectionName.equals("allowedRakazEmails")) {
            // עבור allowedRakazEmails, צור מסמך אתחול
            Map<String, Object> initData = new HashMap<>();
            initData.put("_init", true);
            
            // עיצוב התאריך הנוכחי כ-DD:MM:YYYY
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd:MM:yyyy", Locale.getDefault());
            String formattedDate = dateFormat.format(new Date());
            initData.put("createdAt", formattedDate);
            
            collectionRef.document("_init")
                .set(initData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully created " + collectionName + " subcollection with init document");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating " + collectionName + " subcollection: " + e.getMessage());
                    Toast.makeText(addSchool.this, "שגיאה ביצירת " + collectionName + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        } else {
            // עבור קולקציות אחרות, צור מסמך דמה
            Map<String, Object> dummyData = new HashMap<>();
            dummyData.put("dummy", true);
            
            // עיצוב התאריך הנוכחי כ-DD:MM:YYYY
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd:MM:yyyy", Locale.getDefault());
            String formattedDate = dateFormat.format(new Date());
            dummyData.put("createdAt", formattedDate);
            
            collectionRef.document("_dummy")
                .set(dummyData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully created " + collectionName + " subcollection");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating " + collectionName + " subcollection: " + e.getMessage());
                    Toast.makeText(addSchool.this, "שגיאה ביצירת " + collectionName + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }

    /**
     * מציג דיאלוג הצלחה מותאם אישית לאחר הוספת בית ספר בהצלחה.
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
            titleView.setText("הוספת בית ספר");
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