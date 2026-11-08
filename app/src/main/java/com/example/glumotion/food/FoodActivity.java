package com.example.glumotion.food;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.recipes.SuggestedDishes;
import com.example.glumotion.workout.Exercise;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Food diary screen.
 * - Every day is saved separately in Firebase → arrows to go back to previous days.
 * - Every food the user adds is summed: calories, carbs, protein, fat, sugar.
 * - Targets come from Users/uid/Nutrition (calculated from the profile questions).
 * - Suggestions at the bottom fit the calories that are left today.
 */
public class FoodActivity extends BaseActivity {

    // Views
    private TextView tvFoodDate, tvCalorieProgress, tvCarbsProgress, tvProteinProgress, tvFatProgress, tvSugarToday, tvSuggestionsSub;
    private ProgressBar pbCalories, pbCarbs, pbProtein, pbFat;
    private LinearLayout llSuggestions;

    // Arrays: index 0 = Breakfast, 1 = Lunch, 2 = Dinner, 3 = Snacks (same order as DiaryEntry.MEALS)
    private final TextView[] mealSubs = new TextView[4];
    private final LinearLayout[] mealContainers = new LinearLayout[4];

    // Targets of the user (defaults until Firebase answers)
    private int targetKcal = 2000, targetProtein = 120, targetCarbs = 200, targetFat = 65;

    // Totals of the shown day
    private int totalKcal, totalProtein, totalCarbs, totalFat, totalSugar;

    private final Calendar selectedDate = Calendar.getInstance();
    private DatabaseReference dayRef;
    private ValueEventListener dayListener;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food);

        setupBottomNav(R.id.nav_food);
        setupTopBar();
        initViews();
        setupButtons();
        loadTargets();
        showDay();
    }

    // Stops listening to Firebase when the screen closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
    }

    // Finds the views
    private void initViews() {
        tvFoodDate = findViewById(R.id.tvFoodDate);
        tvCalorieProgress = findViewById(R.id.tvCalorieProgress);
        tvCarbsProgress = findViewById(R.id.tvCarbsProgress);
        tvProteinProgress = findViewById(R.id.tvProteinProgress);
        tvFatProgress = findViewById(R.id.tvFatProgress);
        tvSugarToday = findViewById(R.id.tvSugarToday);
        tvSuggestionsSub = findViewById(R.id.tvSuggestionsSub);
        pbCalories = findViewById(R.id.pbCalories);
        pbCarbs = findViewById(R.id.pbCarbs);
        pbProtein = findViewById(R.id.pbProtein);
        pbFat = findViewById(R.id.pbFat);
        llSuggestions = findViewById(R.id.llSuggestions);

        mealSubs[0] = findViewById(R.id.tvBreakfastSub);
        mealSubs[1] = findViewById(R.id.tvLunchSub);
        mealSubs[2] = findViewById(R.id.tvDinnerSub);
        mealSubs[3] = findViewById(R.id.tvSnacksSub);
        mealContainers[0] = findViewById(R.id.llBreakfastItems);
        mealContainers[1] = findViewById(R.id.llLunchItems);
        mealContainers[2] = findViewById(R.id.llDinnerItems);
        mealContainers[3] = findViewById(R.id.llSnacksItems);
    }

    // Day arrows, "+" of every meal, recipes link
    private void setupButtons() {
        findViewById(R.id.btnFoodPrevDay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeDay(-1);
            }
        });
        findViewById(R.id.btnFoodNextDay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeDay(1);
            }
        });

        addMealButton(R.id.btnAddBreakfast, 0);
        addMealButton(R.id.btnAddLunch, 1);
        addMealButton(R.id.btnAddDinner, 2);
        addMealButton(R.id.btnAddSnacks, 3);

        findViewById(R.id.btnMoreDishes).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FoodActivity.this, SuggestedDishes.class));
            }
        });
    }

    // Helper: "+" of a meal opens the bottom sheet (search / barcode / suggestions / recent)
    private void addMealButton(int buttonId, final int mealIndex) {
        findViewById(buttonId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddMealBottomSheet sheet = AddMealBottomSheet.newInstance(DiaryEntry.MEALS[mealIndex], DateUtil.dateKey(selectedDate));
                sheet.show(getSupportFragmentManager(), "AddMealBottomSheet");
            }
        });
    }

    // Moves one day back / forward (not to the future)
    private void changeDay(int amount) {
        Calendar test = (Calendar) selectedDate.clone();
        test.add(Calendar.DAY_OF_YEAR, amount);
        if (test.after(Calendar.getInstance())) {
            toast("You can't log food for the future");
            return;
        }
        selectedDate.add(Calendar.DAY_OF_YEAR, amount);
        showDay();
    }

    // Reads the targets from Users / uid / Nutrition (saved by the questions / profile)
    private void loadTargets() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("Nutrition").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                targetKcal = readNumber(snapshot, "targetCalories", targetKcal);
                targetProtein = readNumber(snapshot, "protein", targetProtein);
                targetCarbs = readNumber(snapshot, "carbs", targetCarbs);
                targetFat = readNumber(snapshot, "fats", targetFat);
                updateTotalsUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Helper: the values were saved as text ("2450") → number
    public static int readNumber(DataSnapshot snapshot, String key, int defaultValue) {
        Object value = snapshot.child(key).getValue();
        if (value == null) {
            return defaultValue;
        }
        try {
            return (int) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Shows the selected day: title + starts listening to its food diary
    private void showDay() {
        if (DateUtil.sameDay(selectedDate, Calendar.getInstance())) {
            tvFoodDate.setText("Today, " + DateUtil.shortDate(selectedDate));
        } else {
            tvFoodDate.setText(DateUtil.dayName(selectedDate) + ", " + DateUtil.shortDate(selectedDate));
        }

        stopListening();
        dayRef = DiaryEntry.getDayRef(DateUtil.dateKey(selectedDate));
        if (dayRef == null) {
            return;
        }
        dayListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<DiaryEntry> entries = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    DiaryEntry entry = snap.getValue(DiaryEntry.class);
                    if (entry != null) {
                        entry.setId(snap.getKey());
                        entries.add(entry);
                    }
                }
                showEntries(entries);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toast("Load failed: " + error.getMessage());
            }
        };
        // Live listener: when the user adds food in the search screen this screen updates by itself
        dayRef.addValueEventListener(dayListener);
    }

    // Removes the listener of the old day
    private void stopListening() {
        if (dayRef != null && dayListener != null) {
            dayRef.removeEventListener(dayListener);
        }
    }

    // Puts every food under its meal and sums everything
    private void showEntries(ArrayList<DiaryEntry> entries) {
        totalKcal = 0;
        totalProtein = 0;
        totalCarbs = 0;
        totalFat = 0;
        totalSugar = 0;

        int[] mealKcal = new int[4];
        int[] mealCount = new int[4];
        for (int m = 0; m < 4; m++) {
            mealContainers[m].removeAllViews();
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < entries.size(); i++) {
            DiaryEntry e = entries.get(i);
            int m = mealIndexOf(e.getMeal());

            totalKcal = totalKcal + e.getKcal();
            totalProtein = totalProtein + e.getProtein();
            totalCarbs = totalCarbs + e.getCarbs();
            totalFat = totalFat + e.getFat();
            totalSugar = totalSugar + e.getSugar();
            mealKcal[m] = mealKcal[m] + e.getKcal();
            mealCount[m]++;

            mealContainers[m].addView(createEntryRow(inflater, mealContainers[m], e));
        }

        for (int m = 0; m < 4; m++) {
            String items = " items";
            if (mealCount[m] == 1) {
                items = " item";
            }
            mealSubs[m].setText(mealCount[m] + items + " · " + mealKcal[m] + " kcal");
        }
        updateTotalsUI();
    }

    // "Breakfast" → 0 ... (unknown → Snacks)
    private int mealIndexOf(String meal) {
        for (int i = 0; i < DiaryEntry.MEALS.length; i++) {
            if (DiaryEntry.MEALS[i].equals(meal)) {
                return i;
            }
        }
        return 3;
    }

    // One food row (🗑 button or long click = delete)
    private View createEntryRow(LayoutInflater inflater, LinearLayout parent, final DiaryEntry e) {
        View row = inflater.inflate(R.layout.item_diary_entry, parent, false);
        TextView tvName = row.findViewById(R.id.tvEntryName);
        TextView tvMacros = row.findViewById(R.id.tvEntryMacros);
        TextView tvKcal = row.findViewById(R.id.tvEntryKcal);

        tvName.setText(e.getName());
        tvMacros.setText(Exercise.formatKg(e.getGrams()) + "g • P " + e.getProtein() + "g • C " + e.getCarbs()
                + "g • F " + e.getFat() + "g");
        tvKcal.setText(e.getKcal() + " kcal");

        row.findViewById(R.id.btnEntryDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(e);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDelete(e);
                return true;
            }
        });
        return row;
    }

    // Asks before deleting a food from the diary
    private void confirmDelete(final DiaryEntry e) {
        new AlertDialog.Builder(this)
                .setTitle("Remove food")
                .setMessage("Remove " + e.getName() + " (" + e.getKcal() + " kcal)?")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (dayRef != null) {
                            dayRef.child(e.getId()).removeValue();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Updates the totals card and the suggestions
    private void updateTotalsUI() {
        tvCalorieProgress.setText(totalKcal + " / " + targetKcal + " kcal");
        pbCalories.setProgress(percent(totalKcal, targetKcal));
        tvCarbsProgress.setText(totalCarbs + "/" + targetCarbs + "g");
        pbCarbs.setProgress(percent(totalCarbs, targetCarbs));
        tvProteinProgress.setText(totalProtein + "/" + targetProtein + "g");
        pbProtein.setProgress(percent(totalProtein, targetProtein));
        tvFatProgress.setText(totalFat + "/" + targetFat + "g");
        pbFat.setProgress(percent(totalFat, targetFat));

        // Sugar warning for diabetics (WHO: less than 25g added sugar is best)
        tvSugarToday.setText("Sugar: " + totalSugar + "g");
        if (totalSugar > 25) {
            tvSugarToday.setTextColor(0xFFD63031);
            tvSugarToday.setText("Sugar: " + totalSugar + "g - try to keep it under 25g");
        } else {
            tvSugarToday.setTextColor(0xFF6B7671);
        }
        showSuggestions();
    }

    // value/target as 0-100
    private int percent(int value, int target) {
        if (target <= 0) {
            return 0;
        }
        int p = value * 100 / target;
        if (p > 100) {
            p = 100;
        }
        return p;
    }

    // Suggestion cards (low sugar foods that fit the remaining calories)
    private void showSuggestions() {
        int kcalLeft = targetKcal - totalKcal;
        int proteinLeft = targetProtein - totalProtein;
        if (kcalLeft > 0) {
            tvSuggestionsSub.setText("Low sugar foods • " + kcalLeft + " kcal left today");
        } else {
            tvSuggestionsSub.setText("You reached your calories - light low sugar options");
            kcalLeft = 150;
        }

        llSuggestions.removeAllViews();
        ArrayList<FoodItem> suggestions = LocalFoods.suggest(kcalLeft, proteinLeft, 8);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < suggestions.size(); i++) {
            final FoodItem food = suggestions.get(i);
            View card = inflater.inflate(R.layout.item_food_suggestion, llSuggestions, false);
            TextView tvName = card.findViewById(R.id.tvSuggestName);
            TextView tvInfo = card.findViewById(R.id.tvSuggestInfo);
            TextView tvProtein = card.findViewById(R.id.tvSuggestProtein);

            double g = food.getServingGrams();
            tvName.setText(food.getName());
            tvInfo.setText(Exercise.formatKg(g) + "g • " + FoodItem.round(food.kcalFor(g)) + " kcal");
            tvProtein.setText(FoodItem.round(food.proteinFor(g)) + "g protein");

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chooseMealForSuggestion(food);
                }
            });
            llSuggestions.addView(card);
        }
    }

    // Asks to which meal to add the suggested food (1 portion)
    private void chooseMealForSuggestion(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("Add " + food.getName() + " to...")
                .setItems(DiaryEntry.MEALS, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DiaryEntry.add(DateUtil.dateKey(selectedDate), DiaryEntry.MEALS[which], food, food.getServingGrams());
                        toast(food.getName() + " added to " + DiaryEntry.MEALS[which]);
                    }
                })
                .show();
    }
}
