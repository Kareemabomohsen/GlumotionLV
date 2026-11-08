package com.example.glumotion.food;

import com.example.glumotion.glucose.GlucoseReading;

/**
 * Checks if a meal is suitable for a person with diabetes and explains why not.
 * Used by: the food chat bot (text + photo) and the "how many grams?" dialog of the food search.
 *
 * Simple rules (from common diabetes nutrition advice):
 * - sugar of the meal 25g or more        → high sugar
 * - carbs of the meal more than 75g      → too many carbs for one meal (aim for 45-60g)
 * - blood sugar is high now (above 180)  → choose low carbs
 * - blood sugar is low now (below 70)    → fast carbs are OK (15-15 rule)
 * - more calories than what is left today
 */
public class FoodAdvisor {

    public static final int HIGH_SUGAR_MEAL = 25;
    public static final int HIGH_CARBS_MEAL = 75;
    public static final int HIGH_GLUCOSE_NOW = 180;
    private static final long THREE_HOURS = 3L * 60 * 60 * 1000;

    /**
     * Returns the advice text for a meal.
     * lastGlucose = last reading (null if none), kcalLeft = calories left today before this meal.
     */
    public static String check(int kcal, int carbs, int sugar, GlucoseReading lastGlucose, int kcalLeft, boolean arabic) {
        String text = "";
        boolean problem = false;

        // Only a reading of the last 3 hours tells how the sugar is NOW
        int glucoseNow = 0;
        if (lastGlucose != null && System.currentTimeMillis() - lastGlucose.getTimestamp() < THREE_HOURS) {
            glucoseNow = lastGlucose.getValue();
        }

        if (glucoseNow > 0 && glucoseNow < GlucoseReading.LOW_LIMIT) {
            return pick(arabic, "🩸 Your sugar is LOW now (" + glucoseNow + "). Fast carbs are OK now: 15g, wait 15 minutes and measure again.",
                    "🩸 السكر عندك واطي هلأ (" + glucoseNow + "). سكر سريع مسموح هلأ: 15 غرام، استنى 15 دقيقة وافحص مرة ثانية.");
        }
        if (sugar >= HIGH_SUGAR_MEAL) {
            problem = true;
            text = text + pick(arabic, "⚠ High sugar (" + sugar + "g) - it can raise your glucose fast. Take a smaller portion.\n",
                    "⚠ فيها سكر كثير (" + sugar + " غرام) - ممكن ترفع السكر بسرعة. خذ كمية أصغر.\n");
        }
        if (carbs > HIGH_CARBS_MEAL) {
            problem = true;
            text = text + pick(arabic, "⚠ " + carbs + "g carbs is a lot for one meal (aim for 45-60g). Add vegetables and protein instead.\n",
                    "⚠ " + carbs + " غرام كربوهيدرات كثير لوجبة وحدة (الأفضل 45-60). زيد خضار وبروتين بدالهم.\n");
        }
        if (glucoseNow > HIGH_GLUCOSE_NOW && carbs > 30) {
            problem = true;
            text = text + pick(arabic, "⚠ Your sugar is high now (" + glucoseNow + "). Choose a low-carb meal and walk 15 minutes after it.\n",
                    "⚠ السكر عندك عالي هلأ (" + glucoseNow + "). اختار وجبة قليلة كربوهيدرات وامشي 15 دقيقة بعدها.\n");
        }
        if (kcalLeft > 0 && kcal > kcalLeft) {
            problem = true;
            text = text + pick(arabic, "⚠ This is more than the " + kcalLeft + " kcal you have left today.\n",
                    "⚠ هاي أكثر من الـ " + kcalLeft + " سعرة اللي ضايلين إلك اليوم.\n");
        }
        if (!problem) {
            text = pick(arabic, "✅ Suitable for you - good choice!", "✅ مناسبة إلك - اختيار منيح!");
        } else {
            text = text + pick(arabic, "💡 Tip: eat the vegetables and protein first, then the carbs.",
                    "💡 نصيحة: كُل الخضار والبروتين أول، وبعدين النشويات.");
        }
        return text.trim();
    }

    // true if the meal breaks one of the rules (used to color the warning red)
    public static boolean isProblem(int carbs, int sugar) {
        return sugar >= HIGH_SUGAR_MEAL || carbs > HIGH_CARBS_MEAL;
    }

    // English or Arabic text
    private static String pick(boolean arabic, String english, String arabicText) {
        if (arabic) {
            return arabicText;
        }
        return english;
    }
}
