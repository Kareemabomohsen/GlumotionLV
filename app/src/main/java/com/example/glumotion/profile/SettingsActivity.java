package com.example.glumotion.profile;

import com.example.glumotion.R;
import com.example.glumotion.coach.CoachHelper;
import com.example.glumotion.coach.NotificationsActivity;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.common.ReminderReceiver;
import com.example.glumotion.common.ReminderScheduler;
import com.example.glumotion.devices.DevicesActivity;
import com.example.glumotion.medications.MedicationsActivity;
import com.example.glumotion.start.ProfileQuestionsActivity;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * Basic settings only (no language / no colors):
 * reminders, reminder times, glucose unit, medications, setup questions, account, about, log out.
 */
public class SettingsActivity extends BaseActivity {

    private SwitchCompat swReminders;
    private TextView tvRemindersSub, tvUnitsSub;

    // Android 13+ asks the user before showing notifications (runtime permission)
    private ActivityResultLauncher<String> notificationPermission;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupBackBar("Settings");

        swReminders = findViewById(R.id.swReminders);
        tvRemindersSub = findViewById(R.id.rowRemindersSub);
        tvUnitsSub = findViewById(R.id.rowUnitsSub);

        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        if (!granted) {
                            toast("Without permission the reminders can't be shown");
                            swReminders.setChecked(false);
                        }
                    }
                });

        setupReminders();
        setupRows();
        updateTexts();
    }

    // Back from 🔔 Notifications → the coach text is up to date
    @Override
    protected void onResume() {
        super.onResume();
        updateCoachText();
    }

    // Switch + times of the reminders
    private void setupReminders() {
        swReminders.setChecked(AppPrefs.isRemindersOn(this));
        swReminders.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppPrefs.setRemindersOn(SettingsActivity.this, isChecked);
                if (isChecked) {
                    askNotificationPermission();
                    toast("Daily reminders ON");
                } else {
                    toast("Daily reminders OFF");
                }
                ReminderScheduler.scheduleAll(SettingsActivity.this);
            }
        });
        findViewById(R.id.rowNotifications).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swReminders.toggle();
            }
        });
        findViewById(R.id.rowReminders).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseReminder();
            }
        });
    }

    // Runtime permission POST_NOTIFICATIONS (only Android 13 and up)
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // Which reminder to change?
    private void chooseReminder() {
        new AlertDialog.Builder(this)
                .setTitle("Reminder times")
                .setItems(new String[]{"🩸 Glucose check", "💊 Medications"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            pickTime(ReminderReceiver.TYPE_GLUCOSE, ReminderScheduler.DEFAULT_GLUCOSE);
                        } else {
                            pickTime(ReminderReceiver.TYPE_MEDS, ReminderScheduler.DEFAULT_MEDS);
                        }
                    }
                })
                .show();
    }

    // TimePickerDialog → saves the time and schedules again
    private void pickTime(final String type, int defaultMinutes) {
        int minutes = AppPrefs.getReminderMinutes(this, type, defaultMinutes);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                AppPrefs.setReminderMinutes(SettingsActivity.this, type, hourOfDay * 60 + minute);
                ReminderScheduler.scheduleAll(SettingsActivity.this);
                updateTexts();
            }
        }, minutes / 60, minutes % 60, true).show();
    }

    // Small text under "Notifications & My Coach": requests waiting / the coach's name
    private void updateCoachText() {
        final TextView sub = findViewById(R.id.rowMyCoachSub);
        DatabaseReference myRef = FRef.getRefCurrentUser();
        final DatabaseReference requestsRef = CoachHelper.getRefMyRequests();
        if (myRef == null || sub == null || requestsRef == null) {
            return;
        }
        myRef.child("Coaches").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot coaches) {
                String text = "No coach";
                for (DataSnapshot c : coaches.getChildren()) {
                    text = "Coach: " + c.child("name").getValue(String.class);
                }
                final String coachText = text;
                sub.setText(coachText);
                requestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot requests) {
                        if (requests.getChildrenCount() > 0) {
                            sub.setText("📩 " + requests.getChildrenCount() + " request waiting • " + coachText);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // The other rows
    private void setupRows() {
        findViewById(R.id.rowUnits).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseUnit();
            }
        });
        findViewById(R.id.rowMedications).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, MedicationsActivity.class));
            }
        });
        findViewById(R.id.rowHealthSetup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ProfileQuestionsActivity.class));
            }
        });
        findViewById(R.id.rowAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accountMenu();
            }
        });
        findViewById(R.id.rowDevices).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, DevicesActivity.class));
            }
        });
        findViewById(R.id.rowMyCoach).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, NotificationsActivity.class));
            }
        });
        findViewById(R.id.rowAbout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("GluMotion v1.0")
                        .setMessage("An all-in-one app for people with diabetes: food, workouts, blood sugar and medications.\n\n"
                                + "Food data: USDA FoodData Central + Open Food Facts\n"
                                + "Exercises: wger (wger.de)\n\n"
                                + "This app does not replace your doctor.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
        findViewById(R.id.btnSettingsLogout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReminderScheduler.cancelAll(SettingsActivity.this);
                logout();
            }
        });
    }

    // mg/dL or mmol/L (used in the DM screens)
    private void chooseUnit() {
        final String[] units = {AppPrefs.UNIT_MG, AppPrefs.UNIT_MMOL};
        int checked = 0;
        if (AppPrefs.getGlucoseUnit(this).equals(AppPrefs.UNIT_MMOL)) {
            checked = 1;
        }
        new AlertDialog.Builder(this)
                .setTitle("Glucose unit")
                .setSingleChoiceItems(units, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppPrefs.setGlucoseUnit(SettingsActivity.this, units[which]);
                        updateTexts();
                        dialog.dismiss();
                    }
                })
                .show();
    }

    // Change password / delete account
    private void accountMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Account Management")
                .setItems(new String[]{"👤 Edit profile", "🔑 Change password (email)", "🗑 Delete account"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    startActivity(new Intent(SettingsActivity.this, ProfileActivity.class));
                                } else if (which == 1) {
                                    sendPasswordReset();
                                } else {
                                    confirmDeleteAccount();
                                }
                            }
                        })
                .show();
    }

    // Firebase sends an email with a link to choose a new password
    private void sendPasswordReset() {
        FirebaseUser user = FRef.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            return;
        }
        final String email = user.getEmail();
        FRef.getAuth().sendPasswordResetEmail(email).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    toast("Email sent to " + email);
                } else {
                    toast("Could not send the email");
                }
            }
        });
    }

    // Deletes all the data and the user (asks twice - it can't be undone)
    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Delete account?")
                .setMessage("All your data (food, glucose, workouts) will be deleted forever.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteAccount();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 1) delete Users/uid  2) delete the login user  3) back to the login screen
    private void deleteAccount() {
        final FirebaseUser user = FRef.getCurrentUser();
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (user == null || userRef == null) {
            return;
        }
        userRef.removeValue().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                user.delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> deleteTask) {
                        if (!deleteTask.isSuccessful()) {
                            // Firebase asks for a fresh login before deleting
                            toast("Please log in again and then delete the account");
                        }
                        ReminderScheduler.cancelAll(SettingsActivity.this);
                        logout();
                    }
                });
            }
        });
    }

    // Updates the small texts under the rows
    private void updateTexts() {
        int g = AppPrefs.getReminderMinutes(this, ReminderReceiver.TYPE_GLUCOSE, ReminderScheduler.DEFAULT_GLUCOSE);
        int m = AppPrefs.getReminderMinutes(this, ReminderReceiver.TYPE_MEDS, ReminderScheduler.DEFAULT_MEDS);
        tvRemindersSub.setText("Glucose " + DateUtil.twoDigits(g / 60) + ":" + DateUtil.twoDigits(g % 60)
                + " • Medications " + DateUtil.twoDigits(m / 60) + ":" + DateUtil.twoDigits(m % 60));
        tvUnitsSub.setText("Glucose unit: " + AppPrefs.getGlucoseUnit(this));
    }
}
