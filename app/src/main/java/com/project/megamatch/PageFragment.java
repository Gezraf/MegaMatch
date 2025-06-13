package com.project.megamatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * מחלקה המייצגת דף פשוט בממשק המשתמש
 * מחלקה זו משמשת להצגת טקסט בדף נפרד
 */
public class PageFragment extends Fragment {
    private static final String ARG_TEXT = "arg_text";

    /**
     * יוצר מופע חדש של הדף עם הטקסט המבוקש
     * @param text הטקסט שיוצג בדף
     * @return מופע חדש של PageFragment
     */
    public static PageFragment newInstance(String text) {
        PageFragment fragment = new PageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TEXT, text);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * יוצר את תצוגת הדף ומציג את הטקסט שהועבר כפרמטר
     * @param inflater משמש ליצירת התצוגה
     * @param container מיכל התצוגה
     * @param savedInstanceState מצב שמור של הדף
     * @return תצוגת הדף שנוצרה
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(android.R.layout.simple_list_item_1, container, false);
        TextView textView = view.findViewById(android.R.id.text1);

        if (getArguments() != null) {
            textView.setText(getArguments().getString(ARG_TEXT, ""));
        }

        return view;
    }
}
