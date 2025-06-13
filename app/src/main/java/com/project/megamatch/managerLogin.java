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
        
        // Set listener for item selection
        schoolAutocomplete.setOnItemClickListener((parent, view, position, id) -> {
            selectedSchool = (schoolsDB.School) parent.getItemAtPosition(position);
            Log.d(TAG, "Selected school: " + selectedSchool.getSchoolName() + " (ID: " + selectedSchool.getSchoolId() + ")");
        });

        // Add a TextWatcher to clear selectedSchool if text changes
        schoolAutocomplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // If the user types, the selected school might no longer be valid
                if (selectedSchool != null && !selectedSchool.getSchoolName().equals(s.toString())) {
                    selectedSchool = null;
                }
                
                // If the text is empty, hide the dropdown
                if (s.length() == 0) {
                    schoolAutocomplete.dismissDropDown();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not used
            }
        });
    }
    
    /**
     * מוצא בית ספר לפי מזהה בית ספר.
     * @param schoolId מזהה בית הספר.
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
     * מתאם מותאם אישית (Custom Adapter) עבור ה-AutoCompleteTextView של בחירת בית הספר.
     * מסנן את רשימת בתי הספר על בסיס קלט המשתמש.
     */
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
                    List<schoolsDB.School> suggestions = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        suggestions.addAll(originalList);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        for (schoolsDB.School school : originalList) {
                            if (school.getSchoolName().toLowerCase().contains(filterPattern) ||
                                    String.valueOf(school.getSchoolId()).contains(filterPattern)) {
                                suggestions.add(school);
                            }
                        }
                    }

                    results.values = suggestions;
                    results.count = suggestions.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredList.clear();
                    if (results != null && results.count > 0) {
                        filteredList.addAll((List<schoolsDB.School>) results.values);
                    }
                    notifyDataSetChanged();
                }
            };
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item, parent, false);
            }
            TextView schoolName = convertView.findViewById(R.id.text1);
            schoolName.setText(filteredList.get(position).getSchoolName() + " (ID: " + filteredList.get(position).getSchoolId() + ")");
            return convertView;
        }
    }
    
    /**
     * בודק אם מנהל המערכת כבר מחובר (על בסיס סשן שמור).
     * אם כן, מעביר לדף המנהל.
     */
    private void checkIfAlreadyLoggedIn() {
        boolean loggedIn = sharedPreferences.getBoolean("loggedInManager", false);
        String schoolId = sharedPreferences.getString("loggedInManagerSchoolId", "");
        String username = sharedPreferences.getString("loggedInManagerUsername", "");

        if (loggedIn && !schoolId.isEmpty() && !username.isEmpty()) {
            Log.d(TAG, "Manager already logged in: " + username + " for school: " + schoolId);
            goToManagerPage(schoolId, username);
        }
    }
    
    /**
     * שומר את פרטי סשן המנהל בהעדפות המשותפות.
     * @param schoolId מזהה בית הספר של המנהל.
     * @param username שם המשתמש של המנהל.
     */
    private void saveManagerSession(String schoolId, String username) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("loggedInManager", true);
        editor.putString("loggedInManagerSchoolId", schoolId);
        editor.putString("loggedInManagerUsername", username);
        editor.apply();
        Log.d(TAG, "Manager session saved: " + username + " for school: " + schoolId);
    }
    
    /**
     * עובר לדף המנהל.
     * @param schoolId מזהה בית הספר של המנהל.
     * @param username שם המשתמש של המנהל.
     */
    private void goToManagerPage(String schoolId, String username) {
        Intent intent = new Intent(this, managerPage.class);
        intent.putExtra("schoolId", schoolId);
        intent.putExtra("username", username);
        startActivity(intent);
        finish();
    }
    
    /**
     * מטפל בלחיצה על כפתור ההתחברות של המנהל.
     * מאמת את הקלט ומבצע ניסיון התחברות לפיירסטור.
     */
    private void managerLoginClick() {
        String schoolName = schoolAutocomplete.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String id = idInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (schoolName.isEmpty() || username.isEmpty() || id.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "נא למלא את כל השדות", Toast.LENGTH_SHORT).show();
            return;
        }

        if (id.length() != 9) {
            Toast.makeText(this, "תעודת זהות חייבת להיות באורך 9 ספרות", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!id.matches("\\d+")) {
            Toast.makeText(this, "תעודת זהות יכולה להכיל רק ספרות", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedSchool == null || !selectedSchool.getSchoolName().equals(schoolName)) {
            // If selectedSchool is null or its name doesn't match the entered text, try to find it by name
            boolean found = false;
            for (schoolsDB.School school : firebaseSchools) {
                if (school.getSchoolName().equalsIgnoreCase(schoolName)) {
                    selectedSchool = school;
                    found = true;
                    break;
                }
            }
            if (!found) {
                Toast.makeText(this, "נא לבחור בית ספר מהרשימה המוצעת", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (selectedSchool == null) {
            Toast.makeText(this, "שגיאה: לא נבחר בית ספר תקין", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedSchoolId = String.valueOf(selectedSchool.getSchoolId());

        progressBar.setVisibility(View.VISIBLE);
        managerLoginButton.setEnabled(false);

        Log.d(TAG, "Attempting manager login for school: " + selectedSchoolId + ", username: " + username);

        checkManagerExistence(selectedSchoolId, username, id, password);
    }
    
    /**
     * בודק את קיום המנהל בפיירסטור ומאמת את פרטי ההתחברות.
     * @param schoolId מזהה בית הספר.
     * @param username שם המשתמש של המנהל.
     * @param id תעודת הזהות של המנהל.
     * @param password הסיסמה של המנהל.
     */
    private void checkManagerExistence(String schoolId, String username, String id, String password) {
        db.collection("schools").document(schoolId)
                .collection("managers").document(username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String storedId = document.getString("id");
                            String storedPassword = document.getString("password");
                            
                            Log.d(TAG, "Stored ID: " + storedId + ", Input ID: " + id);
                            Log.d(TAG, "Stored Password length: " + (storedPassword != null ? storedPassword.length() : 0));
                            
                            if (id.equals(storedId) && password.equals(storedPassword)) {
                                Toast.makeText(managerLogin.this, "התחברת בהצלחה", Toast.LENGTH_SHORT).show();
                                saveManagerSession(schoolId, username);
                                new Handler(Looper.getMainLooper()).postDelayed(() -> goToManagerPage(schoolId, username), 1000);
                            } else {
                                if (!id.equals(storedId)) {
                                    Toast.makeText(managerLogin.this, "תעודת זהות שגויה", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(managerLogin.this, "סיסמה שגויה", Toast.LENGTH_SHORT).show();
                                }
                                progressBar.setVisibility(View.GONE);
                                managerLoginButton.setEnabled(true);
                            }
                        } else {
                            Log.d(TAG, "Manager document not found. Attempting cache query.");
                            fallbackToCacheQuery(schoolId, username, id, password);
                        }
                    } else {
                        Log.e(TAG, "Error getting manager document: " + task.getException().getMessage(), task.getException());
                        Toast.makeText(managerLogin.this, "שגיאה בהתחברות: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(View.GONE);
                        managerLoginButton.setEnabled(true);
                    }
                });
    }
    
    /**
     * מבצע שאילתת גיבוי במקרה של כשל בגישה מקוונת, מנסה למצוא את המנהל במטמון הפיירסטור.
     * @param schoolId מזהה בית הספר.
     * @param username שם המנהל.
     * @param id תעודת הזהות של המנהל.
     * @param password הסיסמה של המנהל.
     */
    private void fallbackToCacheQuery(String schoolId, String username, String id, String password) {
        db.collection("schools").document(schoolId)
                .collection("managers").document(username)
                .get(com.google.firebase.firestore.Source.CACHE) // Force cache lookup
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Log.d(TAG, "Found manager in cache.");
                            String storedId = document.getString("id");
                            String storedPassword = document.getString("password");

                            if (id.equals(storedId) && password.equals(storedPassword)) {
                                Toast.makeText(managerLogin.this, "התחברת בהצלחה (ממטמון)", Toast.LENGTH_SHORT).show();
                                saveManagerSession(schoolId, username);
                                new Handler(Looper.getMainLooper()).postDelayed(() -> goToManagerPage(schoolId, username), 1000);
                            } else {
                                Toast.makeText(managerLogin.this, "שם משתמש או סיסמה שגויים", Toast.LENGTH_SHORT).show();
                                progressBar.setVisibility(View.GONE);
                                managerLoginButton.setEnabled(true);
                            }
                        } else {
                            Toast.makeText(managerLogin.this, "שם משתמש לא קיים במערכת", Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            managerLoginButton.setEnabled(true);
                        }
                    } else {
                        Log.e(TAG, "Error getting manager document from cache: " + task.getException().getMessage(), task.getException());
                        Toast.makeText(managerLogin.this, "שגיאה בגישה לנתונים מקומיים: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(View.GONE);
                        managerLoginButton.setEnabled(true);
                    }
                });
    }
    
    public void goBack(View view) {
        onBackPressed();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Ensure progress bar is hidden and button re-enabled on resume
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (managerLoginButton != null) {
            managerLoginButton.setEnabled(true);
        }
    }
} 