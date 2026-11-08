package com.example.glumotion.devices;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * ⌚ My Watch: the smart watch through Health Connect -
 * connected? which app? steps and heart rate now.
 */
public class DevicesActivity extends BaseActivity {

    private HealthSync healthSync;
    private TextView tvWatchStatus;
    private String watchSteps = "";
    private String watchHeart = "";

    // Builds the page
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);
        setupBackBar("⌚ My Watch");

        tvWatchStatus = findViewById(R.id.tvWatchStatus);

        setupWatch();
        setupButtons();
    }

    // ⌚ Steps + heart rate from the watch (the same HealthSync as Home / Health)
    private void setupWatch() {
        healthSync = new HealthSync(this, new HealthSync.Listener() {
            @Override
            public void onSteps(int steps, String sourceText) {
                watchSteps = "🚶 " + steps + " steps today • " + sourceText;
                showWatchStatus();
            }

            @Override
            public void onHeartRate(int bpm, String text) {
                if (bpm > 0) {
                    watchHeart = "❤ " + bpm + " bpm • " + text;
                } else {
                    watchHeart = "❤ No heart rate (the watch doesn't measure it, or it didn't sync yet)";
                }
                showWatchStatus();
            }
        });
        healthSync.start();
        showWatchStatus();
    }

    // The text of the watch card
    private void showWatchStatus() {
        Button connect = findViewById(R.id.btnWatchConnect);
        if (!HealthConnectHelper.isAvailable()) {
            tvWatchStatus.setText("Your phone has Android 13 or older - Health Connect needs Android 14.\n"
                    + "The steps are counted by the phone's step sensor.\n" + watchSteps);
            connect.setVisibility(View.GONE);
            return;
        }
        if (!HealthConnectHelper.isConnected(this)) {
            tvWatchStatus.setText("Not connected.\n1) Open your watch app (Samsung Health / Fitbit / Mi Fitness...) and turn on "
                    + "\"Health Connect\" in its settings.\n2) Press the button and allow Steps + Heart rate.");
            connect.setText("⌚ Connect watch (Health Connect)");
            return;
        }
        String text = "✅ Connected to Health Connect";
        if (!watchSteps.isEmpty()) {
            text = text + "\n" + watchSteps;
        }
        if (!watchHeart.isEmpty()) {
            text = text + "\n" + watchHeart;
        }
        tvWatchStatus.setText(text);
        connect.setText("⚙ Health Connect permissions");
    }

    // The button of the page
    private void setupButtons() {
        findViewById(R.id.btnWatchConnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                healthSync.connectWatch();
            }
        });
    }

    // Back to the page → read the newest steps / heart rate
    @Override
    protected void onResume() {
        super.onResume();
        healthSync.refresh();
    }

    // Stops the step sensor
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (healthSync != null) {
            healthSync.stop();
        }
    }
}
