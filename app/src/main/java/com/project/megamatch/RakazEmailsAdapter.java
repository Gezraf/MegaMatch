package com.project.megamatch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * מתאם (Adapter) להצגת רשימת כתובות אימייל של רכזים
 * מחלקה זו מנהלת את הצגת הנתונים ברשימת הרכזים ומטפלת באירועי לחיצה
 */
public class RakazEmailsAdapter extends RecyclerView.Adapter<RakazEmailsAdapter.ViewHolder> {

    private List<AdminRakazEmailsActivity.RakazEmailModel> emails;
    private OnEmailClickListener listener;

    /**
     * ממשק לטיפול באירועי לחיצה על אימייל ברשימה
     */
    public interface OnEmailClickListener {
        /**
         * נקרא כאשר המשתמש לוחץ על אימייל ברשימה
         * @param email מודל האימייל שנלחץ
         * @param position מיקום האימייל ברשימה
         */
        void onEmailClick(AdminRakazEmailsActivity.RakazEmailModel email, int position);
    }

    /**
     * בונה מופע חדש של המתאם
     * @param emails רשימת האימיילים להצגה
     * @param listener מאזין לטיפול באירועי לחיצה
     */
    public RakazEmailsAdapter(List<AdminRakazEmailsActivity.RakazEmailModel> emails, OnEmailClickListener listener) {
        this.emails = emails;
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rakaz_email, parent, false);
        return new ViewHolder(view);
    }

    /**
     * מקשר את הנתונים לתצוגה במיקום המבוקש
     * @param holder מחזיק התצוגה
     * @param position מיקום הפריט ברשימה
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminRakazEmailsActivity.RakazEmailModel email = emails.get(position);
        
        holder.emailText.setText(email.getEmail());
        holder.nameText.setText(email.getFirstName() + " " + email.getLastName());
        
        if (email.isRegistered()) {
            holder.statusText.setText("נרשם");
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.green_700));
        } else if (email.isApproved()) {
            holder.statusText.setText("מאושר");
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.blue_700));
        } else {
            holder.statusText.setText("לא מאושר");
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.red_700));
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEmailClick(email, holder.getAdapterPosition());
            }
        });
    }

    /**
     * מחזיר את מספר הפריטים ברשימה
     * @return מספר הפריטים
     */
    @Override
    public int getItemCount() {
        return emails.size();
    }

    /**
     * מחלקה פנימית המחזיקה את התצוגות של כל פריט ברשימה
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView emailText, nameText, statusText;

        /**
         * בונה מופע חדש של מחזיק התצוגה
         * @param itemView תצוגת הפריט
         */
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            emailText = itemView.findViewById(R.id.emailText);
            nameText = itemView.findViewById(R.id.nameText);
            statusText = itemView.findViewById(R.id.statusText);
        }
    }
} 