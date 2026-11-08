package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.common.FRef;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;

import java.util.HashMap;
import java.util.Map;

public class PQ1 extends Fragment {

    // Key used to pass the chosen goal to the next questions (PQ2 → PQ3 → PQ4)
    public static final String ARG_GOAL = "goal";

    // UI
    private RadioButton rbBulking, rbCutting, rbMaintaining;
    private LinearLayout llBulking, llCutting, llMaintaining;
    private EditText etHeight, etWeight;
    private Button btnBack, btnContinue;

    // The goal that is selected now
    private String selectedGoal = "Bulking";

    public PQ1() {
        // Required empty public constructor
    }

    // Inflates the layout of question 1
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_p_q1, container, false);
    }

    // Finds the views and connects the listeners
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupGoalSelection();
        setupListeners();
    }

    // Finds all the views
    private void initViews(View view) {
        rbBulking = view.findViewById(R.id.rbBulking);
        rbCutting = view.findViewById(R.id.rbCutting);
        rbMaintaining = view.findViewById(R.id.rbMaintaining);
        etHeight = view.findViewById(R.id.etHeight);
        etWeight = view.findViewById(R.id.etWeight);
        btnBack = view.findViewById(R.id.btnBack);
        btnContinue = view.findViewById(R.id.btnContinue);
        llBulking = view.findViewById(R.id.bulking_linearlayot);
        llCutting = view.findViewById(R.id.rbCutting_linearlayot);
        llMaintaining = view.findViewById(R.id.rbMaintaining_linearlayot);
    }

    // BUG FIX: now clicking the whole box OR the radio button selects the goal
    private void setupGoalSelection() {
        View.OnClickListener bulkingListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGoal("Bulking");
            }
        };
        View.OnClickListener cuttingListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGoal("Cutting");
            }
        };
        View.OnClickListener maintainingListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGoal("Maintaining");
            }
        };

        rbBulking.setOnClickListener(bulkingListener);
        llBulking.setOnClickListener(bulkingListener);
        rbCutting.setOnClickListener(cuttingListener);
        llCutting.setOnClickListener(cuttingListener);
        rbMaintaining.setOnClickListener(maintainingListener);
        llMaintaining.setOnClickListener(maintainingListener);

        selectGoal("Bulking");
    }

    // Checks one radio button, unchecks the others and changes the backgrounds
    private void selectGoal(String goal) {
        selectedGoal = goal;
        rbBulking.setChecked(goal.equals("Bulking"));
        rbCutting.setChecked(goal.equals("Cutting"));
        rbMaintaining.setChecked(goal.equals("Maintaining"));

        llBulking.setBackgroundResource(R.drawable.bg_unselected_option);
        llCutting.setBackgroundResource(R.drawable.bg_unselected_option);
        llMaintaining.setBackgroundResource(R.drawable.bg_unselected_option);

        if (goal.equals("Bulking")) {
            llBulking.setBackgroundResource(R.drawable.bg_selected_option);
        } else if (goal.equals("Cutting")) {
            llCutting.setBackgroundResource(R.drawable.bg_selected_option);
        } else {
            llMaintaining.setBackgroundResource(R.drawable.bg_selected_option);
        }
    }

    // Continue / Back buttons
    private void setupListeners() {
        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validateInputs()) {
                    String height = etHeight.getText().toString().trim();
                    String weight = etWeight.getText().toString().trim();
                    saveUserDataToFirebase(selectedGoal, height, weight);
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireActivity().finish();
            }
        });
    }

    // Height and weight must be real numbers in a normal range
    private boolean validateInputs() {
        String heightText = etHeight.getText().toString().trim();
        String weightText = etWeight.getText().toString().trim();
        if (heightText.isEmpty()) {
            etHeight.setError("Please enter your height");
            return false;
        }
        if (weightText.isEmpty()) {
            etWeight.setError("Please enter your weight");
            return false;
        }
        try {
            double height = Double.parseDouble(heightText);
            double weight = Double.parseDouble(weightText);
            if (height < 100 || height > 250) {
                etHeight.setError("Height must be 100-250 cm");
                return false;
            }
            if (weight < 30 || weight > 300) {
                etWeight.setError("Weight must be 30-300 kg");
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Numbers only", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // Saves goal + height + weight under Users / uid / Goals
    private void saveUserDataToFirebase(final String goal, String height, String weight) {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            Toast.makeText(getContext(), "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> goalsData = new HashMap<>();
        goalsData.put("goal", goal);
        goalsData.put("height", height);
        goalsData.put("weight", weight);

        btnContinue.setEnabled(false);
        userRef.child("Goals").updateChildren(goalsData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (isAdded()) {
                            btnContinue.setEnabled(true);
                            navigateToNextFragment(goal);
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

    // Opens PQ2 and sends it the goal inside a Bundle
    private void navigateToNextFragment(String goal) {
        PQ2 pq2Fragment = new PQ2();
        Bundle args = new Bundle();
        args.putString(ARG_GOAL, goal);
        pq2Fragment.setArguments(args);

        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, pq2Fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }
}
