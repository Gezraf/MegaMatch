package com.project.megamatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * מחלקה זו מייצגת את דף המנהל של האפליקציה.
 * היא מספקת אפשרויות ניווט למסכי ניהול שונים כמו בחירת בית ספר והתחברות מנהל.
 */
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

    /**
     * מעביר למסך בחירת בית ספר (התחברות מנהל).
     * @param view התצוגה שלחצה על האירוע.
     */
    public void moveToSchoolSelect(View view) {
        Intent intent = new Intent(adminPage.this, managerLogin.class);
        startActivity(intent);
    }

    /**
     * מעביר למסך התחברות מנהל.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void moveToAdminLogin(View view) {
        Intent intent = new Intent(adminPage.this, adminLogin.class);
        startActivity(intent);
    }

    /**
     * חוזר למסך הקודם.
     * @param view התצוגה שלחצה על האירוע.
     */
    public void goBack(View view) {
        onBackPressed();
    }
} 