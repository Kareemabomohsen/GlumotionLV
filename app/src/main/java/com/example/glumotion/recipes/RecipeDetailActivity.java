package com.example.glumotion.recipes;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.CustomFoodDialog;
import com.example.glumotion.food.DiaryEntry;
import com.example.glumotion.food.FoodActivity;
import com.example.glumotion.food.FoodAdvisor;
import com.example.glumotion.food.FoodItem;
import com.example.glumotion.glucose.GlucoseReading;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * One recipe from TheMealDB: big photo, nutrition of one portion (estimated from the ingredients),
 * is it suitable for the sugar (FoodAdvisor), ingredients, preparation steps, YouTube video, source,
 * and "+ Add" → the user checks the values and the portion is saved in today's food diary.
 */
public class RecipeDetailActivity extends BaseActivity {

    public static final String EXTRA_ID = "recipe_id";

    private Recipe recipe;
    private double[] nutrition;     // {grams, kcal, carbs, protein, fat, sugar, matched, all}
    private GlucoseReading lastReading = null;
    private int kcalLeft = 0;

    // Builds the page and asks the API for the full recipe
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);
        setupBackBar("Recipe");

        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        findViewById(R.id.btnDetailAdd).setEnabled(false);
        loadUserData();
        RecipeApi.lookup(id, new RecipeApi.RecipeCallback() {
            @Override
            public void onRecipe(Recipe r) {
                recipe = r;
                nutrition = RecipeNutrition.estimate(r);
                showRecipe();
            }

            @Override
            public void onError(String error) {
                ((TextView) findViewById(R.id.tvDetailTitle)).setText("Could not load the recipe (" + error + ")");
            }
        });
    }

    // Last glucose + calories left today (for the "is it suitable?" check)
    private void loadUserData() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
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
                if (recipe != null) {
                    showAdvice();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Fills all the parts of the page
    private void showRecipe() {
        setupBackBar(recipe.getTitle());
        ImageLoader.load(recipe.getImageUrl(), (ImageView) findViewById(R.id.ivDetailImage));
        ((TextView) findViewById(R.id.tvDetailTitle)).setText(recipe.getTitle());
        ((TextView) findViewById(R.id.tvDetailInfo)).setText(recipe.getCategory() + " • " + recipe.getArea() + " • "
                + recipe.getIngredients().size() + " ingredients");

        ((TextView) findViewById(R.id.tvDetailNutrition)).setText("🍽 1 portion (about " + FoodItem.round(nutrition[0]) + "g) - estimate\n"
                + FoodItem.round(nutrition[1]) + " kcal • Carbs " + FoodItem.round(nutrition[2]) + "g • Protein "
                + FoodItem.round(nutrition[3]) + "g\nFat " + FoodItem.round(nutrition[4]) + "g • Sugar " + FoodItem.round(nutrition[5]) + "g\n"
                + "(calculated from " + (int) nutrition[6] + " of " + (int) nutrition[7] + " ingredients, recipe for "
                + RecipeNutrition.PORTIONS + " portions)");
        showAdvice();

        // Ingredients: "• 200g Chicken"
        String ingredients = "";
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            ingredients = ingredients + "• " + recipe.getMeasures().get(i) + " " + recipe.getIngredients().get(i) + "\n";
        }
        ((TextView) findViewById(R.id.tvDetailIngredients)).setText(ingredients.trim());

        // Steps: "1. ..."
        ArrayList<String> steps = recipe.getSteps();
        String text = "";
        for (int i = 0; i < steps.size(); i++) {
            text = text + (i + 1) + ". " + steps.get(i) + "\n\n";
        }
        ((TextView) findViewById(R.id.tvDetailSteps)).setText(text.trim());

        setupButtons();
    }

    // Is one portion suitable for the user's sugar? (same rules as the food bot)
    private void showAdvice() {
        if (nutrition == null) {
            return;
        }
        String advice = FoodAdvisor.check(FoodItem.round(nutrition[1]), FoodItem.round(nutrition[2]),
                FoodItem.round(nutrition[5]), lastReading, kcalLeft, false);
        if (recipe.isSweet()) {
            advice = "⚠ This is a dessert - full of sugar. A small portion only.\n" + advice;
        }
        ((TextView) findViewById(R.id.tvDetailAdvice)).setText(advice);
    }

    // Video / Source (Implicit Intents) and Add
    private void setupButtons() {
        View btnVideo = findViewById(R.id.btnDetailVideo);
        if (recipe.getYoutube().isEmpty()) {
            btnVideo.setVisibility(View.GONE);
        }
        btnVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(recipe.getYoutube())));
            }
        });
        View btnSource = findViewById(R.id.btnDetailSource);
        if (recipe.getSource().isEmpty()) {
            btnSource.setVisibility(View.GONE);
        }
        btnSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(recipe.getSource())));
            }
        });
        View btnAdd = findViewById(R.id.btnDetailAdd);
        btnAdd.setEnabled(true);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseMeal();
            }
        });
    }

    // Which meal? Then the user checks the values (estimate) and saves
    private void chooseMeal() {
        new AlertDialog.Builder(this)
                .setTitle("Add 1 portion to...")
                .setItems(DiaryEntry.MEALS, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmValues(DiaryEntry.MEALS[which]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // The "new food" dialog filled with the estimate - the user can fix the numbers
    private void confirmValues(final String meal) {
        CustomFoodDialog.showWithValues(this, recipe.getTitle(), nutrition[0], nutrition[1], nutrition[2],
                nutrition[3], nutrition[4], nutrition[5], new CustomFoodDialog.Listener() {
                    @Override
                    public void onFoodCreated(FoodItem food, double grams) {
                        Task<Void> task = DiaryEntry.add(DateUtil.todayKey(), meal, food, grams);
                        if (task == null) {
                            toast("Not logged in!");
                            return;
                        }
                        task.addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                toast(recipe.getTitle() + " added to " + meal + " ✓");
                            }
                        });
                    }
                });
    }
}
