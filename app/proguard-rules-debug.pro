# Debug-specific ProGuard rules
# Less aggressive than release rules to improve build speed

# Keep source file names and line numbers for better debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all application classes for easier debugging
-keep class com.project.megamatch.** { *; }

# Firebase rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Glide rules
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-dontwarn com.bumptech.glide.**

# GIF Drawable library
-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**

# Credentials library
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

# Don't optimize or obfuscate in debug - just verify
-dontoptimize
-dontobfuscate 