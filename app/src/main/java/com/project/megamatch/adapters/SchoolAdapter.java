package com.project.megamatch.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.project.megamatch.R;
import com.project.megamatch.schoolsDB;

import java.util.List;

/**
 * Adapter for displaying schools in a RecyclerView
 */
public class SchoolAdapter extends RecyclerView.Adapter<SchoolAdapter.SchoolViewHolder> {

    private List<schoolsDB.School> schools;
    private final OnSchoolClickListener listener;

    public interface OnSchoolClickListener {
        void onSchoolClick(schoolsDB.School school);
    }

    public SchoolAdapter(List<schoolsDB.School> schools, OnSchoolClickListener listener) {
        this.schools = schools;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SchoolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_school, parent, false);
        return new SchoolViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SchoolViewHolder holder, int position) {
        schoolsDB.School school = schools.get(position);
        holder.bind(school, listener);
    }

    @Override
    public int getItemCount() {
        return schools.size();
    }

    public void updateData(List<schoolsDB.School> newSchools) {
        this.schools = newSchools;
        notifyDataSetChanged();
    }

    static class SchoolViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewSchoolName;
        private final TextView textViewSchoolDetails;

        public SchoolViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewSchoolName = itemView.findViewById(R.id.textViewSchoolName);
            textViewSchoolDetails = itemView.findViewById(R.id.textViewSchoolDetails);
        }

        public void bind(final schoolsDB.School school, final OnSchoolClickListener listener) {
            textViewSchoolName.setText(school.getSchoolName());
            
            // For the original schoolsDB.School, we only have school ID and principal info
            StringBuilder details = new StringBuilder();
            details.append("סמל מוסד: ").append(school.getSchoolId());
            
            if (school.getPrincipalName() != null && !school.getPrincipalName().isEmpty()) {
                details.append(" | מנהל/ת: ").append(school.getPrincipalName());
            }
            
            textViewSchoolDetails.setText(details.toString());
            textViewSchoolDetails.setVisibility(View.VISIBLE);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSchoolClick(school);
                }
            });
        }
    }
} 