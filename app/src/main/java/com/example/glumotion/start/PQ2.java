package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.NutritionCalculator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
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

import java.util.HashMap;
import java.util.Map;

public class PQ2 extends Fragment {

    // UI Elements
    private TextView tvCaloriesValue, tvProteinValue, tvCarbsValue, tvFatsValue;
    private Button btnBack, btnContinue;
    private RadioButton rbBeginner, rbIntermediate, rbAdvanced, rbGym, rbHome;
    private LinearLayout llBeginner, llIntermediate, llAdvanced, llGym, llHome;

    // Values selected by the user
    private String fitnessLevel = "Beginner";
    private String workoutLocation = "Gym";
    private String goal = "Bulking";

    // Calculated nutrition (kept as numbers, not read back from the TextViews)
    private int targetCalories, protein, carbs, fats;

    // Body data read from Firebase (0 = not loaded yet)
    private double weight, height;
    private int age;
    private String gender = "";

    public PQ2() {
        // Required empty public constructor
    }

    // Inflates the layout of question 2
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_p_q2, container, false);
    }

    // Reads the goal from PQ1, connects listeners and calculates the calories
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null && getArguments().getString(PQ1.ARG_GOAL) != null) {
            goal = getArguments().getString(PQ1.ARG_GOAL);
        }

        initViews(view);
        setupFitnessLevelSelection();
        setupLocationSelection();
        fetchUserDataAndCalculate();
        setupListeners();
    }

    // Finds all the views
    private void initViews(View view) {
        tvCaloriesValue = view.findViewById(R.id.tvCaloriesValue);
        tvProteinValue = view.findViewById(R.id.tvProteinValue);
        tvCarbsValue = view.findViewById(R.id.tvCarbsValue);
        tvFatsValue = view.findViewById(R.id.tvFatsValue);

        rbBeginner = view.findViewById(R.id.rbBeginner);
        rbIntermediate = view.findViewById(R.id.rbIntermediate);
        rbAdvanced = view.findViewById(R.id.rbAdvanced);
        rbGym = view.findViewById(R.id.rbGym);
        rbHome = view.findViewById(R.id.rbHome);

        llBeginner = view.findViewById(R.id.rbBeginner_layout);
        llIntermediate = view.findViewById(R.id.rbIntermediate_layout);
        llAdvanced = view.findViewById(R.id.rbAdvanced_layout);
        llGym = view.findViewById(R.id.rbGym_layout);
        llHome = view.findViewById(R.id.rbHome_layout);

        btnBack = view.findViewById(R.id.btnBack);
        btnContinue = view.findViewById(R.id.btnContinue);
    }

    // Box OR radio button selects the fitness level
    private void setupFitnessLevelSelection() {
        addLevelListener(rbBeginner, llBeginner, "Beginner");
        addLevelListener(rbIntermediate, llIntermediate, "Intermediate");
        addLevelListener(rbAdvanced, llAdvanced, "Advanced");
        selectLevel("Beginner");
    }

    // Helper: one listener for the radio button and its box
    private void addLevelListener(RadioButton rb, LinearLayout box, final String level) {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectLevel(level);
            }
        };
        rb.setOnClickListener(listener);
        box.setOnClickListener(listener);
    }

    // Marks one level as selected
    private void selectLevel(String level) {
        fitnessLevel = level;
        // The activity level changes the calories → calculate again
        if (weight > 0) {
            calculateNutrition();
        }
        markOption(rbBeginner, llBeginner, level.equals("Beginner"));
        markOption(rbIntermediate, llIntermediate, level.equals("Intermediate"));
        markOption(rbAdvanced, llAdvanced, level.equals("Advanced"));
    }

    // Box OR radio button selects the workout place
    private void setupLocationSelection() {
        View.OnClickListener gymListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectLocation("Gym");
            }
        };
        View.OnClickListener homeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectLocation("Home");
            }
        };
        rbGym.setOnClickListener(gymListener);
        llGym.setOnClickListener(gymListener);
        rbHome.setOnClickListener(homeListener);
        llHome.setOnClickListener(homeListener);
        selectLocation("Gym");
    }

    // Marks one place as selected
    private void selectLocation(String location) {
        workoutLocation = location;
        markOption(rbGym, llGym, location.equals("Gym"));
        markOption(rbHome, llHome, location.equals("Home"));
    }

    // Helper: checked + green background, or unchecked + white background
    private void markOption(RadioButton rb, LinearLayout box, boolean selected) {
        rb.setChecked(selected);
        if (selected) {
            box.setBackgroundResource(R.drawable.bg_selected_option);
        } else {
            box.setBackgroundResource(R.drawable.bg_unselected_option);
        }
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
                saveFitnessDataToFirebase();
            }
        });
    }

    // Reads height + weight + age + gender from Firebase and calculates the nutrition
    private void fetchUserDataAndCalculate() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String heightStr = snapshot.child("Goals").child("height").getValue(String.class);
                String weightStr = snapshot.child("Goals").child("weight").getValue(String.class);
                String savedGoal = snapshot.child("Goals").child("goal").getValue(String.class);
                Long savedAge = snapshot.child("age").getValue(Long.class);
                String savedGender = snapshot.child("gender").getValue(String.class);
                if (savedGoal != null) {
                    goal = savedGoal;
                }
                if (savedAge != null) {
                    age = savedAge.intValue();
                }
                if (savedGender != null) {
                    gender = savedGender;
                }
                if (heightStr != null && weightStr != null) {
                    try {
                        weight = Double.parseDouble(weightStr);
                        height = Double.parseDouble(heightStr);
                        calculateNutrition();
                    } catch (NumberFormatException e) {
                        // wrong number - keep default values
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Load failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Calculates calories + macros with NutritionCalculator and shows them
    private void calculateNutrition() {
        int[] result = NutritionCalculator.calculate(weight, height, age, gender, goal, fitnessLevel);
        targetCalories = result[0];
        protein = result[1];
        carbs = result[2];
        fats = result[3];

        if (tvCaloriesValue != null) {
            tvCaloriesValue.setText(String.valueOf(targetCalories));
            tvProteinValue.setText(protein + "g");
            tvCarbsValue.setText(carbs + "g");
            tvFatsValue.setText(fats + "g");
        }
    }

    // Saves Fitness and Nutrition, then opens PQ3
    private void saveFitnessDataToFirebase() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            Toast.makeText(getContext(), "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> fitnessData = new HashMap<>();
        fitnessData.put("fitnessLevel", fitnessLevel);
        fitnessData.put("workoutLocation", workoutLocation);

        Map<String, Object> nutritionData = new HashMap<>();
        nutritionData.put("targetCalories", String.valueOf(targetCalories));
        nutritionData.put("protein", String.valueOf(protein));
        nutritionData.put("carbs", String.valueOf(carbs));
        nutritionData.put("fats", String.valueOf(fats));

        // Both nodes are saved in ONE request (updateChildren with paths)
        Map<String, Object> updates = new HashMap<>();
        updates.put("Fitness", fitnessData);
        updates.put("Nutrition", nutritionData);

        btnContinue.setEnabled(false);
        userRef.updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (isAdded()) {
                            btnContinue.setEnabled(true);
                            navigateToNextFragment();
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

    // Opens PQ3 and passes the goal forward
    private void navigateToNextFragment() {
        PQ3 pq3Fragment = new PQ3();
        Bundle args = new Bundle();
        args.putString(PQ1.ARG_GOAL, goal);
        pq3Fragment.setArguments(args);

        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, pq3Fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }
}
