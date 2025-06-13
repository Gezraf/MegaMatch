package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * מחלקה זו מאפשרת ליצור או לערוך מגמות בבית ספר.
 * היא מטפלת בקלט משתמש עבור שם מגמה, תיאור, תנאי קבלה (מבחן, ממוצע ציונים, תנאים מותאמים אישית)
 * וכן מאפשרת ניווט למסך הוספת קבצים מצורפים.
 */
public class megamaCreate extends AppCompatActivity {

    private TextView greetingText, megamaText;
    private EditText megamaDescriptionInput, gradeAvgInput, customConditionInput;
    private CheckBox requiresExamCheckbox, requiresGradeAvgCheckbox;
    private Button createMegamaButton, backButton, addCustomConditionButton;
    private MaterialButton addConditionButton;
    private TextInputLayout gradeAvgInputLayout, customConditionInputLayout;
    private LinearLayout customConditionsContainer;
    private SharedPreferences sharedPreferences;
    private FirebaseFirestore fireDB;
    private String schoolId;
    private String username;
    private String megamaName;
    private List<String> customConditions = new ArrayList<>();
    
    // לאנצ'ר תוצאות פעילות עבור MegamaAttachments
    private ActivityResultLauncher<Intent> megamaAttachmentsLauncher;
    
    /**
     * מחלקת עזר למניעת לחיצות מרובות מהירות.
     */
    private static class DebounceClickListener implements View.OnClickListener {
        private static final long DEBOUNCE_INTERVAL_MS = 800; // 800 מילישניות
        private final View.OnClickListener clickListener;
        private long lastClickTime = 0;
        
        DebounceClickListener(View.OnClickListener clickListener) {
            this.clickListener = clickListener;
        }
        
        @Override
        public void onClick(View v) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime > DEBOUNCE_INTERVAL_MS) {
                lastClickTime = currentTime;
                clickListener.onClick(v);
            } else {
                Log.d("DebounceClick", "לחיצה התעלמה, מהר מדי לאחר לחיצה קודמת");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.megama_create);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.megamaCreate), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // אתחול רכיבים
        greetingText = findViewById(R.id.greetingText);
        megamaText = findViewById(R.id.megamaText);
        megamaDescriptionInput = findViewById(R.id.megamaDescriptionInput);
        gradeAvgInput = findViewById(R.id.gradeAvgInput);
        customConditionInput = findViewById(R.id.customConditionInput);
        requiresExamCheckbox = findViewById(R.id.requiresExamCheckbox);
        requiresGradeAvgCheckbox = findViewById(R.id.requiresGradeAvgCheckbox);
        gradeAvgInputLayout = findViewById(R.id.gradeAvgInputLayout);
        customConditionInputLayout = findViewById(R.id.customConditionInputLayout);
        createMegamaButton = findViewById(R.id.createMegamaButton);
        backButton = findViewById(R.id.backButton);
        addConditionButton = findViewById(R.id.addConditionButton);
        addCustomConditionButton = findViewById(R.id.addCustomConditionButton);
        customConditionsContainer = findViewById(R.id.customConditionsContainer);
        
        // אתחול פיירבייס
        fireDB = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        
        // קבלת נתוני משתמש מחובר
        schoolId = sharedPreferences.getString("loggedInSchoolId", "");
        username = sharedPreferences.getString("loggedInUsername", "");
        
        // בדיקת תקינות נתונים
        if (schoolId.isEmpty() || username.isEmpty()) {
            goToLoginScreen();
            return;
        }
        
        // אתחול לאנצ'ר תוצאות פעילות עבור MegamaAttachments
        megamaAttachmentsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // אם אנו חוזרים מ-MegamaAttachments עם תוצאה, אל תעשה כלום
                    // הנתונים כבר נשמרים בפעילות זו
                    boolean shouldPreserveData = result.getData().getBooleanExtra("shouldPreserveData", false);
                    if (shouldPreserveData) {
                        // המשתמש לחץ על כפתור חזרה לעריכה, הנתונים כבר נשמרים
                    }
                }
            });
        
        // בדוק אם אנחנו במצב עדכון
        boolean isUpdate = getIntent().getBooleanExtra("isUpdate", false);
        if (isUpdate) {
            // הגדר טקסט כפתור לעדכון
            createMegamaButton.setText("המשך");
            
            // טען נתוני מגמה קיימים
            loadExistingMegamaData();
        } else {
            // טעינת פרטי הרכז
            loadRakazDetails();
        }
        
        // הגדרת מאזין לחיצה על כפתור עם מנגנון Debounce
        createMegamaButton.setOnClickListener(new DebounceClickListener(v -> continueToBuildingMegama(v)));
        
        // הגדרת כפתור חזרה
        backButton.setOnClickListener(v -> finish());
        
        // הגדרת הצ'קבוקס של ממוצע ציונים
        requiresGradeAvgCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            gradeAvgInputLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                gradeAvgInput.requestFocus();
            }
        });
        
        // הגדרת כפתור הוספת תנאי עם אפשרות לסגירת התפריט
        addConditionButton.setOnClickListener(v -> {
            // החלף מצב נראות - אם גלוי, הסתר; אם מוסתר, הצג
            boolean isVisible = customConditionInputLayout.getVisibility() == View.VISIBLE;
            customConditionInputLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            addCustomConditionButton.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            
            // אם אנו מציגים אותו, בקש מיקוד לשדה הקלט
            if (!isVisible) {
                customConditionInput.requestFocus();
            }
        });
        
        // הגדרת כפתור הוספת תנאי מותאם אישית
        addCustomConditionButton.setOnClickListener(v -> {
            String condition = customConditionInput.getText().toString().trim();
            if (!condition.isEmpty()) {
                addCustomCondition(condition);
                customConditionInput.setText("");
                customConditionInputLayout.setVisibility(View.GONE);
                addCustomConditionButton.setVisibility(View.GONE);
            } else {
                Toast.makeText(this, "נא להזין תנאי", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * מוסיף תנאי מותאם אישית לרשימה ומציג אותו בממשק המשתמש.
     * @param condition התנאי המותאם אישית להוספה.
     */
    private void addCustomCondition(String condition) {
        customConditions.add(condition);
        
        // יצירת שורה חדשה עם צ'קבוקס וכפתור מחיקה
        LinearLayout conditionRow = new LinearLayout(this);
        conditionRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT));
        conditionRow.setOrientation(LinearLayout.HORIZONTAL);
        conditionRow.setGravity(Gravity.CENTER_VERTICAL);
        conditionRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        
        // יצירת צ'קבוקס
        MaterialCheckBox checkBox = new MaterialCheckBox(this);
        LinearLayout.LayoutParams checkBoxParams = new LinearLayout.LayoutParams(
                0, 
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f);
        checkBox.setLayoutParams(checkBoxParams);
        checkBox.setText(condition);
        checkBox.setChecked(true);
        checkBox.setTag(condition);
        checkBox.setTextSize(16);
        checkBox.setTextColor(getResources().getColor(R.color.white));
        checkBox.setButtonTintList(getResources().getColorStateList(R.color.teal_200));
        checkBox.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        conditionRow.addView(checkBox);
        
        // יצירת כפתור מחיקה
        MaterialButton deleteButton = new MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.setMarginStart(8);
        deleteButton.setLayoutParams(buttonParams);
        deleteButton.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_delete));
        deleteButton.setIconTint(getResources().getColorStateList(R.color.error));
        deleteButton.setTag(condition);
        
        // הגדרת אירוע לחיצה על כפתור המחיקה
        deleteButton.setOnClickListener(v -> {
            String conditionToRemove = (String) v.getTag();
            customConditions.remove(conditionToRemove);
            customConditionsContainer.removeView(conditionRow);
        });
        
        conditionRow.addView(deleteButton);
        customConditionsContainer.addView(conditionRow);
    }
    
    /**
     * טוען את פרטי הרכז מפיירבייס ומעדכן את ממשק המשתמש.
     */
    private void loadRakazDetails() {
        fireDB.collection("schools").document(schoolId)
              .collection("rakazim").document(username)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      // טעינת שם פרטי
                      String firstName = documentSnapshot.getString("firstName");
                      if (firstName != null && !firstName.isEmpty()) {
                          greetingText.setText("שלום " + firstName);
                      } else {
                          greetingText.setText("שלום " + username);
                      }
                      String rakazMegama = documentSnapshot.getString("megama");
                      if (rakazMegama != null && !rakazMegama.isEmpty()) {
                          megamaName = rakazMegama;
                          megamaText.setText("אתה עומד לעדכן את מגמת: " + megamaName);
                          loadExistingMegamaData(); // Load existing data for update
                          createMegamaButton.setText("המשך");
                      } else {
                          Log.d(TAG, "רכז לא משויך למגמה. יצירת מגמה חדשה.");
                          megamaText.setText("יצירת מגמה חדשה!");
                      }
                  } else {
                      Log.w(TAG, "מסמך רכז לא נמצא עבור שם משתמש: " + username);
                      Toast.makeText(this, "שגיאה: פרטי רכז לא נמצאו", Toast.LENGTH_SHORT).show();
                      goToLoginScreen();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e(TAG, "שגיאה בטעינת פרטי רכז: " + e.getMessage(), e);
                  Toast.makeText(this, "שגיאה בטעינת פרטי רכז", Toast.LENGTH_SHORT).show();
                  goToLoginScreen();
              });
    }

    /**
     * טוען נתוני מגמה קיימים מפיירסטור למצב עדכון.
     */
    private void loadExistingMegamaData() {
        if (megamaName == null || megamaName.isEmpty()) {
            Log.e(TAG, "שם מגמה חסר לטעינת נתונים קיימים.");
            return;
        }
        Log.d(TAG, "טוען נתוני מגמה קיימים עבור: " + megamaName);
        fetchMegamaDataFromFirestore(megamaName);
    }

    /**
     * שולף נתוני מגמה מפיירסטור ומעדכן את רכיבי ממשק המשתמש.
     * @param megamaName שם המגמה לשליפה.
     */
    private void fetchMegamaDataFromFirestore(String megamaName) {
        fireDB.collection("schools").document(schoolId)
                .collection("megamot").document(megamaName)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        megamaDescriptionInput.setText(documentSnapshot.getString("description"));
                        requiresExamCheckbox.setChecked(Boolean.TRUE.equals(documentSnapshot.getBoolean("requiresExam")));
                        requiresGradeAvgCheckbox.setChecked(Boolean.TRUE.equals(documentSnapshot.getBoolean("requiresGradeAvg")));
                        if (documentSnapshot.contains("requiredGradeAvg")) {
                            Long avg = documentSnapshot.getLong("requiredGradeAvg");
                            gradeAvgInput.setText(String.valueOf(avg != null ? avg.intValue() : 0));
                        }
                        ArrayList<String> fetchedCustomConditions = (ArrayList<String>) documentSnapshot.get("customConditions");
                        if (fetchedCustomConditions != null) {
                            customConditions.clear(); // Clear existing conditions
                            for (String condition : fetchedCustomConditions) {
                                addCustomCondition(condition);
                            }
                        }
                    } else {
                        Log.d(TAG, "מסמך מגמה לא נמצא בפיירסטור: " + megamaName);
                    }
                    loadFromIntent(); // Load/override with any data from intent if available
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בשליפת נתוני מגמה: " + e.getMessage(), e);
                    Toast.makeText(this, "שגיאה בטעינת פרטי מגמה קיימים: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    loadFromIntent(); // Try to load from intent if Firestore fetch fails
                });
    }

    /**
     * טוען נתונים מה-Intent אם קיימים, עבור שמירה במצב של שינוי מסך או חזרה.
     */
    private void loadFromIntent() {
        // Load data from intent to restore state after returning from attachments screen
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String intentMegamaName = extras.getString("megamaName", "");
            String intentMegamaDescription = extras.getString("megamaDescription", "");
            boolean intentRequiresExam = extras.getBoolean("requiresExam", requiresExamCheckbox.isChecked());
            boolean intentRequiresGradeAvg = extras.getBoolean("requiresGradeAvg", requiresGradeAvgCheckbox.isChecked());
            int intentRequiredGradeAvg = extras.getInt("requiredGradeAvg", Integer.parseInt(gradeAvgInput.getText().toString().isEmpty() ? "0" : gradeAvgInput.getText().toString()));
            ArrayList<String> intentCustomConditions = extras.getStringArrayList("customConditions");

            if (!intentMegamaName.isEmpty()) {
                megamaName = intentMegamaName;
                megamaText.setText("אתה עומד ליצור את מגמת: " + megamaName);
            }
            if (!intentMegamaDescription.isEmpty()) {
                megamaDescriptionInput.setText(intentMegamaDescription);
            }
            requiresExamCheckbox.setChecked(intentRequiresExam);
            requiresGradeAvgCheckbox.setChecked(intentRequiresGradeAvg);
            gradeAvgInput.setText(String.valueOf(intentRequiredGradeAvg));
            if (intentCustomConditions != null) {
                customConditions.clear();
                customConditionsContainer.removeAllViews();
                for (String condition : intentCustomConditions) {
                    addCustomCondition(condition);
                }
            }
        }
    }

    /**
     * ממשיך למסך הוספת קבצים מצורפים למגמה, או יוצר את המגמה אם אין קבצים מצורפים.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void continueToBuildingMegama(View view) {
        // Capture current state of the form
        megamaName = megamaText.getText().toString().replace("אתה עומד ליצור את מגמת: ", "").trim();
        if (megamaName.isEmpty() || megamaName.equals("יצירת מגמה חדשה!")) {
            Toast.makeText(this, "נא להזין שם מגמה", Toast.LENGTH_SHORT).show();
            return;
        }

        String description = megamaDescriptionInput.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "נא להזין תיאור מגמה", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean requiresExam = requiresExamCheckbox.isChecked();
        boolean requiresGradeAvg = requiresGradeAvgCheckbox.isChecked();
        int requiredGradeAvg = 0;
        if (requiresGradeAvg) {
            String gradeAvgStr = gradeAvgInput.getText().toString().trim();
            if (gradeAvgStr.isEmpty()) {
                Toast.makeText(this, "נא להזין ממוצע ציונים נדרש", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                requiredGradeAvg = Integer.parseInt(gradeAvgStr);
                if (requiredGradeAvg < 0 || requiredGradeAvg > 100) {
                    Toast.makeText(this, "ממוצע ציונים חייב להיות בין 0 ל-100", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "ממוצע ציונים לא חוקי", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Get custom conditions from the views in the container
        ArrayList<String> currentCustomConditions = new ArrayList<>();
        for (int i = 0; i < customConditionsContainer.getChildCount(); i++) {
            View row = customConditionsContainer.getChildAt(i);
            if (row instanceof LinearLayout) {
                LinearLayout linearLayout = (LinearLayout) row;
                for (int j = 0; j < linearLayout.getChildCount(); j++) {
                    View child = linearLayout.getChildAt(j);
                    if (child instanceof MaterialCheckBox) {
                        MaterialCheckBox checkBox = (MaterialCheckBox) child;
                        if (checkBox.isChecked()) {
                            currentCustomConditions.add(checkBox.getText().toString());
                        }
                    }
                }
            }
        }

        // Update the customConditions list with the current state
        customConditions = currentCustomConditions;

        // Intent to MegamaAttachments
        Intent intent = new Intent(this, MegamaAttachments.class);
        intent.putExtra("schoolId", schoolId);
        intent.putExtra("username", username);
        intent.putExtra("megamaName", megamaName);
        intent.putExtra("megamaDescription", description);
        intent.putExtra("requiresExam", requiresExam);
        intent.putExtra("requiresGradeAvg", requiresGradeAvg);
        intent.putExtra("requiredGradeAvg", requiredGradeAvg);
        intent.putStringArrayListExtra("customConditions", customConditions);
        
        // Pass existing image URLs if in update mode
        boolean isUpdateMode = getIntent().getBooleanExtra("isUpdate", false);
        if (isUpdateMode) {
            // Fetch existing images if needed (assuming they are loaded into the current activity's state)
            // For simplicity, let's assume `uploadedImageUrls` already contains them if `loadExistingMegamaData` was called
            // You might need to retrieve them again if they are not stored efficiently.
            ArrayList<String> existingImageUrls = getIntent().getStringArrayListExtra("imageUrls");
            if (existingImageUrls != null) {
                intent.putStringArrayListExtra("imageUrls", existingImageUrls);
            }
            intent.putExtra("isUpdate", true);
        }

        megamaAttachmentsLauncher.launch(intent);
    }

    /**
     * מנווט את המשתמש למסך ההתחברות אם פרטי בית הספר או הרכז חסרים.
     */
    private void goToLoginScreen() {
        Intent intent = new Intent(this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * מחלקה המייצגת מודל נתונים עבור מגמה.
     */
    public static class Megama {
        private String name;
        private String description;
        private String rakazUsername;
        private boolean requiresExam;
        private boolean requiresGradeAvg;
        private int requiredGradeAvg;
        private List<String> customConditions;
        private List<String> imageUrls;
        private int currentEnrolled = 0;

        public Megama() {
            // Required empty public constructor for Firestore
        }

        public Megama(String name, String description, String rakazUsername, 
                     boolean requiresExam, boolean requiresGradeAvg, int requiredGradeAvg, 
                     List<String> customConditions, List<String> imageUrls) {
            this.name = name;
            this.description = description;
            this.rakazUsername = rakazUsername;
            this.requiresExam = requiresExam;
            this.requiresGradeAvg = requiresGradeAvg;
            this.requiredGradeAvg = requiredGradeAvg;
            this.customConditions = customConditions;
            this.imageUrls = imageUrls;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getRakazUsername() { return rakazUsername; }
        public void setRakazUsername(String rakazUsername) { this.rakazUsername = rakazUsername; }

        public boolean isRequiresExam() { return requiresExam; }
        public void setRequiresExam(boolean requiresExam) { this.requiresExam = requiresExam; }

        public boolean isRequiresGradeAvg() { return requiresGradeAvg; }
        public void setRequiresGradeAvg(boolean requiresGradeAvg) { this.requiresGradeAvg = requiresGradeAvg; }

        public int getRequiredGradeAvg() { return requiredGradeAvg; }
        public void setRequiredGradeAvg(int requiredGradeAvg) { this.requiredGradeAvg = requiredGradeAvg; }

        public List<String> getCustomConditions() { return customConditions; }
        public void setCustomConditions(List<String> customConditions) { 
            this.customConditions = customConditions != null ? new ArrayList<>(customConditions) : new ArrayList<>();
        }

        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { 
            this.imageUrls = imageUrls != null ? new ArrayList<>(imageUrls) : new ArrayList<>();
        }

        public int getCurrentEnrolled() { return currentEnrolled; }
        public void setCurrentEnrolled(int currentEnrolled) { this.currentEnrolled = currentEnrolled; }
    }
} 