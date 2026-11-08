package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.common.FRef;
import com.example.glumotion.medications.MedicationModel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PQ3 extends Fragment {

    Spinner diabetesTypeSpinner, monitoringMethodSpinner, timingSpinner;
    EditText medicationNameEt, dosageEt;
    Button addMedicationBtn, backBtn, continueBtn;
    TextView tvMedsListTitle, tvNoMeds;
    LinearLayout medicationsListContainer;

    // BUG FIX: ALL the medications the user adds are kept in this list (before only the last one was saved)
    private final ArrayList<MedicationModel> medications = new ArrayList<>();

    // Medications that were already in Firebase and the user removed them now
    private final ArrayList<String> removedIds = new ArrayList<>();

    private String goal = "Bulking";

    private final String[] diabetesTypes = {"Type 1", "Type 2", "Prediabetes", "Gestational", "None"};
    private final String[] monitoringMethods = {"Traditional finger-prick", "Continuous Glucose Monitor (CGM)"};

    // Inflates the layout and prepares everything
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_p_q3, container, false);

        if (getArguments() != null && getArguments().getString(PQ1.ARG_GOAL) != null) {
            goal = getArguments().getString(PQ1.ARG_GOAL);
        }

        initViews(view);
        setupSpinners();
        setupListeners();
        showMedicationsList();
        loadSavedData();
        return view;
    }

    // Finds all the views
    private void initViews(View view) {
        diabetesTypeSpinner = view.findViewById(R.id.diabetesTypeSpinner);
        monitoringMethodSpinner = view.findViewById(R.id.monitoringMethodSpinner);
        timingSpinner = view.findViewById(R.id.timingSpinner);

        medicationNameEt = view.findViewById(R.id.medicationNameEt);
        dosageEt = view.findViewById(R.id.dosageEt);

        addMedicationBtn = view.findViewById(R.id.addMedicationBtn);
        backBtn = view.findViewById(R.id.backBtn);
        continueBtn = view.findViewById(R.id.continueBtn);

        tvMedsListTitle = view.findViewById(R.id.tvMedsListTitle);
        tvNoMeds = view.findViewById(R.id.tvNoMeds);
        medicationsListContainer = view.findViewById(R.id.medicationsListContainer);
    }

    // Fills the 3 spinners
    private void setupSpinners() {
        diabetesTypeSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, diabetesTypes));
        monitoringMethodSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, monitoringMethods));
        // Same timing options as the medications screen (Morning / Afternoon / Evening / Bedtime)
        timingSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, MedicationModel.TIMING_LABELS));
    }

    // Add / Back / Continue buttons
    private void setupListeners() {
        addMedicationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addMedicationToList(true);
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getParentFragmentManager().popBackStack();
            }
        });

        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If he typed a medication and forgot to press "Add" - add it for him
                if (!medicationNameEt.getText().toString().trim().isEmpty()) {
                    addMedicationToList(false);
                }
                saveAllToFirebase();
            }
        });
    }

    // Reads the fields, creates a MedicationModel and ADDS it to the list (doesn't replace)
    private void addMedicationToList(boolean showErrors) {
        String medName = medicationNameEt.getText().toString().trim();
        String dosage = dosageEt.getText().toString().trim();

        if (medName.isEmpty()) {
            if (showErrors) {
                medicationNameEt.setError("Enter medication name");
            }
            return;
        }

        // Don't add the same medication twice at the same timing
        int timingIndex = timingSpinner.getSelectedItemPosition();
        for (int i = 0; i < medications.size(); i++) {
            MedicationModel m = medications.get(i);
            if (m.getName().equalsIgnoreCase(medName)
                    && MedicationModel.TIMING_LABELS[timingIndex].equals(m.getInstruction())) {
                Toast.makeText(getContext(), medName + " is already in the list", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        MedicationModel med = MedicationModel.create(medName, dosage, timingIndex);
        medications.add(med);
        showMedicationsList();

        medicationNameEt.setText("");
        dosageEt.setText("");
        medicationNameEt.requestFocus();
        Toast.makeText(getContext(), medName + " added (" + medications.size() + " total)", Toast.LENGTH_SHORT).show();
    }

    // Draws all the medications of the list on the screen
    private void showMedicationsList() {
        medicationsListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (int i = 0; i < medications.size(); i++) {
            final MedicationModel med = medications.get(i);
            View row = inflater.inflate(R.layout.item_pending_med, medicationsListContainer, false);

            TextView tvName = row.findViewById(R.id.tvPendingMedName);
            TextView tvTiming = row.findViewById(R.id.tvPendingMedTiming);
            View btnRemove = row.findViewById(R.id.btnRemovePendingMed);

            tvName.setText(med.getDisplayName());
            tvTiming.setText(med.getInstruction() + " • " + med.getTime());

            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeMedication(med);
                }
            });
            medicationsListContainer.addView(row);
        }

        tvMedsListTitle.setText("Your medications (" + medications.size() + ")");
        if (medications.isEmpty()) {
            tvNoMeds.setVisibility(View.VISIBLE);
        } else {
            tvNoMeds.setVisibility(View.GONE);
        }
    }

    // Removes one medication from the list (and later from Firebase if it was saved before)
    private void removeMedication(MedicationModel med) {
        medications.remove(med);
        if (med.getId() != null) {
            removedIds.add(med.getId());
        }
        showMedicationsList();
    }

    // When editing the profile again: load the saved diabetes info + medications
    private void loadSavedData() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    return;
                }
                String type = snapshot.child("HealthConditions").child("diabetesType").getValue(String.class);
                String method = snapshot.child("HealthConditions").child("monitoringMethod").getValue(String.class);
                selectSpinnerValue(diabetesTypeSpinner, diabetesTypes, type);
                selectSpinnerValue(monitoringMethodSpinner, monitoringMethods, method);

                for (DataSnapshot medSnap : snapshot.child("Medications").getChildren()) {
                    MedicationModel med = medSnap.getValue(MedicationModel.class);
                    String key = medSnap.getKey();
                    // Skip it if it's already in the list or the user removed it
                    if (med != null && med.getName() != null && !isInList(key) && !removedIds.contains(key)) {
                        med.setId(key);
                        medications.add(med);
                    }
                }
                showMedicationsList();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Helper: true if a medication with this id is already in the list
    private boolean isInList(String id) {
        for (int i = 0; i < medications.size(); i++) {
            if (id != null && id.equals(medications.get(i).getId())) {
                return true;
            }
        }
        return false;
    }

    // Helper: selects a value inside a spinner
    private void selectSpinnerValue(Spinner spinner, String[] values, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    /**
     * Saves the health conditions AND all the medications in ONE request.
     * All medications go to the same place: Users / uid / Medications / medId
     */
    private void saveAllToFirebase() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        DatabaseReference medsRef = FRef.getRefMedications();
        if (userRef == null || medsRef == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> healthData = new HashMap<>();
        healthData.put("diabetesType", diabetesTypeSpinner.getSelectedItem().toString());
        healthData.put("monitoringMethod", monitoringMethodSpinner.getSelectedItem().toString());
        healthData.put("medicationsCount", medications.size());

        // "path" → value. One updateChildren = everything is saved together
        Map<String, Object> updates = new HashMap<>();
        updates.put("HealthConditions", healthData);

        for (int i = 0; i < medications.size(); i++) {
            MedicationModel med = medications.get(i);
            if (med.getId() == null) {
                // New medication → new unique key, so it never overwrites another one
                med.setId(medsRef.push().getKey());
            }
            updates.put("Medications/" + med.getId(), med.toMap());
        }
        for (int i = 0; i < removedIds.size(); i++) {
            updates.put("Medications/" + removedIds.get(i), null);
        }

        continueBtn.setEnabled(false);
        userRef.updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (isAdded()) {
                            continueBtn.setEnabled(true);
                            removedIds.clear();
                            Toast.makeText(getContext(), "Saved " + medications.size() + " medications", Toast.LENGTH_SHORT).show();
                            navigateToNextFragment();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (isAdded()) {
                            continueBtn.setEnabled(true);
                            Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // BUG FIX: opens PQ4 inside fragment_container (before it was android.R.id.content
    // so the pages were drawn on top of each other and the buttons of the old page got the clicks)
    private void navigateToNextFragment() {
        PQ4 pq4 = new PQ4();
        Bundle args = new Bundle();
        args.putString(PQ1.ARG_GOAL, goal);
        pq4.setArguments(args);

        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, pq4);
        transaction.addToBackStack(null);
        transaction.commit();
    }
}
