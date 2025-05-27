package com.project.megamatch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RakazEmailsAdapter extends RecyclerView.Adapter<RakazEmailsAdapter.ViewHolder> {

    private List<AdminRakazEmailsActivity.RakazEmailModel> emails;
    private OnEmailClickListener listener;

    public interface OnEmailClickListener {
        void onEmailClick(AdminRakazEmailsActivity.RakazEmailModel email, int position);
    }

    public RakazEmailsAdapter(List<AdminRakazEmailsActivity.RakazEmailModel> emails, OnEmailClickListener listener) {
        this.emails = emails;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rakaz_email, parent, false);
        return new ViewHolder(view);
    }

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

    @Override
    public int getItemCount() {
        return emails.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView emailText, nameText, statusText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            emailText = itemView.findViewById(R.id.emailText);
            nameText = itemView.findViewById(R.id.nameText);
            statusText = itemView.findViewById(R.id.statusText);
        }
    }
} 