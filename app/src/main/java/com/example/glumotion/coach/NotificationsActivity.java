package com.example.glumotion.coach;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.FRef;
import com.example.glumotion.start.CreateAccountActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

/**
 * 🔔 Notifications of a normal user (opened from the 👤 menu, instead of the Coach Dashboard):
 * - Coach requests: Accept / Decline. "Later" in the popup on Home keeps the request HERE.
 * - My coaches: call him, or stop sharing my data with him (any time, e.g. when I change my coach).
 */
public class NotificationsActivity extends BaseActivity {

    private LinearLayout llRequests, llCoaches;
    private TextView tvNoRequests, tvNoCoaches;
    private DatabaseReference requestsRef, coachesRef;
    private ValueEventListener requestsListener, coachesListener;

    // Builds the page and listens to my requests and my coaches
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        setupBackBar("🔔 Notifications");

        llRequests = findViewById(R.id.llRequests);
        llCoaches = findViewById(R.id.llCoaches);
        tvNoRequests = findViewById(R.id.tvNoRequests);
        tvNoCoaches = findViewById(R.id.tvNoCoaches);

        requestsRef = CoachHelper.getRefMyRequests();
        DatabaseReference myRef = FRef.getRefCurrentUser();
        if (requestsRef == null || myRef == null) {
            toast("Please log in");
            finish();
            return;
        }
        coachesRef = myRef.child("Coaches");
        listenToRequests();
        listenToCoaches();
    }

    // Stops the live listeners when the page closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestsRef != null && requestsListener != null) {
            requestsRef.removeEventListener(requestsListener);
        }
        if (coachesRef != null && coachesListener != null) {
            coachesRef.removeEventListener(coachesListener);
        }
    }

    // Live list: CoachRequests / my_email_key
    private void listenToRequests() {
        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                llRequests.removeAllViews();
                for (DataSnapshot r : snapshot.getChildren()) {
                    addRequestCard(r.getKey(), r.child("name").getValue(String.class),
                            r.child("email").getValue(String.class), r.child("phone").getValue(String.class));
                }
                if (snapshot.getChildrenCount() == 0) {
                    tvNoRequests.setVisibility(View.VISIBLE);
                } else {
                    tvNoRequests.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvNoRequests.setText("Could not load: " + error.getMessage());
            }
        };
        requestsRef.addValueEventListener(requestsListener);
    }

    // One request: Accept / Decline
    private void addRequestCard(final String coachUid, final String name, final String email, final String phone) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_notification, llRequests, false);
        ((TextView) card.findViewById(R.id.tvNotifTitle)).setText("👥 " + name + " wants to be your coach");
        ((TextView) card.findViewById(R.id.tvNotifText)).setText(email
                + "\nIf you accept, he can SEE your sugar, food, workouts and medications and gets an alert "
                + "if your sugar is dangerous during training.");
        Button accept = card.findViewById(R.id.btnNotifPrimary);
        Button decline = card.findViewById(R.id.btnNotifSecondary);
        accept.setText("Accept");
        decline.setText("Decline");
        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accept(NotificationsActivity.this, coachUid, name, email, phone);
            }
        });
        decline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CoachHelper.declineRequest(coachUid);
                toast("Request declined");
            }
        });
        llRequests.addView(card);
    }

    // Live list: Users / me / Coaches
    private void listenToCoaches() {
        coachesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                llCoaches.removeAllViews();
                for (DataSnapshot c : snapshot.getChildren()) {
                    addCoachCard(c.getKey(), c.child("name").getValue(String.class),
                            c.child("email").getValue(String.class), c.child("phone").getValue(String.class));
                }
                if (snapshot.getChildrenCount() == 0) {
                    tvNoCoaches.setVisibility(View.VISIBLE);
                } else {
                    tvNoCoaches.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        coachesRef.addValueEventListener(coachesListener);
    }

    // One coach: Call / Stop sharing
    private void addCoachCard(final String coachUid, final String name, String email, final String phone) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_notification, llCoaches, false);
        ((TextView) card.findViewById(R.id.tvNotifTitle)).setText("👤 " + name);
        ((TextView) card.findViewById(R.id.tvNotifText)).setText(email + "\nHe can see your data now.");
        Button call = card.findViewById(R.id.btnNotifPrimary);
        Button stop = card.findViewById(R.id.btnNotifSecondary);
        call.setText("📞 Call");
        stop.setText("Stop sharing");
        if (phone == null || phone.isEmpty()) {
            call.setVisibility(View.GONE);
        }
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmStop(coachUid, name);
            }
        });
        llCoaches.addView(card);
    }

    // Stop sharing my data with a coach (asks first)
    private void confirmStop(final String coachUid, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Stop sharing with " + name + "?")
                .setMessage("He will not see your data anymore and you leave his trainees. He can send you a new request later.")
                .setPositiveButton("Stop sharing", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CoachHelper.removeCoach(coachUid);
                        toast("Stopped ✓");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Accepts a coach (used here and by the popup on Home).
     * The coach needs my phone to call me → if I don't have one saved, I write it first.
     */
    public static void accept(final BaseActivity activity, final String coachUid, final String coachName,
                              final String coachEmail, final String coachPhone) {
        final DatabaseReference myRef = FRef.getRefCurrentUser();
        if (myRef == null) {
            return;
        }
        myRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                String name = s.child("fullName").getValue(String.class);
                if (name == null) {
                    name = "Trainee";
                }
                String phone = s.child("phone").getValue(String.class);
                String cPhone = coachPhone;
                if (cPhone == null) {
                    cPhone = "";
                }
                if (phone != null && phone.length() >= 9) {
                    CoachHelper.acceptRequest(coachUid, coachName, coachEmail, cPhone, name, phone);
                    activity.toast(coachName + " is now your coach ✓");
                } else {
                    askPhoneThenAccept(activity, myRef, coachUid, coachName, coachEmail, cPhone, name);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                activity.toast("Error: " + error.getMessage());
            }
        });
    }

    // My phone isn't saved → ask it (so the coach can call me), save it, then accept
    private static void askPhoneThenAccept(final BaseActivity activity, final DatabaseReference myRef, final String coachUid,
                                           final String coachName, final String coachEmail, final String coachPhone,
                                           final String myName) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setHint("05X-XXXXXXX");
        LinearLayout box = new LinearLayout(activity);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity)
                .setTitle("📞 Your phone number")
                .setMessage("Your coach needs it to call you (for example when your sugar is dangerous).")
                .setView(box)
                .setPositiveButton("Save & accept", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String phone = CreateAccountActivity.cleanPhone(input.getText().toString());
                        if (phone.length() < 9) {
                            activity.toast("Write a real phone number");
                            return;
                        }
                        myRef.child("phone").setValue(phone);
                        CoachHelper.acceptRequest(coachUid, coachName, coachEmail, coachPhone, myName, phone);
                        activity.toast(coachName + " is now your coach ✓");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
