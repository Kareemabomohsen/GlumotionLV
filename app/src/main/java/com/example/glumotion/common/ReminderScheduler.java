package com.example.glumotion.common;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/**
 * Schedules the daily reminders with AlarmManager (they repeat every day).
 */
public class ReminderScheduler {

    // Default times (minutes from midnight): glucose 08:00, medications 09:00
    public static final int DEFAULT_GLUCOSE = 8 * 60;
    public static final int DEFAULT_MEDS = 9 * 60;

    // Turns on (or off) both reminders according to the settings
    public static void scheduleAll(Context context) {
        // ================================================================
        // 🚨 فحص حساس السكر (CGM) كل 15 دقيقة + تنبيه الطوارئ والمدرب إذا السكر خطير
        // لتفعيل: شيل الـ // من السطر اللي تحت (ولازم تفعّل كمان NightscoutHelper - شوف EmergencyHelper.java)
        // ================================================================
        // scheduleSensorCheck(context);

        if (!AppPrefs.isRemindersOn(context)) {
            cancelAll(context);
            return;
        }
        schedule(context, 1, ReminderReceiver.TYPE_GLUCOSE,
                AppPrefs.getReminderMinutes(context, ReminderReceiver.TYPE_GLUCOSE, DEFAULT_GLUCOSE));
        schedule(context, 2, ReminderReceiver.TYPE_MEDS,
                AppPrefs.getReminderMinutes(context, ReminderReceiver.TYPE_MEDS, DEFAULT_MEDS));
    }

    // One daily alarm at hour:minute
    private static void schedule(Context context, int requestCode, String type, int minutes) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, minutes / 60);
        time.set(Calendar.MINUTE, minutes % 60);
        time.set(Calendar.SECOND, 0);
        // The time already passed today → start tomorrow
        if (time.getTimeInMillis() <= System.currentTimeMillis()) {
            time.add(Calendar.DAY_OF_YEAR, 1);
        }
        // setInexactRepeating = battery friendly, no special permission needed
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, makeIntent(context, requestCode, type));
    }

    /**
     * Every 15 minutes: ReminderReceiver (TYPE_CGM) → NightscoutHelper.importLatest → EmergencyHelper.
     * Not used until the line in scheduleAll is turned on.
     */
    public static void scheduleSensorCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, makeIntent(context, 3, ReminderReceiver.TYPE_CGM));
    }

    // Cancels both alarms
    public static void cancelAll(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(makeIntent(context, 1, ReminderReceiver.TYPE_GLUCOSE));
        alarmManager.cancel(makeIntent(context, 2, ReminderReceiver.TYPE_MEDS));
    }

    // The PendingIntent that "calls" ReminderReceiver
    private static PendingIntent makeIntent(Context context, int requestCode, String type) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_TYPE, type);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
