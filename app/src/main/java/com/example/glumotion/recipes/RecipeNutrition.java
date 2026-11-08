package com.example.glumotion.recipes;

import java.util.ArrayList;

/**
 * TheMealDB gives the ingredients and their amounts, but no calories.
 * This class ESTIMATES the nutrition of one portion:
 * 1) every amount ("2 tbs", "200g", "1 cup", "1/2 lb", "3") is turned into grams
 * 2) every ingredient is matched to a table of common ingredients (values per 100g)
 * 3) all are summed and divided by the number of portions (TheMealDB recipes are usually for 4)
 * The result is an estimate - the user can fix it before adding it to his diary.
 */
public class RecipeNutrition {

    public static final int PORTIONS = 4;

    /**
     * keyword, kcal, carbs, protein, fat, sugar (per 100g), grams of ONE piece.
     * More exact words come FIRST ("sweet potato" before "potato", "olive oil" before "oil").
     */
    private static final String[][] TABLE = {
            {"black pepper", "251", "64", "10", "3", "0.6", "1"}, {"salt", "0", "0", "0", "0", "0", "1"},
            {"water", "0", "0", "0", "0", "0", "240"}, {"stock", "5", "0.5", "0.5", "0.2", "0.3", "240"},
            {"broth", "5", "0.5", "0.5", "0.2", "0.3", "240"}, {"olive oil", "884", "0", "0", "100", "0", "15"},
            {"oil", "884", "0", "0", "100", "0", "15"}, {"peanut butter", "588", "20", "25", "50", "9", "16"}, {"butter", "717", "0", "1", "81", "0", "10"},
            {"brown sugar", "380", "98", "0", "0", "97", "12"}, {"sugar", "387", "100", "0", "0", "100", "12"},
            {"honey", "304", "82", "0", "0", "82", "21"}, {"syrup", "260", "67", "0", "0", "60", "20"},
            {"jam", "250", "63", "0.5", "0", "50", "20"}, {"chocolate", "546", "60", "5", "31", "48", "30"},
            {"breadcrumbs", "395", "72", "13", "5", "6", "30"}, {"flour", "364", "76", "10", "1", "0.3", "125"},
            {"oats", "389", "66", "17", "7", "1", "40"}, {"rice vinegar", "20", "1", "0", "0", "0.4", "15"}, {"rice", "360", "80", "7", "1", "0", "185"},
            {"spaghetti", "371", "75", "13", "1.5", "3", "100"}, {"pasta", "371", "75", "13", "1.5", "3", "100"},
            {"penne", "371", "75", "13", "1.5", "3", "100"}, {"noodle", "371", "75", "13", "1.5", "3", "100"},
            {"bread", "265", "49", "9", "3", "5", "30"}, {"tortilla", "310", "52", "8", "8", "2", "45"},
            {"sweet potato", "86", "20", "1.6", "0.1", "4.2", "130"}, {"potato", "77", "17", "2", "0.1", "0.8", "170"},
            {"chicken", "165", "0", "31", "3.6", "0", "170"}, {"beef", "250", "0", "26", "15", "0", "150"},
            {"steak", "250", "0", "26", "15", "0", "200"}, {"mince", "250", "0", "26", "15", "0", "150"},
            {"lamb", "282", "0", "25", "20", "0", "150"}, {"bacon", "540", "1", "37", "42", "0", "15"},
            {"pork", "260", "0", "27", "16", "0", "150"}, {"sausage", "300", "3", "12", "27", "1", "60"},
            {"salmon", "208", "0", "20", "13", "0", "150"}, {"tuna", "130", "0", "28", "1", "0", "120"},
            {"prawn", "99", "0", "24", "0.3", "0", "15"}, {"shrimp", "99", "0", "24", "0.3", "0", "15"},
            {"fish", "105", "0", "23", "1", "0", "150"}, {"cod", "82", "0", "18", "0.7", "0", "150"},
            {"eggplant", "25", "6", "1", "0.2", "3.5", "250"}, {"egg", "155", "1", "13", "11", "1", "50"}, {"coconut milk", "230", "6", "2", "24", "3", "400"},
            {"milk", "60", "5", "3", "3", "5", "240"}, {"cream", "340", "3", "2", "36", "3", "15"},
            {"yogurt", "60", "4", "10", "0.4", "3", "170"}, {"yoghurt", "60", "4", "10", "0.4", "3", "170"},
            {"parmesan", "430", "4", "38", "29", "1", "10"}, {"cheese", "400", "1", "25", "33", "0.5", "30"},
            {"mayonnaise", "680", "1", "1", "75", "1", "15"}, {"ketchup", "112", "26", "1", "0.1", "22", "15"},
            {"soy sauce", "53", "5", "8", "0.6", "0.4", "15"}, {"vinegar", "20", "1", "0", "0", "0.4", "15"},
            {"tomato puree", "82", "19", "4", "0.5", "12", "15"}, {"tomato paste", "82", "19", "4", "0.5", "12", "15"},
            {"tomato", "18", "4", "1", "0.2", "2.6", "120"}, {"onion", "40", "9", "1", "0.1", "4", "110"},
            {"garlic", "149", "33", "6", "0.5", "1", "5"}, {"ginger", "80", "18", "2", "0.8", "1.7", "10"},
            {"carrot", "41", "10", "1", "0.2", "5", "60"}, {"red pepper", "31", "6", "1", "0.3", "4", "120"},
            {"green pepper", "20", "5", "1", "0.2", "2.4", "120"}, {"bell pepper", "31", "6", "1", "0.3", "4", "120"},
            {"chilli", "40", "9", "2", "0.4", "5", "15"}, {"mushroom", "22", "3", "3", "0.3", "2", "15"},
            {"spinach", "23", "4", "3", "0.4", "0.4", "30"}, {"lettuce", "15", "3", "1", "0.2", "1", "30"},
            {"cabbage", "25", "6", "1", "0.1", "3", "30"}, {"courgette", "17", "3", "1", "0.3", "2.5", "200"},
            {"zucchini", "17", "3", "1", "0.3", "2.5", "200"}, {"aubergine", "25", "6", "1", "0.2", "3.5", "250"},
            {"cucumber", "15", "3.6", "0.7", "0.1", "1.7", "200"},
            {"chickpea", "164", "27", "9", "2.6", "4.8", "100"}, {"lentil", "116", "20", "9", "0.4", "1.8", "100"},
            {"bean", "127", "23", "9", "0.5", "0.3", "100"}, {"peas", "81", "14", "5", "0.4", "6", "80"},
            {"corn", "86", "19", "3", "1.2", "6", "90"}, {"lemon", "29", "9", "1", "0.3", "2.5", "60"},
            {"lime", "30", "11", "0.7", "0.2", "1.7", "45"}, {"apple", "52", "14", "0.3", "0.2", "10", "180"},
            {"banana", "89", "23", "1", "0.3", "12", "120"}, {"almond", "579", "22", "21", "50", "4.4", "30"},
            {"walnut", "654", "14", "15", "65", "2.6", "30"}, {"peanut", "567", "16", "26", "49", "4", "30"},
            {"nut", "600", "20", "20", "52", "4", "30"}, {"avocado", "160", "8.5", "2", "15", "0.7", "150"},
            {"cumin", "0", "0", "0", "0", "0", "2"}, {"paprika", "0", "0", "0", "0", "0", "2"}, {"cinnamon", "0", "0", "0", "0", "0", "2"},
            {"oregano", "0", "0", "0", "0", "0", "1"}, {"thyme", "0", "0", "0", "0", "0", "1"}, {"parsley", "36", "6", "3", "0.8", "0.9", "5"},
            {"coriander", "23", "4", "2", "0.5", "0.9", "5"}, {"basil", "23", "3", "3", "0.6", "0.3", "2"}
    };

    /**
     * Estimate of ONE portion: {grams, kcal, carbs, protein, fat, sugar, matched ingredients, all ingredients}
     */
    public static double[] estimate(Recipe recipe) {
        ArrayList<String> ings = recipe.getIngredients();
        ArrayList<String> meas = recipe.getMeasures();
        double grams = 0, kcal = 0, carbs = 0, protein = 0, fat = 0, sugar = 0;
        int matched = 0;
        for (int i = 0; i < ings.size(); i++) {
            String name = ings.get(i).toLowerCase();
            int row = findRow(name);
            if (row < 0) {
                continue;
            }
            matched++;
            double g = toGrams(meas.get(i).toLowerCase(), name, Double.parseDouble(TABLE[row][6]));
            grams = grams + g;
            kcal = kcal + Double.parseDouble(TABLE[row][1]) * g / 100;
            carbs = carbs + Double.parseDouble(TABLE[row][2]) * g / 100;
            protein = protein + Double.parseDouble(TABLE[row][3]) * g / 100;
            fat = fat + Double.parseDouble(TABLE[row][4]) * g / 100;
            sugar = sugar + Double.parseDouble(TABLE[row][5]) * g / 100;
        }
        return new double[]{grams / PORTIONS, kcal / PORTIONS, carbs / PORTIONS, protein / PORTIONS,
                fat / PORTIONS, sugar / PORTIONS, matched, ings.size()};
    }

    // Row of the ingredient in the table (-1 if unknown)
    private static int findRow(String name) {
        for (int i = 0; i < TABLE.length; i++) {
            if (name.contains(TABLE[i][0])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Amount text → grams.
     * "200g" → 200 • "1kg" → 1000 • "2 tbs" → 30 • "1 tsp" → 5 • "1 cup" → 125-240 • "1 lb" → 454
     * "1 can" → 400 • "3" (pieces) → 3 × piece • "to taste" / "pinch" → 1
     */
    public static double toGrams(String m, String ingredient, double piece) {
        double n = leadingNumber(m);
        boolean hasNumber = n > 0;
        if (!hasNumber) {
            n = 1;
        }
        if (m.contains("kg")) {
            return n * 1000;
        }
        if (m.contains("ml")) {
            return n;
        }
        if (digitThen(m, 'g') || m.contains("gram")) {
            return n;
        }
        if (m.contains("litre") || m.contains("liter") || digitThen(m, 'l')) {
            return n * 1000;
        }
        if (m.contains("tbsp") || m.contains("tbs") || m.contains("tablespoon")) {
            return n * 15;
        }
        if (m.contains("tsp") || m.contains("teaspoon")) {
            return n * 5;
        }
        if (m.contains("cup")) {
            if (ingredient.contains("flour")) {
                return n * 125;
            }
            if (ingredient.contains("rice")) {
                return n * 185;
            }
            return n * 220;
        }
        if (m.contains("lb") || m.contains("pound")) {
            return n * 454;
        }
        if (m.contains("oz")) {
            return n * 28;
        }
        if (m.contains("can") || m.contains("tin")) {
            return n * 400;
        }
        if (m.contains("handful")) {
            return n * 30;
        }
        if (m.contains("slice")) {
            return n * 30;
        }
        if (m.contains("pinch") || m.contains("taste") || m.contains("sprinkl") || m.contains("dash")) {
            return 1;
        }
        if (m.contains("clove")) {
            return n * 5;
        }
        return n * piece;
    }

    // true for "200g" (a digit and right after it the letter)
    private static boolean digitThen(String text, char letter) {
        for (int i = 0; i < text.length() - 1; i++) {
            if (Character.isDigit(text.charAt(i)) && text.charAt(i + 1) == letter
                    && (i + 2 >= text.length() || !Character.isLetter(text.charAt(i + 2)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Number at the start: "2" → 2, "1/2" → 0.5, "1 1/2" → 1.5, "½" → 0.5, "2.5" → 2.5 (0 if none)
     */
    public static double leadingNumber(String m) {
        m = m.replace("½", " 1/2").replace("¼", " 1/4").replace("¾", " 3/4").trim();
        String[] parts = m.split(" ");
        double total = 0;
        for (int i = 0; i < parts.length && i < 2; i++) {
            String p = parts[i];
            // "200g" → "200"
            String digits = "";
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (Character.isDigit(c) || c == '.' || c == '/') {
                    digits = digits + c;
                } else {
                    break;
                }
            }
            if (digits.isEmpty()) {
                break;
            }
            try {
                if (digits.contains("/")) {
                    String[] f = digits.split("/");
                    total = total + Double.parseDouble(f[0]) / Double.parseDouble(f[1]);
                } else {
                    total = total + Double.parseDouble(digits);
                }
            } catch (Exception e) {
                break;
            }
            if (digits.length() < p.length()) {
                break;   // "200g" - the unit is glued to the number
            }
        }
        return total;
    }
}
