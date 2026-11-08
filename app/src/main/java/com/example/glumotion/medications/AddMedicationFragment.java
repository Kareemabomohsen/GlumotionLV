package com.example.glumotion.medications;

import com.example.glumotion.R;
import com.example.glumotion.common.FRef;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

public class AddMedicationFragment extends Fragment {

    private EditText etMedicationName, etMedicationDosage;
    private RadioGroup rgTimeCategory;
    private Button btnSaveMedication, btnBackToDashboard;

    public AddMedicationFragment() {
        // Required empty public constructor
    }

    // Inflates the add medication layout
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_medication, container, false);
    }

    // Finds the views and connects the buttons
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupListeners();
    }

    // Finds all the views
    private void initViews(View view) {
        etMedicationName = view.findViewById(R.id.etMedicationName);
        etMedicationDosage = view.findViewById(R.id.etMedicationDosage);
        rgTimeCategory = view.findViewById(R.id.rgTimeCategory);
        btnSaveMedication = view.findViewById(R.id.btnSaveMedication);
        btnBackToDashboard = view.findViewById(R.id.btnBackToDashboard);
    }

    // Save / Back buttons
    private void setupListeners() {
        btnSaveMedication.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveMedicationToFirebase();
            }
        });
        btnBackToDashboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
    }

    // Goes back to the list (or closes the Activity if there is nothing behind)
    private void goBack() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().finish();
        }
    }

    // Builds the medication and saves it with FRef.saveMedication (same place as the questions screen)
    private void saveMedicationToFirebase() {
        String medName = etMedicationName.getText().toString().trim();
        String dosage = etMedicationDosage.getText().toString().trim();

        if (medName.isEmpty()) {
            etMedicationName.setError("Please enter medication name");
            return;
        }

        String category = "Morning";
        String time = "8:00 AM";
        int selectedId = rgTimeCategory.getCheckedRadioButtonId();
        if (selectedId == R.id.rbAfternoon) {
            category = "Afternoon";
            time = "2:00 PM";
        } else if (selectedId == R.id.rbEvening) {
            category = "Evening";
            time = "6:00 PM";
        } else if (selectedId == R.id.rbBedtime) {
            category = "Bedtime";
            time = "9:30 PM";
        }

        MedicationModel med = MedicationModel.createForCategory(medName, dosage, category, time);
        Task<Void> task = FRef.saveMedication(med);
        if (task == null) {
            Toast.makeText(getContext(), "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveMedication.setEnabled(false);
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (isAdded()) {
                    btnSaveMedication.setEnabled(true);
                    Toast.makeText(getContext(), "Medication saved", Toast.LENGTH_SHORT).show();
                    goBack();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (isAdded()) {
                    btnSaveMedication.setEnabled(true);
                    Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
