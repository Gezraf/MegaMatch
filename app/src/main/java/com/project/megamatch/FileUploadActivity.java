package com.project.megamatch;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.project.megamatch.utils.SupabaseStorageManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * מחלקה זו מאפשרת למשתמשים לבחור או לצלם תמונות ולהעלות אותן לאחסון Supabase.
 * היא מטפלת בקבלת תמונות מהמצלמה או מהגלריה, הצגתן והעלאתן, כולל שמירת נתונים בפיירסטור.
 */
public class FileUploadActivity extends AppCompatActivity {
    private static final String TAG = "FileUploadActivity";

    private Button btnTakePhoto, btnChoosePhoto, btnUploadFile;
    private ImageView imagePreview;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private Uri currentPhotoUri;
    private String currentPhotoPath;

    private SharedPreferences sharedPreferences;
    private String schoolId;
    private String username;

    private final ActivityResultLauncher<Intent> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    displayImage(currentPhotoUri);
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    currentPhotoUri = uri;
                    displayImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_upload);

        // אתחול רכיבי הממשק
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnChoosePhoto = findViewById(R.id.btnChoosePhoto);
        btnUploadFile = findViewById(R.id.btnUploadFile);
        imagePreview = findViewById(R.id.imagePreview);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);

        // קבלת פרטי משתמש מהעדפות משותפות
        sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        schoolId = sharedPreferences.getString("loggedInSchoolId", "");
        username = sharedPreferences.getString("loggedInUsername", "");

        // הגדרת מאזינים ללחיצות כפתורים
        btnTakePhoto.setOnClickListener(v -> takePhoto());
        btnChoosePhoto.setOnClickListener(v -> choosePhotoFromGallery());
        btnUploadFile.setOnClickListener(v -> uploadCurrentPhoto());

        // השבתת כפתור ההעלאה בתחילה
        btnUploadFile.setEnabled(false);
    }

    /**
     * מפעיל את אפליקציית המצלמה לצילום תמונה חדשה.
     */
    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // יצירת קובץ שבו התמונה צריכה להישמר
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "שגיאה ביצירת קובץ תמונה", ex);
                Toast.makeText(this, "שגיאה ביצירת קובץ תמונה", Toast.LENGTH_SHORT).show();
                return;
            }

            // המשך רק אם הקובץ נוצר בהצלחה
            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        "com.project.megamatch.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                takePictureLauncher.launch(takePictureIntent);
            }
        } else {
            Toast.makeText(this, "אין אפליקציית מצלמה במכשיר זה", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * יוצר קובץ תמונה ריק זמני.
     * @return קובץ תמונה.
     * @throws IOException אם מתרחשת שגיאת קלט/פלט.
     */
    private File createImageFile() throws IOException {
        // יצירת שם קובץ תמונה
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* קידומת */
                ".jpg",         /* סיומת */
                storageDir      /* ספרייה */
        );

        // שמירת נתיב קובץ לשימוש עם אינטנטים מסוג ACTION_VIEW
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    /**
     * מפעיל את גלריית התמונות לבחירת תמונה קיימת.
     */
    private void choosePhotoFromGallery() {
        pickImageLauncher.launch("image/*");
    }

    /**
     * מציג את התמונה שנבחרה או צולמה בתצוגה מקדימה.
     * @param imageUri URI של התמונה להצגה.
     */
    private void displayImage(Uri imageUri) {
        Glide.with(this)
                .load(imageUri)
                .centerCrop()
                .into(imagePreview);

        btnUploadFile.setEnabled(true);
    }

    /**
     * מעלה את התמונה הנוכחית לאחסון Supabase.
     */
    private void uploadCurrentPhoto() {
        if (currentPhotoUri == null) {
            Toast.makeText(this, "יש לבחור תמונה תחילה", Toast.LENGTH_SHORT).show();
            return;
        }

        // הצגת התקדמות
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("מעלה קובץ...");
        btnUploadFile.setEnabled(false);

        // יצירת נתיב ייחודי לקובץ באחסון Supabase
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String storagePath = "schools/" + schoolId + "/uploads/" + username + "_" + timeStamp + ".jpg";

        // העלאת קובץ לאחסון Supabase
        SupabaseStorageManager.getInstance().uploadFile(
                this,
                currentPhotoUri,
                storagePath,
                new SupabaseStorageManager.FileUploadListener() {
                    @Override
                    public void onSuccess(String publicUrl) {
                        // עדכון ממשק המשתמש בשרשור הראשי
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            tvStatus.setText("הקובץ הועלה בהצלחה");
                            Toast.makeText(FileUploadActivity.this, "הקובץ הועלה בהצלחה", Toast.LENGTH_SHORT).show();

                            // שמירת ה-URL לפיירסטור (אופציונלי)
                            saveFileUrlToFirestore(publicUrl, storagePath);

                            btnUploadFile.setEnabled(true);
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // עדכון ממשק המשתמש בשרשור הראשי
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            tvStatus.setText("שגיאה בהעלאת הקובץ: " + errorMessage);
                            Toast.makeText(FileUploadActivity.this, "שגיאה בהעלאת הקובץ", Toast.LENGTH_SHORT).show();
                            btnUploadFile.setEnabled(true);
                        });
                    }
                });
    }

    /**
     * שומר את כתובת ה-URL של הקובץ שהועלה לפיירסטור.
     * @param fileUrl כתובת ה-URL הציבורית של הקובץ.
     * @param storagePath הנתיב של הקובץ באחסון.
     */
    private void saveFileUrlToFirestore(String fileUrl, String storagePath) {
        // אופציונלי: שמירת כתובת ה-URL של הקובץ לפיירסטור לצורך עתידי
        // זה שימושי אם רוצים לעקוב אחר כל הקבצים שהועלו

        if (schoolId.isEmpty() || username.isEmpty()) {
            Log.e(TAG, "לא ניתן לשמור URL של קובץ: schoolId או username ריקים");
            return;
        }

        // יצירת אובייקט נתונים לשמירה
        java.util.Map<String, Object> fileData = new java.util.HashMap<>();
        fileData.put("url", fileUrl);
        fileData.put("path", storagePath);
        fileData.put("uploadedBy", username);
        fileData.put("uploadedAt", new java.util.Date());
        fileData.put("fileType", "image/jpeg");

        // שמירה לפיירסטור
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("schools").document(schoolId)
                .collection("uploads").add(fileData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "URL של הקובץ נשמר בפיירסטור עם מזהה: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "שגיאה בשמירת URL של הקובץ לפיירסטור", e);
                });
    }
}