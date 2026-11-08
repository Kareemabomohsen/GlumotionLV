package com.example.glumotion.workout;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Rest timer between sets (CountDownTimer).
 * Opened by WorkoutSessionActivity with registerForActivityResult,
 * returns RESULT_OK when the rest is over or RESULT_CANCELED when the user skips.
 */
public class RestTimerActivity extends BaseActivity {

    public static final String EXTRA_EXERCISE = "exercise";
    public static final String EXTRA_COMING_UP = "coming_up";
    public static final String EXTRA_REST_SECONDS = "rest_seconds";
    public static final String EXTRA_NEXT_TITLE = "next_title";
    public static final String EXTRA_NEXT_DETAIL = "next_detail";
    public static final String EXTRA_NEXT_REST = "next_rest";

    private TextView tvTimerValue, tvTimerTotal;
    private ProgressBar pbRest;
    private ImageView btnTimerPause;

    private CountDownTimer timer;
    private long totalMillis;
    private long remainingMillis;
    private boolean running = false;

    // Builds the screen and starts the countdown
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rest_timer);

        setupBottomNav(R.id.nav_workout);
        setupTopBar();
        initViews();
        readExtras();
        setupButtons();
        startTimer();
    }

    // Finds the views
    private void initViews() {
        tvTimerValue = findViewById(R.id.tvTimerValue);
        tvTimerTotal = findViewById(R.id.tvTimerTotal);
        pbRest = findViewById(R.id.pbRest);
        btnTimerPause = findViewById(R.id.btnTimerPause);
    }

    // Reads the data sent from the workout screen
    private void readExtras() {
        int restSeconds = getIntent().getIntExtra(EXTRA_REST_SECONDS, 90);
        totalMillis = restSeconds * 1000L;
        remainingMillis = totalMillis;

        setTextFromExtra(R.id.tvTimerExercise, EXTRA_EXERCISE);
        setTextFromExtra(R.id.tvTimerComingUp, EXTRA_COMING_UP);
        setTextFromExtra(R.id.tvNextTitle, EXTRA_NEXT_TITLE);
        setTextFromExtra(R.id.tvNextDetail, EXTRA_NEXT_DETAIL);

        int nextRest = getIntent().getIntExtra(EXTRA_NEXT_REST, restSeconds);
        TextView tvNextRestTag = findViewById(R.id.tvNextRestTag);
        tvNextRestTag.setText(nextRest + "s rest");

        tvTimerTotal.setText(DateUtil.formatSeconds(restSeconds));
        updateTimerUI();
    }

    // Helper: puts an Intent extra inside a TextView (if it exists)
    private void setTextFromExtra(int textViewId, String key) {
        String value = getIntent().getStringExtra(key);
        if (value != null) {
            TextView tv = findViewById(textViewId);
            tv.setText(value);
        }
    }

    // Restart / Pause-Play / Skip
    private void setupButtons() {
        findViewById(R.id.btnTimerRestart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimer();
                remainingMillis = totalMillis;
                updateTimerUI();
                startTimer();
            }
        });

        btnTimerPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) {
                    stopTimer();
                } else {
                    startTimer();
                }
            }
        });

        findViewById(R.id.btnTimerSkip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimer();
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    // Starts (or continues) the CountDownTimer from the remaining time
    private void startTimer() {
        // Ticks every 100ms so the progress bar moves smoothly
        timer = new CountDownTimer(remainingMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateTimerUI();
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                running = false;
                updateTimerUI();
                onRestFinished();
            }
        };
        timer.start();
        running = true;
        btnTimerPause.setImageResource(R.drawable.ic_pause_white);
    }

    // Pauses the timer (keeps the remaining time)
    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
        running = false;
        btnTimerPause.setImageResource(R.drawable.ic_play_white);
    }

    // Shows "1:18" and moves the progress bar
    private void updateTimerUI() {
        // +999 = round up, so it shows 1:30 at the start and 0:01 in the last second
        int secondsLeft = (int) ((remainingMillis + 999) / 1000);
        tvTimerValue.setText(DateUtil.formatSeconds(secondsLeft));

        long passed = totalMillis - remainingMillis;
        int progress = 0;
        if (totalMillis > 0) {
            progress = (int) (passed * 1000 / totalMillis);
        }
        pbRest.setProgress(progress);
    }

    // Rest is over: sound + vibration, then back to the workout
    private void onRestFinished() {
        try {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(this, sound);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            // no sound - not important
        }

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        setResult(RESULT_OK);
        finish();
    }

    // Any tab of the bottom bar → back to the workout (the workout screen is behind this one)
    @Override
    protected void onBottomNavClick(Class<?> target) {
        stopTimer();
        setResult(RESULT_CANCELED);
        finish();
    }

    // Stops the timer when the screen is closed (no timer running in the background)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
