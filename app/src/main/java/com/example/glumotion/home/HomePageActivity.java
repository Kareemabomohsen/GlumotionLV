package com.example.glumotion.home;

import com.example.glumotion.R;
import com.example.glumotion.coach.CoachHelper;
import com.example.glumotion.coach.NotificationsActivity;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.devices.HealthSync;
import com.example.glumotion.food.DiaryEntry;
import com.example.glumotion.food.FoodActivity;
import com.example.glumotion.glucose.DMActivity;
import com.example.glumotion.glucose.GlucoseReading;
import com.example.glumotion.health.HealthActivity;
import com.example.glumotion.medications.MedicationModel;
import com.example.glumotion.medications.MedicationsActivity;
import com.example.glumotion.profile.User;
import com.example.glumotion.workout.WorkoutActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
 * Dashboard - everything here is personal: it is built from the user's own data in Firebase
 * (name, calorie target, food eaten today, last glucose, steps, workouts, water, sleep, medications).
 * It listens live, so any change in another screen shows here immediately.
 */
public class HomePageActivity extends BaseActivity {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private TextView tvGreeting, tvDate;
    private HealthSync healthSync;   // 🚶⌚ steps (watch / phone) + ❤ heart rate from the watch
    private TextView tvHomeGlucose, tvHomeGlucoseStatus, tvHomeGlucoseTime;
    private TextView tvEatenCalories, tvTotalCalories, tvProtein, tvCarbs, tvFats;
    private TextView tvStepsCount, tvWorkoutCount, tvWorkoutStatus, tvHydrationCount, tvSleepDuration, tvSleepQuality;
    private ProgressBar progressBarCalorie, progressBarSteps, progressBarWorkout, progressBarHydration, progressBarSleep;
    private LinearLayout llMedications;
    private TextView tvMedEmpty;

    private DatabaseReference userRef;
    private ValueEventListener userListener;

    // Builds the dashboard
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);

        setupBottomNav(R.id.nav_dashboard);
        setupTopBar();
        initViews();
        setupCardClicks();
        showGreeting("");
        listenToUser();
        checkCoachRequests();
        startHealthSync();
    }

    /**
     * Steps + heart rate. The steps are saved in DailyHealth (the steps card reads them from Firebase),
     * the heart rate is shown at the top left - only if the watch measures it.
     */
    private void startHealthSync() {
        healthSync = new HealthSync(this, new HealthSync.Listener() {
            @Override
            public void onSteps(int steps, String sourceText) {
                // nothing here: the steps card updates by itself from Firebase (listenToUser)
            }

            @Override
            public void onHeartRate(int bpm, String text) {
                TextView tvHeart = findViewById(R.id.tvHeartRate);
                if (bpm <= 0) {
                    tvHeart.setVisibility(View.GONE);
                    return;
                }
                tvHeart.setText("❤ Heart rate: " + bpm + " bpm  •  " + text);
                tvHeart.setVisibility(View.VISIBLE);
            }
        });
        healthSync.start();
    }

    // Back to Home → read the watch again
    @Override
    protected void onResume() {
        super.onResume();
        if (healthSync != null) {
            healthSync.refresh();
        }
    }

    // Stops the live listener when the screen closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (healthSync != null) {
            healthSync.stop();
        }
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
    }

    // Finds all the views
    private void initViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDate = findViewById(R.id.tvDate);
        tvHomeGlucose = findViewById(R.id.tvHomeGlucose);
        tvHomeGlucoseStatus = findViewById(R.id.tvHomeGlucoseStatus);
        tvHomeGlucoseTime = findViewById(R.id.tvHomeGlucoseTime);
        tvEatenCalories = findViewById(R.id.tvEatenCalories);
        tvTotalCalories = findViewById(R.id.tvTotalCalories);
        tvProtein = findViewById(R.id.tvProten);
        tvCarbs = findViewById(R.id.tvCarbs);
        tvFats = findViewById(R.id.tvFats);
        tvStepsCount = findViewById(R.id.tvStepsCount);
        tvWorkoutCount = findViewById(R.id.tvWorkoutCount);
        tvWorkoutStatus = findViewById(R.id.tvWorkoutStatus);
        tvHydrationCount = findViewById(R.id.tvHydrationCount);
        tvSleepDuration = findViewById(R.id.tvSleepDuration);
        tvSleepQuality = findViewById(R.id.tvSleepQuality);
        progressBarCalorie = findViewById(R.id.progressBarCalorie);
        progressBarSteps = findViewById(R.id.progressBarSteps);
        progressBarWorkout = findViewById(R.id.progressBarWorkout);
        progressBarHydration = findViewById(R.id.progressBarHydration);
        progressBarSleep = findViewById(R.id.progressBarSleep);
        llMedications = findViewById(R.id.llMedicationListContainer);
        tvMedEmpty = findViewById(R.id.tvMedEmptyPlaceholder);
        progressBarCalorie.setMax(100);
    }

    // Every card opens its screen
    private void setupCardClicks() {
        openOnClick(R.id.tvViewDiary, FoodActivity.class, true);
        openOnClick(R.id.cardHomeGlucose, DMActivity.class, true);
        openOnClick(R.id.cardSteps, HealthActivity.class, true);
        openOnClick(R.id.cardHydration, HealthActivity.class, true);
        openOnClick(R.id.cardSleep, HealthActivity.class, true);
        openOnClick(R.id.cardWorkout, WorkoutActivity.class, true);
        openOnClick(R.id.cardMedicationSection, MedicationsActivity.class, false);
    }

    // Helper: click on a view → open a screen (tab screens replace the dashboard)
    private void openOnClick(int viewId, final Class<?> screen, final boolean isTab) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isTab) {
                    navigateTo(screen);
                } else {
                    startActivity(new Intent(HomePageActivity.this, screen));
                }
            }
        });
    }

    // "Good Morning, Karim" + today's date
    private void showGreeting(String firstName) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello = "Good Evening";
        if (hour >= 5 && hour < 12) {
            hello = "Good Morning";
        } else if (hour >= 12 && hour < 18) {
            hello = "Good Afternoon";
        }
        if (firstName.isEmpty()) {
            tvGreeting.setText(hello);
        } else {
            tvGreeting.setText(hello + ", " + firstName);
        }
        Calendar now = Calendar.getInstance();
        tvDate.setText(DateUtil.dayName(now) + ", " + DateUtil.shortDate(now) + ", " + now.get(Calendar.YEAR));
    }

    // Live listener on Users / uid
    private void listenToUser() {
        userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                showAll(s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        userRef.addValueEventListener(userListener);
    }

    // Fills every card
    private void showAll(DataSnapshot s) {
        User user = s.getValue(User.class);
        if (user != null) {
            showGreeting(user.firstName());
        }
        String today = DateUtil.todayKey();

        showGlucose(s);
        showFood(s, today);
        showSteps(s, today);
        showWorkouts(s);
        showWater(s, today);
        showSleep(s, today);
        showMedications(s);
    }

    // Last glucose reading
    private void showGlucose(DataSnapshot s) {
        GlucoseReading last = null;
        for (DataSnapshot r : s.child("GlucoseReadings").getChildren()) {
            GlucoseReading reading = r.getValue(GlucoseReading.class);
            if (reading == null || reading.getValue() <= 0) {
                continue;
            }
            if (last == null || reading.getTimestamp() > last.getTimestamp()) {
                last = reading;
            }
        }
        if (last != null) {
            int value = last.getValue();
            tvHomeGlucose.setText(AppPrefs.formatGlucose(this, value));
            tvHomeGlucose.setTextColor(GlucoseReading.getStatusColor(value));
            tvHomeGlucoseStatus.setText(AppPrefs.getGlucoseUnit(this) + " • " + GlucoseReading.getStatus(value));
            tvHomeGlucoseStatus.setTextColor(GlucoseReading.getStatusColor(value));
            String when = DateUtil.shortDate(last.getCalendar());
            if (DateUtil.sameDay(last.getCalendar(), Calendar.getInstance())) {
                when = "Today";
            }
            tvHomeGlucoseTime.setText(when + ", " + last.getTimeText() + " • " + last.getContext());
        }
    }

    // Calories + macros eaten today vs the user's targets
    private void showFood(DataSnapshot s, String today) {
        int target = FoodActivity.readNumber(s.child("Nutrition"), "targetCalories", 2000);
        int kcal = 0, protein = 0, carbs = 0, fat = 0;
        for (DataSnapshot e : s.child("FoodDiary").child(today).getChildren()) {
            DiaryEntry entry = e.getValue(DiaryEntry.class);
            if (entry != null) {
                kcal = kcal + entry.getKcal();
                protein = protein + entry.getProtein();
                carbs = carbs + entry.getCarbs();
                fat = fat + entry.getFat();
            }
        }
        tvEatenCalories.setText(kcal + " eaten");
        tvTotalCalories.setText(target + " goal");
        progressBarCalorie.setProgress(Math.min(100, kcal * 100 / Math.max(target, 1)));
        tvProtein.setText(protein + "g");
        tvCarbs.setText(carbs + "g");
        tvFats.setText(fat + "g");
    }

    // Steps today (from the Health screen / step sensor)
    private void showSteps(DataSnapshot s, String today) {
        Long steps = s.child("DailyHealth").child(today).child("steps").getValue(Long.class);
        int value = 0;
        if (steps != null) {
            value = steps.intValue();
        }
        tvStepsCount.setText(String.valueOf(value));
        int percent = Math.min(100, value * 100 / 10000);
        progressBarSteps.setProgress(percent);
    }

    // Workouts in the last 7 days vs weekly goal (by fitness level)
    private void showWorkouts(DataSnapshot s) {
        String level = s.child("Fitness").child("fitnessLevel").getValue(String.class);
        int goal = 3;
        if ("Intermediate".equals(level)) {
            goal = 4;
        } else if ("Advanced".equals(level)) {
            goal = 5;
        }
        long weekAgo = System.currentTimeMillis() - 7 * DAY;
        int count = 0;
        for (DataSnapshot w : s.child("Workouts").getChildren()) {
            Long time = w.child("timestamp").getValue(Long.class);
            if (time != null && time >= weekAgo) {
                count++;
            }
        }
        tvWorkoutCount.setText(count + "/" + goal);
        tvWorkoutStatus.setText("this week");
        int percent = Math.min(100, count * 100 / goal);
        progressBarWorkout.setProgress(percent);
    }

    // Water today in glasses (250 ml) vs the user's target
    private void showWater(DataSnapshot s, String today) {
        Long water = s.child("DailyHealth").child(today).child("waterMl").getValue(Long.class);
        int ml = 0;
        if (water != null) {
            ml = water.intValue();
        }
        int goalMl = 2500;
        Object target = s.child("WaterIntake").child("waterTargetLiters").getValue();
        if (target != null) {
            try {
                goalMl = (int) (Double.parseDouble(target.toString()) * 1000);
            } catch (NumberFormatException e) {
                goalMl = 2500;
            }
        }
        tvHydrationCount.setText((ml / 250) + "/" + (goalMl / 250));
        progressBarHydration.setProgress(Math.min(100, ml * 100 / Math.max(goalMl, 1)));
    }

    // Sleep of last night (saved by the Health screen)
    private void showSleep(DataSnapshot s, String today) {
        DataSnapshot day = s.child("DailyHealth").child(today);
        Long sh = day.child("sleepHour").getValue(Long.class);
        Long sm = day.child("sleepMinute").getValue(Long.class);
        Long wh = day.child("wakeHour").getValue(Long.class);
        Long wm = day.child("wakeMinute").getValue(Long.class);
        if (sh == null || sm == null || wh == null || wm == null) {
            tvSleepDuration.setText("-");
            tvSleepQuality.setText("log it in Health");
            progressBarSleep.setProgress(0);
            return;
        }
        int sleep = sh.intValue() * 60 + sm.intValue();
        int wake = wh.intValue() * 60 + wm.intValue();
        int minutes = wake - sleep;
        if (minutes < 0) {
            minutes = minutes + 24 * 60;
        }
        tvSleepDuration.setText((minutes / 60) + "h " + DateUtil.twoDigits(minutes % 60) + "m");
        if (minutes >= 7 * 60 && minutes <= 9 * 60) {
            tvSleepQuality.setText("quality: good");
        } else if (minutes < 7 * 60) {
            tvSleepQuality.setText("quality: short");
        } else {
            tvSleepQuality.setText("quality: long");
        }
        progressBarSleep.setProgress(Math.min(100, minutes * 100 / (8 * 60)));
    }

    // Today's medications (tap = Taken / Pending)
    private void showMedications(DataSnapshot s) {
        llMedications.removeAllViews();
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        int count = 0;
        for (DataSnapshot m : s.child("Medications").getChildren()) {
            final MedicationModel med = m.getValue(MedicationModel.class);
            if (med == null || med.getName() == null) {
                continue;
            }
            med.setId(m.getKey());
            count++;

            final boolean taken = "Taken".equals(med.getTodayStatus());
            TextView row = new TextView(this);
            String mark = "◯";
            if (taken) {
                mark = "✓";
            }
            row.setText(mark + "  " + med.getTime() + "  •  " + med.getDisplayName());
            row.setTextSize(14);
            row.setPadding(pad, pad, pad, pad);
            if (taken) {
                row.setTextColor(0xFF27A765);
            } else {
                row.setTextColor(0xFF1D2623);
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (taken) {
                        FRef.updateMedicationStatus(med.getId(), "Pending");
                    } else {
                        FRef.updateMedicationStatus(med.getId(), "Taken");
                    }
                }
            });
            llMedications.addView(row);
        }
        if (count == 0) {
            tvMedEmpty.setVisibility(View.VISIBLE);
            llMedications.addView(tvMedEmpty);
        }
    }
    // Coach requests already shown on this run of the app (so the popup isn't shown again and again)
    private static final ArrayList<String> shownRequests = new ArrayList<>();

    /**
     * A coach asked to follow me (CoachRequests / my_email_key) → popup here.
     * Accept / Decline, or "Later" → the request stays in 👤 menu → 🔔 Notifications.
     */
    private void checkCoachRequests() {
        DatabaseReference requestsRef = CoachHelper.getRefMyRequests();
        if (requestsRef == null) {
            return;
        }
        requestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot r : snapshot.getChildren()) {
                    String coachUid = r.getKey();
                    if (coachUid != null && !shownRequests.contains(coachUid)) {
                        shownRequests.add(coachUid);
                        showRequestPopup(coachUid, r.child("name").getValue(String.class),
                                r.child("email").getValue(String.class), r.child("phone").getValue(String.class));
                        return;   // one popup at a time
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // The popup of one coach request
    private void showRequestPopup(final String coachUid, final String coachName, final String coachEmail, final String coachPhone) {
        new AlertDialog.Builder(this)
                .setTitle("👥 " + coachName + " wants to be your coach")
                .setMessage(coachEmail + "\n\nIf you accept, he can SEE your sugar, food, workouts and medications "
                        + "(not change them) and gets an alert if your sugar is dangerous during training.\n"
                        + "You can stop sharing any time: 👤 menu → 🔔 Notifications.")
                .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        NotificationsActivity.accept(HomePageActivity.this, coachUid, coachName, coachEmail, coachPhone);
                    }
                })
                .setNegativeButton("Decline", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CoachHelper.declineRequest(coachUid);
                    }
                })
                .setNeutralButton("Later", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toast("It waits for you in 👤 menu → 🔔 Notifications");
                    }
                })
                .show();
    }
}
