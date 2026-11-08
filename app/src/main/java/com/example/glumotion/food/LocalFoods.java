package com.example.glumotion.food;

import java.util.ArrayList;

/**
 * Built-in list of common foods (works without internet and fills the gaps of the API:
 * fresh foods like apple, rice, hummus usually don't have a barcode).
 * Values per 100g - from USDA FoodData Central.
 */
public class LocalFoods {

    private static ArrayList<FoodItem> foods;

    // Returns all the built-in foods (built only once)
    public static ArrayList<FoodItem> getAll() {
        if (foods == null) {
            foods = new ArrayList<>();
            //        name                      brand  kcal  prot  carb  fat  sugar serving(g)
            add("Apple", 52, 0.3, 14, 0.2, 10, 180);
            add("Banana", 89, 1.1, 23, 0.3, 12, 120);
            add("Orange", 47, 0.9, 12, 0.1, 9, 150);
            add("Strawberries", 32, 0.7, 7.7, 0.3, 4.9, 150);
            add("Cucumber", 15, 0.7, 3.6, 0.1, 1.7, 100);
            add("Tomato", 18, 0.9, 3.9, 0.2, 2.6, 120);
            add("Israeli salad", 35, 1, 5, 1.5, 3, 200);
            add("Broccoli", 34, 2.8, 7, 0.4, 1.7, 150);
            add("Boiled egg", 155, 13, 1.1, 11, 1.1, 50);
            add("Chicken breast (grilled)", 165, 31, 0, 3.6, 0, 150);
            add("Salmon (baked)", 206, 22, 0, 12, 0, 150);
            add("Tuna in water", 116, 26, 0, 0.8, 0, 100);
            add("Beef (lean, cooked)", 250, 26, 0, 15, 0, 150);
            add("White rice (cooked)", 130, 2.7, 28, 0.3, 0.1, 150);
            add("Brown rice (cooked)", 112, 2.3, 24, 0.8, 0.4, 150);
            add("Bulgur (cooked)", 83, 3.1, 19, 0.2, 0.1, 150);
            add("Lentils (cooked)", 116, 9, 20, 0.4, 1.8, 150);
            add("Chickpeas (cooked)", 164, 8.9, 27, 2.6, 4.8, 150);
            add("Hummus", 166, 7.9, 14, 9.6, 0.3, 60);
            add("Falafel", 333, 13, 32, 18, 0, 70);
            add("Pita bread", 275, 9.1, 56, 1.2, 1.3, 60);
            add("Whole wheat bread", 247, 13, 41, 3.4, 6, 30);
            add("Oats", 389, 17, 66, 7, 1, 40);
            add("Labneh", 160, 7, 4, 13, 3, 50);
            add("Greek yogurt (plain)", 59, 10, 3.6, 0.4, 3.2, 170);
            add("Cottage cheese 5%", 98, 11, 3.4, 4.3, 2.7, 100);
            add("White cheese 5%", 90, 9, 4, 5, 3, 50);
            add("Milk 3%", 60, 3.2, 4.7, 3, 4.7, 240);
            add("Olive oil", 884, 0, 0, 100, 0, 10);
            add("Almonds", 579, 21, 22, 50, 4.4, 30);
            add("Walnuts", 654, 15, 14, 65, 2.6, 30);
            add("Dates", 282, 2.5, 75, 0.4, 63, 24);
            add("Avocado", 160, 2, 8.5, 15, 0.7, 100);
            add("Potato (boiled)", 87, 1.9, 20, 0.1, 0.9, 150);
            add("Sweet potato (baked)", 90, 2, 21, 0.2, 6.5, 150);
            add("Pasta (cooked)", 158, 5.8, 31, 0.9, 0.6, 180);
            add("Shakshuka", 110, 6.5, 6, 7, 3.5, 250);
            add("Tahini", 595, 17, 21, 54, 0.5, 15);
            add("Orange juice", 45, 0.7, 10, 0.2, 8.4, 250);
            add("Cola", 42, 0, 10.6, 0, 10.6, 330);
            add("Glucose tablets", 380, 0, 95, 0, 95, 4);
        }
        return foods;
    }

    // Helper: adds one food to the list
    private static void add(String name, double kcal, double protein, double carbs, double fat, double sugar, double serving) {
        foods.add(new FoodItem(name, "", kcal, protein, carbs, fat, sugar, serving));
    }

    // Foods whose name contains the text
    public static ArrayList<FoodItem> search(String text) {
        ArrayList<FoodItem> result = new ArrayList<>();
        String lower = text.toLowerCase().trim();
        for (int i = 0; i < getAll().size(); i++) {
            FoodItem f = getAll().get(i);
            if (f.getName().toLowerCase().contains(lower)) {
                result.add(f);
            }
        }
        return result;
    }

    /**
     * Food suggestions for the rest of the day (shown under the meals):
     * - only diabetes-friendly foods (low sugar)
     * - if the user needs more protein → high protein foods first
     * - the portion must fit in the calories that are left
     */
    public static ArrayList<FoodItem> suggest(int kcalLeft, int proteinLeft, int count) {
        ArrayList<FoodItem> result = new ArrayList<>();
        ArrayList<FoodItem> all = getAll();
        boolean needProtein = proteinLeft > 20;

        // First round: protein foods (if needed). Second round: everything friendly.
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < all.size() && result.size() < count; i++) {
                FoodItem f = all.get(i);
                double portionKcal = f.kcalFor(f.getServingGrams());
                boolean fits = kcalLeft <= 0 || portionKcal <= kcalLeft;
                boolean highProtein = f.proteinFor(f.getServingGrams()) >= 10;
                boolean ok = f.isDiabetesFriendly() && fits && !result.contains(f);
                if (round == 0 && needProtein && !highProtein) {
                    ok = false;
                }
                if (ok) {
                    result.add(f);
                }
            }
        }
        return result;
    }
}
