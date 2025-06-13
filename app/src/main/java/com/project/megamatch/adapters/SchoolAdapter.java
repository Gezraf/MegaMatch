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
 * מתאם (Adapter) להצגת בתי ספר ברשימת RecyclerView
 */
public class SchoolAdapter extends RecyclerView.Adapter<SchoolAdapter.SchoolViewHolder> {

    private List<schoolsDB.School> schools;
    private final OnSchoolClickListener listener;

    /**
     * ממשק לטיפול בלחיצה על בית ספר ברשימה
     */
    public interface OnSchoolClickListener {
        void onSchoolClick(schoolsDB.School school);
    }

    /**
     * בונה מופע חדש של המתאם
     * @param schools רשימת בתי הספר להצגה
     * @param listener מאזין ללחיצות
     */
    public SchoolAdapter(List<schoolsDB.School> schools, OnSchoolClickListener listener) {
        this.schools = schools;
        this.listener = listener;
    }

    /**
     * יוצר תצוגה חדשה לכל פריט ברשימה
     * @param parent קבוצת התצוגה המכילה
     * @param viewType סוג התצוגה
     * @return מחזיק התצוגה החדש
     */
    @NonNull
    @Override
    public SchoolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_school, parent, false);
        return new SchoolViewHolder(view);
    }

    /**
     * מקשר את הנתונים לתצוגה במיקום המבוקש
     * @param holder מחזיק התצוגה
     * @param position מיקום הפריט ברשימה
     */
    @Override
    public void onBindViewHolder(@NonNull SchoolViewHolder holder, int position) {
        schoolsDB.School school = schools.get(position);
        holder.bind(school, listener);
    }

    /**
     * מחזיר את מספר הפריטים ברשימה
     * @return מספר הפריטים
     */
    @Override
    public int getItemCount() {
        return schools.size();
    }

    /**
     * מעדכן את רשימת בתי הספר ומרענן את התצוגה
     * @param newSchools רשימת בתי הספר החדשה
     */
    public void updateData(List<schoolsDB.School> newSchools) {
        this.schools = newSchools;
        notifyDataSetChanged();
    }

    /**
     * מחזיק תצוגה פנימי עבור כל בית ספר ברשימה
     */
    static class SchoolViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewSchoolName;
        private final TextView textViewSchoolDetails;

        public SchoolViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewSchoolName = itemView.findViewById(R.id.textViewSchoolName);
            textViewSchoolDetails = itemView.findViewById(R.id.textViewSchoolDetails);
        }

        /**
         * קושר את נתוני בית הספר לתצוגה ומגדיר את מאזין הלחיצה
         * @param school בית הספר להצגה
         * @param listener מאזין ללחיצה
         */
        public void bind(final schoolsDB.School school, final OnSchoolClickListener listener) {
            textViewSchoolName.setText(school.getSchoolName());
            
            // עבור schoolsDB.School המקורי, יש רק סמל מוסד ויישוב
            StringBuilder details = new StringBuilder();
            details.append("סמל מוסד: ").append(school.getSchoolId());
            
            if (school.getTown() != null && !school.getTown().isEmpty()) {
                details.append(" | יישוב: ").append(school.getTown());
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