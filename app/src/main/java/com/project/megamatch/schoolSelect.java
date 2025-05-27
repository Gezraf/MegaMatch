package com.project.megamatch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.project.megamatch.adapters.SchoolAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Activity for selecting a school from a list
 */
public class schoolSelect extends AppCompatActivity implements SchoolAdapter.OnSchoolClickListener {

    private RecyclerView recyclerViewSchools;
    private SchoolAdapter schoolAdapter;
    private AutoCompleteTextView autoCompleteSearch;
    private ProgressBar progressBar;
    private TextView textViewNoSchools;
    private View loadingOverlay;
    private TextView textViewRealtimeStatus;
    
    private FirebaseFirestore fireDB;
    private SharedPreferences sharedPreferences;
    private List<schoolsDB.School> allSchools; // All schools from CSV
    private List<schoolsDB.School> firebaseSchools; // Schools that exist in Firebase
    private List<schoolsDB.School> filteredSchools; // Schools filtered by search text
    private Map<String, schoolsDB.School> schoolNameToSchool; // Map for quick lookup
    private List<String> schoolNames; // List of school names for autocomplete
    
    private static final String TAG = "SchoolSelect";
    private static final String PREF_KNOWN_SCHOOLS = "knownSchoolIds";
    
    // Real-time listener for school changes
    private com.google.firebase.firestore.ListenerRegistration schoolsListener;

    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.school_select);
        
        // Initialize UI elements
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        recyclerViewSchools = findViewById(R.id.recyclerViewSchools);
        autoCompleteSearch = findViewById(R.id.editTextSearch);
        progressBar = findViewById(R.id.progressBar);
        textViewNoSchools = findViewById(R.id.textViewNoSchools);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        textViewRealtimeStatus = findViewById(R.id.textViewRealtimeStatus);
        
        // Check if user is admin
        Intent intent = getIntent();
        isAdmin = intent.getBooleanExtra("isAdmin", false);
        
        // If admin, change title
        if (isAdmin) {
            getSupportActionBar().setTitle("בחר בית ספר להוספת מנהל");
        }
        
        // Initialize Firebase and SharedPreferences
        fireDB = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);

        // Initialize collections
        allSchools = new ArrayList<>();
        firebaseSchools = new ArrayList<>();
        filteredSchools = new ArrayList<>();
        schoolNameToSchool = new HashMap<>();
        schoolNames = new ArrayList<>();

        // Set up RecyclerView
        recyclerViewSchools.setLayoutManager(new LinearLayoutManager(this));
        schoolAdapter = new SchoolAdapter(filteredSchools, this);
        recyclerViewSchools.setAdapter(schoolAdapter);
        
        // Load schools from CSV, then filter by Firebase
        loadSchoolsFromCSV();
        loadSchoolsFromFirestore();
    }
    
    /**
     * Load all schools from CSV
     */
    private void loadSchoolsFromCSV() {
        schoolsDB.loadSchoolsFromCSV(this);
        allSchools = schoolsDB.getAllSchools();
        Log.d(TAG, "נטענו " + allSchools.size() + " בתי ספר מקובץ CSV");
    }
    
    /**
     * Load schools from Firestore and filter the CSV list
     */
    private void loadSchoolsFromFirestore() {
        showLoading(true);
                
        // Clear previous data
        firebaseSchools.clear();
        schoolNameToSchool.clear();
        schoolNames.clear();
        
        Log.d(TAG, "מתחיל טעינת בתי ספר מפיירסטור...");
        
        // Use the simplest, most direct approach
        fireDB.collection("schools")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                Log.d(TAG, "נמצאו " + querySnapshot.size() + " בתי ספר");
                
                // Create a set of discovered school IDs
                Set<String> schoolIds = new HashSet<>();
                for (QueryDocumentSnapshot document : querySnapshot) {
                    schoolIds.add(document.getId());
                    Log.d(TAG, "נמצא בית ספר: " + document.getId());
                }
                
                // Save discovered school IDs for future use
                sharedPreferences.edit().putStringSet(PREF_KNOWN_SCHOOLS, schoolIds).apply();
                
                // Process schools
                if (!schoolIds.isEmpty()) {
                    // Match with CSV data or create placeholders
                    for (String schoolId : schoolIds) {
                        boolean found = false;
                        
                        // Look for the school in our CSV data
                    for (schoolsDB.School school : allSchools) {
                            String csvSchoolId = String.valueOf(school.getSchoolId());
                            String trimmedCsvId = csvSchoolId.replaceFirst("^0+(?!$)", "");
                            
                            if (csvSchoolId.equals(schoolId) || trimmedCsvId.equals(schoolId)) {
                                firebaseSchools.add(school);
                                schoolNameToSchool.put(school.getSchoolName(), school);
                                schoolNames.add(school.getSchoolName());
                                found = true;
                            break;
                        }
                    }
                    
                        // If school not found in CSV, create a placeholder
                        if (!found) {
                            try {
                                schoolsDB.School placeholderSchool = new schoolsDB.School("בית ספר " + schoolId, "");
                                placeholderSchool.setSchoolId(Integer.parseInt(schoolId));
                                
                                firebaseSchools.add(placeholderSchool);
                                String schoolName = "בית ספר " + schoolId;
                                schoolNameToSchool.put(schoolName, placeholderSchool);
                                schoolNames.add(schoolName);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "שגיאה בפירוש מזהה בית ספר: " + schoolId, e);
                            }
                        }
                    }
                    
                    // Update UI on main thread
                    runOnUiThread(() -> {
                        Log.d(TAG, "מציג " + firebaseSchools.size() + " בתי ספר");
                        updateUI();
                        
                        // Set up real-time listener
                        setupRealtimeSchoolListener();
                    });
                } else {
                    runOnUiThread(() -> {
                        showError("לא נמצאו בתי ספר במערכת");
                    });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "שגיאה בטעינת בתי ספר: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    showError("שגיאה בטעינת בתי ספר: " + e.getMessage());
                });
            });
    }
    
    private void listAllSchoolDocuments(Set<String> allDiscoveredSchoolIds, AtomicInteger pendingOperations) {
        Log.d(TAG, "DEBUG-LIST-ALL: Attempting to list all school documents directly");
        
        // Force online mode to bypass cache
        fireDB.collection("schools")
            .get(com.google.firebase.firestore.Source.SERVER) // Force server access, bypass cache
            .addOnSuccessListener(querySnapshot -> {
                Log.d(TAG, "DEBUG-LIST-ALL: Found " + querySnapshot.size() + " schools directly");
                
                for (QueryDocumentSnapshot document : querySnapshot) {
                    String schoolId = document.getId();
                    allDiscoveredSchoolIds.add(schoolId);
                    Log.d(TAG, "DEBUG-LIST-ALL-FOUND: " + schoolId);
                }
                
                // Setup real-time listener after initial load
                setupRealtimeSchoolListener();
                
                // Verify all discovered school IDs before processing
                verifySchoolDocumentsExist(allDiscoveredSchoolIds, verifiedIds -> {
                    Log.d(TAG, "Verified " + verifiedIds.size() + " out of " + allDiscoveredSchoolIds.size() + " schools");
                    processAndSaveSchools(verifiedIds);
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "DEBUG-LIST-ALL-ERROR: Failed to list school documents: " + e.getMessage(), e);
                completeOperation(allDiscoveredSchoolIds, pendingOperations);
            });
    }
    
    /**
     * Verify that each school ID actually exists as a document in Firestore
     */
    private void verifySchoolDocumentsExist(Set<String> schoolIds, Consumer<Set<String>> callback) {
        Set<String> verifiedIds = new HashSet<>();
        AtomicInteger pendingChecks = new AtomicInteger(schoolIds.size());
        
        if (schoolIds.isEmpty()) {
            callback.accept(verifiedIds);
            return;
        }
        
        for (String id : schoolIds) {
            fireDB.collection("schools").document(id)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        verifiedIds.add(id);
                        Log.d(TAG, "Verified school exists: " + id);
                } else {
                        Log.d(TAG, "School does not exist or couldn't verify: " + id);
                    }
                    
                    if (pendingChecks.decrementAndGet() == 0) {
                        callback.accept(verifiedIds);
            }
        });
        }
    }

    private void completeOperation(Set<String> allDiscoveredSchoolIds, AtomicInteger pendingOperations) {
        // If this was the last operation, process the results
        if (pendingOperations.decrementAndGet() == 0) {
            Log.d(TAG, "DEBUG-OPERATIONS-COMPLETE: All discovery operations finished");
        
            // Verify all discovered school IDs before processing
            verifySchoolDocumentsExist(allDiscoveredSchoolIds, verifiedIds -> {
                Log.d(TAG, "Verified " + verifiedIds.size() + " out of " + allDiscoveredSchoolIds.size() + " schools");
                processAndSaveSchools(verifiedIds);
            });
        }
    }
    
    private void processAndSaveSchools(Set<String> schoolIds) {
        if (schoolIds.isEmpty()) {
            Log.w(TAG, "DEBUG-EMPTY-RESULTS: No school IDs discovered from any method");
            showError("לא נמצאו בתי ספר במערכת");
            return;
        }
        
        Log.d(TAG, "DEBUG-PROCESSING: " + schoolIds.size() + " school IDs: " + schoolIds);
        
        // Save discovered school IDs for future use
        sharedPreferences.edit().putStringSet(PREF_KNOWN_SCHOOLS, schoolIds).apply();
        
        // Clear previous schools list before adding new ones
        firebaseSchools.clear();
        schoolNameToSchool.clear();
        schoolNames.clear();
                
        // For each discovered school ID, try to find it in the CSV
        for (String schoolId : schoolIds) {
            boolean found = false;
            
            // Look for the school in our CSV data
            for (schoolsDB.School school : allSchools) {
                String csvSchoolId = String.valueOf(school.getSchoolId());
                String trimmedCsvId = csvSchoolId.replaceFirst("^0+(?!$)", "");
                
                if (csvSchoolId.equals(schoolId) || trimmedCsvId.equals(schoolId)) {
                    firebaseSchools.add(school);
                    schoolNameToSchool.put(school.getSchoolName(), school);
                    schoolNames.add(school.getSchoolName());
                    found = true;
                    Log.d(TAG, "DEBUG-CSV-MATCH: " + school.getSchoolName() + " (ID: " + csvSchoolId + ")");
                            break;
                        }
                    }
            
            // If school not found in CSV, create a placeholder
            if (!found) {
                try {
                    schoolsDB.School placeholderSchool = new schoolsDB.School("בית ספר " + schoolId, "");
                    placeholderSchool.setSchoolId(Integer.parseInt(schoolId));
                    
                    firebaseSchools.add(placeholderSchool);
                    String schoolName = "בית ספר " + schoolId;
                    schoolNameToSchool.put(schoolName, placeholderSchool);
                    schoolNames.add(schoolName);
                    
                    Log.d(TAG, "DEBUG-PLACEHOLDER: Created for " + schoolName);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "DEBUG-PARSE-ERROR: " + schoolId, e);
                }
            }
        }
        
        // Debug the final list of schools
        Log.d(TAG, "DEBUG-FINAL-COUNT: " + firebaseSchools.size() + " schools");
        for (schoolsDB.School school : firebaseSchools) {
            Log.d(TAG, "DEBUG-FINAL-SCHOOL: " + school.getSchoolName() + " (ID: " + school.getSchoolId() + ")");
        }
        
        // Update UI on the main thread to avoid potential crashes
        runOnUiThread(() -> {
            updateUI();
        });
    }
    
    private Set<String> extractSchoolIdsFromSnapshot(QuerySnapshot snapshot, String collectionName) {
        Set<String> schoolIds = new HashSet<>();
        for (QueryDocumentSnapshot document : snapshot) {
            String fullPath = document.getReference().getPath();
            Log.d(TAG, "DEBUG-PATH: " + fullPath + " from " + collectionName);
            
            String[] pathSegments = fullPath.split("/");
            if (pathSegments.length >= 2 && pathSegments[0].equalsIgnoreCase("schools")) {
                String schoolId = pathSegments[1];
                schoolIds.add(schoolId);
                Log.d(TAG, "DEBUG-EXTRACTED: School ID " + schoolId + " from " + collectionName);
            }
        }
        return schoolIds;
    }
    
    private void updateUI() {
        Log.d(TAG, "Updating UI with " + firebaseSchools.size() + " schools");
        
        filteredSchools.clear();
        filteredSchools.addAll(firebaseSchools);
        
        // Update the adapter on the main thread
        schoolAdapter.updateData(filteredSchools);
        
        // Setup autocomplete with current data
        setupAutoComplete();
        
        // Hide loading indicator
        showLoading(false);
        
        // Show appropriate message if no schools
        if (firebaseSchools.isEmpty()) {
            showNoSchoolsMessage(true);
            Log.d(TAG, "No schools to display, showing empty message");
        } else {
            showNoSchoolsMessage(false);
            Log.d(TAG, "Displaying " + firebaseSchools.size() + " schools in RecyclerView");
        }
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        showLoading(false);
        showNoSchoolsMessage(true);
    }
    
    /**
     * Set up the AutoCompleteTextView with school names
     */
    private void setupAutoComplete() {
        // Replace AutoCompleteTextView functionality with direct filtering
        autoCompleteSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

        @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                @Override
            public void afterTextChanged(Editable editable) {
                filterSchools(editable.toString());
            }
        });
    }
    
    /**
     * Filter schools based on search text
     */
    private void filterSchools(String query) {
        if (firebaseSchools == null || firebaseSchools.isEmpty()) return;
        
        filteredSchools.clear();
        
        if (query.isEmpty()) {
            filteredSchools.addAll(firebaseSchools);
                    } else {
            String lowerCaseQuery = query.toLowerCase(Locale.getDefault());
            
            for (schoolsDB.School school : firebaseSchools) {
                if (school.getSchoolName().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    String.valueOf(school.getSchoolId()).contains(lowerCaseQuery) ||
                    (school.getPrincipalName() != null && 
                     school.getPrincipalName().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery))) {
                                    filteredSchools.add(school);
                                }
                            }
                        }
                        
        schoolAdapter.updateData(filteredSchools);
        
        if (filteredSchools.isEmpty()) {
            showNoSchoolsMessage(true);
        } else {
            showNoSchoolsMessage(false);
                    }
    }
    
    private void showLoading(boolean show) {
        if (show) {
            loadingOverlay.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            recyclerViewSchools.setVisibility(View.GONE);
            textViewNoSchools.setVisibility(View.GONE);
        } else {
            loadingOverlay.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }
    }
    
    private void showNoSchoolsMessage(boolean show) {
        if (show) {
            textViewNoSchools.setVisibility(View.VISIBLE);
            recyclerViewSchools.setVisibility(View.GONE);
        } else {
            textViewNoSchools.setVisibility(View.GONE);
            recyclerViewSchools.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSchoolClick(schoolsDB.School school) {
        if (isAdmin) {
            // Admin is adding a manager - navigate to addManager activity
            Intent intent = new Intent(this, addManager.class);
            intent.putExtra("schoolId", String.valueOf(school.getSchoolId()));
            intent.putExtra("schoolName", school.getSchoolName());
            startActivity(intent);
        } else {
            // Regular user is viewing school megamot
        // Save school info to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("loggedInSchoolId", String.valueOf(school.getSchoolId()));
        editor.putString("viewingAsGuest", "true");
        editor.apply();

        // Navigate to school details screen
        Intent intent = new Intent(this, schoolMegamotDetails.class);
        startActivity(intent);
        // Don't finish this activity so it stays in the back stack
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (isAdmin) {
            // Navigate back to adminHub if user is an admin
            Intent intent = new Intent(this, adminHub.class);
            startActivity(intent);
            finish();
        } else {
            // Navigate back to the login page for regular users
        Intent intent = new Intent(this, loginPage.class);
        startActivity(intent);
        finish();
        }
    }

    @Override
    protected void onDestroy() {
        // Remove Firestore listener when activity is destroyed
        if (schoolsListener != null) {
            schoolsListener.remove();
            schoolsListener = null;
        }
        super.onDestroy();
        }
        
    /**
     * Set up a real-time listener for changes to the schools collection
     */
    private void setupRealtimeSchoolListener() {
        Log.d(TAG, "מגדיר מאזין לשינויים בזמן אמת");
        
        // Remove any existing listener
        if (schoolsListener != null) {
            schoolsListener.remove();
        }
        
        // Show real-time status indicator
        textViewRealtimeStatus.setVisibility(View.VISIBLE);
        
        // Create a new listener
        schoolsListener = fireDB.collection("schools")
            .addSnapshotListener((snapshots, e) -> {
                if (e != null) {
                    Log.e(TAG, "האזנה נכשלה:", e);
                    textViewRealtimeStatus.setVisibility(View.GONE);
                    return;
                }
                
                if (snapshots == null) {
                    Log.d(TAG, "אין בתי ספר בעדכון בזמן אמת (צילום ריק)");
                    return;
                }
                
                // Set of current school IDs
                Set<String> currentSchoolIds = getCurrentSchoolIds();
                
                // Set of updated school IDs
                Set<String> updatedSchoolIds = new HashSet<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    updatedSchoolIds.add(doc.getId());
                }
                
                Log.d(TAG, "התקבל עדכון בזמן אמת - נמצאו " + updatedSchoolIds.size() + " בתי ספר");
                
                // Check if the document set has changed
                if (!updatedSchoolIds.equals(currentSchoolIds)) {
                    Log.d(TAG, "רשימת בתי הספר השתנתה, מעדכן ממשק");
                    
                    // Show a Toast message based on what changed
                    runOnUiThread(() -> {
                        textViewRealtimeStatus.setText("עדכון בזמן אמת התקבל!");
                        
                        if (updatedSchoolIds.size() > currentSchoolIds.size()) {
                            Toast.makeText(schoolSelect.this, "נוסף בית ספר חדש", Toast.LENGTH_SHORT).show();
                        } else if (updatedSchoolIds.size() < currentSchoolIds.size()) {
                            Toast.makeText(schoolSelect.this, "בית ספר הוסר", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(schoolSelect.this, "עדכון ברשימת בתי הספר", Toast.LENGTH_SHORT).show();
                        }
                        
                        // Delay resetting status text
                        new Handler().postDelayed(() -> {
                            if (textViewRealtimeStatus != null) {
                                textViewRealtimeStatus.setText("מאזין לשינויים בזמן אמת...");
                }
                        }, 2000);
                    });
                    
                    // Reload all schools - the simplest way to ensure consistency
                    loadSchoolsFromFirestore();
                }
            });
    }
    
    /**
     * Get current set of school IDs from the firebaseSchools list
     */
    private Set<String> getCurrentSchoolIds() {
        Set<String> schoolIds = new HashSet<>();
        for (schoolsDB.School school : firebaseSchools) {
            schoolIds.add(String.valueOf(school.getSchoolId()));
        }
        return schoolIds;
    }
}
