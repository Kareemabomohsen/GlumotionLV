package com.example.glumotion.recipes;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * One recipe from TheMealDB (free recipes API with photos):
 * name, category, country, photo, preparation steps, YouTube video and ingredients with amounts.
 */
public class Recipe {

    private String id;
    private String title;
    private String category = "";
    private String area = "";
    private String imageUrl = "";
    private String instructions = "";
    private String youtube = "";
    private String source = "";
    private final ArrayList<String> ingredients = new ArrayList<>();
    private final ArrayList<String> measures = new ArrayList<>();

    // Constructor of a short recipe (the list gives only id, name and photo)
    public Recipe(String id, String title, String imageUrl) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
    }

    /**
     * Full recipe from the JSON of TheMealDB.
     * The ingredients come as strIngredient1..20 and the amounts as strMeasure1..20.
     */
    public static Recipe fromJson(JSONObject m) {
        Recipe r = new Recipe(m.optString("idMeal"), m.optString("strMeal"), m.optString("strMealThumb"));
        r.category = clean(m.optString("strCategory"));
        r.area = clean(m.optString("strArea"));
        r.instructions = clean(m.optString("strInstructions"));
        r.youtube = clean(m.optString("strYoutube"));
        r.source = clean(m.optString("strSource"));
        for (int i = 1; i <= 20; i++) {
            String ing = clean(m.optString("strIngredient" + i));
            if (!ing.isEmpty()) {
                r.ingredients.add(ing);
                r.measures.add(clean(m.optString("strMeasure" + i)));
            }
        }
        return r;
    }

    // JSON "null" → ""
    private static String clean(String text) {
        if (text == null || text.equals("null")) {
            return "";
        }
        return text.trim();
    }

    // Small photo for the list (TheMealDB gives a small version with "/preview")
    public String getSmallImageUrl() {
        if (imageUrl.isEmpty()) {
            return "";
        }
        return imageUrl + "/preview";
    }

    // Preparation split into steps (by lines, empty lines removed)
    public ArrayList<String> getSteps() {
        ArrayList<String> steps = new ArrayList<>();
        String[] lines = instructions.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // some recipes write "STEP 1" in a line by itself
            if (line.length() > 3 && !line.toUpperCase().startsWith("STEP")) {
                steps.add(line);
            }
        }
        return steps;
    }

    // Desserts are full of sugar → a warning for people with diabetes
    public boolean isSweet() {
        return category.equals("Dessert");
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getArea() { return area; }
    public String getImageUrl() { return imageUrl; }
    public String getInstructions() { return instructions; }
    public String getYoutube() { return youtube; }
    public String getSource() { return source; }
    public ArrayList<String> getIngredients() { return ingredients; }
    public ArrayList<String> getMeasures() { return measures; }
    public void setCategory(String category) { this.category = category; }
}
