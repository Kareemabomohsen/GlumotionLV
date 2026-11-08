package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
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

public class PQ4 extends Fragment {

    // Value saved when the diet question is skipped
    public static final String DIET_NOT_NEEDED = "Not needed (Bulking)";

    // UI
    private CheckBox cbLactose, cbGluten, cbNut, cbHalal, cbVegan, cbVegetarian;
    private Spinner spinnerDiet;
    private EditText etWakeUpTime, etSleepTime;
    private Button btnBack, btnContinue;
    private View cardDietPreference;
    private TextView tvDietSkippedNote;

    // The goal chosen in PQ1 (Bulking / Cutting / Maintaining)
    private String goal = "";

    // Diet options (Low Glycemic first because the app is for diabetics)
    private final String[] diets = {"Low Glycemic (Diabetic-friendly)", "Standard", "Low Carb", "High Protein", "Mediterranean", "Paleo"};

    public PQ4() {
        // Required empty public constructor
    }

    // Inflates the layout of question 4
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_p_q4, container, false);
    }

    // Prepares the screen and decides if the diet question is shown
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupSpinner();
        setupTimePickers();
        setupListeners();

        // Goal comes from PQ1 inside the Bundle, if not → read it from Firebase
        if (getArguments() != null && getArguments().getString(PQ1.ARG_GOAL) != null) {
            goal = getArguments().getString(PQ1.ARG_GOAL);
            updateDietQuestion();
        } else {
            loadGoalFromFirebase();
        }
    }

    // Finds all the views
    private void initViews(View view) {
        cbLactose = view.findViewById(R.id.cbLactose);
        cbGluten = view.findViewById(R.id.cbGluten);
        cbNut = view.findViewById(R.id.cbNut);
        cbHalal = view.findViewById(R.id.cbHalal);
        cbVegan = view.findViewById(R.id.cbVegan);
        cbVegetarian = view.findViewById(R.id.cbVegetarian);

        spinnerDiet = view.findViewById(R.id.spinnerDiet);
        etWakeUpTime = view.findViewById(R.id.etWakeUpTime);
        etSleepTime = view.findViewById(R.id.etSleepTime);

        cardDietPreference = view.findViewById(R.id.cardDietPreference);
        tvDietSkippedNote = view.findViewById(R.id.tvDietSkippedNote);

        btnBack = view.findViewById(R.id.btnBack);
        btnContinue = view.findViewById(R.id.btnContinue);
    }

    // Fills the diet spinner
    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, diets);
        spinnerDiet.setAdapter(adapter);
    }

    // Reads Users / uid / Goals / goal (used when the Bundle is missing)
    private void loadGoalFromFirebase() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("Goals").child("goal").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String savedGoal = snapshot.getValue(String.class);
                if (savedGoal != null && isAdded()) {
                    goal = savedGoal;
                    updateDietQuestion();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /**
     * BUG FIX: if the goal is Bulking we DON'T ask about a diet
     * (a diet = eating less, bulking = eating more, they don't fit together).
     */
    private void updateDietQuestion() {
        if (isBulking()) {
            cardDietPreference.setVisibility(View.GONE);
            tvDietSkippedNote.setVisibility(View.VISIBLE);
        } else {
            cardDietPreference.setVisibility(View.VISIBLE);
            tvDietSkippedNote.setVisibility(View.GONE);
        }
    }

    // True when the user chose Bulking
    private boolean isBulking() {
        return "Bulking".equals(goal);
    }

    // Opens a clock when the user clicks on the wake up / sleep fields
    private void setupTimePickers() {
        etWakeUpTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePicker(etWakeUpTime, 7);
            }
        });
        etSleepTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePicker(etSleepTime, 23);
            }
        });
    }

    // Shows a TimePickerDialog and writes the chosen time ("07:30") into the field
    private void showTimePicker(final EditText target, int defaultHour) {
        TimePickerDialog dialog = new TimePickerDialog(requireContext(), new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                target.setText(DateUtil.twoDigits(hourOfDay) + ":" + DateUtil.twoDigits(minute));
                target.setError(null);
            }
        }, defaultHour, 0, true);
        dialog.show();
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
                savePQ4DataToFirebase();
            }
        });
    }

    // Saves allergies, diet and sleep schedule in one request, then opens PQ5
    private void savePQ4DataToFirebase() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            Toast.makeText(getContext(), "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (etWakeUpTime.getText().toString().trim().isEmpty()) {
            etWakeUpTime.setError("Choose a time");
            return;
        }
        if (etSleepTime.getText().toString().trim().isEmpty()) {
            etSleepTime.setError("Choose a time");
            return;
        }

        Map<String, Object> allergiesData = new HashMap<>();
        allergiesData.put("lactoseAllergy", cbLactose.isChecked());
        allergiesData.put("glutenAllergy", cbGluten.isChecked());
        allergiesData.put("nutAllergy", cbNut.isChecked());
        allergiesData.put("halal", cbHalal.isChecked());
        allergiesData.put("vegan", cbVegan.isChecked());
        allergiesData.put("vegetarian", cbVegetarian.isChecked());

        Map<String, Object> dietData = new HashMap<>();
        if (isBulking()) {
            dietData.put("dietPreference", DIET_NOT_NEEDED);
        } else {
            dietData.put("dietPreference", spinnerDiet.getSelectedItem().toString());
        }

        Map<String, Object> sleepData = new HashMap<>();
        sleepData.put("wakeUpTime", etWakeUpTime.getText().toString().trim());
        sleepData.put("sleepTime", etSleepTime.getText().toString().trim());

        Map<String, Object> updates = new HashMap<>();
        updates.put("Allergies", allergiesData);
        updates.put("DietPreference", dietData);
        updates.put("SleepSchedule", sleepData);

        btnContinue.setEnabled(false);
        userRef.updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (isAdded()) {
                            btnContinue.setEnabled(true);
                            openNext();
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
    private void openNext() {
        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new PQ5());
        transaction.addToBackStack(null);
        transaction.commit();
    }
}
