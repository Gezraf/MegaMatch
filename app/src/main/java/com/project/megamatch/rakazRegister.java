package com.project.megamatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class rakazRegister extends AppCompatActivity {

    private AutoCompleteTextView schoolAutocomplete;
    private EditText idInput, emailInput, usernameInput, passwordInput, confirmPasswordInput;
    private ProgressBar progressBar;
    private FirebaseFirestore fireDB;
    private static final String TAG = "RakazRegister";
    private List<schoolsDB.School> allSchools; // All schools from CSV
    private List<schoolsDB.School> firebaseSchools; // Schools that exist in Firebase
    private SchoolAdapter schoolAdapter;
    private schoolsDB.School selectedSchool;
    private SharedPreferences sharedPreferences;
    private static final String PREF_KNOWN_SCHOOLS = "knownSchoolIds";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.rakaz_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rakazRegister), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // קישור לרכיבי UI
        schoolAutocomplete = findViewById(R.id.schoolAutocomplete);
        idInput = findViewById(R.id.idInput);
        emailInput = findViewById(R.id.emailInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        progressBar = findViewById(R.id.progressBar);

        // אתחול FirebaseFirestore
        fireDB = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        
        // Initialize collections
        allSchools = new ArrayList<>();
        firebaseSchools = new ArrayList<>();
        
        // Load schools data
        schoolsDB.loadSchoolsFromCSV(this);
        allSchools = schoolsDB.getAllSchools();
        
        // Load schools from Firestore
        loadSchoolsFromFirestore();
        
        // Check if we received a school selection from the login screen
        String schoolId = getIntent().getStringExtra("schoolId");
        String schoolName = getIntent().getStringExtra("schoolName");
        
        if (schoolId != null && schoolName != null) {
            // Check if this school exists in Firestore (will be in our filtered list)
            if (schoolId.matches("\\d+")) {
                int id = Integer.parseInt(schoolId);
                selectedSchool = findSchoolById(id);
                
                if (selectedSchool != null) {
                    schoolAutocomplete.setText(selectedSchool.getSchoolName());
                    // Focus on the next field (ID)
                    idInput.requestFocus();
                }
            }
        }
    }

    /**
     * Load schools from Firestore that actually exist
     */
    private void loadSchoolsFromFirestore() {
        progressBar.setVisibility(View.VISIBLE);
        
        // Clear previous data
        firebaseSchools.clear();
        
        Log.d(TAG, "Loading schools from Firestore...");
        
        // Load previously known schools as a starting point
        Set<String> previouslyFoundSchools = sharedPreferences.getStringSet(PREF_KNOWN_SCHOOLS, new HashSet<>());
        if (!previouslyFoundSchools.isEmpty()) {
            Log.d(TAG, "Found " + previouslyFoundSchools.size() + " previously discovered schools");
            
            // Pre-populate with previously known schools
            for (String schoolId : previouslyFoundSchools) {
                addSchoolById(schoolId);
            }
            
            // Setup autocomplete with what we have so far
            setupSchoolAutocomplete();
        }
        
        // Query Firestore for schools that exist
        fireDB.collection("schools")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                Log.d(TAG, "Found " + querySnapshot.size() + " schools in Firestore");
                
                // Create a set of discovered school IDs
                Set<String> schoolIds = new HashSet<>();
                for (QueryDocumentSnapshot document : querySnapshot) {
                    schoolIds.add(document.getId());
                    Log.d(TAG, "Found school: " + document.getId());
                }
                
                // Save discovered school IDs for future use
                sharedPreferences.edit().putStringSet(PREF_KNOWN_SCHOOLS, schoolIds).apply();
                
                // Process schools
                if (!schoolIds.isEmpty()) {
                    // Clear any previous data
                    firebaseSchools.clear();
                    
                    // Add each school from the list
                    for (String schoolId : schoolIds) {
                        addSchoolById(schoolId);
                    }
                    
                    // Update the UI
                    runOnUiThread(() -> {
                        Log.d(TAG, "Updating autocomplete with " + firebaseSchools.size() + " schools");
                        setupSchoolAutocomplete();
                        progressBar.setVisibility(View.GONE);
                    });
                } else {
                    // No schools found
                    Log.d(TAG, "No schools found in Firestore");
                    progressBar.setVisibility(View.GONE);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading schools: " + e.getMessage(), e);
                progressBar.setVisibility(View.GONE);
                
                // Show the autocomplete with what we have (if anything)
                setupSchoolAutocomplete();
            });
    }
    
    /**
     * Add a school to the firebaseSchools list by its ID
     */
    private void addSchoolById(String schoolId) {
        boolean found = false;
        
        // Look for the school in our CSV data
        for (schoolsDB.School school : allSchools) {
            String csvSchoolId = String.valueOf(school.getSchoolId());
            String trimmedCsvId = csvSchoolId.replaceFirst("^0+(?!$)", ""); // Remove leading zeros
            
            if (csvSchoolId.equals(schoolId) || trimmedCsvId.equals(schoolId)) {
                // Only add if not already in the list
                if (!schoolExists(school)) {
                    firebaseSchools.add(school);
                    found = true;
                    Log.d(TAG, "Added school: " + school.getSchoolName() + " (ID: " + csvSchoolId + ")");
                }
                break;
            }
        }
        
        // If school not found in CSV, create a placeholder
        if (!found) {
            try {
                schoolsDB.School placeholderSchool = new schoolsDB.School("בית ספר " + schoolId, "");
                placeholderSchool.setSchoolId(Integer.parseInt(schoolId));
                
                // Only add if not already in the list
                if (!schoolExists(placeholderSchool)) {
                    firebaseSchools.add(placeholderSchool);
                    Log.d(TAG, "Added placeholder school: בית ספר " + schoolId);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing school ID: " + schoolId, e);
            }
        }
    }
    
    /**
     * Check if a school already exists in the firebaseSchools list
     */
    private boolean schoolExists(schoolsDB.School school) {
        for (schoolsDB.School existingSchool : firebaseSchools) {
            if (existingSchool.getSchoolId() == school.getSchoolId()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Find a school in the firebaseSchools list by its ID
     */
    private schoolsDB.School findSchoolById(int schoolId) {
        for (schoolsDB.School school : firebaseSchools) {
            if (school.getSchoolId() == schoolId) {
                return school;
            }
        }
        return null;
    }

    private void setupSchoolAutocomplete() {
        // יצירת מתאם מותאם עם בתי הספר שקיימים בפיירבייס
        schoolAdapter = new SchoolAdapter(this, android.R.layout.simple_dropdown_item_1line, firebaseSchools);
        schoolAutocomplete.setAdapter(schoolAdapter);
        
        // טיפול בבחירת בית ספר מהרשימה
        schoolAutocomplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedSchool = (schoolsDB.School) parent.getItemAtPosition(position);
                schoolAutocomplete.setText(selectedSchool.getSchoolName());
            }
        });
        
        // טיפול בהקלדה בשדה החיפוש
        schoolAutocomplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                
                // בדיקה אם הוזן מספר סימול בית ספר
                if (input.matches("\\d+") && input.length() == 6) {
                    // Check if this school ID exists in the Firestore schools
                    int schoolId = Integer.parseInt(input);
                    selectedSchool = findSchoolById(schoolId);
                    
                    if (selectedSchool != null) {
                        schoolAutocomplete.setText(selectedSchool.getSchoolName());
                        schoolAutocomplete.dismissDropDown();
                    }
                } else if (!input.equals(selectedSchool != null ? selectedSchool.getSchoolName() : "")) {
                    // Only reset selection if the text doesn't match the current selected school
                    selectedSchool = null;
                    
                    // Check if the exact text matches a school name in our filtered list
                    for (schoolsDB.School school : firebaseSchools) {
                        if (school.getSchoolName().equals(input)) {
                            selectedSchool = school;
                            break;
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // מתאם מותאם עם פונקציית סינון מותאמת
    private class SchoolAdapter extends ArrayAdapter<schoolsDB.School> implements Filterable {
        private List<schoolsDB.School> originalList;
        private List<schoolsDB.School> filteredList;

        public SchoolAdapter(rakazRegister context, int resource, List<schoolsDB.School> objects) {
            super(context, resource, objects);
            this.originalList = new ArrayList<>(objects);
            this.filteredList = new ArrayList<>(objects);
        }

        @Override
        public int getCount() {
            return filteredList.size();
        }

        @Override
        public schoolsDB.School getItem(int position) {
            return filteredList.get(position);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    
                    // אם אין מחרוזת חיפוש, מחזירים את כל הרשימה
                    if (constraint == null || constraint.length() == 0) {
                        results.values = originalList;
                        results.count = originalList.size();
                    } else {
                        List<schoolsDB.School> filteredSchools = new ArrayList<>();
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        
                        // Check if filter is a school ID
                        if (filterPattern.matches("\\d+") && filterPattern.length() <= 6) {
                            for (schoolsDB.School school : originalList) {
                                if (String.valueOf(school.getSchoolId()).startsWith(filterPattern)) {
                                    filteredSchools.add(school);
                                }
                            }
                        }
                        
                        // סינון בתי ספר לפי שם
                        for (schoolsDB.School school : originalList) {
                            if (school.getSchoolName().toLowerCase().contains(filterPattern)) {
                                // Don't add duplicates if already added by ID match
                                if (!filteredSchools.contains(school)) {
                                    filteredSchools.add(school);
                                }
                            }
                        }
                        
                        results.values = filteredSchools;
                        results.count = filteredSchools.size();
                    }
                    
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredList = (List<schoolsDB.School>) results.values;
                    notifyDataSetChanged();
                }
            };
        }
        
        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            
            // Make sure the text displayed is the school name
            android.widget.TextView text = (android.widget.TextView) view;
            schoolsDB.School school = getItem(position);
            text.setText(school.getSchoolName());
            
            return view;
        }
    }

    // פונקציה להרשמת רכז
    public void registerRakaz(View view) {
        // בדיקת תקינות הקלט
        if (!validateInput()) {
            return;
        }

        // הצגת סרגל התקדמות
        progressBar.setVisibility(View.VISIBLE);

        // קבלת ערכי הקלט
        String schoolId = String.valueOf(selectedSchool.getSchoolId());
        String id = idInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // בדיקה האם האימייל מאושר ברשימת allowedRakazEmails
        checkAllowedRakazEmail(schoolId, email, id, username, password);
    }

    // בדיקת תקינות הקלט
    private boolean validateInput() {
        String id = idInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // בדיקת שדות ריקים
        if (selectedSchool == null || TextUtils.isEmpty(id) ||
                TextUtils.isEmpty(email) || TextUtils.isEmpty(username) || 
                TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            if (selectedSchool == null) {
                Toast.makeText(this, "נא לבחור בית ספר", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "נא למלא את כל השדות", Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        // בדיקת תקינות האימייל
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "נא להזין כתובת אימייל תקינה", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        // בדיקת תקינות תעודת זהות (9 ספרות)
        if (id.length() != 9) {
            Toast.makeText(this, "תעודת זהות חייבת להיות 9 ספרות", Toast.LENGTH_SHORT).show();
            return false;
        }

        // בדיקת התאמת סיסמאות
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "הסיסמאות אינן תואמות", Toast.LENGTH_SHORT).show();
            return false;
        }

        // בדיקת אורך שם משתמש
        if (username.length() < 4) {
            Toast.makeText(this, "שם המשתמש חייב להכיל לפחות 4 תווים", Toast.LENGTH_SHORT).show();
            return false;
        }

        // בדיקת אורך סיסמה
        if (password.length() < 6) {
            Toast.makeText(this, "הסיסמה חייבת להכיל לפחות 6 תווים", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    // בדיקה האם האימייל מאושר ברשימת allowedRakazEmails
    private void checkAllowedRakazEmail(String schoolId, String email, String id,
                                       String username, String password) {
        // Normalize the email by trimming and converting to lowercase
        String normalizedEmail = email.trim().toLowerCase();
        
        // Log for debugging
        Log.d(TAG, "Checking email: " + normalizedEmail + " for school ID: " + schoolId);
        
        // First try with the exact email as document ID
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(normalizedEmail)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        handleEmailDocumentFound(documentSnapshot, schoolId, normalizedEmail, id, username, password);
                    } else {
                        // If not found, try querying the collection where email field equals the input
                        Log.d(TAG, "Email not found as document ID, trying to query by email field");
                        fireDB.collection("schools").document(schoolId)
                                .collection("allowedRakazEmails")
                                .whereEqualTo("email", normalizedEmail)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    if (!querySnapshot.isEmpty()) {
                                        // Found by email field
                                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                                        Log.d(TAG, "Found email in query: " + doc.getId());
                                        handleEmailDocumentFound(doc, schoolId, normalizedEmail, id, username, password);
                                    } else {
                                        // Try one more time with the original case of the email
                                        Log.d(TAG, "Email not found in query, trying with original case: " + email);
                                        fireDB.collection("schools").document(schoolId)
                                                .collection("allowedRakazEmails").document(email)
                                                .get()
                                                .addOnSuccessListener(docSnapshot -> {
                                                    if (docSnapshot.exists()) {
                                                        Log.d(TAG, "Found email with original case");
                                                        handleEmailDocumentFound(docSnapshot, schoolId, email, id, username, password);
                                                    } else {
                                                        // Not found in any attempt
                                                        progressBar.setVisibility(View.GONE);
                                                        Log.e(TAG, "Email not found in allowed list: " + email);
                                                        Toast.makeText(rakazRegister.this, 
                                                                "האימייל שלך לא נמצא ברשימת הרכזים המאושרים לבית הספר הזה", 
                                                                Toast.LENGTH_LONG).show();
                                                    }
                                                })
                                                .addOnFailureListener(e -> handleEmailCheckFailure(e));
                                    }
                                })
                                .addOnFailureListener(e -> handleEmailCheckFailure(e));
                    }
                })
                .addOnFailureListener(e -> handleEmailCheckFailure(e));
    }
    
    private void handleEmailDocumentFound(DocumentSnapshot documentSnapshot, String schoolId, String email, 
                                         String id, String username, String password) {
                        // Email exists in allowed list, check if approved and not already registered
                        Boolean approved = documentSnapshot.getBoolean("approved");
                        Boolean registered = documentSnapshot.getBoolean("registered");
                        String storedId = documentSnapshot.getString("id");
                        String storedFirstName = documentSnapshot.getString("firstName");
                        String storedLastName = documentSnapshot.getString("lastName");
                        String storedMegama = documentSnapshot.getString("megama");

        Log.d(TAG, "Email found. Approved: " + approved + ", Registered: " + registered);
        Log.d(TAG, "Stored ID: " + storedId);
        Log.d(TAG, "Input ID: " + id);

                        if (approved == null || !approved) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(rakazRegister.this, 
                                    "האימייל שלך לא אושר עדיין על ידי מנהל המערכת", 
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (registered != null && registered) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(rakazRegister.this, 
                                    "האימייל הזה כבר רשום במערכת", 
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Verify that ID matches
                        if (storedId == null || !storedId.equals(id)) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(rakazRegister.this, 
                                    "תעודת הזהות אינה תואמת לאימייל המאושר", 
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Proceed with registration - check if username already exists
                        checkUsernameExists(schoolId, email, storedFirstName, storedLastName, username, password, storedMegama);
                    }
    
    private void handleEmailCheckFailure(Exception e) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(rakazRegister.this, 
                            "שגיאה בבדיקת האימייל: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error checking allowed email", e);
    }

    // בדיקה האם שם המשתמש קיים כבר במערכת
    private void checkUsernameExists(String schoolId, String email, String firstName, 
                                    String lastName, String username, String password, String megama) {
        fireDB.collection("schools").document(schoolId)
                .collection("rakazim").document(username)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(rakazRegister.this, 
                                "שם משתמש זה תפוס, אנא בחר שם משתמש אחר", Toast.LENGTH_SHORT).show();
                    } else {
                        // רישום הרכז החדש
                        registerNewRakaz(schoolId, email, firstName, lastName, username, password, megama);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(rakazRegister.this, 
                            "שגיאה בבדיקת שם המשתמש: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error checking username", e);
                });
    }

    // רישום הרכז החדש למערכת
    private void registerNewRakaz(String schoolId, String email, String firstName, 
                                 String lastName, String username, String password, String megama) {
        // Create a new document for the rakaz
        Map<String, Object> rakazData = new HashMap<>();
        rakazData.put("firstName", firstName);
        rakazData.put("lastName", lastName);
        rakazData.put("email", email);
        rakazData.put("id", idInput.getText().toString().trim());
        rakazData.put("password", password);
        rakazData.put("megama", megama);
        
        // Format current date as DD:MM:YYYY
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd:MM:yyyy", java.util.Locale.getDefault());
        String formattedDate = dateFormat.format(new java.util.Date());
        rakazData.put("createdAt", formattedDate);
        
        fireDB.collection("schools").document(schoolId)
                .collection("rakazim").document(username)
                .set(rakazData)
                .addOnSuccessListener(aVoid -> {
                    // Update the email's registration status
                    updateEmailRegistrationStatus(schoolId, email, username);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(rakazRegister.this, 
                            "שגיאה ברישום חשבון חדש: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error registering new rakaz", e);
                });
    }

    // עדכון סטטוס הרישום של האימייל ברשימה
    private void updateEmailRegistrationStatus(String schoolId, String email, String username) {
        // Normalize the email by trimming and converting to lowercase
        String normalizedEmail = email.trim().toLowerCase();
        
        // Update the registered status in allowedRakazEmails collection
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("registered", true); // Now enabling this for production use
        updateData.put("username", username); // Store the username used for registration
        
        // Format current date as DD:MM:YYYY
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd:MM:yyyy", java.util.Locale.getDefault());
        String formattedDate = dateFormat.format(new java.util.Date());
        updateData.put("registeredAt", formattedDate);
        
        // First try with normalized email
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(normalizedEmail)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Found with normalized email
                        updateEmailDocument(schoolId, normalizedEmail, updateData);
                    } else {
                        // Try with original email case
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email)
                                .get()
                                .addOnSuccessListener(docSnapshot -> {
                                    if (docSnapshot.exists()) {
                                        // Found with original case
                                        updateEmailDocument(schoolId, email, updateData);
                                    } else {
                                        // Try to find by querying
                                        fireDB.collection("schools").document(schoolId)
                                                .collection("allowedRakazEmails")
                                                .whereEqualTo("email", normalizedEmail)
                                                .get()
                                                .addOnSuccessListener(querySnapshot -> {
                                                    if (!querySnapshot.isEmpty()) {
                                                        // Found by query
                                                        String docId = querySnapshot.getDocuments().get(0).getId();
                                                        updateEmailDocument(schoolId, docId, updateData);
                                                    } else {
                                                        // Not found, show error
                                                        handleUpdateFailure(new Exception("Email document not found"));
                                                    }
                                                })
                                                .addOnFailureListener(this::handleUpdateFailure);
                                    }
                                })
                                .addOnFailureListener(this::handleUpdateFailure);
                    }
                })
                .addOnFailureListener(this::handleUpdateFailure);
    }
    
    private void updateEmailDocument(String schoolId, String emailDocId, Map<String, Object> updateData) {
        Log.d(TAG, "Updating email document: " + emailDocId);
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(emailDocId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(rakazRegister.this, 
                            "נרשמת בהצלחה! כעת תוכל להתחבר למערכת", 
                            Toast.LENGTH_LONG).show();
                    
                    // Go to login screen
                    goToLoginScreen();
                })
                .addOnFailureListener(this::handleUpdateFailure);
    }
    
    private void handleUpdateFailure(Exception e) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(rakazRegister.this, 
                            "החשבון נוצר אך הייתה בעיה בעדכון סטטוס הרישום: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error updating email registration status", e);
                    
                    // Still go to login screen since the rakaz account was created
                    goToLoginScreen();
    }

    // מעבר למסך ההתחברות
    private void goToLoginScreen() {
        Intent intent = new Intent(rakazRegister.this, rakazLogin.class);
        startActivity(intent);
        finish();
    }

    // מעבר למסך ההתחברות באמצעות לחיצה על כפתור "יש לי חשבון"
    public void moveToRakazLogin(View view) {
        Intent intent = new Intent(rakazRegister.this, rakazLogin.class);
        startActivity(intent);
        finish();
    }
}
