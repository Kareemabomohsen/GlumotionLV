package com.example.glumotion.food;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates the daily calories and macros of the user (used by the questions and the profile).
 * Mifflin-St Jeor formula:  BMR = 10×kg + 6.25×cm − 5×age + 5 (male) / −161 (female)
 * TDEE = BMR × activity (Beginner 1.375, Intermediate 1.55, Advanced 1.725)
 * Goal: Bulking +400 kcal, Cutting −500 kcal, Maintaining ±0
 */
public class NutritionCalculator {

    // Returns {calories, protein, carbs, fat}
    public static int[] calculate(double weight, double height, int age, String gender, String goal, String level) {
        if (age <= 0) {
            age = 25;
        }
        double bmr = 10 * weight + 6.25 * height - 5 * age;
        if ("Female".equals(gender)) {
            bmr = bmr - 161;
        } else {
            bmr = bmr + 5;
        }

        double activity = 1.375;
        if ("Intermediate".equals(level)) {
            activity = 1.55;
        } else if ("Advanced".equals(level)) {
            activity = 1.725;
        }
        double calories = bmr * activity;

        if ("Bulking".equals(goal)) {
            calories = calories + 400;
        } else if ("Cutting".equals(goal)) {
            calories = calories - 500;
        }

        int kcal = (int) calories;
        int protein = (int) (weight * 2.0);
        int fat = (int) (calories * 0.25 / 9);
        // Diabetics: carbs = what is left (protein & carbs 4 kcal/g, fat 9 kcal/g)
        int carbs = (kcal - protein * 4 - fat * 9) / 4;
        if (carbs < 50) {
            carbs = 50;
        }
        return new int[]{kcal, protein, carbs, fat};
    }

    // The same result as a HashMap for Users / uid / Nutrition (saved as text like before)
    public static Map<String, Object> toMap(int[] values) {
        Map<String, Object> map = new HashMap<>();
        map.put("targetCalories", String.valueOf(values[0]));
        map.put("protein", String.valueOf(values[1]));
        map.put("carbs", String.valueOf(values[2]));
        map.put("fats", String.valueOf(values[3]));
        return map;
    }

    // BMI = kg / m²
    public static double bmi(double weight, double heightCm) {
        if (heightCm <= 0) {
            return 0;
        }
        double m = heightCm / 100.0;
        return Math.round(weight / (m * m) * 10) / 10.0;
    }

    // BMI category
    public static String bmiCategory(double bmi) {
        if (bmi <= 0) {
            return "-";
        }
        if (bmi < 18.5) {
            return "Underweight";
        }
        if (bmi < 25) {
            return "Normal";
        }
        if (bmi < 30) {
            return "Overweight";
        }
        return "Obese";
    }
}
