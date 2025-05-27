package com.project.megamatch;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.firebase.firestore.FirebaseFirestore;
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
        
        // Load rakazim
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
        
        // First check allowedRakazEmails collection
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails")
            .get()
            .addOnCompleteListener(emailsTask -> {
                if (emailsTask.isSuccessful()) {
                    List<String> allowedEmails = new ArrayList<>();
                    for (QueryDocumentSnapshot document : emailsTask.getResult()) {
                        String email = document.getId();
                        allowedEmails.add(email);
                    }
                    
                    // Now load the actual rakazim who have registered
                    loadRegisteredRakazim(allowedEmails);
                } else {
                    Log.e(TAG, "Error getting allowed emails", emailsTask.getException());
                    showError("שגיאה בטעינת דוא\"ל מורשים");
                    showLoading(false);
                }
            });
    }
    
    private void loadRegisteredRakazim(List<String> allowedEmails) {
        // Load registered rakazim from the rakazim collection
        db.collection("schools").document(schoolId)
            .collection("rakazim")
            .get()
            .addOnCompleteListener(rakazimTask -> {
                if (rakazimTask.isSuccessful()) {
                    rakazList.clear();
                    
                    for (QueryDocumentSnapshot document : rakazimTask.getResult()) {
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
                        
                        // Fetch detailed info from allowedRakazEmails
                        final String finalUsername = username;
                        fetchRakazDetailsFromAllowedEmails(email, username, (detailedFullName, registeredUsername, assignedMegama) -> {
                            // Use the name from allowedRakazEmails if available, otherwise use what we have
                            String displayName = detailedFullName != null ? detailedFullName : 
                                                (finalFullName != null ? finalFullName : "שם לא ידוע");
                            
                            // Check if this rakaz has created a megama
                            checkMegamaDetailsAndExistence(finalUsername, (hasMegama, megamaName) -> {
                                // Create rakaz item with complete information
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
                                
                                // Remove from allowed emails list to track unregistered rakazim
                                if (email != null) {
                                    allowedEmails.remove(email);
                                }
                                
                                // If this was the last rakaz, add unregistered ones
                                if (document.equals(rakazimTask.getResult().getDocuments().get(rakazimTask.getResult().size() - 1))) {
                                    addUnregisteredRakazim(allowedEmails);
                                }
                            });
                        });
                    }
                    
                    // If there are no registered rakazim, just add the unregistered ones
                    if (rakazimTask.getResult().isEmpty()) {
                        addUnregisteredRakazim(allowedEmails);
                    }
                } else {
                    Log.e(TAG, "Error getting rakazim", rakazimTask.getException());
                    showError("שגיאה בטעינת רכזים");
                    showLoading(false);
                }
            });
    }
    
    private void addUnregisteredRakazim(List<String> allowedEmails) {
        // Add unregistered but allowed rakazim to the list
        for (String email : allowedEmails) {
            // Skip dummy/initialization entries
            if (email.equals("_init") || email.equals("_dummy")) {
                continue;
            }
            
            // Fetch detailed info for unregistered rakaz
            fetchRakazDetailsFromAllowedEmails(email, "", (detailedFullName, registeredUsername, assignedMegama) -> {
                String displayName = detailedFullName != null ? detailedFullName : "רכז לא רשום";
                
                RakazItem rakazItem = new RakazItem(
                    "",  // No username for unregistered rakazim
                    displayName,
                    email,
                    false,  // No megama for unregistered rakazim
                    false,  // Not registered
                    null,  // No megama name
                    assignedMegama  // Assigned megama from allowedRakazEmails
                );
                
                rakazAdapter.notifyDataSetChanged();
            });
        }
        
        // Check if we're processing the last item in the list
        if (allowedEmails.isEmpty()) {
            // Update UI when all emails have been processed
            runOnUiThread(() -> {
                rakazAdapter.notifyDataSetChanged();
                showLoading(false);
                
                // Show empty state if there are no rakazim
                if (rakazList.isEmpty()) {
                    showEmptyState(true);
                } else {
                    showEmptyState(false);
                }
            });
        }
    }
    
    private void checkMegamaExists(String rakazUsername, MegamaCheckCallback callback) {
        // Check if megama exists for this rakaz
        db.collection("schools").document(schoolId)
            .collection("megamot")
            .document(rakazUsername)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    callback.onResult(task.getResult().exists());
                } else {
                    callback.onResult(false);
                }
            });
    }
    
    private interface MegamaCheckCallback {
        void onResult(boolean hasMegama);
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
                } else if (rakazItem.hasMegama()) {
                    // View megama - first check if the megama exists using its name as document ID
                    final String megamaName = rakazItem.getMegamaName() != null ? 
                                        rakazItem.getMegamaName() : 
                                        rakazItem.getAssignedMegama();
                    
                    if (megamaName != null && !megamaName.isEmpty()) {
                        // Log for debugging
                        Log.d(TAG, "Attempting to open megama: " + megamaName);
                        
                        // First check if the megama exists with megama name as document ID
                        db.collection("schools").document(schoolId)
                            .collection("megamot").document(megamaName)
                            .get()
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                    // Megama exists with name as document ID
                                    Intent intent = new Intent(manageRakaz.this, MegamaPreview.class);
                                    intent.putExtra("schoolId", schoolId);
                                    intent.putExtra("rakazUsername", rakazItem.getUsername());
                                    intent.putExtra("isManager", true);
                                    intent.putExtra("megamaName", megamaName);
                                    intent.putExtra("megamaDocId", megamaName); // Use name as document ID
                                    Log.d(TAG, "Opening megama with megamaName: " + megamaName);
                                    startActivity(intent);
                                } else {
                                    // Try the old way with rakaz username as document ID
                                    db.collection("schools").document(schoolId)
                                        .collection("megamot").document(rakazItem.getUsername())
                                        .get()
                                        .addOnCompleteListener(usernameTask -> {
                                            if (usernameTask.isSuccessful() && usernameTask.getResult() != null && 
                                                usernameTask.getResult().exists()) {
                                                // Megama exists with username as document ID (old way)
                                                Intent intent = new Intent(manageRakaz.this, MegamaPreview.class);
                                                intent.putExtra("schoolId", schoolId);
                                                intent.putExtra("rakazUsername", rakazItem.getUsername());
                                                intent.putExtra("isManager", true);
                                                intent.putExtra("megamaName", megamaName);
                                                intent.putExtra("megamaDocId", rakazItem.getUsername()); // Old style
                                                Log.d(TAG, "Opening megama with username as docId: " + rakazItem.getUsername());
                                                startActivity(intent);
                                            } else {
                                                // Megama doesn't exist at all
                                                Log.e(TAG, "Megama not found with either name or username as document ID");
                                                Toast.makeText(manageRakaz.this, "המגמה לא נמצאה במערכת", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                }
                            });
                    } else {
                        // No megama name available
                        Toast.makeText(manageRakaz.this, "שם המגמה חסר", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // No megama
                    if (rakazItem.getAssignedMegama() != null && !rakazItem.getAssignedMegama().isEmpty()) {
                        Toast.makeText(manageRakaz.this, "רכז טרם יצר את מגמת " + rakazItem.getAssignedMegama(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(manageRakaz.this, "רכז טרם יצר מגמה", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            
            // Handle edit button click
            holder.editButton.setOnClickListener(v -> {
                if (rakazItem.getUsername().isEmpty()) {
                    // Can't edit unregistered rakaz
                    Toast.makeText(manageRakaz.this, "רכז טרם נרשם למערכת", Toast.LENGTH_SHORT).show();
                } else {
                    // TODO: Implement rakaz edit functionality
                    Toast.makeText(manageRakaz.this, "עריכת רכז בפיתוח", Toast.LENGTH_SHORT).show();
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
        builder.setPositiveButton("מחק", (dialog, which) -> {
            deleteAllowedRakazEmail(rakazItem.getEmail());
        });
        builder.setNegativeButton("בטל", null);
        builder.show();
    }
    
    private void confirmDeleteRakaz(RakazItem rakazItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("מחיקת רכז");
        builder.setMessage("האם למחוק את הרכז " + rakazItem.getFullName() + "?\n\nפעולה זו תמחק גם את המגמה אם קיימת!");
        builder.setPositiveButton("מחק", (dialog, which) -> {
            deleteRegisteredRakaz(rakazItem);
        });
        builder.setNegativeButton("בטל", null);
        builder.show();
    }
    
    private void deleteAllowedRakazEmail(String email) {
        db.collection("schools").document(schoolId)
            .collection("allowedRakazEmails").document(email)
            .delete()
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(manageRakaz.this, "הרכז הוסר בהצלחה", Toast.LENGTH_SHORT).show();
                // Reload rakazim
                loadRakazim();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting allowed email", e);
                Toast.makeText(manageRakaz.this, "שגיאה במחיקת הרכז: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    private void deleteRegisteredRakaz(RakazItem rakazItem) {
        // Delete from rakazim collection
        db.collection("schools").document(schoolId)
            .collection("rakazim").document(rakazItem.getUsername())
            .delete()
            .addOnSuccessListener(aVoid -> {
                // If email exists, also remove from allowedRakazEmails
                if (rakazItem.getEmail() != null && !rakazItem.getEmail().isEmpty()) {
                    db.collection("schools").document(schoolId)
                        .collection("allowedRakazEmails").document(rakazItem.getEmail())
                        .delete();
                }
                
                // If megama exists, delete it too
                if (rakazItem.hasMegama()) {
                    db.collection("schools").document(schoolId)
                        .collection("megamot").document(rakazItem.getUsername())
                        .delete();
                }
                
                Toast.makeText(manageRakaz.this, "הרכז הוסר בהצלחה", Toast.LENGTH_SHORT).show();
                // Reload rakazim
                loadRakazim();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting rakaz", e);
                Toast.makeText(manageRakaz.this, "שגיאה במחיקת הרכז: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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