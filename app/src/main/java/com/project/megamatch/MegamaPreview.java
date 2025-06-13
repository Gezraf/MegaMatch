package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * מחלקה זו מציגה תצוגה מקדימה של מגמה, כולל פרטיה, דרישות קבלה ותמונות מצורפות.
 * היא תומכת בשני מצבי תצוגה: למשתמש רגיל ולמנהל, כאשר למנהל יש אפשרות למחוק את המגמה.
 */
public class MegamaPreview extends AppCompatActivity {

    private static final String TAG = "MegamaPreview";

    // רכיבי ממשק משתמש
    private TextView greetingText, megamaTitle, megamaDescription, imageCounter;
    private TextView requirementExam, requirementGrade, noRequirementsText, noImagesText;
    private Button backButton;
    private ImageButton prevImageButton, nextImageButton;
    private ViewPager2 imageViewPager;
    private LinearLayout customRequirementsContainer;

    // פיירבייס
    private FirebaseFirestore fireDB;

    // נתונים
    private String schoolId;
    private String username;
    private String megamaName;
    private String megamaDocId; // מזהה המסמך עבור המגמה (יכול להיות שם המגמה או שם המשתמש של הרכז)
    private List<String> imageUrls = new ArrayList<>();
    private int currentImagePosition = 0;
    private boolean isManager = false; // דגל לזיהוי אם המשתמש הוא מנהל
    private ImageButton deleteButton; // כפתור מחיקה למנהלים

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.megama_preview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.megamaPreview), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // אתחול Firebase
        fireDB = FirebaseFirestore.getInstance();

        // אתחול רכיבי ממשק המשתמש
        initializeViews();

        // קבלת נתונים מהאינטנט
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            schoolId = extras.getString("schoolId", "");
            username = extras.getString("username", "");
            megamaName = extras.getString("megamaName", "");
            megamaDocId = extras.getString("megamaDocId", "");
            isManager = extras.getBoolean("isManager", false);
            
            // לתאימות לאחור
            if (megamaDocId == null || megamaDocId.isEmpty()) {
                megamaDocId = megamaName; // ברירת מחדל לשם המגמה אם לא צוין
            }
            
            Log.d(TAG, "התקבל מהאינטנט - שם מגמה: " + megamaName + ", מזהה מסמך מגמה: " + megamaDocId + ", האם מנהל: " + isManager);

            // אם יש לנו את מזהה המסמך ישירות, טען את פרטי המגמה
            if (megamaDocId != null && !megamaDocId.isEmpty()) {
                loadMegamaDetailsDirect();
            } else {
                // אחרת, טען דרך מסמך הרכז תחילה
                loadMegamaData();
            }
        } else {
            Toast.makeText(this, "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show();
            finish();
        }

        // הגדרת מאזיני לחיצה
        setupClickListeners();
    }

    /**
     * מאתחל את כל רכיבי ממשק המשתמש.
     */
    private void initializeViews() {
        greetingText = findViewById(R.id.greetingText);
        megamaTitle = findViewById(R.id.megamaTitle);
        megamaDescription = findViewById(R.id.megamaDescription);
        imageCounter = findViewById(R.id.imageCounter);
        requirementExam = findViewById(R.id.requirementExam);
        requirementGrade = findViewById(R.id.requirementGrade);
        noRequirementsText = findViewById(R.id.noRequirementsText);
        noImagesText = findViewById(R.id.noImagesText);
        backButton = findViewById(R.id.backButton);
        prevImageButton = findViewById(R.id.prevImageButton);
        nextImageButton = findViewById(R.id.nextImageButton);
        imageViewPager = findViewById(R.id.imageViewPager);
        customRequirementsContainer = findViewById(R.id.customRequirementsContainer);

        // טפל מיידית בממשק המשתמש של המנהל אם נדרש
        if (isManager) {
            // הסתר את הברכה מיד כדי למנוע הבהוב
            if (greetingText != null) {
                greetingText.setVisibility(View.GONE);
            }
        }

        // הגדרת ViewPager
        ImageSliderAdapter sliderAdapter = new ImageSliderAdapter();
        imageViewPager.setAdapter(sliderAdapter);
        
        // אם המשתמש הוא מנהל, החלף את הברכה בכפתור מחיקה
        if (isManager) {
            Log.d(TAG, "מצב מנהל זוהה, מגדיר כפתור מחיקה");
            setupDeleteButton();
            
            // גישה חלופית - שנה ישירות את הפריסה
            if (greetingText != null) {
                // החלף טקסט ברכה בכפתור
                ViewGroup rootLayout = findViewById(R.id.megamaPreview);
                if (rootLayout != null) {
                    Log.d(TAG, "מוסיף כפתור מחיקה לפריסת השורש כגישת גיבוי");
                    
                    // צור כפתור מחיקה נוסף כגיבוי
                    ImageButton backupDeleteButton = new ImageButton(this);
                    backupDeleteButton.setImageResource(android.R.drawable.ic_menu_delete);
                    backupDeleteButton.setBackgroundColor(Color.RED);
                    backupDeleteButton.setColorFilter(Color.WHITE);
                    
                    // הגדר גודל קבוע
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            dpToPx(60),
                            dpToPx(60)
                    );
                    
                    // מקם אותו בפינה הימנית העליונה
                    params.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                    params.setMargins(0, dpToPx(16), dpToPx(16), 0);
                    backupDeleteButton.setLayoutParams(params);
                    
                    // הוסף מאזין לחיצה
                    backupDeleteButton.setOnClickListener(v -> showDeleteConfirmation());
                    
                    // הוסף לפריסה
                    rootLayout.addView(backupDeleteButton);
                }
            }
        }
        
        // הגדרת מאזין שינוי עמוד של ViewPager
        imageViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentImagePosition = position;
                updateImageCounter();
            }
        });
    }

    /**
     * מגדיר את מאזיני הלחיצה עבור כפתורי הניווט.
     */
    private void setupClickListeners() {
        // כפתור חזרה
        backButton.setOnClickListener(v -> finish());

        // כפתורי ניווט תמונה
        prevImageButton.setOnClickListener(v -> {
            if (currentImagePosition > 0) {
                imageViewPager.setCurrentItem(currentImagePosition - 1);
            }
        });

        nextImageButton.setOnClickListener(v -> {
            if (currentImagePosition < imageUrls.size() - 1) {
                imageViewPager.setCurrentItem(currentImagePosition + 1);
            }
        });
    }

    /**
     * טוען נתוני מגמה מפיירסטור, תחילה באמצעות מסמך הרכז.
     */
    private void loadMegamaData() {
        // תחילה קבל את שם המגמה ממסמך הרכז
        fireDB.collection("schools").document(schoolId)
              .collection("rakazim").document(username)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      // קבלת שם הרכז וברכה (רק עבור מי שאינם מנהלים)
                      if (!isManager) {
                          String firstName = documentSnapshot.getString("firstName");
                          if (firstName != null && !firstName.isEmpty()) {
                              greetingText.setText("שלום " + firstName);
                          } else {
                              greetingText.setText("שלום " + username);
                          }
                      }

                      // קבל שם מגמה
                      megamaName = documentSnapshot.getString("megama");
                      if (megamaName != null && !megamaName.isEmpty()) {
                          // כעת קבל את פרטי המגמה
                          loadMegamaDetails();
                      } else {
                          Toast.makeText(this, "לא נמצאה מגמה", Toast.LENGTH_SHORT).show();
                          finish();
                      }
                  } else {
                      Toast.makeText(this, "לא נמצאו פרטי רכז", Toast.LENGTH_SHORT).show();
                      finish();
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e(TAG, "שגיאה בטעינת נתוני רכז: " + e.getMessage(), e);
                  Toast.makeText(this, "שגיאה בטעינת פרטי רכז", Toast.LENGTH_SHORT).show();
                  finish();
              });
    }

    /**
     * טוען את פרטי המגמה ישירות ממסמך המגמה בפיירסטור.
     */
    private void loadMegamaDetailsDirect() {
        // אם זו תצוגת מנהל, אל תגדיר ברכה כלל
        if (isManager) {
            // וודא שהברכה אינה גלויה למנהלים
            if (greetingText != null) {
                Log.d(TAG, "תצוגת מנהל - הופך את הברכה לכפתור מחיקה");
                // No need to set greetingText visibility to GONE, as it's already handled in initializeViews()
                // The delete button will overlay it
                
                // Ensure megamaDocId is correctly set if it was loaded from Rakaz document
                if (megamaDocId == null || megamaDocId.isEmpty()) {
                    megamaDocId = megamaName; // Fallback
                }
            }
        }

        fireDB.collection("schools").document(schoolId)
                .collection("megamot").document(megamaDocId)
                .get()
                .addOnSuccessListener(this::displayMegamaDetails)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בטעינת פרטי מגמה ישירות: " + e.getMessage(), e);
                    Toast.makeText(this, "שגיאה בטעינת פרטי מגמה", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    /**
     * טוען את פרטי המגמה ממסמך המגמה בפיירסטור.
     * זוהי שיטה משנית ל-loadMegamaDetailsDirect, המשמשת לאחר שליפת שם המגמה ממסמך הרכז.
     */
    private void loadMegamaDetails() {
        fireDB.collection("schools").document(schoolId)
            .collection("megamot").document(megamaName) // Use megamaName retrieved from rakaz doc
            .get()
            .addOnSuccessListener(this::displayMegamaDetails)
            .addOnFailureListener(e -> {
                Log.e(TAG, "שגיאה בטעינת פרטי מגמה: " + e.getMessage(), e);
                Toast.makeText(this, "שגיאה בטעינת פרטי מגמה", Toast.LENGTH_SHORT).show();
                finish();
            });
    }

    /**
     * מציג את פרטי המגמה על גבי ממשק המשתמש.
     * @param document מסמך ה-Firestore המכיל את פרטי המגמה.
     */
    private void displayMegamaDetails(DocumentSnapshot document) {
        if (document.exists()) {
            // קבלת נתונים מהמסמך
            String fetchedMegamaName = document.getString("name");
            String description = document.getString("description");
            Boolean requiresExam = document.getBoolean("requiresExam");
            Boolean requiresGradeAvg = document.getBoolean("requiresGradeAvg");
            Long requiredGradeAvg = document.getLong("requiredGradeAvg");
            ArrayList<String> customConditions = (ArrayList<String>) document.get("customConditions");
            ArrayList<String> fetchedImageUrls = (ArrayList<String>) document.get("imageUrls");

            // עדכון רכיבי ממשק המשתמש
            if (fetchedMegamaName != null) {
                megamaTitle.setText(fetchedMegamaName);
                this.megamaName = fetchedMegamaName; // Update internal megamaName
            }
            megamaDescription.setText(description != null ? description : "אין תיאור.");

            // טפל בדרישות
            boolean hasRequirements = false;
            if (Boolean.TRUE.equals(requiresExam)) {
                requirementExam.setVisibility(View.VISIBLE);
                hasRequirements = true;
            } else {
                requirementExam.setVisibility(View.GONE);
            }

            if (Boolean.TRUE.equals(requiresGradeAvg) && requiredGradeAvg != null) {
                requirementGrade.setText("דורש ממוצע ציונים: " + requiredGradeAvg);
                requirementGrade.setVisibility(View.VISIBLE);
                hasRequirements = true;
            } else {
                requirementGrade.setVisibility(View.GONE);
            }

            // הוסף תנאים מותאמים אישית
            if (customConditions != null && !customConditions.isEmpty()) {
                customRequirementsContainer.setVisibility(View.VISIBLE);
                for (String condition : customConditions) {
                    addCustomRequirement(condition);
                }
                hasRequirements = true;
            } else {
                customRequirementsContainer.setVisibility(View.GONE);
            }

            if (!hasRequirements) {
                noRequirementsText.setVisibility(View.VISIBLE);
            } else {
                noRequirementsText.setVisibility(View.GONE);
            }

            // טפל בתמונות
            if (fetchedImageUrls != null && !fetchedImageUrls.isEmpty()) {
                imageUrls.clear();
                imageUrls.addAll(fetchedImageUrls);
                imageViewPager.getAdapter().notifyDataSetChanged();
                updateImageCounter();
                prevImageButton.setVisibility(View.VISIBLE);
                nextImageButton.setVisibility(View.VISIBLE);
                noImagesText.setVisibility(View.GONE);
            } else {
                imageViewPager.setVisibility(View.GONE);
                prevImageButton.setVisibility(View.GONE);
                nextImageButton.setVisibility(View.GONE);
                imageCounter.setVisibility(View.GONE);
                noImagesText.setVisibility(View.VISIBLE);
            }
        } else {
            Toast.makeText(this, "מגמה לא נמצאה", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * מוסיף דרישה מותאמת אישית לממשק המשתמש.
     * @param requirement הדרישה המותאמת אישית להוספה.
     */
    private void addCustomRequirement(String requirement) {
        TextView textView = new TextView(this);
        textView.setText("• " + requirement);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 0);
        textView.setLayoutParams(params);
        customRequirementsContainer.addView(textView);
    }

    /**
     * מעדכן את מונה התמונות המוצגות.
     */
    private void updateImageCounter() {
        if (!imageUrls.isEmpty()) {
            String counterText = (currentImagePosition + 1) + " / " + imageUrls.size();
            imageCounter.setText(counterText);
            imageCounter.setVisibility(View.VISIBLE);
        } else {
            imageCounter.setVisibility(View.GONE);
        }
    }

    /**
     * מגדיר את כפתור המחיקה למנהלים.
     * מחליף את ה-greetingText בכפתור מחיקה.
     */
    private void setupDeleteButton() {
        Log.d(TAG, "Setting up delete button for manager");
        if (greetingText != null) {
            // Replace greetingText with a Button
            ViewGroup parent = (ViewGroup) greetingText.getParent();
            int index = parent.indexOfChild(greetingText);
            parent.removeView(greetingText);

            deleteButton = new ImageButton(this);
            deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
            deleteButton.setBackgroundColor(Color.TRANSPARENT);
            deleteButton.setColorFilter(Color.WHITE);
            
            // Set a size for the button (e.g., 48dp)
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dpToPx(48),
                    dpToPx(48)
            );
            // Center the button horizontally
            params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            deleteButton.setLayoutParams(params);

            deleteButton.setOnClickListener(v -> showDeleteConfirmation());
            parent.addView(deleteButton, index); // Add at the same position
        }
    }

    /**
     * ממיר יחידות dp לפיקסלים.
     * @param dp ערך ב-dp.
     * @return הערך המומר בפיקסלים.
     */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * מציג דיאלוג אישור למחיקת המגמה.
     */
    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("מחיקת מגמה")
                .setMessage("האם אתה בטוח שברצונך למחוק את המגמה " + megamaName + "?")
                .setPositiveButton("מחק", new DialogInterface.OnClickListener() {
                    private static final int COUNTDOWN_TIME = 3;
                    private CountDownTimer countDownTimer;
                    private Button positiveButton;

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (positiveButton != null && !positiveButton.isEnabled()) {
                            deleteMegama();
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
                                    deleteMegama();
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
     * מוחק את המגמה מפיירסטור.
     */
    private void deleteMegama() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("מוחק מגמה...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        fireDB.collection("schools").document(schoolId)
                .collection("megamot").document(megamaDocId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MegamaPreview.this, "מגמה נמחקה בהצלחה", Toast.LENGTH_SHORT).show();
                    // Update rakaz document to remove megama reference
                    fireDB.collection("schools").document(schoolId)
                            .collection("rakazim").document(username)
                            .update("megama", null) // Remove megama name from rakaz document
                            .addOnSuccessListener(aVoid2 -> Log.d(TAG, "הפניית מגמה הוסרה ממסמך הרכז"))
                            .addOnFailureListener(e -> Log.e(TAG, "שגיאה בהסרת הפניית מגמה ממסמך הרכז", e));
                    progressDialog.dismiss();
                    finish(); // Close activity after deletion
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה במחיקת מגמה: " + e.getMessage(), e);
                    Toast.makeText(MegamaPreview.this, "שגיאה במחיקת מגמה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    progressDialog.dismiss();
                });
    }

    /**
     * מתאם (Adapter) עבור ה-ViewPager2 המציג את תמונות המגמה.
     */
    private class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder> {

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.image_item, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String imageUrl = imageUrls.get(position);
            Glide.with(holder.imageView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        /**
         * מחזיק תצוגה (ViewHolder) עבור פריטי תמונה במחוון התמונות.
         */
        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
            }
        }
    }
} 