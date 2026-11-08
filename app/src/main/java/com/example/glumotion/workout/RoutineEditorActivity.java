package com.example.glumotion.workout;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.FRef;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * Create or edit a personal routine (like the Hevy app):
 * name of the routine, which day, which exercises, sets / reps / rest of every exercise.
 * Saved in Firebase: Users / uid / Routines / routineId
 */
public class RoutineEditorActivity extends BaseActivity {

    public static final String EXTRA_ROUTINE_ID = "routine_id";
    public static final String[] DAYS = {"Any day", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    // One exercise row on the screen (the EditTexts hold the values)
    private static class ExerciseRow {
        String name;
        String muscle;
        View view;
        EditText etSets, etReps, etRest;
    }

    private final ArrayList<ExerciseRow> rows = new ArrayList<>();

    private EditText etRoutineName;
    private Spinner spRoutineDay;
    private LinearLayout llEditorExercises;
    private TextView tvEditorExercisesTitle, tvEditorEmpty;

    private String routineId;          // null = new routine
    private boolean changed = false;   // to ask before leaving without saving

    private ActivityResultLauncher<Intent> pickerLauncher;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routine_editor);

        routineId = getIntent().getStringExtra(EXTRA_ROUTINE_ID);
        if (routineId == null) {
            setupBackBar("New Routine");
        } else {
            setupBackBar("Edit Routine");
        }
        setupToolbarAction("Save", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRoutine();
            }
        });

        etRoutineName = findViewById(R.id.etRoutineName);
        spRoutineDay = findViewById(R.id.spRoutineDay);
        llEditorExercises = findViewById(R.id.llEditorExercises);
        tvEditorExercisesTitle = findViewById(R.id.tvEditorExercisesTitle);
        tvEditorEmpty = findViewById(R.id.tvEditorEmpty);
        spRoutineDay.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DAYS));

        registerPicker();
        setupButtons();
        setupBackConfirm();
        updateTitle();

        if (routineId != null) {
            loadRoutine();
        }
    }

    // The picker returns the chosen exercises (ActivityResultContract)
    private void registerPicker() {
        pickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return;
                        }
                        String[] names = result.getData().getStringArrayExtra(ExercisePickerActivity.RESULT_NAMES);
                        String[] muscles = result.getData().getStringArrayExtra(ExercisePickerActivity.RESULT_MUSCLES);
                        if (names == null || muscles == null) {
                            return;
                        }
                        for (int i = 0; i < names.length; i++) {
                            addRow(names[i], muscles[i], 3, "10", 90);
                        }
                        changed = true;
                    }
                });
    }

    // "+ Add Exercise" and "Delete routine"
    private void setupButtons() {
        findViewById(R.id.btnAddExerciseToRoutine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickerLauncher.launch(new Intent(RoutineEditorActivity.this, ExercisePickerActivity.class));
            }
        });

        View btnDelete = findViewById(R.id.btnDeleteRoutine);
        if (routineId != null) {
            btnDelete.setVisibility(View.VISIBLE);
        }
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
    }

    // Back (arrow or phone) with changes → ask first
    private void setupBackConfirm() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!changed) {
                    finish();
                    return;
                }
                new AlertDialog.Builder(RoutineEditorActivity.this)
                        .setTitle("Discard changes?")
                        .setMessage("The routine was not saved.")
                        .setPositiveButton("Discard", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setNegativeButton("Keep editing", null)
                        .show();
            }
        });
    }

    // Adds one exercise row to the screen
    private void addRow(String name, String muscle, int sets, String reps, int rest) {
        final ExerciseRow row = new ExerciseRow();
        row.name = name;
        row.muscle = muscle;
        row.view = LayoutInflater.from(this).inflate(R.layout.item_routine_exercise_edit, llEditorExercises, false);

        TextView tvName = row.view.findViewById(R.id.tvEditExName);
        TextView tvMuscle = row.view.findViewById(R.id.tvEditExMuscle);
        row.etSets = row.view.findViewById(R.id.etEditExSets);
        row.etReps = row.view.findViewById(R.id.etEditExReps);
        row.etRest = row.view.findViewById(R.id.etEditExRest);

        tvName.setText(name);
        tvMuscle.setText(muscle);
        row.etSets.setText(String.valueOf(sets));
        row.etReps.setText(reps);
        row.etRest.setText(String.valueOf(rest));

        row.view.findViewById(R.id.btnEditExRemove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rows.remove(row);
                llEditorExercises.removeView(row.view);
                changed = true;
                updateTitle();
            }
        });
        row.view.findViewById(R.id.btnEditExUp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveUp(row);
            }
        });

        rows.add(row);
        llEditorExercises.addView(row.view);
        updateTitle();
    }

    // Moves an exercise one place up (change the order of the routine)
    private void moveUp(ExerciseRow row) {
        int index = rows.indexOf(row);
        if (index <= 0) {
            return;
        }
        rows.remove(index);
        rows.add(index - 1, row);
        llEditorExercises.removeView(row.view);
        llEditorExercises.addView(row.view, index - 1);
        changed = true;
    }

    // "Exercises (3)" + empty message
    private void updateTitle() {
        tvEditorExercisesTitle.setText("Exercises (" + rows.size() + ")");
        if (rows.isEmpty()) {
            tvEditorEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEditorEmpty.setVisibility(View.GONE);
        }
    }

    // Edit mode: reads the routine from Firebase
    private void loadRoutine() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("Routines").child(routineId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Routine routine = Routine.fromSnapshot(snapshot);
                if (routine == null) {
                    toast("Routine not found");
                    finish();
                    return;
                }
                etRoutineName.setText(routine.getTitle());
                for (int i = 0; i < DAYS.length; i++) {
                    if (DAYS[i].equals(routine.getDay())) {
                        spRoutineDay.setSelection(i);
                    }
                }
                for (int i = 0; i < routine.getExercises().size(); i++) {
                    Exercise ex = routine.getExercises().get(i);
                    addRow(ex.getName(), ex.getPrimaryMuscle(), ex.getSets(), ex.getReps(), ex.getRestSeconds());
                }
                changed = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Checks everything and saves the routine
    private void saveRoutine() {
        String name = etRoutineName.getText().toString().trim();
        if (name.isEmpty()) {
            etRoutineName.setError("Give the routine a name");
            return;
        }
        if (rows.isEmpty()) {
            toast("Add at least one exercise");
            return;
        }

        Routine routine = new Routine(name, 0, 0, "", R.drawable.ic_workout);
        routine.setDay(spRoutineDay.getSelectedItem().toString());
        for (int i = 0; i < rows.size(); i++) {
            ExerciseRow row = rows.get(i);
            int sets = parseInt(row.etSets.getText().toString(), 0);
            int rest = parseInt(row.etRest.getText().toString(), 90);
            String reps = row.etReps.getText().toString().trim();
            if (sets < 1 || sets > 10) {
                row.etSets.setError("1-10");
                return;
            }
            if (reps.isEmpty()) {
                row.etReps.setError("?");
                return;
            }
            if (rest < 10 || rest > 600) {
                row.etRest.setError("10-600");
                return;
            }
            routine.addExercise(Exercise.createCustom(row.name, row.muscle, sets, reps, rest));
        }

        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            toast("Not logged in!");
            return;
        }
        DatabaseReference routinesRef = userRef.child("Routines");
        if (routineId == null) {
            routineId = routinesRef.push().getKey();
        }
        routinesRef.child(routineId).setValue(routine.toMap()).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                toast("Routine saved ✓");
                changed = false;
                finish();
            }
        });
    }

    // Asks before deleting the routine
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete routine?")
                .setMessage("Your workout history stays saved.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DatabaseReference userRef = FRef.getRefCurrentUser();
                        if (userRef != null && routineId != null) {
                            userRef.child("Routines").child(routineId).removeValue();
                        }
                        toast("Deleted");
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Text → int (default if wrong)
    private int parseInt(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
