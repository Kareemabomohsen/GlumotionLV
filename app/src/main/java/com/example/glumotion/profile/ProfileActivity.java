package com.example.glumotion.profile;

import com.example.glumotion.R;
import com.example.glumotion.coach.CoachHelper;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.NutritionCalculator;
import com.example.glumotion.start.PQ4;
import com.example.glumotion.workout.Exercise;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Profile page (like the design): photo, name, age • gender and 7 sections.
 * Every change is saved in Firebase, and when the body data / goal changes
 * the calories and macros are calculated again (the whole app adapts to the user).
 */
public class ProfileActivity extends BaseActivity {

    public static final String[] GOALS = {"Bulking", "Cutting", "Maintaining"};
    public static final String[] LEVELS = {"Beginner", "Intermediate", "Advanced"};
    public static final String[] LOCATIONS = {"Gym", "Home"};
    public static final String[] GENDERS = {"Male", "Female"};
    public static final String[] DIABETES = {"Type 1", "Type 2", "Prediabetes", "Gestational", "None"};
    public static final String[] MONITORING = {"Traditional finger-prick", "Continuous Glucose Monitor (CGM)"};
    public static final String[] DIETS = {"Low Glycemic (Diabetic-friendly)", "Standard", "Low Carb", "High Protein", "Mediterranean", "Paleo"};

    private ImageView ivPhoto;
    private TextView tvName, tvAge, tvSummary;

    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private DataSnapshot data;   // last copy of Users/uid

    // Camera / gallery / permission (ActivityResultContracts)
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Void> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        setupBackBar("Profile");

        ivPhoto = findViewById(R.id.ivProfilePhoto);
        tvName = findViewById(R.id.tvProfileName);
        tvAge = findViewById(R.id.tvProfileAge);
        tvSummary = findViewById(R.id.tvProfileSummary);
        makePhotoRound();

        registerPhotoLaunchers();
        setupCards();
        listenToUser();
    }

    // Stops listening when the page closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
    }

    // Cuts the photo to a circle
    private void makePhotoRound() {
        ivPhoto.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        ivPhoto.setClipToOutline(true);
    }

    // Live listener on Users / uid → every edit shows immediately
    private void listenToUser() {
        userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                data = snapshot;
                showHeader();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        userRef.addValueEventListener(userListener);
    }

    // Helpers that read from the last snapshot
    private String text(String path, String defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        Object value = childOf(path).getValue();
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private DataSnapshot childOf(String path) {
        DataSnapshot snap = data;
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            snap = snap.child(parts[i]);
        }
        return snap;
    }

    private double number(String path) {
        try {
            return Double.parseDouble(text(path, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Photo, name, age • gender, summary line
    private void showHeader() {
        tvName.setText(text("fullName", "User"));
        String age = text("age", "-");
        if (age.equals("0")) {
            age = "-";
        }
        tvAge.setText("Age: " + age + " • " + text("gender", "-"));

        double bmi = NutritionCalculator.bmi(number("Goals/weight"), number("Goals/height"));
        tvSummary.setText(text("HealthConditions/diabetesType", "-") + " • " + text("Goals/goal", "-") + " • BMI " + bmi);

        Bitmap photo = ImageHelper.base64ToBitmap(text("profileImage", ""));
        if (photo != null) {
            ivPhoto.setImageBitmap(photo);
        }
    }

    // ----------------------------- Photo -----------------------------

    // Registers: CAMERA permission, camera, gallery
    private void registerPhotoLaunchers() {
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        if (granted) {
                            cameraLauncher.launch(null);
                        } else {
                            toast("Camera permission is needed to take a photo");
                        }
                    }
                });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                new ActivityResultCallback<Bitmap>() {
                    @Override
                    public void onActivityResult(Bitmap bitmap) {
                        if (bitmap != null) {
                            savePhoto(bitmap);
                        }
                    }
                });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            loadFromGallery(uri);
                        }
                    }
                });

        findViewById(R.id.btnChangePhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePhotoSource();
            }
        });
    }

    // Dialog: Camera or Gallery
    private void choosePhotoSource() {
        new AlertDialog.Builder(this)
                .setTitle("Profile photo")
                .setItems(new String[]{"📷 Camera", "🖼 Gallery"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            openCamera();
                        } else {
                            galleryLauncher.launch("image/*");
                        }
                    }
                })
                .show();
    }

    // Runtime permission: asks for CAMERA only if it was not given before
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null);
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // Reads the chosen picture from the gallery
    private void loadFromGallery(Uri uri) {
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (stream != null) {
                stream.close();
            }
            if (bitmap != null) {
                savePhoto(bitmap);
            }
        } catch (Exception e) {
            toast("Could not open the picture");
        }
    }

    // Saves the photo as Base64 in Users / uid / profileImage
    private void savePhoto(Bitmap bitmap) {
        ivPhoto.setImageBitmap(bitmap);
        if (userRef != null) {
            userRef.child("profileImage").setValue(ImageHelper.bitmapToBase64(bitmap));
            toast("Photo saved");
        }
    }

    // ----------------------------- Cards -----------------------------

    // The 7 cards of the grid
    private void setupCards() {
        findViewById(R.id.cardContact).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editContact();
            }
        });
        findViewById(R.id.cardPhysical).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editPhysical();
            }
        });
        findViewById(R.id.cardGoals).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editGoals();
            }
        });
        findViewById(R.id.cardPreferences).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editPreferences();
            }
        });
        openList(R.id.cardExerciseWeight, ProfileListActivity.MODE_WEIGHT);
        openList(R.id.cardAchievements, ProfileListActivity.MODE_ACHIEVEMENTS);
        openList(R.id.cardHistory, ProfileListActivity.MODE_HISTORY);
    }

    // Helper: a card that opens a list page
    private void openList(int cardId, final String mode) {
        findViewById(cardId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileActivity.this, ProfileListActivity.class);
                intent.putExtra(ProfileListActivity.EXTRA_MODE, mode);
                startActivity(intent);
            }
        });
    }

    // Helper: fills a spinner and selects the saved value
    private void fillSpinner(Spinner spinner, String[] values, String selected) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selected)) {
                spinner.setSelection(i);
            }
        }
    }

    // Helper: shows a dialog with a form and a Save button
    private void showForm(String title, View form, DialogInterface.OnClickListener onSave) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(form)
                .setPositiveButton("Save", onSave)
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Contact Details: name, phone, emergency contact
    private void editContact() {
        if (data == null) {
            return;
        }
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null);
        final EditText etName = form.findViewById(R.id.etProfileFullName);
        final EditText etPhone = form.findViewById(R.id.etProfilePhone);
        final EditText etEmName = form.findViewById(R.id.etEmergencyName);
        final EditText etEmPhone = form.findViewById(R.id.etEmergencyPhone);
        TextView tvEmail = form.findViewById(R.id.tvProfileEmail);

        etName.setText(text("fullName", ""));
        tvEmail.setText("Email: " + text("email", "-"));
        etPhone.setText(text("phone", ""));
        etEmName.setText(text("EmergencyContact/contactName", ""));
        etEmPhone.setText(text("EmergencyContact/phoneNumber", ""));

        showForm("Contact Details", form, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString().trim();
                if (name.length() < 2) {
                    toast("Name is too short");
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("fullName", name);
                updates.put("phone", etPhone.getText().toString().trim());
                updates.put("EmergencyContact/contactName", etEmName.getText().toString().trim());
                updates.put("EmergencyContact/phoneNumber", etEmPhone.getText().toString().trim());
                userRef.updateChildren(updates);
                CoachHelper.updateMyPhone(etPhone.getText().toString().trim());   // my coaches get the new number
                toast("Saved");
            }
        });
    }

    // Physical Stats: age, gender, height, weight → calories again + weight history
    private void editPhysical() {
        if (data == null) {
            return;
        }
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_physical, null);
        final EditText etAge = form.findViewById(R.id.etProfileAge);
        final Spinner spGender = form.findViewById(R.id.spProfileGender);
        final EditText etHeight = form.findViewById(R.id.etProfileHeight);
        final EditText etWeight = form.findViewById(R.id.etProfileWeight);

        etAge.setText(text("age", ""));
        fillSpinner(spGender, GENDERS, text("gender", "Male"));
        etHeight.setText(text("Goals/height", ""));
        etWeight.setText(text("Goals/weight", ""));
        final String oldWeight = text("Goals/weight", "");

        showForm("Physical Stats", form, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int age;
                double height, weight;
                try {
                    age = Integer.parseInt(etAge.getText().toString().trim());
                    height = Double.parseDouble(etHeight.getText().toString().trim());
                    weight = Double.parseDouble(etWeight.getText().toString().trim());
                } catch (NumberFormatException e) {
                    toast("Fill all the numbers");
                    return;
                }
                if (age < 10 || age > 100 || height < 100 || height > 250 || weight < 30 || weight > 300) {
                    toast("Check the values (age 10-100, height 100-250, weight 30-300)");
                    return;
                }
                String gender = spGender.getSelectedItem().toString();
                Map<String, Object> updates = new HashMap<>();
                updates.put("age", age);
                updates.put("gender", gender);
                updates.put("Goals/height", Exercise.formatKg(height));
                updates.put("Goals/weight", Exercise.formatKg(weight));
                // New weight → a new point in the weight history
                if (!Exercise.formatKg(weight).equals(oldWeight)) {
                    String key = userRef.child("WeightHistory").push().getKey();
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("kg", weight);
                    entry.put("timestamp", System.currentTimeMillis());
                    updates.put("WeightHistory/" + key, entry);
                }
                int[] nutrition = NutritionCalculator.calculate(weight, height, age, gender,
                        text("Goals/goal", "Maintaining"), text("Fitness/fitnessLevel", "Beginner"));
                updates.put("Nutrition", NutritionCalculator.toMap(nutrition));
                userRef.updateChildren(updates);
                toast("Saved • new target " + nutrition[0] + " kcal");
            }
        });
    }

    // Fitness Goals: goal, level, location → calories again; Bulking → no diet
    private void editGoals() {
        if (data == null) {
            return;
        }
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_goals, null);
        final Spinner spGoal = form.findViewById(R.id.spProfileGoal);
        final Spinner spLevel = form.findViewById(R.id.spProfileLevel);
        final Spinner spLocation = form.findViewById(R.id.spProfileLocation);
        fillSpinner(spGoal, GOALS, text("Goals/goal", "Maintaining"));
        fillSpinner(spLevel, LEVELS, text("Fitness/fitnessLevel", "Beginner"));
        fillSpinner(spLocation, LOCATIONS, text("Fitness/workoutLocation", "Gym"));

        showForm("Fitness Goals", form, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String goal = spGoal.getSelectedItem().toString();
                String level = spLevel.getSelectedItem().toString();
                Map<String, Object> updates = new HashMap<>();
                updates.put("Goals/goal", goal);
                updates.put("Fitness/fitnessLevel", level);
                updates.put("Fitness/workoutLocation", spLocation.getSelectedItem().toString());
                if (goal.equals("Bulking")) {
                    updates.put("DietPreference/dietPreference", PQ4.DIET_NOT_NEEDED);
                }
                double weight = number("Goals/weight");
                double height = number("Goals/height");
                if (weight > 0 && height > 0) {
                    int[] nutrition = NutritionCalculator.calculate(weight, height, (int) number("age"),
                            text("gender", "Male"), goal, level);
                    updates.put("Nutrition", NutritionCalculator.toMap(nutrition));
                }
                userRef.updateChildren(updates);
                toast("Goals saved");
            }
        });
    }

    // Preferences: diabetes, monitoring, diet (not for Bulking), water, allergies
    private void editPreferences() {
        if (data == null) {
            return;
        }
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_preferences, null);
        final Spinner spDiabetes = form.findViewById(R.id.spPrefDiabetes);
        final Spinner spMonitoring = form.findViewById(R.id.spPrefMonitoring);
        final Spinner spDiet = form.findViewById(R.id.spPrefDiet);
        final EditText etWater = form.findViewById(R.id.etPrefWater);
        final CheckBox cbLactose = form.findViewById(R.id.cbPrefLactose);
        final CheckBox cbGluten = form.findViewById(R.id.cbPrefGluten);
        final CheckBox cbNut = form.findViewById(R.id.cbPrefNut);
        final CheckBox cbHalal = form.findViewById(R.id.cbPrefHalal);
        final CheckBox cbVegetarian = form.findViewById(R.id.cbPrefVegetarian);

        fillSpinner(spDiabetes, DIABETES, text("HealthConditions/diabetesType", "Type 2"));
        fillSpinner(spMonitoring, MONITORING, text("HealthConditions/monitoringMethod", MONITORING[0]));
        fillSpinner(spDiet, DIETS, text("DietPreference/dietPreference", DIETS[0]));
        etWater.setText(text("WaterIntake/waterTargetLiters", "2.5"));
        cbLactose.setChecked(text("Allergies/lactoseAllergy", "false").equals("true"));
        cbGluten.setChecked(text("Allergies/glutenAllergy", "false").equals("true"));
        cbNut.setChecked(text("Allergies/nutAllergy", "false").equals("true"));
        cbHalal.setChecked(text("Allergies/halal", "false").equals("true"));
        cbVegetarian.setChecked(text("Allergies/vegetarian", "false").equals("true"));

        // Same rule as the questions: no diet question for Bulking
        final boolean bulking = text("Goals/goal", "").equals("Bulking");
        if (bulking) {
            spDiet.setVisibility(View.GONE);
            form.findViewById(R.id.tvPrefDietLabel).setVisibility(View.GONE);
        }

        showForm("Preferences", form, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                double water;
                try {
                    water = Double.parseDouble(etWater.getText().toString().trim());
                } catch (NumberFormatException e) {
                    water = 2.5;
                }
                if (water < 0.5 || water > 6) {
                    toast("Water target must be 0.5-6 liters");
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("HealthConditions/diabetesType", spDiabetes.getSelectedItem().toString());
                updates.put("HealthConditions/monitoringMethod", spMonitoring.getSelectedItem().toString());
                if (!bulking) {
                    updates.put("DietPreference/dietPreference", spDiet.getSelectedItem().toString());
                }
                updates.put("WaterIntake/waterTargetLiters", String.valueOf(water));
                updates.put("Allergies/lactoseAllergy", cbLactose.isChecked());
                updates.put("Allergies/glutenAllergy", cbGluten.isChecked());
                updates.put("Allergies/nutAllergy", cbNut.isChecked());
                updates.put("Allergies/halal", cbHalal.isChecked());
                updates.put("Allergies/vegetarian", cbVegetarian.isChecked());
                userRef.updateChildren(updates);
                toast("Preferences saved");
            }
        });
    }
}
