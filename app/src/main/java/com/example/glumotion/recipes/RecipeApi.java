package com.example.glumotion.recipes;

import com.example.glumotion.common.ApiHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Recipes from TheMealDB - a FREE recipes API with photos (the test key "1" is free for education).
 * Docs: https://www.themealdb.com/api.php
 *   by category: /filter.php?c=Seafood   (id, name, photo)
 *   search:      /search.php?s=chicken   (full recipes)
 *   one recipe:  /lookup.php?i=52772     (full recipe: ingredients, steps, video)
 */
public class RecipeApi {

    private static final String BASE = "https://www.themealdb.com/api/json/v1/1/";

    // Categories shown as buttons (the healthier ones first, Dessert last with a warning)
    public static final String[] CATEGORIES = {"Seafood", "Chicken", "Vegetarian", "Vegan", "Beef", "Lamb",
            "Breakfast", "Starter", "Side", "Pasta", "Dessert"};

    // Answer of a list request
    public interface ListCallback {
        void onRecipes(ArrayList<Recipe> recipes);

        void onError(String error);
    }

    // Answer of one recipe
    public interface RecipeCallback {
        void onRecipe(Recipe recipe);

        void onError(String error);
    }

    // Recipes of one category (only id, name and photo - the details come with lookup)
    public static void byCategory(final String category, final ListCallback callback) {
        ApiHelper.get(BASE + "filter.php?c=" + category, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                ArrayList<Recipe> list = parseList(response);
                for (int i = 0; i < list.size(); i++) {
                    list.get(i).setCategory(category);
                }
                callback.onRecipes(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Search by name (full recipes)
    public static void search(String text, final ListCallback callback) {
        String encoded = text;
        try {
            encoded = URLEncoder.encode(text, "UTF-8");
        } catch (Exception e) {
            // keep the text as it is
        }
        ApiHelper.get(BASE + "search.php?s=" + encoded, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onRecipes(parseList(response));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // One full recipe by its id
    public static void lookup(String id, final RecipeCallback callback) {
        ApiHelper.get(BASE + "lookup.php?i=" + id, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                ArrayList<Recipe> list = parseList(response);
                if (list.isEmpty()) {
                    callback.onError("Recipe not found");
                } else {
                    callback.onRecipe(list.get(0));
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // {"meals":[ {...}, {...} ]}  → list of recipes ("meals": null when nothing was found)
    private static ArrayList<Recipe> parseList(String json) {
        ArrayList<Recipe> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (root.isNull("meals")) {
                return list;
            }
            JSONArray meals = root.getJSONArray("meals");
            for (int i = 0; i < meals.length(); i++) {
                list.add(Recipe.fromJson(meals.getJSONObject(i)));
            }
        } catch (Exception e) {
            list.clear();
        }
        return list;
    }
}
