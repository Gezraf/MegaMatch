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

public class creditsPage extends AppCompatActivity {
    
    // Contact information constants
    private static final String EMAIL_ADDRESS = "megamatch.contact@gmail.com";
    private static final String PHONE_NUMBER = "+972537253141";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.credits_page);
        
        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.creditsPage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Setup contact button
        MaterialButton contactButton = findViewById(R.id.contactButton);
        contactButton.setOnClickListener(v -> showContactChooser());
        
        // Setup close button
        MaterialButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> finish());
    }

    private void showContactChooser() {
        // Create intent for messaging/SMS options
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
        smsIntent.setData(Uri.parse("smsto:" + PHONE_NUMBER)); // Set the phone number
        
        // Create intent for WhatsApp
        Intent whatsappIntent = new Intent(Intent.ACTION_VIEW);
        whatsappIntent.setData(Uri.parse("https://wa.me/" + PHONE_NUMBER)); // WhatsApp universal link format
        
        // Create intent for Gmail
        Intent gmailIntent = new Intent(Intent.ACTION_SENDTO);
        gmailIntent.setData(Uri.parse("mailto:" + EMAIL_ADDRESS)); // Set the email address
        
        // Create a chooser with all available options
        Intent chooser = Intent.createChooser(gmailIntent, "צור קשר באמצעות..."); // "Contact via..."
        
        // Add other intents if apps are installed
        Intent[] extraIntents = new Intent[2];
        extraIntents[0] = smsIntent;
        extraIntents[1] = whatsappIntent;
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

        // Start the chooser activity
        startActivity(chooser);
    }

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
