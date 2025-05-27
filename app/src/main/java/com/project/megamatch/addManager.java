package com.project.megamatch;

import android.content.Intent;
import android.os.Bundle;
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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class addManager extends AppCompatActivity {

    private TextView schoolNameTextView;
    private EditText usernameEditText;
    private EditText idEditText;
    private EditText passwordEditText;
    private EditText fullNameEditText;
    private Button addButton;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private String schoolId;
    private String schoolName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.add_manager);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addManagerLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get intent data
        Intent intent = getIntent();
        schoolId = intent.getStringExtra("schoolId");
        schoolName = intent.getStringExtra("schoolName");

        if (schoolId == null || schoolName == null) {
            Toast.makeText(this, "שגיאה: פרטי בית הספר חסרים", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        usernameEditText = findViewById(R.id.usernameEditText);
        idEditText = findViewById(R.id.idEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        fullNameEditText = findViewById(R.id.fullNameEditText);
        addButton = findViewById(R.id.addButton);
        progressBar = findViewById(R.id.progressBar);

        // Set school name
        schoolNameTextView.setText(schoolName);

        addButton.setOnClickListener(v -> addManagerToSchool());
    }

    private void addManagerToSchool() {
        String username = usernameEditText.getText().toString().trim();
        String id = idEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String fullName = fullNameEditText.getText().toString().trim();

        // Validate inputs
        if (username.isEmpty() || id.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "יש למלא את כל השדות", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate ID is 9 digits long
        if (id.length() != 9 || !id.matches("\\d+")) {
            Toast.makeText(this, "תעודת זהות חייבת להיות באורך 9 ספרות", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate password length is between 3 and 22 characters
        if (password.length() < 3 || password.length() > 22) {
            Toast.makeText(this, "סיסמה חייבת להיות באורך 3-22 תווים", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        addButton.setEnabled(false);

        // Check if manager already exists
        db.collection("schools").document(schoolId)
            .collection("managers").document(username)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    // Manager already exists
                    Toast.makeText(addManager.this, "מנהל עם שם משתמש זה כבר קיים", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    addButton.setEnabled(true);
                } else {
                    // Create manager document
                    Map<String, Object> managerData = new HashMap<>();
                    managerData.put("id", id);
                    managerData.put("password", password);
                    managerData.put("fullName", fullName);
                    
                    // Format current date as DD:MM:YYYY
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd:MM:yyyy", Locale.getDefault());
                    String formattedDate = dateFormat.format(new Date());
                    managerData.put("createdAt", formattedDate);

                    db.collection("schools").document(schoolId)
                        .collection("managers").document(username)
                        .set(managerData)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(addManager.this, "המנהל נוסף בהצלחה", Toast.LENGTH_SHORT).show();
                            clearFields();
                            progressBar.setVisibility(View.GONE);
                            addButton.setEnabled(true);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(addManager.this, "שגיאה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            addButton.setEnabled(true);
                        });
                }
            });
    }

    private void clearFields() {
        usernameEditText.setText("");
        idEditText.setText("");
        passwordEditText.setText("");
        fullNameEditText.setText("");
        usernameEditText.requestFocus();
    }

    public void goBack(View view) {
        onBackPressed();
    }
} 