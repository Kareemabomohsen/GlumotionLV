package com.example.glumotion.chat;

/**
 * One food that the food bot added to the diary (so the user can delete it from the chat).
 * key = the push key of Users / uid / FoodDiary / date / key
 */
public class AddedFood {

    private final String key;
    private final String date;
    private final String name;
    private final int kcal, protein, carbs, fat, sugar;

    // Constructor: where the food is saved + its values (to fix the totals of the bot after deleting)
    public AddedFood(String key, String date, String name, int kcal, int protein, int carbs, int fat, int sugar) {
        this.key = key;
        this.date = date;
        this.name = name;
        this.kcal = kcal;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.sugar = sugar;
    }

    // Getters
    public String getKey() { return key; }
    public String getDate() { return date; }
    public String getName() { return name; }
    public int getKcal() { return kcal; }
    public int getProtein() { return protein; }
    public int getCarbs() { return carbs; }
    public int getFat() { return fat; }
    public int getSugar() { return sugar; }
}
