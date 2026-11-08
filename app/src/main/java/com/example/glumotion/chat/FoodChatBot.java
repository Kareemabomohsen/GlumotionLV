package com.example.glumotion.chat;

import com.example.glumotion.common.ApiHelper;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.food.DiaryEntry;
import com.example.glumotion.food.FoodAdvisor;
import com.example.glumotion.food.FoodApi;
import com.example.glumotion.food.FoodItem;
import com.example.glumotion.food.LocalFoods;
import com.example.glumotion.workout.Exercise;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * 🍽 The FOOD chat bot.
 * 1) "I ate 150g rice and 2 eggs" / "اكلت 200 غرام رز وبيضتين" → finds the foods, adds them to today's diary
 *    and tells the user if the meal is suitable for his sugar (FoodAdvisor).
 * 2) "can I eat dates?" / "بنفع آكل تمر؟" → checks the food without adding it.
 * 3) A food it doesn't know → searches Open Food Facts → if still not found, asks the user to create it.
 * 4) A meal photo → Gemini AI recognizes the foods (optional, needs a free key - see GeminiHelper).
 * 5) "delete the rice" / "احذف الرز" → deletes that food from today's diary (and every added food has a 🗑 button).
 */
public class FoodChatBot {

    // The chat screen gets the answers through this interface
    public interface Callback {
        void onReply(String text);                         // a message of the bot

        void onUnknownFood(String name, double grams);      // food not found → show "create food" dialog

        void onPhotoFoods(ArrayList<FoodItem> foods, ArrayList<Double> grams, String summary); // foods of a photo → ask before adding
    }

    // Words that mean "I ate this" (work in every bot)
    private static final String[] ATE_WORDS = {"ate", "drank", "i eat",
            "اكلت", "أكلت", "كليت", "فطرت", "تغديت", "تغذيت", "تعشيت", "شربت"};

    // Words that mean "add this food" (only inside the food bot - in the general bot "add" can be a medication...)
    private static final String[] ADD_WORDS = {"add", "log", "ضيف", "اضف", "أضف", "سجل", "سجّل"};

    // Parts that are surely not a food (so the bot doesn't search "low sugar" as a food)
    private static final String[] NOT_FOOD = {"sugar", "glucose", "medic", "pill", "workout", "insulin",
            "سكر", "دواء", "حبوب", "تمرين", "قراءة", "انسولين"};

    // Words of a question "is it OK to eat ...?"
    private static final String[] CHECK_WORDS = {"can i", "is it ok", "good for", "should i have", "is ",
            "ينفع", "بنفع", "مسموح", "مناسب", "بقدر", "بصير", "هل"};

    // Words that mean "delete this food from my diary"
    private static final String[] DELETE_WORDS = {"delete", "remove", "undo", "احذف", "حذف", "امسح", "شيل", "احطف", "حطف"};

    // Words that mean "the last food I added"
    private static final String[] LAST_WORDS = {"last", "اخر", "آخر"};

    private static final String[] NEW_FOOD_WORDS = {"new food", "create food", "custom food", "اكلة جديدة", "أكلة جديدة", "اكله جديده"};

    /**
     * Keyword → name of a food in LocalFoods.
     * Longer / more exact words come FIRST ("brown rice" before "rice", "sweet potato" before "potato").
     */
    private static final String[][] ALIASES = {
            {"brown rice", "Brown rice (cooked)"}, {"رز بني", "Brown rice (cooked)"},
            {"sweet potato", "Sweet potato (baked)"}, {"بطاطا حلوة", "Sweet potato (baked)"},
            {"whole wheat", "Whole wheat bread"}, {"خبز اسمر", "Whole wheat bread"}, {"خبز أسمر", "Whole wheat bread"},
            {"orange juice", "Orange juice"}, {"عصير", "Orange juice"}, {"juice", "Orange juice"},
            {"olive oil", "Olive oil"}, {"زيت", "Olive oil"},
            {"greek yogurt", "Greek yogurt (plain)"}, {"cottage", "Cottage cheese 5%"}, {"كوتج", "Cottage cheese 5%"},
            {"glucose tab", "Glucose tablets"}, {"جلوكوز", "Glucose tablets"},
            {"chickpea", "Chickpeas (cooked)"}, {"حمص حب", "Chickpeas (cooked)"}, {"حمص", "Hummus"}, {"hummus", "Hummus"},
            {"rice", "White rice (cooked)"}, {"رز", "White rice (cooked)"}, {"ارز", "White rice (cooked)"}, {"أرز", "White rice (cooked)"},
            {"apple", "Apple"}, {"تفاح", "Apple"}, {"banana", "Banana"}, {"موز", "Banana"},
            {"orange", "Orange"}, {"برتقال", "Orange"}, {"strawberr", "Strawberries"}, {"فراول", "Strawberries"},
            {"cucumber", "Cucumber"}, {"خيار", "Cucumber"}, {"tomato", "Tomato"}, {"بندورة", "Tomato"}, {"طماطم", "Tomato"},
            {"salad", "Israeli salad"}, {"سلطة", "Israeli salad"}, {"سلطه", "Israeli salad"},
            {"broccoli", "Broccoli"}, {"بروكلي", "Broccoli"}, {"egg", "Boiled egg"}, {"بيض", "Boiled egg"},
            {"chicken", "Chicken breast (grilled)"}, {"دجاج", "Chicken breast (grilled)"}, {"جاج", "Chicken breast (grilled)"},
            {"salmon", "Salmon (baked)"}, {"سلمون", "Salmon (baked)"}, {"tuna", "Tuna in water"}, {"تونة", "Tuna in water"}, {"تونا", "Tuna in water"},
            {"beef", "Beef (lean, cooked)"}, {"steak", "Beef (lean, cooked)"}, {"لحمة", "Beef (lean, cooked)"}, {"لحم", "Beef (lean, cooked)"},
            {"bulgur", "Bulgur (cooked)"}, {"برغل", "Bulgur (cooked)"}, {"lentil", "Lentils (cooked)"}, {"عدس", "Lentils (cooked)"},
            {"falafel", "Falafel"}, {"فلافل", "Falafel"}, {"pita", "Pita bread"}, {"bread", "Pita bread"}, {"خبز", "Pita bread"},
            {"كماج", "Pita bread"}, {"oat", "Oats"}, {"شوفان", "Oats"}, {"labneh", "Labneh"}, {"لبنة", "Labneh"}, {"لبنه", "Labneh"},
            {"yogurt", "Greek yogurt (plain)"}, {"زبادي", "Greek yogurt (plain)"}, {"لبن", "Greek yogurt (plain)"},
            {"cheese", "White cheese 5%"}, {"جبنة", "White cheese 5%"}, {"جبنه", "White cheese 5%"},
            {"milk", "Milk 3%"}, {"حليب", "Milk 3%"}, {"almond", "Almonds"}, {"لوز", "Almonds"}, {"walnut", "Walnuts"}, {"جوز", "Walnuts"},
            {"date", "Dates"}, {"تمر", "Dates"}, {"avocado", "Avocado"}, {"افوكادو", "Avocado"}, {"أفوكادو", "Avocado"},
            {"potato", "Potato (boiled)"}, {"بطاطا", "Potato (boiled)"}, {"pasta", "Pasta (cooked)"}, {"معكرونة", "Pasta (cooked)"},
            {"مكرونة", "Pasta (cooked)"}, {"باستا", "Pasta (cooked)"}, {"shakshuka", "Shakshuka"}, {"شكشوكة", "Shakshuka"},
            {"tahini", "Tahini"}, {"طحينة", "Tahini"}, {"طحينه", "Tahini"}, {"cola", "Cola"}, {"coke", "Cola"}, {"pepsi", "Cola"},
            {"كولا", "Cola"}, {"بيبسي", "Cola"}
    };

    private final ChatBotEngine engine;
    private boolean arabic = false;
    private String defaultMeal = null;   // meal chosen in the "add meal" window (null = by the words / the hour)
    private final ArrayList<AddedFood> justAdded = new ArrayList<>();   // foods of the last answer (for the 🗑 button)

    // The food bot was opened from "+" of a meal → food goes to that meal
    public void setDefaultMeal(String meal) {
        defaultMeal = meal;
    }

    // The meal chosen when the bot was opened (or null)
    public String getDefaultMeal() {
        return defaultMeal;
    }

    // Constructor: the engine has the user's data (calories left, last glucose, custom foods)
    public FoodChatBot(ChatBotEngine engine) {
        this.engine = engine;
    }

    // English or Arabic text
    private String t(String english, String arabicText) {
        if (arabic) {
            return arabicText;
        }
        return english;
    }

    // true if the message says "I ate ..." / "اكلت ..." (used by the general bot)
    public static boolean isLogRequest(String question) {
        String q = ChatBotEngine.clean(toLatinDigits(question.toLowerCase()));
        return ChatBotEngine.has(q, ATE_WORDS);
    }

    // true if the message asks to delete a food ("delete the rice" / "احذف الرز")
    public static boolean isDeleteRequest(String question) {
        String q = ChatBotEngine.clean(question.toLowerCase());
        return ChatBotEngine.has(q, DELETE_WORDS);
    }

    // The foods added by the last answer (the chat screen puts a 🗑 button under that answer). Empties the list.
    public ArrayList<AddedFood> takeJustAdded() {
        ArrayList<AddedFood> list = new ArrayList<>(justAdded);
        justAdded.clear();
        return list;
    }

    /**
     * Main method of the food bot. Returns false if the message is not about adding / checking food
     * (then the chat screen asks the normal engine: calories left, macros, suggestions...).
     */
    public boolean handle(String question, boolean foodMode, final Callback callback) {
        arabic = ChatBotEngine.isArabic(question);
        String q = ChatBotEngine.clean(toLatinDigits(question.toLowerCase())).trim();

        // Delete: in the food bot always, in the general bot only when a known food is named ("delete the rice")
        if (ChatBotEngine.has(q, DELETE_WORDS) && (foodMode || findFood(q) != null)) {
            deleteFromDiary(q, callback);
            return true;
        }
        if (ChatBotEngine.has(q, NEW_FOOD_WORDS)) {
            callback.onReply(t("Sure! Write the name, the grams and the values ✍", "أكيد! اكتب الاسم والغرامات والقيم ✍"));
            callback.onUnknownFood("", 0);
            return true;
        }
        boolean log = ChatBotEngine.has(q, ATE_WORDS) || (foodMode && ChatBotEngine.has(q, ADD_WORDS));
        boolean check = !log && ChatBotEngine.has(q, CHECK_WORDS);
        if (!log && !check) {
            return false;
        }

        // Split "rice and chicken, salad" into parts → one food in every part
        ArrayList<String> parts = splitParts(q);
        ArrayList<FoodItem> foods = new ArrayList<>();
        ArrayList<Double> grams = new ArrayList<>();
        ArrayList<String> unknown = new ArrayList<>();
        ArrayList<Double> unknownGrams = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            FoodItem food = findFood(part);
            if (food != null) {
                foods.add(food);
                grams.add(amountOf(part, food.getServingGrams()));
            } else {
                String name = leftoverName(part);
                if (name.length() >= 2 && name.length() <= 30 && !ChatBotEngine.has(name, NOT_FOOD)) {
                    unknown.add(name);
                    unknownGrams.add(amountOf(part, 0));
                }
            }
        }

        if (check && foods.isEmpty()) {
            return false;   // not a food question → the normal engine answers it
        }
        if (foods.isEmpty() && unknown.isEmpty()) {
            callback.onReply(t("What did you eat? e.g. \"I ate 150g rice and 2 eggs\"", "شو أكلت؟ مثلاً: \"اكلت 150 غرام رز وبيضتين\""));
            return true;
        }

        if (check) {
            // Only checking - nothing is added. No known food → it's another question (the engine answers it)
            if (foods.isEmpty()) {
                return false;
            }
            callback.onReply(t("Checking (not added):\n", "فحص (ما انضافت):\n") + describe(foods, grams, false));
            return true;
        }

        String meal = mealOf(q);
        if (!foods.isEmpty()) {
            callback.onReply(addFoods(foods, grams, meal));
        }
        // Foods not in the app's list → search Open Food Facts one by one
        for (int i = 0; i < unknown.size(); i++) {
            searchOnline(unknown.get(i), unknownGrams.get(i), meal, callback);
        }
        return true;
    }

    /**
     * Adds the foods to today's diary (Users / uid / FoodDiary / today) and returns the answer:
     * list of foods + totals + is it suitable for the sugar.
     */
    public String addFoods(ArrayList<FoodItem> foods, ArrayList<Double> grams, String meal) {
        int kcalLeftBefore = engine.getKcalLeft();
        String today = DateUtil.todayKey();
        for (int i = 0; i < foods.size(); i++) {
            String key = DiaryEntry.addAndGetKey(today, meal, foods.get(i), grams.get(i));
            if (key == null) {
                return t("Please log in first.", "لازم تسجّل دخول أول.");
            }
            FoodItem f = foods.get(i);
            double g = grams.get(i);
            int kcal = FoodItem.round(f.kcalFor(g));
            int protein = FoodItem.round(f.proteinFor(g));
            int carbs = FoodItem.round(f.carbsFor(g));
            int fat = FoodItem.round(f.fatFor(g));
            int sugar = FoodItem.round(f.sugarFor(g));
            engine.addEaten(kcal, protein, carbs, fat, sugar);
            justAdded.add(new AddedFood(key, today, f.getName() + " " + Exercise.formatKg(g) + "g", kcal, protein, carbs, fat, sugar));
        }
        String text = t("✅ Added to " + meal + ":\n", "✅ انضافت لـ " + meal + ":\n");
        return text + describeWithLeft(foods, grams, kcalLeftBefore);
    }

    // List of foods + totals + advice (calories left = what is left now)
    public String describe(ArrayList<FoodItem> foods, ArrayList<Double> grams, boolean added) {
        return describeWithLeft(foods, grams, engine.getKcalLeft());
    }

    // Builds the text: one line per food, totals and the FoodAdvisor check
    private String describeWithLeft(ArrayList<FoodItem> foods, ArrayList<Double> grams, int kcalLeftBefore) {
        String text = "";
        int kcal = 0, carbs = 0, sugar = 0, protein = 0;
        for (int i = 0; i < foods.size(); i++) {
            FoodItem f = foods.get(i);
            double g = grams.get(i);
            text = text + "• " + f.getName() + " " + Exercise.formatKg(g) + "g - " + FoodItem.round(f.kcalFor(g)) + " kcal, C "
                    + FoodItem.round(f.carbsFor(g)) + "g, " + t("sugar ", "سكر ") + FoodItem.round(f.sugarFor(g)) + "g\n";
            kcal = kcal + FoodItem.round(f.kcalFor(g));
            carbs = carbs + FoodItem.round(f.carbsFor(g));
            sugar = sugar + FoodItem.round(f.sugarFor(g));
            protein = protein + FoodItem.round(f.proteinFor(g));
        }
        text = text + t("Total: ", "المجموع: ") + kcal + " kcal • C " + carbs + "g • P " + protein + "g\n\n";
        text = text + FoodAdvisor.check(kcal, carbs, sugar, engine.getLastReading(), kcalLeftBefore, arabic);
        return text;
    }

    // Food not in the list → Open Food Facts → add the first result, or ask the user to create it
    private void searchOnline(final String name, final double grams, final String meal, final Callback callback) {
        FoodApi.search(name, new FoodApi.FoodListCallback() {
            @Override
            public void onFoods(ArrayList<FoodItem> list) {
                FoodItem best = null;
                for (int i = 0; i < list.size() && best == null; i++) {
                    if (list.get(i).getKcal100() > 0) {
                        best = list.get(i);
                    }
                }
                if (best == null) {
                    callback.onReply(t("🔎 I couldn't find \"" + name + "\". Let's add it as a new food:",
                            "🔎 ما لقيت \"" + name + "\". خلينا نضيفها كأكلة جديدة:"));
                    callback.onUnknownFood(name, grams);
                    return;
                }
                double g = grams;
                if (g <= 0) {
                    g = best.getServingGrams();
                }
                if (g <= 0) {
                    g = 100;
                }
                ArrayList<FoodItem> one = new ArrayList<>();
                ArrayList<Double> oneGrams = new ArrayList<>();
                one.add(best);
                oneGrams.add(g);
                callback.onReply(t("🔎 Found online: ", "🔎 لقيتها بالإنترنت: ") + best.getFullName() + "\n" + addFoods(one, oneGrams, meal));
            }

            @Override
            public void onError(String error) {
                callback.onReply(t("No internet to search \"" + name + "\" - you can add it yourself:",
                        "ما في إنترنت عشان أدوّر على \"" + name + "\" - بتقدر تضيفها بنفسك:"));
                callback.onUnknownFood(name, grams);
            }
        });
    }

    // ===================================================================
    //                              DELETE
    // ===================================================================

    /**
     * "delete the rice" / "احذف الرز" / "delete last" → finds that food in TODAY's diary and deletes it
     * (the newest one if the food was eaten more than once).
     */
    private void deleteFromDiary(String q, final Callback callback) {
        // The text without the delete words → the name of the food
        String text = " " + q + " ";
        for (int i = 0; i < DELETE_WORDS.length; i++) {
            text = text.replace(DELETE_WORDS[i], " ");
        }
        final boolean last = ChatBotEngine.has(q, LAST_WORDS);
        FoodItem known = findFood(text);
        String name = leftoverName(text);
        String[] small = {"the", "my", "from", "food", "اكلة", "أكلة", "اكله", "من", "ال"};
        for (int i = 0; i < small.length; i++) {
            name = (" " + name + " ").replace(" " + small[i] + " ", " ").trim();
        }
        if (name.startsWith("ال") && name.length() > 3) {
            name = name.substring(2);
        }
        String knownName = null;
        if (known != null) {
            knownName = known.getName().toLowerCase();
        }
        final String wantedKnown = knownName;
        final String wanted = name.toLowerCase();
        if (!last && wantedKnown == null && wanted.length() < 2) {
            callback.onReply(t("Which food? e.g. \"delete the rice\" or \"delete last\"", "أي أكلة؟ مثلاً \"احذف الرز\" أو \"احذف آخر أكلة\""));
            return;
        }

        final DatabaseReference dayRef = DiaryEntry.getDayRef(DateUtil.todayKey());
        if (dayRef == null) {
            callback.onReply(t("Please log in first.", "لازم تسجّل دخول أول."));
            return;
        }
        dayRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot best = null;
                long bestTime = -1;
                for (DataSnapshot e : snapshot.getChildren()) {
                    String entryName = e.child("name").getValue(String.class);
                    Long time = e.child("timestamp").getValue(Long.class);
                    if (entryName == null) {
                        continue;
                    }
                    String lower = entryName.toLowerCase();
                    boolean match = last || lower.equals(wantedKnown) || (wanted.length() >= 2 && lower.contains(wanted));
                    long entryTime = 0;
                    if (time != null) {
                        entryTime = time;
                    }
                    if (match && entryTime >= bestTime) {
                        best = e;
                        bestTime = entryTime;
                    }
                }
                if (best == null) {
                    callback.onReply(t("I didn't find it in today's diary 🤔", "ما لقيتها بأكل اليوم 🤔"));
                    return;
                }
                DiaryEntry entry = best.getValue(DiaryEntry.class);
                dayRef.child(best.getKey()).removeValue();
                if (entry != null) {
                    engine.addEaten(-entry.getKcal(), -entry.getProtein(), -entry.getCarbs(), -entry.getFat(), -entry.getSugar());
                    callback.onReply(t("🗑 Deleted from " + entry.getMeal() + ": ", "🗑 انحذفت من " + entry.getMeal() + ": ")
                            + entry.getName() + " (" + entry.getKcal() + " kcal)\n"
                            + t("Calories left today: ", "السعرات الضايلة اليوم: ") + engine.getKcalLeft());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onReply(t("Could not delete: ", "ما قدرت أحذف: ") + error.getMessage());
            }
        });
    }

    /**
     * Deletes foods that the bot added (the 🗑 button under the answer) and fixes the totals of the bot.
     */
    public String deleteAdded(ArrayList<AddedFood> foods) {
        String names = "";
        for (int i = 0; i < foods.size(); i++) {
            AddedFood f = foods.get(i);
            DatabaseReference dayRef = DiaryEntry.getDayRef(f.getDate());
            if (dayRef == null) {
                return t("Please log in first.", "لازم تسجّل دخول أول.");
            }
            dayRef.child(f.getKey()).removeValue();
            engine.addEaten(-f.getKcal(), -f.getProtein(), -f.getCarbs(), -f.getFat(), -f.getSugar());
            names = names + "• " + f.getName() + "\n";
        }
        return t("🗑 Deleted:\n", "🗑 انحذفت:\n") + names + t("Calories left today: ", "السعرات الضايلة اليوم: ") + engine.getKcalLeft();
    }

    // ===================================================================
    //                              PHOTO
    // ===================================================================

    /**
     * Sends the meal photo to Gemini. Gemini answers JSON with the foods and their values,
     * then the screen asks the user before adding (AI can make mistakes).
     */
    public void handlePhoto(android.graphics.Bitmap photo, final boolean arabicUser, final Callback callback) {
        arabic = arabicUser;
        if (!GeminiHelper.isEnabled()) {
            callback.onReply(t("📷 To recognize photos, add a free Gemini key in GeminiHelper.java.\n"
                            + "Meanwhile tell me what is in the photo, e.g. \"I ate 200g rice and chicken\".",
                    "📷 عشان أعرف الأكل من الصورة لازم تحط مفتاح Gemini المجاني بملف GeminiHelper.java.\n"
                            + "هلأ احكيلي شو بالصورة، مثلاً: \"اكلت 200 غرام رز ودجاج\"."));
            return;
        }
        callback.onReply(t("🔎 Looking at your meal...", "🔎 عم بشوف الوجبة..."));
        GeminiHelper.analyzeFoodPhoto(photo, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                ArrayList<FoodItem> foods = new ArrayList<>();
                ArrayList<Double> grams = new ArrayList<>();
                parsePhotoJson(response, foods, grams);
                if (foods.isEmpty()) {
                    callback.onReply(t("I couldn't see food in the photo 🤔 Try again or write what you ate.",
                            "ما شفت أكل بالصورة 🤔 جرّب مرة ثانية أو اكتب شو أكلت."));
                    return;
                }
                callback.onPhotoFoods(foods, grams, describe(foods, grams, false));
            }

            @Override
            public void onError(String error) {
                callback.onReply(t("Photo error: " + error + ". Write what you ate instead.", "صار خطأ بالصورة: " + error + ". اكتب شو أكلت."));
            }
        });
    }

    /**
     * Gemini answer → foods. Expected: {"foods":[{"name":"Rice","grams":200,"kcal":260,"carbs":56,"protein":5,"fat":1,"sugar":0}]}
     * The values are for the grams in the photo → converted to "per 100g".
     */
    public static void parsePhotoJson(String text, ArrayList<FoodItem> foods, ArrayList<Double> grams) {
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return;
            }
            JSONArray list = new JSONObject(text.substring(start, end + 1)).getJSONArray("foods");
            for (int i = 0; i < list.length(); i++) {
                JSONObject f = list.getJSONObject(i);
                double g = f.optDouble("grams", 0);
                if (g <= 0) {
                    continue;
                }
                double k = 100.0 / g;
                FoodItem food = new FoodItem(f.optString("name", "Food"), "", f.optDouble("kcal", 0) * k,
                        f.optDouble("protein", 0) * k, f.optDouble("carbs", 0) * k, f.optDouble("fat", 0) * k,
                        f.optDouble("sugar", 0) * k, g);
                foods.add(food);
                grams.add(g);
            }
        } catch (Exception e) {
            foods.clear();
            grams.clear();
        }
    }

    // ===================================================================
    //                          TEXT HELPERS
    // ===================================================================

    // "rice and chicken, salad" → ["rice", "chicken", "salad"]
    private ArrayList<String> splitParts(String q) {
        String text = " " + q + " ";
        String[] separators = {" and ", " with ", " plus ", " مع ", " و"};
        for (int i = 0; i < separators.length; i++) {
            text = text.replace(separators[i], " | ");
        }
        String[] pieces = text.split("\\|");
        ArrayList<String> parts = new ArrayList<>();
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].trim().length() > 0) {
                parts.add(" " + pieces[i].trim() + " ");
            }
        }
        return parts;
    }

    // Finds the food of a part: the user's own foods first, then the keywords table
    private FoodItem findFood(String part) {
        ArrayList<FoodItem> custom = engine.getCustomFoods();
        for (int i = 0; i < custom.size(); i++) {
            String name = custom.get(i).getName().toLowerCase();
            if (name.length() >= 3 && part.contains(name)) {
                return custom.get(i);
            }
        }
        for (int i = 0; i < ALIASES.length; i++) {
            String key = ALIASES[i][0];
            boolean found;
            if (ChatBotEngine.isArabic(key)) {
                found = hasArabicWord(part, key);
            } else {
                found = ChatBotEngine.has(part.trim(), new String[]{key});
            }
            if (found) {
                return findLocal(ALIASES[i][1]);
            }
        }
        return null;
    }

    /**
     * Arabic word search: "التمر" / "بالتمر" / "تمرتين" match "تمر", but "التمرين" (workout) doesn't:
     * the word may have up to 3 extra letters (ال / ب / و / ة / تين).
     */
    private boolean hasArabicWord(String text, String key) {
        if (key.contains(" ")) {
            return text.contains(key);
        }
        String[] words = text.trim().split(" ");
        for (int i = 0; i < words.length; i++) {
            if (words[i].contains(key) && words[i].length() - key.length() <= 3) {
                return true;
            }
        }
        return false;
    }

    // Food of LocalFoods by its exact name
    private FoodItem findLocal(String name) {
        ArrayList<FoodItem> all = LocalFoods.getAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getName().equals(name)) {
                return all.get(i);
            }
        }
        return null;
    }

    /**
     * Amount in grams from a part of the sentence:
     * "150g" / "150 غرام" → 150 • "2 eggs" / "2" → 2 portions • "half" / "نص" → half a portion
     * • "بيضتين" (Arabic dual) → 2 portions • nothing → 1 portion. serving = grams of 1 portion.
     */
    private double amountOf(String part, double serving) {
        double number = firstNumber(part);
        boolean isGrams = ChatBotEngine.has(part.trim(), new String[]{"gram", "ml", "غرام", "جرام"})
                || containsDigitThen(part, 'g') || part.contains(" g ");
        boolean isKg = ChatBotEngine.has(part.trim(), new String[]{"kg", "كيلو"});
        if (number > 0 && isKg) {
            return number * 1000;
        }
        if (number > 0 && isGrams) {
            return number;
        }
        if (serving <= 0) {
            serving = 100;
        }
        if (number > 0) {
            return number * serving;
        }
        if (ChatBotEngine.has(part.trim(), new String[]{"half", "نص", "نصف"})) {
            return serving / 2;
        }
        // "two" / "اثنين" / Arabic dual ending: "بيضتين" = 2 eggs
        if (ChatBotEngine.has(part.trim(), new String[]{"two", "اثنين", "تنين"}) || part.contains("تين ")) {
            return serving * 2;
        }
        return serving;
    }

    // true for "150g" (a digit and right after it the letter)
    private boolean containsDigitThen(String text, char letter) {
        for (int i = 0; i < text.length() - 1; i++) {
            if (Character.isDigit(text.charAt(i)) && text.charAt(i + 1) == letter) {
                return true;
            }
        }
        return false;
    }

    // First number in the text ("ate 150g rice" → 150), 0 if there is none
    private double firstNumber(String text) {
        String digits = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits = digits + c;
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        return Double.parseDouble(digits);
    }

    // The name of an unknown food = the part without numbers, units and "I ate" words
    private String leftoverName(String part) {
        String[] remove = {"i ate", "ate", "i had", "had", "add", "log", "drank", "for breakfast", "for lunch", "for dinner",
                "breakfast", "lunch", "dinner", "snack", "today", "grams", "gram", "kg", "ml",
                "اكلت", "أكلت", "كليت", "ضيف", "اضف", "أضف", "سجل", "سجّل", "فطرت", "تغديت", "تعشيت", "شربت",
                "غرام", "جرام", "كيلو", "على", "الفطور", "الغدا", "العشا", "فطور", "غدا", "عشا", "اليوم", "حبة", "صحن"};
        String text = " " + part + " ";
        for (int i = 0; i < remove.length; i++) {
            text = text.replace(" " + remove[i] + " ", " ");
        }
        String result = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isDigit(c)) {
                result = result + c;
            }
        }
        result = result.trim();
        // "150g" leaves a single "g" at the start
        if (result.startsWith("g ")) {
            result = result.substring(2).trim();
        }
        return result;
    }

    // Which meal: from the words, otherwise by the hour now
    private String mealOf(String q) {
        if (ChatBotEngine.has(q, new String[]{"breakfast", "فطور", "فطرت", "ترويقة"})) {
            return "Breakfast";
        }
        if (ChatBotEngine.has(q, new String[]{"lunch", "غدا", "تغديت", "تغذيت"})) {
            return "Lunch";
        }
        if (ChatBotEngine.has(q, new String[]{"dinner", "عشا", "تعشيت"})) {
            return "Dinner";
        }
        if (ChatBotEngine.has(q, new String[]{"snack", "سناك", "تصبيرة"})) {
            return "Snacks";
        }
        if (defaultMeal != null) {
            return defaultMeal;
        }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 11) {
            return "Breakfast";
        }
        if (hour >= 11 && hour < 16) {
            return "Lunch";
        }
        if (hour >= 16 && hour < 21) {
            return "Dinner";
        }
        return "Snacks";
    }

    // Arabic digits ٠١٢ → 012 (so "٢٠٠ غرام" works too)
    public static String toLatinDigits(String text) {
        String result = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '٠' && c <= '٩') {
                result = result + (char) ('0' + (c - '٠'));
            } else {
                result = result + c;
            }
        }
        return result;
    }
}
