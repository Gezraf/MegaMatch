package com.project.megamatch.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.project.megamatch.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * מחלקת עזר לטיפול בפעולות אחסון ב-Supabase Storage.
 * מספקת פונקציונליות להעלאת תמונות לשרת האחסון של Supabase.
 */
public class SupabaseStorageUtil {
    /**
     * תגית המשמשת לרישום הודעות לוג (Logcat).
     */
    private static final String TAG = "SupabaseStorageUtil";
    
    // קבועים עבור מבנה תיקיות האחסון ב-Supabase
    /**
     * שם תיקיית השורש עבור בתי ספר ב-Supabase Storage.
     */
    private static final String SCHOOLS_FOLDER = "schools";
    /**
     * שם תיקיית המשנה עבור רכזים ב-Supabase Storage.
     */
    private static final String RAKAZIM_FOLDER = "rakazim";
    
    /**
     * מופע של OkHttpClient המשמש לביצוע בקשות HTTP.
     * הוגדר עם זמני קריאה, כתיבה וחיבור של 30 שניות.
     */
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    /**
     * מעלה תמונה ל-Supabase Storage באופן אסינכרוני.
     * יוצרת נתיב תיקייה ייחודי עבור בית הספר ושם המשתמש,
     * ומעלה את קובץ התמונה למיקום זה. תומך במנגנון נפילה (fallback) להעלאה ישירה.
     * 
     * @param context הקונטקסט של היישום, לגישה ל-ContentResolver.
     * @param imageUri URI של התמונה להעלאה.
     * @param schoolId מזהה בית הספר, המשמש כחלק מנתיב התיקייה.
     * @param username שם המשתמש, המשמש כחלק מנתיב התיקייה.
     * @param callback ממשק Callback לטיפול בתוצאת ההעלאה (הצלחה או שגיאה).
     */
    public static void uploadImage(Context context, Uri imageUri, String schoolId, 
                                  String username, UploadCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "מתחיל תהליך העלאת תמונה");
                
                // קבלת הגדרות Supabase ממשאבים
                String supabaseUrl = context.getString(R.string.supabase_url);
                String supabaseApiKey = context.getString(R.string.supabase_api_key);
                String bucketName = context.getString(R.string.supabase_bucket_name);
                
                Log.d(TAG, "משתמש בכתובת Supabase: " + supabaseUrl);
                Log.d(TAG, "משתמש בדלי: " + bucketName);
                Log.d(TAG, "מפתח API (10 תווים ראשונים): " + 
                      (supabaseApiKey.length() > 10 ? supabaseApiKey.substring(0, 10) + "..." : "מפתח לא חוקי"));
                
                // בדוק תחילה הרשאות דלי
                if (!checkBucketPermission(supabaseUrl, supabaseApiKey, bucketName)) {
                    if (callback != null) {
                        callback.onError("אין הרשאה לגשת לדלי האחסון. אנא ודא את הגדרות Supabase.");
                    }
                    return;
                }
                
                // יצירת נתיב תיקייה - ודא שהנתיב מעוצב כהלכה
                String folderPath = SCHOOLS_FOLDER + "/" + schoolId + "/" + RAKAZIM_FOLDER + "/" + username;
                
                // יצירת שם קובץ ייחודי לתמונה
                String filename = UUID.randomUUID().toString() + ".jpg";
                
                // יצירת הנתיב המלא כולל שם הקובץ
                String fullPath = folderPath + "/" + filename;
                
                // המרת Uri לקובץ
                File imageFile = uriToFile(context, imageUri);
                if (imageFile == null || !imageFile.exists()) {
                    Log.e(TAG, "כשל ביצירת קובץ תמונה מ-URI");
                    if (callback != null) {
                        callback.onError("כשל בעיבוד קובץ תמונה");
                    }
                    return;
                }
                
                Log.d(TAG, "קובץ תמונה נוצר: " + imageFile.length() + " בתים");
                
                // נסה להשתמש בנקודת הקצה של העלאה בשיטת POST במקום PUT
                String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName;
                
                // יצירת בקשה מרובת חלקים (multipart) עבור נקודת הקצה POST
                MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename,
                        RequestBody.create(imageFile, MediaType.parse("image/jpeg")))
                    .addFormDataPart("bucket", bucketName)
                    .addFormDataPart("path", folderPath);
                
                RequestBody requestBody = multipartBuilder.build();
                
                // בניית בקשה
                Request request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("apikey", supabaseApiKey)
                    .addHeader("Authorization", "Bearer " + supabaseApiKey)
                    .post(requestBody)
                    .build();
                
                Log.d(TAG, "מבצע בקשה ל- " + uploadUrl);
                
                // ביצוע הבקשה
                Response response = client.newCall(request).execute();
                
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "העלאה מוצלחת: " + responseBody);
                    
                    // יצירת URL ציבורי לתמונה
                    String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fullPath;
                    
                    Log.d(TAG, "נוצר URL ציבורי: " + publicUrl);
                    
                    // העברת התוצאה חזרה ב-main thread
                    final String finalPublicUrl = publicUrl;
                    if (callback != null) {
                        callback.onSuccess(finalPublicUrl);
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "העלאה נכשלה עם קוד " + response.code() + ": " + errorBody);
                    
                    // נסה להשתמש בנקודת קצה ישירה לתמונה כמנגנון נפילה
                    useDirectUploadFallback(context, imageUri, schoolId, username, callback, 
                                          supabaseUrl, supabaseApiKey, bucketName, filename, imageFile);
                }
                
                // ניקוי קובץ זמני
                if (imageFile != null && imageFile.exists()) {
                    if (imageFile.delete()) {
                        Log.d(TAG, "קובץ זמני נמחק בהצלחה");
                    } else {
                        Log.w(TAG, "כשל במחיקת קובץ זמני");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "שגיאה בהעלאת תמונה", e);
                if (callback != null) {
                    callback.onError("שגיאה בהעלאת תמונה: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * בודקת אם קיימת הרשאה לגשת לדלי האחסון ב-Supabase על ידי ניסיון לרשום את הדליים הקיימים.
     * @param supabaseUrl כתובת ה-URL של שירות Supabase.
     * @param apiKey מפתח ה-API של Supabase.
     * @param bucketName שם הדלי לבדיקה.
     * @return true אם קיימת הרשאה, false אחרת.
     */
    private static boolean checkBucketPermission(String supabaseUrl, String apiKey, String bucketName) {
        try {
            // ננסה לרשום דליים כדי לבדוק הרשאות
            String bucketListUrl = supabaseUrl + "/storage/v1/bucket";
            
            Request request = new Request.Builder()
                .url(bucketListUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();
                
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "רשימת דליים נשלפה בהצלחה, יש לנו הרשאות");
                return true;
            } else {
                Log.e(TAG, "כשל ברישום דליים: " + response.code());
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "פרטי שגיאה: " + errorBody);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בבדיקת הרשאות דלי", e);
            return false;
        }
    }
    
    /**
     * שיטת העלאה חלופית (fallback) המשתמשת בבקשת PUT ישירה ל-Supabase Storage.
     * מופעלת אם שיטת ה-POST הראשונית נכשלת.
     * @param context הקונטקסט של היישום.
     * @param imageUri URI של התמונה להעלאה.
     * @param schoolId מזהה בית הספר.
     * @param username שם המשתמש.
     * @param callback ממשק Callback לטיפול בתוצאה.
     * @param supabaseUrl כתובת ה-URL של Supabase.
     * @param supabaseApiKey מפתח ה-API של Supabase.
     * @param bucketName שם הדלי.
     * @param filename שם הקובץ.
     * @param imageFile אובייקט File של התמונה.
     */
    private static void useDirectUploadFallback(
            Context context, Uri imageUri, String schoolId, String username, 
            UploadCallback callback, String supabaseUrl, String supabaseApiKey, 
            String bucketName, String filename, File imageFile) {
        
        try {
            Log.d(TAG, "מנסה שיטת העלאה חלופית...");
            
            // יצירת נתיב תיקייה
            String folderPath = SCHOOLS_FOLDER + "/" + schoolId + "/" + RAKAZIM_FOLDER + "/" + username;
            String fullPath = folderPath + "/" + filename;
            
            // בניית URL ישיר ל-Supabase Storage
            String directUploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fullPath;
            
            Log.d(TAG, "URL העלאה חלופית: " + directUploadUrl);
            
            // יצירת גוף בקשה
            RequestBody requestBody = RequestBody.create(imageFile, MediaType.parse("image/jpeg"));
            
            // בניית בקשת PUT ישירה עם כותרות מינימליות
            Request request = new Request.Builder()
                    .url(directUploadUrl)
                    .addHeader("apikey", supabaseApiKey)
                    .addHeader("Authorization", "Bearer " + supabaseApiKey)
                    .put(requestBody)
                    .build();
            
            // ביצוע הבקשה
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "העלאה חלופית מוצלחת: " + responseBody);
                
                // יצירת URL ציבורי לתמונה
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fullPath;
                
                Log.d(TAG, "נוצר URL ציבורי: " + publicUrl);
                
                if (callback != null) {
                    callback.onSuccess(publicUrl);
                }
            } else {
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "העלאה חלופית נכשלה עם קוד " + response.code() + ": " + errorBody);
                
                // ספק הודעת שגיאה ידידותית למשתמש
                String errorMessage;
                switch (response.code()) {
                    case 401:
                        errorMessage = "אימות נכשל. בדוק את מפתח ה-API שלך.";
                        break;
                    case 403:
                        errorMessage = "הרשאה נדחתה. בדוק את מדיניות דלי האחסון.";
                        break;
                    case 404:
                        errorMessage = "דלי אחסון או נתיב לא נמצא.";
                        break;
                    case 413:
                        errorMessage = "קובץ התמונה גדול מדי.";
                        break;
                    default:
                        errorMessage = "העלאה נכשלה: " + response.code() + " - " + errorBody;
                }
                
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "שגיאה בהעלאה חלופית", e);
            if (callback != null) {
                callback.onError("שגיאה בהעלאת תמונה: " + e.getMessage());
            }
        }
    }
    
    /**
     * ממירה URI של תוכן לקובץ זמני.
     * @param context הקונטקסט של היישום.
     * @param uri ה-URI של התמונה.
     * @return אובייקט File המייצג את הקובץ הזמני.
     * @throws IOException אם מתרחשת שגיאת קלט/פלט במהלך ההמרה.
     */
    private static File uriToFile(Context context, Uri uri) throws IOException {
        // יצירת קובץ זמני
        File tempFile = File.createTempFile("upload", ".jpg", context.getCacheDir());
        
        // העתקת נתונים מה-URI לקובץ
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            
            if (inputStream == null) {
                throw new IOException("כשל בפתיחת זרם קלט");
            }
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
        
        return tempFile;
    }
    
    /**
     * ממשק Callback עבור פעולות העלאת תמונה.
     * משמש להחזרת תוצאות ההעלאה (URL במקרה של הצלחה, או הודעת שגיאה במקרה של כשל).
     */
    public interface UploadCallback {
        /**
         * נקרא כאשר העלאת התמונה הושלמה בהצלחה.
         * @param imageUrl ה-URL הציבורי של התמונה שהועלתה.
         */
        void onSuccess(String imageUrl);
        /**
         * נקרא כאשר העלאת התמונה נכשלה.
         * @param errorMessage הודעת שגיאה המתארת את הכשל.
         */
        void onError(String errorMessage);
    }
} 