package com.example.glumotion.workout;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.DiaryEntry;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Choose exercises for a routine (like Hevy):
 * - library of exercises (works offline)
 * - search in the free wger API
 * - create your own exercise (saved in Users / uid / CustomExercises → it is in the list every time)
 * Several exercises can be selected; "Add" returns them to the routine editor (ActivityResult).
 */
public class ExercisePickerActivity extends BaseActivity {

    public static final String RESULT_NAMES = "names";
    public static final String RESULT_MUSCLES = "muscles";

    public static final String[] MUSCLES = {"Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads",
            "Hamstrings", "Glutes", "Calves", "Abs", "Cardio", "Full Body", "Other"};

    // Rows shown on the screen (name + muscle) and which of them are selected
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<String> muscles = new ArrayList<>();
    private final ArrayList<String> selectedNames = new ArrayList<>();
    private final ArrayList<String> selectedMuscles = new ArrayList<>();

    // The user's own exercises (saved in Users / uid / CustomExercises) - shown first with ⭐
    private final ArrayList<String> myNames = new ArrayList<>();
    private final ArrayList<String> myMuscles = new ArrayList<>();

    private EditText etSearch;
    private TextView tvStatus, btnAdd;
    private ProgressBar pbPicker;
    private PickAdapter adapter;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_picker);
        setupBackBar("Add Exercises");

        btnAdd = setupToolbarAction("Add (0)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                returnSelected();
            }
        });

        etSearch = findViewById(R.id.etExerciseSearch);
        tvStatus = findViewById(R.id.tvPickerStatus);
        pbPicker = findViewById(R.id.pbPicker);

        RecyclerView rv = findViewById(R.id.rvPickExercises);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PickAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btnExerciseSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });
        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    search();
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.btnCreateCustomExercise).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCustomDialog();
            }
        });

        showLibrary("");
        loadMyExercises();
    }

    // Reads the exercises the user created before (Users / uid / CustomExercises)
    private void loadMyExercises() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("CustomExercises").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                myNames.clear();
                myMuscles.clear();
                for (DataSnapshot e : snapshot.getChildren()) {
                    String name = e.child("name").getValue(String.class);
                    String muscle = e.child("muscle").getValue(String.class);
                    if (name != null) {
                        myNames.add(name);
                        if (muscle == null) {
                            muscle = "Other";
                        }
                        myMuscles.add(muscle);
                    }
                }
                showLibrary(etSearch.getText().toString().trim());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Saves a new exercise of the user so it is in the list next time: Users / uid / CustomExercises / name
    private void saveMyExercise(String name, String muscle) {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("muscle", muscle);
        map.put("created", System.currentTimeMillis());
        userRef.child("CustomExercises").child(DiaryEntry.safeKey(name)).setValue(map);
        myNames.add(name);
        myMuscles.add(muscle);
    }

    // Shows my exercises (⭐) + the built-in library (filtered by text)
    private void showLibrary(String filter) {
        names.clear();
        muscles.clear();
        for (int i = 0; i < myNames.size(); i++) {
            if (filter.isEmpty() || myNames.get(i).toLowerCase().contains(filter.toLowerCase())) {
                names.add(myNames.get(i));
                muscles.add("⭐ My exercise • " + myMuscles.get(i));
            }
        }
        ArrayList<Exercise> library = WorkoutData.getLibrary();
        for (int i = 0; i < library.size(); i++) {
            Exercise ex = library.get(i);
            if (filter.isEmpty() || ex.getName().toLowerCase().contains(filter.toLowerCase())
                    || ex.getPrimaryMuscle().toLowerCase().contains(filter.toLowerCase())) {
                names.add(ex.getName());
                muscles.add(ex.getPrimaryMuscle());
            }
        }
        adapter.notifyDataSetChanged();
    }

    // Library results first, then results from wger
    private void search() {
        final String term = etSearch.getText().toString().trim();
        showLibrary(term);
        if (term.length() < 2) {
            tvStatus.setText("Exercise library");
            return;
        }
        pbPicker.setVisibility(View.VISIBLE);
        tvStatus.setText("Searching wger.de...");
        ExerciseApi.search(term, new ExerciseApi.ExerciseCallback() {
            @Override
            public void onResults(ArrayList<ExerciseApi.Result> results) {
                pbPicker.setVisibility(View.GONE);
                for (int i = 0; i < results.size(); i++) {
                    if (!containsIgnoreCase(names, results.get(i).name)) {
                        names.add(results.get(i).name);
                        muscles.add(results.get(i).category);
                    }
                }
                adapter.notifyDataSetChanged();
                tvStatus.setText(names.size() + " exercises for \"" + term + "\"");
            }

            @Override
            public void onError(String error) {
                pbPicker.setVisibility(View.GONE);
                tvStatus.setText("Offline - library only (" + names.size() + ")");
            }
        });
    }

    // Helper: list.contains without caring about big/small letters
    private boolean containsIgnoreCase(ArrayList<String> list, String text) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    // Selects / unselects one exercise
    private void toggle(int position) {
        String name = names.get(position);
        int index = selectedNames.indexOf(name);
        if (index >= 0) {
            selectedNames.remove(index);
            selectedMuscles.remove(index);
        } else {
            selectedNames.add(name);
            // "⭐ My exercise • Chest" → "Chest"
            String muscle = muscles.get(position);
            if (muscle.contains("• ")) {
                muscle = muscle.substring(muscle.indexOf("• ") + 2);
            }
            selectedMuscles.add(muscle);
        }
        btnAdd.setText("Add (" + selectedNames.size() + ")");
        adapter.notifyItemChanged(position);
    }

    // Dialog: name + muscle of a new exercise of the user
    private void showCustomDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_custom_exercise, null);
        final EditText etName = view.findViewById(R.id.etCustomExName);
        final Spinner spMuscle = view.findViewById(R.id.spCustomExMuscle);
        spMuscle.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MUSCLES));
        etName.setText(etSearch.getText().toString().trim());

        new AlertDialog.Builder(this)
                .setTitle("Create custom exercise")
                .setView(view)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = etName.getText().toString().trim();
                        if (name.length() < 2) {
                            toast("Write a name");
                            return;
                        }
                        String muscle = spMuscle.getSelectedItem().toString();
                        saveMyExercise(name, muscle);   // saved → it is in the list next time too
                        names.add(0, name);
                        muscles.add(0, "⭐ My exercise • " + muscle);
                        adapter.notifyDataSetChanged();
                        toggle(0);
                        toast(name + " saved in your exercises ✓");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Sends the selected exercises back to the routine editor
    private void returnSelected() {
        if (selectedNames.isEmpty()) {
            toast("Select at least one exercise");
            return;
        }
        Intent data = new Intent();
        data.putExtra(RESULT_NAMES, selectedNames.toArray(new String[0]));
        data.putExtra(RESULT_MUSCLES, selectedMuscles.toArray(new String[0]));
        setResult(RESULT_OK, data);
        finish();
    }

    // Adapter of the list (inner class - it uses the lists of the Activity)
    private class PickAdapter extends RecyclerView.Adapter<PickAdapter.PickHolder> {

        // Creates one row
        @NonNull
        @Override
        public PickHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exercise_pick, parent, false);
            return new PickHolder(view);
        }

        // Fills one row (green check if selected)
        @Override
        public void onBindViewHolder(@NonNull final PickHolder holder, int position) {
            holder.tvName.setText(names.get(position));
            holder.tvMuscle.setText(muscles.get(position));
            if (selectedNames.contains(names.get(position))) {
                holder.ivCheck.setImageResource(R.drawable.ic_check_circle_green);
            } else {
                holder.ivCheck.setImageResource(R.drawable.ic_check_circle_outline);
            }
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        toggle(pos);
                    }
                }
            });
        }

        // Number of rows
        @Override
        public int getItemCount() {
            return names.size();
        }

        // Views of one row
        class PickHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvMuscle;
            ImageView ivCheck;

            PickHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvPickName);
                tvMuscle = itemView.findViewById(R.id.tvPickMuscle);
                ivCheck = itemView.findViewById(R.id.ivPickCheck);
            }
        }
    }
}
