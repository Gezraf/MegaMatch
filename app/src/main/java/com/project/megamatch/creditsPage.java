package com.project.megamatch;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

/**
 * מחלקה זו מייצגת את עמוד הקרדיטים של האפליקציה.
 * היא מכילה מידע ליצירת קשר ומאפשרת למשתמשים ליצור קשר עם המפתחים.
 */
public class creditsPage extends AppCompatActivity {
    
    // קבועים לפרטי יצירת קשר
    private static final String EMAIL_ADDRESS = "megamatch.contact@gmail.com";
    private static final String PHONE_NUMBER = "+972537253141";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.credits_page);
        
        // החלת Insets של החלון
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.creditsPage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // הגדרת כפתור יצירת קשר
        MaterialButton contactButton = findViewById(R.id.contactButton);
        contactButton.setOnClickListener(v -> showContactChooser());
        
        // הגדרת כפתור סגירה
        MaterialButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> finish());
    }

    /**
     * מציג בורר יישומים ליצירת קשר (אימייל, SMS, וואטסאפ).
     */
    private void showContactChooser() {
        // יצירת אינטנט לאפשרויות הודעות/SMS
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
        smsIntent.setData(Uri.parse("smsto:" + PHONE_NUMBER)); // הגדרת מספר הטלפון
        
        // יצירת אינטנט לוואטסאפ
        Intent whatsappIntent = new Intent(Intent.ACTION_VIEW);
        whatsappIntent.setData(Uri.parse("https://wa.me/" + PHONE_NUMBER)); // פורמט קישור אוניברסלי לוואטסאפ
        
        // יצירת אינטנט לג'ימייל
        Intent gmailIntent = new Intent(Intent.ACTION_SENDTO);
        gmailIntent.setData(Uri.parse("mailto:" + EMAIL_ADDRESS)); // הגדרת כתובת האימייל
        
        // יצירת בורר עם כל האפשרויות הזמינות
        Intent chooser = Intent.createChooser(gmailIntent, "צור קשר באמצעות...");
        
        // הוספת אינטנטים נוספים אם היישומים מותקנים
        Intent[] extraIntents = new Intent[2];
        extraIntents[0] = smsIntent;
        extraIntents[1] = whatsappIntent;
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

        // הפעלת פעילות הבורר
        startActivity(chooser);
    }

    /**
     * בודק אם יישום מותקן במכשיר.
     * @param packageName שם החבילה של היישום.
     * @return true אם היישום מותקן, false אחרת.
     */
    private boolean isAppInstalled(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
