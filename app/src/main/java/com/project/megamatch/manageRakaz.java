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
    
    // Firestore listeners for real-time updates
    private ListenerRegistration allowedEmailsListener;
    private ListenerRegistration rakazimListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rakaz_manage);
        
        // Get intent data
        schoolId = getIntent().getStringExtra("schoolId");
        if (schoolId == null || schoolId.isEmpty()) {
            Toast.makeText(this, "שגיאה: לא התקבל מזהה בית ספר", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize UI elements
        titleTextView = findViewById(R.id.titleTextView);
        schoolNameTextView = findViewById(R.id.schoolNameTextView);
        recyclerViewRakazim = findViewById(R.id.recyclerViewRakazim);
        emptyStateTextView = findViewById(R.id.emptyStateTextView);
        progressBar = findViewById(R.id.progressBar);
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Initialize recycler view
        rakazList = new ArrayList<>();
        rakazAdapter = new RakazAdapter(rakazList);
        recyclerViewRakazim.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewRakazim.setAdapter(rakazAdapter);
        
        // Load school name
        loadSchoolName();
        
        // Clear any previous state
        removeListeners();
        rakazList.clear();
        rakazAdapter.notifyDataSetChanged();
        
        // Load rakazim with real-time listener
        Log.d(TAG, "Starting initial data load with real-time listeners");
        loadRakazim();
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
    
    private void loadRakazim() {
        showLoading(true);
        
        // Clear existing list
        rakazList.clear();
        rakazAdapter.notifyDataSetChanged();
        
        // Remove any existing listeners
        removeListeners();
        
        // Set up listener for allowedRakazEmails collection
        allowedEmailsListener = db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails")
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error listening for allowed emails", e);
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
                    
                    // Set up listener for registered rakazim
                    setupRakazimListener(allowedEmails);
                }
            });
    }
    
    private void setupRakazimListener(List<String> allowedEmails) {
        // Clear existing rakaz list to rebuild it
        rakazList.clear();
        
        Log.d(TAG, "Setting up real-time listener for rakazim");
        
        // Set up listener for registered rakazim - using EXPLICIT listener settings for better real-time updates
        rakazimListener = db.collection("schools").document(schoolId)
            .collection("rakazim")
            .addSnapshotListener((rakazimSnapshot, rakazimError) -> {
                // Log when changes are detected
                Log.d(TAG, "Rakaz collection changed - updating UI");
                if (rakazimError != null) {
                    Log.e(TAG, "Error listening for rakazim", rakazimError);
                    showError("שגיאה בטעינת רכזים");
                    showLoading(false);
                    return;
                }
                
                if (rakazimSnapshot != null) {
                    // Clear list to rebuild it with both registered and unregistered rakazim
                    rakazList.clear();
                    
                    // Track if all rakaz documents have been processed
                    final int[] rakazDocCount = {0};
                    final int totalDocCount = rakazimSnapshot.size();
                    
                    // If no registered rakazim, go straight to unregistered
                    if (totalDocCount == 0) {
                        addUnregisteredRakazimWithListener(allowedEmails);
                        return;
                    }
                    
                    // Process each registered rakaz
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
                            fullName = document.getString("fullName"); // For backward compatibility
                        }
                        
                        String email = document.getString("email");
                        boolean isRegistered = true;
                        
                        // Create final copy of fullName for use in lambda
                        final String finalFullName = fullName;
                        final String finalUsername = username;
                        
                        // Fetch detailed info from allowedRakazEmails
                        fetchRakazDetailsFromAllowedEmails(email, username, (detailedFullName, registeredUsername, assignedMegama) -> {
                            // Use the name from allowedRakazEmails if available
                            String displayName = detailedFullName != null ? detailedFullName : 
                                (finalFullName != null ? finalFullName : "שם לא ידוע");
                            
                            // Check if this rakaz has created a megama
                            checkMegamaDetailsAndExistence(finalUsername, (hasMegama, megamaName) -> {
                                // Create rakaz item with complete info
                                RakazItem rakazItem = new RakazItem(
                                    finalUsername,
                                    displayName,
                                    email != null ? email : "אימייל לא ידוע",
                                    hasMegama,
                                    isRegistered,
                                    megamaName,
                                    assignedMegama
                                );
                                
                                // Add to list and update UI
                                rakazList.add(rakazItem);
                                rakazAdapter.notifyDataSetChanged();
                                
                                // Remove from allowed emails to track unregistered
                                if (email != null) {
                                    allowedEmails.remove(email);
                                }
                                
                                // Track completion and handle unregistered rakazim
                                rakazDocCount[0]++;
                                if (rakazDocCount[0] >= totalDocCount) {
                                    addUnregisteredRakazimWithListener(allowedEmails);
                                }
                            });
                        });
                    }
                }
            });
    }
    
    private void addUnregisteredRakazimWithListener(List<String> allowedEmails) {
        // Process unregistered rakazim
        if (allowedEmails.isEmpty()) {
            // No unregistered rakazim to add
            runOnUiThread(() -> {
                showLoading(false);
                if (rakazList.isEmpty()) {
                    showEmptyState(true);
                } else {
                    showEmptyState(false);
                }
            });
            return;
        }
        
        // Track completion status
        final int[] processedCount = {0};
        final int totalCount = allowedEmails.size();
        
        // Process each unregistered rakaz
        for (String email : allowedEmails) {
            fetchRakazDetailsFromAllowedEmails(email, "", (detailedFullName, registeredUsername, assignedMegama) -> {
                String displayName = detailedFullName != null ? detailedFullName : "רכז לא רשום";
                
                RakazItem rakazItem = new RakazItem(
                    "",  // No username for unregistered
                    displayName,
                    email,
                    false,  // No megama for unregistered
                    false,  // Not registered
                    null,   // No megama name
                    assignedMegama  // Assigned megama
                );
                
                // Add to list and update UI
                rakazList.add(rakazItem);
                rakazAdapter.notifyDataSetChanged();
                
                // Track completion
                processedCount[0]++;
                if (processedCount[0] >= totalCount) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        if (rakazList.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                        }
                    });
                }
            });
        }
    }
    
    // Remove all listeners to prevent memory leaks
    private void removeListeners() {
        if (allowedEmailsListener != null) {
            allowedEmailsListener.remove();
            allowedEmailsListener = null;
        }
        
        if (rakazimListener != null) {
            rakazimListener.remove();
            rakazimListener = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up listeners
        removeListeners();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Reload data when activity resumes (e.g., after editing a rakaz)
        if (schoolId != null && !schoolId.isEmpty()) {
            loadRakazim();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Handle result from EditRakazActivity (registered rakaz)
        if ((requestCode == 1001 || requestCode == 1002) && resultCode == RESULT_OK) {
            // Force immediate refresh - works for both registered and unregistered rakazim
            Log.d(TAG, "Returned from edit activity (request code: " + requestCode + ") - forcing refresh");
            rakazList.clear();
            rakazAdapter.notifyDataSetChanged();
            removeListeners();
            loadRakazim();
        }
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewRakazim.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyState(boolean show) {
        emptyStateTextView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewRakazim.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    public void addRakaz(View view) {
        Intent intent = new Intent(manageRakaz.this, addRakaz.class);
        intent.putExtra("schoolId", schoolId);
        startActivity(intent);
    }
    
    public void goBack(View view) {
        onBackPressed();
    }
    
    // Data model for a rakaz item
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
    
    // Adapter for the rakaz recycler view
    private class RakazAdapter extends RecyclerView.Adapter<RakazAdapter.RakazViewHolder> {
        
        private List<RakazItem> rakazItems;
        
        public RakazAdapter(List<RakazItem> rakazItems) {
            this.rakazItems = rakazItems;
        }
        
        @NonNull
        @Override
        public RakazViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rakaz_list_item, parent, false);
            return new RakazViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull RakazViewHolder holder, int position) {
            RakazItem rakazItem = rakazItems.get(position);
            
            // Set name and information
            holder.nameTextView.setText(rakazItem.getFullName());
            
            // If registered, show username above email
            if (rakazItem.isRegistered() && !rakazItem.getUsername().isEmpty()) {
                String emailText = "שם משתמש: " + rakazItem.getUsername() + "\n" + rakazItem.getEmail();
                holder.emailTextView.setText(emailText);
            } else {
                holder.emailTextView.setText(rakazItem.getEmail());
            }
            
            // Set megama status
            if (rakazItem.hasMegama()) {
                // Show the actual megama name if available
                String megamaText = "מגמה: " + (rakazItem.getMegamaName() != null ? rakazItem.getMegamaName() : "קיימת");
                holder.megamaStatusTextView.setText(megamaText);
                holder.megamaStatusTextView.setTextColor(getResources().getColor(R.color.teal_200));
            } else if (rakazItem.getAssignedMegama() != null && !rakazItem.getAssignedMegama().isEmpty()) {
                // Assigned but not created
                holder.megamaStatusTextView.setText("מגמה: " + rakazItem.getAssignedMegama());
                holder.megamaStatusTextView.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
            } else {
                // No megama
                holder.megamaStatusTextView.setText("מגמה טרם נוצרה");
                holder.megamaStatusTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
            
            // Handle click on the card to view megama
            holder.itemView.setOnClickListener(v -> {
                if (rakazItem.getUsername().isEmpty()) {
                    // Unregistered rakaz
                    Toast.makeText(manageRakaz.this, "רכז טרם נרשם למערכת", Toast.LENGTH_SHORT).show();
                } else {
                    // Always check if megama exists, regardless of hasMegama flag
                    // First try to get megama name from the item
                    final String megamaName = rakazItem.getMegamaName() != null ? 
                                       rakazItem.getMegamaName() : 
                                       rakazItem.getAssignedMegama();
                    
                    if (megamaName != null && !megamaName.isEmpty()) {
                        // Log for debugging
                        Log.d(TAG, "Attempting to open megama: " + megamaName + " for rakaz: " + rakazItem.getUsername());
                        
                        // First check if the megama exists with megama name as document ID
                        db.collection("schools").document(schoolId)
                            .collection("megamot").document(megamaName)
                            .get()
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                    // Megama exists with name as document ID
                                    Log.d(TAG, "Found megama document with megamaName: " + megamaName);
                                    Intent intent = new Intent(manageRakaz.this, MegamaPreview.class);
                                    intent.putExtra("schoolId", schoolId);
                                    intent.putExtra("rakazUsername", rakazItem.getUsername());
                                    intent.putExtra("isManager", true);
                                    intent.putExtra("megamaName", megamaName);
                                    intent.putExtra("megamaDocId", megamaName); // Use name as document ID
                                    startActivity(intent);
                                } else {
                                    Log.d(TAG, "Megama not found with name: " + megamaName + ", trying with username: " + rakazItem.getUsername());
                                    // Try the old way with rakaz username as document ID
                                    db.collection("schools").document(schoolId)
                                        .collection("megamot").document(rakazItem.getUsername())
                                        .get()
                                        .addOnCompleteListener(usernameTask -> {
                                            if (usernameTask.isSuccessful() && usernameTask.getResult() != null && 
                                                usernameTask.getResult().exists()) {
                                                // Megama exists with username as document ID (old way)
                                                Log.d(TAG, "Found megama document with username: " + rakazItem.getUsername());
                                                Intent intent = new Intent(manageRakaz.this, MegamaPreview.class);
                                                intent.putExtra("schoolId", schoolId);
                                                intent.putExtra("rakazUsername", rakazItem.getUsername());
                                                intent.putExtra("isManager", true);
                                                intent.putExtra("megamaName", megamaName);
                                                intent.putExtra("megamaDocId", rakazItem.getUsername()); // Old style
                                                startActivity(intent);
                                            } else {
                                                // Megama doesn't exist at all
                                                Log.e(TAG, "Megama not found with either name or username as document ID");
                                                Toast.makeText(manageRakaz.this, "רכז טרם יצר את מגמת " + megamaName, Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                }
                            });
                    } else {
                        // No megama name available
                        Toast.makeText(manageRakaz.this, "רכז טרם יצר מגמה", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            
            // Handle edit button click
            holder.editButton.setOnClickListener(v -> {
                // Open edit activity for both registered and unregistered rakazim
                Intent intent = new Intent(manageRakaz.this, EditRakazActivity.class);
                intent.putExtra("schoolId", schoolId);
                intent.putExtra("username", rakazItem.getUsername());
                intent.putExtra("email", rakazItem.getEmail());
                
                // Parse first and last name from full name
                String firstName = rakazItem.getFullName().split(" ").length > 0 ? 
                                  rakazItem.getFullName().split(" ")[0] : "";
                String lastName = rakazItem.getFullName().split(" ").length > 1 ? 
                                 rakazItem.getFullName().split(" ")[1] : "";
                
                intent.putExtra("firstName", firstName);
                intent.putExtra("lastName", lastName);
                
                // Different data based on registration status
                if (rakazItem.isRegistered()) {
                    intent.putExtra("megama", rakazItem.getMegamaName());
                    intent.putExtra("isRegistered", true);
                    startActivityForResult(intent, 1001); // Use request code for registered edit
                } else {
                    intent.putExtra("megama", rakazItem.getAssignedMegama());
                    intent.putExtra("isRegistered", false);
                    startActivityForResult(intent, 1002); // Use request code for unregistered edit
                }
            });
            
            // Handle delete button click
            holder.deleteButton.setOnClickListener(v -> {
                if (rakazItem.getUsername().isEmpty()) {
                    // Delete email from allowedRakazEmails
                    confirmDeleteUnregisteredRakaz(rakazItem);
                } else {
                    // Delete registered rakaz
                    confirmDeleteRakaz(rakazItem);
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return rakazItems.size();
        }
        
        class RakazViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView emailTextView;
            TextView megamaStatusTextView;
            ImageButton editButton;
            ImageButton deleteButton;
            
            public RakazViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.rakazNameTextView);
                emailTextView = itemView.findViewById(R.id.rakazEmailTextView);
                megamaStatusTextView = itemView.findViewById(R.id.megamaStatusTextView);
                editButton = itemView.findViewById(R.id.editButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
            }
        }
    }
    
    private void confirmDeleteUnregisteredRakaz(RakazItem rakazItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("מחיקת רכז");
        builder.setMessage("האם למחוק את הרכז " + rakazItem.getEmail() + " מרשימת המורשים?");
        
        // Create cancel button
        builder.setNegativeButton("בטל", (dialog, which) -> dialog.dismiss());
        
        // Create delete button with cooldown
        final int[] countdown = {3}; // 3 second countdown
        
        builder.setPositiveButton("מחק", null); // We'll override this below
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            deleteButton.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            deleteButton.setEnabled(false);
            
            // Set initial transparency
            deleteButton.setAlpha(0.3f);
            
            // Start countdown
            new CountDownTimer(3000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    countdown[0] = (int) (millisUntilFinished / 1000) + 1;
                    deleteButton.setText("מחק (" + countdown[0] + ")");
                }
                
                @Override
                public void onFinish() {
                    deleteButton.setEnabled(true);
                    deleteButton.setAlpha(1.0f);
                    deleteButton.setText("מחק");
                    
                    // Set click listener for actual delete
                    deleteButton.setOnClickListener(v -> {
                        dialog.dismiss();
                        deleteAllowedRakazEmail(rakazItem.getEmail());
                    });
                }
            }.start();
        });
        
        dialog.show();
    }
    
    private void confirmDeleteRakaz(RakazItem rakazItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("מחיקת רכז");
        builder.setMessage("האם למחוק את הרכז " + rakazItem.getFullName() + "?\n\nפעולה זו תמחק גם את המגמה אם קיימת!");
        
        // Create cancel button
        builder.setNegativeButton("בטל", (dialog, which) -> dialog.dismiss());
        
        // Create delete button with cooldown
        final int[] countdown = {3}; // 3 second countdown
        
        builder.setPositiveButton("מחק", null); // We'll override this below
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            deleteButton.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            deleteButton.setEnabled(false);
            
            // Set initial transparency
            deleteButton.setAlpha(0.3f);
            
            // Start countdown
            new CountDownTimer(3000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    countdown[0] = (int) (millisUntilFinished / 1000) + 1;
                    deleteButton.setText("מחק (" + countdown[0] + ")");
                }
                
                @Override
                public void onFinish() {
                    deleteButton.setEnabled(true);
                    deleteButton.setAlpha(1.0f);
                    deleteButton.setText("מחק");
                    
                    // Set click listener for actual delete
                    deleteButton.setOnClickListener(v -> {
                        dialog.dismiss();
                        deleteRegisteredRakaz(rakazItem);
                    });
                }
            }.start();
        });
        
        dialog.show();
    }
    
    private void deleteAllowedRakazEmail(String email) {
        // Show loading indicator
        showLoading(true);
        
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails").document(email)
            .delete()
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(manageRakaz.this, "הרכז הוסר בהצלחה", Toast.LENGTH_SHORT).show();
                // No need to reload - listeners will handle updates automatically
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting allowed email", e);
                Toast.makeText(manageRakaz.this, "שגיאה במחיקת הרכז: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                showLoading(false);
            });
    }
    
    private void deleteRegisteredRakaz(RakazItem rakazItem) {
        // Show loading during deletion
        showLoading(true);
        
        // Delete from rakazim collection
        db.collection("schools").document(schoolId)
            .collection("rakazim").document(rakazItem.getUsername())
            .delete()
            .addOnSuccessListener(aVoid -> {
                // If email exists, also remove from allowedRakazEmails
                if (rakazItem.getEmail() != null && !rakazItem.getEmail().isEmpty()) {
                    db.collection("schools").document(schoolId)
                        .collection("allowedRakazEmails").document(rakazItem.getEmail())
                        .delete()
                        .addOnSuccessListener(aVoid2 -> {
                            // Check if megama exists first by its name
                            if (rakazItem.getMegamaName() != null && !rakazItem.getMegamaName().isEmpty()) {
                                // Try to delete megama by megama name first
                                db.collection("schools").document(schoolId)
                                    .collection("megamot").document(rakazItem.getMegamaName())
                                    .delete()
                                    .addOnSuccessListener(aVoid3 -> {
                                        Toast.makeText(manageRakaz.this, "הרכז והמגמה הוסרו בהצלחה", Toast.LENGTH_SHORT).show();
                                        // Data will refresh automatically via listeners
                                    })
                                    .addOnFailureListener(e -> {
                                        // If failed, try by username
                                        deleteMegamaByUsername(rakazItem);
                                    });
                            } else if (rakazItem.hasMegama()) {
                                // Otherwise try by username
                                deleteMegamaByUsername(rakazItem);
                            } else {
                                Toast.makeText(manageRakaz.this, "הרכז הוסר בהצלחה", Toast.LENGTH_SHORT).show();
                                // Data will refresh automatically via listeners
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting allowed email", e);
                            Toast.makeText(manageRakaz.this, "הרכז הוסר אך נכשל בהסרת האימייל המורשה", Toast.LENGTH_SHORT).show();
                        });
                } else {
                    // If no email to delete, check if megama needs deletion
                    if (rakazItem.hasMegama()) {
                        deleteMegamaByUsername(rakazItem);
                    } else {
                        Toast.makeText(manageRakaz.this, "הרכז הוסר בהצלחה", Toast.LENGTH_SHORT).show();
                        // Data will refresh automatically via listeners
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting rakaz", e);
                Toast.makeText(manageRakaz.this, "שגיאה במחיקת הרכז: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                showLoading(false);
            });
    }
    
    private void deleteMegamaByUsername(RakazItem rakazItem) {
        // Delete megama using username as document ID (old method)
        db.collection("schools").document(schoolId)
            .collection("megamot").document(rakazItem.getUsername())
            .delete()
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(manageRakaz.this, "הרכז והמגמה הוסרו בהצלחה", Toast.LENGTH_SHORT).show();
                // Data will refresh automatically via listeners
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting megama", e);
                Toast.makeText(manageRakaz.this, "הרכז הוסר אך נכשל בהסרת המגמה", Toast.LENGTH_SHORT).show();
            });
    }
    
    // Helper method to fetch rakaz details from allowedRakazEmails
    private void fetchRakazDetailsFromAllowedEmails(String email, String username, RakazDetailsCallback callback) {
        if (email == null || email.isEmpty()) {
            callback.onResult(null, null, null);
            return;
        }
        
        // Try to find the rakaz in the allowedRakazEmails collection
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails").document(email)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    DocumentSnapshot document = task.getResult();
                    String firstName = document.getString("firstName");
                    String lastName = document.getString("lastName");
                    String megama = document.getString("megama");
                    String fullName = "";
                    
                    if (firstName != null && lastName != null) {
                        fullName = firstName + " " + lastName;
                    } else if (firstName != null) {
                        fullName = firstName;
                    } else if (lastName != null) {
                        fullName = lastName;
                    }
                    
                    callback.onResult(fullName.isEmpty() ? null : fullName, username, megama);
                } else {
                    // If document not found with exact ID, try a query
                    db.collection("schools").document(schoolId)
                        .collection("allowedRakazEmails")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnCompleteListener(queryTask -> {
                            if (queryTask.isSuccessful() && !queryTask.getResult().isEmpty()) {
                                DocumentSnapshot doc = queryTask.getResult().getDocuments().get(0);
                                String firstName = doc.getString("firstName");
                                String lastName = doc.getString("lastName");
                                String megama = doc.getString("megama");
                                String fullName = "";
                                
                                if (firstName != null && lastName != null) {
                                    fullName = firstName + " " + lastName;
                                } else if (firstName != null) {
                                    fullName = firstName;
                                } else if (lastName != null) {
                                    fullName = lastName;
                                }
                                
                                callback.onResult(fullName.isEmpty() ? null : fullName, username, megama);
                            } else {
                                callback.onResult(null, null, null);
                            }
                        });
                }
            });
    }
    
    private interface RakazDetailsCallback {
        void onResult(String fullName, String username, String megama);
    }
    
    // Method to check if a megama exists and get its name
    private void checkMegamaDetailsAndExistence(String rakazUsername, MegamaDetailsCallback callback) {
        Log.d(TAG, "Checking megama existence for rakaz: " + rakazUsername);
        
        // Get the megama name from the rakaz's document in allowedRakazEmails first
        String email = null;
        for (RakazItem item : rakazList) {
            if (item.getUsername().equals(rakazUsername)) {
                email = item.getEmail();
                break;
            }
        }

        if (email != null && !email.isEmpty()) {
            // Check assigned megama in allowedRakazEmails first
            db.collection("schools").document(schoolId)
                .collection("allowedRakazEmails").document(email)
                .get()
                .addOnCompleteListener(emailTask -> {
                    if (emailTask.isSuccessful() && emailTask.getResult() != null && emailTask.getResult().exists()) {
                        String assignedMegama = emailTask.getResult().getString("megama");
                        
                        if (assignedMegama != null && !assignedMegama.isEmpty()) {
                            Log.d(TAG, "Found assigned megama: " + assignedMegama);
                            
                            // Now check if this megama exists in the megamot collection
                            db.collection("schools").document(schoolId)
                                .collection("megamot").document(assignedMegama)
                                .get()
                                .addOnCompleteListener(megamaTask -> {
                                    if (megamaTask.isSuccessful() && megamaTask.getResult() != null && megamaTask.getResult().exists()) {
                                        // Megama exists with the name as document ID
                                        String megamaName = megamaTask.getResult().getString("name");
                                        Log.d(TAG, "Found megama document with name: " + megamaName);
                                        callback.onResult(true, megamaName != null ? megamaName : assignedMegama);
                                    } else {
                                        // Check the old way - using rakaz username as document ID
                                        checkMegamaByRakazUsername(rakazUsername, callback);
                                    }
                                });
                        } else {
                            // No assigned megama, check the old way
                            checkMegamaByRakazUsername(rakazUsername, callback);
                        }
                    } else {
                        // Email not found or error, check the old way
                        checkMegamaByRakazUsername(rakazUsername, callback);
                    }
                });
        } else {
            // No email available, check the old way
            checkMegamaByRakazUsername(rakazUsername, callback);
        }
    }
    
    // Helper method to check megama by rakaz username (old way)
    private void checkMegamaByRakazUsername(String rakazUsername, MegamaDetailsCallback callback) {
        // Check if megama exists for this rakaz and get its name (legacy method)
        db.collection("schools").document(schoolId)
            .collection("megamot")
            .document(rakazUsername)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    String megamaName = task.getResult().getString("name");
                    Log.d(TAG, "Found megama with rakaz username as document ID: " + megamaName);
                    callback.onResult(true, megamaName);
                } else {
                    Log.d(TAG, "No megama found for rakaz: " + rakazUsername);
                    callback.onResult(false, null);
                }
            });
    }
    
    private interface MegamaDetailsCallback {
        void onResult(boolean hasMegama, String megamaName);
    }
} 