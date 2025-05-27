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

public class TextFragment extends Fragment {

    private TextView instructionText;
    private ImageView pageIllustration;
    private int currentPage = 0;
    
    // Different illustrations for each page
    private final int[] illustrations = {
        R.drawable.icon_white,      // Welcome illustration
        R.drawable.ic_login_door,   // Login page
        R.drawable.ic_notebook,     // Student page - changed to notebook
        R.drawable.ic_help          // Help page
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_text, container, false);
        instructionText = view.findViewById(R.id.instructionText);
        pageIllustration = view.findViewById(R.id.pageIllustration);
        return view;
    }

    public void updateText(String text) {
        if (instructionText != null) {
            instructionText.setText(text);
        }
    }
    
    public void updatePage(String text, int page) {
        currentPage = page;
        updateText(text);
        updateIllustration();
    }
    
    private void updateIllustration() {
        if (pageIllustration != null && currentPage < illustrations.length) {
            pageIllustration.setImageResource(illustrations[currentPage]);
        }
    }
}
