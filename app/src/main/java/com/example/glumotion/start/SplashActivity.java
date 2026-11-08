package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.coach.CoachActivity;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.FRef;
import com.example.glumotion.home.HomePageActivity;
import com.example.glumotion.profile.User;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000; // 3 seconds delay

    // Shows the logo and starts a background Thread that waits 3 seconds
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // New start of the app → not training (in case the app was closed in the middle of a workout)
        AppPrefs.setInWorkout(this, false);
        setContentView(R.layout.activity_splash);

        SleepThread sleepThread = new SleepThread();
        Thread thread = new Thread(sleepThread);
        thread.start();
    }

    // The Thread: waits, then decides which screen to open
    public class SleepThread implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(SPLASH_DURATION);
            } catch (InterruptedException e) {
                return;
            }
            if (FRef.getCurrentUser() == null) {
                // Not logged in → create account screen
                startActivity(new Intent(SplashActivity.this, CreateAccountActivity.class));
                finish();
            } else {
                goToRightScreen(SplashActivity.this);
            }
        }
    }

    /**
     * Used by Splash and Login:
     * coach → Coach Dashboard; patient who didn't finish the profile questions → questions; otherwise → dashboard.
     */
    public static void goToRightScreen(final Activity activity) {
        FirebaseUser user = FRef.getCurrentUser();
        if (user == null) {
            return;
        }
        FRef.getRefUsers().child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // A coach account goes straight to the Coach Dashboard
                        if (User.ROLE_COACH.equals(snapshot.child("role").getValue(String.class))) {
                            openScreen(activity, CoachActivity.class);
                            return;
                        }
                        Boolean completed = snapshot.child("Status").child("isProfileCompleted").getValue(Boolean.class);
                        if (completed != null && completed) {
                            openScreen(activity, HomePageActivity.class);
                        } else {
                            openScreen(activity, ProfileQuestionsActivity.class);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        openScreen(activity, HomePageActivity.class);
                    }
                });
    }

    // Opens a screen, clears the old ones and closes the current one
    private static void openScreen(Activity activity, Class<?> screen) {
        Intent intent = new Intent(activity, screen);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
