package com.example.glumotion.medications;

import com.example.glumotion.R;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.MedViewHolder> {

    private Context context;
    private List<MedicationModel> medicationList;
    private OnStatusClickListener listener;

    // The screen that uses the adapter implements this interface to know about clicks
    public interface OnStatusClickListener {
        void OnStatusClick(MedicationModel medication, int position);

        void OnLongClick(MedicationModel medication);
    }

    // Constructor
    public MedicationAdapter(Context context, List<MedicationModel> medicationList, OnStatusClickListener listener) {
        this.context = context;
        this.medicationList = medicationList;
        this.listener = listener;
    }

    // Creates one row from item_medication.xml
    @NonNull
    @Override
    public MedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_medication, parent, false);
        return new MedViewHolder(view);
    }

    // Fills one row with the data of one medication
    @Override
    public void onBindViewHolder(@NonNull final MedViewHolder holder, int position) {
        final MedicationModel med = medicationList.get(position);

        holder.tvMedName.setText(med.getDisplayName());
        holder.tvMedTimeInstruction.setText(med.getTime());

        String frequency = med.getFrequency();
        if (frequency == null) {
            frequency = "Daily";
        }
        String instruction = med.getInstruction();
        if (instruction == null) {
            instruction = "";
        }
        holder.tvMedFrequency.setText(frequency + " · " + instruction);

        String status = med.getTodayStatus();

        if (status.equalsIgnoreCase("Taken")) {
            holder.tvMedStatus.setText("✓ Taken");
            holder.tvMedStatus.setTextColor(Color.parseColor("#27A765"));
        } else if (status.equalsIgnoreCase("Skipped")) {
            holder.tvMedStatus.setText("✕ Skipped");
            holder.tvMedStatus.setTextColor(Color.parseColor("#F04438"));
        } else {
            holder.tvMedStatus.setText("◯ Pending");
            holder.tvMedStatus.setTextColor(Color.parseColor("#667085"));
        }

        // BUG FIX: read the position when clicked (the old "position" can be wrong after changes)
        holder.tvMedStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.OnStatusClick(medicationList.get(pos), pos);
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (listener != null) {
                    listener.OnLongClick(med);
                }
                return true;
            }
        });
    }

    // Number of rows
    @Override
    public int getItemCount() {
        return medicationList.size();
    }

    // Holds the views of one row
    public static class MedViewHolder extends RecyclerView.ViewHolder {
        TextView tvMedName, tvMedTimeInstruction, tvMedFrequency, tvMedStatus;

        public MedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMedName = itemView.findViewById(R.id.tvMedName);
            tvMedTimeInstruction = itemView.findViewById(R.id.tvMedTimeInstruction);
            tvMedFrequency = itemView.findViewById(R.id.tvMedFrequency);
            tvMedStatus = itemView.findViewById(R.id.tvMedStatus);
        }
    }
}
