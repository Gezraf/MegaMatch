package com.project.megamatch;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * מחלקה זו מאפשרת למנהל המערכת לנהל רכזים בבית ספר ספציפי.
 * היא מציגה רשימה של רכזים רשומים ולא רשומים, ומאפשרת עריכה ומחיקה של פרטיהם.
 * השינויים מתעדכנים בזמן אמת באמצעות מאזיני פיירסטור.
 */
public class manageRakaz extends AppCompatActivity {

    private static final String TAG = "ManageRakaz";
    
    private TextView titleTextView;
    private TextView schoolNameTextView;
    private RecyclerView recyclerViewRakazim;
    private TextView emptyStateTextView;
    private View progressBar;
    
    private FirebaseFirestore db;
    private String schoolId;
    private RakazAdapter rakazAdapter;
    private List<RakazItem> rakazList;
    
    // מאזיני פיירסטור לעדכונים בזמן אמת
    private ListenerRegistration allowedEmailsListener;
    private ListenerRegistration rakazimListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rakaz_manage);
        
        // קבלת נתוני האינטנט
        schoolId = getIntent().getStringExtra("schoolId");
        if (schoolId == null || schoolId.isEmpty()) {
            Toast.makeText(this, "שגיאה: לא התקבל מזהה בית ספר", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // אתחול רכיבי הממשק
        titleTextView = findViewById(R.id.titleTextView);
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        recyclerViewRakazim = findViewById(R.id.recyclerViewRakazim);
        emptyStateTextView = findViewById(R.id.emptyStateTextView);
        progressBar = findViewById(R.id.progressBar);
        
        // אתחול Firestore
        db = FirebaseFirestore.getInstance();
        
        // אתחול RecyclerView
        rakazList = new ArrayList<>();
        rakazAdapter = new RakazAdapter(rakazList);
        recyclerViewRakazim.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewRakazim.setAdapter(rakazAdapter);
        
        // טעינת שם בית הספר
        loadSchoolName();
        
        // ניקוי כל מצב קודם
        removeListeners();
        rakazList.clear();
        rakazAdapter.notifyDataSetChanged();
        
        // טעינת רכזים עם מאזין בזמן אמת
        Log.d(TAG, "מתחיל טעינת נתונים ראשונית עם מאזינים בזמן אמת");
        loadRakazim();
    }
    
    /**
     * טוען את שם בית הספר מה-CSV או מפיירסטור.
     */
    private void loadSchoolName() {
        // נסה למצוא בית ספר במסד הנתונים CSV תחילה
        for (schoolsDB.School school : schoolsDB.getAllSchools()) {
            if (String.valueOf(school.getSchoolId()).equals(schoolId)) {
                schoolNameTextView.setText(school.getSchoolName());
                return;
            }
        }
        
        // אם לא נמצא ב-CSV, נסה לקבל אותו מפיירסטור
        db.collection("schools").document(schoolId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // בדוק אם לבית הספר יש שדה "name"
                        String name = document.getString("name");
                        
                        if (name != null && !name.isEmpty()) {
                            schoolNameTextView.setText(name);
                        } else {
                            // השתמש במחזיק מקום עם המזהה
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
    
    /**
     * טוען את רשימת הרכזים ומגדיר מאזיני זמן אמת.
     */
    private void loadRakazim() {
        showLoading(true);
        
        // ניקוי רשימה קיימת
        rakazList.clear();
        rakazAdapter.notifyDataSetChanged();
        
        // הסרת כל מאזינים קיימים
        removeListeners();
        
        // הגדרת מאזין לאוסף allowedRakazEmails
        allowedEmailsListener = db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails")
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.e(TAG, "שגיאה בהאזנה לאימיילים מורשים", e);
                    showError("שגיאה בטעינת דוא\"ל מורשים");
                    showLoading(false);
                    return;
                }
                
                if (snapshot != null) {
                    List<String> allowedEmails = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snapshot) {
                        String email = document.getId();
                        if (!email.equals("_init") && !email.equals("_dummy")) {
                        allowedEmails.add(email);
                        }
                    }
                    
                    // הגדרת מאזין לרכזים רשומים
                    setupRakazimListener(allowedEmails);
                }
            });
    }
    
    /**
     * מגדיר מאזין זמן אמת לאוסף הרכזים ומעדכן את הרשימה.
     * @param allowedEmails רשימת האימיילים המורשים.
     */
    private void setupRakazimListener(List<String> allowedEmails) {
        // ניקוי רשימת הרכזים הקיימת לבנייה מחדש
        rakazList.clear();
        
        Log.d(TAG, "מגדיר מאזין בזמן אמת לרכזים");
        
        // הגדרת מאזין לרכזים רשומים - שימוש בהגדרות מאזין מפורשות לעדכונים טובים יותר בזמן אמת
        rakazimListener = db.collection("schools").document(schoolId)
            .collection("rakazim")
            .addSnapshotListener((rakazimSnapshot, rakazimError) -> {
                // תיעוד כאשר זוהו שינויים
                Log.d(TAG, "אוסף הרכזים השתנה - מעדכן ממשק משתמש");
                if (rakazimError != null) {
                    Log.e(TAG, "שגיאה בהאזנה לרכזים", rakazimError);
                    showError("שגיאה בטעינת רכזים");
                    showLoading(false);
                    return;
                }
                
                if (rakazimSnapshot != null) {
                    // ניקוי הרשימה כדי לבנות אותה מחדש עם רכזים רשומים ולא רשומים
                    rakazList.clear();
                    
                    // עקוב אם כל מסמכי הרכז עובדו
                    final int[] rakazDocCount = {0};
                    final int totalDocCount = rakazimSnapshot.size();
                    
                    // אם אין רכזים רשומים, עבור ישירות לרכזים לא רשומים
                    if (totalDocCount == 0) {
                        addUnregisteredRakazimWithListener(allowedEmails);
                        return;
                    }
                    
                    // עיבוד כל רכז רשום
                    for (QueryDocumentSnapshot document : rakazimSnapshot) {
                        String username = document.getId();
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String fullName = null;
                        
                        if (firstName != null) {
                            fullName = firstName;
                            if (lastName != null) {
                                fullName = firstName + " " + lastName;
                            }
                        } else {
                            fullName = document.getString("fullName"); // לתאימות לאחור
                        }
                        
                        String email = document.getString("email");
                        boolean isRegistered = true;
                        
                        // יצירת עותק סופי של fullName לשימוש בלמבדה
                        final String finalFullName = fullName;
                        final String finalUsername = username;
                        
                        // שליפת פרטים מפורטים מ-allowedRakazEmails
                        fetchRakazDetailsFromAllowedEmails(email, username, (detailedFullName, registeredUsername, assignedMegama) -> {
                            // השתמש בשם מ-allowedRakazEmails אם זמין
                            String displayName = detailedFullName != null ? detailedFullName : 
                                (finalFullName != null ? finalFullName : "שם לא ידוע");
                        
                        // בדוק אם רכז זה יצר מגמה
                            checkMegamaDetailsAndExistence(finalUsername, (hasMegama, megamaName) -> {
                                // יצירת פריט רכז עם מידע מלא
                            RakazItem rakazItem = new RakazItem(
                                    finalUsername,
                                    displayName,
                                email != null ? email : "אימייל לא ידוע",
                                    hasMegama,
                                    isRegistered,
                                    megamaName,
                                    assignedMegama
                            );
                            
                            // הוספה לרשימה ועדכון ממשק משתמש
                            rakazList.add(rakazItem);
                            rakazAdapter.notifyDataSetChanged();
                            
                                // הסרה מאימיילים מורשים למעקב אחר לא רשומים
                            if (email != null) {
                                allowedEmails.remove(email);
                            }
                            
                            // Continue processing remaining allowed emails if all registered rakazim are processed
                            rakazDocCount[0]++;
                            if (rakazDocCount[0] == totalDocCount) {
                                addUnregisteredRakazimWithListener(allowedEmails);
                            }
                        });
                        });
                    }
                }
            });
    }
    
    /**
     * מוסיף רכזים לא רשומים לרשימה ומגדיר מאזין בזמן אמת עבורם.
     * @param allowedEmails רשימת האימיילים המורשים שנותרו (שלא רשומים).
     */
    private void addUnregisteredRakazimWithListener(List<String> allowedEmails) {
        Log.d(TAG, "Adding unregistered rakazim to the list.");
        // Add unregistered allowed emails to the list
        for (String email : allowedEmails) {
            // Fetch details for unregistered rakaz from allowedRakazEmails
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");
                        String fullName = null;
                        if (firstName != null) {
                            fullName = firstName;
                            if (lastName != null) {
                                fullName = firstName + " " + lastName;
                            }
                        }
                        String megama = documentSnapshot.getString("megama");

                        RakazItem rakazItem = new RakazItem(
                                "", // No username for unregistered
                                fullName != null ? fullName : "שם לא ידוע",
                                email,
                                false, // Unregistered rakazim don't have a megama (yet)
                                false,
                                null, // No megama name for unregistered
                                megama // Assigned megama from allowed emails
                        );
                        rakazList.add(rakazItem);
                        rakazAdapter.notifyDataSetChanged();
                        checkEmptyState();
                    }
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching unregistered rakaz details for email: " + email, e);
                    showLoading(false);
                });
        }

        if (allowedEmails.isEmpty() && rakazList.isEmpty()) {
            showEmptyState(true);
        } else if (!allowedEmails.isEmpty() || !rakazList.isEmpty()) {
            showEmptyState(false);
        }

        showLoading(false);
    }

    /**
     * מסיר את כל המאזינים הפעילים מפיירסטור.
     */
    private void removeListeners() {
        if (allowedEmailsListener != null) {
            allowedEmailsListener.remove();
            allowedEmailsListener = null;
            Log.d(TAG, "AllowedEmails listener removed.");
        }
        if (rakazimListener != null) {
            rakazimListener.remove();
            rakazimListener = null;
            Log.d(TAG, "Rakazim listener removed.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeListeners(); // Remove listeners to prevent memory leaks
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-load rakazim list on resume in case of changes from other activities
        Log.d(TAG, "onResume: Reloading rakazim list.");
        loadRakazim();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // If an item was edited and saved successfully, refresh the list
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Log.d(TAG, "onActivityResult: Item edited, reloading data.");
            loadRakazim();
        }
    }

    /**
     * מציג או מסתיר את סרגל ההתקדמות.
     * @param show true כדי להציג, false כדי להסתיר.
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewRakazim.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyStateTextView.setVisibility(View.GONE); // Hide empty state while loading
    }

    /**
     * מציג או מסתיר את מצב הריק (כאשר אין רכזים).
     * @param show true כדי להציג, false כדי להסתיר.
     */
    private void showEmptyState(boolean show) {
        emptyStateTextView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewRakazim.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * מציג הודעת שגיאה ב-Toast.
     * @param message הודעת השגיאה להצגה.
     */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * מעביר למסך הוספת רכז.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void addRakaz(View view) {
        Intent intent = new Intent(this, addRakaz.class);
        intent.putExtra("schoolId", schoolId);
        startActivity(intent);
    }

    /**
     * חוזר למסך הקודם.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void goBack(View view) {
        onBackPressed();
    }

    /**
     * מודל נתונים עבור פריט רכז המוצג ברשימה.
     */
    public static class RakazItem {
        private String username;
        private String fullName;
        private String email;
        private boolean hasMegama;
        private boolean isRegistered;
        private String megamaName;
        private String assignedMegama;

        public RakazItem(String username, String fullName, String email, boolean hasMegama,
                         boolean isRegistered, String megamaName, String assignedMegama) {
            this.username = username;
            this.fullName = fullName;
            this.email = email;
            this.hasMegama = hasMegama;
            this.isRegistered = isRegistered;
            this.megamaName = megamaName;
            this.assignedMegama = assignedMegama;
        }

        public String getUsername() {
            return username;
        }

        public String getFullName() {
            return fullName;
        }

        public String getEmail() {
            return email;
        }

        public boolean hasMegama() {
            return hasMegama;
        }

        public boolean isRegistered() {
            return isRegistered;
        }

        public String getMegamaName() {
            return megamaName;
        }

        public String getAssignedMegama() {
            return assignedMegama;
        }
    }

    /**
     * מתאם (Adapter) עבור ה-RecyclerView המציג את רשימת הרכזים.
     */
    private class RakazAdapter extends RecyclerView.Adapter<RakazAdapter.RakazViewHolder> {

        private List<RakazItem> rakazItems;

        public RakazAdapter(List<RakazItem> rakazItems) {
            this.rakazItems = rakazItems;
        }

        @NonNull
        @Override
        public RakazViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.rakaz_list_item, parent, false);
            return new RakazViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RakazViewHolder holder, int position) {
            RakazItem rakazItem = rakazItems.get(position);
            holder.nameTextView.setText(rakazItem.getFullName());
            holder.emailTextView.setText(rakazItem.getEmail());

            // Display megama status
            if (rakazItem.isRegistered()) {
                if (rakazItem.hasMegama()) {
                    holder.megamaStatusTextView.setText("רכז מגמה: " + rakazItem.getMegamaName());
                    holder.megamaStatusTextView.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
                } else {
                    holder.megamaStatusTextView.setText("רכז רשום ללא מגמה");
                    holder.megamaStatusTextView.setTextColor(getResources().getColor(R.color.colorAccent));
                }
            } else {
                // Unregistered rakaz
                if (rakazItem.getAssignedMegama() != null && !rakazItem.getAssignedMegama().isEmpty()) {
                    holder.megamaStatusTextView.setText("מיועד למגמה: " + rakazItem.getAssignedMegama());
                } else {
                    holder.megamaStatusTextView.setText("רכז לא רשום");
                }
                holder.megamaStatusTextView.setTextColor(getResources().getColor(R.color.colorTextSecondary));
            }

            holder.editButton.setOnClickListener(v -> {
                Intent intent = new Intent(manageRakaz.this, EditRakazActivity.class);
                intent.putExtra("schoolId", schoolId);
                intent.putExtra("username", rakazItem.getUsername());
                intent.putExtra("email", rakazItem.getEmail());
                // Extract first name and last name from full name if available, otherwise pass empty
                String[] nameParts = rakazItem.getFullName().split(" ");
                intent.putExtra("firstName", nameParts.length > 0 ? nameParts[0] : "");
                intent.putExtra("lastName", nameParts.length > 1 ? nameParts[1] : "");

                intent.putExtra("megama", rakazItem.getMegamaName()); // Pass megama name
                intent.putExtra("isRegistered", rakazItem.isRegistered());
                startActivityForResult(intent, 1);
            });

            holder.deleteButton.setOnClickListener(v -> {
                if (!rakazItem.isRegistered()) {
                    confirmDeleteUnregisteredRakaz(rakazItem);
                } else {
                    confirmDeleteRakaz(rakazItem);
                }
            });
        }

        @Override
        public int getItemCount() {
            return rakazItems.size();
        }

        /**
         * מחזיק תצוגה (ViewHolder) עבור פריטי הרכז ב-RecyclerView.
         */
        class RakazViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView emailTextView;
            TextView megamaStatusTextView;
            ImageButton editButton;
            ImageButton deleteButton;

            public RakazViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.nameTextView);
                emailTextView = itemView.findViewById(R.id.emailTextView);
                megamaStatusTextView = itemView.findViewById(R.id.megamaStatusTextView);
                editButton = itemView.findViewById(R.id.editButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
            }
        }
    }

    /**
     * מציג דיאלוג אישור למחיקת רכז לא רשום.
     * מחיקה של רכז לא רשום כרוכה רק במחיקת האימייל שלו מרשימת האימיילים המורשים.
     * @param rakazItem פריט הרכז למחיקה.
     */
    private void confirmDeleteUnregisteredRakaz(RakazItem rakazItem) {
        new AlertDialog.Builder(this)
                .setTitle("מחק רכז לא רשום")
                .setMessage("האם אתה בטוח שברצונך למחוק את האימייל " + rakazItem.getEmail() + " מרשימת האימיילים המורשים? ")
                .setPositiveButton("מחק", new DialogInterface.OnClickListener() {
                    // Add countdown to button
                    private static final int COUNTDOWN_TIME = 3;
                    private CountDownTimer countDownTimer;
                    private Button positiveButton;

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // This will be called when the button is clicked, after countdown is finished
                        if (positiveButton != null && !positiveButton.isEnabled()) {
                            deleteAllowedRakazEmail(rakazItem.getEmail());
                            if (countDownTimer != null) {
                                countDownTimer.cancel();
                            }
                        } else {
                            // Initial click, start countdown
                            positiveButton = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                            positiveButton.setEnabled(false);
                            countDownTimer = new CountDownTimer(COUNTDOWN_TIME * 1000, 1000) {
                                @Override
                                public void onTick(long millisUntilFinished) {
                                    positiveButton.setText("מחק (" + (millisUntilFinished / 1000) + ")");
                                }

                                @Override
                                public void onFinish() {
                                    positiveButton.setText("מחק");
                                    positiveButton.setEnabled(true);
                                    // Execute deletion after countdown
                                    deleteAllowedRakazEmail(rakazItem.getEmail());
                                    dialog.dismiss();
                                }
                            }.start();
                        }
                    }
                })
                .setNegativeButton("בטל", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * מציג דיאלוג אישור למחיקת רכז רשום.
     * מחיקה של רכז רשום כרוכה במחיקת המסמך שלו ובמחיקת האימייל שלו מרשימת האימיילים המורשים.
     * כמו כן, היא בודקת אם לרכז יש מגמה ומציגה אזהרה בהתאם.
     * @param rakazItem פריט הרכז למחיקה.
     */
    private void confirmDeleteRakaz(RakazItem rakazItem) {
        String message = "האם אתה בטוח שברצונך למחוק את הרכז " + rakazItem.getFullName() + " (משתמש: " + rakazItem.getUsername() + ")?";
        if (rakazItem.hasMegama()) {
            message += "\nאזהרה: רכז זה משויך למגמה (" + rakazItem.getMegamaName() + "). מחיקת הרכז תמחק גם את המגמה.\nהאם אתה בטוח?";
        }

        new AlertDialog.Builder(this)
                .setTitle("מחק רכז רשום")
                .setMessage(message)
                .setPositiveButton("מחק", new DialogInterface.OnClickListener() {
                    private static final int COUNTDOWN_TIME = 3;
                    private CountDownTimer countDownTimer;
                    private Button positiveButton;

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (positiveButton != null && !positiveButton.isEnabled()) {
                            deleteRegisteredRakaz(rakazItem);
                            if (countDownTimer != null) {
                                countDownTimer.cancel();
                            }
                        } else {
                            positiveButton = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                            positiveButton.setEnabled(false);
                            countDownTimer = new CountDownTimer(COUNTDOWN_TIME * 1000, 1000) {
                                @Override
                                public void onTick(long millisUntilFinished) {
                                    positiveButton.setText("מחק (" + (millisUntilFinished / 1000) + ")");
                                }

                                @Override
                                public void onFinish() {
                                    positiveButton.setText("מחק");
                                    positiveButton.setEnabled(true);
                                    deleteRegisteredRakaz(rakazItem);
                                    dialog.dismiss();
                                }
                            }.start();
                        }
                    }
                })
                .setNegativeButton("בטל", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * מוחק כתובת אימייל מרשימת האימיילים המורשים בפיירסטור.
     * @param email האימייל למחיקה.
     */
    private void deleteAllowedRakazEmail(String email) {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(manageRakaz.this, "האימייל נמחק בהצלחה", Toast.LENGTH_SHORT).show();
                    // No need to call loadRakazim() here, snapshot listener will handle update
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(manageRakaz.this, "שגיאה במחיקת האימייל: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "שגיאה במחיקת אימייל מרשימת המורשים", e);
                })
                .addOnCompleteListener(task -> progressBar.setVisibility(View.GONE));
    }

    /**
     * מוחק רכז רשום מפיירסטור, כולל את מסמך הרכז ואת האימייל המורשה.
     * אם לרכז יש מגמה, גם המגמה נמחקת.
     * @param rakazItem פריט הרכז למחיקה.
     */
    private void deleteRegisteredRakaz(RakazItem rakazItem) {
        progressBar.setVisibility(View.VISIBLE);
        // Delete rakaz document
        db.collection("schools").document(schoolId)
                .collection("rakazim").document(rakazItem.getUsername())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "מסמך רכז נמחק: " + rakazItem.getUsername());
                    // Delete allowed email
                    if (rakazItem.getEmail() != null && !rakazItem.getEmail().isEmpty()) {
                        deleteAllowedRakazEmail(rakazItem.getEmail());
                    }
                    // Delete megama if exists
                    if (rakazItem.hasMegama()) {
                        deleteMegamaByUsername(rakazItem);
                    }
                    Toast.makeText(manageRakaz.this, "הרכז נמחק בהצלחה", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(manageRakaz.this, "שגיאה במחיקת רכז: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "שגיאה במחיקת מסמך רכז", e);
                })
                .addOnCompleteListener(task -> progressBar.setVisibility(View.GONE));
    }

    /**
     * מוחק את מסמך המגמה המשויך לרכז מסוים מפיירסטור.
     * @param rakazItem פריט הרכז שהמגמה שלו תימחק.
     */
    private void deleteMegamaByUsername(RakazItem rakazItem) {
        if (rakazItem.getMegamaName() == null || rakazItem.getMegamaName().isEmpty()) {
            Log.d(TAG, "אין שם מגמה למחיקה עבור רכז: " + rakazItem.getUsername());
            return;
        }

        db.collection("schools").document(schoolId)
                .collection("megamot").document(rakazItem.getMegamaName())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "מסמך מגמה נמחק בהצלחה: " + rakazItem.getMegamaName());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה במחיקת מסמך מגמה: " + rakazItem.getMegamaName(), e);
                });
    }

    /**
     * שולף פרטים נוספים של רכז מרשימת האימיילים המורשים.
     * @param email כתובת האימייל של הרכז.
     * @param username שם המשתמש של הרכז.
     * @param callback קריאה חוזרת עם הפרטים הנשלפים.
     */
    private void fetchRakazDetailsFromAllowedEmails(String email, String username, RakazDetailsCallback callback) {
        if (email == null || email.isEmpty()) {
            callback.onResult(null, username, null);
            return;
        }

        db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String firstName = documentSnapshot.getString("firstName");
                    String lastName = documentSnapshot.getString("lastName");
                    String megama = documentSnapshot.getString("megama");
                    String fullName = null;

                    if (firstName != null) {
                        fullName = firstName;
                        if (lastName != null) {
                            fullName = firstName + " " + lastName;
                        }
                    }
                    callback.onResult(fullName, username, megama);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בשליפת פרטי רכז מורשים עבור אימייל: " + email, e);
                    callback.onResult(null, username, null);
                });
    }

    /**
     * ממשק לקריאה חוזרת של פרטי רכז.
     */
    private interface RakazDetailsCallback {
        void onResult(String fullName, String username, String megama);
    }

    /**
     * בודק את קיומה של מגמה עבור רכז מסוים ושולף את פרטיה.
     * @param rakazUsername שם המשתמש של הרכז.
     * @param callback קריאה חוזרת עם מידע על המגמה.
     */
    private void checkMegamaDetailsAndExistence(String rakazUsername, MegamaDetailsCallback callback) {
        // Check in megamot collection by rakazUsername field
        db.collection("schools").document(schoolId)
                .collection("megamot")
                .whereEqualTo("rakazUsername", rakazUsername)
                .limit(1) // Assuming one megama per rakaz
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Megama found by rakazUsername
                        QueryDocumentSnapshot document = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                        String megamaName = document.getString("name");
                        callback.onResult(true, megamaName);
                    } else {
                        // Megama not found by rakazUsername, check by document ID (old structure)
                        checkMegamaByRakazUsername(rakazUsername, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בבדיקת קיום מגמה לפי שם משתמש רכז: " + e.getMessage(), e);
                    callback.onResult(false, null);
                });
    }

    /**
     * בודק אם מגמה קיימת על פי שם משתמש הרכז כשם המסמך (מבנה ישן).
     * @param rakazUsername שם המשתמש של הרכז.
     * @param callback קריאה חוזרת עם מידע על המגמה.
     */
    private void checkMegamaByRakazUsername(String rakazUsername, MegamaDetailsCallback callback) {
        db.collection("schools").document(schoolId)
                .collection("megamot").document(rakazUsername)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String megamaName = documentSnapshot.getString("name");
                        callback.onResult(true, megamaName);
                    } else {
                        callback.onResult(false, null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בבדיקת מגמה לפי שם משתמש כמזהה מסמך: " + e.getMessage(), e);
                    callback.onResult(false, null);
                });
    }

    /**
     * ממשק לקריאה חוזרת של פרטי מגמה.
     */
    private interface MegamaDetailsCallback {
        void onResult(boolean hasMegama, String megamaName);
    }
} 