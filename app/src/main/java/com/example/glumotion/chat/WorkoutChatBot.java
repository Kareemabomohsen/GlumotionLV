package com.example.glumotion.chat;

import com.example.glumotion.glucose.GlucoseReading;
import com.example.glumotion.workout.Exercise;
import com.example.glumotion.workout.WorkoutData;

import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * 💪 The WORKOUT chat bot.
 * - "how to do squat" / "فيديو سكوات" → form tips + a YouTube video button
 * - "chest exercises" / "تمارين صدر" → exercises of that muscle (from the app's library)
 * - "plan" / "برنامج" → a weekly plan by the user's fitness level
 * - warm up, rest, sets & reps, soreness, cardio, sugar before training (diabetes safety)
 * Returns null when it doesn't know → the chat screen asks the normal engine.
 */
public class WorkoutChatBot {

    private final ChatBotEngine engine;
    private boolean arabic = false;

    // Arabic / slang name → word of the English exercise name
    private static final String[][] ARABIC_NAMES = {
            {"سكوات", "squat"}, {"سكوت", "squat"}, {"ضغط", "push ups"}, {"بنش", "bench"}, {"ديدلفت", "deadlift"},
            {"ديد لفت", "deadlift"}, {"عقلة", "pull ups"}, {"عقله", "pull ups"}, {"بلانك", "plank"}, {"طعن", "lunges"},
            {"لانج", "lunges"}, {"تجديف", "row"}, {"باي", "curl"}, {"تراي", "pushdown"},
            {"جري", "running"}, {"ركض", "running"}, {"دراجة", "cycling"}, {"بسكليت", "cycling"}, {"نط", "jump rope"},
            {"حبل", "jump rope"}, {"مشي", "treadmill walk"}, {"ليج بريس", "leg press"}, {"سحب", "lat pulldown"}
    };

    // Muscle group words → texts that appear in the "primary muscle" of the exercises
    private static final String[][] MUSCLES = {
            {"chest", "Chest"}, {"صدر", "Chest"},
            {"back", "Back|Lats|Posterior"}, {"ظهر", "Back|Lats|Posterior"},
            {"leg", "Quads|Hamstrings|Glutes|Calves"}, {"رجل", "Quads|Hamstrings|Glutes|Calves"}, {"أرجل", "Quads|Hamstrings|Glutes|Calves"},
            {"shoulder", "Shoulders"}, {"كتف", "Shoulders"}, {"اكتاف", "Shoulders"}, {"أكتاف", "Shoulders"},
            {"bicep", "Biceps"}, {"arm", "Biceps|Triceps"}, {"بايسبس", "Biceps"}, {"ترايسبس", "Triceps"}, {"tricep", "Triceps"},
            {"abs", "Abs|Core"}, {"core", "Abs|Core"}, {"بطن", "Abs|Core"}, {"معدة", "Abs|Core"},
            {"cardio", "Cardio"}, {"كارديو", "Cardio"}, {"هوائي", "Cardio"}, {"glute", "Glutes|Quads"}, {"مؤخرة", "Glutes|Quads"}
    };

    // Generic words that don't tell which exercise ("barbell press" is not enough)
    private static final String[] GENERIC = {"barbell", "dumbbell", "machine", "cable", "seated", "standing", "press",
            "conventional", "walking", "incline", "overhead", "close", "grip", "hanging", "raise", "back"};

    // Constructor: the engine has the user's data (fitness level, routines, last glucose)
    public WorkoutChatBot(ChatBotEngine engine) {
        this.engine = engine;
    }

    // English or Arabic text
    private String t(String english, String arabicText) {
        if (arabic) {
            return arabicText;
        }
        return english;
    }

    /**
     * Main method: question → bot message (with a YouTube link when it's about one exercise).
     * The ORDER matters: one exercise first, then muscles, then general topics.
     */
    public ChatMessage answer(String question) {
        arabic = ChatBotEngine.isArabic(question);
        String q = ChatBotEngine.clean(question.toLowerCase()).trim();

        // Sugar & training first (safety!)
        if (ChatBotEngine.has(q, new String[]{"sugar", "glucose", "hypo", "سكر", "هبوط"})) {
            return bot(sugarAnswer());
        }
        // "leg exercises" = a list for the muscle (and not the exercise "Leg Press")
        int muscle = findMuscle(q);
        boolean askList = ChatBotEngine.has(q, new String[]{"exercises", "list", "تمارين", "تمرينات"});
        if (muscle >= 0 && askList) {
            return bot(muscleAnswer(muscle));
        }
        Exercise exercise = findExercise(q);
        if (exercise != null) {
            return exerciseAnswer(exercise);
        }
        if (muscle >= 0) {
            return bot(muscleAnswer(muscle));
        }
        if (ChatBotEngine.has(q, new String[]{"video", "youtube", "فيديو", "يوتيوب"})) {
            ChatMessage m = bot(t("Which exercise? e.g. \"squat video\". Here is a full-body workout for beginners:",
                    "أي تمرين؟ مثلاً \"فيديو سكوات\". هاد فيديو تمرين لكل الجسم للمبتدئين:"));
            m.setLink(youtubeLink("full body workout for beginners"), "▶ YouTube");
            return m;
        }
        if (ChatBotEngine.has(q, new String[]{"plan", "program", "split", "routine", "برنامج", "خطة", "روتين", "جدول"})) {
            return bot(planAnswer());
        }
        if (ChatBotEngine.has(q, new String[]{"warm", "احماء", "إحماء", "تسخين"})) {
            ChatMessage m = bot(t("🔥 Warm up 5-10 minutes: easy walk/bike, arm & leg circles, then 1-2 light sets of the first exercise. "
                            + "It protects the joints and helps the sugar stay stable.",
                    "🔥 إحماء 5-10 دقايق: مشي أو دراجة خفيف، تدوير إيدين ورجلين، وبعدين مجموعة-مجموعتين خفاف من أول تمرين. "
                            + "بيحمي المفاصل وبيساعد السكر يضل ثابت."));
            m.setLink(youtubeLink("5 minute warm up before workout"), "▶ Warm-up video");
            return m;
        }
        if (ChatBotEngine.has(q, new String[]{"rest", "راحة", "استراحة"})) {
            return bot(t("⏱ Rest between sets: heavy exercises (squat, bench, deadlift) 2-3 min, others 60-90 sec. "
                            + "The app's rest timer starts by itself after every ✓ set.",
                    "⏱ الراحة بين المجموعات: التمارين الثقيلة (سكوات، بنش، ديدلفت) 2-3 دقايق، والباقي 60-90 ثانية. "
                            + "مؤقت الراحة بالتطبيق بيشتغل لحاله بعد كل ✓."));
        }
        if (ChatBotEngine.has(q, new String[]{"set", "rep", "how many", "مجموع", "عدات", "تكرار", "كم مرة"})) {
            return bot(setsAnswer());
        }
        if (ChatBotEngine.has(q, new String[]{"sore", "pain", "hurt", "وجع", "الم", "ألم", "بوجعني"})) {
            return bot(t("💢 Muscle soreness 1-2 days after training is normal: walk, drink water, sleep well, train another muscle. "
                            + "Sharp pain in a joint is NOT normal - stop that exercise and ask a doctor/physio.",
                    "💢 وجع العضلات يوم-يومين بعد التمرين طبيعي: امشي، اشرب مي، نام منيح، ومرّن عضلة ثانية. "
                            + "وجع حاد بالمفصل مش طبيعي - وقّف التمرين واسأل دكتور/أخصائي علاج طبيعي."));
        }
        if (ChatBotEngine.has(q, new String[]{"beginner", "start", "مبتدئ", "ابلش", "أبدأ", "ابدا"})) {
            return bot(planAnswer());
        }
        return null;
    }

    // Bot message without a link
    private ChatMessage bot(String text) {
        return new ChatMessage(text, false);
    }

    // One exercise: sets, rest, form tips + YouTube button
    private ChatMessage exerciseAnswer(Exercise ex) {
        String text = "🏋 " + ex.getName() + " (" + ex.getPrimaryMuscle() + ")\n" + ex.getSetsRepsText()
                + " • " + t("rest ", "راحة ") + ex.getRestSeconds() + "s\n";
        if (ex.getFormCues() != null && ex.getFormCues().length > 0) {
            text = text + ex.getCuesText();
            if (ex.getProgression() != null && !ex.getProgression().isEmpty()) {
                text = text + "\n📈 " + ex.getProgression();
            }
        } else {
            text = text + t("Watch the video for the right form, start light and add weight slowly.",
                    "شوف الفيديو عشان الطريقة الصحيحة، ابلش بوزن خفيف وزيد شوي شوي.");
        }
        ChatMessage m = bot(text);
        m.setLink(youtubeLink(ex.getName() + " proper form"), "▶ " + t("Watch on YouTube", "شوف الفيديو على يوتيوب"));
        return m;
    }

    // Exercises of one muscle group (index = row of the MUSCLES table)
    private String muscleAnswer(int index) {
        String[] wanted = MUSCLES[index][1].split("\\|");
        ArrayList<Exercise> all = WorkoutData.getLibrary();
        String label = MUSCLES[index][0];   // the word the user wrote ("chest" / "صدر")
        String text = t("Exercises for " + label + ":\n", "تمارين " + label + ":\n");
        int count = 0;
        for (int i = 0; i < all.size() && count < 6; i++) {
            Exercise ex = all.get(i);
            for (int j = 0; j < wanted.length; j++) {
                if (ex.getPrimaryMuscle().contains(wanted[j])) {
                    text = text + "• " + ex.getName() + " - " + ex.getSetsRepsText() + "\n";
                    count++;
                    break;
                }
            }
        }
        return text + t("Ask me \"video of ...\" for any of them, or add them to a routine: Workout → + New Routine.",
                "اسألني \"فيديو ...\" عن أي واحد، أو ضيفهم لروتين: Workout ← + New Routine.");
    }

    // Weekly plan by the fitness level of the user
    private String planAnswer() {
        String level = engine.getFitnessLevel();
        String plan;
        if (level.equals("Advanced")) {
            plan = t("5 days: Push / Pull / Legs / Upper / Lower + 2 cardio sessions of 20 min.",
                    "5 أيام: Push / Pull / Legs / Upper / Lower + كارديو 20 دقيقة مرتين.");
        } else if (level.equals("Intermediate")) {
            plan = t("4 days: Chest & Triceps / Back & Biceps / Legs & Abs / Full body + walks after meals.",
                    "4 أيام: صدر وترايسبس / ظهر وبايسبس / رجلين وبطن / كل الجسم + مشي بعد الأكل.");
        } else {
            plan = t("3 days (e.g. Sun / Tue / Thu): full body - squat, push-ups, row, plank - 3 sets of 10-12. "
                            + "On the other days walk 20-30 minutes.",
                    "3 أيام (مثلاً أحد / ثلاثاء / خميس): كل الجسم - سكوات، ضغط، تجديف، بلانك - 3 مجموعات 10-12. "
                            + "بباقي الأيام امشي 20-30 دقيقة.");
        }
        String text = t("📅 Plan for " + level + ":\n", "📅 برنامج لمستوى " + level + ":\n") + plan;
        if (engine.getRoutines().isEmpty()) {
            text = text + t("\nCreate it in the app: Workout → + New Routine.", "\nاعمله بالتطبيق: Workout ← + New Routine.");
        } else {
            text = text + t("\nYou have " + engine.getRoutines().size() + " routines in the app 💪",
                    "\nعندك " + engine.getRoutines().size() + " روتينات بالتطبيق 💪");
        }
        return text;
    }

    // Sets and reps by the goal (Bulking / Cutting / Maintaining)
    private String setsAnswer() {
        String goal = engine.getGoal();
        if (goal.equals("Bulking")) {
            return t("💪 Bulking: 3-4 sets of 6-12 reps, heavy, rest 90-120 sec. Add weight when all reps are easy.",
                    "💪 تضخيم: 3-4 مجموعات 6-12 عدة، وزن ثقيل، راحة 90-120 ثانية. زيد الوزن لما كل العدات تصير سهلة.");
        }
        if (goal.equals("Cutting")) {
            return t("🔥 Cutting: 3 sets of 10-15 reps, rest 45-60 sec, + 20 min cardio. Keep the weights to keep the muscle.",
                    "🔥 تنشيف: 3 مجموعات 10-15 عدة، راحة 45-60 ثانية، + كارديو 20 دقيقة. ضل ارفع أوزان عشان تحافظ عالعضل.");
        }
        return t("⚖ 3 sets of 8-12 reps, rest 60-90 sec, 3-4 times a week.",
                "⚖ 3 مجموعات 8-12 عدة، راحة 60-90 ثانية، 3-4 مرات بالأسبوع.");
    }

    // Diabetes safety: what the last reading means for training
    private String sugarAnswer() {
        GlucoseReading last = engine.getLastReading();
        String text = t("🩸 Before training: below 70 → don't train, eat 15g fast carbs. 70-89 → eat 15-30g carbs first. "
                        + "90-250 → OK. Above 250 (type 1) → check ketones first. Measure again after training.",
                "🩸 قبل التمرين: أقل من 70 ← لا تتمرن، كُل 15 غرام سكر سريع. 70-89 ← كُل 15-30 غرام كربوهيدرات قبل. "
                        + "90-250 ← تمام. فوق 250 (نوع أول) ← افحص كيتونات قبل. وافحص كمان بعد التمرين.");
        if (last != null) {
            text = text + t("\n\nYour last reading: ", "\n\nآخر قراءة إلك: ") + last.getValue() + " mg/dL → "
                    + GlucoseReading.getWorkoutAdvice(last.getValue());
        }
        return text;
    }

    /**
     * Finds the exercise the user means.
     * 1) Arabic name (سكوات → squat)  2) the full name  3) the most matching special words of the name.
     */
    private Exercise findExercise(String q) {
        String search = q;
        for (int i = 0; i < ARABIC_NAMES.length; i++) {
            if (q.contains(ARABIC_NAMES[i][0])) {
                search = search + " " + ARABIC_NAMES[i][1];
            }
        }
        ArrayList<Exercise> all = WorkoutData.getLibrary();
        Exercise best = null;
        int bestScore = 0;
        for (int i = 0; i < all.size(); i++) {
            Exercise ex = all.get(i);
            String name = ex.getName().toLowerCase().replace('-', ' ');   // "pull-ups" → "pull ups" (like clean())
            if (search.contains(name)) {
                return ex;
            }
            String[] words = name.split(" ");
            int score = 0;
            for (int j = 0; j < words.length; j++) {
                String w = words[j];
                if (w.endsWith("s") && w.length() > 4) {
                    w = w.substring(0, w.length() - 1);   // "lunges" → "lunge", "flyes" → "flye"
                }
                if (w.length() > 2 && !isGeneric(w) && ChatBotEngine.has(search, new String[]{w})) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = ex;
            }
        }
        return best;
    }

    // true for words like "barbell" / "press" that are in many exercise names
    private boolean isGeneric(String word) {
        for (int i = 0; i < GENERIC.length; i++) {
            if (GENERIC[i].equals(word)) {
                return true;
            }
        }
        return false;
    }

    // Row of the muscle group in the MUSCLES table (-1 if the question has no muscle)
    private int findMuscle(String q) {
        for (int i = 0; i < MUSCLES.length; i++) {
            if (ChatBotEngine.has(q, new String[]{MUSCLES[i][0]})) {
                return i;
            }
        }
        return -1;
    }

    // YouTube search link (always works - no API key needed)
    public static String youtubeLink(String search) {
        try {
            return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(search, "UTF-8");
        } catch (Exception e) {
            return "https://www.youtube.com";
        }
    }
}
