package com.project.megamatch;

import android.app.ActivityManager;
import android.content.Context;
import androidx.multidex.MultiDexApplication;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

/**
 * מחלקת האפליקציה הראשית של MegaMatch.
 * מטפלת באתחול ובתצורה גלובלית של רכיבים כמו Firebase Firestore ו-Glide,
 * וכן באופטימיזציית זיכרון.
 */
public class MegaMatchApplication extends MultiDexApplication {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // הרשמה להתקשרויות חוזרות של זיכרון נמוך
        registerActivityLifecycleCallbacks(new MemoryOptimizedActivityCallbacks());
        
        // הגדרת Glide לאופטימיזציית זיכרון
        configureGlide();
        
        // הגדרת Firebase Firestore לתמיכה במצב לא מקוון
        configureFirestore();
    }
    
    /**
     * מגדיר את Firebase Firestore לתמיכה במצב לא מקוון.
     */
    private void configureFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
        db.setFirestoreSettings(settings);
    }
    
    /**
     * מגדיר את Glide לאופטימיזציית זיכרון.
     */
    private void configureGlide() {
        // הגדרת Glide להשתמש בפחות זיכרון
        boolean isLowMemory = isLowMemoryDevice(this);
        
        // הגדרת מטמון זיכרון קטן יותר עבור Glide
        int memoryCacheSizeBytes = isLowMemory ? 
                1024 * 1024 * 10 : // 10MB למכשירים עם זיכרון נמוך
                1024 * 1024 * 30;  // 30MB למכשירים רגילים
                
        // יישום שימוש פחות אגרסיבי בזיכרון למכשירים עם זיכרון נמוך
        if (isLowMemory) {
            Glide.get(this).clearMemory();
            RequestOptions options = new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565); // משתמש בפחות זיכרון
            
            Glide.with(this).setDefaultRequestOptions(options);
        }
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        // נקה משאבים צורכי זיכרון רבים
        Glide.get(this).clearMemory();
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        // הגב בהתאם לרמת לחץ הזיכרון
        if (level >= TRIM_MEMORY_BACKGROUND) {
            // נקה מטמון זיכרון של Glide כאשר האפליקציה עוברת לרקע
            Glide.get(this).clearMemory();
        }
        
        if (level >= TRIM_MEMORY_MODERATE || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            // נקה את כל מטמוני התמונות במצבים קריטיים
            Glide.get(this).clearMemory();
            System.gc();
        }
    }
    
    /**
     * בודק אם המכשיר הוא מכשיר עם זיכרון נמוך.
     * @param context הקונטקסט של האפליקציה.
     * @return true אם המכשיר הוא מכשיר עם זיכרון נמוך, false אחרת.
     */
    public static boolean isLowMemoryDevice(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return am.isLowRamDevice();
    }
    
    /**
     * קריאות חוזרות של מחזור חיים של פעילויות כדי לסייע בניהול זיכרון.
     */
    private static class MemoryOptimizedActivityCallbacks implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {}

        @Override
        public void onActivityStarted(android.app.Activity activity) {}

        @Override
        public void onActivityResumed(android.app.Activity activity) {}

        @Override
        public void onActivityPaused(android.app.Activity activity) {}

        @Override
        public void onActivityStopped(android.app.Activity activity) {
            // כאשר פעילות נעצרת, נקה זיכרון
            Glide.get(activity).clearMemory();
        }

        @Override
        public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {}

        @Override
        public void onActivityDestroyed(android.app.Activity activity) {
            // נקה משאבים
            System.gc();
        }
    }
} 