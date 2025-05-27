package com.project.megamatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class managerPage extends AppCompatActivity {

    private static final String TAG = "ManagerPage";
    
    private TextView schoolNameTextView;
    private TextView managerNameTextView;
    private SharedPreferences sharedPreferences;
    private FirebaseFirestore db;
    
    private String schoolId;
    private String username;
    private String managerFullName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.manager_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.managerPageLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI elements
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        managerNameTextView = findViewById(R.id.managerNameTextView);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get saved manager info
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        schoolId = sharedPreferences.getString("loggedInManagerSchoolId", "");
        username = sharedPreferences.getString("loggedInManagerUsername", "");

        // If not logged in, redirect to login
        if (schoolId.isEmpty() || username.isEmpty()) {
            Intent intent = new Intent(managerPage.this, managerLogin.class);
            startActivity(intent);
            finish();
            return;
        }

        // Load manager details
        loadManagerDetails();
    }

    private void loadManagerDetails() {
        // Load manager details from Firestore
        db.collection("schools").document(schoolId)
            .collection("managers").document(username)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // Get manager details
                        managerFullName = document.getString("fullName");
                        
                        // Update UI
                        runOnUiThread(() -> {
                            managerNameTextView.setText(managerFullName);
                            
                            // Load school name
                            loadSchoolName();
                        });
                    } else {
                        Log.w(TAG, "Manager document doesn't exist!");
                        showError("לא נמצאו פרטי מנהל");
                    }
                } else {
                    Log.e(TAG, "Error loading manager details", task.getException());
                    showError("שגיאה בטעינת פרטי מנהל");
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
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void manageRakazim(View view) {
        Intent intent = new Intent(managerPage.this, manageRakaz.class);
        intent.putExtra("schoolId", schoolId);
        startActivity(intent);
    }

    public void logout(View view) {
        // Clear manager session
        sharedPreferences.edit()
            .remove("loggedInManagerSchoolId")
            .remove("loggedInManagerUsername")
            .apply();

        // Redirect to login page
        Intent intent = new Intent(managerPage.this, loginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 