package com.example.glumotion.chat;

import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.DiaryEntry;
import com.example.glumotion.food.FoodActivity;
import com.example.glumotion.food.FoodItem;
import com.example.glumotion.food.LocalFoods;
import com.example.glumotion.food.NutritionCalculator;
import com.example.glumotion.glucose.GlucoseReading;
import com.example.glumotion.medications.MedicationModel;
import com.example.glumotion.workout.Exercise;
import com.example.glumotion.workout.Routine;
import com.example.glumotion.workout.RoutineEditorActivity;
import com.example.glumotion.workout.WorkoutData;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * The "brain" of the chat bot (works without internet AI).
 * 1) Loads the user's data from Firebase (food today, glucose, medications, workouts, steps...).
 * 2) Finds keywords in the question (English or Arabic) and builds an answer from the real data.
 * Answers in Arabic if the question has Arabic letters, otherwise in English.
 */
public class ChatBotEngine {

    // The screen is told when the data is ready
    public interface ReadyListener {
        void onReady();
    }

    private static final long DAY = 24L * 60 * 60 * 1000;

    // ---- data of the user ----
    private String name = "";
    private String goal = "";
    private String diabetesType = "";
    private int targetKcal = 2000, targetProtein = 120, targetCarbs = 200, targetFat = 65;
    private int eatenKcal, eatenProtein, eatenCarbs, eatenFat, eatenSugar;
    private int stepsToday, waterToday, waterGoal = 2500;
    private double weight, height;
    private int workoutsWeek;
    private final ArrayList<GlucoseReading> readings = new ArrayList<>();
    private final ArrayList<MedicationModel> meds = new ArrayList<>();
    private final ArrayList<Routine> routines = new ArrayList<>();
    private final ArrayList<FoodItem> customFoods = new ArrayList<>(); // foods the user created himself
    private String fitnessLevel = "Beginner";

    private boolean arabic = false; // language of the current question

    // Reads everything once from Users / uid
    public void load(final ReadyListener listener) {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            listener.onReady();
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                readData(s);
                listener.onReady();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onReady();
            }
        });
    }

    // Copies the snapshot into the variables
    private void readData(DataSnapshot s) {
        String fullName = s.child("fullName").getValue(String.class);
        if (fullName != null && !fullName.isEmpty()) {
            name = fullName.split(" ")[0];
        }
        goal = textOf(s.child("Goals").child("goal"));
        diabetesType = textOf(s.child("HealthConditions").child("diabetesType"));
        weight = numberOf(s.child("Goals").child("weight"));
        height = numberOf(s.child("Goals").child("height"));
        targetKcal = FoodActivity.readNumber(s.child("Nutrition"), "targetCalories", targetKcal);
        targetProtein = FoodActivity.readNumber(s.child("Nutrition"), "protein", targetProtein);
        targetCarbs = FoodActivity.readNumber(s.child("Nutrition"), "carbs", targetCarbs);
        targetFat = FoodActivity.readNumber(s.child("Nutrition"), "fats", targetFat);
        double waterL = numberOf(s.child("WaterIntake").child("waterTargetLiters"));
        if (waterL > 0) {
            waterGoal = (int) (waterL * 1000);
        }

        String today = DateUtil.todayKey();
        for (DataSnapshot e : s.child("FoodDiary").child(today).getChildren()) {
            DiaryEntry entry = e.getValue(DiaryEntry.class);
            if (entry != null) {
                eatenKcal = eatenKcal + entry.getKcal();
                eatenProtein = eatenProtein + entry.getProtein();
                eatenCarbs = eatenCarbs + entry.getCarbs();
                eatenFat = eatenFat + entry.getFat();
                eatenSugar = eatenSugar + entry.getSugar();
            }
        }
        stepsToday = (int) numberOf(s.child("DailyHealth").child(today).child("steps"));
        waterToday = (int) numberOf(s.child("DailyHealth").child(today).child("waterMl"));

        for (DataSnapshot r : s.child("GlucoseReadings").getChildren()) {
            GlucoseReading reading = r.getValue(GlucoseReading.class);
            if (reading != null && reading.getValue() > 0) {
                // keep the list sorted by time (insert in the right place)
                int pos = readings.size();
                while (pos > 0 && readings.get(pos - 1).getTimestamp() > reading.getTimestamp()) {
                    pos--;
                }
                readings.add(pos, reading);
            }
        }
        for (DataSnapshot m : s.child("Medications").getChildren()) {
            MedicationModel med = m.getValue(MedicationModel.class);
            if (med != null && med.getName() != null) {
                meds.add(med);
            }
        }
        long weekAgo = System.currentTimeMillis() - 7 * DAY;
        for (DataSnapshot w : s.child("Workouts").getChildren()) {
            Long t = w.child("timestamp").getValue(Long.class);
            if (t != null && t > weekAgo) {
                workoutsWeek++;
            }
        }
        for (DataSnapshot r : s.child("Routines").getChildren()) {
            Routine routine = Routine.fromSnapshot(r);
            if (routine != null) {
                routines.add(routine);
            }
        }
        for (DataSnapshot f : s.child("CustomFoods").getChildren()) {
            FoodItem food = f.getValue(FoodItem.class);
            if (food != null && food.getName() != null) {
                customFoods.add(food);
            }
        }
        String level = textOf(s.child("Fitness").child("fitnessLevel"));
        if (!level.isEmpty()) {
            fitnessLevel = level;
        }
    }

    // ===================== getters (used by the food and workout bots) =====================

    public String getName() { return name; }
    public String getGoal() { return goal; }
    public String getDiabetesType() { return diabetesType; }
    public String getFitnessLevel() { return fitnessLevel; }
    public int getKcalLeft() { return targetKcal - eatenKcal; }
    public int getTargetCarbs() { return targetCarbs; }
    public int getEatenCarbs() { return eatenCarbs; }
    public ArrayList<FoodItem> getCustomFoods() { return customFoods; }
    public ArrayList<Routine> getRoutines() { return routines; }

    // Last glucose reading (null if there are no readings)
    public GlucoseReading getLastReading() {
        if (readings.isEmpty()) {
            return null;
        }
        return readings.get(readings.size() - 1);
    }

    // After the food bot adds food → the totals of today change
    public void addEaten(int kcal, int protein, int carbs, int fat, int sugar) {
        eatenKcal = eatenKcal + kcal;
        eatenProtein = eatenProtein + protein;
        eatenCarbs = eatenCarbs + carbs;
        eatenFat = eatenFat + fat;
        eatenSugar = eatenSugar + sugar;
    }

    // A medication added from the chat (so "my medications" shows it right away)
    public void addMedication(MedicationModel med) {
        meds.add(med);
    }

    // A food the user created in the chat (so the bot knows it right away)
    public void addCustomFood(FoodItem food) {
        customFoods.add(food);
    }

    // Helpers to read values safely
    private String textOf(DataSnapshot snap) {
        Object v = snap.getValue();
        if (v == null) {
            return "";
        }
        return v.toString();
    }

    // Reads a number safely (0 if empty or not a number)
    private double numberOf(DataSnapshot snap) {
        try {
            return Double.parseDouble(textOf(snap));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Short summary of the user (sent to Gemini if it is turned on)
    public String getContextSummary() {
        String last = "none";
        if (!readings.isEmpty()) {
            last = readings.get(readings.size() - 1).getValue() + " mg/dL";
        }
        return "name " + name + ", diabetes " + diabetesType + ", goal " + goal + ", eaten today " + eatenKcal + "/"
                + targetKcal + " kcal, last glucose " + last + ", steps " + stepsToday + ", workouts this week " + workoutsWeek;
    }

    // ===================================================================
    //                        ANSWERING QUESTIONS
    // ===================================================================

    // English or Arabic text, by the language of the question
    private String t(String english, String arabicText) {
        if (arabic) {
            return arabicText;
        }
        return english;
    }

    /**
     * True if the question contains one of the words.
     * English words must be at the START of a word ("hi" won't match "this", "low" won't match "below"),
     * Arabic words can be anywhere (because of ال / و / ب before the word).
     */
    public static boolean has(String q, String[] words) {
        String padded = " " + q + " ";
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (isArabic(w)) {
                if (q.contains(w)) {
                    return true;
                }
            } else if (padded.contains(" " + w)) {
                return true;
            }
        }
        return false;
    }

    // Replaces ? ! , . with spaces so the words are separated
    public static String clean(String text) {
        String result = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                result = result + c;
            } else {
                result = result + ' ';
            }
        }
        return result;
    }

    // True if the text has Arabic letters
    public static boolean isArabic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '؀' && c <= 'ۿ') {
                return true;
            }
        }
        return false;
    }

    /**
     * Main method: question → answer.
     * Returns null if the bot doesn't understand (then Gemini can be asked, if turned on).
     * The ORDER of the checks matters: specific topics first, general topics last.
     */
    public String answer(String question) {
        arabic = isArabic(question);
        String q = clean(question.toLowerCase()).trim();

        if (has(q, new String[]{"low", "hypo", "هبوط", "منخفض", "واطي", "دايخ", "dizzy"})) {
            return lowSugarAnswer();
        }
        if (has(q, new String[]{"high sugar", "hyper", "ارتفاع", "مرتفع", "عالي", "ketone", "كيتون"})) {
            return highSugarAnswer();
        }
        if (has(q, new String[]{"a1c", "hba1c", "تراكمي"})) {
            return hba1cAnswer();
        }
        if (has(q, new String[]{"sugar", "glucose", "reading", "سكر", "قراءة", "قراءه"})) {
            return glucoseAnswer();
        }
        String exerciseTips = exerciseAnswer(q);
        if (exerciseTips != null) {
            return exerciseTips;
        }
        String foodInfo = foodInfoAnswer(q);
        if (foodInfo != null) {
            return foodInfo;
        }
        if (has(q, new String[]{"protein", "carb", "fat", "macro", "بروتين", "كربوهيدرات", "دهون", "ماكروز"})) {
            return macrosAnswer();
        }
        if (has(q, new String[]{"calorie", "kcal", "سعرات", "كالوري", "ate today", "اكلت"})) {
            return caloriesAnswer();
        }
        if (has(q, new String[]{"eat", "suggest", "hungry", "meal", "snack", "اكل", "آكل", "جوعان", "وجبة", "اقترح"})) {
            return suggestFoodAnswer();
        }
        if (has(q, new String[]{"medic", "pill", "dose", "دواء", "ادوية", "أدوية", "حبوب", "حبة"})) {
            return medsAnswer();
        }
        if (has(q, new String[]{"workout", "gym", "routine", "train", "تمرين", "تمارين", "جيم", "روتين"})) {
            return workoutAnswer();
        }
        if (has(q, new String[]{"step", "walk", "خطوات", "مشي", "امشي"})) {
            return stepsAnswer();
        }
        if (has(q, new String[]{"water", "drink", "ماء", "مي", "اشرب", "مية"})) {
            return waterAnswer();
        }
        if (has(q, new String[]{"bmi", "weight", "وزن", "كتلة"})) {
            return bmiAnswer();
        }
        if (has(q, new String[]{"barcode", "باركود", "scan"})) {
            return t("Food tab → \"+\" on a meal → Barcode. Point the camera at the product (Israeli products work too) and choose the grams.",
                    "صفحة الأكل ← اكبس + على الوجبة ← Barcode. وجّه الكاميرا للمنتج (المنتجات الإسرائيلية بتشتغل) واختار كم غرام.");
        }
        if (has(q, new String[]{"thank", "thanks", "شكرا", "يسلمو", "تسلم"})) {
            return t("You're welcome " + name + "! 💚", "العفو " + name + "! 💚");
        }
        if (has(q, new String[]{"help", "what can you", "مساعدة", "شو بتعرف", "ساعدني"})) {
            return helpAnswer();
        }
        if (has(q, new String[]{"hi", "hello", "hey", "مرحبا", "اهلا", "أهلا", "السلام", "هاي", "كيفك", "شلونك"})) {
            return t("Hi " + name + "! 👋 I'm GluBot. Ask me about your sugar, food, workouts, medications or steps.",
                    "أهلاً " + name + "! 👋 أنا GluBot. اسألني عن السكر، الأكل، التمارين، الأدوية أو المشي.");
        }
        return null;
    }

    /**
     * General bot only: "how do I ...?" questions about the app itself (where is every feature).
     * Returns null if the question is not about using the app.
     */
    public String appGuide(String question) {
        arabic = isArabic(question);
        String q = clean(question.toLowerCase()).trim();
        boolean how = has(q, new String[]{"how", "where", "can i", "كيف", "وين", "بقدر", "طريقة"});
        if (!how) {
            return null;
        }
        if (has(q, new String[]{"watch", "heart", "ساعة", "ساعه", "دقات", "نبض"})) {
            return t("⌚ Turn on Health Connect in your watch app (Samsung Health / Fitbit / Mi Fitness), then Health → ⌚ Connect watch "
                            + "(or Settings → ⌚ My watch). Steps and heart rate come by themselves.",
                    "⌚ شغّل Health Connect بتطبيق الساعة (Samsung Health / Fitbit / Mi Fitness)، وبعدين الصحة ← ⌚ Connect watch "
                            + "(أو الإعدادات ← ⌚ My watch). الخطوات ودقات القلب بيوصلوا لحالهم.");
        }
        if (has(q, new String[]{"history", "trained", "سجل", "تدربت"})) {
            return t("📅 Workout tab → 📅 History: the days you trained, how long, sets and kg. Tap a workout to see its sets.",
                    "📅 صفحة التمارين ← 📅 History: الأيام اللي تدربت فيها، كم وقت، المجموعات والكيلوهات. اكبس على تمرين بتشوف المجموعات.");
        }
        if (has(q, new String[]{"medic", "pill", "دواء", "ادوية", "أدوية", "حبوب"})) {
            return t("💊 Write to me: \"add medication Metformin 500mg morning\" and I add it. Or: Health tab → pills button → + Add New "
                            + "(or Settings → My Medications).",
                    "💊 اكتبلي: \"ضيف دوا متفورمين 500 ملغ الصبح\" وأنا بضيفه. أو: صفحة Health ← زر الحبوب ← + Add New "
                            + "(أو الإعدادات ← My Medications).");
        }
        if (has(q, new String[]{"sugar", "glucose", "reading", "سكر", "قراءة"})) {
            return t("🩸 DM tab → \"+\" → write the number, choose when you measured and press Done.",
                    "🩸 صفحة DM ← \"+\" ← اكتب الرقم، اختار وقت القياس واكبس Done.");
        }
        if (has(q, new String[]{"routine", "workout", "روتين", "تمرين"})) {
            return t("💪 Workout tab → + New Routine → name, day, + Add Exercise → Save.",
                    "💪 صفحة Workout ← + New Routine ← الاسم، اليوم، + Add Exercise ← Save.");
        }
        if (has(q, new String[]{"food", "meal", "eat", "اكل", "أكل", "وجبة"})) {
            return t("🍽 Food tab → \"+\" next to a meal → search / barcode / recipes. Or tell me \"I ate ...\" and I add it!",
                    "🍽 صفحة Food ← \"+\" جنب الوجبة ← بحث / باركود / وصفات. أو احكيلي \"اكلت ...\" وأنا بضيفها!");
        }
        if (has(q, new String[]{"coach", "trainer", "admin", "مدرب", "ادمن", "أدمن"})) {
            return t("👥 Coaches make their own account (Login → \"I'm a coach\") and add you by email. "
                            + "Requests from a coach: 👤 menu (top right) → 🔔 Notifications → Accept / Decline. "
                            + "There you can also call your coach or stop sharing with him.",
                    "👥 المدرب بيعمل حساب مدرب (الدخول ← \"I'm a coach\") وبيضيفك بالإيميل. "
                            + "طلبات المدرب: قائمة 👤 (فوق يمين) ← 🔔 Notifications ← قبول / رفض. "
                            + "ومن هناك بتقدر تتصل بمدربك أو توقف المشاركة معه.");
        }
        if (has(q, new String[]{"remind", "notif", "تذكير", "اشعار", "إشعار"})) {
            return t("⏰ Settings → Daily reminders (switch) → Reminder times.", "⏰ الإعدادات ← Daily reminders ← Reminder times.");
        }
        if (has(q, new String[]{"photo", "picture", "صورة", "صوره"})) {
            return t("📷 Profile → camera button on your photo. For meal photos: open the Food Bot and press the camera.",
                    "📷 البروفايل ← زر الكاميرا على صورتك. لصورة الوجبة: افتح بوت الأكل واكبس الكاميرا.");
        }
        if (has(q, new String[]{"weight", "height", "وزن", "طول"})) {
            return t("⚖ Profile → Physical Stats. The calories are calculated again by themselves.",
                    "⚖ البروفايل ← Physical Stats. السعرات بتنحسب من جديد لحالها.");
        }
        if (has(q, new String[]{"password", "account", "كلمة السر", "حساب"})) {
            return t("🔐 Settings → Account → change password / delete account.", "🔐 الإعدادات ← Account ← تغيير كلمة السر / حذف الحساب.");
        }
        return null;
    }

    // Message when nothing matched
    public String notUnderstood() {
        return t("I didn't understand 🤔 Try: \"my sugar\", \"calories left\", \"what should I eat\", \"my medications\", \"squat tips\".",
                "ما فهمت عليك 🤔 جرّب: \"السكر تبعي\"، \"كم سعرات ضايل\"، \"شو آكل\"، \"أدويتي\"، \"تمرين\".");
    }

    // Welcome message with a short status of today
    public String welcome() {
        String sugar = "-";
        if (!readings.isEmpty()) {
            sugar = readings.get(readings.size() - 1).getValue() + " mg/dL";
        }
        return "Hi " + name + "! 👋 I'm GluBot / أهلاً، أنا GluBot\n\n"
                + "🩸 Last sugar: " + sugar + "\n"
                + "🍽 Calories: " + eatenKcal + " / " + targetKcal + "\n"
                + "🚶 Steps: " + stepsToday + "\n\n"
                + "Ask me anything in English or Arabic 💬";
    }

    // ----- glucose -----

    // Last reading + 7-day average + advice by the status
    private String glucoseAnswer() {
        if (readings.isEmpty()) {
            return t("You have no readings yet. Go to the DM tab → \"Log Blood Sugar Reading\".",
                    "ما في قراءات لسا. روح لصفحة DM ← \"Log Blood Sugar Reading\".");
        }
        GlucoseReading last = readings.get(readings.size() - 1);
        int value = last.getValue();
        String status = GlucoseReading.getStatus(value);

        // Average of the last 7 days
        long weekAgo = System.currentTimeMillis() - 7 * DAY;
        int sum = 0, count = 0, inRange = 0;
        for (int i = 0; i < readings.size(); i++) {
            if (readings.get(i).getTimestamp() > weekAgo) {
                sum = sum + readings.get(i).getValue();
                count++;
                if (GlucoseReading.isInRange(readings.get(i).getValue())) {
                    inRange++;
                }
            }
        }
        String week = "";
        if (count > 0) {
            week = t("\n📊 7-day average: " + (sum / count) + " mg/dL • in range " + (inRange * 100 / count) + "%",
                    "\n📊 معدل 7 أيام: " + (sum / count) + " • بالرينج " + (inRange * 100 / count) + "%");
        }
        String advice;
        if (status.equals("Low")) {
            advice = lowSugarAnswer();
        } else if (status.equals("Normal")) {
            advice = t("Great - it's in the target range ✅", "ممتاز - ضمن المعدل الطبيعي ✅");
        } else if (status.equals("Elevated")) {
            advice = t("A bit high. A 10-15 minute walk can help bring it down.", "شوي عالي. مشي 10-15 دقيقة بيساعد ينزّله.");
        } else {
            advice = highSugarAnswer();
        }
        return t("🩸 Last reading: " + value + " mg/dL (" + status + ") - " + last.getContext() + ", " + last.getTimeText(),
                "🩸 آخر قراءة: " + value + " (" + status + ") - " + last.getContext() + "، " + last.getTimeText())
                + week + "\n\n" + advice;
    }

    // What to do when the sugar is low (15-15 rule)
    private String lowSugarAnswer() {
        return t("⚠ Low sugar (below 70) - the 15-15 rule:\n1. Eat 15g fast carbs (½ cup juice / 3-4 glucose tablets)\n"
                        + "2. Wait 15 minutes and measure again\n3. Still below 70 → repeat\n4. Then eat a small snack with protein.\n"
                        + "If you feel very bad or can't swallow - call emergency (101).\n📚 Source: American Diabetes Association (diabetes.org)",
                "⚠ سكر واطي (أقل من 70) - قاعدة 15-15:\n1. كُل 15 غرام سكر سريع (نص كاسة عصير / 3-4 حبات جلوكوز)\n"
                        + "2. استنى 15 دقيقة وافحص مرة ثانية\n3. إذا لسا أقل من 70 ← عيد\n4. بعدين كُل سناك فيه بروتين.\n"
                        + "إذا حاسس حالك كتير تعبان - اتصل بالإسعاف (101).\n📚 المصدر: جمعية السكري الأمريكية ADA (diabetes.org)");
    }

    // What to do when the sugar is high
    private String highSugarAnswer() {
        return t("High sugar: drink water, take your medication as your doctor said, and take a light walk if you feel OK. "
                        + "Above 250 with type 1 → check ketones and don't do hard exercise. If it stays high - call your doctor.\n"
                        + "📚 Source: American Diabetes Association (diabetes.org)",
                "سكر عالي: اشرب مي، خذ دواك متل ما حكالك الدكتور، وامشي مشي خفيف إذا حاسس حالك منيح. "
                        + "فوق 250 ونوع أول ← افحص كيتونات وبلاش تمرين قوي. إذا ضل عالي - كلّم الدكتور.\n"
                        + "📚 المصدر: جمعية السكري الأمريكية ADA (diabetes.org)");
    }

    // Estimated HbA1c from the readings of the last 30 days
    private String hba1cAnswer() {
        long monthAgo = System.currentTimeMillis() - 30 * DAY;
        int sum = 0, count = 0;
        for (int i = 0; i < readings.size(); i++) {
            if (readings.get(i).getTimestamp() > monthAgo) {
                sum = sum + readings.get(i).getValue();
                count++;
            }
        }
        if (count < 5) {
            return t("HbA1c = average sugar of ~3 months. I need at least 5 readings this month to estimate it.",
                    "السكر التراكمي = معدل السكر لـ 3 شهور تقريباً. بدي 5 قراءات على الأقل هالشهر عشان أقدّره.");
        }
        double a1c = Math.round((sum / (double) count + 46.7) / 28.7 * 10) / 10.0;
        return t("Estimated HbA1c: " + a1c + "% (from " + count + " readings). Most adults with diabetes aim for below 7%. "
                        + "The real number comes from a blood test.\n📚 Source: ADA (eAG formula and the 7% goal)",
                "التراكمي التقريبي: " + a1c + "% (من " + count + " قراءة). أغلب مرضى السكري هدفهم أقل من 7%. "
                        + "الرقم الحقيقي بيطلع من فحص دم.\n📚 المصدر: ADA (معادلة المعدل والهدف 7%)");
    }

    // ----- food -----

    // Calories eaten today and how many are left
    private String caloriesAnswer() {
        int left = targetKcal - eatenKcal;
        if (left >= 0) {
            return t("🍽 Today you ate " + eatenKcal + " of " + targetKcal + " kcal → " + left + " kcal left.\nSugar today: " + eatenSugar + "g",
                    "🍽 اليوم أكلت " + eatenKcal + " من " + targetKcal + " سعرة ← ضايلك " + left + ".\nسكر اليوم: " + eatenSugar + " غرام");
        }
        return t("🍽 You ate " + eatenKcal + " kcal - " + (-left) + " over your target (" + targetKcal + "). A walk will help!",
                "🍽 أكلت " + eatenKcal + " سعرة - يعني " + (-left) + " زيادة عن هدفك (" + targetKcal + "). المشي بيساعد!");
    }

    // Protein / carbs / fat of today vs the targets
    private String macrosAnswer() {
        return t("Today:\n🥩 Protein " + eatenProtein + "/" + targetProtein + "g\n🍞 Carbs " + eatenCarbs + "/" + targetCarbs
                        + "g\n🥑 Fat " + eatenFat + "/" + targetFat + "g",
                "اليوم:\n🥩 بروتين " + eatenProtein + "/" + targetProtein + "\n🍞 كربوهيدرات " + eatenCarbs + "/" + targetCarbs
                        + "\n🥑 دهون " + eatenFat + "/" + targetFat);
    }

    // Low sugar food ideas that fit the calories left
    private String suggestFoodAnswer() {
        int left = targetKcal - eatenKcal;
        ArrayList<FoodItem> list = LocalFoods.suggest(left, targetProtein - eatenProtein, 4);
        String text = t("Low sugar ideas for you (" + Math.max(left, 0) + " kcal left):\n",
                "اقتراحات قليلة السكر (ضايلك " + Math.max(left, 0) + " سعرة):\n");
        for (int i = 0; i < list.size(); i++) {
            FoodItem f = list.get(i);
            text = text + "• " + f.getName() + " - " + Exercise.formatKg(f.getServingGrams()) + "g, "
                    + FoodItem.round(f.kcalFor(f.getServingGrams())) + " kcal, P " + FoodItem.round(f.proteinFor(f.getServingGrams())) + "g\n";
        }
        return text + t("Tip: eat vegetables/protein before carbs - it lowers the sugar spike.",
                "نصيحة: كُل خضار وبروتين قبل النشويات - بيقلل ارتفاع السكر.");
    }

    // "calories in banana" / "كم سعرات بالموز" → finds a food from the built-in list
    private String foodInfoAnswer(String q) {
        ArrayList<FoodItem> all = LocalFoods.getAll();
        for (int i = 0; i < all.size(); i++) {
            FoodItem f = all.get(i);
            String first = f.getName().toLowerCase().split(" ")[0];
            if (first.length() > 3 && has(q, new String[]{first})) {
                double g = f.getServingGrams();
                String sugarNote;
                if (f.isDiabetesFriendly()) {
                    sugarNote = t("✅ low sugar", "✅ سكر قليل");
                } else {
                    sugarNote = t("⚠ high sugar - small portion", "⚠ سكر عالي - كمية صغيرة");
                }
                return "🍽 " + f.getName() + " (" + Exercise.formatKg(g) + "g): " + FoodItem.round(f.kcalFor(g)) + " kcal\n"
                        + "P " + FoodItem.round(f.proteinFor(g)) + "g • C " + FoodItem.round(f.carbsFor(g)) + "g • F "
                        + FoodItem.round(f.fatFor(g)) + "g • Sugar " + FoodItem.round(f.sugarFor(g)) + "g\n" + sugarNote;
            }
        }
        return null;
    }

    // ----- medications -----

    // The medications and their status today
    private String medsAnswer() {
        if (meds.isEmpty()) {
            return t("No medications saved. Settings → My Medications → + Add New.",
                    "ما في أدوية محفوظة. الإعدادات ← My Medications ← + Add New.");
        }
        String text = t("💊 Your medications:\n", "💊 أدويتك:\n");
        int pending = 0;
        for (int i = 0; i < meds.size(); i++) {
            MedicationModel m = meds.get(i);
            String status = m.getTodayStatus();
            if (status.equals("Pending")) {
                pending++;
            }
            text = text + "• " + m.getDisplayName() + " - " + m.getTime() + " (" + status + ")\n";
        }
        return text + t(pending + " still pending. Never change a dose without your doctor.",
                pending + " لسا ما انوخذت. لا تغيّر الجرعة بدون الدكتور.");
    }

    // ----- workouts -----

    // Workouts this week + today's routine
    private String workoutAnswer() {
        String today = RoutineEditorActivity.DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK)];
        String todayRoutine = null;
        for (int i = 0; i < routines.size(); i++) {
            if (routines.get(i).getDay().equals(today)) {
                todayRoutine = routines.get(i).getTitle() + " (" + routines.get(i).getExercisesPreview() + ")";
            }
        }
        String text = t("💪 Workouts this week: " + workoutsWeek + "\n", "💪 تمارين هالأسبوع: " + workoutsWeek + "\n");
        if (todayRoutine != null) {
            text = text + t("Today's routine: " + todayRoutine, "روتين اليوم: " + todayRoutine);
        } else if (routines.isEmpty()) {
            text = text + t("Create your own routine: Workout tab → + New Routine.", "اعمل روتين خاص فيك: صفحة Workout ← + New Routine.");
        } else {
            text = text + t("No routine for " + today + " - a 30 min walk is a great choice.", "ما في روتين لليوم - مشي 30 دقيقة اختيار ممتاز.");
        }
        return text + t("\n🩸 Check your sugar before training (below 90 → eat 15-30g carbs first).",
                "\n🩸 افحص السكر قبل التمرين (أقل من 90 ← كُل 15-30 غرام كربوهيدرات قبل).");
    }

    // "squat tips" → form cues of a known exercise
    private String exerciseAnswer(String q) {
        ArrayList<Exercise> all = WorkoutData.getAllExercises();
        for (int i = 0; i < all.size(); i++) {
            Exercise ex = all.get(i);
            String[] words = ex.getName().toLowerCase().split(" ");
            String key = words[words.length - 1];   // "Barbell Back Squat" → "squat"
            if (key.length() > 3 && has(q, new String[]{key})) {
                return "🏋 " + ex.getName() + " • " + ex.getSetsRepsText() + " • rest " + ex.getRestSeconds() + "s\n"
                        + ex.getCuesText() + "\n📈 " + ex.getProgression();
            }
        }
        return null;
    }

    // ----- steps, water, BMI, help -----

    // Steps today vs 10,000
    private String stepsAnswer() {
        int percent = stepsToday * 100 / 10000;
        return t("🚶 Steps today: " + stepsToday + " / 10,000 (" + percent + "%). Walking after meals lowers blood sugar!",
                "🚶 خطوات اليوم: " + stepsToday + " / 10,000 (" + percent + "%). المشي بعد الأكل بنزّل السكر!");
    }

    // Water today vs the goal
    private String waterAnswer() {
        return t("💧 Water today: " + waterToday + " / " + waterGoal + " ml. High sugar makes you lose water - keep drinking.",
                "💧 مي اليوم: " + waterToday + " / " + waterGoal + " مل. السكر العالي بيخسّرك مي - ضلك اشرب.");
    }

    // Weight and BMI
    private String bmiAnswer() {
        double bmi = NutritionCalculator.bmi(weight, height);
        if (bmi <= 0) {
            return t("Add your height and weight in Profile → Physical Stats.", "زبّط طولك ووزنك بالبروفايل ← Physical Stats.");
        }
        return t("⚖ " + Exercise.formatKg(weight) + " kg • BMI " + bmi + " (" + NutritionCalculator.bmiCategory(bmi) + ") • goal: " + goal,
                "⚖ " + Exercise.formatKg(weight) + " كغم • BMI " + bmi + " (" + NutritionCalculator.bmiCategory(bmi) + ") • الهدف: " + goal);
    }

    // What the bot can do
    private String helpAnswer() {
        return t("I can tell you:\n🩸 your sugar and what to do if it's low/high\n🍽 calories & macros left, what to eat\n"
                        + "💊 your medications + add one (\"add medication Metformin 500mg morning\")\n"
                        + "📋 what a medication is for, from the FDA label (\"what is metformin\")\n💪 today's workout + exercise tips (e.g. \"squat\")\n🚶 steps • 💧 water • ⚖ BMI",
                "بقدر أحكيلك:\n🩸 السكر تبعك وشو تعمل إذا واطي/عالي\n🍽 كم سعرات ضايل وشو تاكل\n"
                        + "💊 أدويتك + إضافة دوا (\"ضيف دوا متفورمين 500 ملغ الصبح\")\n"
                        + "📋 لشو الدوا من نشرة FDA (\"what is metformin\")\n💪 تمرين اليوم + نصائح تمارين\n🚶 الخطوات • 💧 المي • ⚖ الوزن");
    }
}
