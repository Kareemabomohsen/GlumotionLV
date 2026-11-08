package com.example.glumotion.health;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.devices.HealthConnectHelper;
import com.example.glumotion.devices.HealthSync;
import com.example.glumotion.medications.MedicationsActivity;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class HealthActivity extends BaseActivity {

    // Steps variables
    private int currentSteps;
    private static final int STEPS_GOAL = 10000;

    // Water variables
    private int currentWater;
    private int WATER_GOAL = 2500; // changed from the user's water target (Users/uid/WaterIntake)

    // 🚶⌚ Automatic steps: the watch (Health Connect) + the phone's step sensor (see HealthSync)
    private HealthSync healthSync;

    // Sleep variables (default values: 11:15 PM and 6:45 AM)
    private int sleepHour = 23;
    private int sleepMinute = 15;
    private int wakeHour = 6;
    private int wakeMinute = 45;

    // Views
    private TextView tvStepsCount;
    private TextView tvStepsPercent;
    private TextView tvWaterCount;
    private TextView tvWaterPercent;
    private ProgressBar pbSteps;
    private View viewWaterLevel;

    // Sleep Views
    private TextView tvSleepTime;
    private TextView tvWakeTime;
    private TextView tvSleepDuration;
    private TextView tvSleepStatusText;
    private View llSleepStatusBanner;
    private ImageView ivSleepStatusIcon;
    private TextView badgePills;

    // Builds the health screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health);

        setupBottomNav(R.id.nav_health);
        setupTopBar();
        initViews();
        setupButtons();

        updateStepsUI();
        updateWaterUI();
        updateSleepUI();

        loadTodayFromFirebase();
        loadWaterGoal();

        // 🚶⌚ Steps from the watch / the phone sensor - they replace the +/- buttons
        healthSync = new HealthSync(this, new HealthSync.Listener() {
            @Override
            public void onSteps(int steps, String sourceText) {
                currentSteps = steps;
                updateStepsUI();
                ((TextView) findViewById(R.id.tvStepsSource)).setText(sourceText);
                findViewById(R.id.llStepsButtons).setVisibility(View.GONE);
            }

            @Override
            public void onHeartRate(int bpm, String text) {
            }
        });
        healthSync.start();
        updateConnectWatchButton();
    }

    // Back to the screen → read the watch again (its app syncs every few minutes)
    @Override
    protected void onResume() {
        super.onResume();
        if (healthSync != null) {
            healthSync.refresh();
            updateConnectWatchButton();
        }
    }

    // "⌚ Connect watch" (Android 14+) or "⌚ Watch connected" (opens Health Connect)
    private void updateConnectWatchButton() {
        TextView button = findViewById(R.id.btnConnectWatch);
        if (!HealthConnectHelper.isAvailable()) {
            button.setVisibility(View.GONE);
            return;
        }
        if (HealthConnectHelper.isConnected(this)) {
            button.setText("⌚ Connected");
        } else {
            button.setText("⌚ Connect watch");
        }
    }

    // Stops the step sensor when the screen closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        healthSync.stop();
    }

    // Water goal from the profile (liters → ml)
    private void loadWaterGoal() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("WaterIntake").child("waterTargetLiters").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object value = snapshot.getValue();
                if (value == null) {
                    return;
                }
                try {
                    WATER_GOAL = (int) (Double.parseDouble(value.toString()) * 1000);
                    TextView tvWaterGoal = findViewById(R.id.tvWaterGoal);
                    tvWaterGoal.setText("Goal: " + WATER_GOAL + " ml");
                    updateWaterUI();
                } catch (NumberFormatException e) {
                    // keep 2500
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Finds all views of the screen
    private void initViews() {
        tvStepsCount = findViewById(R.id.tvStepsCountText);
        tvStepsPercent = findViewById(R.id.tvStepsPercent);
        pbSteps = findViewById(R.id.pbSteps);

        tvWaterCount = findViewById(R.id.tvWaterCountText);
        tvWaterPercent = findViewById(R.id.tvWaterPercentText);
        viewWaterLevel = findViewById(R.id.viewWaterLevel);

        tvSleepTime = findViewById(R.id.tvSleepTime);
        tvWakeTime = findViewById(R.id.tvWakeTime);
        tvSleepDuration = findViewById(R.id.tvSleepDuration);
        tvSleepStatusText = findViewById(R.id.tvSleepStatusText);
        llSleepStatusBanner = findViewById(R.id.llSleepStatusBanner);
        ivSleepStatusIcon = findViewById(R.id.ivSleepStatusIcon);

        badgePills = findViewById(R.id.badgePills);
    }

    // Connects all the buttons (there was a bug: the pills badge had 2 different listeners)
    private void setupButtons() {
        // Pills badge → medications screen
        badgePills.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HealthActivity.this, MedicationsActivity.class));
            }
        });

        // ⌚ Connect watch → Health Connect permissions
        findViewById(R.id.btnConnectWatch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                healthSync.connectWatch();
            }
        });

        // Steps buttons (only for a phone without a step sensor and without a watch)
        findViewById(R.id.btnMinus1K).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeSteps(-1000);
            }
        });
        findViewById(R.id.btnPlus1K).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeSteps(1000);
            }
        });
        findViewById(R.id.btnPlus5K).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeSteps(5000);
            }
        });

        // Water buttons
        findViewById(R.id.btnMinus250ml).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeWater(-250);
            }
        });
        findViewById(R.id.btnPlus250ml).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeWater(250);
            }
        });
        findViewById(R.id.btnPlus500ml).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeWater(500);
            }
        });

        // Sleep / wake pickers
        tvSleepTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(true);
            }
        });
        tvWakeTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(false);
            }
        });
    }

    // Adds (or removes) steps and keeps the value between 0 and 2x goal
    private void changeSteps(int amount) {
        currentSteps = currentSteps + amount;
        if (currentSteps < 0) {
            currentSteps = 0;
        }
        if (currentSteps > STEPS_GOAL * 2) {
            currentSteps = STEPS_GOAL * 2;
        }
        updateStepsUI();
        saveTodayToFirebase();
    }

    // Adds (or removes) water and keeps the value between 0 and 2x goal
    private void changeWater(int amount) {
        currentWater = currentWater + amount;
        if (currentWater < 0) {
            currentWater = 0;
        }
        if (currentWater > WATER_GOAL * 2) {
            currentWater = WATER_GOAL * 2;
        }
        updateWaterUI();
        saveTodayToFirebase();
    }

    // Opens a clock dialog to choose the sleep time or the wake time
    private void showTimePickerDialog(final boolean isSleepTime) {
        int initialHour = wakeHour;
        int initialMinute = wakeMinute;
        if (isSleepTime) {
            initialHour = sleepHour;
            initialMinute = sleepMinute;
        }

        TimePickerDialog dialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                if (isSleepTime) {
                    sleepHour = hourOfDay;
                    sleepMinute = minute;
                } else {
                    wakeHour = hourOfDay;
                    wakeMinute = minute;
                }
                updateSleepUI();
                saveTodayToFirebase();
            }
        }, initialHour, initialMinute, false);
        dialog.show();
    }

    // Shows sleep times, duration and a colored message
    private void updateSleepUI() {
        tvSleepTime.setText(DateUtil.formatTime12(sleepHour, sleepMinute));
        tvWakeTime.setText(DateUtil.formatTime12(wakeHour, wakeMinute));

        int durationMinutes = calculateDurationInMinutes();
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        tvSleepDuration.setText(hours + "h " + DateUtil.twoDigits(minutes) + "m");

        if (hours >= 7 && hours <= 9) {
            showSleepBanner(R.string.sleep_status_good, R.color.purple_accent, R.drawable.ic_check, R.drawable.bg_sleep_banner);
        } else if (hours > 9) {
            showSleepBanner(R.string.sleep_status_long, R.color.purple_accent, R.drawable.ic_check, R.drawable.bg_sleep_banner);
        } else {
            showSleepBanner(R.string.sleep_status_short, R.color.red_accent, R.drawable.ic_clock, R.drawable.bg_sleep_banner_bad);
        }
    }

    // Helper: changes the sleep banner text, color, icon and background
    private void showSleepBanner(int textRes, int colorRes, int iconRes, int bgRes) {
        int color = ContextCompat.getColor(this, colorRes);
        tvSleepStatusText.setText(textRes);
        tvSleepStatusText.setTextColor(color);
        ivSleepStatusIcon.setImageResource(iconRes);
        ivSleepStatusIcon.setColorFilter(color);
        llSleepStatusBanner.setBackgroundResource(bgRes);
    }

    // Calculates minutes between sleep time and wake time (also over midnight)
    private int calculateDurationInMinutes() {
        int sleepTotalMinutes = sleepHour * 60 + sleepMinute;
        int wakeTotalMinutes = wakeHour * 60 + wakeMinute;

        if (wakeTotalMinutes >= sleepTotalMinutes) {
            return wakeTotalMinutes - sleepTotalMinutes;
        }
        return (24 * 60 - sleepTotalMinutes) + wakeTotalMinutes;
    }

    // Updates steps number, percent and progress bar
    private void updateStepsUI() {
        tvStepsCount.setText(String.valueOf(currentSteps));
        int percentage = (currentSteps * 100) / STEPS_GOAL;
        tvStepsPercent.setText(percentage + "% complete");
        pbSteps.setProgress(currentSteps);
    }

    // Updates water text and the height of the "water in the glass" view
    private void updateWaterUI() {
        tvWaterCount.setText(currentWater + " ml");
        int percentage = (currentWater * 100) / WATER_GOAL;
        tvWaterPercent.setText(percentage + "% of daily goal");

        // post() waits until the glass has its real size on the screen
        viewWaterLevel.post(new Runnable() {
            @Override
            public void run() {
                View container = findViewById(R.id.clGlassContainer);
                if (container == null) {
                    return;
                }
                int containerHeight = container.getHeight();

                float ratio = (float) currentWater / WATER_GOAL;
                if (ratio > 1.0f) {
                    ratio = 1.0f;
                }
                int newHeight = (int) (containerHeight * ratio);
                if (newHeight <= 0 && currentWater > 0) {
                    newHeight = 1;
                }

                ViewGroup.LayoutParams params = viewWaterLevel.getLayoutParams();
                if (params != null) {
                    params.height = newHeight;
                    viewWaterLevel.setLayoutParams(params);
                }

                if (currentWater <= 0) {
                    viewWaterLevel.setVisibility(View.GONE);
                } else {
                    viewWaterLevel.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    // Firebase path: Users/uid/DailyHealth/2026-11-02
    private DatabaseReference getTodayRef() {
        FirebaseUser user = FRef.getCurrentUser();
        if (user == null) {
            return null;
        }
        return FRef.getRefUsers().child(user.getUid()).child("DailyHealth").child(DateUtil.todayKey());
    }

    // Saves today's steps, water and sleep
    private void saveTodayToFirebase() {
        DatabaseReference ref = getTodayRef();
        if (ref == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("steps", currentSteps);
        data.put("waterMl", currentWater);
        data.put("sleepHour", sleepHour);
        data.put("sleepMinute", sleepMinute);
        data.put("wakeHour", wakeHour);
        data.put("wakeMinute", wakeMinute);
        ref.updateChildren(data);
    }

    // Loads today's saved values (so they don't reset when leaving the screen)
    private void loadTodayFromFirebase() {
        DatabaseReference ref = getTodayRef();
        if (ref == null) {
            return;
        }
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                currentSteps = Math.max(currentSteps, readInt(snapshot, "steps", 0));   // the automatic count may already be here
                currentWater = readInt(snapshot, "waterMl", currentWater);
                sleepHour = readInt(snapshot, "sleepHour", sleepHour);
                sleepMinute = readInt(snapshot, "sleepMinute", sleepMinute);
                wakeHour = readInt(snapshot, "wakeHour", wakeHour);
                wakeMinute = readInt(snapshot, "wakeMinute", wakeMinute);
                updateStepsUI();
                updateWaterUI();
                updateSleepUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Helper: reads a number from Firebase or returns the default value
    private int readInt(DataSnapshot snapshot, String key, int defaultValue) {
        Long value = snapshot.child(key).getValue(Long.class);
        if (value == null) {
            return defaultValue;
        }
        return value.intValue();
    }
}
