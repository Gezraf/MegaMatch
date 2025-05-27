package com.project.megamatch;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.view.MotionEvent;
import android.view.View;

import android.widget.ImageView;
import android.content.Intent;
import android.Manifest;
import android.widget.Button;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;

public class loginPage extends AppCompatActivity {
    private static final String TAG = "LoginPage";
    private ImageView image1;
    private Button adminButton;
    private final long ADMIN_BUTTON_HOLD_TIME = 1000; // 1 second in milliseconds
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable adminButtonRunnable;
    private boolean isButtonHeld = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check for existing admin login
        SharedPreferences sharedPreferences = getSharedPreferences("MegaMatchPrefs", MODE_PRIVATE);
        String savedAdminUsername = sharedPreferences.getString("loggedInAdmin", "");
        
        if (!savedAdminUsername.isEmpty()) {
            Log.d(TAG, "Found saved admin session, redirecting to AdminLoadingActivity");
            Intent intent = new Intent(loginPage.this, AdminLoadingActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.login_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginPage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (!checkPermission(Manifest.permission.SEND_SMS)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, 1);
        }
        
        // Initialize admin button
        adminButton = findViewById(R.id.adminButton);
        
        // Set up admin button touch listener
        adminButtonRunnable = () -> {
            if (isButtonHeld) {
                // Show a brief confirmation and navigate to admin page
                adminButton.setAlpha(1.0f);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(loginPage.this, adminPage.class);
                    startActivity(intent);
                }, 300);
            }
        };
        
        adminButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isButtonHeld = true;
                    handler.postDelayed(adminButtonRunnable, ADMIN_BUTTON_HOLD_TIME);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isButtonHeld = false;
                    handler.removeCallbacks(adminButtonRunnable);
                    return true;
            }
            return false;
        });
    }


    public void moveToSchoolSelect(View view)
    {
        Intent intent = new Intent(loginPage.this, schoolSelect.class);
        startActivity(intent);
    }

    public void moveToRakazLogin(View view)
    {
        Intent i1 = new Intent(this, rakazLogin.class);
        startActivity(i1);
    }


    public void moveToHelp(View view)
    {
        Intent i1 = new Intent(this, helpPage.class);
        startActivity(i1);
    }

    public void moveToCredits(View view)
    {
        Intent i1 = new Intent(this, creditsPage.class);
        startActivity(i1);
    }


    public boolean checkPermission(String permission)
    {
        int check = ContextCompat.checkSelfPermission(this, permission);
        return (check == PackageManager.PERMISSION_GRANTED);
    }
}