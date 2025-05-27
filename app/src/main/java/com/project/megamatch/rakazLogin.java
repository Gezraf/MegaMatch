package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class rakazLogin extends AppCompatActivity {

    private AutoCompleteTextView schoolAutocomplete;
    private EditText idInput, passwordInput;
    private Button rakazLoginButton;
    private Button noAccountButton;
    private FirebaseFirestore fireDB;
    private SharedPreferences sharedPreferences;
    private List<schoolsDB.School> allSchools; // All schools from CSV
    private List<schoolsDB.School> firebaseSchools; // Schools that exist in Firebase 
    private SchoolAdapter schoolAdapter;
    private schoolsDB.School selectedSchool;
    private static final String TAG = "RakazLogin";
    private static final String PREF_KNOWN_SCHOOLS = "knownSchoolIds";
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.rakaz_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rakazLogin), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Load schools data
        schoolsDB.loadSchoolsFromCSV(this);
        Log.d("SchoolDB", "Total schools loaded: " + schoolsDB.getTotalSchoolsCount());

        schoolAutocomplete = findViewById(R.id.schoolAutocomplete);
        idInput = findViewById(R.id.idInput);
        passwordInput = findViewById(R.id.passwordInput);
        rakazLoginButton = findViewById(R.id.rakazLoginButton);
        noAccountButton = findViewById(R.id.noAccountButton);
        progressBar = findViewById(R.id.progressBar);
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        // Initialize collections
        allSchools = new ArrayList<>();
        firebaseSchools = new ArrayList<>();
        
        fireDB = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);

        // Check if Rakaz is already logged in (saved session)
        checkIfAlreadyLoggedIn();

        // Load all schools from CSV
        allSchools = schoolsDB.getAllSchools();
        
        // Load schools from Firestore (only ones that exist)
        loadSchoolsFromFirestore();
        
        // Setup noAccountButton to redirect to registration
        noAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveToRakazRegister(v);
            }
        });

        rakazLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rakazLoginClick();
            }
        });
    }

    /**
     * Load schools from Firestore that actually exist
     */
    private void loadSchoolsFromFirestore() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        
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
                for (com.google.firebase.firestore.QueryDocumentSnapshot document : querySnapshot) {
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
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                    });
                } else {
                    // No schools found
                    Log.d(TAG, "No schools found in Firestore");
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading schools: " + e.getMessage(), e);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                
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
                
                // Log the selected school for debugging
                Log.d(TAG, "Selected school: " + selectedSchool.getSchoolName() + " (ID: " + selectedSchool.getSchoolId() + ")");
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

    // מתאם מותאם עם פונקציית סינון מותאמת
    private class SchoolAdapter extends ArrayAdapter<schoolsDB.School> implements Filterable {
        private List<schoolsDB.School> originalList;
        private List<schoolsDB.School> filteredList;

        public SchoolAdapter(rakazLogin context, int resource, List<schoolsDB.School> objects) {
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

    // Check if Rakaz is already logged in
    private void checkIfAlreadyLoggedIn() {
        if (sharedPreferences.contains("loggedInUsername")) {
            Log.d("Auth", "Rakaz session found, auto-login");
            goToNextScreen();
        }
    }


    private void saveRakazSession(String schoolId, String username) {
        // Save session data in SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("loggedInSchoolId", schoolId);
        editor.putString("loggedInUsername", username);
        editor.apply();
    }

    private void goToNextScreen() {
        Intent intent = new Intent(this, LoadingActivity.class);
        startActivity(intent);
        finish();
    }

    public void moveToRakazRegister(View view) {
        Intent intent = new Intent(this, rakazRegister.class);
        
        // Pass the selected school ID if available
        if (selectedSchool != null) {
            intent.putExtra("schoolId", String.valueOf(selectedSchool.getSchoolId()));
            intent.putExtra("schoolName", selectedSchool.getSchoolName());
        }
        
        startActivity(intent);
    }

    // For testing: Grant admin privileges to a rakaz user
    private void grantAdminPrivileges(String schoolId, String username) {
        fireDB.collection("schools").document(schoolId)
                .collection("rakazim").document(username)
                .update("isAdmin", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d("RakazLogin", "Admin privileges granted to " + username);
                    goToNextScreen();
                })
                .addOnFailureListener(e -> {
                    Log.e("RakazLogin", "Failed to grant admin privileges: " + e.getMessage());
                    goToNextScreen(); // Continue anyway
                });
    }

    private void rakazLoginClick() {
        String currentText = schoolAutocomplete.getText().toString().trim();
        
        // Double-check if text matches a school name but selectedSchool isn't set
        if (selectedSchool == null && !currentText.isEmpty()) {
            // Try to find by name
            for (schoolsDB.School school : firebaseSchools) {
                if (school.getSchoolName().equals(currentText)) {
                    selectedSchool = school;
                    Log.d(TAG, "Found school by name: " + school.getSchoolName());
                    break;
                }
            }
            
            // Try to find by ID if it's numeric
            if (selectedSchool == null && currentText.matches("\\d+") && currentText.length() == 6) {
                int schoolId = Integer.parseInt(currentText);
                selectedSchool = findSchoolById(schoolId);
                if (selectedSchool != null) {
                    Log.d(TAG, "Found school by ID: " + selectedSchool.getSchoolName());
                }
            }
        }
        
        String password = passwordInput.getText().toString().trim();
        String id = idInput.getText().toString().trim();

        if (selectedSchool != null && !password.isEmpty() && !id.isEmpty()) {
            // Show loading state
            rakazLoginButton.setEnabled(false);
            rakazLoginButton.setText("מתחבר...");
            
            String schoolId = String.valueOf(selectedSchool.getSchoolId());
            Log.d(TAG, "Attempting login for rakaz with ID: " + id + " at school: " + schoolId);
            
            // Try cache first for better performance, then verify with server if needed
            fireDB.collection("schools").document(schoolId)
                    .collection("rakazim")
                    .whereEqualTo("id", id)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        rakazLoginButton.setEnabled(true);
                        rakazLoginButton.setText("התחברות");
                        
                        if (!querySnapshot.isEmpty()) {
                            // We found a rakaz with this ID
                            com.google.firebase.firestore.DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
                            String username = documentSnapshot.getId(); // Get the username from the document ID
                            Log.d(TAG, "Rakaz document found with username: " + username);
                            
                            String storedPassword = documentSnapshot.getString("password");
                            if (storedPassword != null && storedPassword.equals(password)) {
                                Log.d(TAG, "Login successful");
                                // Save session and redirect
                                saveRakazSession(schoolId, username);
                                
                                // For testing purposes: grant admin privileges to specific account
                                if (username.equals("admin")) {
                                    grantAdminPrivileges(schoolId, username);
                                } else {
                                    goToNextScreen();
                                }
                            } else {
                                Log.d(TAG, "Password mismatch");
                                Toast.makeText(rakazLogin.this, "סיסמה שגויה", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.d(TAG, "Rakaz with ID " + id + " not found");
                            Toast.makeText(rakazLogin.this, "רכז עם תעודת זהות זו לא נמצא", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        rakazLoginButton.setEnabled(true);
                        rakazLoginButton.setText("התחברות");
                        
                        Log.e(TAG, "Firestore error: " + e.getMessage(), e);
                        
                                                    // Check for network connectivity issues
                        if (e.getMessage() != null && 
                            (e.getMessage().contains("network") || e.getMessage().contains("offline") || 
                             e.getMessage().contains("unavailable"))) {
                            Toast.makeText(rakazLogin.this, "בעיית תקשורת. בדוק את החיבור לאינטרנט", Toast.LENGTH_LONG).show();
                            // Try to use cache as a last resort even with network issues
                            fallbackToCacheQuery(schoolId, id, password);
                        } else {
                            Toast.makeText(rakazLogin.this, "שגיאה בגישה לנתונים", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else if (selectedSchool == null) {
            Toast.makeText(rakazLogin.this, "נא לבחור בית ספר", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(rakazLogin.this, "נא למלא את כל השדות", Toast.LENGTH_SHORT).show();
        }
    }
    

    
    private void fallbackToCacheQuery(String schoolId, String id, String password) {
        Log.d(TAG, "Attempting fallback to cache query");
        // Try using cache as a last resort
        fireDB.collection("schools").document(schoolId)
                .collection("rakazim")
                .whereEqualTo("id", id)
                .get(com.google.firebase.firestore.Source.CACHE)
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
                        String username = documentSnapshot.getId(); // Get the username from the document ID
                        Log.d(TAG, "Rakaz found in cache with username: " + username);
                        
                        String storedPassword = documentSnapshot.getString("password");
                        if (storedPassword != null && storedPassword.equals(password)) {
                            Log.d(TAG, "Password match from cache");
                            saveRakazSession(schoolId, username);
                            goToNextScreen();
                        } else {
                            Toast.makeText(rakazLogin.this, "סיסמה שגויה", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(rakazLogin.this, "רכז עם תעודת זהות זו לא נמצא", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Cache query failed: " + e.getMessage(), e);
                    Toast.makeText(rakazLogin.this, "שגיאה בגישה לנתונים", Toast.LENGTH_SHORT).show();
                });
    }
}
