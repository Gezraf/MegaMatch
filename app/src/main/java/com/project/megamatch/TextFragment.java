package com.project.megamatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * פרגמנט להצגת טקסט והמחשה גרפית במסכי עזרה או הדרכה
 */
public class TextFragment extends Fragment {

    private TextView instructionText;
    private ImageView pageIllustration;
    private int currentPage = 0;
    
    // המחשות שונות לכל עמוד
    private final int[] illustrations = {
        R.drawable.icon_white,      // המחשה לעמוד ברוך הבא
        R.drawable.ic_login_door,   // עמוד התחברות
        R.drawable.ic_notebook,     // עמוד תלמיד - שונה למחברת
        R.drawable.ic_help          // עמוד עזרה
    };

    /**
     * יוצר את תצוגת הפרגמנט
     * @param inflater מנפח התצוגה
     * @param container מיכל התצוגה
     * @param savedInstanceState מצב שמור
     * @return תצוגת הפרגמנט
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_text, container, false);
        instructionText = view.findViewById(R.id.instructionText);
        pageIllustration = view.findViewById(R.id.pageIllustration);
        return view;
    }

    /**
     * מעדכן את הטקסט המוצג בפרגמנט
     * @param text הטקסט החדש
     */
    public void updateText(String text) {
        if (instructionText != null) {
            instructionText.setText(text);
        }
    }
    
    /**
     * מעדכן את הטקסט והעמוד הנוכחי ומרענן המחשה
     * @param text הטקסט החדש
     * @param page מספר העמוד
     */
    public void updatePage(String text, int page) {
        currentPage = page;
        updateText(text);
        updateIllustration();
    }
    
    /**
     * מעדכן את ההמחשה הגרפית לפי העמוד הנוכחי
     */
    private void updateIllustration() {
        if (pageIllustration != null && currentPage < illustrations.length) {
            pageIllustration.setImageResource(illustrations[currentPage]);
        }
    }
}
