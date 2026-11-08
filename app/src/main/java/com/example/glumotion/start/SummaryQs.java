package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.common.FRef;
import com.example.glumotion.home.HomePageActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SummaryQs extends Fragment {

    private TextView tvGoal, tvFitnessLevel, tvLocation, tvHeightWeight, tvCalories, tvMacros, tvDiet, tvSleep, tvWater, tvEmergency;
    private Button btnSaveProfile;

    public SummaryQs() {
        // Required empty public constructor
    }

    // Inflates the summary layout
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_summary_qs, container, false);
    }

    // Loads all answers and connects the save button
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadUserDataFromFirebase();
        setupListeners();
    }

    // Finds all the views
    private void initViews(View view) {
        tvGoal = view.findViewById(R.id.tvSummaryGoal);
        tvFitnessLevel = view.findViewById(R.id.tvSummaryFitnessLevel);
        tvLocation = view.findViewById(R.id.tvSummaryLocation);
        tvHeightWeight = view.findViewById(R.id.tvSummaryHeightWeight);
        tvCalories = view.findViewById(R.id.tvSummaryCalories);
        tvMacros = view.findViewById(R.id.tvSummaryMacros);
        tvDiet = view.findViewById(R.id.tvSummaryDiet);
        tvSleep = view.findViewById(R.id.tvSummarySleep);
        tvWater = view.findViewById(R.id.tvSummaryWater);
        tvEmergency = view.findViewById(R.id.tvSummaryEmergency);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
    }

    // Helper: reads a String from Users/uid/section/key
    private String read(DataSnapshot snapshot, String section, String key) {
        return snapshot.child(section).child(key).getValue(String.class);
    }

    // Reads the whole user node once and shows every answer
    private void loadUserDataFromFirebase() {
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
                String goal = read(snapshot, "Goals", "goal");
                String height = read(snapshot, "Goals", "height");
                String weight = read(snapshot, "Goals", "weight");
                if (goal != null) {
                    tvGoal.setText(goal);
                }
                if (height != null && weight != null) {
                    tvHeightWeight.setText(height + " cm / " + weight + " kg");
                }

                String level = read(snapshot, "Fitness", "fitnessLevel");
                String location = read(snapshot, "Fitness", "workoutLocation");
                if (level != null) {
                    tvFitnessLevel.setText(level);
                }
                if (location != null) {
                    tvLocation.setText(location);
                }

                String calories = read(snapshot, "Nutrition", "targetCalories");
                String protein = read(snapshot, "Nutrition", "protein");
                String carbs = read(snapshot, "Nutrition", "carbs");
                String fats = read(snapshot, "Nutrition", "fats");
                if (calories != null) {
                    tvCalories.setText(calories + " kcal");
                }
                if (protein != null && carbs != null && fats != null) {
                    tvMacros.setText(protein + "g / " + carbs + "g / " + fats + "g");
                }

                String diet = read(snapshot, "DietPreference", "dietPreference");
                if (diet != null) {
                    tvDiet.setText(diet);
                }

                String sleepTime = read(snapshot, "SleepSchedule", "sleepTime");
                String wakeTime = read(snapshot, "SleepSchedule", "wakeUpTime");
                if (sleepTime != null && wakeTime != null) {
                    tvSleep.setText(sleepTime + " - " + wakeTime);
                }

                String water = read(snapshot, "WaterIntake", "waterTargetLiters");
                if (water != null) {
                    tvWater.setText(water + "L/day");
                }

                String contactName = read(snapshot, "EmergencyContact", "contactName");
                if (contactName != null && !contactName.isEmpty()) {
                    tvEmergency.setText("Configured (" + contactName + ")");
                } else {
                    tvEmergency.setText("Not Configured");
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

    // Marks the profile as completed and opens the dashboard
    private void setupListeners() {
        btnSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatabaseReference userRef = FRef.getRefCurrentUser();
                if (userRef == null) {
                    return;
                }
                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("isProfileCompleted", true);

                btnSaveProfile.setEnabled(false);
                userRef.child("Status").updateChildren(statusMap)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                if (isAdded()) {
                                    Toast.makeText(getContext(), "Profile saved!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(requireActivity(), HomePageActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    requireActivity().finish();
                                }
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                if (isAdded()) {
                                    btnSaveProfile.setEnabled(true);
                                    Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
    }
}
