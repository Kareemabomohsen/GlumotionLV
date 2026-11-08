package com.example.glumotion.glucose;

import com.example.glumotion.coach.CoachHelper;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.common.ReminderReceiver;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;

/**
 * 🚨 Emergency alerts when the SENSOR (CGM) sees a dangerous blood sugar:
 * 1) a notification to the user
 * 2) an SMS to the emergency contact (saved in the questions / profile)
 * 3) an alert to the coach - only DURING training (AppPrefs.isInWorkout)
 *
 * ===================== كيف تفعّلها (كلها موقوفة بـ //) =====================
 * 1) فعّل الحساس أول: NightscoutHelper.java (الرابط) + السطر بـ DMActivity.java
 * 2) AndroidManifest.xml: شيل الـ comment عن السطر  uses-permission ... SEND_SMS
 * 3) DMActivity.java: شيل الـ // عن سطر طلب إذن الـ SMS (requestPermissions)
 * 4) NightscoutHelper.java: شيل الـ // عن السطرين اللي فيهم EmergencyHelper
 * 5) ReminderScheduler.java: شيل الـ // عن scheduleSensorCheck (عشان يفحص كل 15 دقيقة لحاله)
 * 6) للتجربة بدون حساس: LogGlucoseActivity.java - شيل الـ // عن سطر EmergencyHelper (قراءة يدوية خطيرة)
 * ==========================================================================
 */
public class EmergencyHelper {

    public static final int DANGER_LOW = 55;     // below 55 = severe low (hypoglycemia level 2)
    public static final int DANGER_HIGH = 300;   // above 300 = very high
    private static final long MIN_GAP = 30L * 60 * 1000;   // max one alert every 30 minutes

    // true if the value needs an emergency alert
    public static boolean isDangerous(int value) {
        return value > 0 && (value < DANGER_LOW || value > DANGER_HIGH);
    }

    /**
     * Checks one reading and, if it is dangerous, sends all the alerts.
     * Called with the newest sensor reading (NightscoutHelper).
     */
    public static void checkAndAlert(final Context context, final int value) {
        if (!isDangerous(value)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - AppPrefs.getLastEmergencyTime(context) < MIN_GAP) {
            return;   // already alerted a few minutes ago
        }
        AppPrefs.setLastEmergencyTime(context, now);

        final String level;
        String advice;
        if (value < DANGER_LOW) {
            level = "LOW";
            advice = "Eat 15g fast sugar NOW (juice / glucose tablets) and measure again in 15 minutes.";
        } else {
            level = "HIGH";
            advice = "Drink water, check ketones (type 1), take your medication as your doctor said. Don't train.";
        }

        // 1) Notification to the user
        ReminderReceiver.show(context, 9, "🚨 Dangerous blood sugar: " + value + " mg/dL", advice, LogGlucoseActivity.class);

        // 2) + 3) need the user's data (name, emergency phone)
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                String name = s.child("fullName").getValue(String.class);
                if (name == null) {
                    name = "GluMotion user";
                }
                Calendar cal = Calendar.getInstance();
                String time = DateUtil.formatTime12(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

                String phone = s.child("EmergencyContact").child("phoneNumber").getValue(String.class);
                if (phone != null && !phone.trim().isEmpty()) {
                    sendSms(context, phone.trim(), "GluMotion ALERT: " + name + "'s blood sugar is " + value
                            + " mg/dL (dangerously " + level + ") at " + time + ". Please check on him now.");
                }

                // The coach gets it only while the trainee is training
                if (AppPrefs.isInWorkout(context)) {
                    CoachHelper.alertMyCoaches("sugar " + value + " mg/dL (" + level + ") during training at " + time);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Sends an SMS (only if the user gave the SEND_SMS permission)
    private static void sendSms(Context context, String phone, String text) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= 31) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }
            smsManager.sendMultipartTextMessage(phone, null, smsManager.divideMessage(text), null, null);
        } catch (Exception e) {
            // no SIM card / emulator - nothing to do
        }
    }
}
