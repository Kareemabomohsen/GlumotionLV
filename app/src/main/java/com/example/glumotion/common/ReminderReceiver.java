package com.example.glumotion.common;

import com.example.glumotion.R;
import com.example.glumotion.glucose.LogGlucoseActivity;
import com.example.glumotion.glucose.NightscoutHelper;
import com.example.glumotion.medications.MedicationsActivity;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * BroadcastReceiver: AlarmManager "wakes" it at the reminder time and it shows a Notification.
 * It also gets BOOT_COMPLETED (phone restarted) → the alarms are scheduled again.
 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_TYPE = "type";
    public static final String TYPE_GLUCOSE = "glucose";
    public static final String TYPE_MEDS = "meds";
    public static final String TYPE_CGM = "cgm";   // check the sensor every 15 minutes (see ReminderScheduler - off by default)
    private static final String CHANNEL_ID = "glumotion_reminders";

    // Called by Android when the alarm time comes (or after the phone restarts)
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.scheduleAll(context);
            return;
        }
        String type = intent.getStringExtra(EXTRA_TYPE);
        if (TYPE_CGM.equals(type)) {
            // Imports the new sensor readings (and checks if they are dangerous - see NightscoutHelper)
            NightscoutHelper.importLatest(context);
            return;
        }
        if (TYPE_MEDS.equals(type)) {
            show(context, 2, "💊 Medication time", "Did you take your medications? Tap to mark them.", MedicationsActivity.class);
        } else {
            show(context, 1, "🩸 Check your blood sugar", "Time to log your glucose reading.", LogGlucoseActivity.class);
        }
    }

    // Builds and shows the notification (tap → opens the right screen)
    public static void show(Context context, int id, String title, String text, Class<?> screen) {
        createChannel(context);

        // Android 13+: without the POST_NOTIFICATIONS permission we can't show it
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent open = new Intent(context, screen);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_heart_pulse)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    // Android 8+ needs a notification channel
    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Reminders",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Glucose and medication reminders");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
