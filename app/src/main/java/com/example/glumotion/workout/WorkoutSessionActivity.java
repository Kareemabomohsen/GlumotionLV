package com.example.glumotion.workout;

import com.example.glumotion.R;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.glucose.DMActivity;
import com.example.glumotion.glucose.GlucoseReading;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The workout logger: for every exercise the user writes kg + reps of every set,
 * presses ✓ and a rest timer opens. At the end everything is saved in Firebase.
 */
public class WorkoutSessionActivity extends BaseActivity {

    public static final String EXTRA_ROUTINE = "routine_index";
    public static final String EXTRA_SINGLE_EXERCISE = "single_exercise";

    // For the user's own routines (Hevy style) - the exercises come as arrays
    public static final String EXTRA_CUSTOM_TITLE = "custom_title";
    public static final String EXTRA_NAMES = "names";
    public static final String EXTRA_MUSCLES = "muscles";
    public static final String EXTRA_REPS = "reps";
    public static final String EXTRA_SETS = "sets";
    public static final String EXTRA_RESTS = "rests";

    // ---- small inner classes that hold the views of the screen (OOP) ----

    // One row = one set
    private static class SetRow {
        int number;
        View root;
        TextView tvPrevious;
        EditText etKg, etReps;
        ImageView ivDone;
        boolean done = false;
    }

    // One exercise card = the exercise + its set rows
    private static class ExerciseBlock {
        Exercise exercise;
        LinearLayout setsContainer;
        ArrayList<SetRow> rows = new ArrayList<>();
    }

    // ---- data ----
    private Routine routine;
    private final ArrayList<Exercise> sessionExercises = new ArrayList<>();
    private final ArrayList<ExerciseBlock> blocks = new ArrayList<>();

    // "Previous" values from the last workout: exercise key → list like ["60kg × 8", "60kg × 8"]
    private final HashMap<String, ArrayList<String>> previousMap = new HashMap<>();

    private int preWorkoutGlucose = -1;
    private long startTime;

    // ---- views ----
    private LinearLayout exercisesContainer;
    private TextView tvRoutineTitle, tvSessionProgress, tvPreGlucose;
    private ImageView ivRoutineIcon;
    private Button btnFinishWorkout, btnBackToRoutines;

    // Opens the rest timer and gets its result back (ActivityResultContract)
    private ActivityResultLauncher<Intent> restTimerLauncher;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The trainee is training now → sensor alerts also go to his coach (EmergencyHelper)
        AppPrefs.setInWorkout(this, true);
        setContentView(R.layout.activity_workout_session);

        setupBottomNav(R.id.nav_workout);
        setupTopBar();
        startTime = System.currentTimeMillis();

        initViews();
        readRoutineFromIntent();
        registerRestTimer();
        buildExerciseCards();
        setupButtons();
        setupBackButton();
        loadPreviousFromFirebase();
        askPreWorkoutGlucose();
    }

    // Finds the views
    private void initViews() {
        exercisesContainer = findViewById(R.id.exercisesContainer);
        tvRoutineTitle = findViewById(R.id.tvRoutineTitle);
        tvSessionProgress = findViewById(R.id.tvSessionProgress);
        tvPreGlucose = findViewById(R.id.tvPreGlucose);
        ivRoutineIcon = findViewById(R.id.ivRoutineIcon);
        btnFinishWorkout = findViewById(R.id.btnFinishWorkout);
        btnBackToRoutines = findViewById(R.id.btnBackToRoutines);
    }

    // Reads which routine (or which single exercise) was chosen
    private void readRoutineFromIntent() {
        int index = getIntent().getIntExtra(EXTRA_ROUTINE, 0);
        routine = WorkoutData.getRoutine(index);

        // A routine that the user built himself
        String customTitle = getIntent().getStringExtra(EXTRA_CUSTOM_TITLE);
        String[] names = getIntent().getStringArrayExtra(EXTRA_NAMES);
        if (customTitle != null && names != null) {
            String[] muscles = getIntent().getStringArrayExtra(EXTRA_MUSCLES);
            String[] reps = getIntent().getStringArrayExtra(EXTRA_REPS);
            int[] sets = getIntent().getIntArrayExtra(EXTRA_SETS);
            int[] rests = getIntent().getIntArrayExtra(EXTRA_RESTS);
            for (int i = 0; i < names.length; i++) {
                sessionExercises.add(Exercise.createCustom(names[i], muscles[i], sets[i], reps[i], rests[i]));
            }
            tvRoutineTitle.setText(customTitle);
            ivRoutineIcon.setImageResource(R.drawable.ic_workout);
            return;
        }

        String single = getIntent().getStringExtra(EXTRA_SINGLE_EXERCISE);

        if (single != null && WorkoutData.findExercise(single) != null) {
            sessionExercises.add(WorkoutData.findExercise(single));
            tvRoutineTitle.setText(single);
        } else {
            sessionExercises.addAll(routine.getExercises());
            tvRoutineTitle.setText(routine.getTitle());
        }
        ivRoutineIcon.setImageResource(routine.getIconRes());
    }

    // What happens when the rest timer screen closes
    private void registerRestTimer() {
        restTimerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            Toast.makeText(WorkoutSessionActivity.this, "Rest done - next set, go!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Creates one card for every exercise with its sets
    private void buildExerciseCards() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < sessionExercises.size(); i++) {
            Exercise ex = sessionExercises.get(i);
            View card = inflater.inflate(R.layout.item_session_exercise, exercisesContainer, false);

            TextView tvName = card.findViewById(R.id.tvSessionExerciseName);
            TextView tvTarget = card.findViewById(R.id.tvSessionExerciseTarget);
            tvName.setText(ex.getName());
            tvTarget.setText(ex.getSetsRepsText() + " • " + ex.getRestSeconds() + "s");

            final ExerciseBlock block = new ExerciseBlock();
            block.exercise = ex;
            block.setsContainer = card.findViewById(R.id.setsContainer);
            blocks.add(block);

            for (int s = 0; s < ex.getSets(); s++) {
                addSetRow(block);
            }

            card.findViewById(R.id.btnAddSet).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addSetRow(block);
                    updateProgress();
                }
            });
            exercisesContainer.addView(card);
        }
        updateProgress();
    }

    // Adds one set row to an exercise card
    private void addSetRow(final ExerciseBlock block) {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_set_row, block.setsContainer, false);

        final SetRow row = new SetRow();
        row.number = block.rows.size() + 1;
        row.root = rowView;
        row.tvPrevious = rowView.findViewById(R.id.tvSetPrevious);
        row.etKg = rowView.findViewById(R.id.etSetKg);
        row.etReps = rowView.findViewById(R.id.etSetReps);
        row.ivDone = rowView.findViewById(R.id.ivSetDone);

        TextView tvNumber = rowView.findViewById(R.id.tvSetNumber);
        tvNumber.setText(String.valueOf(row.number));

        fillPreviousAndHints(block, row);

        row.ivDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSetDone(block, row);
            }
        });

        block.rows.add(row);
        block.setsContainer.addView(rowView);
    }

    // Writes the "Previous" text and the hints (gray numbers inside the boxes)
    private void fillPreviousAndHints(ExerciseBlock block, SetRow row) {
        Exercise ex = block.exercise;
        String kgHint = Exercise.formatKg(ex.getDefaultKg());
        String repsHint = String.valueOf(ex.getDefaultReps());
        String previousText = "-";

        ArrayList<String> previous = previousMap.get(ex.getKey());
        if (previous != null && !previous.isEmpty()) {
            // If there are fewer saved sets than rows, use the last saved one
            int index = row.number - 1;
            if (index >= previous.size()) {
                index = previous.size() - 1;
            }
            previousText = previous.get(index);
            // "60kg × 8" → kg = 60, reps = 8
            String[] parts = previousText.split("kg × ");
            if (parts.length == 2) {
                kgHint = parts[0].trim();
                repsHint = parts[1].trim();
            }
        }

        row.tvPrevious.setText(previousText);
        row.etKg.setHint(kgHint);
        row.etReps.setHint(repsHint);
    }

    // ✓ pressed: marks the set as done (green) or cancels it
    private void toggleSetDone(ExerciseBlock block, SetRow row) {
        if (row.done) {
            row.done = false;
            setRowLook(row, false);
            updateProgress();
            return;
        }

        // Empty box = the user accepts the gray hint
        if (row.etKg.getText().toString().trim().isEmpty()) {
            row.etKg.setText(row.etKg.getHint());
        }
        if (row.etReps.getText().toString().trim().isEmpty()) {
            row.etReps.setText(row.etReps.getHint());
        }

        double kg = parseDouble(row.etKg.getText().toString());
        int reps = (int) parseDouble(row.etReps.getText().toString());
        if (kg < 0 || kg > 500) {
            row.etKg.setError("0-500");
            return;
        }
        if (reps <= 0 || reps > 200) {
            row.etReps.setError("1-200");
            return;
        }

        row.done = true;
        setRowLook(row, true);
        updateProgress();
        openRestTimer(block, row);
    }

    // Green row + green check, or back to normal
    private void setRowLook(SetRow row, boolean done) {
        if (done) {
            row.root.setBackgroundResource(R.drawable.bg_set_done_row);
            row.etKg.setBackgroundResource(R.drawable.bg_set_input);
            row.etReps.setBackgroundResource(R.drawable.bg_set_input);
            row.ivDone.setImageResource(R.drawable.ic_check_circle_green);
        } else {
            row.root.setBackgroundResource(0);
            row.etKg.setBackgroundResource(R.drawable.bg_set_input_empty);
            row.etReps.setBackgroundResource(R.drawable.bg_set_input_empty);
            row.ivDone.setImageResource(R.drawable.ic_check_circle_outline);
        }
        row.etKg.setEnabled(!done);
        row.etReps.setEnabled(!done);
    }

    // Finds the next set that is not done and opens the rest timer for it
    private void openRestTimer(ExerciseBlock currentBlock, SetRow currentRow) {
        ExerciseBlock nextBlock = null;
        SetRow nextRow = null;

        // 1) next not-done set in the same exercise
        int startIndex = currentBlock.rows.indexOf(currentRow) + 1;
        for (int i = startIndex; i < currentBlock.rows.size() && nextRow == null; i++) {
            if (!currentBlock.rows.get(i).done) {
                nextBlock = currentBlock;
                nextRow = currentBlock.rows.get(i);
            }
        }
        // 2) otherwise the first not-done set in the next exercises
        int blockIndex = blocks.indexOf(currentBlock) + 1;
        for (int b = blockIndex; b < blocks.size() && nextRow == null; b++) {
            ExerciseBlock block = blocks.get(b);
            for (int i = 0; i < block.rows.size() && nextRow == null; i++) {
                if (!block.rows.get(i).done) {
                    nextBlock = block;
                    nextRow = block.rows.get(i);
                }
            }
        }

        if (nextRow == null) {
            Toast.makeText(this, "Last set done! Press Finish Workout", Toast.LENGTH_SHORT).show();
            return;
        }

        String nextKg = nextRow.etKg.getText().toString().trim();
        if (nextKg.isEmpty()) {
            nextKg = nextRow.etKg.getHint().toString();
        }
        String nextReps = nextRow.etReps.getText().toString().trim();
        if (nextReps.isEmpty()) {
            nextReps = nextRow.etReps.getHint().toString();
        }

        Intent intent = new Intent(this, RestTimerActivity.class);
        intent.putExtra(RestTimerActivity.EXTRA_EXERCISE, currentBlock.exercise.getName());
        intent.putExtra(RestTimerActivity.EXTRA_COMING_UP,
                "Set " + nextRow.number + " of " + nextBlock.rows.size() + " coming up");
        intent.putExtra(RestTimerActivity.EXTRA_REST_SECONDS, currentBlock.exercise.getRestSeconds());
        intent.putExtra(RestTimerActivity.EXTRA_NEXT_TITLE, "Next: Set " + nextRow.number);
        intent.putExtra(RestTimerActivity.EXTRA_NEXT_DETAIL,
                nextBlock.exercise.getName() + " • " + nextKg + "kg × " + nextReps + " reps");
        intent.putExtra(RestTimerActivity.EXTRA_NEXT_REST, nextBlock.exercise.getRestSeconds());
        restTimerLauncher.launch(intent);
    }

    // "5 / 24 sets done"
    private void updateProgress() {
        tvSessionProgress.setText(countDoneSets() + " / " + countAllSets() + " sets done");
    }

    // Number of sets marked with ✓
    private int countDoneSets() {
        int count = 0;
        for (int b = 0; b < blocks.size(); b++) {
            ArrayList<SetRow> rows = blocks.get(b).rows;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).done) {
                    count++;
                }
            }
        }
        return count;
    }

    // Number of all set rows
    private int countAllSets() {
        int count = 0;
        for (int b = 0; b < blocks.size(); b++) {
            count = count + blocks.get(b).rows.size();
        }
        return count;
    }

    // Helper: text → number (0 if wrong)
    private double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Finish / Back buttons
    private void setupButtons() {
        btnFinishWorkout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishWorkout();
            }
        });
        btnBackToRoutines.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmLeave(WorkoutActivity.class);
            }
        });
    }

    // The phone's Back button asks too (so the workout isn't lost by mistake)
    private void setupBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave(WorkoutActivity.class);
            }
        });
    }

    // Bottom bar pressed during the workout → ask first
    @Override
    protected void onBottomNavClick(Class<?> target) {
        confirmLeave(target);
    }

    // The Workout screen is already behind us → just close. Other tabs → start clean (no old screens behind)
    @Override
    protected void navigateTo(Class<?> target) {
        if (target == WorkoutActivity.class) {
            finish();
            return;
        }
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // If some sets are done - ask before leaving; otherwise just leave
    private void confirmLeave(final Class<?> target) {
        if (countDoneSets() == 0) {
            navigateTo(target);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Leave workout?")
                .setMessage("Your sets were not saved. Press Finish Workout to save them.")
                .setPositiveButton("Leave", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigateTo(target);
                    }
                })
                .setNegativeButton("Stay", null)
                .show();
    }

    // Checks the sets and asks if not all of them are done
    private void finishWorkout() {
        int done = countDoneSets();
        if (done == 0) {
            Toast.makeText(this, "Complete at least one set (press ✓)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (done < countAllSets()) {
            new AlertDialog.Builder(this)
                    .setTitle("Finish workout?")
                    .setMessage("You did " + done + " of " + countAllSets() + " sets. Save the workout anyway?")
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            saveWorkoutToFirebase();
                        }
                    })
                    .setNegativeButton("Keep training", null)
                    .show();
        } else {
            saveWorkoutToFirebase();
        }
    }

    /**
     * Saves:  Users/uid/Workouts/pushId  (the whole workout)
     *         Users/uid/ExerciseHistory/exerciseKey  (last sets → "Previous" column next time)
     * Everything in ONE updateChildren.
     */
    private void saveWorkoutToFirebase() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            Toast.makeText(this, "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String workoutId = userRef.child("Workouts").push().getKey();
        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> exercisesMap = new HashMap<>();
        double totalVolume = 0;
        int totalSets = 0;

        for (int b = 0; b < blocks.size(); b++) {
            ExerciseBlock block = blocks.get(b);
            ArrayList<Object> setsList = new ArrayList<>();
            ArrayList<String> historyList = new ArrayList<>();

            for (int i = 0; i < block.rows.size(); i++) {
                SetRow row = block.rows.get(i);
                if (!row.done) {
                    continue;
                }
                double kg = parseDouble(row.etKg.getText().toString());
                int reps = (int) parseDouble(row.etReps.getText().toString());

                Map<String, Object> setMap = new HashMap<>();
                setMap.put("kg", kg);
                setMap.put("reps", reps);
                setsList.add(setMap);
                historyList.add(Exercise.formatKg(kg) + "kg × " + reps);

                totalVolume = totalVolume + kg * reps;
                totalSets++;
            }

            if (!setsList.isEmpty()) {
                Map<String, Object> exMap = new HashMap<>();
                exMap.put("name", block.exercise.getName());
                exMap.put("sets", setsList);
                exercisesMap.put(block.exercise.getKey(), exMap);
                updates.put("ExerciseHistory/" + block.exercise.getKey(), historyList);
            }
        }

        Map<String, Object> workout = new HashMap<>();
        workout.put("routine", tvRoutineTitle.getText().toString());
        workout.put("timestamp", System.currentTimeMillis());
        workout.put("date", DateUtil.todayKey());
        workout.put("durationMinutes", (System.currentTimeMillis() - startTime) / 60000);
        workout.put("totalSets", totalSets);
        workout.put("totalVolumeKg", totalVolume);
        workout.put("exercises", exercisesMap);
        if (preWorkoutGlucose > 0) {
            workout.put("preWorkoutGlucose", preWorkoutGlucose);
        }
        updates.put("Workouts/" + workoutId, workout);

        final int savedSets = totalSets;
        final double savedVolume = totalVolume;
        btnFinishWorkout.setEnabled(false);
        userRef.updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        showAfterWorkoutDialog(savedSets, savedVolume);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        btnFinishWorkout.setEnabled(true);
                        Toast.makeText(WorkoutSessionActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Summary + reminder to check blood sugar after training (exercise can lower it for hours)
    private void showAfterWorkoutDialog(int sets, double volume) {
        new AlertDialog.Builder(this)
                .setTitle("Workout saved 💪")
                .setMessage(sets + " sets • " + Exercise.formatKg(volume) + " kg total volume\n\n"
                        + "Remember to check your blood sugar now - exercise can lower glucose for up to 24 hours.")
                .setCancelable(false)
                .setPositiveButton("Log glucose", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigateTo(DMActivity.class);
                    }
                })
                .setNegativeButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigateTo(WorkoutActivity.class);
                    }
                })
                .show();
    }

    // Reads Users/uid/ExerciseHistory to show the "Previous" column
    private void loadPreviousFromFirebase() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("ExerciseHistory").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot exSnap : snapshot.getChildren()) {
                    ArrayList<String> list = new ArrayList<>();
                    for (DataSnapshot setSnap : exSnap.getChildren()) {
                        String value = setSnap.getValue(String.class);
                        if (value != null) {
                            list.add(value);
                        }
                    }
                    previousMap.put(exSnap.getKey(), list);
                }
                // Refresh the rows that are still empty
                for (int b = 0; b < blocks.size(); b++) {
                    ExerciseBlock block = blocks.get(b);
                    for (int i = 0; i < block.rows.size(); i++) {
                        if (!block.rows.get(i).done) {
                            fillPreviousAndHints(block, block.rows.get(i));
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Diabetes safety: asks for the blood sugar before training (can be skipped)
    private void askPreWorkoutGlucose() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 120");
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(padding, padding / 2, padding, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Blood sugar before training")
                .setMessage("What is your glucose now (mg/dL)? We'll tell you if it's safe to start.")
                .setView(box)
                .setPositiveButton("Check", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int value = (int) parseDouble(input.getText().toString());
                        if (value >= 20 && value <= 600) {
                            onPreWorkoutGlucose(value);
                        } else {
                            Toast.makeText(WorkoutSessionActivity.this, "Enter a value between 20 and 600", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Skip", null)
                .show();
    }

    // Shows the advice and saves the reading in the Blood Sugar log as "Before Workout"
    private void onPreWorkoutGlucose(int value) {
        preWorkoutGlucose = value;
        GlucoseReading.save(new GlucoseReading(value, "Before Workout"));

        tvPreGlucose.setVisibility(View.VISIBLE);
        tvPreGlucose.setText("Pre-workout glucose: " + value + " mg/dL (" + GlucoseReading.getStatus(value) + ")");
        tvPreGlucose.setTextColor(GlucoseReading.getStatusColor(value));

        new AlertDialog.Builder(this)
                .setTitle(value + " mg/dL")
                .setMessage(GlucoseReading.getWorkoutAdvice(value))
                .setPositiveButton("OK", null)
                .show();
    }

    // The workout page closed → not training anymore
    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppPrefs.setInWorkout(this, false);
    }
}
