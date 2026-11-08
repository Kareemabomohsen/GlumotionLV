package com.example.glumotion.coach;

import com.example.glumotion.common.FRef;
import com.example.glumotion.food.DiaryEntry;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * 👥 Coach (Admin) ↔ Trainee - all the Firebase work in one place.
 *
 * Firebase:
 *   Coaches / coachUid                 { name, email, gym, phone, since }   ← every coach is saved here
 *   Coaches / coachUid / Trainees / traineeUid   { name, email, phone }      ← the people he is responsible for
 *   Coaches / coachUid / Sent / email_key        { email, time }             ← requests waiting for approval
 *   Coaches / coachUid / Alerts / id             { traineeName, message... } ← dangerous sugar during training
 *   CoachRequests / email_key / coachUid         { name, email, phone, time } ← the request (found by the trainee's email)
 *   Users / traineeUid / Coaches / coachUid      { name, email, phone }       ← the trainee agreed (the coach may read his data)
 *
 * The trainee must agree (privacy!) and he can stop sharing any time.
 */
public class CoachHelper {

    // Answer to the screen (a message to show)
    public interface ResultListener {
        void onResult(String message);
    }

    // Firebase keys can't have "." → karim@gmail.com → karim@gmail_com
    public static String emailKey(String email) {
        return DiaryEntry.safeKey(email.trim().toLowerCase());
    }

    // Key of the logged in user's email (null if not logged in)
    public static String myEmailKey() {
        FirebaseUser me = FRef.getCurrentUser();
        if (me == null || me.getEmail() == null) {
            return null;
        }
        return emailKey(me.getEmail());
    }

    // Coaches / coachUid
    public static DatabaseReference getRefCoach(String coachUid) {
        return FRef.getDatabase().getReference("Coaches").child(coachUid);
    }

    // CoachRequests / my_email_key (the requests that were sent to me)
    public static DatabaseReference getRefMyRequests() {
        String key = myEmailKey();
        if (key == null) {
            return null;
        }
        return FRef.getDatabase().getReference("CoachRequests").child(key);
    }

    /**
     * Saves a NEW coach: Coaches / uid = {name, email, gym, phone, since}
     * (called by the sign up of a coach)
     */
    public static void registerCoach(String name, String email, String gym, String phone) {
        FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            return;
        }
        Map<String, Object> coach = new HashMap<>();
        coach.put("name", name);
        coach.put("email", email.toLowerCase());
        coach.put("gym", gym);
        coach.put("phone", phone);
        coach.put("since", System.currentTimeMillis());
        getRefCoach(me.getUid()).updateChildren(coach);
    }

    // Makes sure the coach has his name and email under Coaches / uid (accounts from older versions)
    public static void ensureCoachProfile() {
        final FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            return;
        }
        FRef.getRefUsers().child(me.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Map<String, Object> coach = new HashMap<>();
                String name = s.child("fullName").getValue(String.class);
                if (name != null) {
                    coach.put("name", name);
                }
                if (me.getEmail() != null) {
                    coach.put("email", me.getEmail().toLowerCase());
                }
                if (!coach.isEmpty()) {
                    getRefCoach(me.getUid()).updateChildren(coach);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /**
     * Coach: sends a follow request to the user with this email.
     * The request is saved under the EMAIL (CoachRequests / email_key / myUid), so the coach doesn't need
     * to search all the users. The trainee sees it in 🔔 Notifications when he opens the app.
     * The answer is shown only after Firebase answers (success or the real error).
     */
    public static void sendRequest(String typedEmail, final ResultListener listener) {
        final FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            listener.onResult("Please log in first");
            return;
        }
        final String email = typedEmail.trim().toLowerCase();
        if (me.getEmail() != null && email.equals(me.getEmail().toLowerCase())) {
            listener.onResult("This is your own email 🙂");
            return;
        }
        getRefCoach(me.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot coachSnap) {
                // Already my trainee?
                for (DataSnapshot t : coachSnap.child("Trainees").getChildren()) {
                    if (email.equals(t.child("email").getValue(String.class))) {
                        listener.onResult(email + " is already your trainee ✓");
                        return;
                    }
                }
                String myName = coachSnap.child("name").getValue(String.class);
                if (myName == null) {
                    myName = "Coach";
                }
                String myPhone = coachSnap.child("phone").getValue(String.class);
                if (myPhone == null) {
                    myPhone = "";
                }
                writeRequest(me, myName, myPhone, email, listener);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onResult(rulesMessage(error.getMessage()));
            }
        });
    }

    // Writes the request (by email) + "waiting" at the coach, and reports the REAL result
    private static void writeRequest(final FirebaseUser me, String myName, String myPhone, final String email,
                                     final ResultListener listener) {
        final String key = emailKey(email);
        Map<String, Object> request = new HashMap<>();
        request.put("name", myName);
        request.put("email", me.getEmail());
        request.put("phone", myPhone);
        request.put("time", System.currentTimeMillis());
        FRef.getDatabase().getReference("CoachRequests").child(key).child(me.getUid()).setValue(request)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Map<String, Object> sent = new HashMap<>();
                        sent.put("email", email);
                        sent.put("time", System.currentTimeMillis());
                        getRefCoach(me.getUid()).child("Sent").child(key).setValue(sent);
                        listener.onResult("Request sent ✓\n" + email + " sees it in GluMotion → 👤 menu → 🔔 Notifications.\n"
                                + "After he accepts, he moves from \"Waiting\" to your trainees.");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        listener.onResult(rulesMessage(e.getMessage()));
                    }
                });
    }

    // Firebase refused → explain how to fix it (the rules in Firebase Console)
    private static String rulesMessage(String error) {
        return "Firebase refused: " + error + "\n\nFix: Firebase Console → Realtime Database → Rules → "
                + "paste the file database.rules.json (it is in the project) → Publish.";
    }

    // Coach: cancels a request that is still waiting
    public static void cancelRequest(String traineeEmailKey) {
        FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("CoachRequests/" + traineeEmailKey + "/" + me.getUid(), null);
        updates.put("Coaches/" + me.getUid() + "/Sent/" + traineeEmailKey, null);
        FRef.getDatabase().getReference().updateChildren(updates);
    }

    /**
     * Trainee: accepts a coach. One updateChildren from the ROOT changes 4 places together:
     * I'm saved under the coach (with my phone - so he can call me), the coach is saved under me,
     * and the request + the "waiting" row are deleted.
     */
    public static void acceptRequest(String coachUid, String coachName, String coachEmail, String coachPhone,
                                     String myName, String myPhone) {
        FirebaseUser me = FRef.getCurrentUser();
        String myKey = myEmailKey();
        if (me == null || myKey == null) {
            return;
        }
        Map<String, Object> coach = new HashMap<>();
        coach.put("name", coachName);
        coach.put("email", coachEmail);
        coach.put("phone", coachPhone);
        Map<String, Object> trainee = new HashMap<>();
        trainee.put("name", myName);
        trainee.put("email", me.getEmail().toLowerCase());
        trainee.put("phone", myPhone);

        Map<String, Object> updates = new HashMap<>();
        updates.put("Users/" + me.getUid() + "/Coaches/" + coachUid, coach);
        updates.put("Coaches/" + coachUid + "/Trainees/" + me.getUid(), trainee);
        updates.put("CoachRequests/" + myKey + "/" + coachUid, null);
        updates.put("Coaches/" + coachUid + "/Sent/" + myKey, null);
        FRef.getDatabase().getReference().updateChildren(updates);
    }

    // Trainee: says no to a request (removed at the trainee AND at the coach's "waiting" list)
    public static void declineRequest(String coachUid) {
        String myKey = myEmailKey();
        if (myKey == null) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("CoachRequests/" + myKey + "/" + coachUid, null);
        updates.put("Coaches/" + coachUid + "/Sent/" + myKey, null);
        FRef.getDatabase().getReference().updateChildren(updates);
    }

    // Coach: stops following a trainee (removed from both sides)
    public static void removeTrainee(String traineeUid) {
        FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("Coaches/" + me.getUid() + "/Trainees/" + traineeUid, null);
        updates.put("Users/" + traineeUid + "/Coaches/" + me.getUid(), null);
        FRef.getDatabase().getReference().updateChildren(updates);
    }

    // Trainee: stops sharing with a coach (he can't see my data anymore)
    public static void removeCoach(String coachUid) {
        FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("Users/" + me.getUid() + "/Coaches/" + coachUid, null);
        updates.put("Coaches/" + coachUid + "/Trainees/" + me.getUid(), null);
        FRef.getDatabase().getReference().updateChildren(updates);
    }

    // Trainee changed his phone → every coach of his gets the new number
    public static void updateMyPhone(final String phone) {
        final FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            return;
        }
        FRef.getRefUsers().child(me.getUid()).child("Coaches").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                for (DataSnapshot coach : s.getChildren()) {
                    getRefCoach(coach.getKey()).child("Trainees").child(me.getUid()).child("phone").setValue(phone);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /**
     * Sends an alert to ALL the coaches of the logged in user:  Coaches / coachUid / Alerts / id
     * (used by EmergencyHelper when the sensor sees a dangerous sugar during training)
     */
    public static void alertMyCoaches(final String message) {
        final FirebaseUser me = FRef.getCurrentUser();
        if (me == null) {
            return;
        }
        FRef.getRefUsers().child(me.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                String myName = s.child("fullName").getValue(String.class);
                if (myName == null) {
                    myName = "Your trainee";
                }
                for (DataSnapshot coach : s.child("Coaches").getChildren()) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("traineeUid", me.getUid());
                    alert.put("traineeName", myName);
                    alert.put("message", message);
                    alert.put("time", System.currentTimeMillis());
                    getRefCoach(coach.getKey()).child("Alerts").push().setValue(alert);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
}
