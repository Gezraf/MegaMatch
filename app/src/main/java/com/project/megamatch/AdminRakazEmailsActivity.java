package com.project.megamatch;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminRakazEmailsActivity extends AppCompatActivity {

    private static final String TAG = "AdminRakazEmails";
    private FirebaseFirestore fireDB;
    private String schoolId;
    
    private TextInputLayout emailInputLayout, firstNameInputLayout, lastNameInputLayout;
    private EditText emailInput, firstNameInput, lastNameInput;
    private Button addEmailButton;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView emptyView;
    
    private RakazEmailsAdapter adapter;
    private List<RakazEmailModel> emailsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_rakaz_emails);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Get school ID from intent or shared preferences
        schoolId = getIntent().getStringExtra("schoolId");
        if (schoolId == null) {
            // If not in intent, get from shared preferences
            schoolId = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE)
                    .getString("loggedInSchoolId", null);
            
            if (schoolId == null) {
                Toast.makeText(this, "שגיאה: לא נמצא סימול בית ספר", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        
        // Initialize Firestore
        fireDB = FirebaseFirestore.getInstance();
        
        // Initialize UI components
        emailInputLayout = findViewById(R.id.emailInputLayout);
        firstNameInputLayout = findViewById(R.id.firstNameInputLayout);
        lastNameInputLayout = findViewById(R.id.lastNameInputLayout);
        emailInput = findViewById(R.id.emailInput);
        firstNameInput = findViewById(R.id.firstNameInput);
        lastNameInput = findViewById(R.id.lastNameInput);
        addEmailButton = findViewById(R.id.addEmailButton);
        progressBar = findViewById(R.id.progressBar);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        
        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RakazEmailsAdapter(emailsList, this::onEmailItemClick);
        recyclerView.setAdapter(adapter);
        
        // Setup Add button
        addEmailButton.setOnClickListener(v -> addNewRakazEmail());
        
        // Load existing emails
        loadAllowedRakazEmails();
    }
    
    private void loadAllowedRakazEmails() {
        progressBar.setVisibility(View.VISIBLE);
        emailsList.clear();
        
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                        return;
                    }
                    
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String email = document.getId();
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        Boolean approved = document.getBoolean("approved");
                        Boolean registered = document.getBoolean("registered");
                        
                        RakazEmailModel model = new RakazEmailModel(
                                email, 
                                firstName != null ? firstName : "", 
                                lastName != null ? lastName : "",
                                approved != null && approved,
                                registered != null && registered
                        );
                        
                        emailsList.add(model);
                    }
                    
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminRakazEmailsActivity.this, 
                            "שגיאה בטעינת רשימת האימיילים: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error loading emails", e);
                });
    }
    
    private void addNewRakazEmail() {
        String email = emailInput.getText().toString().trim();
        String firstName = firstNameInput.getText().toString().trim();
        String lastName = lastNameInput.getText().toString().trim();
        
        // Validate fields
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName)) {
            Toast.makeText(this, "נא למלא את כל השדות", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "נא להזין כתובת אימייל תקינה", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        
        // Check if email already exists
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(AdminRakazEmailsActivity.this, 
                                "אימייל זה כבר קיים ברשימה", 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // Add new email to Firestore
                        Map<String, Object> emailData = new HashMap<>();
                        emailData.put("firstName", firstName);
                        emailData.put("lastName", lastName);
                        emailData.put("approved", true);
                        emailData.put("registered", false);
                        
                        fireDB.collection("schools").document(schoolId)
                                .collection("allowedRakazEmails").document(email)
                                .set(emailData)
                                .addOnSuccessListener(aVoid -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(AdminRakazEmailsActivity.this, 
                                            "אימייל נוסף בהצלחה", 
                                            Toast.LENGTH_SHORT).show();
                                    
                                    // Clear inputs
                                    emailInput.setText("");
                                    firstNameInput.setText("");
                                    lastNameInput.setText("");
                                    
                                    // Refresh list
                                    loadAllowedRakazEmails();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(AdminRakazEmailsActivity.this, 
                                            "שגיאה בהוספת אימייל: " + e.getMessage(), 
                                            Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Error adding email", e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminRakazEmailsActivity.this, 
                            "שגיאה בבדיקת אימייל: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error checking email", e);
                });
    }
    
    private void onEmailItemClick(RakazEmailModel email, int position) {
        // Show dialog with options
        String[] options = {"מחק", "שנה סטטוס אישור"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(email.getEmail())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Delete
                            confirmDeleteEmail(email);
                            break;
                        case 1: // Toggle approval
                            toggleEmailApproval(email, position);
                            break;
                    }
                });
        builder.create().show();
    }
    
    private void confirmDeleteEmail(RakazEmailModel email) {
        if (email.isRegistered()) {
            Toast.makeText(this, "לא ניתן למחוק אימייל שכבר נרשם למערכת", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("מחיקת אימייל")
                .setMessage("האם אתה בטוח שברצונך למחוק את האימייל " + email.getEmail() + "?")
                .setPositiveButton("כן", (dialog, which) -> deleteEmail(email))
                .setNegativeButton("לא", null)
                .show();
    }
    
    private void deleteEmail(RakazEmailModel email) {
        progressBar.setVisibility(View.VISIBLE);
        
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email.getEmail())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminRakazEmailsActivity.this, 
                            "אימייל נמחק בהצלחה", 
                            Toast.LENGTH_SHORT).show();
                    
                    // Refresh list
                    loadAllowedRakazEmails();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminRakazEmailsActivity.this, 
                            "שגיאה במחיקת אימייל: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error deleting email", e);
                });
    }
    
    private void toggleEmailApproval(RakazEmailModel email, int position) {
        if (email.isRegistered()) {
            Toast.makeText(this, "לא ניתן לשנות סטטוס אישור לאימייל שכבר נרשם", Toast.LENGTH_SHORT).show();
            return;
        }
        
        boolean newApprovalStatus = !email.isApproved();
        progressBar.setVisibility(View.VISIBLE);
        
        fireDB.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email.getEmail())
                .update("approved", newApprovalStatus)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminRakazEmailsActivity.this, 
                            "סטטוס אישור עודכן בהצלחה", 
                            Toast.LENGTH_SHORT).show();
                    
                    // Update local list
                    email.setApproved(newApprovalStatus);
                    adapter.notifyItemChanged(position);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminRakazEmailsActivity.this, 
                            "שגיאה בעדכון סטטוס אישור: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error updating approval status", e);
                });
    }
    
    // Model class for rakaz email
    public static class RakazEmailModel {
        private String email;
        private String firstName;
        private String lastName;
        private boolean approved;
        private boolean registered;
        
        public RakazEmailModel(String email, String firstName, String lastName, boolean approved, boolean registered) {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.approved = approved;
            this.registered = registered;
        }
        
        public String getEmail() {
            return email;
        }
        
        public String getFirstName() {
            return firstName;
        }
        
        public String getLastName() {
            return lastName;
        }
        
        public boolean isApproved() {
            return approved;
        }
        
        public void setApproved(boolean approved) {
            this.approved = approved;
        }
        
        public boolean isRegistered() {
            return registered;
        }
    }
} 