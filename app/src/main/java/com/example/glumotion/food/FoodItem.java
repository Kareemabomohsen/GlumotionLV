package com.example.glumotion.food;

import com.google.firebase.database.Exclude;

import java.util.HashMap;
import java.util.Map;

/**
 * One food (from the API, the barcode, or the built-in list).
 * All nutrition values are per 100 grams.
 */
public class FoodItem {

    private String name;
    private String brand;
    private String barcode;
    private double kcal100;
    private double protein100;
    private double carbs100;
    private double fat100;
    private double sugar100;
    private double servingGrams;     // usual portion (e.g. 1 apple = 180g)
    private boolean diabetesFriendly; // low sugar / low glycemic food

    // Empty constructor - needed by Firebase
    public FoodItem() {
    }

    // Full constructor
    public FoodItem(String name, String brand, double kcal100, double protein100, double carbs100,
                    double fat100, double sugar100, double servingGrams) {
        this.name = name;
        this.brand = brand;
        this.kcal100 = kcal100;
        this.protein100 = protein100;
        this.carbs100 = carbs100;
        this.fat100 = fat100;
        this.sugar100 = sugar100;
        this.servingGrams = servingGrams;
        this.barcode = "";
        // Simple rule: less than 10g sugar in 100g = friendly for diabetics
        this.diabetesFriendly = sugar100 < 10;
    }

    // Calories for a given amount of grams
    public double kcalFor(double grams) {
        return kcal100 * grams / 100.0;
    }

    public double proteinFor(double grams) {
        return protein100 * grams / 100.0;
    }

    public double carbsFor(double grams) {
        return carbs100 * grams / 100.0;
    }

    public double fatFor(double grams) {
        return fat100 * grams / 100.0;
    }

    public double sugarFor(double grams) {
        return sugar100 * grams / 100.0;
    }

    // "Hummus • Sabra" or just "Hummus"
    @Exclude
    public String getFullName() {
        if (brand == null || brand.isEmpty()) {
            return name;
        }
        return name + " • " + brand;
    }

    // "166 kcal • P 8g • C 14g • F 9g  (per 100g)"
    @Exclude
    public String getPer100Text() {
        return round(kcal100) + " kcal • P " + round(protein100) + "g • C " + round(carbs100)
                + "g • F " + round(fat100) + "g (100g)";
    }

    // Converts the food + the eaten grams to a HashMap for the food diary in Firebase
    public Map<String, Object> toDiaryMap(String meal, double grams) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", getFullName());
        map.put("meal", meal);
        map.put("grams", grams);
        map.put("kcal", round(kcalFor(grams)));
        map.put("protein", round(proteinFor(grams)));
        map.put("carbs", round(carbsFor(grams)));
        map.put("fat", round(fatFor(grams)));
        map.put("sugar", round(sugarFor(grams)));
        map.put("barcode", barcode);
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }

    // Converts the food (per 100g) to a HashMap to save it in "recent foods"
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("brand", brand);
        map.put("barcode", barcode);
        map.put("kcal100", kcal100);
        map.put("protein100", protein100);
        map.put("carbs100", carbs100);
        map.put("fat100", fat100);
        map.put("sugar100", sugar100);
        map.put("servingGrams", servingGrams);
        map.put("diabetesFriendly", diabetesFriendly);
        return map;
    }

    // Rounds to a whole number (5.6 → 6)
    public static int round(double value) {
        return (int) Math.round(value);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public double getKcal100() { return kcal100; }
    public void setKcal100(double kcal100) { this.kcal100 = kcal100; }

    public double getProtein100() { return protein100; }
    public void setProtein100(double protein100) { this.protein100 = protein100; }

    public double getCarbs100() { return carbs100; }
    public void setCarbs100(double carbs100) { this.carbs100 = carbs100; }

    public double getFat100() { return fat100; }
    public void setFat100(double fat100) { this.fat100 = fat100; }

    public double getSugar100() { return sugar100; }
    public void setSugar100(double sugar100) { this.sugar100 = sugar100; }

    public double getServingGrams() { return servingGrams; }
    public void setServingGrams(double servingGrams) { this.servingGrams = servingGrams; }

    public boolean isDiabetesFriendly() { return diabetesFriendly; }
    public void setDiabetesFriendly(boolean diabetesFriendly) { this.diabetesFriendly = diabetesFriendly; }
}
