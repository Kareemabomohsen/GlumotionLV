package com.example.glumotion.workout;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * 📅 Workout history: the days the user trained and how long.
 * Reads Users / uid / Workouts (saved by WorkoutSessionActivity when he presses Finish):
 * - summary: number of workouts, total time, days trained this month
 * - last 7 days: ✓ on the days he trained
 * - every workout (newest first): day, time, routine, minutes, sets, volume → tap = the exercises and sets
 */
public class WorkoutHistoryActivity extends BaseActivity {

    private static final long DAY = 24L * 60 * 60 * 1000;

    // Builds the page and reads the workouts once
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_history);
        setupBackBar("📅 Workout History");

        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            finish();
            return;
        }
        userRef.child("Workouts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<DataSnapshot> workouts = new ArrayList<>();
                for (DataSnapshot w : snapshot.getChildren()) {
                    workouts.add(w);
                }
                sortNewestFirst(workouts);
                showSummary(workouts);
                showWeek(workouts);
                showList(workouts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toast("Load failed: " + error.getMessage());
            }
        });
    }

    // Time of a workout (0 if missing)
    private long timeOf(DataSnapshot w) {
        Long time = w.child("timestamp").getValue(Long.class);
        if (time == null) {
            return 0;
        }
        return time;
    }

    // Minutes of a workout (0 if missing)
    private int minutesOf(DataSnapshot w) {
        Long minutes = w.child("durationMinutes").getValue(Long.class);
        if (minutes == null) {
            return 0;
        }
        return minutes.intValue();
    }

    // Selection sort: the newest workout first
    private void sortNewestFirst(ArrayList<DataSnapshot> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            int newest = i;
            for (int j = i + 1; j < list.size(); j++) {
                if (timeOf(list.get(j)) > timeOf(list.get(newest))) {
                    newest = j;
                }
            }
            DataSnapshot temp = list.get(i);
            list.set(i, list.get(newest));
            list.set(newest, temp);
        }
    }

    // Number of workouts, total time and days trained this month
    private void showSummary(ArrayList<DataSnapshot> workouts) {
        int totalMinutes = 0;
        ArrayList<String> monthDays = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < workouts.size(); i++) {
            DataSnapshot w = workouts.get(i);
            totalMinutes = totalMinutes + minutesOf(w);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timeOf(w));
            if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
                String key = DateUtil.dateKey(cal);
                if (!monthDays.contains(key)) {
                    monthDays.add(key);
                }
            }
        }
        ((TextView) findViewById(R.id.tvHistoryCount)).setText(workouts.size() + "\nworkouts");
        ((TextView) findViewById(R.id.tvHistoryTime)).setText((totalMinutes / 60) + "h " + (totalMinutes % 60) + "m\ntotal time");
        ((TextView) findViewById(R.id.tvHistoryMonth)).setText(monthDays.size() + "\ndays this month");
    }

    // 7 small boxes (6 days ago ... today): green ✓ = trained that day
    private void showWeek(ArrayList<DataSnapshot> workouts) {
        LinearLayout week = findViewById(R.id.llHistoryWeek);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        for (int d = 6; d >= 0; d--) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(System.currentTimeMillis() - d * DAY);
            int minutes = 0;
            boolean trained = false;
            for (int i = 0; i < workouts.size(); i++) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(timeOf(workouts.get(i)));
                if (DateUtil.sameDay(cal, day)) {
                    trained = true;
                    minutes = minutes + minutesOf(workouts.get(i));
                }
            }
            TextView box = new TextView(this);
            box.setGravity(android.view.Gravity.CENTER);
            box.setTextSize(12);
            box.setPadding(0, pad, 0, pad);
            if (trained) {
                box.setText(DateUtil.dayName(day) + "\n✓\n" + minutes + "m");
                box.setTextColor(0xFFFFFFFF);
                box.setBackgroundResource(R.drawable.bg_btn_gym);
            } else {
                box.setText(DateUtil.dayName(day) + "\n-\n ");
                box.setTextColor(0xFF6B7671);
                box.setBackgroundResource(R.drawable.bg_card);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(pad / 2, 0, pad / 2, 0);
            week.addView(box, params);
        }
    }

    // One row for every workout (newest first)
    private void showList(ArrayList<DataSnapshot> workouts) {
        LinearLayout list = findViewById(R.id.llHistoryList);
        View empty = findViewById(R.id.tvHistoryEmpty);
        if (workouts.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < workouts.size(); i++) {
            final DataSnapshot w = workouts.get(i);
            View row = inflater.inflate(R.layout.item_workout_history, list, false);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timeOf(w));

            String routine = w.child("routine").getValue(String.class);
            if (routine == null || routine.isEmpty()) {
                routine = "Workout";
            }
            Long sets = w.child("totalSets").getValue(Long.class);
            Double volume = w.child("totalVolumeKg").getValue(Double.class);
            long setsValue = 0;
            if (sets != null) {
                setsValue = sets;
            }
            double volumeValue = 0;
            if (volume != null) {
                volumeValue = volume;
            }

            ((TextView) row.findViewById(R.id.tvHistoryDay)).setText(DateUtil.dayName(cal) + "\n" + cal.get(Calendar.DAY_OF_MONTH));
            ((TextView) row.findViewById(R.id.tvHistoryRoutine)).setText(routine);
            ((TextView) row.findViewById(R.id.tvHistoryInfo)).setText(DateUtil.shortDate(cal) + ", " + cal.get(Calendar.YEAR) + " • "
                    + DateUtil.formatTime12(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
            ((TextView) row.findViewById(R.id.tvHistoryStats)).setText("⏱ " + minutesOf(w) + " min • " + setsValue + " sets • "
                    + Exercise.formatKg(volumeValue) + " kg");
            final String title = routine;
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDetails(title, w);
                }
            });
            list.addView(row);
        }
    }

    // The exercises and the sets of one workout
    private void showDetails(String title, DataSnapshot w) {
        String text = "";
        for (DataSnapshot ex : w.child("exercises").getChildren()) {
            text = text + "🏋 " + ex.child("name").getValue(String.class) + "\n";
            int number = 1;
            for (DataSnapshot set : ex.child("sets").getChildren()) {
                Double kg = set.child("kg").getValue(Double.class);
                Long reps = set.child("reps").getValue(Long.class);
                double kgValue = 0;
                if (kg != null) {
                    kgValue = kg;
                }
                long repsValue = 0;
                if (reps != null) {
                    repsValue = reps;
                }
                text = text + "   Set " + number + ": " + Exercise.formatKg(kgValue) + " kg × " + repsValue + "\n";
                number++;
            }
        }
        Long glucose = w.child("preWorkoutGlucose").getValue(Long.class);
        if (glucose != null) {
            text = text + "\n🩸 Sugar before: " + glucose + " mg/dL";
        }
        if (text.isEmpty()) {
            text = "No sets were saved.";
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text.trim())
                .setPositiveButton("OK", null)
                .show();
    }
}
