package com.example.glumotion.start;

import com.example.glumotion.R;
import com.example.glumotion.coach.CoachActivity;
import com.example.glumotion.coach.CoachHelper;
import com.example.glumotion.common.FRef;
import com.example.glumotion.profile.User;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;

import java.util.HashMap;
import java.util.Map;

public class CreateAccountActivity extends AppCompatActivity {

    public static final String EXTRA_ROLE = "role";   // Login → "I'm a coach" opens this screen with Coach chosen

    EditText fullNameEt, emailEt, phoneEt, passwordEt, confirmPasswordEt, ageEt, coachGymEt;
    RadioGroup rgAccountType;
    View coachExtraLayout;
    TextView titleTv;
    Button createAccountBtn;
    TextView signInTv;
    Spinner genderSpinner;

    // Builds the create account screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);
        initViews();
    }

    // Finds the views and connects the buttons
    private void initViews() {
        fullNameEt = findViewById(R.id.fullNameEt);
        emailEt = findViewById(R.id.emailEt);
        phoneEt = findViewById(R.id.phoneEt);
        passwordEt = findViewById(R.id.passwordEt);
        confirmPasswordEt = findViewById(R.id.confirmPasswordEt);
        ageEt = findViewById(R.id.ageEt);
        createAccountBtn = findViewById(R.id.createAccountBtn);
        signInTv = findViewById(R.id.signInTv);
        genderSpinner = findViewById(R.id.genderSpinner);
        rgAccountType = findViewById(R.id.rgAccountType);
        coachExtraLayout = findViewById(R.id.coachExtraLayout);
        coachGymEt = findViewById(R.id.coachGymEt);
        titleTv = findViewById(R.id.titleTv);
        setupAccountType();
        // Gender is used to calculate the calories correctly
        genderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Male", "Female"}));

        createAccountBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });

        // BUG FIX: before it was finish() → the app closed (Splash was already closed)
        signInTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(CreateAccountActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    // Patient / Coach buttons: the coach gets the "gym / team" field and his own title
    private void setupAccountType() {
        rgAccountType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                showRole(checkedId == R.id.rbCoach);
            }
        });
        if (User.ROLE_COACH.equals(getIntent().getStringExtra(EXTRA_ROLE))) {
            rgAccountType.check(R.id.rbCoach);
            showRole(true);
        }
    }

    // Changes the screen for a coach or for a patient
    private void showRole(boolean coach) {
        if (coach) {
            coachExtraLayout.setVisibility(View.VISIBLE);
            titleTv.setText("Create Coach Account");
            createAccountBtn.setText("Create Coach Account");
        } else {
            coachExtraLayout.setVisibility(View.GONE);
            titleTv.setText(R.string.create_your_account);
            createAccountBtn.setText(R.string.create_account_continue);
        }
    }

    // true when "Coach" is chosen
    private boolean isCoach() {
        return rgAccountType.getCheckedRadioButtonId() == R.id.rbCoach;
    }

    // Checks the fields, creates the user in Firebase Auth and saves him in the database
    private void registerUser() {
        final String fullName = fullNameEt.getText().toString().trim();
        final String email = emailEt.getText().toString().trim();
        final String password = passwordEt.getText().toString().trim();
        String confirmPassword = confirmPasswordEt.getText().toString().trim();
        String age = ageEt.getText().toString().trim();
        final String gender = genderSpinner.getSelectedItem().toString();
        final String phone = cleanPhone(phoneEt.getText().toString());

        // The age is needed for the calories of a patient - a coach doesn't have to write it
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)
                || (TextUtils.isEmpty(age) && !isCoach())) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        // The phone is needed so the coach can call the trainee (and the trainee can call the coach)
        if (phone.length() < 9 || phone.length() > 13) {
            phoneEt.setError("Write a real phone number (e.g. 0501234567)");
            return;
        }
        int ageValue = 0;
        if (!TextUtils.isEmpty(age)) {
            try {
                ageValue = Integer.parseInt(age);
            } catch (NumberFormatException e) {
                ageEt.setError("Numbers only");
                return;
            }
            if (ageValue < 10 || ageValue > 100) {
                ageEt.setError("Age 10-100");
                return;
            }
        }
        final int ageNumber = ageValue;
        if (password.length() < 6) {
            passwordEt.setError("At least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        createAccountBtn.setEnabled(false);
        FRef.getAuth().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            saveUser(fullName, email, phone, ageNumber, gender);
                        } else {
                            createAccountBtn.setEnabled(true);
                            String msg = "error";
                            if (task.getException() != null) {
                                msg = task.getException().getMessage();
                            }
                            Toast.makeText(CreateAccountActivity.this, "Auth creation failed: " + msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Saves the new user in Users/uid (with his role).
     * Patient → profile questions. Coach → no questions (Status = completed) + Coaches / uid → Coach Dashboard.
     */
    private void saveUser(final String fullName, final String email, final String phone, int age, String gender) {
        String uid = FRef.getCurrentUser().getUid();
        // The password is NOT saved in the database (Firebase Authentication keeps it safely encrypted)
        final User user = new User(uid, fullName, email.toLowerCase(), phone, age, gender);
        if (isCoach()) {
            user.setRole(User.ROLE_COACH);
        }

        FRef.getRefUsers().child(uid).setValue(user)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> dbTask) {
                        createAccountBtn.setEnabled(true);
                        if (dbTask.isSuccessful()) {
                            Toast.makeText(CreateAccountActivity.this, "Account created successfully", Toast.LENGTH_SHORT).show();
                            if (User.ROLE_COACH.equals(user.getRole())) {
                                saveCoachInfo(fullName, email, phone);
                                Intent intent = new Intent(CreateAccountActivity.this, CoachActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            } else {
                                startActivity(new Intent(CreateAccountActivity.this, ProfileQuestionsActivity.class));
                            }
                            finish();
                        } else {
                            String msg = "error";
                            if (dbTask.getException() != null) {
                                msg = dbTask.getException().getMessage();
                            }
                            Toast.makeText(CreateAccountActivity.this, "Database error: " + msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Coach: Coaches / uid {name, email, gym, phone} + Status/isProfileCompleted = true (coaches skip the patient questions)
    private void saveCoachInfo(String fullName, String email, String phone) {
        CoachHelper.registerCoach(fullName, email, coachGymEt.getText().toString().trim(), phone);
        Map<String, Object> updates = new HashMap<>();
        updates.put("Status/isProfileCompleted", true);
        FRef.getRefCurrentUser().updateChildren(updates);
    }

    // "050-123 4567" → "0501234567" (only digits and +)
    public static String cleanPhone(String text) {
        String result = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || (c == '+' && result.isEmpty())) {
                result = result + c;
            }
        }
        return result;
    }
}
