package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.common.FRef;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;

import java.util.HashMap;
import java.util.Map;

public class PQ5 extends Fragment {

    // UI
    private EditText etWaterTarget, etContactName, etPhoneNumber;
    private SwitchCompat switchReminders;
    private Button btnBack, btnContinue;

    public PQ5() {
        // Required empty public constructor
    }

    // Inflates the layout of question 5
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_p_q5, container, false);
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
        etWaterTarget = view.findViewById(R.id.etWaterTarget);
        switchReminders = view.findViewById(R.id.switchReminders);
        etContactName = view.findViewById(R.id.etContactName);
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber);
        btnBack = view.findViewById(R.id.btnBack);
        btnContinue = view.findViewById(R.id.btnContinue);
    }

    // Back / Continue
    private void setupListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getParentFragmentManager().popBackStack();
            }
        });
        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePQ5DataToFirebase();
            }
        });
    }

    // Saves water target + emergency contact, then opens the summary
    private void savePQ5DataToFirebase() {
        String waterTarget = etWaterTarget.getText().toString().trim();
        String contactName = etContactName.getText().toString().trim();
        String phoneNumber = etPhoneNumber.getText().toString().trim();

        if (waterTarget.isEmpty()) {
            etWaterTarget.setError("Please enter your water intake target");
            return;
        }
        // Emergency contact is important for diabetics (hypoglycemia) - name and phone go together
        if (!contactName.isEmpty() && phoneNumber.length() < 9) {
            etPhoneNumber.setError("Enter a valid phone number");
            return;
        }

        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            Toast.makeText(getContext(), "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> waterData = new HashMap<>();
        waterData.put("waterTargetLiters", waterTarget);
        waterData.put("remindersEnabled", switchReminders.isChecked());

        Map<String, Object> emergencyData = new HashMap<>();
        emergencyData.put("contactName", contactName);
        emergencyData.put("phoneNumber", phoneNumber);

        Map<String, Object> updates = new HashMap<>();
        updates.put("WaterIntake", waterData);
        updates.put("EmergencyContact", emergencyData);

        btnContinue.setEnabled(false);
        userRef.updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (isAdded()) {
                            btnContinue.setEnabled(true);
                            openSummary();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (isAdded()) {
                            btnContinue.setEnabled(true);
                            Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // BUG FIX: R.id.fragment_container instead of android.R.id.content
    private void openSummary() {
        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new SummaryQs());
        transaction.addToBackStack(null);
        transaction.commit();
    }
}
