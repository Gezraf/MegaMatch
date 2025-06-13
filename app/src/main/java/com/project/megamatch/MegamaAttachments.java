package com.project.megamatch;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.project.megamatch.megamaCreate.Megama;
import com.project.megamatch.utils.SupabaseStorageUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

/**
 * מחלקה זו אחראית על ניהול קבצים מצורפים (תמונות) למגמה.
 * היא מאפשרת למשתמש להוסיף תמונות מהגלריה, לצלם תמונות חדשות, או להוסיף תמונות מ-URL,
 * להציגן ולנהל אותן לפני שמירתן לפיירסטור ולאחסון Supabase.
 */
public class MegamaAttachments extends AppCompatActivity {
    private static final String TAG = "MegamaAttachments";

    // רכיבי ממשק משתמש
    private TextView greetingText, megamaText;
    private Button backButton;
    private MaterialButton createMegamaButton, addUrlImageButton;
    private ImageButton expandImageSectionButton;
    private EditText imageUrlInput;
    private LinearLayout imageSection;
    private FrameLayout addImageButton;
    private RecyclerView selectedImagesRecyclerView;
    private ProgressBar progressBar;

    // פיירבייס
    private FirebaseFirestore fireDB;

    // נתונים
    private String schoolId;
    private String username;
    private String megamaName;
    private String megamaDescription;
    private boolean requiresExam;
    private boolean requiresGradeAvg;
    private int requiredGradeAvg;
    private ArrayList<String> customConditions;
    private ArrayList<Uri> selectedImageUris = new ArrayList<>();
    private ArrayList<String> uploadedImageUrls = new ArrayList<>();

    // טיפול בתמונות
    private Uri currentPhotoUri;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ImagesAdapter imagesAdapter;
    
    /**
     * מחלקת עזר למניעת לחיצות מרובות מהירות.
     */
    private static class DebounceClickListener implements View.OnClickListener {
        private static final long DEBOUNCE_INTERVAL_MS = 800; // 800 מילישניות
        private final View.OnClickListener clickListener;
        private long lastClickTime = 0;
        
        DebounceClickListener(View.OnClickListener clickListener) {
            this.clickListener = clickListener;
        }
        
        @Override
        public void onClick(View v) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime > DEBOUNCE_INTERVAL_MS) {
                lastClickTime = currentTime;
                clickListener.onClick(v);
            } else {
                Log.d("DebounceClick", "לחיצה התעלמה, מהר מדי לאחר לחיצה קודמת");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.megama_attachments);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.megamaAttachments), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // אתחול Firebase
        fireDB = FirebaseFirestore.getInstance();

        // אתחול רכיבי ממשק המשתמש
        initializeViews();
        setupImagePickers();
        setupStorageAndDatabase();
        setupListeners();
        
        // בדוק אם אנחנו במצב עדכון עם תמונות קיימות
        boolean isUpdate = getIntent().getBooleanExtra("isUpdate", false);
        if (isUpdate) {
            // הגדר טקסט כפתור לעדכון
            createMegamaButton.setText("עדכון מגמה");
            
            // טען תמונות קיימות
            ArrayList<String> imageUrls = getIntent().getStringArrayListExtra("imageUrls");
            if (imageUrls != null && !imageUrls.isEmpty()) {
                for (String imageUrl : imageUrls) {
                    uploadedImageUrls.add(imageUrl);
                }
                imagesAdapter.notifyDataSetChanged();
                updateImageCountText();
                
                // הרחב אוטומטית את קטע התמונה כאשר יש תמונות קיימות
                imageSection.setVisibility(View.VISIBLE);
                expandImageSectionButton.setImageResource(android.R.drawable.arrow_up_float);
            }
        }
    }

    /**
     * מאתחל את כל רכיבי ממשק המשתמש.
     */
    private void initializeViews() {
        greetingText = findViewById(R.id.greetingText);
        megamaText = findViewById(R.id.megamaText);
        backButton = findViewById(R.id.backButton);
        createMegamaButton = findViewById(R.id.createMegamaButton);
        expandImageSectionButton = findViewById(R.id.expandImageSectionButton);
        imageSection = findViewById(R.id.imageSection);
        addImageButton = findViewById(R.id.addImageButton);
        imageUrlInput = findViewById(R.id.imageUrlInput);
        addUrlImageButton = findViewById(R.id.addUrlImageButton);
        selectedImagesRecyclerView = findViewById(R.id.selectedImagesRecyclerView);
        progressBar = findViewById(R.id.progressBar);

        // הגדרת RecyclerView
        selectedImagesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        imagesAdapter = new ImagesAdapter(selectedImageUris);
        selectedImagesRecyclerView.setAdapter(imagesAdapter);
    }

    /**
     * מגדיר את הלאנצ'רים (launchers) לבחירת תמונות מהגלריה או צילום תמונה חדשה.
     */
    private void setupImagePickers() {
        // לאנצ'ר בוחר תמונות (גלריה)
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            addImageToSelection(imageUri);
                        }
                    }
                });

        // לאנצ'ר מצלמה
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && currentPhotoUri != null) {
                        addImageToSelection(currentPhotoUri);
                    }
                });
    }

    /**
     * מאתחל את נתוני האחסון והמסד נתונים, כולל קבלת נתונים מהאינטנט.
     */
    private void setupStorageAndDatabase() {
        // קבלת נתונים מהאינטנט
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            schoolId = extras.getString("schoolId", "");
            username = extras.getString("username", "");
            megamaName = extras.getString("megamaName", "");
            megamaDescription = extras.getString("megamaDescription", "");
            requiresExam = extras.getBoolean("requiresExam", false);
            requiresGradeAvg = extras.getBoolean("requiresGradeAvg", false);
            requiredGradeAvg = extras.getInt("requiredGradeAvg", 0);
            customConditions = extras.getStringArrayList("customConditions");

            Log.d(TAG, "נתונים ראשוניים מהאינטנט - שם מגמה: " + megamaName);
            
            // תמיד טען פרטי רכז כדי לוודא שיש לנו את שם המגמה הנכון
            loadRakazDetails();
        }
    }

    /**
     * טוען פרטי רכז מפיירסטור כדי לוודא את שם המגמה הנכון.
     */
    private void loadRakazDetails() {
        Log.d(TAG, "טוען פרטי רכז עבור שם משתמש: " + username);
        fireDB.collection("schools").document(schoolId)
            .collection("rakazim").document(username)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // טעינת שם פרטי
                    String firstName = documentSnapshot.getString("firstName");
                    if (firstName != null && !firstName.isEmpty()) {
                        greetingText.setText("שלום " + firstName);
                    } else {
                        greetingText.setText("שלום " + username);
                    }

                    // קבל את שם המגמה ממסמך הרכז - זהו מקור האמת
                    String rakazMegamaName = documentSnapshot.getString("megama");
                    Log.d(TAG, "מסמך הרכז מכיל שם מגמה: " + rakazMegamaName);
                    
                    if (rakazMegamaName != null && !rakazMegamaName.isEmpty()) {
                        megamaName = rakazMegamaName; // Update with the actual megama name from rakaz
                        fetchMegamaDataFromFirestore(megamaName);
                    } else {
                        Log.w(TAG, "מסמך הרכז אינו מכיל שם מגמה. משתמש בשם מגמה מהאינטנט: " + megamaName);
                        updateUIWithMegamaDetails();
                    }
                } else {
                    Log.w(TAG, "מסמך רכז לא נמצא עבור שם משתמש: " + username);
                    // Fallback to megamaName from intent if rakaz document not found
                    updateUIWithMegamaDetails();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "שגיאה בטעינת פרטי רכז: " + e.getMessage(), e);
                // Fallback to megamaName from intent on failure
                updateUIWithMegamaDetails();
            });
    }

    /**
     * שולף נתוני מגמה מפיירסטור.
     * @param megamaName שם המגמה לשליפה.
     */
    private void fetchMegamaDataFromFirestore(String megamaName) {
        fireDB.collection("schools").document(schoolId)
                .collection("megamot").document(megamaName)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        megamaDescription = documentSnapshot.getString("description");
                        requiresExam = Boolean.TRUE.equals(documentSnapshot.getBoolean("requiresExam"));
                        requiresGradeAvg = Boolean.TRUE.equals(documentSnapshot.getBoolean("requiresGradeAvg"));
                        if (documentSnapshot.contains("requiredGradeAvg")) {
                            Long avg = documentSnapshot.getLong("requiredGradeAvg");
                            requiredGradeAvg = (avg != null) ? avg.intValue() : 0;
                        }
                        customConditions = (ArrayList<String>) documentSnapshot.get("customConditions");
                        uploadedImageUrls = (ArrayList<String>) documentSnapshot.get("imageUrls");
                        if (uploadedImageUrls == null) {
                            uploadedImageUrls = new ArrayList<>();
                        }
                        
                        // Convert uploaded URLs to Uris for the adapter for display
                        selectedImageUris.clear();
                        for (String url : uploadedImageUrls) {
                            selectedImageUris.add(Uri.parse(url));
                        }
                        imagesAdapter.notifyDataSetChanged();
                        updateImageCountText();

                        // Automatically expand image section if there are existing images
                        if (!uploadedImageUrls.isEmpty()) {
                            imageSection.setVisibility(View.VISIBLE);
                            expandImageSectionButton.setImageResource(android.R.drawable.arrow_up_float);
                        }

                    } else {
                        Log.d(TAG, "מסמך מגמה לא נמצא בפיירסטור: " + megamaName);
                    }
                    updateUIWithMegamaDetails();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בשליפת נתוני מגמה: " + e.getMessage(), e);
                    Toast.makeText(this, "שגיאה בטעינת פרטי מגמה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updateUIWithMegamaDetails();
                });
    }

    /**
     * מעדכן את ממשק המשתמש עם פרטי המגמה הנוכחיים.
     */
    private void updateUIWithMegamaDetails() {
        megamaText.setText("אתה עומד ליצור את מגמת: " + megamaName);
        
        // If in create mode, ensure greeting text is set
        if (greetingText.getText().equals("טוען...")) {
            String firstName = getIntent().getStringExtra("firstName");
            if (firstName != null && !firstName.isEmpty()) {
                greetingText.setText("שלום " + firstName);
            } else {
                greetingText.setText("שלום " + username);
            }
        }
    }

    /**
     * מגדיר את כל המאזינים לרכיבי ממשק המשתמש.
     */
    private void setupListeners() {
        backButton.setOnClickListener(new DebounceClickListener(v -> goBackToMegamaCreate()));
        createMegamaButton.setOnClickListener(new DebounceClickListener(v -> createNewMegama()));

        expandImageSectionButton.setOnClickListener(new DebounceClickListener(v -> {
            if (imageSection.getVisibility() == View.GONE) {
                imageSection.setVisibility(View.VISIBLE);
                expandImageSectionButton.setImageResource(android.R.drawable.arrow_up_float);
            } else {
                imageSection.setVisibility(View.GONE);
                expandImageSectionButton.setImageResource(android.R.drawable.arrow_down_float);
            }
        }));

        addImageButton.setOnClickListener(new DebounceClickListener(v -> showImageSourceOptions()));

        addUrlImageButton.setOnClickListener(new DebounceClickListener(v -> {
            String imageUrl = imageUrlInput.getText().toString().trim();
            if (!imageUrl.isEmpty()) {
                if (isValidImageUrl(imageUrl)) {
                    Uri imageUri = Uri.parse(imageUrl);
                    addImageToSelection(imageUri);
                    imageUrlInput.setText(""); // Clear input
                } else {
                    Toast.makeText(this, "כתובת URL לא חוקית לתמונה", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "נא להזין כתובת URL של תמונה", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    /**
     * מציג דיאלוג לבחירת מקור תמונה (מצלמה או גלריה).
     */
    private void showImageSourceOptions() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.image_source_bottom_sheet, null);
        bottomSheetDialog.setContentView(view);

        MaterialButton btnCamera = view.findViewById(R.id.btnCamera);
        MaterialButton btnGallery = view.findViewById(R.id.btnGallery);

        btnCamera.setOnClickListener(v -> {
            openCamera();
            bottomSheetDialog.dismiss();
        });

        btnGallery.setOnClickListener(v -> {
            openGallery();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    /**
     * מפעיל את אפליקציית המצלמה לצילום תמונה.
     */
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "שגיאה ביצירת קובץ תמונה", ex);
                Toast.makeText(this, "שגיאה ביצירת קובץ תמונה", Toast.LENGTH_SHORT).show();
                return;
            }
            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        "com.project.megamatch.fileprovider",
                        photoFile);
                takePictureLauncher.launch(currentPhotoUri);
            }
        } else {
            Toast.makeText(this, "אין אפליקציית מצלמה במכשיר זה", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * יוצר קובץ תמונה זמני עבור תמונה שתצולם.
     * @return קובץ תמונה.
     * @throws IOException אם מתרחשת שגיאת קלט/פלט.
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    /**
     * פותח את גלריית התמונות לבחירת תמונה.
     */
    private void openGallery() {
        Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(pickPhoto);
    }

    /**
     * מוסיף URI של תמונה לרשימת התמונות שנבחרו ומעדכן את הממשק.
     * @param imageUri URI של התמונה להוספה.
     */
    private void addImageToSelection(Uri imageUri) {
        if (!selectedImageUris.contains(imageUri)) {
            selectedImageUris.add(imageUri);
            imagesAdapter.notifyDataSetChanged();
            updateImageCountText();
        } else {
            Toast.makeText(this, "תמונה זו כבר נבחרה", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * מעלה את התמונות שנבחרו לאחסון Supabase.
     * לאחר העלאה מוצלחת, יוצר או מעדכן את מסמך המגמה בפיירסטור.
     * @param imageUri URI של התמונה להעלאה.
     */
    private void uploadImageToSupabase(Uri imageUri) {
        progressBar.setVisibility(View.VISIBLE);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "megama_" + megamaName + "_" + timeStamp + ".jpg";
        String storagePath = "schools/" + schoolId + "/megamot_attachments/" + fileName;

        SupabaseStorageUtil.getInstance().uploadFile(
                this,
                imageUri,
                storagePath,
                new SupabaseStorageUtil.FileUploadListener() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        Log.d(TAG, "תמונה הועלתה בהצלחה: " + imageUrl);
                        uploadedImageUrls.add(imageUrl);
                        // If all images are uploaded, create/update Firestore document
                        if (uploadedImageUrls.size() == selectedImageUris.size()) {
                            createNewMegamaDocumentInFirestore();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "שגיאה בהעלאת תמונה: " + errorMessage);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MegamaAttachments.this, "שגיאה בהעלאת תמונה: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * בודק אם כתובת URL של תמונה תקינה.
     * @param url כתובת ה-URL לבדיקה.
     * @return true אם ה-URL תקין, false אחרת.
     */
    private boolean isValidImageUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * חוזר למסך יצירת המגמה (MegamaCreate).
     */
    private void goBackToMegamaCreate() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("megamaName", megamaName);
        resultIntent.putExtra("megamaDescription", megamaDescription);
        resultIntent.putExtra("requiresExam", requiresExam);
        resultIntent.putExtra("requiresGradeAvg", requiresGradeAvg);
        resultIntent.putExtra("requiredGradeAvg", requiredGradeAvg);
        resultIntent.putStringArrayListExtra("customConditions", customConditions);
        setResult(RESULT_CANCELED, resultIntent); // Indicate cancellation if not explicitly created
        finish();
    }

    /**
     * יוצר מגמה חדשה (או מעדכן קיימת) בפיירסטור.
     * מטפל בהעלאת תמונות לפני יצירת/עדכון המסמך.
     */
    private void createNewMegama() {
        if (megamaName == null || megamaName.isEmpty()) {
            Toast.makeText(this, "שם מגמה חסר. אנא חזור לדף הקודם.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        createMegamaButton.setEnabled(false);

        // Clear previous uploaded URLs to avoid duplicates if re-uploading
        uploadedImageUrls.clear();

        if (selectedImageUris.isEmpty()) {
            // No images to upload, proceed directly to Firestore
            createNewMegamaDocumentInFirestore();
        } else {
            // Upload all selected images first
            for (Uri uri : selectedImageUris) {
                // Check if it's already an uploaded URL (from update mode)
                if (uri.toString().startsWith("http")) {
                    uploadedImageUrls.add(uri.toString());
                    // If all images are pre-uploaded URLs, create/update Firestore document
                    if (uploadedImageUrls.size() == selectedImageUris.size()) {
                        createNewMegamaDocumentInFirestore();
                    }
                } else {
                    uploadImageToSupabase(uri);
                }
            }
        }
    }

    /**
     * יוצר או מעדכן את מסמך המגמה בפיירסטור עם כל הנתונים, כולל כתובות ה-URL של התמונות שהועלו.
     */
    private void createNewMegamaDocumentInFirestore() {
        Map<String, Object> megamaData = new HashMap<>();
        megamaData.put("name", megamaName);
        megamaData.put("description", megamaDescription);
        megamaData.put("rakazUsername", username);
        megamaData.put("requiresExam", requiresExam);
        megamaData.put("requiresGradeAvg", requiresGradeAvg);
        megamaData.put("requiredGradeAvg", requiredGradeAvg);
        megamaData.put("customConditions", customConditions);
        megamaData.put("imageUrls", uploadedImageUrls); // Save uploaded image URLs

        fireDB.collection("schools").document(schoolId)
                .collection("megamot").document(megamaName)
                .set(megamaData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "מסמך מגמה נוצר/עודכן בהצלחה: " + megamaName);
                    // Update rakaz document with megama name
                    fireDB.collection("schools").document(schoolId)
                            .collection("rakazim").document(username)
                            .update("megama", megamaName)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "שם מגמה עודכן במסמך הרכז");
                                finishMegamaCreation();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "שגיאה בעדכון שם מגמה במסמך הרכז", e);
                                Toast.makeText(MegamaAttachments.this, "שגיאה בעדכון מסמך רכז: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                finishMegamaCreation(); // Still finish even if rakaz update fails
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה ביצירת/עדכון מסמך מגמה: " + e.getMessage(), e);
                    progressBar.setVisibility(View.GONE);
                    createMegamaButton.setEnabled(true);
                    Toast.makeText(MegamaAttachments.this, "שגיאה ביצירת/עדכון מגמה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * מסיים את תהליך יצירת/עדכון המגמה ומציג דיאלוג הצלחה.
     */
    private void finishMegamaCreation() {
        progressBar.setVisibility(View.GONE);
        createMegamaButton.setEnabled(true);
        showCustomSuccessDialog("המגמה " + megamaName + " נוצרה/עודכנה בהצלחה!");
    }

    /**
     * מציג דיאלוג הצלחה מותאם אישית.
     * @param message הודעת ההצלחה להצגה.
     */
    private void showCustomSuccessDialog(String message) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.megama_success_dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView dialogTitle = dialog.findViewById(R.id.dialogTitle);
        TextView dialogMessage = dialog.findViewById(R.id.dialogMessage);
        Button dialogButton = dialog.findViewById(R.id.dialogButton);

        dialogTitle.setText("הצלחה!");
        dialogMessage.setText(message);

        dialogButton.setOnClickListener(v -> {
            dialog.dismiss();
            setResult(RESULT_OK);
            finish();
        });
        dialog.show();
    }

    /**
     * מעדכן את טקסט ספירת התמונות.
     */
    private void updateImageCountText() {
        if (selectedImageUris.isEmpty()) {
            greetingText.setText("העלה קבצים מצורפים");
        } else {
            greetingText.setText("קבצים מצורפים (" + selectedImageUris.size() + ")");
        }
    }

    /**
     * מתאם (Adapter) עבור ה-RecyclerView המציג את התמונות שנבחרו/הועלו.
     */
    private class ImagesAdapter extends RecyclerView.Adapter<ImagesAdapter.ImageViewHolder> {
        private final List<Uri> imageUris;
        private final List<String> uploadedUrls;

        public ImagesAdapter(List<Uri> imageUris) {
            this.imageUris = imageUris;
            this.uploadedUrls = MegamaAttachments.this.uploadedImageUrls; // Reference parent's uploaded list
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selected_image, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            Uri currentUri = imageUris.get(position);
            Glide.with(holder.imageView.getContext())
                    .load(currentUri)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(holder.imageView);

            holder.removeButton.setOnClickListener(v -> {
                // Show confirmation dialog
                new AlertDialog.Builder(MegamaAttachments.this)
                        .setTitle("מחיקת תמונה")
                        .setMessage("האם אתה בטוח שברצונך למחוק תמונה זו?")
                        .setPositiveButton("מחק", (dialog, which) -> {
                            // Remove from selected list
                            imageUris.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, imageUris.size());

                            // Remove from uploaded URLs if it exists there
                            if (uploadedUrls.contains(currentUri.toString())) {
                                uploadedUrls.remove(currentUri.toString());
                            }
                            updateImageCountText();
                            Toast.makeText(MegamaAttachments.this, "תמונה נמחקה", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("בטל", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return imageUris.size();
        }

        /**
         * מחזיק תצוגה (ViewHolder) עבור פריטי תמונה ב-RecyclerView.
         */
        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            ImageButton removeButton;

            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
                removeButton = itemView.findViewById(R.id.removeButton);
            }
        }
    }
} 