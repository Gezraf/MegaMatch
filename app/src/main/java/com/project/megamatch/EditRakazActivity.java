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

/**
 * מחלקה זו מאפשרת עריכת פרטי רכז, כולל שם משתמש, אימייל, שם פרטי, שם משפחה ומגמה.
 * היא מטפלת בשני סוגי רכזים: רשומים ולא רשומים, עם לוגיקת אימות ושמירה שונה לכל אחד.
 */
public class EditRakazActivity extends AppCompatActivity {

    private static final String TAG = "EditRakazActivity";
    
    // פיירבייס
    private FirebaseFirestore db;
    
    // רכיבי ממשק משתמש
    private EditText usernameEditText;
    private EditText emailEditText;
    private EditText firstNameEditText;
    private EditText lastNameEditText;
    private EditText megamaEditText;
    private Button saveButton;
    private Button cancelButton;
    
    // נתונים
    private String schoolId;
    private String originalUsername;
    private String originalEmail;
    private String originalMegama;
    private boolean isRegistered;
    
    // אלמנטים של ממשק המשתמש
    private ProgressBar progressBar;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_rakaz);
        
        // אתחול פיירבייס
        db = FirebaseFirestore.getInstance();
        
        // אתחול רכיבי ממשק משתמש
        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        firstNameEditText = findViewById(R.id.firstNameEditText);
        lastNameEditText = findViewById(R.id.lastNameEditText);
        megamaEditText = findViewById(R.id.megamaEditText);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);
        
        // קבלת נתונים מהאינטנט
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            schoolId = extras.getString("schoolId", "");
            originalUsername = extras.getString("username", "");
            originalEmail = extras.getString("email", "");
            String firstName = extras.getString("firstName", "");
            String lastName = extras.getString("lastName", "");
            originalMegama = extras.getString("megama", "");
            isRegistered = extras.getBoolean("isRegistered", true);
            
            // הגדרת ערכים ראשוניים
            usernameEditText.setText(originalUsername);
            emailEditText.setText(originalEmail);
            firstNameEditText.setText(firstName);
            lastNameEditText.setText(lastName);
            megamaEditText.setText(originalMegama);
            
            // השבתת שדות שם משתמש ומגמה לרכזים לא רשומים
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
        
        // הגדרת מאזינים לכפתורים
        saveButton.setOnClickListener(v -> validateAndSave());
        cancelButton.setOnClickListener(v -> finish());
    }
    
    /**
     * מאמת את שדות הקלט ושומר את השינויים.
     * מטפל בלוגיקת אימות ושמירה שונה בהתאם לסטטוס הרישום של הרכז.
     */
    private void validateAndSave() {
        // קבלת ערכים מהשדות
        String newUsername = usernameEditText.getText().toString().trim();
        String newEmail = emailEditText.getText().toString().trim();
        String newFirstName = firstNameEditText.getText().toString().trim();
        String newLastName = lastNameEditText.getText().toString().trim();
        String newMegama = megamaEditText.getText().toString().trim();
        
        // אימות ערכים
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
        
        // מסלולי אימות שונים בהתבסס על סטטוס הרישום
        if (isRegistered) {
            // לרכזים רשומים
            if (!newUsername.equals(originalUsername)) {
                confirmUsernameChange(newUsername, newEmail, newFirstName, newLastName, newMegama);
            } 
            // אישור אם שם המגמה השתנה
            else if (!newMegama.equals(originalMegama) && !originalMegama.isEmpty()) {
                confirmMegamaChange(newUsername, newEmail, newFirstName, newLastName, newMegama);
            } 
            // אחרת המשך בשמירה
            else {
                saveChanges(newUsername, newEmail, newFirstName, newLastName, newMegama);
            }
        } else {
            // לרכזים לא רשומים - רק אישור שינוי אימייל
            if (!newEmail.equals(originalEmail)) {
                confirmEmailChange(newEmail, newFirstName, newLastName);
            } else {
                saveUnregisteredChanges(newEmail, newFirstName, newLastName);
            }
        }
    }
    
    /**
     * מציג דיאלוג אישור לשינוי כתובת אימייל של רכז לא רשום.
     * @param newEmail כתובת האימייל החדשה.
     * @param newFirstName השם הפרטי החדש.
     * @param newLastName שם המשפחה החדש.
     */
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
    
    /**
     * מציג דיאלוג אישור לשינוי שם משתמש של רכז רשום.
     * @param newUsername שם המשתמש החדש.
     * @param newEmail כתובת האימייל החדשה.
     * @param newFirstName השם הפרטי החדש.
     * @param newLastName שם המשפחה החדש.
     * @param newMegama שם המגמה החדש.
     */
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
    
    /**
     * מציג דיאלוג אישור לשינוי שם מגמה של רכז רשום.
     * @param newUsername שם המשתמש החדש.
     * @param newEmail כתובת האימייל החדשה.
     * @param newFirstName השם הפרטי החדש.
     * @param newLastName שם המשפחה החדש.
     * @param newMegama שם המגמה החדש.
     */
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
    
    /**
     * שומר שינויים עבור רכזים לא רשומים.
     * מטפל בעדכון או יצירת מסמך חדש באוסף allowedRakazEmails.
     * @param newEmail כתובת האימייל החדשה.
     * @param newFirstName השם הפרטי החדש.
     * @param newLastName שם המשפחה החדש.
     */
    private void saveUnregisteredChanges(String newEmail, String newFirstName, String newLastName) {
        // הצגת מחוון התקדמות והשבתת כפתורים
        progressBar.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        
        // יצירה או עדכון מסמך באוסף allowedRakazEmails
        Map<String, Object> rakazData = new HashMap<>();
        rakazData.put("firstName", newFirstName);
        rakazData.put("lastName", newLastName);
        rakazData.put("megama", originalMegama); // שמירה על שם המגמה המקורי לרכזים לא רשומים
        
        if (!newEmail.equals(originalEmail)) {
            // יצירת מסמך חדש עם אימייל חדש
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(newEmail)
                .set(rakazData)
                .addOnSuccessListener(aVoid -> {
                    // מחיקת המסמך הישן
                    db.collection("schools").document(schoolId)
                        .collection("allowedRakazEmails").document(originalEmail)
                        .delete()
                        .addOnSuccessListener(aVoid2 -> {
                            completeUpdate();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "שגיאה במחיקת מסמך אימייל ישן", e);
                            hideProgress();
                            showError("שגיאה במחיקת מסמך דוא\"ל ישן: " + e.getMessage());
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה ביצירת מסמך אימייל חדש", e);
                    hideProgress();
                    showError("שגיאה ביצירת מסמך דוא\"ל חדש: " + e.getMessage());
                });
        } else {
            // עדכון מסמך קיים
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(originalEmail)
                .update(rakazData)
                .addOnSuccessListener(aVoid -> {
                    completeUpdate();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בעדכון מסמך אימייל", e);
                    hideProgress();
                    showError("שגיאה בעדכון מסמך דוא\"ל: " + e.getMessage());
                });
        }
    }
    
    /**
     * שומר שינויים עבור רכזים רשומים.
     * מטפל בעדכון מסמך הרכז באוסף 'rakazim' ובמגמה באוסף 'megamot'.
     * @param newUsername שם המשתמש החדש.
     * @param newEmail כתובת האימייל החדשה.
     * @param newFirstName השם הפרטי החדש.
     * @param newLastName שם המשפחה החדש.
     * @param newMegama שם המגמה החדש.
     */
    private void saveChanges(String newUsername, String newEmail, String newFirstName, String newLastName, String newMegama) {
        // הצגת מחוון התקדמות והשבתת כפתורים
        progressBar.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        
        // עדכון מסמך הרכז באוסף rakazim
        Map<String, Object> rakazUpdates = new HashMap<>();
        rakazUpdates.put("firstName", newFirstName);
        rakazUpdates.put("lastName", newLastName);
        rakazUpdates.put("email", newEmail);

        // התייחסות לשינוי שם משתמש
        if (!newUsername.equals(originalUsername)) {
            handleUsernameChange(newUsername, progressBar);
        } else if (!newMegama.equals(originalMegama) && !originalMegama.isEmpty()) {
            // טיפול בשינוי שם מגמה כאשר שם המשתמש לא השתנה
            handleMegamaChange(newUsername, newMegama, progressBar);
        } else {
            // רק עדכון פרטי הרכז ללא שינוי שם משתמש/מגמה
            db.collection("schools").document(schoolId)
                    .collection("rakazim").document(originalUsername)
                    .update(rakazUpdates)
                    .addOnSuccessListener(aVoid -> {
                        updateAllowedEmail(newEmail, newFirstName, newLastName, newMegama, progressBar);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "שגיאה בעדכון פרטי רכז", e);
                        hideProgress();
                        showError("שגיאה בעדכון פרטי רכז: " + e.getMessage());
                    });
        }
    }

    /**
     * מטפל בשינוי שם משתמש של רכז.
     * יוצר מסמך רכז חדש עם השם החדש, מעביר את המגמות ומוחק את המסמך הישן.
     * @param newUsername שם המשתמש החדש.
     * @param progressBar מחוון התקדמות.
     */
    private void handleUsernameChange(String newUsername, ProgressBar progressBar) {
        DocumentReference oldRakazRef = db.collection("schools").document(schoolId)
                .collection("rakazim").document(originalUsername);

        oldRakazRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Map<String, Object> oldRakazData = documentSnapshot.getData();
                if (oldRakazData == null) {
                    Log.e(TAG, "נתוני הרכז הישן הם null");
                    hideProgress();
                    showError("שגיאה: נתוני הרכז הישן חסרים.");
                    return;
                }

                // יצירת מסמך רכז חדש עם שם המשתמש החדש
                db.collection("schools").document(schoolId)
                        .collection("rakazim").document(newUsername)
                        .set(oldRakazData)
                        .addOnSuccessListener(aVoid -> {
                            // מחיקת המסמך הישן
                            oldRakazRef.delete()
                                    .addOnSuccessListener(aVoid1 -> {
                                        Log.d(TAG, "מסמך רכז ישן נמחק בהצלחה");
                                        // עדכון מגמות עם שם המשתמש החדש
                                        updateMegamaForUsernameChange(newUsername, progressBar);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "שגיאה במחיקת מסמך רכז ישן", e);
                                        hideProgress();
                                        showError("שגיאה במחיקת מסמך רכז ישן: " + e.getMessage());
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "שגיאה ביצירת מסמך רכז חדש", e);
                            hideProgress();
                            showError("שגיאה ביצירת מסמך רכז חדש: " + e.getMessage());
                        });
            } else {
                Log.e(TAG, "מסמך רכז מקורי לא נמצא בשינוי שם משתמש");
                hideProgress();
                showError("שגיאה: מסמך רכז מקורי לא נמצא.");
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "שגיאה בקבלת מסמך רכז מקורי עבור שינוי שם משתמש", e);
            hideProgress();
            showError("שגיאה בקבלת מסמך רכז מקורי: " + e.getMessage());
        });
    }

    /**
     * מעדכן את שם המשתמש של המגמה באוסף 'megamot' בעקבות שינוי שם משתמש של הרכז.
     * @param newUsername שם המשתמש החדש של הרכז.
     * @param progressBar מחוון התקדמות.
     */
    private void updateMegamaForUsernameChange(String newUsername, ProgressBar progressBar) {
        db.collection("schools").document(schoolId)
                .collection("megamot")
                .whereEqualTo("rakazUsername", originalUsername)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        DocumentReference megamaRef = document.getReference();
                        megamaRef.update("rakazUsername", newUsername)
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "שגיאה בעדכון שם משתמש רכז במגמה", e);
                                    // ממשיך למרות השגיאה כדי לא לחסום
                                });
                    }
                    updateAllowedEmail(emailEditText.getText().toString().trim(),
                            firstNameEditText.getText().toString().trim(),
                            lastNameEditText.getText().toString().trim(),
                            megamaEditText.getText().toString().trim(), progressBar);

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בקבלת מגמות לעדכון שם משתמש רכז", e);
                    hideProgress();
                    showError("שגיאה בעדכון מגמות: " + e.getMessage());
                });
    }

    /**
     * מטפל בשינוי שם המגמה של רכז רשום.
     * מעדכן את שם המגמה במסמך הרכז ובאוסף 'megamot'.
     * @param username שם המשתמש של הרכז.
     * @param newMegama שם המגמה החדש.
     * @param progressBar מחוון התקדמות.
     */
    private void handleMegamaChange(String username, String newMegama, ProgressBar progressBar) {
        // עדכון שם המגמה במסמך הרכז
        db.collection("schools").document(schoolId)
                .collection("rakazim").document(username)
                .update("megama", newMegama)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "שם מגמה עודכן במסמך הרכז");
                    // עדכון מסמכי המגמה עצמם
                    updateMegamaDocument(username, newMegama, progressBar);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בעדכון שם מגמה במסמך הרכז", e);
                    hideProgress();
                    showError("שגיאה בעדכון שם מגמה: " + e.getMessage());
                });
    }

    /**
     * מעדכן את מסמכי המגמה באוסף 'megamot' בעקבות שינוי שם המגמה של הרכז.
     * @param username שם המשתמש של הרכז.
     * @param newMegama שם המגמה החדש.
     * @param progressBar מחוון התקדמות.
     */
    private void updateMegamaDocument(String username, String newMegama, ProgressBar progressBar) {
        db.collection("schools").document(schoolId)
                .collection("megamot")
                .whereEqualTo("rakazUsername", username)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        DocumentReference megamaRef = document.getReference();
                        megamaRef.update("name", newMegama)
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "שגיאה בעדכון שם מגמה במסמך המגמה", e);
                                });
                    }
                    updateAllowedEmail(emailEditText.getText().toString().trim(),
                            firstNameEditText.getText().toString().trim(),
                            lastNameEditText.getText().toString().trim(),
                            newMegama, progressBar);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בקבלת מסמכי מגמה לעדכון", e);
                    hideProgress();
                    showError("שגיאה בקבלת מסמכי מגמה: " + e.getMessage());
                });
    }

    /**
     * מעדכן את רשימת האימיילים המאושרים של הרכז בפיירבייס.
     * מטפל במחיקה/הוספה של אימייל במקרה של שינוי.
     * @param newEmail כתובת האימייל החדשה.
     * @param firstName השם הפרטי של הרכז.
     * @param lastName שם המשפחה של הרכז.
     * @param megama שם המגמה של הרכז.
     * @param progressBar מחוון התקדמות.
     */
    private void updateAllowedEmail(String newEmail, String firstName, String lastName, String megama, ProgressBar progressBar) {
        // Get current data from allowedRakazEmails for comparison
        db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(originalEmail)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean originalApproved = documentSnapshot.exists() && Boolean.TRUE.equals(documentSnapshot.getBoolean("approved"));
                    boolean originalRegistered = documentSnapshot.exists() && Boolean.TRUE.equals(documentSnapshot.getBoolean("registered"));

                    if (!newEmail.equals(originalEmail)) {
                        // Create new allowed email document
                        Map<String, Object> newAllowedEmailData = new HashMap<>();
                        newAllowedEmailData.put("firstName", firstName);
                        newAllowedEmailData.put("lastName", lastName);
                        newAllowedEmailData.put("approved", originalApproved);
                        newAllowedEmailData.put("registered", originalRegistered);
                        newAllowedEmailData.put("megama", megama);

                        db.collection("schools").document(schoolId)
                                .collection("allowedRakazEmails").document(newEmail)
                                .set(newAllowedEmailData)
                                .addOnSuccessListener(aVoid -> {
                                    // Delete old allowed email document
                                    db.collection("schools").document(schoolId)
                                            .collection("allowedRakazEmails").document(originalEmail)
                                            .delete()
                                            .addOnSuccessListener(aVoid2 -> {
                                                completeUpdate();
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "שגיאה במחיקת אימייל רכז ישן מאושר", e);
                                                hideProgress();
                                                showError("שגיאה במחיקת אימייל רכז ישן מאושר: " + e.getMessage());
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "שגיאה ביצירת אימייל רכז חדש מאושר", e);
                                    hideProgress();
                                    showError("שגיאה ביצירת אימייל רכז חדש מאושר: " + e.getMessage());
                                });
                    } else {
                        // Update existing allowed email document
                        Map<String, Object> updatedAllowedEmailData = new HashMap<>();
                        updatedAllowedEmailData.put("firstName", firstName);
                        updatedAllowedEmailData.put("lastName", lastName);
                        updatedAllowedEmailData.put("megama", megama);

                        db.collection("schools").document(schoolId)
                                .collection("allowedRakazEmails").document(newEmail)
                                .update(updatedAllowedEmailData)
                                .addOnSuccessListener(aVoid -> {
                                    completeUpdate();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "שגיאה בעדכון אימייל רכז מאושר", e);
                                    hideProgress();
                                    showError("שגיאה בעדכון אימייל רכז מאושר: " + e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בקבלת אימייל רכז מקורי מאושר", e);
                    hideProgress();
                    showError("שגיאה בקבלת אימייל רכז מקורי מאושר: " + e.getMessage());
                });
    }
    
    /**
     * מסיים את תהליך העדכון ומציג הודעת הצלחה.
     */
    private void completeUpdate() {
        hideProgress();
        Toast.makeText(EditRakazActivity.this, "השינויים נשמרו בהצלחה", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
    
    /**
     * מסתיר את מחוון ההתקדמות ומפעיל מחדש את הכפתורים.
     */
    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        cancelButton.setEnabled(true);
    }
    
    /**
     * מציג הודעת שגיאה למשתמש.
     * @param message הודעת השגיאה להצגה.
     */
    private void showError(String message) {
        Toast.makeText(EditRakazActivity.this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * חוזר למסך הקודם.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void goBack(View view) {
        onBackPressed();
    }
} 