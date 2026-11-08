package com.example.glumotion.food;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.glucose.GlucoseReading;
import com.example.glumotion.workout.Exercise;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * Search food → choose grams → it is added to the diary of the chosen day and meal.
 * Food data: built-in list + USDA FoodData Central (English names) / Open Food Facts.
 * Barcode: the user TYPES the number → GluMotion products (Firebase) → Open Food Facts → or he adds the product himself.
 */
public class FoodSearchActivity extends BaseActivity {

    public static final String EXTRA_MEAL = "meal";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_OPEN_BARCODE = "open_barcode";
    public static final String EXTRA_QUERY = "query";

    private EditText etFoodSearch;
    private TextView tvSearchStatus;
    private ProgressBar pbFoodSearch;

    private final ArrayList<FoodItem> results = new ArrayList<>();
    private FoodResultAdapter adapter;

    private String meal = "Breakfast";
    private String dateKey;

    private final ArrayList<FoodItem> customFoods = new ArrayList<>(); // foods the user created (Users/uid/CustomFoods)
    private GlucoseReading lastReading = null;                         // for the "is it suitable?" check
    private int kcalLeft = 0;                                          // calories left today (0 = unknown)

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_search);

        meal = getIntent().getStringExtra(EXTRA_MEAL);
        if (meal == null) {
            meal = "Breakfast";
        }
        dateKey = getIntent().getStringExtra(EXTRA_DATE);
        if (dateKey == null) {
            dateKey = DateUtil.todayKey();
        }
        setupBackBar("Add to " + meal);

        etFoodSearch = findViewById(R.id.etFoodSearch);
        tvSearchStatus = findViewById(R.id.tvSearchStatus);
        pbFoodSearch = findViewById(R.id.pbFoodSearch);

        RecyclerView rv = findViewById(R.id.rvFoodResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FoodResultAdapter(results, new FoodResultAdapter.OnFoodClickListener() {
            @Override
            public void onFoodClick(FoodItem food) {
                showAmountDialog(food);
            }
        });
        rv.setAdapter(adapter);

        setupSearch();
        setupBarcodeButtons();
        loadRecentFoods();
        loadUserFoodData();
        findViewById(R.id.btnCreateFood).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CustomFoodDialog.show(FoodSearchActivity.this, etFoodSearch.getText().toString().trim(),
                        new CustomFoodDialog.Listener() {
                            @Override
                            public void onFoodCreated(FoodItem food, double grams) {
                                customFoods.add(food);
                                addToDiary(food, grams);
                            }
                        });
            }
        });

        // Opened from the "Barcode" button → open the "type the barcode" window right away
        if (getIntent().getBooleanExtra(EXTRA_OPEN_BARCODE, false)) {
            showTypeBarcodeDialog();
        }
        // Opened with a text from the bottom sheet → search right away
        String query = getIntent().getStringExtra(EXTRA_QUERY);
        if (query != null && !query.isEmpty()) {
            etFoodSearch.setText(query);
            doSearch();
        }
    }

    // Search button + "search" key on the keyboard
    private void setupSearch() {
        findViewById(R.id.btnDoSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch();
            }
        });
        etFoodSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch();
                    return true;
                }
                return false;
            }
        });
    }

    // 1) built-in foods immediately  2) Open Food Facts results when they arrive
    private void doSearch() {
        final String query = etFoodSearch.getText().toString().trim();
        if (query.length() < 2) {
            etFoodSearch.setError("Type at least 2 letters");
            return;
        }
        results.clear();
        // The user's own foods first, then the built-in list
        for (int i = 0; i < customFoods.size(); i++) {
            if (customFoods.get(i).getName().toLowerCase().contains(query.toLowerCase())) {
                results.add(customFoods.get(i));
            }
        }
        results.addAll(LocalFoods.search(query));
        adapter.notifyDataSetChanged();

        pbFoodSearch.setVisibility(View.VISIBLE);
        tvSearchStatus.setText("Searching \"" + query + "\"...");

        FoodApi.search(query, new FoodApi.FoodListCallback() {
            @Override
            public void onFoods(ArrayList<FoodItem> foods) {
                pbFoodSearch.setVisibility(View.GONE);
                results.addAll(foods);
                adapter.notifyDataSetChanged();
                tvSearchStatus.setText(results.size() + " results for \"" + query + "\"");
                if (results.isEmpty()) {
                    tvSearchStatus.setText("Nothing found - create it with \"➕ Create a new food\"");
                }
            }

            @Override
            public void onError(String error) {
                pbFoodSearch.setVisibility(View.GONE);
                tvSearchStatus.setText("Offline: " + results.size() + " built-in results");
            }
        });
    }

    // Users / uid / RecentFoods - the last foods (newest first)
    private void loadRecentFoods() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("RecentFoods").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // If the user already searched - don't replace his results
                if (!etFoodSearch.getText().toString().trim().isEmpty()) {
                    return;
                }
                ArrayList<FoodItem> recent = new ArrayList<>();
                ArrayList<Long> times = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    FoodItem food = snap.getValue(FoodItem.class);
                    Long time = snap.child("lastUsed").getValue(Long.class);
                    if (food != null && food.getName() != null) {
                        if (time == null) {
                            time = 0L;
                        }
                        // Insert sorted: newest first
                        int pos = 0;
                        while (pos < times.size() && times.get(pos) > time) {
                            pos++;
                        }
                        recent.add(pos, food);
                        times.add(pos, time);
                    }
                }
                results.clear();
                results.addAll(recent);
                if (results.isEmpty()) {
                    // New user: show the built-in foods
                    results.addAll(LocalFoods.getAll());
                    tvSearchStatus.setText("Popular foods");
                } else {
                    tvSearchStatus.setText("Recent foods");
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /**
     * Reads once: the user's own foods, the last glucose reading and the calories left today
     * (used to warn when a food is not suitable for his sugar).
     */
    private void loadUserFoodData() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                for (DataSnapshot f : s.child("CustomFoods").getChildren()) {
                    FoodItem food = f.getValue(FoodItem.class);
                    if (food != null && food.getName() != null) {
                        customFoods.add(food);
                    }
                }
                for (DataSnapshot r : s.child("GlucoseReadings").getChildren()) {
                    GlucoseReading reading = r.getValue(GlucoseReading.class);
                    if (reading != null && (lastReading == null || reading.getTimestamp() > lastReading.getTimestamp())) {
                        lastReading = reading;
                    }
                }
                int target = FoodActivity.readNumber(s.child("Nutrition"), "targetCalories", 2000);
                int eaten = 0;
                for (DataSnapshot e : s.child("FoodDiary").child(DateUtil.todayKey()).getChildren()) {
                    DiaryEntry entry = e.getValue(DiaryEntry.class);
                    if (entry != null) {
                        eaten = eaten + entry.getKcal();
                    }
                }
                kcalLeft = target - eaten;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // "Enter barcode number" button
    private void setupBarcodeButtons() {
        findViewById(R.id.btnTypeBarcode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTypeBarcodeDialog();
            }
        });
    }

    // The user types the numbers under the barcode (8-13 digits)
    private void showTypeBarcodeDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 7290000066318");
        LinearLayout box = new LinearLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Barcode number")
                .setMessage("Write the numbers under the barcode of the product")
                .setView(box)
                .setPositiveButton("Find", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String code = input.getText().toString().trim();
                        if (code.length() >= 8 && code.length() <= 14) {
                            lookupBarcode(code);
                        } else {
                            toast("A barcode has 8-13 digits");
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Finds the product of a barcode:
     * 1) Products / barcode in Firebase (products that GluMotion users added - Israeli products that are missing online)
     * 2) Open Food Facts (world database, Israeli products too)
     * 3) Not found → the user adds it once, and from then it is found for EVERY user of the app
     */
    private void lookupBarcode(final String code) {
        pbFoodSearch.setVisibility(View.VISIBLE);
        tvSearchStatus.setText("Looking for " + code + "...");
        FRef.getDatabase().getReference("Products").child(code).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                FoodItem saved = snapshot.getValue(FoodItem.class);
                if (saved != null && saved.getName() != null) {
                    pbFoodSearch.setVisibility(View.GONE);
                    tvSearchStatus.setText("Found: " + saved.getName());
                    showAmountDialog(saved);
                } else {
                    lookupBarcodeOnline(code);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                lookupBarcodeOnline(code);
            }
        });
    }

    // Open Food Facts (world + Israeli products)
    private void lookupBarcodeOnline(final String code) {
        FoodApi.findByBarcode(code, new FoodApi.FoodCallback() {
            @Override
            public void onFood(FoodItem food) {
                pbFoodSearch.setVisibility(View.GONE);
                tvSearchStatus.setText("Found: " + food.getName());
                showAmountDialog(food);
            }

            @Override
            public void onNotFound() {
                pbFoodSearch.setVisibility(View.GONE);
                tvSearchStatus.setText("Barcode " + code + " not found");
                askToAddProduct(code);
            }

            @Override
            public void onError(String error) {
                pbFoodSearch.setVisibility(View.GONE);
                tvSearchStatus.setText("No internet: " + error);
            }
        });
    }

    // Product not found → the user writes its values from the package (once) → saved for everybody
    private void askToAddProduct(final String code) {
        new AlertDialog.Builder(this)
                .setTitle("Product not found")
                .setMessage("Barcode " + code + " is not in the database yet.\n"
                        + "Add it from the nutrition table on the package - next time (for every GluMotion user) it is found.")
                .setPositiveButton("Add product", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CustomFoodDialog.show(FoodSearchActivity.this, "", new CustomFoodDialog.Listener() {
                            @Override
                            public void onFoodCreated(FoodItem food, double grams) {
                                food.setBarcode(code);
                                FRef.getDatabase().getReference("Products").child(code).setValue(food.toMap());
                                customFoods.add(food);
                                addToDiary(food, grams);
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Dialog: how many grams? The calories/macros update while typing (TextWatcher)
    private void showAmountDialog(final FoodItem food) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_food_amount, null);
        final EditText etGrams = view.findViewById(R.id.etFoodGrams);
        final TextView tvMacros = view.findViewById(R.id.tvAmountMacros);
        final TextView tvWarning = view.findViewById(R.id.tvAmountWarning);

        etGrams.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateMacrosText(food, s.toString(), tvMacros);
                updateWarning(food, s.toString(), tvWarning);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        etGrams.setText(Exercise.formatKg(food.getServingGrams()));

        new AlertDialog.Builder(this)
                .setTitle(food.getFullName())
                .setView(view)
                .setPositiveButton("Add to " + meal, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        double grams = parse(etGrams.getText().toString());
                        if (grams <= 0 || grams > 3000) {
                            toast("Enter grams between 1 and 3000");
                            return;
                        }
                        addToDiary(food, grams);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Shows the values for the typed grams
    private void updateMacrosText(FoodItem food, String text, TextView target) {
        double g = parse(text);
        target.setText(FoodItem.round(food.kcalFor(g)) + " kcal\n"
                + "Protein " + FoodItem.round(food.proteinFor(g)) + "g   •   Carbs " + FoodItem.round(food.carbsFor(g)) + "g\n"
                + "Fat " + FoodItem.round(food.fatFor(g)) + "g   •   Sugar " + FoodItem.round(food.sugarFor(g)) + "g");
    }

    // Is this amount suitable for the user's sugar? (FoodAdvisor) - red = problem, green = OK
    private void updateWarning(FoodItem food, String text, TextView target) {
        double g = parse(text);
        if (g <= 0) {
            target.setVisibility(View.GONE);
            return;
        }
        int carbs = FoodItem.round(food.carbsFor(g));
        int sugar = FoodItem.round(food.sugarFor(g));
        int kcal = FoodItem.round(food.kcalFor(g));
        target.setVisibility(View.VISIBLE);
        target.setText(FoodAdvisor.check(kcal, carbs, sugar, lastReading, kcalLeft, false));
        if (target.getText().toString().startsWith("✅")) {
            target.setTextColor(0xFF2E7D32);
        } else {
            target.setTextColor(0xFFD63031);
        }
    }

    // Saves the food in Users / uid / FoodDiary / date
    private void addToDiary(final FoodItem food, double grams) {
        Task<Void> task = DiaryEntry.add(dateKey, meal, food, grams);
        if (task == null) {
            toast("Not logged in!");
            return;
        }
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                toast(food.getName() + " added to " + meal + " ✓");
            }
        });
        // The next food of today is checked with the new calories left
        kcalLeft = kcalLeft - FoodItem.round(food.kcalFor(grams));
    }

    // Text → number (0 if wrong)
    private double parse(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
