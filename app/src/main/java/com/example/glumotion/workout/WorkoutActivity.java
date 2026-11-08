package com.example.glumotion.workout;

import com.example.glumotion.R;
import com.example.glumotion.chat.ChatBotActivity;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.FRef;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

public class WorkoutActivity extends BaseActivity {

    // The chat button of this page opens the workout bot
    @Override
    protected String getChatMode() {
        return ChatBotActivity.MODE_WORKOUT;
    }

    private TextView btnRoutinesTab, btnExplanationTab;
    private View layoutRoutines, layoutExplanation;
    private RecyclerView rvExercises;
    private LinearLayout llMyRoutines;
    private TextView tvNoRoutines;

    // Builds the workout screen (2 tabs: Routines / Explanation + History button)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout);

        setupBottomNav(R.id.nav_workout);
        setupTopBar();
        initViews();
        setupTabs();
        setupRoutineButtons();
        setupExerciseList();
        setupMyRoutines();
        showRoutinesTab();
    }

    // Every time the screen opens again (after creating / editing a routine) → reload
    @Override
    protected void onResume() {
        super.onResume();
        loadMyRoutines();
    }

    // Finds the views
    private void initViews() {
        btnRoutinesTab = findViewById(R.id.btnRoutinesTab);
        btnExplanationTab = findViewById(R.id.btnExplanationTab);
        layoutRoutines = findViewById(R.id.layoutRoutines);
        layoutExplanation = findViewById(R.id.layoutExplanation);
        rvExercises = findViewById(R.id.rvExercises);
        llMyRoutines = findViewById(R.id.llMyRoutines);
        tvNoRoutines = findViewById(R.id.tvNoRoutines);
    }

    // "+ New Routine" opens the editor (Hevy style)
    private void setupMyRoutines() {
        findViewById(R.id.btnNewRoutine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(WorkoutActivity.this, RoutineEditorActivity.class));
            }
        });
    }

    // Reads Users / uid / Routines and shows a card for every routine
    private void loadMyRoutines() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("Routines").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<Routine> routines = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Routine routine = Routine.fromSnapshot(snap);
                    if (routine != null) {
                        routines.add(routine);
                    }
                }
                showMyRoutines(routines);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Draws the routine cards (today's routine is marked)
    private void showMyRoutines(ArrayList<Routine> routines) {
        llMyRoutines.removeAllViews();
        if (routines.isEmpty()) {
            tvNoRoutines.setVisibility(View.VISIBLE);
        } else {
            tvNoRoutines.setVisibility(View.GONE);
        }
        String today = RoutineEditorActivity.DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK)];

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < routines.size(); i++) {
            final Routine routine = routines.get(i);
            View card = inflater.inflate(R.layout.item_my_routine, llMyRoutines, false);
            TextView tvName = card.findViewById(R.id.tvMyRoutineName);
            TextView tvInfo = card.findViewById(R.id.tvMyRoutineInfo);
            TextView tvExercises = card.findViewById(R.id.tvMyRoutineExercises);

            String day = routine.getDay();
            if (day.equals(today)) {
                day = "Today 🔥";
            }
            tvName.setText(routine.getTitle());
            tvInfo.setText(day + " • " + routine.getExercises().size() + " exercises • " + routine.getTotalSets() + " sets");
            tvExercises.setText(routine.getExercisesPreview());

            card.findViewById(R.id.btnStartMyRoutine).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCustomSession(routine);
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(WorkoutActivity.this, RoutineEditorActivity.class);
                    intent.putExtra(RoutineEditorActivity.EXTRA_ROUTINE_ID, routine.getId());
                    startActivity(intent);
                }
            });
            llMyRoutines.addView(card);
        }
    }

    // Starts the logger with the exercises of a user routine (sent as arrays inside the Intent)
    private void openCustomSession(Routine routine) {
        int n = routine.getExercises().size();
        String[] names = new String[n];
        String[] muscles = new String[n];
        String[] reps = new String[n];
        int[] sets = new int[n];
        int[] rests = new int[n];
        for (int i = 0; i < n; i++) {
            Exercise ex = routine.getExercises().get(i);
            names[i] = ex.getName();
            muscles[i] = ex.getPrimaryMuscle();
            reps[i] = ex.getReps();
            sets[i] = ex.getSets();
            rests[i] = ex.getRestSeconds();
        }
        Intent intent = new Intent(this, WorkoutSessionActivity.class);
        intent.putExtra(WorkoutSessionActivity.EXTRA_CUSTOM_TITLE, routine.getTitle());
        intent.putExtra(WorkoutSessionActivity.EXTRA_NAMES, names);
        intent.putExtra(WorkoutSessionActivity.EXTRA_MUSCLES, muscles);
        intent.putExtra(WorkoutSessionActivity.EXTRA_REPS, reps);
        intent.putExtra(WorkoutSessionActivity.EXTRA_SETS, sets);
        intent.putExtra(WorkoutSessionActivity.EXTRA_RESTS, rests);
        startActivity(intent);
    }

    // The two tab buttons at the top
    private void setupTabs() {
        btnRoutinesTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRoutinesTab();
            }
        });
        btnExplanationTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExplanationTab();
            }
        });
        // 📅 History: the days I trained, how long, sets and volume
        findViewById(R.id.btnHistoryTab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(WorkoutActivity.this, WorkoutHistoryActivity.class));
            }
        });
    }

    // Shows the routine cards and colors the "Routines" tab purple
    private void showRoutinesTab() {
        layoutRoutines.setVisibility(View.VISIBLE);
        layoutExplanation.setVisibility(View.GONE);
        setTabSelected(btnRoutinesTab, true);
        setTabSelected(btnExplanationTab, false);
    }

    // Shows the exercise explanations and colors the "Explanation" tab purple
    private void showExplanationTab() {
        layoutRoutines.setVisibility(View.GONE);
        layoutExplanation.setVisibility(View.VISIBLE);
        setTabSelected(btnRoutinesTab, false);
        setTabSelected(btnExplanationTab, true);
    }

    // Helper: purple pill with white text OR white pill with grey text
    private void setTabSelected(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_pill_selected);
            tab.setTextColor(Color.WHITE);
        } else {
            tab.setBackgroundResource(R.drawable.bg_pill_unselected);
            tab.setTextColor(getResources().getColor(R.color.text_grey, getTheme()));
        }
    }

    // "Start" buttons and the cards themselves open the workout logger
    private void setupRoutineButtons() {
        connectRoutine(R.id.btnStartChest, R.id.cardChestTriceps, WorkoutData.CHEST_TRICEPS);
        connectRoutine(R.id.btnStartBack, R.id.cardBackBiceps, WorkoutData.BACK_BICEPS);
        connectRoutine(R.id.btnStartLegs, R.id.cardLegsAbs, WorkoutData.LEGS_ABS);
    }

    // Helper: one routine = one Start button + one card
    private void connectRoutine(int buttonId, int cardId, final int routineIndex) {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSession(routineIndex, null);
            }
        };
        findViewById(buttonId).setOnClickListener(listener);
        findViewById(cardId).setOnClickListener(listener);
    }

    // RecyclerView of the "Explanation" tab
    private void setupExerciseList() {
        rvExercises.setLayoutManager(new LinearLayoutManager(this));
        ExerciseAdapter adapter = new ExerciseAdapter(WorkoutData.getAllExercises(), new ExerciseAdapter.OnExerciseClickListener() {
            @Override
            public void onStartClick(Exercise exercise) {
                // Workout with only this one exercise
                openSession(WorkoutData.findRoutineOf(exercise.getName()), exercise.getName());
            }

            @Override
            public void onVideoClick(Exercise exercise) {
                openVideo(exercise);
            }
        });
        rvExercises.setAdapter(adapter);
    }

    // Opens the workout logger (singleExercise = null → whole routine)
    private void openSession(int routineIndex, String singleExercise) {
        Intent intent = new Intent(this, WorkoutSessionActivity.class);
        intent.putExtra(WorkoutSessionActivity.EXTRA_ROUTINE, routineIndex);
        if (singleExercise != null) {
            intent.putExtra(WorkoutSessionActivity.EXTRA_SINGLE_EXERCISE, singleExercise);
        }
        startActivity(intent);
    }

    // Opens a YouTube search for the exercise form video (Implicit Intent)
    private void openVideo(Exercise exercise) {
        String query = exercise.getName().replace(" ", "+") + "+proper+form";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + query));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app to open the video", Toast.LENGTH_SHORT).show();
        }
    }
}
