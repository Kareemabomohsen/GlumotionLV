package com.example.glumotion.medications;

import com.example.glumotion.R;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MedicationsFragment extends Fragment {

    private RecyclerView rvMedications;
    private MedicationAdapter adapter;
    private final List<MedicationModel> allMedicationsList = new ArrayList<>();
    private final List<MedicationModel> filteredList = new ArrayList<>();

    private TextView tabMorning, tabAfternoon, tabEvening, tabBedtime, tvCategoryHeader, tvTimeRange, tvEmptyMeds;
    private Button btnAddNewMed;

    private String selectedCategory = "Morning";

    // Firebase listener (kept so we can remove it when the screen closes)
    private DatabaseReference medsRef;
    private ValueEventListener medsListener;

    public MedicationsFragment() {
        // Required empty public constructor
    }

    // Inflates the medications layout
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_medications, container, false);
    }

    // Prepares list, tabs, buttons and starts listening to Firebase
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupRecyclerView();
        setupTabs();
        setupListeners();
        selectTabByCurrentTime();
        loadMedicationsFromFirebase();
    }

    // Stops listening to Firebase when the view is destroyed (no double listeners)
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (medsRef != null && medsListener != null) {
            medsRef.removeEventListener(medsListener);
        }
    }

    // Finds all the views
    private void initViews(View view) {
        rvMedications = view.findViewById(R.id.rvMedications);
        tabMorning = view.findViewById(R.id.tabMorning);
        tabAfternoon = view.findViewById(R.id.tabAfternoon);
        tabEvening = view.findViewById(R.id.tabEvening);
        tabBedtime = view.findViewById(R.id.tabBedtime);
        tvCategoryHeader = view.findViewById(R.id.tvCategoryHeader);
        tvTimeRange = view.findViewById(R.id.tvTimeRange);
        tvEmptyMeds = view.findViewById(R.id.tvEmptyMeds);
        btnAddNewMed = view.findViewById(R.id.btnAddNewMed);
    }

    // RecyclerView + adapter with click on status and long click to delete
    private void setupRecyclerView() {
        rvMedications.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MedicationAdapter(getContext(), filteredList, new MedicationAdapter.OnStatusClickListener() {
            @Override
            public void OnStatusClick(MedicationModel medication, int position) {
                String newStatus = nextStatus(medication.getTodayStatus());
                medication.setStatus(newStatus);
                medication.setStatusDate(DateUtil.todayKey());
                adapter.notifyItemChanged(position);
                FRef.updateMedicationStatus(medication.getId(), newStatus);
            }

            @Override
            public void OnLongClick(MedicationModel medication) {
                confirmDelete(medication);
            }
        });
        rvMedications.setAdapter(adapter);
    }

    // Pending → Taken → Skipped → Pending
    private String nextStatus(String status) {
        if ("Taken".equalsIgnoreCase(status)) {
            return "Skipped";
        }
        if ("Skipped".equalsIgnoreCase(status)) {
            return "Pending";
        }
        return "Taken";
    }

    // AlertDialog: asks before deleting a medication
    private void confirmDelete(final MedicationModel medication) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete medication")
                .setMessage("Delete " + medication.getDisplayName() + "?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DatabaseReference ref = FRef.getRefMedications();
                        if (ref != null && medication.getId() != null) {
                            ref.child(medication.getId()).removeValue();
                            Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // The 4 time tabs
    private void setupTabs() {
        tabMorning.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab("Morning");
            }
        });
        tabAfternoon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab("Afternoon");
            }
        });
        tabEvening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab("Evening");
            }
        });
        tabBedtime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab("Bedtime");
            }
        });
    }

    // Opens the tab that matches the hour right now
    private void selectTabByCurrentTime() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 6 && hour < 12) {
            selectTab("Morning");
        } else if (hour >= 12 && hour < 17) {
            selectTab("Afternoon");
        } else if (hour >= 17 && hour < 21) {
            selectTab("Evening");
        } else {
            selectTab("Bedtime");
        }
    }

    // "+ Add New" opens the add screen inside the same container
    private void setupListeners() {
        btnAddNewMed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.medications_container, new AddMedicationFragment());
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });
    }

    // Changes the selected tab, the header and filters the list
    private void selectTab(String category) {
        selectedCategory = category;
        tvCategoryHeader.setText("⚙ " + category);

        if (category.equals("Morning")) {
            tvTimeRange.setText("6:00 AM – 12:00 PM");
        } else if (category.equals("Afternoon")) {
            tvTimeRange.setText("12:00 PM – 5:00 PM");
        } else if (category.equals("Evening")) {
            tvTimeRange.setText("5:00 PM – 9:00 PM");
        } else {
            tvTimeRange.setText("9:00 PM – 12:00 AM");
        }

        int grey = Color.parseColor("#667085");
        int green = Color.parseColor("#27A765");
        tabMorning.setTextColor(grey);
        tabAfternoon.setTextColor(grey);
        tabEvening.setTextColor(grey);
        tabBedtime.setTextColor(grey);

        if (category.equals("Morning")) {
            tabMorning.setTextColor(green);
        } else if (category.equals("Afternoon")) {
            tabAfternoon.setTextColor(green);
        } else if (category.equals("Evening")) {
            tabEvening.setTextColor(green);
        } else {
            tabBedtime.setTextColor(green);
        }

        filterMedications();
    }

    // Listens to Users / uid / Medications (the same place PQ3 and the add screen write to)
    private void loadMedicationsFromFirebase() {
        medsRef = FRef.getRefMedications();
        if (medsRef == null) {
            return;
        }
        medsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allMedicationsList.clear();
                for (DataSnapshot medSnapshot : snapshot.getChildren()) {
                    MedicationModel med = medSnapshot.getValue(MedicationModel.class);
                    if (med != null) {
                        med.setId(medSnapshot.getKey());
                        allMedicationsList.add(med);
                    }
                }
                filterMedications();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Load failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        };
        medsRef.addValueEventListener(medsListener);
    }

    // Keeps only the medications of the selected time
    private void filterMedications() {
        filteredList.clear();
        for (int i = 0; i < allMedicationsList.size(); i++) {
            MedicationModel med = allMedicationsList.get(i);
            if (med.getTimingCategory() != null && med.getTimingCategory().equalsIgnoreCase(selectedCategory)) {
                filteredList.add(med);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (tvEmptyMeds != null) {
            if (filteredList.isEmpty()) {
                tvEmptyMeds.setVisibility(View.VISIBLE);
            } else {
                tvEmptyMeds.setVisibility(View.GONE);
            }
        }
    }
}
