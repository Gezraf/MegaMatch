package com.project.megamatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class managerLogin extends AppCompatActivity {

    private static final String TAG = "ManagerLogin";
    
    private AutoCompleteTextView schoolAutocomplete;
    private EditText usernameInput, idInput, passwordInput;
    private Button managerLoginButton;
    private ProgressBar progressBar;
    
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    
    private List<schoolsDB.School> allSchools; // All schools from CSV
    private List<schoolsDB.School> firebaseSchools; // Schools that exist in Firebase
    private SchoolAdapter schoolAdapter;
    private schoolsDB.School selectedSchool;
    
    private static final String PREF_KNOWN_SCHOOLS = "knownSchoolIds";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.manager_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.managerLogin), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Load schools data
        schoolsDB.loadSchoolsFromCSV(this);
        Log.d("SchoolDB", "Total schools loaded: " + schoolsDB.getTotalSchoolsCount());

        // Initialize UI elements
        schoolAutocomplete = findViewById(R.id.schoolAutocomplete);
        usernameInput = findViewById(R.id.usernameInput);
        idInput = findViewById(R.id.idInput);
        passwordInput = findViewById(R.id.passwordInput);
        managerLoginButton = findViewById(R.id.managerLoginButton);
        progressBar = findViewById(R.id.progressBar);
        
        // Hide progress bar initially
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        // Initialize collections
        allSchools = new ArrayList<>();
        firebaseSchools = new ArrayList<>();
        
        // Initialize Firestore with offline persistence
        db = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
        db.setFirestoreSettings(settings);
        
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);

        // Check if manager is already logged in (saved session)
        checkIfAlreadyLoggedIn();

        // Load all schools from CSV
        allSchools = schoolsDB.getAllSchools();
        
        // Load schools from Firestore (only ones that exist)
        loadSchoolsFromFirestore();
        
        // Set click listener for login button
        managerLoginButton.setOnClickListener(v -> managerLoginClick());
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
        db.collection("schools")
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
                Toast.makeText(managerLogin.this, "שגיאה בטעינת בתי ספר: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        // Create adapter with schools that exist in Firebase
        schoolAdapter = new SchoolAdapter(this, R.layout.dropdown_item, firebaseSchools);
        schoolAutocomplete.setAdapter(schoolAdapter);
        
        // Set dropdown background to be semi-transparent
        schoolAutocomplete.setDropDownBackgroundResource(R.drawable.dropdown_background);
        
        // Handle school selection from the dropdown
        schoolAutocomplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedSchool = (schoolsDB.School) parent.getItemAtPosition(position);
                schoolAutocomplete.setText(selectedSchool.getSchoolName());
            }
        });
        
        // Handle text changes (for manual entry)
        schoolAutocomplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    // Try to find school by ID or name
                    String query = s.toString().trim();
                    
                    // Check if the entered text is a school ID
                    if (query.matches("\\d+")) {
                        int schoolId = Integer.parseInt(query);
                        schoolsDB.School foundSchool = findSchoolById(schoolId);
                        
                        if (foundSchool != null) {
                            selectedSchool = foundSchool;
                        } else {
                            selectedSchool = null;
                        }
                    } else {
                        // If text doesn't match a selected school, clear the selection
                        if (selectedSchool != null && !selectedSchool.getSchoolName().equals(query)) {
                            selectedSchool = null;
                        }
                    }
                } else {
                    selectedSchool = null;
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private schoolsDB.School findSchoolById(int schoolId) {
        for (schoolsDB.School school : firebaseSchools) {
            if (school.getSchoolId() == schoolId) {
                return school;
            }
        }
        return null;
    }
    
    private class SchoolAdapter extends ArrayAdapter<schoolsDB.School> implements Filterable {
        private List<schoolsDB.School> originalList;
        private List<schoolsDB.School> filteredList;
        
        public SchoolAdapter(managerLogin context, int resource, List<schoolsDB.School> objects) {
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
                    
                    // If there's no constraint, return the whole list
                    if (constraint == null || constraint.length() == 0) {
                        results.values = originalList;
                        results.count = originalList.size();
                        return results;
                    }
                    
                    // Convert to lower case and remove leading/trailing spaces
                    String filterSeq = constraint.toString().toLowerCase().trim();
                    
                    List<schoolsDB.School> filtered = new ArrayList<>();
                    
                    // Filter by school name, ID, or principal name
                    for (schoolsDB.School school : originalList) {
                        String schoolName = school.getSchoolName().toLowerCase();
                        String schoolId = String.valueOf(school.getSchoolId());
                        String principalName = school.getPrincipalName() != null ? 
                                               school.getPrincipalName().toLowerCase() : "";
                                               
                        if (schoolName.contains(filterSeq) || 
                            schoolId.contains(filterSeq) || 
                            principalName.contains(filterSeq)) {
                            filtered.add(school);
                        }
                    }
                    
                    results.values = filtered;
                    results.count = filtered.size();
                    
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
            schoolsDB.School school = getItem(position);
            
            // Display only school name in the dropdown, not ID
            ((android.widget.TextView) view).setText(school.getSchoolName());
            // Make text white and background transparent
            ((android.widget.TextView) view).setTextColor(getResources().getColor(R.color.white));
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            
            return view;
        }
    }
    
    private void checkIfAlreadyLoggedIn() {
        String savedSchoolId = sharedPreferences.getString("loggedInManagerSchoolId", "");
        String savedUsername = sharedPreferences.getString("loggedInManagerUsername", "");
        
        if (!savedSchoolId.isEmpty() && !savedUsername.isEmpty()) {
            // User is already logged in, navigate to manager home page
            Intent intent = new Intent(managerLogin.this, managerPage.class);
            startActivity(intent);
            finish();
        }
    }
    
    private void saveManagerSession(String schoolId, String username) {
        try {
            // Save manager session data in SharedPreferences
            Log.d(TAG, "Saving manager session for: " + username + " in school: " + schoolId);
            sharedPreferences.edit()
                .putString("loggedInManagerSchoolId", schoolId)
                .putString("loggedInManagerUsername", username)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving manager session: " + e.getMessage(), e);
        }
    }
    
    private void goToManagerPage() {
        Intent intent = new Intent(managerLogin.this, managerPage.class);
        startActivity(intent);
        finish();
    }
    
    private void managerLoginClick() {
        // Get input values
        String schoolIdText = "";
        String username = usernameInput.getText().toString().trim();
        String id = idInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        // Validate school selection
        if (selectedSchool == null) {
            String enteredSchool = schoolAutocomplete.getText().toString().trim();
            
            if (enteredSchool.isEmpty()) {
                Toast.makeText(this, "יש לבחור בית ספר", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Check if entered text is a valid school ID
            if (enteredSchool.matches("\\d+")) {
                schoolIdText = enteredSchool;
            } else {
                // Try to find the school by name
                boolean found = false;
                for (schoolsDB.School school : firebaseSchools) {
                    if (school.getSchoolName().equalsIgnoreCase(enteredSchool)) {
                        selectedSchool = school;
                        schoolIdText = String.valueOf(school.getSchoolId());
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    Toast.makeText(this, "בית הספר שהוזן אינו קיים במערכת", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } else {
            schoolIdText = String.valueOf(selectedSchool.getSchoolId());
        }
        
        // Validate other inputs
        if (username.isEmpty()) {
            Toast.makeText(this, "יש להזין שם משתמש", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (id.isEmpty()) {
            Toast.makeText(this, "יש להזין תעודת זהות", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate ID is 9 digits
        if (id.length() != 9 || !id.matches("\\d+")) {
            Toast.makeText(this, "תעודת זהות חייבת להיות באורך 9 ספרות", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (password.isEmpty()) {
            Toast.makeText(this, "יש להזין סיסמה", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate password length
        if (password.length() < 3 || password.length() > 22) {
            Toast.makeText(this, "סיסמה חייבת להיות באורך 3-22 תווים", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress indicator
        progressBar.setVisibility(View.VISIBLE);
        managerLoginButton.setEnabled(false);
        
        // Check if manager exists
        checkManagerExistence(schoolIdText, username, id, password);
    }
    
    private void checkManagerExistence(String schoolId, String username, String id, String password) {
        db.collection("schools").document(schoolId)
            .collection("managers").document(username)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // Verify credentials
                        String storedId = document.getString("id");
                        String storedPassword = document.getString("password");
                        
                        Log.d(TAG, "Found manager document. Checking credentials...");
                        
                        if (id.equals(storedId) && password.equals(storedPassword)) {
                            // Login successful
                            Toast.makeText(managerLogin.this, "התחברת בהצלחה", Toast.LENGTH_SHORT).show();
                            
                            // Save manager session
                            saveManagerSession(schoolId, username);
                            
                            // Navigate to manager page
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                goToManagerPage();
                            }, 1000);
                        } else {
                            // Invalid credentials
                            Toast.makeText(managerLogin.this, "תעודת זהות או סיסמה שגויים", Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            managerLoginButton.setEnabled(true);
                        }
                    } else {
                        // Manager doesn't exist
                        Toast.makeText(managerLogin.this, "לא נמצא מנהל עם שם המשתמש הזה", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        managerLoginButton.setEnabled(true);
                    }
                } else {
                    // Error checking manager
                    Log.e(TAG, "Error checking manager existence: " + task.getException());
                    Toast.makeText(managerLogin.this, "שגיאה בבדיקת פרטי המנהל", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    managerLoginButton.setEnabled(true);
                    
                    // Try offline mode if available
                    fallbackToCacheQuery(schoolId, username, id, password);
                }
            });
    }
    
    private void fallbackToCacheQuery(String schoolId, String username, String id, String password) {
        Log.d(TAG, "Attempting fallback to cached data");
        
        // Using offline persistence to query the cache
        db.collection("schools").document(schoolId)
            .collection("managers").document(username)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    DocumentSnapshot document = task.getResult();
                    
                    // Verify credentials
                    String storedId = document.getString("id");
                    String storedPassword = document.getString("password");
                    
                    if (id.equals(storedId) && password.equals(storedPassword)) {
                        // Login successful (from cache)
                        Toast.makeText(managerLogin.this, "התחברת בהצלחה (מצב לא מקוון)", Toast.LENGTH_SHORT).show();
                        
                        // Save manager session
                        saveManagerSession(schoolId, username);
                        
                        // Navigate to manager page
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            goToManagerPage();
                        }, 1000);
                    } else {
                        // Invalid credentials
                        Toast.makeText(managerLogin.this, "תעודת זהות או סיסמה שגויים", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // No cached data available
                    Toast.makeText(managerLogin.this, "לא ניתן להתחבר במצב לא מקוון", Toast.LENGTH_SHORT).show();
                }
                
                progressBar.setVisibility(View.GONE);
                managerLoginButton.setEnabled(true);
            });
    }
    
    public void goBack(View view) {
        onBackPressed();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Re-enable login button
        if (managerLoginButton != null) {
            managerLoginButton.setEnabled(true);
        }
        
        // Hide progress bar
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }
} 