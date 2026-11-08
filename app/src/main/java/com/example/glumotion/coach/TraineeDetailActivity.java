package com.example.glumotion.coach;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.DiaryEntry;
import com.example.glumotion.food.FoodActivity;
import com.example.glumotion.glucose.GlucoseReading;
import com.example.glumotion.medications.MedicationModel;
import com.example.glumotion.workout.Exercise;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Coach → one trainee: ALL his tracking in one page (read only - the coach can't change anything).
 * Profile, blood sugar (last readings, 7-day average, time in range), food today,
 * workouts, medications today and steps / water / sleep. It updates live.
 */
public class TraineeDetailActivity extends BaseActivity {

    public static final String EXTRA_UID = "trainee_uid";
    public static final String EXTRA_NAME = "trainee_name";
    private static final long DAY = 24L * 60 * 60 * 1000;

    private TextView tvProfile, tvGlucose, tvFood, tvWorkouts, tvMeds, tvHealth;
    private DatabaseReference traineeRef;
    private ValueEventListener listener;
    private String phone = "", emergencyPhone = "";

    // Builds the page and listens to the trainee's data
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trainee_detail);

        String uid = getIntent().getStringExtra(EXTRA_UID);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null) {
            name = "Trainee";
        }
        setupBackBar(name);
        if (uid == null) {
            finish();
            return;
        }

        tvProfile = findViewById(R.id.tvDetailProfile);
        tvGlucose = findViewById(R.id.tvDetailGlucose);
        tvFood = findViewById(R.id.tvDetailFood);
        tvWorkouts = findViewById(R.id.tvDetailWorkouts);
        tvMeds = findViewById(R.id.tvDetailMeds);
        tvHealth = findViewById(R.id.tvDetailHealth);
        setupCallButtons();

        traineeRef = FRef.getRefUsers().child(uid);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                showProfile(s);
                showGlucose(s);
                showFood(s);
                showWorkouts(s);
                showMeds(s);
                showHealth(s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvProfile.setText("No access to this trainee's data.\nHe must accept your request (👤 menu → 🔔 Notifications).");
            }
        };
        traineeRef.addValueEventListener(listener);
    }

    // Stops listening when the page closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (traineeRef != null && listener != null) {
            traineeRef.removeEventListener(listener);
        }
    }

    // "Call" buttons open the phone app with the number (ACTION_DIAL - no permission needed)
    private void setupCallButtons() {
        findViewById(R.id.btnCallTrainee).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dial(phone);
            }
        });
        findViewById(R.id.btnCallEmergency).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dial(emergencyPhone);
            }
        });
    }

    // Opens the phone dialer with a number
    private void dial(String number) {
        if (number == null || number.trim().isEmpty()) {
            toast("No phone number saved");
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.trim())));
    }

    // Helper: any Firebase value as text ("-" if empty)
    private String text(DataSnapshot snap) {
        Object v = snap.getValue();
        if (v == null || v.toString().isEmpty()) {
            return "-";
        }
        return v.toString();
    }

    // 👤 Age, diabetes type, goal, weight, emergency contact
    private void showProfile(DataSnapshot s) {
        phone = s.child("phone").getValue(String.class);
        emergencyPhone = s.child("EmergencyContact").child("phoneNumber").getValue(String.class);
        tvProfile.setText("👤 Profile\n"
                + "Age " + text(s.child("age")) + " • " + text(s.child("gender")) + "\n"
                + "Diabetes: " + text(s.child("HealthConditions").child("diabetesType"))
                + " • measures with " + text(s.child("HealthConditions").child("monitoringMethod")) + "\n"
                + "Goal: " + text(s.child("Goals").child("goal")) + " • level " + text(s.child("Fitness").child("fitnessLevel")) + "\n"
                + "Weight " + text(s.child("Goals").child("weight")) + " kg • height " + text(s.child("Goals").child("height")) + " cm\n"
                + "🚑 Emergency: " + text(s.child("EmergencyContact").child("contactName")) + " " + text(s.child("EmergencyContact").child("phoneNumber")));
    }

    // 🩸 Last 8 readings + 7-day average + time in range
    private void showGlucose(DataSnapshot s) {
        ArrayList<GlucoseReading> list = new ArrayList<>();
        for (DataSnapshot r : s.child("GlucoseReadings").getChildren()) {
            GlucoseReading reading = r.getValue(GlucoseReading.class);
            if (reading != null && reading.getValue() > 0) {
                // insert sorted: newest first
                int pos = 0;
                while (pos < list.size() && list.get(pos).getTimestamp() > reading.getTimestamp()) {
                    pos++;
                }
                list.add(pos, reading);
            }
        }
        if (list.isEmpty()) {
            tvGlucose.setText("🩸 Blood sugar\nNo readings yet.");
            return;
        }
        long weekAgo = System.currentTimeMillis() - 7 * DAY;
        int sum = 0, count = 0, inRange = 0, lows = 0;
        for (int i = 0; i < list.size(); i++) {
            GlucoseReading r = list.get(i);
            if (r.getTimestamp() > weekAgo) {
                sum = sum + r.getValue();
                count++;
                if (GlucoseReading.isInRange(r.getValue())) {
                    inRange++;
                }
                if (r.getValue() < GlucoseReading.LOW_LIMIT) {
                    lows++;
                }
            }
        }
        String text = "🩸 Blood sugar\n";
        if (count > 0) {
            text = text + "7 days: average " + (sum / count) + " • in range " + (inRange * 100 / count) + "% • lows " + lows + "\n\n";
        }
        for (int i = 0; i < list.size() && i < 8; i++) {
            GlucoseReading r = list.get(i);
            text = text + "• " + DateUtil.shortDate(r.getCalendar()) + " " + r.getTimeText() + " - " + r.getValue()
                    + " (" + GlucoseReading.getStatus(r.getValue()) + ") " + r.getContext() + "\n";
        }
        tvGlucose.setText(text.trim());
        tvGlucose.setTextColor(getResources().getColor(R.color.text_dark, null));
        if (list.get(0).getValue() < GlucoseReading.LOW_LIMIT || list.get(0).getValue() > 250) {
            tvGlucose.setTextColor(0xFFD63031);   // last reading is dangerous → red card text
        }
    }

    // 🍽 What he ate today vs his targets
    private void showFood(DataSnapshot s) {
        int kcal = 0, carbs = 0, protein = 0, sugar = 0;
        String list = "";
        for (DataSnapshot e : s.child("FoodDiary").child(DateUtil.todayKey()).getChildren()) {
            DiaryEntry entry = e.getValue(DiaryEntry.class);
            if (entry != null) {
                kcal = kcal + entry.getKcal();
                carbs = carbs + entry.getCarbs();
                protein = protein + entry.getProtein();
                sugar = sugar + entry.getSugar();
                list = list + "• " + entry.getMeal() + ": " + entry.getName() + " " + Exercise.formatKg(entry.getGrams())
                        + "g - " + entry.getKcal() + " kcal\n";
            }
        }
        int target = FoodActivity.readNumber(s.child("Nutrition"), "targetCalories", 2000);
        int targetCarbs = FoodActivity.readNumber(s.child("Nutrition"), "carbs", 200);
        if (list.isEmpty()) {
            list = "Nothing logged today.";
        }
        tvFood.setText("🍽 Food today\n" + kcal + " / " + target + " kcal • carbs " + carbs + "/" + targetCarbs
                + "g • protein " + protein + "g • sugar " + sugar + "g\n\n" + list.trim());
    }

    // 💪 Workouts of the last 7 days + the last 5 workouts
    private void showWorkouts(DataSnapshot s) {
        ArrayList<DataSnapshot> list = new ArrayList<>();
        for (DataSnapshot w : s.child("Workouts").getChildren()) {
            list.add(0, w);   // newest first
        }
        long weekAgo = System.currentTimeMillis() - 7 * DAY;
        int week = 0;
        String text = "";
        for (int i = 0; i < list.size(); i++) {
            DataSnapshot w = list.get(i);
            Long time = w.child("timestamp").getValue(Long.class);
            if (time != null && time > weekAgo) {
                week++;
            }
            if (i < 5) {
                String pre = "";
                if (w.child("preWorkoutGlucose").exists()) {
                    pre = " • sugar before " + text(w.child("preWorkoutGlucose"));
                }
                text = text + "• " + text(w.child("date")) + " " + text(w.child("routine")) + " - "
                        + text(w.child("durationMinutes")) + " min, " + text(w.child("totalSets")) + " sets" + pre + "\n";
            }
        }
        if (list.isEmpty()) {
            text = "No workouts yet.";
        }
        tvWorkouts.setText("💪 Workouts (" + week + " this week)\n" + text.trim());
    }

    // 💊 Every medication with today's status
    private void showMeds(DataSnapshot s) {
        String text = "";
        for (DataSnapshot m : s.child("Medications").getChildren()) {
            MedicationModel med = m.getValue(MedicationModel.class);
            if (med != null && med.getName() != null) {
                String status = med.getTodayStatus();
                String icon = "⏳";
                if (status.equals("Taken")) {
                    icon = "✅";
                } else if (status.equals("Skipped")) {
                    icon = "❌";
                }
                text = text + icon + " " + med.getDisplayName() + " - " + med.getTime() + " (" + status + ")\n";
            }
        }
        if (text.isEmpty()) {
            text = "No medications.";
        }
        tvMeds.setText("💊 Medications today\n" + text.trim());
    }

    // 🚶 Steps, water and sleep of today
    private void showHealth(DataSnapshot s) {
        DataSnapshot day = s.child("DailyHealth").child(DateUtil.todayKey());
        String sleep = "-";
        Long sleepHour = day.child("sleepHour").getValue(Long.class);
        Long wakeHour = day.child("wakeHour").getValue(Long.class);
        if (sleepHour != null && wakeHour != null) {
            long hours = wakeHour - sleepHour;
            if (hours < 0) {
                hours = hours + 24;   // slept before midnight
            }
            sleep = "about " + hours + " h";
        }
        Calendar now = Calendar.getInstance();
        tvHealth.setText("🚶 Today (" + DateUtil.shortDate(now) + ")\n"
                + "Steps: " + text(day.child("steps")) + "\n"
                + "Water: " + text(day.child("waterMl")) + " ml\n"
                + "Sleep: " + sleep);
    }
}
