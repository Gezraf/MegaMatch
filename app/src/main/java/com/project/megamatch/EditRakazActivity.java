package com.project.megamatch;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditRakazActivity extends AppCompatActivity {

    private static final String TAG = "EditRakazActivity";
    
    // Firebase
    private FirebaseFirestore db;
    
    // UI components
    private EditText usernameEditText;
    private EditText emailEditText;
    private EditText firstNameEditText;
    private EditText lastNameEditText;
    private EditText megamaEditText;
    private Button saveButton;
    private Button cancelButton;
    
    // Data
    private String schoolId;
    private String originalUsername;
    private String originalEmail;
    private String originalMegama;
    private boolean isRegistered;
    
    // UI elements
    private ProgressBar progressBar;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_rakaz);
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        
        // Initialize UI components
        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        firstNameEditText = findViewById(R.id.firstNameEditText);
        lastNameEditText = findViewById(R.id.lastNameEditText);
        megamaEditText = findViewById(R.id.megamaEditText);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);
        
        // Get data from intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            schoolId = extras.getString("schoolId", "");
            originalUsername = extras.getString("username", "");
            originalEmail = extras.getString("email", "");
            String firstName = extras.getString("firstName", "");
            String lastName = extras.getString("lastName", "");
            originalMegama = extras.getString("megama", "");
            isRegistered = extras.getBoolean("isRegistered", true);
            
            // Set initial values
            usernameEditText.setText(originalUsername);
            emailEditText.setText(originalEmail);
            firstNameEditText.setText(firstName);
            lastNameEditText.setText(lastName);
            megamaEditText.setText(originalMegama);
            
            // Disable username and megama fields for unregistered rakazim
            if (!isRegistered) {
                usernameEditText.setEnabled(false);
                megamaEditText.setEnabled(false);
                usernameEditText.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
                megamaEditText.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
            }
        } else {
            Toast.makeText(this, "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Set button listeners
        saveButton.setOnClickListener(v -> validateAndSave());
        cancelButton.setOnClickListener(v -> finish());
    }
    
    private void validateAndSave() {
        // Get values from fields
        String newUsername = usernameEditText.getText().toString().trim();
        String newEmail = emailEditText.getText().toString().trim();
        String newFirstName = firstNameEditText.getText().toString().trim();
        String newLastName = lastNameEditText.getText().toString().trim();
        String newMegama = megamaEditText.getText().toString().trim();
        
        // Validate values
        if (isRegistered && newUsername.isEmpty()) {
            showError("שם משתמש לא יכול להיות ריק");
            return;
        }
        
        if (newEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            showError("כתובת דוא\"ל לא תקינה");
            return;
        }
        
        if (newFirstName.isEmpty()) {
            showError("שם פרטי לא יכול להיות ריק");
            return;
        }
        
        // Different validation paths based on registration status
        if (isRegistered) {
            // For registered rakazim
            if (!newUsername.equals(originalUsername)) {
                confirmUsernameChange(newUsername, newEmail, newFirstName, newLastName, newMegama);
            } 
            // Confirm if megama name has changed
            else if (!newMegama.equals(originalMegama) && !originalMegama.isEmpty()) {
                confirmMegamaChange(newUsername, newEmail, newFirstName, newLastName, newMegama);
            } 
            // Otherwise proceed with save
            else {
                saveChanges(newUsername, newEmail, newFirstName, newLastName, newMegama);
            }
        } else {
            // For unregistered rakazim - only confirm email change
            if (!newEmail.equals(originalEmail)) {
                confirmEmailChange(newEmail, newFirstName, newLastName);
            } else {
                saveUnregisteredChanges(newEmail, newFirstName, newLastName);
            }
        }
    }
    
    private void confirmEmailChange(String newEmail, String newFirstName, String newLastName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("שינוי כתובת דוא\"ל");
        builder.setMessage("שינוי כתובת הדוא\"ל ישפיע על הזדהות הרכז בעתיד. האם להמשיך?");
        builder.setPositiveButton("המשך", (dialog, which) -> {
            saveUnregisteredChanges(newEmail, newFirstName, newLastName);
        });
        builder.setNegativeButton("בטל", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }
    
    private void confirmUsernameChange(String newUsername, String newEmail, String newFirstName, String newLastName, String newMegama) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("שינוי שם משתמש");
        builder.setMessage("שינוי שם משתמש עלול להשפיע על מגמת הרכז. האם להמשיך?");
        builder.setPositiveButton("המשך", (dialog, which) -> {
            saveChanges(newUsername, newEmail, newFirstName, newLastName, newMegama);
        });
        builder.setNegativeButton("בטל", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }
    
    private void confirmMegamaChange(String newUsername, String newEmail, String newFirstName, String newLastName, String newMegama) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("שינוי שם מגמה");
        builder.setMessage("שינוי שם המגמה ישפיע על מסמכי המגמה הקיימים. האם להמשיך?");
        builder.setPositiveButton("המשך", (dialog, which) -> {
            saveChanges(newUsername, newEmail, newFirstName, newLastName, newMegama);
        });
        builder.setNegativeButton("בטל", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }
    
    private void saveUnregisteredChanges(String newEmail, String newFirstName, String newLastName) {
        // Show progress indicator and disable buttons
        progressBar.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        
        // Create or update document in allowedRakazEmails collection
        Map<String, Object> rakazData = new HashMap<>();
        rakazData.put("firstName", newFirstName);
        rakazData.put("lastName", newLastName);
        rakazData.put("megama", originalMegama); // Keep original megama for unregistered rakazim
        
        if (!newEmail.equals(originalEmail)) {
            // Create new document with new email
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(newEmail)
                .set(rakazData)
                .addOnSuccessListener(aVoid -> {
                    // Delete the old document
                    db.collection("schools").document(schoolId)
                        .collection("allowedRakazEmails").document(originalEmail)
                        .delete()
                        .addOnSuccessListener(aVoid2 -> {
                            completeUpdate();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting old email document", e);
                            hideProgress();
                            showError("שגיאה במחיקת מסמך דוא\"ל ישן: " + e.getMessage());
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating new email document", e);
                    hideProgress();
                    showError("שגיאה ביצירת מסמך דוא\"ל חדש: " + e.getMessage());
                });
        } else {
            // Update existing document
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(originalEmail)
                .update(rakazData)
                .addOnSuccessListener(aVoid -> {
                    completeUpdate();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating email document", e);
                    hideProgress();
                    showError("שגיאה בעדכון מסמך דוא\"ל: " + e.getMessage());
                });
        }
    }
    
    private void saveChanges(String newUsername, String newEmail, String newFirstName, String newLastName, String newMegama) {
        // Show progress indicator and disable buttons
        progressBar.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        
        // Update rakaz document in rakazim collection
        Map<String, Object> rakazUpdates = new HashMap<>();
        rakazUpdates.put("firstName", newFirstName);
        rakazUpdates.put("lastName", newLastName);
        rakazUpdates.put("email", newEmail);
        if (!newMegama.isEmpty()) {
            rakazUpdates.put("megama", newMegama);
        }
        
        // First update the rakaz document
        db.collection("schools").document(schoolId)
            .collection("rakazim").document(originalUsername)
            .update(rakazUpdates)
            .addOnSuccessListener(aVoid -> {
                // Handle username change if needed
                if (!newUsername.equals(originalUsername)) {
                    handleUsernameChange(newUsername, progressBar);
                } 
                // Handle megama change if needed
                else if (!newMegama.equals(originalMegama) && !originalMegama.isEmpty()) {
                    handleMegamaChange(newUsername, newMegama, progressBar);
                } else {
                    // Update email in allowedRakazEmails if email changed
                    if (!newEmail.equals(originalEmail)) {
                        updateAllowedEmail(newEmail, newFirstName, newLastName, newMegama, progressBar);
                    } else {
                        completeUpdate();
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating rakaz", e);
                hideProgress();
                showError("שגיאה בעדכון פרטי רכז: " + e.getMessage());
            });
    }
    
    private void handleUsernameChange(String newUsername, ProgressBar progressBar) {
        // 1. Get current rakaz document data
        db.collection("schools").document(schoolId)
            .collection("rakazim").document(originalUsername)
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    // 2. Create new document with new username
                    db.collection("schools").document(schoolId)
                        .collection("rakazim").document(newUsername)
                        .set(document.getData())
                        .addOnSuccessListener(aVoid -> {
                            // 3. Delete old document
                            db.collection("schools").document(schoolId)
                                .collection("rakazim").document(originalUsername)
                                .delete()
                                .addOnSuccessListener(aVoid2 -> {
                                    // 4. Handle megama document if exists
                                    updateMegamaForUsernameChange(newUsername, progressBar);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error deleting old rakaz document", e);
                                    hideProgress();
                                    showError("שגיאה במחיקת מסמך רכז ישן: " + e.getMessage());
                                });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error creating new rakaz document", e);
                            hideProgress();
                            showError("שגיאה ביצירת מסמך רכז חדש: " + e.getMessage());
                        });
                } else {
                    hideProgress();
                    showError("מסמך רכז לא נמצא");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting rakaz document", e);
                hideProgress();
                showError("שגיאה בקריאת מסמך רכז: " + e.getMessage());
            });
    }
    
    private void updateMegamaForUsernameChange(String newUsername, ProgressBar progressBar) {
        // Check if the megama document exists with old username
        db.collection("schools").document(schoolId)
            .collection("megamot").document(originalUsername)
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    // If exists, copy to new document ID
                    db.collection("schools").document(schoolId)
                        .collection("megamot").document(newUsername)
                        .set(document.getData())
                        .addOnSuccessListener(aVoid -> {
                            // Delete old megama document
                            db.collection("schools").document(schoolId)
                                .collection("megamot").document(originalUsername)
                                .delete()
                                .addOnSuccessListener(aVoid2 -> {
                                    // Update email in allowedRakazEmails
                                    updateAllowedEmail(emailEditText.getText().toString().trim(), 
                                                     firstNameEditText.getText().toString().trim(), 
                                                     lastNameEditText.getText().toString().trim(),
                                                     megamaEditText.getText().toString().trim(),
                                                     progressBar);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error deleting old megama document", e);
                                    hideProgress();
                                    showError("שגיאה במחיקת מסמך מגמה ישן: " + e.getMessage());
                                });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error creating new megama document", e);
                            hideProgress();
                            showError("שגיאה ביצירת מסמך מגמה חדש: " + e.getMessage());
                        });
                } else {
                    // If megama doesn't exist with old username, check if it exists with megama name
                    updateAllowedEmail(emailEditText.getText().toString().trim(), 
                                     firstNameEditText.getText().toString().trim(), 
                                     lastNameEditText.getText().toString().trim(),
                                     megamaEditText.getText().toString().trim(),
                                     progressBar);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking megama document", e);
                hideProgress();
                showError("שגיאה בבדיקת מסמך מגמה: " + e.getMessage());
            });
    }
    
    private void handleMegamaChange(String username, String newMegama, ProgressBar progressBar) {
        // First check if megama exists with old name
        if (originalMegama.isEmpty()) {
            // No original megama, just update allowed email
            updateAllowedEmail(emailEditText.getText().toString().trim(), 
                             firstNameEditText.getText().toString().trim(), 
                             lastNameEditText.getText().toString().trim(),
                             newMegama,
                             progressBar);
            return;
        }
        
        // Check if the megama document exists with old name
        db.collection("schools").document(schoolId)
            .collection("megamot").document(originalMegama)
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    // If exists, copy to new document ID with new megama name
                    Map<String, Object> megamaData = document.getData();
                    if (megamaData != null) {
                        megamaData.put("name", newMegama);
                        
                        db.collection("schools").document(schoolId)
                            .collection("megamot").document(newMegama)
                            .set(megamaData)
                            .addOnSuccessListener(aVoid -> {
                                // Delete old megama document
                                db.collection("schools").document(schoolId)
                                    .collection("megamot").document(originalMegama)
                                    .delete()
                                    .addOnSuccessListener(aVoid2 -> {
                                        // Update email in allowedRakazEmails
                                        updateAllowedEmail(emailEditText.getText().toString().trim(), 
                                                         firstNameEditText.getText().toString().trim(), 
                                                         lastNameEditText.getText().toString().trim(),
                                                         newMegama,
                                                         progressBar);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error deleting old megama document", e);
                                        hideProgress();
                                        showError("שגיאה במחיקת מסמך מגמה ישן: " + e.getMessage());
                                    });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error creating new megama document", e);
                                hideProgress();
                                showError("שגיאה ביצירת מסמך מגמה חדש: " + e.getMessage());
                            });
                    } else {
                        // No data in the document
                        updateAllowedEmail(emailEditText.getText().toString().trim(), 
                                         firstNameEditText.getText().toString().trim(), 
                                         lastNameEditText.getText().toString().trim(),
                                         newMegama,
                                         progressBar);
                    }
                } else {
                    // Check if megama exists with username as document ID
                    db.collection("schools").document(schoolId)
                        .collection("megamot").document(username)
                        .get()
                        .addOnSuccessListener(usernameDocument -> {
                            if (usernameDocument.exists()) {
                                // Update megama name in the document
                                db.collection("schools").document(schoolId)
                                    .collection("megamot").document(username)
                                    .update("name", newMegama)
                                    .addOnSuccessListener(aVoid -> {
                                        updateAllowedEmail(emailEditText.getText().toString().trim(), 
                                                         firstNameEditText.getText().toString().trim(), 
                                                         lastNameEditText.getText().toString().trim(),
                                                         newMegama,
                                                         progressBar);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error updating megama name", e);
                                        hideProgress();
                                        showError("שגיאה בעדכון שם מגמה: " + e.getMessage());
                                    });
                            } else {
                                // No megama document found
                                updateAllowedEmail(emailEditText.getText().toString().trim(), 
                                                 firstNameEditText.getText().toString().trim(), 
                                                 lastNameEditText.getText().toString().trim(),
                                                 newMegama,
                                                 progressBar);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error checking username megama document", e);
                            hideProgress();
                            showError("שגיאה בבדיקת מסמך מגמה: " + e.getMessage());
                        });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking megama document", e);
                hideProgress();
                showError("שגיאה בבדיקת מסמך מגמה: " + e.getMessage());
            });
    }
    
    private void updateAllowedEmail(String newEmail, String firstName, String lastName, String megama, ProgressBar progressBar) {
        // Skip if original email is empty
        if (originalEmail == null || originalEmail.isEmpty()) {
            completeUpdate();
            return;
        }
        
        // Check if email has changed
        if (!newEmail.equals(originalEmail)) {
            // Create a new document with new email
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("firstName", firstName);
            emailData.put("lastName", lastName);
            emailData.put("megama", megama);
            
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(newEmail)
                .set(emailData)
                .addOnSuccessListener(aVoid -> {
                    // Delete old email document
                    db.collection("schools").document(schoolId)
                        .collection("allowedRakazEmails").document(originalEmail)
                        .delete()
                        .addOnSuccessListener(aVoid2 -> {
                            completeUpdate();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting old email document", e);
                            hideProgress();
                            showError("שגיאה במחיקת מסמך דוא\"ל ישן: " + e.getMessage());
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating new email document", e);
                    hideProgress();
                    showError("שגיאה ביצירת מסמך דוא\"ל חדש: " + e.getMessage());
                });
        } else {
            // Just update the existing email document
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("firstName", firstName);
            emailData.put("lastName", lastName);
            emailData.put("megama", megama);
            
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(originalEmail)
                .update(emailData)
                .addOnSuccessListener(aVoid -> {
                    completeUpdate();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating email document", e);
                    hideProgress();
                    showError("שגיאה בעדכון מסמך דוא\"ל: " + e.getMessage());
                });
        }
    }
    
    private void completeUpdate() {
        hideProgress();
        Toast.makeText(this, "פרטי הרכז עודכנו בהצלחה", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
    
    // New method to hide progress
    private void hideProgress() {
        // Hide progress and re-enable buttons
        progressBar.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        cancelButton.setEnabled(true);
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    public void goBack(View view) {
        finish();
    }
} 