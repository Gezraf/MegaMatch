package com.project.megamatch;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

public class addSchool extends AppCompatActivity {

    private static final String TAG = "AddSchool";
    private EditText schoolIdEditText;
    private TextView schoolInfoTextView;
    private TextView schoolNameTextView;
    private TextView principalNameTextView;
    private MaterialCardView schoolNameCard;
    private Button addButton;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private List<schoolsDB.School> allSchools;

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

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Load schools from CSV
        schoolsDB.loadSchoolsFromCSV(this);
        allSchools = schoolsDB.getAllSchools();
        Log.d(TAG, "Loaded " + allSchools.size() + " schools from CSV");

        // Initialize UI elements
        schoolIdEditText = findViewById(R.id.schoolIdEditText);
        schoolInfoTextView = findViewById(R.id.schoolInfoTextView);
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        principalNameTextView = findViewById(R.id.principalNameTextView);
        schoolNameCard = findViewById(R.id.schoolNameCard);
        addButton = findViewById(R.id.addButton);
        progressBar = findViewById(R.id.progressBar);

        // Set up TextWatcher for school ID input
        schoolIdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSchoolInfo(s.toString());
            }
        });

        addButton.setOnClickListener(v -> addSchoolToFirestore());
    }

    private void updateSchoolInfo(String schoolIdStr) {
        // Hide the card by default
        schoolNameCard.setVisibility(View.GONE);
        
        // Show instruction text when field is empty
        if (schoolIdStr.isEmpty()) {
            schoolInfoTextView.setText("מידע אודות בית הספר יתמלא אוטומטית לפי סמל המוסד");
            schoolInfoTextView.setVisibility(View.VISIBLE);
            return;
        }
        
        // Check if it's exactly 6 digits
        if (schoolIdStr.length() == 6 && schoolIdStr.matches("\\d+")) {
            try {
                int schoolId = Integer.parseInt(schoolIdStr);
                schoolsDB.School foundSchool = findSchoolById(schoolId);
                
                if (foundSchool != null) {
                    // Valid school found - show the card
                    schoolNameTextView.setText(foundSchool.getSchoolName());
                    principalNameTextView.setText(foundSchool.getPrincipalName());
                    schoolNameCard.setVisibility(View.VISIBLE);
                    
                    // Hide the info text when we show the card
                    schoolInfoTextView.setVisibility(View.GONE);
                } else {
                    // School ID is valid format but not found
                    schoolInfoTextView.setText("לא נמצא בית ספר עם סמל מוסד זה");
                    schoolInfoTextView.setVisibility(View.VISIBLE);
                }
            } catch (NumberFormatException e) {
                // Should not happen since we already checked if it's a number
                schoolInfoTextView.setText("סמל מוסד חייב להיות מספר");
                schoolInfoTextView.setVisibility(View.VISIBLE);
            }
        } else if (schoolIdStr.length() > 6) {
            // Too many digits
            schoolInfoTextView.setText("סמל מוסד חייב להיות באורך 6 ספרות בדיוק");
            schoolInfoTextView.setVisibility(View.VISIBLE);
        } else {
            // Still typing, show a hint
            schoolInfoTextView.setText("המשך להקליד את סמל המוסד (6 ספרות)");
            schoolInfoTextView.setVisibility(View.VISIBLE);
        }
    }

    private void addSchoolToFirestore() {
        String schoolIdStr = schoolIdEditText.getText().toString().trim();

        // Validate input
        if (schoolIdStr.isEmpty()) {
            Toast.makeText(this, "נא להזין סמל מוסד", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate school ID is exactly 6 digits
        if (schoolIdStr.length() != 6 || !schoolIdStr.matches("\\d+")) {
            Toast.makeText(this, "סמל מוסד חייב להיות באורך 6 ספרות בדיוק", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert to integer
        int schoolId;
        try {
            schoolId = Integer.parseInt(schoolIdStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "סמל מוסד חייב להיות מספר", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find school in database
        schoolsDB.School foundSchool = findSchoolById(schoolId);
        if (foundSchool == null) {
            Toast.makeText(this, "לא נמצא בית ספר עם סמל מוסד זה", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract school info
        String schoolName = foundSchool.getSchoolName();
        String schoolSymbol = String.valueOf(foundSchool.getSchoolId());
        String schoolManager = foundSchool.getPrincipalName();

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        addButton.setEnabled(false);

        // Check if school already exists
        db.collection("schools").document(schoolSymbol)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    // School already exists
                    Toast.makeText(addSchool.this, "בית ספר עם סמל זה כבר קיים במערכת", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    addButton.setEnabled(true);
                } else {
                    // Create the school document
                    Map<String, Object> schoolData = new HashMap<>();
                    schoolData.put("name", schoolName);
                    schoolData.put("manager", schoolManager);
                    
                    // Format current date as DD:MM:YYYY
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd:MM:yyyy", Locale.getDefault());
                    String formattedDate = dateFormat.format(new Date());
                    schoolData.put("createdAt", formattedDate);

                    db.collection("schools").document(schoolSymbol)
                        .set(schoolData)
                        .addOnSuccessListener(aVoid -> {
                            // Create all required subcollections
                            createAllSubcollections(schoolSymbol);

                            Toast.makeText(addSchool.this, "בית הספר " + schoolName + " נוסף בהצלחה", Toast.LENGTH_SHORT).show();
                            schoolIdEditText.setText("");
                            schoolNameCard.setVisibility(View.GONE);
                            schoolInfoTextView.setText("מידע אודות בית הספר יתמלא אוטומטית לפי סמל המוסד");
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

    private schoolsDB.School findSchoolById(int schoolId) {
        for (schoolsDB.School school : allSchools) {
            if (school.getSchoolId() == schoolId) {
                return school;
            }
        }
        return null;
    }

    private void createAllSubcollections(String schoolId) {
        // Create all required subcollections
        createEmptyCollection(schoolId, "allowedRakazEmails");
        createEmptyCollection(schoolId, "megamot");
        createEmptyCollection(schoolId, "rakazim");
        createEmptyCollection(schoolId, "managers");
    }

    private void createEmptyCollection(String schoolId, String collectionName) {
        Log.d(TAG, "Creating subcollection: " + collectionName + " for school: " + schoolId);
        CollectionReference collectionRef = db.collection("schools").document(schoolId)
                .collection(collectionName);
        
        // Create a document appropriate for the collection type
        if (collectionName.equals("allowedRakazEmails")) {
            // For allowedRakazEmails, create an initialization document
            Map<String, Object> initData = new HashMap<>();
            initData.put("_init", true);
            
            // Format current date as DD:MM:YYYY
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
            // For other collections, create a dummy document
            Map<String, Object> dummyData = new HashMap<>();
            dummyData.put("dummy", true);
            
            // Format current date as DD:MM:YYYY
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

    public void goBack(View view) {
        onBackPressed();
    }
} 