package com.example.glumotion.food;

import com.example.glumotion.R;
import com.example.glumotion.common.FRef;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DatabaseReference;

/**
 * Dialog to create a NEW food (when the search or the bot didn't find it).
 * The user writes the values of the amount he ate → we convert them to "per 100g"
 * and save the food in Users / uid / CustomFoods / name, so it can be searched next time.
 * Used by: FoodSearchActivity and the food chat bot (ChatBotActivity).
 */
public class CustomFoodDialog {

    // Tells the screen that the food was created (and how many grams were eaten)
    public interface Listener {
        void onFoodCreated(FoodItem food, double grams);
    }

    // Shows the dialog (presetName can be "" or the name the bot didn't know)
    public static void show(final Activity activity, String presetName, final Listener listener) {
        build(activity, "➕ New food", presetName, null, listener);
    }

    /**
     * Same dialog filled with values (e.g. the estimate of a recipe) - the user checks / fixes them.
     * The values are for the given grams.
     */
    public static void showWithValues(Activity activity, String name, double grams, double kcal, double carbs,
                                      double protein, double fat, double sugar, Listener listener) {
        build(activity, "✏ Check the values", name, new double[]{grams, kcal, carbs, protein, fat, sugar}, listener);
    }

    // Builds and shows the dialog (values = null → empty fields)
    private static void build(final Activity activity, String title, String presetName, double[] values, final Listener listener) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_custom_food, null);
        final EditText etName = view.findViewById(R.id.etCustomFoodName);
        final EditText etGrams = view.findViewById(R.id.etCustomFoodGrams);
        final EditText etKcal = view.findViewById(R.id.etCustomFoodKcal);
        final EditText etCarbs = view.findViewById(R.id.etCustomFoodCarbs);
        final EditText etProtein = view.findViewById(R.id.etCustomFoodProtein);
        final EditText etFat = view.findViewById(R.id.etCustomFoodFat);
        final EditText etSugar = view.findViewById(R.id.etCustomFoodSugar);
        if (presetName != null) {
            etName.setText(presetName);
        }
        if (values != null) {
            EditText[] fields = {etGrams, etKcal, etCarbs, etProtein, etFat, etSugar};
            for (int i = 0; i < fields.length; i++) {
                fields[i].setText(String.valueOf(FoodItem.round(values[i])));
            }
        }

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(view)
                .setPositiveButton("Save & add", null)   // the click is set below (so a wrong field doesn't close it)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String name = etName.getText().toString().trim();
                        double grams = parse(etGrams);
                        double kcal = parse(etKcal);
                        double carbs = parse(etCarbs);
                        double protein = parse(etProtein);
                        double fat = parse(etFat);
                        double sugar = parse(etSugar);

                        // Validation: name + grams + calories are a must, values must make sense
                        if (name.length() < 2) {
                            etName.setError("Write the name");
                            return;
                        }
                        if (grams <= 0 || grams > 3000) {
                            etGrams.setError("1-3000 g");
                            return;
                        }
                        if (kcal <= 0 || kcal > 5000) {
                            etKcal.setError("Write the calories");
                            return;
                        }
                        if (carbs + protein + fat > grams) {
                            etCarbs.setError("Carbs + protein + fat can't be more than the grams");
                            return;
                        }
                        if (sugar > carbs) {
                            etSugar.setError("Sugar is part of the carbs (can't be more)");
                            return;
                        }

                        // Values of the amount → values for 100g (the app always keeps "per 100g")
                        double k = 100.0 / grams;
                        FoodItem food = new FoodItem(name, "", kcal * k, protein * k, carbs * k, fat * k, sugar * k, grams);
                        save(food);
                        Toast.makeText(activity, name + " saved in your foods ✓", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        listener.onFoodCreated(food, grams);
                    }
                });
            }
        });
        dialog.show();
    }

    // Users / uid / CustomFoods / name
    public static void save(FoodItem food) {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.child("CustomFoods").child(DiaryEntry.safeKey(food.getName())).setValue(food.toMap());
    }

    // Text of a field → number (0 if empty or wrong)
    private static double parse(EditText field) {
        try {
            return Double.parseDouble(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
