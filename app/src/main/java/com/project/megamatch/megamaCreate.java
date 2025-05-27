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
    
    // Activity result launcher for MegamaAttachments
    private ActivityResultLauncher<Intent> megamaAttachmentsLauncher;
    
    /**
     * Helper class to prevent rapid multiple clicks
     */
    private static class DebounceClickListener implements View.OnClickListener {
        private static final long DEBOUNCE_INTERVAL_MS = 800; // 800 milliseconds
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
                Log.d("DebounceClick", "Click ignored, too soon after previous click");
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
        
        // Initialize activity result launcher for MegamaAttachments
        megamaAttachmentsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // If we come back from MegamaAttachments with a result, do nothing
                    // Data is already preserved in this activity
                    boolean shouldPreserveData = result.getData().getBooleanExtra("shouldPreserveData", false);
                    if (shouldPreserveData) {
                        // The user clicked back to edit, data is already preserved
                    }
                }
            });
        
        // Check if we're in update mode
        boolean isUpdate = getIntent().getBooleanExtra("isUpdate", false);
        if (isUpdate) {
            // Set button text to update
            createMegamaButton.setText("המשך");
            
            // Load existing megama data
            loadExistingMegamaData();
        } else {
            // טעינת פרטי הרכז
            loadRakazDetails();
        }
        
        // Set button click listener with debounce mechanism
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
            // Toggle visibility - if visible, hide it; if hidden, show it
            boolean isVisible = customConditionInputLayout.getVisibility() == View.VISIBLE;
            customConditionInputLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            addCustomConditionButton.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            
            // If we're making it visible, request focus to the input field
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
    
    // פונקציה להוספת תנאי מותאם
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
    
    // פונקציה לטעינת פרטי הרכז מפיירבייס
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
                      
                      // טעינת שם מגמה
                      megamaName = documentSnapshot.getString("megama");
                      if (megamaName != null && !megamaName.isEmpty()) {
                          megamaText.setText("עדכון מגמת " + megamaName);
                          
                          // Now that we have the megama name, fetch the full megama data directly from Firestore
                          fetchMegamaDataFromFirestore(megamaName);
                      } else {
                          megamaText.setText("יצירת מגמה חדשה!");
                          // Set button text to "המשך" for consistency
                          createMegamaButton.setText("המשך");
                          // If there's no megama associated with the rakaz, try to use the intent data
                          loadFromIntent();
                      }
                  } else {
                      greetingText.setText("שלום " + username);
                      megamaText.setText("יצירת מגמה חדשה!");
                      // Set button text to "המשך" for consistency
                      createMegamaButton.setText("המשך");
                      // If no rakaz document exists, try to use the intent data
                      loadFromIntent();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e("MegamaCreate", "Error loading rakaz details: " + e.getMessage());
                  greetingText.setText("שלום " + username);
                  megamaText.setText("יצירת מגמה חדשה!");
                  // Set button text to "המשך" for consistency
                  createMegamaButton.setText("המשך");
                  // On failure, try to use the intent data
                  loadFromIntent();
              });
    }
    
    // Load existing megama data from Firestore
    private void loadExistingMegamaData() {
        // Load rakaz details first to set the greeting text
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
                      
                      // טעינת שם מגמה
                      megamaName = documentSnapshot.getString("megama");
                      if (megamaName != null && !megamaName.isEmpty()) {
                          megamaText.setText("עדכון מגמת " + megamaName);
                          
                          // Now that we have the megama name, fetch the full megama data directly from Firestore
                          fetchMegamaDataFromFirestore(megamaName);
                      } else {
                          megamaText.setText("יצירת מגמה חדשה!");
                          // If there's no megama associated with the rakaz, try to use the intent data
                          loadFromIntent();
                      }
                  } else {
                      greetingText.setText("שלום " + username);
                      megamaText.setText("יצירת מגמה חדשה!");
                      // If no rakaz document exists, try to use the intent data
                      loadFromIntent();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e("MegamaCreate", "Error loading rakaz details: " + e.getMessage());
                  greetingText.setText("שלום " + username);
                  megamaText.setText("יצירת מגמה חדשה!");
                  // On failure, try to use the intent data
                  loadFromIntent();
              });
    }
    
    // Fetch megama data directly from Firestore
    private void fetchMegamaDataFromFirestore(String megamaName) {
        fireDB.collection("schools").document(schoolId)
              .collection("megamot").document(megamaName)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      // Get data from Firestore document
                      String description = documentSnapshot.getString("description");
                      Boolean requiresExam = documentSnapshot.getBoolean("requiresExam");
                      Boolean requiresGradeAvg = documentSnapshot.getBoolean("requiresGradeAvg");
                      Long requiredGradeAvgLong = documentSnapshot.getLong("requiredGradeAvg");
                      int requiredGradeAvg = requiredGradeAvgLong != null ? requiredGradeAvgLong.intValue() : 0;
                      ArrayList<String> conditionsList = (ArrayList<String>) documentSnapshot.get("customConditions");
                      
                      // Set data to UI components
                      if (description != null) {
                          megamaDescriptionInput.setText(description);
                      }
                      
                      if (requiresExam != null) {
                          requiresExamCheckbox.setChecked(requiresExam);
                      }
                      
                      if (requiresGradeAvg != null) {
                          requiresGradeAvgCheckbox.setChecked(requiresGradeAvg);
                      }
                      
                      if (requiresGradeAvg != null && requiresGradeAvg) {
                          gradeAvgInputLayout.setVisibility(View.VISIBLE);
                          gradeAvgInput.setText(String.valueOf(requiredGradeAvg));
                      }
                      
                      // Clear existing conditions before adding
                      customConditionsContainer.removeAllViews();
                      customConditions.clear();
                      
                      // Add custom conditions
                      if (conditionsList != null && !conditionsList.isEmpty()) {
                          for (String condition : conditionsList) {
                              addCustomCondition(condition);
                          }
                      }
                  } else {
                      // If document doesn't exist, fall back to intent extras
                      loadFromIntent();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e("MegamaCreate", "Error loading megama data: " + e.getMessage());
                  // On failure, try to use the intent data
                  loadFromIntent();
              });
    }
    
    // Load data from intent extras (fallback method)
    private void loadFromIntent() {
        // Get data from intent extras
        megamaName = getIntent().getStringExtra("megamaName");
        String description = getIntent().getStringExtra("megamaDescription");
        boolean requiresExam = getIntent().getBooleanExtra("requiresExam", false);
        boolean requiresGradeAvg = getIntent().getBooleanExtra("requiresGradeAvg", false);
        int requiredGradeAvg = getIntent().getIntExtra("requiredGradeAvg", 0);
        ArrayList<String> conditionsList = getIntent().getStringArrayListExtra("customConditions");
        
        // Set data to UI components
        if (description != null) {
            megamaDescriptionInput.setText(description);
        }
        
        requiresExamCheckbox.setChecked(requiresExam);
        requiresGradeAvgCheckbox.setChecked(requiresGradeAvg);
        
        if (requiresGradeAvg) {
            gradeAvgInputLayout.setVisibility(View.VISIBLE);
            gradeAvgInput.setText(String.valueOf(requiredGradeAvg));
        }
        
        // Add custom conditions
        if (conditionsList != null && !conditionsList.isEmpty()) {
            for (String condition : conditionsList) {
                addCustomCondition(condition);
            }
        }
    }
    
    // Function to continue to MegamaAttachments screen
    public void continueToBuildingMegama(View view) {
        // Show loading state
        createMegamaButton.setEnabled(false);
        createMegamaButton.setText("טוען...");
        
        String megamaDescription = megamaDescriptionInput.getText().toString().trim();
        boolean requiresExam = requiresExamCheckbox.isChecked();
        boolean requiresGradeAvg = requiresGradeAvgCheckbox.isChecked();
        
        // בדיקת תקינות קלט
        if (megamaDescription.isEmpty()) {
            Toast.makeText(this, "יש למלא תיאור מגמה", Toast.LENGTH_SHORT).show();
            // Reset button state
            createMegamaButton.setEnabled(true);
            createMegamaButton.setText("המשך");
            return;
        }
        
        if (megamaName == null || megamaName.isEmpty()) {
            Toast.makeText(this, "שגיאה: לא נמצא שם מגמה", Toast.LENGTH_SHORT).show();
            // Reset button state
            createMegamaButton.setEnabled(true);
            createMegamaButton.setText("המשך");
            return;
        }
        
        // טיפול בציון ממוצע אם נדרש
        int requiredGradeAvg = 0;
        if (requiresGradeAvg) {
            String gradeStr = gradeAvgInput.getText().toString().trim();
            if (gradeStr.isEmpty()) {
                Toast.makeText(this, "יש להזין ציון ממוצע נדרש", Toast.LENGTH_SHORT).show();
                // Reset button state
                createMegamaButton.setEnabled(true);
                createMegamaButton.setText("המשך");
                return;
            }
            
            try {
                requiredGradeAvg = Integer.parseInt(gradeStr);
                if (requiredGradeAvg <= 0 || requiredGradeAvg > 100) {
                    Toast.makeText(this, "יש להזין ציון תקין (1-100)", Toast.LENGTH_SHORT).show();
                    // Reset button state
                    createMegamaButton.setEnabled(true);
                    createMegamaButton.setText("המשך");
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "יש להזין ציון תקין", Toast.LENGTH_SHORT).show();
                // Reset button state
                createMegamaButton.setEnabled(true);
                createMegamaButton.setText("המשך");
                return;
            }
        }
        
        // איסוף תנאים מותאמים אישית מסומנים
        ArrayList<String> activeCustomConditions = new ArrayList<>();
        for (int i = 0; i < customConditionsContainer.getChildCount(); i++) {
            View child = customConditionsContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View rowChild = row.getChildAt(j);
                    if (rowChild instanceof CheckBox) {
                        CheckBox checkBox = (CheckBox) rowChild;
                        if (checkBox.isChecked()) {
                            activeCustomConditions.add(checkBox.getText().toString());
                        }
                    }
                }
            }
        }
        
        // Check if we're in update mode
        boolean isUpdate = getIntent().getBooleanExtra("isUpdate", false);
        
        // Create intent and pass data to MegamaAttachments
        Intent intent = new Intent(this, MegamaAttachments.class);
        intent.putExtra("schoolId", schoolId);
        intent.putExtra("username", username);
        intent.putExtra("megamaName", megamaName);
        intent.putExtra("megamaDescription", megamaDescription);
        intent.putExtra("requiresExam", requiresExam);
        intent.putExtra("requiresGradeAvg", requiresGradeAvg);
        intent.putExtra("requiredGradeAvg", requiredGradeAvg);
        intent.putStringArrayListExtra("customConditions", activeCustomConditions);
        intent.putExtra("isUpdate", isUpdate);
        
        // Use flags to ensure we don't stack activities
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        // If in update mode, try to get existing image URLs from Firestore
        if (isUpdate) {
            // First try to get image URLs from intent
            ArrayList<String> imageUrls = getIntent().getStringArrayListExtra("imageUrls");
            if (imageUrls != null && !imageUrls.isEmpty()) {
                intent.putStringArrayListExtra("imageUrls", imageUrls);
                // Launch immediately if we have the URLs
                megamaAttachmentsLauncher.launch(intent);
                // Reset button state after launching
                createMegamaButton.setEnabled(true);
                createMegamaButton.setText("המשך");
            } else {
                // If not available in intent, try fetching from Firestore
                fireDB.collection("schools").document(schoolId)
                      .collection("megamot").document(megamaName)
                      .get()
                      .addOnSuccessListener(documentSnapshot -> {
                          if (documentSnapshot.exists()) {
                              ArrayList<String> fetchedUrls = (ArrayList<String>) documentSnapshot.get("imageUrls");
                              if (fetchedUrls != null && !fetchedUrls.isEmpty()) {
                                  intent.putStringArrayListExtra("imageUrls", fetchedUrls);
                              }
                          }
                          megamaAttachmentsLauncher.launch(intent);
                          // Reset button state after launching
                          createMegamaButton.setEnabled(true);
                          createMegamaButton.setText("המשך");
                      })
                      .addOnFailureListener(e -> {
                          // Continue even if fetch fails
                          megamaAttachmentsLauncher.launch(intent);
                          // Reset button state after launching
                          createMegamaButton.setEnabled(true);
                          createMegamaButton.setText("המשך");
                      });
            }
        } else {
            // If not in update mode, just launch
            megamaAttachmentsLauncher.launch(intent);
            // Reset button state after launching
            createMegamaButton.setEnabled(true);
            createMegamaButton.setText("המשך");
        }
    }
    
    private void goToLoginScreen() {
        Intent intent = new Intent(this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    // מחלקה לייצוג מגמה
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
        
        // קונסטרקטור ריק לפיירבייס
        public Megama() {
            this.customConditions = new ArrayList<>();
            this.imageUrls = new ArrayList<>();
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
            this.customConditions = customConditions != null ? new ArrayList<>(customConditions) : new ArrayList<>();
            this.imageUrls = imageUrls != null ? new ArrayList<>(imageUrls) : new ArrayList<>();
        }
        
        // גטרים וסטרים
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