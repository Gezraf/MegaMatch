package com.project.megamatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PageFragment extends Fragment {
    private static final String ARG_TEXT = "arg_text";

    public static PageFragment newInstance(String text) {
        PageFragment fragment = new PageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TEXT, text);
        fragment.setArguments(args);
        return fragment;
    }

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
