package com.example.glumotion.coach;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.DiaryEntry;
import com.example.glumotion.food.FoodActivity;
import com.example.glumotion.glucose.GlucoseReading;
import com.example.glumotion.medications.MedicationModel;
import com.example.glumotion.profile.SettingsActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * 👥 Coach Dashboard (the ADMIN of the app).
 * A coach / trainer is responsible for a group: he adds a trainee by email, the trainee accepts,
 * and then the coach sees every trainee's status (sugar, food, steps, workouts, medications)
 * and gets alerts when a trainee's sugar is dangerous during training.
 */
public class CoachActivity extends BaseActivity {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private LinearLayout llTrainees, llAlerts, llPending;
    private TextView tvNoTrainees, tvNoAlerts, tvTraineesTitle;
    private DatabaseReference myRef;
    private ValueEventListener traineesListener, alertsListener, pendingListener;

    // Builds the screen and starts listening to my trainees and my alerts
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coach);
        setupBackBar("Coach Dashboard");

        llTrainees = findViewById(R.id.llTrainees);
        llAlerts = findViewById(R.id.llCoachAlerts);
        llPending = findViewById(R.id.llPending);
        tvNoTrainees = findViewById(R.id.tvNoTrainees);
        tvNoAlerts = findViewById(R.id.tvNoAlerts);
        tvTraineesTitle = findViewById(R.id.tvTraineesTitle);

        findViewById(R.id.btnAddTrainee).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddDialog();
            }
        });
        findViewById(R.id.btnClearAlerts).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myRef != null) {
                    myRef.child("Alerts").removeValue();
                }
            }
        });

        // Everything of the coach is under Coaches / myUid (Trainees, Sent, Alerts)
        if (FRef.getCurrentUser() == null) {
            toast("Please log in");
            finish();
            return;
        }
        myRef = CoachHelper.getRefCoach(FRef.getCurrentUser().getUid());
        CoachHelper.ensureCoachProfile();
        listenToTrainees();
        listenToAlerts();
        listenToPending();
        setupToolbarAction("⋮", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });
    }

    // Menu of the coach (a coach account has no bottom bar): Settings / Log out
    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Settings");
        menu.getMenu().add("Log out");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getTitle().toString().equals("Settings")) {
                    startActivity(new Intent(CoachActivity.this, SettingsActivity.class));
                } else {
                    logout();
                }
                return true;
            }
        });
        menu.show();
    }

    // Requests I sent that the trainee didn't answer yet (Coaches / me / Sent)
    private void listenToPending() {
        final TextView title = findViewById(R.id.tvPendingTitle);
        pendingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                llPending.removeAllViews();
                for (DataSnapshot r : snapshot.getChildren()) {
                    addPendingRow(r.getKey(), r.child("email").getValue(String.class));
                }
                if (snapshot.getChildrenCount() > 0) {
                    title.setVisibility(View.VISIBLE);
                } else {
                    title.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        myRef.child("Sent").addValueEventListener(pendingListener);
    }

    // One "waiting" row (long press = cancel the request)
    private void addPendingRow(final String traineeEmailKey, final String email) {
        TextView row = new TextView(this);
        row.setText("⏳ " + email + "\nWaiting for him to accept (👤 menu → 🔔 Notifications) • long press = cancel");
        row.setTextColor(0xFF1D2623);
        row.setTextSize(13);
        row.setBackgroundResource(R.drawable.bg_card);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = pad / 2;
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                CoachHelper.cancelRequest(traineeEmailKey);
                toast("Request to " + email + " cancelled");
                return true;
            }
        });
        llPending.addView(row, params);
    }

    // Stops the live listeners when the page closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myRef != null && traineesListener != null) {
            myRef.child("Trainees").removeEventListener(traineesListener);
        }
        if (myRef != null && alertsListener != null) {
            myRef.child("Alerts").removeEventListener(alertsListener);
        }
        if (myRef != null && pendingListener != null) {
            myRef.child("Sent").removeEventListener(pendingListener);
        }
    }

    // Dialog: the email of the trainee → sends him a request
    private void showAddDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint("trainee@email.com");
        LinearLayout box = new LinearLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Add trainee")
                .setMessage("Write the email he uses in GluMotion. He will get a request and must accept it.")
                .setView(box)
                .setPositiveButton("Send request", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String email = input.getText().toString().trim();
                        if (!email.contains("@") || !email.contains(".")) {
                            toast("Write a real email");
                            return;
                        }
                        CoachHelper.sendRequest(email, new CoachHelper.ResultListener() {
                            @Override
                            public void onResult(String message) {
                                new AlertDialog.Builder(CoachActivity.this)
                                        .setMessage(message)
                                        .setPositiveButton("OK", null)
                                        .show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Live list of Coaches / me / Trainees → one card per trainee
    private void listenToTrainees() {
        traineesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                llTrainees.removeAllViews();
                int count = 0;
                for (DataSnapshot t : snapshot.getChildren()) {
                    String name = t.child("name").getValue(String.class);
                    String email = t.child("email").getValue(String.class);
                    addTraineeCard(t.getKey(), name, email);
                    count++;
                }
                tvTraineesTitle.setText("👥 My trainees (" + count + ")");
                if (count == 0) {
                    tvNoTrainees.setVisibility(View.VISIBLE);
                } else {
                    tvNoTrainees.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toast("Error: " + error.getMessage());
            }
        };
        myRef.child("Trainees").addValueEventListener(traineesListener);
    }

    // One trainee card: name + email now, the status when his data arrives
    private void addTraineeCard(final String uid, String name, final String email) {
        if (name == null) {
            name = "Trainee";
        }
        final String traineeName = name;
        View card = LayoutInflater.from(this).inflate(R.layout.item_trainee, llTrainees, false);
        ((TextView) card.findViewById(R.id.tvTraineeName)).setText(traineeName);
        ((TextView) card.findViewById(R.id.tvTraineeEmail)).setText(email);
        final TextView tvGlucose = card.findViewById(R.id.tvTraineeGlucose);
        final TextView tvToday = card.findViewById(R.id.tvTraineeToday);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CoachActivity.this, TraineeDetailActivity.class);
                intent.putExtra(TraineeDetailActivity.EXTRA_UID, uid);
                intent.putExtra(TraineeDetailActivity.EXTRA_NAME, traineeName);
                startActivity(intent);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmRemove(uid, traineeName);
                return true;
            }
        });
        llTrainees.addView(card);

        // The trainee's data (allowed only because he accepted - see database.rules.json)
        FRef.getRefUsers().child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                showSummary(s, tvGlucose, tvToday);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvToday.setText("No access to his data (he may have removed you).");
            }
        });
    }

    /**
     * Short status of a trainee: last glucose (colored), calories today, steps,
     * workouts in the last 7 days and medications taken today.
     */
    private void showSummary(DataSnapshot s, TextView tvGlucose, TextView tvToday) {
        // Last glucose reading
        GlucoseReading last = null;
        for (DataSnapshot r : s.child("GlucoseReadings").getChildren()) {
            GlucoseReading reading = r.getValue(GlucoseReading.class);
            if (reading != null && (last == null || reading.getTimestamp() > last.getTimestamp())) {
                last = reading;
            }
        }
        if (last != null) {
            tvGlucose.setText(last.getValue() + "\n" + GlucoseReading.getStatus(last.getValue()));
            tvGlucose.setTextColor(GlucoseReading.getStatusColor(last.getValue()));
        } else {
            tvGlucose.setText("no\nreadings");
        }

        String today = DateUtil.todayKey();
        int kcal = 0;
        for (DataSnapshot e : s.child("FoodDiary").child(today).getChildren()) {
            DiaryEntry entry = e.getValue(DiaryEntry.class);
            if (entry != null) {
                kcal = kcal + entry.getKcal();
            }
        }
        int target = FoodActivity.readNumber(s.child("Nutrition"), "targetCalories", 2000);
        Long steps = s.child("DailyHealth").child(today).child("steps").getValue(Long.class);
        if (steps == null) {
            steps = 0L;
        }

        int workouts = 0;
        long weekAgo = System.currentTimeMillis() - 7 * DAY;
        for (DataSnapshot w : s.child("Workouts").getChildren()) {
            Long time = w.child("timestamp").getValue(Long.class);
            if (time != null && time > weekAgo) {
                workouts++;
            }
        }

        int medsTotal = 0, medsTaken = 0;
        for (DataSnapshot m : s.child("Medications").getChildren()) {
            MedicationModel med = m.getValue(MedicationModel.class);
            if (med != null && med.getName() != null) {
                medsTotal++;
                if (med.getTodayStatus().equals("Taken")) {
                    medsTaken++;
                }
            }
        }

        String lastTime = "";
        if (last != null) {
            lastTime = "🩸 last reading: " + DateUtil.shortDate(last.getCalendar()) + " " + last.getTimeText() + "\n";
        }
        tvToday.setText(lastTime + "🍽 " + kcal + " / " + target + " kcal   🚶 " + steps + " steps\n"
                + "💪 " + workouts + " workouts this week   💊 " + medsTaken + "/" + medsTotal + " meds today");
    }

    // Long press on a trainee → stop following him
    private void confirmRemove(final String uid, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + name + "?")
                .setMessage("You will not see his data anymore.")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CoachHelper.removeTrainee(uid);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Live list of Coaches / me / Alerts (newest first, max 10)
    private void listenToAlerts() {
        alertsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                llAlerts.removeAllViews();
                ArrayList<DataSnapshot> list = new ArrayList<>();
                for (DataSnapshot a : snapshot.getChildren()) {
                    list.add(0, a);   // push keys are in time order → adding at 0 = newest first
                }
                for (int i = 0; i < list.size() && i < 10; i++) {
                    addAlertRow(list.get(i));
                }
                if (list.isEmpty()) {
                    tvNoAlerts.setVisibility(View.VISIBLE);
                } else {
                    tvNoAlerts.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        myRef.child("Alerts").addValueEventListener(alertsListener);
    }

    // One red alert row (tap = open the trainee)
    private void addAlertRow(DataSnapshot alert) {
        final String traineeUid = alert.child("traineeUid").getValue(String.class);
        final String name = alert.child("traineeName").getValue(String.class);
        String message = alert.child("message").getValue(String.class);
        Long time = alert.child("time").getValue(Long.class);
        String when = "";
        if (time != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(time);
            when = DateUtil.shortDate(cal) + " " + DateUtil.formatTime12(cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE));
        }

        TextView row = new TextView(this);
        row.setText("🚨 " + name + " - " + message + "\n" + when);
        row.setTextColor(0xFFD63031);
        row.setTextSize(14);
        row.setBackgroundResource(R.drawable.bg_card);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = pad / 2;
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (traineeUid != null) {
                    Intent intent = new Intent(CoachActivity.this, TraineeDetailActivity.class);
                    intent.putExtra(TraineeDetailActivity.EXTRA_UID, traineeUid);
                    intent.putExtra(TraineeDetailActivity.EXTRA_NAME, name);
                    startActivity(intent);
                }
            }
        });
        llAlerts.addView(row, params);
    }
}
