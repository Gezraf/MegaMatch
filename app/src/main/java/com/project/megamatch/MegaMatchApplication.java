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

public class MegaMatchApplication extends MultiDexApplication {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Register for low memory callbacks
        registerActivityLifecycleCallbacks(new MemoryOptimizedActivityCallbacks());
        
        // Configure Glide for memory optimization
        configureGlide();
        
        // Configure Firebase Firestore for offline support
        configureFirestore();
    }
    
    /**
     * Configure Firebase Firestore for offline support
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
     * Configure Glide for memory optimization
     */
    private void configureGlide() {
        // Configure Glide to use less memory
        boolean isLowMemory = isLowMemoryDevice(this);
        
        // Set a smaller memory cache for Glide
        int memoryCacheSizeBytes = isLowMemory ? 
                1024 * 1024 * 10 : // 10MB for low memory devices
                1024 * 1024 * 30;  // 30MB for normal devices
                
        // Apply less aggressive memory usage for low memory devices
        if (isLowMemory) {
            Glide.get(this).clearMemory();
            RequestOptions options = new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565); // Uses less memory
            
            Glide.with(this).setDefaultRequestOptions(options);
        }
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        // Clear memory-heavy resources
        Glide.get(this).clearMemory();
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        // React based on memory pressure level
        if (level >= TRIM_MEMORY_BACKGROUND) {
            // Clear Glide memory cache when app goes to background
            Glide.get(this).clearMemory();
        }
        
        if (level >= TRIM_MEMORY_MODERATE || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            // Clear all image caches in critical situations
            Glide.get(this).clearMemory();
            System.gc();
        }
    }
    
    public static boolean isLowMemoryDevice(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return am.isLowRamDevice();
    }
    
    /**
     * Activity lifecycle callbacks to help manage memory
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
            // When activity is stopped, trim memory
            Glide.get(activity).clearMemory();
        }

        @Override
        public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {}

        @Override
        public void onActivityDestroyed(android.app.Activity activity) {
            // Clean up resources
            System.gc();
        }
    }
} 