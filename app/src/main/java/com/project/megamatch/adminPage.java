package com.project.megamatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class adminPage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.admin_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.adminPageLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public void moveToSchoolSelect(View view) {
        Intent intent = new Intent(adminPage.this, schoolSelect.class);
        // Set flag to indicate this is an admin login
        intent.putExtra("isAdminLogin", true);
        startActivity(intent);
    }

    public void moveToAdminLogin(View view) {
        Intent intent = new Intent(adminPage.this, adminLogin.class);
        startActivity(intent);
    }

    public void goBack(View view) {
        onBackPressed();
    }
} 