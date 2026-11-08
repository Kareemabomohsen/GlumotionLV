package com.example.glumotion.devices;

import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.DateUtil;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * 🚶 Counts the steps automatically with the phone's step sensor (TYPE_STEP_COUNTER).
 * The sensor gives "all steps since the phone was turned on" and keeps counting even when the app is closed.
 * We save the last value we counted (SharedPreferences): new steps = now − last value.
 * - new day → today starts from 0
 * - the phone restarted (the sensor went back to 0) → we add the new value
 * Used by HealthSync together with the watch (Health Connect).
 * ملاحظة: الإيميوليتر ما فيه حساس خطوات → جرّبه على موبايل حقيقي.
 */
public class StepCounterHelper implements SensorEventListener {

    // The screen gets the new number of steps through this interface
    public interface StepListener {
        void onSteps(int stepsToday);
    }

    private final AppCompatActivity activity;
    private final StepListener listener;
    private final SensorManager sensorManager;
    private final Sensor stepSensor;
    private final ActivityResultLauncher<String> permissionLauncher;

    // Must be created in onCreate (because of registerForActivityResult)
    public StepCounterHelper(AppCompatActivity activity, StepListener listener) {
        this.activity = activity;
        this.listener = listener;
        this.sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            this.stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        } else {
            this.stepSensor = null;
        }
        // Runtime permission (Android 10+): "Physical activity"
        this.permissionLauncher = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        if (granted) {
                            register();
                        }
                    }
                });
    }

    // Starts counting (asks for the permission if needed)
    public void start() {
        if (stepSensor == null) {
            return;   // no step sensor (emulator / old phone) → the watch or the buttons
        }
        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
            return;
        }
        register();
    }

    // true if the "Physical activity" permission is needed and not given yet (Android 10+)
    public boolean needsPermission() {
        return stepSensor != null && Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED;
    }

    // Starts counting only if the permission is already given (never opens a permission window)
    public void startIfAllowed() {
        if (stepSensor != null && !needsPermission()) {
            register();
        }
    }

    // true if the phone has a step sensor
    public boolean hasSensor() {
        return stepSensor != null;
    }

    // Stops counting (call in onDestroy to save battery)
    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    // Connects this class to the sensor
    private void register() {
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
    }

    // Called by Android every time the sensor has a new value
    @Override
    public void onSensorChanged(SensorEvent event) {
        float raw = event.values[0];
        String today = DateUtil.todayKey();
        int steps = AppPrefs.getPhoneStepsToday(activity);
        float lastRaw = AppPrefs.getStepsLastRaw(activity);

        if (!today.equals(AppPrefs.getStepsDay(activity))) {
            // A new day (or the first time) → start counting from now
            steps = 0;
        } else if (lastRaw < 0 || raw < lastRaw) {
            // The phone restarted → the sensor started again from 0
            steps = steps + (int) raw;
        } else {
            // Normal: add the new steps since the last time
            steps = steps + (int) (raw - lastRaw);
        }
        AppPrefs.saveSteps(activity, today, steps, raw);
        listener.onSteps(steps);
    }

    // Not needed, but SensorEventListener requires it
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
