package com.example.glumotion.profile;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.NutritionCalculator;
import com.example.glumotion.workout.Exercise;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * One page for 3 profile sections (the "mode" comes in the Intent):
 * - Exercise & Weight: weight history + workout totals (+ add weight)
 * - Achievements: badges calculated from the user's data
 * - History Overview: summary of the last 14 days
 */
public class ProfileListActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_WEIGHT = "weight";
    public static final String MODE_ACHIEVEMENTS = "achievements";
    public static final String MODE_HISTORY = "history";

    private static final long DAY = 24L * 60 * 60 * 1000;

    private String mode;
    private TextView tvHeader;
    private final ArrayList<String[]> rows = new ArrayList<>();
    private ProfileRowAdapter adapter;
    private DatabaseReference userRef;
    private DataSnapshot data;

    // Builds the page by mode
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_list);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) {
            mode = MODE_HISTORY;
        }
        if (mode.equals(MODE_WEIGHT)) {
            setupBackBar("Exercise & Weight");
            setupToolbarAction("+ Weight", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addWeightDialog();
                }
            });
        } else if (mode.equals(MODE_ACHIEVEMENTS)) {
            setupBackBar("Achievements");
        } else {
            setupBackBar("History Overview");
        }

        tvHeader = findViewById(R.id.tvListHeader);
        RecyclerView rv = findViewById(R.id.rvProfileList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProfileRowAdapter(rows);
        rv.setAdapter(adapter);
        loadData();
    }

    // Reads the whole user once (and again after adding a weight)
    private void loadData() {
        userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                data = snapshot;
                rows.clear();
                if (mode.equals(MODE_WEIGHT)) {
                    buildWeight();
                } else if (mode.equals(MODE_ACHIEVEMENTS)) {
                    buildAchievements();
                } else {
                    buildHistory();
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvHeader.setText("Load failed");
            }
        });
    }

    // ----------------------------- Exercise & Weight -----------------------------

    // Weight list (newest first, with the change) + workout totals
    private void buildWeight() {
        int workouts = 0;
        double volume = 0;
        int sets = 0;
        for (DataSnapshot w : data.child("Workouts").getChildren()) {
            workouts++;
            Double v = w.child("totalVolumeKg").getValue(Double.class);
            Long s = w.child("totalSets").getValue(Long.class);
            if (v != null) {
                volume = volume + v;
            }
            if (s != null) {
                sets = sets + s.intValue();
            }
        }
        String weight = textOf(data.child("Goals").child("weight"), "-");
        String height = textOf(data.child("Goals").child("height"), "0");
        double bmi = 0;
        try {
            bmi = NutritionCalculator.bmi(Double.parseDouble(weight), Double.parseDouble(height));
        } catch (NumberFormatException e) {
            bmi = 0;
        }
        tvHeader.setText("⚖ Current weight: " + weight + " kg • BMI " + bmi + " (" + NutritionCalculator.bmiCategory(bmi) + ")\n"
                + "🏋 Workouts: " + workouts + " • Sets: " + sets + "\n"
                + "📦 Total volume lifted: " + Exercise.formatKg(Math.round(volume)) + " kg");

        // Read the weights into arrays, then show newest first
        ArrayList<Double> kgs = new ArrayList<>();
        ArrayList<Long> times = new ArrayList<>();
        for (DataSnapshot w : data.child("WeightHistory").getChildren()) {
            Double kg = w.child("kg").getValue(Double.class);
            Long t = w.child("timestamp").getValue(Long.class);
            if (kg != null && t != null) {
                kgs.add(kg);
                times.add(t);
            }
        }
        for (int i = kgs.size() - 1; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(times.get(i));
            String change = "";
            if (i > 0) {
                double diff = Math.round((kgs.get(i) - kgs.get(i - 1)) * 10) / 10.0;
                if (diff > 0) {
                    change = "+" + diff + " kg";
                } else {
                    change = diff + " kg";
                }
            }
            rows.add(new String[]{"⚖", Exercise.formatKg(kgs.get(i)) + " kg", DateUtil.dayName(c) + ", " + DateUtil.shortDate(c), change});
        }
        if (rows.isEmpty()) {
            rows.add(new String[]{"➕", "No weight history yet", "Tap \"+ Weight\" to add today's weight", ""});
        }
    }

    // Dialog to add today's weight (also updates the calories)
    private void addWeightDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Weight in kg");
        LinearLayout box = new LinearLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Today's weight")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        double kg;
                        try {
                            kg = Double.parseDouble(input.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            toast("Enter a number");
                            return;
                        }
                        if (kg < 30 || kg > 300) {
                            toast("Weight must be 30-300 kg");
                            return;
                        }
                        saveWeight(kg);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Saves the weight + new calories in one request, then reloads the list
    private void saveWeight(double kg) {
        if (userRef == null || data == null) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        String key = userRef.child("WeightHistory").push().getKey();
        Map<String, Object> entry = new HashMap<>();
        entry.put("kg", kg);
        entry.put("timestamp", System.currentTimeMillis());
        updates.put("WeightHistory/" + key, entry);
        updates.put("Goals/weight", Exercise.formatKg(kg));

        try {
            double height = Double.parseDouble(textOf(data.child("Goals").child("height"), "0"));
            Long age = data.child("age").getValue(Long.class);
            int ageValue = 25;
            if (age != null) {
                ageValue = age.intValue();
            }
            if (height > 0) {
                int[] nutrition = NutritionCalculator.calculate(kg, height, ageValue,
                        textOf(data.child("gender"), "Male"), textOf(data.child("Goals").child("goal"), "Maintaining"),
                        textOf(data.child("Fitness").child("fitnessLevel"), "Beginner"));
                updates.put("Nutrition", NutritionCalculator.toMap(nutrition));
            }
        } catch (NumberFormatException e) {
            // no height - only the weight is saved
        }
        userRef.updateChildren(updates);
        toast("Weight saved");
        loadData();
    }

    // ----------------------------- Achievements -----------------------------

    // Badges calculated from the data (✅ = unlocked, otherwise progress)
    private void buildAchievements() {
        long now = System.currentTimeMillis();

        int workouts = (int) data.child("Workouts").getChildrenCount();
        int weekWorkouts = 0;
        for (DataSnapshot w : data.child("Workouts").getChildren()) {
            Long t = w.child("timestamp").getValue(Long.class);
            if (t != null && t > now - 7 * DAY) {
                weekWorkouts++;
            }
        }
        int readings = (int) data.child("GlucoseReadings").getChildrenCount();
        int streak = glucoseStreak();
        int foodDays = (int) data.child("FoodDiary").getChildrenCount();
        int routines = (int) data.child("Routines").getChildrenCount();

        int maxSteps = 0;
        int maxWater = 0;
        for (DataSnapshot d : data.child("DailyHealth").getChildren()) {
            Long steps = d.child("steps").getValue(Long.class);
            Long water = d.child("waterMl").getValue(Long.class);
            if (steps != null && steps > maxSteps) {
                maxSteps = steps.intValue();
            }
            if (water != null && water > maxWater) {
                maxWater = water.intValue();
            }
        }

        int unlocked = 0;
        unlocked = unlocked + badge("🏋", "First Workout", "Finish your first workout", workouts, 1);
        unlocked = unlocked + badge("💪", "Gym Regular", "Finish 10 workouts", workouts, 10);
        unlocked = unlocked + badge("🔥", "On Fire", "3 workouts in one week", weekWorkouts, 3);
        unlocked = unlocked + badge("📝", "Planner", "Create your own routine", routines, 1);
        unlocked = unlocked + badge("🩸", "First Check", "Log your first glucose reading", readings, 1);
        unlocked = unlocked + badge("📅", "7-Day Streak", "Log glucose 7 days in a row", streak, 7);
        unlocked = unlocked + badge("🥗", "Food Logger", "Log food on 7 different days", foodDays, 7);
        unlocked = unlocked + badge("🚶", "10K Steps", "Walk 10,000 steps in one day", maxSteps, 10000);
        unlocked = unlocked + badge("💧", "Hydrated", "Drink 2,500 ml in one day", maxWater, 2500);
        tvHeader.setText("🏆 You unlocked " + unlocked + " of 9 achievements");
    }

    // Adds one badge row and returns 1 if unlocked
    private int badge(String icon, String title, String description, int value, int goal) {
        if (value >= goal) {
            rows.add(new String[]{icon, title, description, "✅"});
            return 1;
        }
        rows.add(new String[]{"🔒", title, description, value + "/" + goal});
        return 0;
    }

    // How many days in a row (until today/yesterday) have a glucose reading
    private int glucoseStreak() {
        ArrayList<String> days = new ArrayList<>();
        for (DataSnapshot r : data.child("GlucoseReadings").getChildren()) {
            String date = r.child("date").getValue(String.class);
            if (date != null && !days.contains(date)) {
                days.add(date);
            }
        }
        Calendar day = Calendar.getInstance();
        if (!days.contains(DateUtil.dateKey(day))) {
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        int streak = 0;
        while (days.contains(DateUtil.dateKey(day))) {
            streak++;
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    // ----------------------------- History -----------------------------

    // Last 14 days: calories, glucose average, steps, workout
    private void buildHistory() {
        // Glucose average per day (HashMap: day → {sum, count})
        HashMap<String, int[]> glucose = new HashMap<>();
        for (DataSnapshot r : data.child("GlucoseReadings").getChildren()) {
            String date = r.child("date").getValue(String.class);
            Long value = r.child("value").getValue(Long.class);
            if (date == null || value == null) {
                continue;
            }
            int[] sc = glucose.get(date);
            if (sc == null) {
                sc = new int[2];
                glucose.put(date, sc);
            }
            sc[0] = sc[0] + value.intValue();
            sc[1]++;
        }
        ArrayList<String> workoutDays = new ArrayList<>();
        for (DataSnapshot w : data.child("Workouts").getChildren()) {
            String date = w.child("date").getValue(String.class);
            if (date != null) {
                workoutDays.add(date);
            }
        }

        int totalKcal = 0;
        int foodDays = 0;
        int totalWorkouts = 0;
        Calendar day = Calendar.getInstance();
        for (int i = 0; i < 14; i++) {
            String key = DateUtil.dateKey(day);

            int kcal = 0;
            for (DataSnapshot e : data.child("FoodDiary").child(key).getChildren()) {
                Long k = e.child("kcal").getValue(Long.class);
                if (k != null) {
                    kcal = kcal + k.intValue();
                }
            }
            Long steps = data.child("DailyHealth").child(key).child("steps").getValue(Long.class);
            int[] g = glucose.get(key);

            String sub = "🍽 " + kcal + " kcal";
            if (g != null) {
                sub = sub + "  •  🩸 avg " + (g[0] / g[1]);
            }
            if (steps != null) {
                sub = sub + "  •  🚶 " + steps;
            }
            String value = "";
            if (workoutDays.contains(key)) {
                value = "💪";
                totalWorkouts++;
            }
            if (kcal > 0) {
                totalKcal = totalKcal + kcal;
                foodDays++;
            }
            String title = DateUtil.dayName(day) + ", " + DateUtil.shortDate(day);
            if (i == 0) {
                title = "Today";
            }
            rows.add(new String[]{"📅", title, sub, value});
            day.add(Calendar.DAY_OF_YEAR, -1);
        }

        String avgKcal = "-";
        if (foodDays > 0) {
            avgKcal = String.valueOf(totalKcal / foodDays);
        }
        tvHeader.setText("Last 14 days\n🍽 Average: " + avgKcal + " kcal/day\n💪 Workouts: " + totalWorkouts);
    }

    // Helper: any Firebase value as text
    private String textOf(DataSnapshot snap, String defaultValue) {
        Object value = snap.getValue();
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
}
