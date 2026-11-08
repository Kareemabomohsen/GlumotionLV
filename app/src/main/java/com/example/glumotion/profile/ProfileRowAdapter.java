package com.example.glumotion.profile;

import com.example.glumotion.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Simple adapter for the profile lists.
 * Every row is a String array: {icon, title, subtitle, value}
 */
public class ProfileRowAdapter extends RecyclerView.Adapter<ProfileRowAdapter.RowHolder> {

    private final ArrayList<String[]> rows;

    // Constructor
    public ProfileRowAdapter(ArrayList<String[]> rows) {
        this.rows = rows;
    }

    // Creates one row from item_profile_row.xml
    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_row, parent, false);
        return new RowHolder(view);
    }

    // Fills one row
    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        String[] row = rows.get(position);
        holder.tvIcon.setText(row[0]);
        holder.tvTitle.setText(row[1]);
        holder.tvSubtitle.setText(row[2]);
        holder.tvValue.setText(row[3]);
    }

    // Number of rows
    @Override
    public int getItemCount() {
        return rows.size();
    }

    // Views of one row
    public static class RowHolder extends RecyclerView.ViewHolder {
        TextView tvIcon, tvTitle, tvSubtitle, tvValue;

        public RowHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvRowIcon);
            tvTitle = itemView.findViewById(R.id.tvRowTitle);
            tvSubtitle = itemView.findViewById(R.id.tvRowSubtitle);
            tvValue = itemView.findViewById(R.id.tvRowValue);
        }
    }
}
