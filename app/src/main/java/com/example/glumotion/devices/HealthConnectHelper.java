package com.example.glumotion.devices;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.StepsRecord;
import android.os.Build;
import android.os.OutcomeReceiver;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * ⌚ Health Connect = the "health box" of Android (built into Android 14 and newer).
 * The SMART WATCH is connected to the PHONE by its own app (Samsung Health for Galaxy Watch,
 * Fitbit / Google Fit for Pixel and Wear OS watches, Mi Fitness, Huawei Health, Garmin Connect...).
 * That app writes the steps and the heart rate into Health Connect, and GluMotion READS them from there.
 *
 * Why steps are not counted twice (watch + phone):
 * Health Connect adds all the apps together and removes the overlap itself (by the "App priority"
 * the user chooses in Health Connect). We ask for ONE total = STEPS_COUNT_TOTAL.
 */
public class HealthConnectHelper {

    // The permissions GluMotion asks for (only READ - the app never changes the user's health data)
    public static final String PERM_STEPS = "android.permission.health.READ_STEPS";
    public static final String PERM_HEART_RATE = "android.permission.health.READ_HEART_RATE";
    public static final String[] PERMISSIONS = {PERM_STEPS, PERM_HEART_RATE};

    // Steps of today (total of all apps, without double counting)
    public interface StepsCallback {
        void onSteps(long steps, String source);
    }

    // The newest heart rate (bpm) and when it was measured
    public interface HeartRateCallback {
        void onHeartRate(long bpm, long timeMillis, String source);
    }

    // Health Connect is part of Android from Android 14 (API 34)
    public static boolean isAvailable() {
        return Build.VERSION.SDK_INT >= 34;
    }

    // true if the user allowed this permission in Health Connect
    public static boolean hasPermission(Context context, String permission) {
        return isAvailable() && context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    // true if steps or heart rate are allowed (= a watch / health app can be read)
    public static boolean isConnected(Context context) {
        return hasPermission(context, PERM_STEPS) || hasPermission(context, PERM_HEART_RATE);
    }

    // Opens the Health Connect page of GluMotion (the user turns the permissions on / off there)
    public static void openSettings(Activity activity) {
        if (!isAvailable()) {
            return;
        }
        Intent intent = new Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, activity.getPackageName());
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            // old Android without this page - nothing to open
        }
    }

    // From midnight until now
    private static TimeInstantRangeFilter today() {
        Instant start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(Instant.now()).build();
    }

    // The last "hours" hours until now
    private static TimeInstantRangeFilter lastHours(int hours) {
        Instant now = Instant.now();
        return new TimeInstantRangeFilter.Builder().setStartTime(now.minusSeconds(hours * 3600L)).setEndTime(now).build();
    }

    /**
     * Steps of today = ONE total of the watch + the phone apps (Health Connect removes the overlap).
     * steps = -1 if there is no permission or no data.
     */
    public static void readStepsToday(Context context, final StepsCallback callback) {
        if (!hasPermission(context, PERM_STEPS)) {
            callback.onSteps(-1, "");
            return;
        }
        HealthConnectManager manager = context.getSystemService(HealthConnectManager.class);
        AggregateRecordsRequest<Long> request = new AggregateRecordsRequest.Builder<Long>(today())
                .addAggregationType(StepsRecord.STEPS_COUNT_TOTAL)
                .build();
        manager.aggregate(request, context.getMainExecutor(),
                new OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException>() {
                    @Override
                    public void onResult(AggregateRecordsResponse<Long> response) {
                        Long steps = response.get(StepsRecord.STEPS_COUNT_TOTAL);
                        if (steps == null) {
                            callback.onSteps(-1, "");
                            return;
                        }
                        callback.onSteps(steps, originsText(response.getDataOrigins(StepsRecord.STEPS_COUNT_TOTAL)));
                    }

                    @Override
                    public void onError(HealthConnectException error) {
                        callback.onSteps(-1, "");
                    }
                });
    }

    // The newest heart rate of the last 6 hours (bpm = -1 if the watch doesn't measure it / no permission)
    public static void readLatestHeartRate(Context context, final HeartRateCallback callback) {
        if (!hasPermission(context, PERM_HEART_RATE)) {
            callback.onHeartRate(-1, 0, "");
            return;
        }
        HealthConnectManager manager = context.getSystemService(HealthConnectManager.class);
        ReadRecordsRequestUsingFilters<HeartRateRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<HeartRateRecord>(HeartRateRecord.class)
                        .setTimeRangeFilter(lastHours(6))
                        .setAscending(false)
                        .setPageSize(5)
                        .build();
        manager.readRecords(request, context.getMainExecutor(),
                new OutcomeReceiver<ReadRecordsResponse<HeartRateRecord>, HealthConnectException>() {
                    @Override
                    public void onResult(ReadRecordsResponse<HeartRateRecord> response) {
                        long bestBpm = -1;
                        long bestTime = 0;
                        String source = "";
                        // A heart rate record has many samples → the newest sample of all records
                        for (HeartRateRecord record : response.getRecords()) {
                            for (HeartRateRecord.HeartRateSample sample : record.getSamples()) {
                                long time = sample.getTime().toEpochMilli();
                                if (time > bestTime) {
                                    bestTime = time;
                                    bestBpm = sample.getBeatsPerMinute();
                                    source = deviceText(record.getMetadata().getDevice(),
                                            record.getMetadata().getDataOrigin());
                                }
                            }
                        }
                        callback.onHeartRate(bestBpm, bestTime, source);
                    }

                    @Override
                    public void onError(HealthConnectException error) {
                        callback.onHeartRate(-1, 0, "");
                    }
                });
    }

    // "Samsung Health, Google Fit" - the apps that gave the steps
    private static String originsText(Set<DataOrigin> origins) {
        String text = "";
        for (DataOrigin origin : origins) {
            if (!text.isEmpty()) {
                text = text + ", ";
            }
            text = text + appName(origin.getPackageName());
        }
        return text;
    }

    // "⌚ Galaxy Watch6 (Samsung Health)" - which device measured the heart rate
    private static String deviceText(Device device, DataOrigin origin) {
        String app = appName(origin.getPackageName());
        if (device != null && device.getType() == Device.DEVICE_TYPE_WATCH) {
            String model = device.getModel();
            if (model == null || model.isEmpty()) {
                model = "watch";
            }
            return "⌚ " + model + " (" + app + ")";
        }
        return app;
    }

    // Package name → a name people know
    public static String appName(String pkg) {
        String[][] names = {
                {"com.sec.android.app.shealth", "Samsung Health"},
                {"com.google.android.apps.fitness", "Google Fit"},
                {"com.fitbit.FitbitMobile", "Fitbit"},
                {"com.mi.health", "Mi Fitness"},
                {"com.xiaomi.wearable", "Mi Fitness"},
                {"com.huawei.health", "Huawei Health"},
                {"com.garmin.android.apps.connectmobile", "Garmin Connect"},
                {"com.google.android.apps.healthdata", "Health Connect"},
                {"com.example.glumotion", "GluMotion"}
        };
        for (int i = 0; i < names.length; i++) {
            if (names[i][0].equals(pkg)) {
                return names[i][1];
            }
        }
        return pkg;
    }
}
