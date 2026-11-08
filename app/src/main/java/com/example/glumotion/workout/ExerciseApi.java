package com.example.glumotion.workout;

import com.example.glumotion.common.ApiHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Exercises from wger.de - a FREE open source workout database (no API key needed).
 * Search: https://wger.de/api/v2/exercise/search/?term=bench&language=en
 * Answer: {"suggestions":[{"value":"Bench Press","data":{"name":"Bench Press","category":"Chest"}}]}
 */
public class ExerciseApi {

    // One result = name + muscle group
    public static class Result {
        public String name;
        public String category;

        public Result(String name, String category) {
            this.name = name;
            this.category = category;
        }
    }

    // The screen gets the results through this interface
    public interface ExerciseCallback {
        void onResults(ArrayList<Result> results);

        void onError(String error);
    }

    // Searches exercises by name
    public static void search(String term, final ExerciseCallback callback) {
        String encoded;
        try {
            encoded = URLEncoder.encode(term, "UTF-8");
        } catch (Exception e) {
            encoded = term;
        }
        String url = "https://wger.de/api/v2/exercise/search/?language=en&term=" + encoded;
        ApiHelper.get(url, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onResults(parse(response));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // JSON → list of results (without duplicates)
    private static ArrayList<Result> parse(String json) {
        ArrayList<Result> list = new ArrayList<>();
        try {
            JSONArray suggestions = new JSONObject(json).optJSONArray("suggestions");
            if (suggestions == null) {
                return list;
            }
            for (int i = 0; i < suggestions.length(); i++) {
                JSONObject item = suggestions.getJSONObject(i);
                JSONObject data = item.optJSONObject("data");
                String name = item.optString("value", "");
                String category = "Other";
                if (data != null) {
                    if (name.isEmpty()) {
                        name = data.optString("name", "");
                    }
                    category = data.optString("category", "Other");
                }
                if (!name.isEmpty() && !contains(list, name)) {
                    list.add(new Result(name, category));
                }
            }
        } catch (Exception e) {
            // bad JSON → empty list
        }
        return list;
    }

    // True if the name is already in the list
    private static boolean contains(ArrayList<Result> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
