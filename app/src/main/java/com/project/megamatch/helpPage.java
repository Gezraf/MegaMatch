package com.project.megamatch;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;

public class helpPage extends AppCompatActivity {

    private MaterialButton prevPageButton, nextPageButton, closeButton;
    private TextView pageNumberText;
    private TextFragment textFragment;
    private int currentPage = 0;
    private final int TOTAL_PAGES = 4;

    // Instructions to display (Hebrew version)
    private final String[] instructions = {
            "ברוך הבא ל-MegaMatch! מדריך זה יעזור לך לנווט באפליקציה.",
            "תלמידים יכולים להתחבר באמצעות תעודת מזהה ולפי סיסמה שנקבעה להם על ידי בית הספר.\n\n" +
                    "רכזים יכולים להתחבר באמצעות שם משתמש וסיסמה שנקבעים על ידי המערכת וניתנים לשינוי לאחר ההתחברות הראשונה.",
            "לאחר ההתחברות:\nתלמידים יכולים לצפות ברשימת המגמות הבית-ספרית שמצטברת בהתאם ליצירת המגמות על ידי רכזי המקצוע השונים בבית הספר.\n\n" +
                    "רכזים יכולים להוסיף מגמות ולנהל אותן בקלות דרך רשימת המגמות הבית-ספרית.",
            "אם אתם נתקלים בבעיות, פנו לרכז בית הספר שלכם לקבלת עזרה \uD83D\uDE03"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.help_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.helpPage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI components
        prevPageButton = findViewById(R.id.prevPageButton);
        nextPageButton = findViewById(R.id.nextPageButton);
        closeButton = findViewById(R.id.closeButton);
        pageNumberText = findViewById(R.id.pageNumberText);

        // Initialize and add the TextFragment
        textFragment = new TextFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.helpFragmentContainer, textFragment);
        fragmentTransaction.commit();

        // Display the first instruction and page number
        updateUI();
        updateNavigationButtons();

        // Handle navigation
        prevPageButton.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                updateUI();
                updateNavigationButtons();
            }
        });

        nextPageButton.setOnClickListener(v -> {
            if (currentPage < instructions.length - 1) {
                currentPage++;
                updateUI();
                updateNavigationButtons();
            }
        });
        
        // Close button returns to previous screen
        closeButton.setOnClickListener(v -> finish());
    }

    private void updateUI() {
        // Update fragment with text and current page
        textFragment.updatePage(instructions[currentPage], currentPage);
        // Display page number as "1 מתוך 4" (1 of 4)
        pageNumberText.setText((currentPage + 1) + " מתוך " + TOTAL_PAGES);
    }
    
    private void updateNavigationButtons() {
        // Disable prev button on first page
        prevPageButton.setEnabled(currentPage > 0);
        prevPageButton.setAlpha(currentPage > 0 ? 1.0f : 0.5f);
        
        // Disable next button on last page
        nextPageButton.setEnabled(currentPage < instructions.length - 1);
        nextPageButton.setAlpha(currentPage < instructions.length - 1 ? 1.0f : 0.5f);
    }
}
