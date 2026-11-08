package com.example.glumotion.food;

import com.example.glumotion.common.FRef;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;

/**
 * One food that the user ate (a row in the food diary).
 * Firebase:  Users / uid / FoodDiary / 2026-11-02 / entryId  →  {name, meal, grams, kcal, protein, carbs, fat, sugar, timestamp}
 * Every day has its own folder, so the user can go back to previous days.
 */
public class DiaryEntry {

    public static final String[] MEALS = {"Breakfast", "Lunch", "Dinner", "Snacks"};

    private String id;
    private String name;
    private String meal;
    private double grams;
    private int kcal;
    private int protein;
    private int carbs;
    private int fat;
    private int sugar;
    private long timestamp;
    private String barcode;

    // Empty constructor - needed by Firebase
    public DiaryEntry() {
    }

    // Users / uid / FoodDiary / date
    public static DatabaseReference getDayRef(String dateKey) {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return null;
        }
        return userRef.child("FoodDiary").child(dateKey);
    }

    /**
     * Adds a food to the diary of a day and remembers it in "Recent foods".
     * Returns the Firebase Task (or null if nobody is logged in).
     */
    public static Task<Void> add(String dateKey, String meal, FoodItem food, double grams) {
        DatabaseReference dayRef = getDayRef(dateKey);
        if (dayRef == null) {
            return null;
        }
        String key = dayRef.push().getKey();
        saveRecent(food);
        return dayRef.child(key).setValue(food.toDiaryMap(meal, grams));
    }

    /**
     * Same as add, but returns the key of the new food (the food bot needs it to delete the food later).
     * Returns null if nobody is logged in.
     */
    public static String addAndGetKey(String dateKey, String meal, FoodItem food, double grams) {
        DatabaseReference dayRef = getDayRef(dateKey);
        if (dayRef == null) {
            return null;
        }
        String key = dayRef.push().getKey();
        saveRecent(food);
        dayRef.child(key).setValue(food.toDiaryMap(meal, grams));
        return key;
    }

    // Users / uid / RecentFoods / food_name  (last foods the user ate - fast to add again)
    public static void saveRecent(FoodItem food) {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        String key = safeKey(food.getFullName());
        java.util.Map<String, Object> map = food.toMap();
        map.put("lastUsed", System.currentTimeMillis());
        userRef.child("RecentFoods").child(key).setValue(map);
    }

    // Firebase keys can't have . # $ [ ] / → replace them
    public static String safeKey(String text) {
        String key = text.toLowerCase().trim();
        String bad = ".#$[]/";
        for (int i = 0; i < bad.length(); i++) {
            key = key.replace(bad.charAt(i), '_');
        }
        return key;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMeal() { return meal; }
    public void setMeal(String meal) { this.meal = meal; }

    public double getGrams() { return grams; }
    public void setGrams(double grams) { this.grams = grams; }

    public int getKcal() { return kcal; }
    public void setKcal(int kcal) { this.kcal = kcal; }

    public int getProtein() { return protein; }
    public void setProtein(int protein) { this.protein = protein; }

    public int getCarbs() { return carbs; }
    public void setCarbs(int carbs) { this.carbs = carbs; }

    public int getFat() { return fat; }
    public void setFat(int fat) { this.fat = fat; }

    public int getSugar() { return sugar; }
    public void setSugar(int sugar) { this.sugar = sugar; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
