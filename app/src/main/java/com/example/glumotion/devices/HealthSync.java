package com.example.glumotion.devices;

import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;

import android.Manifest;
import android.text.format.DateUtils;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.Map;

/**
 * 🚶⌚ One place for the steps and the heart rate (used by Home and Health).
 *
 * Two sources - and they are NEVER added together:
 * 1) The WATCH → its app on the phone (Samsung Health / Fitbit / Mi Fitness...) → Health Connect.
 *    Health Connect already joins the watch and the phone apps into ONE total (no double steps).
 * 2) The PHONE's own step sensor (StepCounterHelper) - for a user without a watch.
 * Steps of today = the bigger of the two (both measure the same walking, so we never add them).
 * The result is saved in Users / uid / DailyHealth / today / steps (Home, coach and charts read it).
 */
public class HealthSync {

    // The screen gets the numbers through this interface
    public interface Listener {
        void onSteps(int steps, String sourceText);

        void onHeartRate(int bpm, String text);
    }

    private final AppCompatActivity activity;
    private final Listener listener;
    private final StepCounterHelper phoneCounter;
    private final ActivityResultLauncher<String[]> healthPermissionLauncher;

    private int phoneSteps = -1;
    private long watchSteps = -1;
    private String watchSource = "";
    private int savedSteps = -100;   // last value written to Firebase (we write every 20 steps, not every step)

    // Must be created in onCreate (because of registerForActivityResult)
    public HealthSync(AppCompatActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.phoneCounter = new StepCounterHelper(activity, new StepCounterHelper.StepListener() {
            @Override
            public void onSteps(int stepsToday) {
                phoneSteps = stepsToday;
                publishSteps();
            }
        });
        // ONE permission window for everything (two windows at the same time don't work on Android):
        // "Physical activity" for the phone sensor + the Health Connect permissions (Android 14+)
        this.healthPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                new ActivityResultCallback<Map<String, Boolean>>() {
                    @Override
                    public void onActivityResult(Map<String, Boolean> result) {
                        phoneCounter.startIfAllowed();
                        refresh();
                    }
                });
    }

    // Starts the phone sensor and reads the watch (asks the missing permissions in ONE window)
    public void start() {
        ArrayList<String> ask = new ArrayList<>();
        if (phoneCounter.needsPermission()) {
            ask.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (HealthConnectHelper.isAvailable() && !HealthConnectHelper.isConnected(activity)
                && !AppPrefs.askedHealthConnect(activity)) {
            AppPrefs.setAskedHealthConnect(activity);   // the watch permissions are asked only once (later: the button)
            for (int i = 0; i < HealthConnectHelper.PERMISSIONS.length; i++) {
                ask.add(HealthConnectHelper.PERMISSIONS[i]);
            }
        }
        if (!ask.isEmpty()) {
            healthPermissionLauncher.launch(ask.toArray(new String[0]));
            return;
        }
        phoneCounter.startIfAllowed();
        refresh();
    }

    // "⌚ Connect watch" button: asks the permissions again (or opens Health Connect if he said no before)
    public void connectWatch() {
        if (!HealthConnectHelper.isAvailable()) {
            return;
        }
        if (HealthConnectHelper.isConnected(activity)) {
            HealthConnectHelper.openSettings(activity);
        } else {
            healthPermissionLauncher.launch(HealthConnectHelper.PERMISSIONS);
        }
    }

    // Reads the watch again (call in onResume - the watch app syncs every few minutes)
    public void refresh() {
        HealthConnectHelper.readStepsToday(activity, new HealthConnectHelper.StepsCallback() {
            @Override
            public void onSteps(long steps, String source) {
                watchSteps = steps;
                watchSource = source;
                publishSteps();
            }
        });
        HealthConnectHelper.readLatestHeartRate(activity, new HealthConnectHelper.HeartRateCallback() {
            @Override
            public void onHeartRate(long bpm, long timeMillis, String source) {
                if (bpm <= 0) {
                    listener.onHeartRate(-1, "");
                    return;
                }
                String ago = DateUtils.getRelativeTimeSpanString(timeMillis, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS).toString();
                listener.onHeartRate((int) bpm, ago + " • " + source);
            }
        });
    }

    // Stops the phone sensor (onDestroy)
    public void stop() {
        phoneCounter.stop();
    }

    // true if the phone counts steps by itself or a watch is connected (= no need for the +/- buttons)
    public boolean isAutomatic() {
        return phoneCounter.hasSensor() || HealthConnectHelper.isConnected(activity);
    }

    // The bigger of watch / phone (never added) → screen + Firebase
    private void publishSteps() {
        if (phoneSteps < 0 && watchSteps < 0) {
            return;
        }
        int steps;
        String source;
        if (watchSteps >= phoneSteps) {
            steps = (int) watchSteps;
            source = "⌚ Watch / Health Connect (" + watchSource + ")";
        } else {
            steps = phoneSteps;
            source = "📱 Phone step sensor";
        }
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef != null && Math.abs(steps - savedSteps) >= 20) {
            savedSteps = steps;
            userRef.child("DailyHealth").child(DateUtil.todayKey()).child("steps").setValue(steps);
        }
        listener.onSteps(steps, source);
    }
}
