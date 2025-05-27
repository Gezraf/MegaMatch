package com.project.megamatch;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class addRakaz extends AppCompatActivity {

    private static final String TAG = "AddRakaz";
    
    private TextView schoolNameTextView;
    private TextInputLayout firstNameInputLayout;
    private TextInputEditText firstNameInput;
    private TextInputLayout lastNameInputLayout;
    private TextInputEditText lastNameInput;
    private TextInputLayout idInputLayout;
    private TextInputEditText idInput;
    private TextInputLayout megamaInputLayout;
    private TextInputEditText megamaInput;
    private TextInputLayout emailInputLayout;
    private TextInputEditText emailInput;
    private Button addButton;
    private ProgressBar progressBar;
    
    private FirebaseFirestore db;
    private String schoolId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rakaz_add);
        
        // Get intent data
        schoolId = getIntent().getStringExtra("schoolId");
        if (schoolId == null || schoolId.isEmpty()) {
            Toast.makeText(this, "שגיאה: לא התקבל מזהה בית ספר", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize UI elements
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
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Load school name
        loadSchoolName();
        
        // Set up validation for all fields
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
    
    private void loadSchoolName() {
        // Try to find school in the CSV database first
        for (schoolsDB.School school : schoolsDB.getAllSchools()) {
            if (String.valueOf(school.getSchoolId()).equals(schoolId)) {
                schoolNameTextView.setText(school.getSchoolName());
                return;
            }
        }
        
        // If not found in CSV, try to get it from Firestore
        db.collection("schools").document(schoolId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // Check if the school has a "name" field
                        String name = document.getString("name");
                        
                        if (name != null && !name.isEmpty()) {
                            schoolNameTextView.setText(name);
                        } else {
                            // Use a placeholder with the ID
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
    
    private boolean validateEmail() {
        String email = emailInput.getText().toString().trim();
        
        if (email.isEmpty()) {
            emailInputLayout.setError("יש להזין דוא\"ל");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError("יש להזין דוא\"ל תקין");
            return false;
        } else {
            emailInputLayout.setError(null);
            return true;
        }
    }
    
    public void addRakaz(View view) {
        // Validate all fields
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
        
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        addButton.setEnabled(false);
        
        // Check if email already exists
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails").document(email)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // Email already exists
                        progressBar.setVisibility(View.GONE);
                        addButton.setEnabled(true);
                        Toast.makeText(addRakaz.this, "דוא\"ל זה כבר קיים במערכת", Toast.LENGTH_SHORT).show();
                    } else {
                        // Add new email
                        addEmailToAllowedList(email);
                    }
                } else {
                    // Error checking
                    progressBar.setVisibility(View.GONE);
                    addButton.setEnabled(true);
                    Toast.makeText(addRakaz.this, "שגיאה בבדיקת דוא\"ל", Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void addEmailToAllowedList(String email) {
        // Create document data with all fields in the specified order
        Map<String, Object> data = new HashMap<>();
        
        // Format current date as DD:MM:YYYY for consistency with other date fields
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd:MM:yyyy", java.util.Locale.getDefault());
        String formattedDate = dateFormat.format(new java.util.Date());
        
        // Adding fields in the requested order
        data.put("addedAt", formattedDate);
        data.put("approved", true);
        data.put("registered", false);
        data.put("id", idInput.getText().toString().trim());
        data.put("firstName", firstNameInput.getText().toString().trim());
        data.put("lastName", lastNameInput.getText().toString().trim());
        data.put("megama", megamaInput.getText().toString().trim());
        
        // Add email to allowedRakazEmails collection
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails").document(email)
            .set(data)
            .addOnSuccessListener(aVoid -> {
                progressBar.setVisibility(View.GONE);
                addButton.setEnabled(true);
                Toast.makeText(addRakaz.this, "רכז נוסף בהצלחה", Toast.LENGTH_SHORT).show();
                
                // Clear all inputs
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
                Toast.makeText(addRakaz.this, "שגיאה בהוספת דוא\"ל: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    public void goBack(View view) {
        onBackPressed();
    }
} 