package com.example.glumotion.food;

import com.example.glumotion.common.ApiHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Food data from FREE APIs:
 * - Search:  USDA FoodData Central - simple foods with ENGLISH names ("Rice, white, cooked")
 *            → if it doesn't answer: Open Food Facts, English names first (no more French names)
 * - Barcode: Open Food Facts (world database - the same products as il.openfoodfacts.org + all countries)
 * Docs: https://fdc.nal.usda.gov/api-guide  •  https://openfoodfacts.github.io/openfoodfacts-server/api/
 */
public class FoodApi {

    // Only the fields we need (smaller answer = faster)
    private static final String FIELDS = "code,product_name,product_name_en,product_name_he,generic_name_en,brands,nutriments,serving_quantity,lang";

    /**
     * USDA key: "DEMO_KEY" works for testing (a few searches every hour).
     * A free personal key (1000 searches every hour): https://fdc.nal.usda.gov/api-key-signup → paste it here.
     */
    private static final String USDA_KEY = "DEMO_KEY";

    // The screen gets the list of foods through this interface
    public interface FoodListCallback {
        void onFoods(ArrayList<FoodItem> foods);

        void onError(String error);
    }

    // The screen gets one food (barcode) through this interface
    public interface FoodCallback {
        void onFood(FoodItem food);

        void onNotFound();

        void onError(String error);
    }

    // Searches foods by name → English names (USDA first, Open Food Facts if USDA doesn't answer)
    public static void search(final String query, final FoodListCallback callback) {
        String encoded = encode(query);
        String url = "https://api.nal.usda.gov/fdc/v1/foods/search?api_key=" + USDA_KEY + "&query=" + encoded
                + "&pageSize=25&dataType=Foundation,SR%20Legacy,Survey%20(FNDDS)";
        ApiHelper.get(url, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                ArrayList<FoodItem> list = parseUsda(response);
                if (list.isEmpty()) {
                    searchOpenFoodFacts(query, callback);
                } else {
                    callback.onFoods(list);
                }
            }

            @Override
            public void onError(String error) {
                searchOpenFoodFacts(query, callback);
            }
        });
    }

    // Open Food Facts search (English names first)
    private static void searchOpenFoodFacts(String query, final FoodListCallback callback) {
        String url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=" + encode(query)
                + "&search_simple=1&action=process&json=1&lc=en&sort_by=unique_scans_n&page_size=40&fields=" + FIELDS;
        ApiHelper.get(url, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onFoods(parseSearch(response));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Text → URL text ("white rice" → "white+rice")
    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * USDA answer: {"foods":[{"description":"Rice, white, cooked","foodNutrients":[{"nutrientId":1008,"value":130}...]}]}
     * Values are per 100g. Nutrient ids: 1008 kcal, 1003 protein, 1005 carbs, 1004 fat, 2000 / 1063 sugar.
     */
    private static ArrayList<FoodItem> parseUsda(String json) {
        ArrayList<FoodItem> list = new ArrayList<>();
        try {
            JSONArray foods = new JSONObject(json).optJSONArray("foods");
            if (foods == null) {
                return list;
            }
            for (int i = 0; i < foods.length(); i++) {
                JSONObject f = foods.getJSONObject(i);
                String name = niceName(f.optString("description", ""));
                JSONArray nutrients = f.optJSONArray("foodNutrients");
                if (name.isEmpty() || nutrients == null) {
                    continue;
                }
                double kcal = -1, protein = 0, carbs = 0, fat = 0, sugar = 0;
                for (int k = 0; k < nutrients.length(); k++) {
                    JSONObject n = nutrients.getJSONObject(k);
                    int id = n.optInt("nutrientId", 0);
                    double value = safe(n.optDouble("value", 0));
                    String unit = n.optString("unitName", "");
                    if ((id == 1008 || id == 2047 || id == 2048) && kcal < 0) {
                        kcal = value;
                        if (unit.equalsIgnoreCase("kJ")) {
                            kcal = value / 4.184;
                        }
                    } else if (id == 1003) {
                        protein = value;
                    } else if (id == 1005) {
                        carbs = value;
                    } else if (id == 1004) {
                        fat = value;
                    } else if (id == 2000 || id == 1063) {
                        sugar = value;
                    }
                }
                if (kcal < 0) {
                    continue;
                }
                list.add(new FoodItem(name, "", kcal, protein, carbs, fat, sugar, 100));
            }
        } catch (Exception e) {
            // bad JSON → empty list
        }
        return list;
    }

    // "RICE, WHITE, COOKED" / "Rice, white, cooked" → "Rice, white, cooked"
    private static String niceName(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return t;
        }
        t = t.toLowerCase();
        return t.substring(0, 1).toUpperCase() + t.substring(1);
    }

    // Finds a product by its barcode (Open Food Facts world database - Israeli products are there too)
    public static void findByBarcode(final String barcode, final FoodCallback callback) {
        String url = "https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json?fields=" + FIELDS;
        ApiHelper.get(url, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                FoodItem food = parseProduct(response);
                if (food == null) {
                    callback.onNotFound();
                } else {
                    food.setBarcode(barcode);
                    callback.onFood(food);
                }
            }

            @Override
            public void onError(String error) {
                // 404 = product not in the database
                if (error.contains("404")) {
                    callback.onNotFound();
                } else {
                    callback.onError(error);
                }
            }
        });
    }

    /**
     * Turns the search JSON into a list of FoodItem.
     * Products with an ENGLISH name come first; products that have only a French / other name are dropped
     * when there are enough English results.
     */
    private static ArrayList<FoodItem> parseSearch(String json) {
        ArrayList<FoodItem> english = new ArrayList<>();
        ArrayList<FoodItem> others = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray products = root.optJSONArray("products");
            if (products == null) {
                return english;
            }
            for (int i = 0; i < products.length(); i++) {
                JSONObject p = products.getJSONObject(i);
                FoodItem food = productToFood(p);
                if (food == null) {
                    continue;
                }
                if (hasEnglishName(p)) {
                    english.add(food);
                } else {
                    others.add(food);
                }
            }
        } catch (Exception e) {
            // bad JSON → empty list
        }
        if (english.size() < 5) {
            english.addAll(others);
        }
        return english;
    }

    // true if the product has an English name
    private static boolean hasEnglishName(JSONObject p) {
        return !p.optString("product_name_en", "").trim().isEmpty() || "en".equals(p.optString("lang", ""));
    }

    // Barcode answer: {"status":1, "product":{...}}
    private static FoodItem parseProduct(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("status", 0) != 1) {
                return null;
            }
            return productToFood(root.getJSONObject("product"));
        } catch (Exception e) {
            return null;
        }
    }

    // One product JSON → FoodItem (null if it has no name or no calories). The English name comes first.
    private static FoodItem productToFood(JSONObject p) {
        String name = p.optString("product_name_en", "").trim();
        if (name.isEmpty()) {
            name = p.optString("generic_name_en", "").trim();
        }
        if (name.isEmpty()) {
            name = p.optString("product_name", "").trim();
        }
        if (name.isEmpty()) {
            name = p.optString("product_name_he", "").trim();
        }
        JSONObject n = p.optJSONObject("nutriments");
        if (name.isEmpty() || n == null) {
            return null;
        }

        double kcal = n.optDouble("energy-kcal_100g", -1);
        if (kcal < 0) {
            // Some products have only kJ → 1 kcal = 4.184 kJ
            double kj = n.optDouble("energy_100g", -1);
            if (kj < 0) {
                return null;
            }
            kcal = kj / 4.184;
        }

        String brand = p.optString("brands", "");
        if (brand.contains(",")) {
            brand = brand.substring(0, brand.indexOf(","));
        }
        double serving = p.optDouble("serving_quantity", 100);
        if (serving <= 0 || Double.isNaN(serving)) {
            serving = 100;
        }

        FoodItem food = new FoodItem(name.trim(), brand.trim(), kcal,
                safe(n.optDouble("proteins_100g", 0)),
                safe(n.optDouble("carbohydrates_100g", 0)),
                safe(n.optDouble("fat_100g", 0)),
                safe(n.optDouble("sugars_100g", 0)),
                serving);
        food.setBarcode(p.optString("code", ""));
        return food;
    }

    // NaN (missing value) → 0
    private static double safe(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return value;
    }
}
