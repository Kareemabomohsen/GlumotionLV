package com.example.glumotion.chat;

import com.example.glumotion.common.ApiHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 💊 The medication part of the chat bots:
 * 1) "add medication Metformin 500mg morning" / "ضيف دوا متفورمين 500 ملغ الصبح"
 *    → finds the name, the dose and the time → the chat screen asks "Add?" → saved in Firebase.
 * 2) "what is metformin?" / "معلومات عن دوا اسبرين"
 *    → reads the OFFICIAL drug label of the U.S. FDA (openFDA - free, no key):
 *      https://open.fda.gov/apis/drug/label/
 * The bot never invents medical information: it shows only what the FDA label says
 * and always reminds the user to ask the doctor / pharmacist.
 */
public class MedicationChatBot {

    // What the user asked to add
    public static class MedRequest {
        public String name = "";       // "Metformin"
        public String dosage = "";     // "500 mg"
        public String category = "";   // Morning / Afternoon / Evening / Bedtime ("" = not said yet)
        public String time = "";       // "8:00 AM"
    }

    // The answer of the FDA lookup
    public interface InfoCallback {
        void onInfo(String text);
    }

    // The same 4 times as the "Add medication" screen
    public static final String[] CATEGORIES = {"Morning", "Afternoon", "Evening", "Bedtime"};
    public static final String[] TIMES = {"8:00 AM", "2:00 PM", "6:00 PM", "9:30 PM"};

    private static final String[] ADD_WORDS = {"add", "save", "new medic", "ضيف", "أضف", "اضف", "اضيف", "أضيف", "سجل", "زيد"};
    private static final String[] MED_WORDS = {"medic", "med ", "meds", "pill", "drug", "tablet", "insulin",
            "دوا", "حبوب", "انسولين", "إنسولين", "ابرة", "ابره"};   // not "حبة" alone: "ضيف حبة تمر" is food
    private static final String[] INFO_WORDS = {"what is", "whats", "info", "side effect", "used for", "tell me about",
            "warning", "معلومات", "شو هو", "شو هاد", "شو هذا", "لشو", "اعراض", "أعراض", "تحذير", "ما هو"};
    private static final String[] QUESTION_WORDS = {"how", "where", "can i", "كيف", "وين", "فين"};

    // Words that are not part of the medication name
    private static final String[] SKIP_WORDS = {"add", "save", "new", "a", "an", "the", "my", "to", "list", "please", "pls",
            "medication", "medications", "medicine", "medicines", "med", "meds", "pill", "pills", "drug", "drugs",
            "tablet", "tablets", "called", "named", "i", "take", "every", "each", "day", "daily", "at", "in", "on",
            "of", "for", "with", "before", "after", "food", "meal", "what", "is", "whats", "info", "about", "tell", "me",
            "side", "effects", "effect", "used", "warning", "warnings", "and",
            "ضيف", "أضف", "اضف", "اضيف", "أضيف", "ضيفلي", "اضفلي", "سجل", "سجلي", "زيد", "لي", "إلي", "الي", "جديد",
            "دوا", "دواء", "الدوا", "الدواء", "دوائي", "دواي", "حبة", "حبه", "حبوب", "اسمه", "إسمه", "اسمها", "عن",
            "معلومات", "شو", "هو", "هاد", "هذا", "لشو", "ما", "كل", "يوم", "باليوم", "قبل", "بعد", "الأكل", "الاكل",
            "اعراض", "أعراض", "جانبية", "جانبيه", "تحذير", "تحذيرات", "و", "على", "في", "ب", "من"};

    // Time words → index in CATEGORIES (Arabic words can have ال / ب / و before them)
    private static final String[][] TIME_WORDS = {
            {"0", "morning", "breakfast", "صبح", "صباح", "فطور", "الفجر"},
            {"1", "afternoon", "noon", "lunch", "ظهر", "عصر", "غدا"},
            {"2", "evening", "dinner", "مسا", "مساء", "عشا", "ليل"},
            {"3", "bedtime", "sleep", "night", "نوم", "النوم"}};

    // Arabic / brand names → the English (generic) name the FDA knows
    private static final String[][] NAMES = {
            {"متفورمين", "Metformin"}, {"ميتفورمين", "Metformin"}, {"جلوكوفاج", "Metformin"}, {"glucophage", "Metformin"},
            {"انسولين", "Insulin"}, {"إنسولين", "Insulin"}, {"لانتوس", "Insulin glargine"}, {"lantus", "Insulin glargine"},
            {"نوفورابيد", "Insulin aspart"}, {"novorapid", "Insulin aspart"},
            {"جانوفيا", "Sitagliptin"}, {"januvia", "Sitagliptin"}, {"جارديانس", "Empagliflozin"}, {"jardiance", "Empagliflozin"},
            {"فورسيجا", "Dapagliflozin"}, {"forxiga", "Dapagliflozin"}, {"اوزمبيك", "Semaglutide"}, {"ozempic", "Semaglutide"},
            {"جليكلازيد", "Gliclazide"}, {"اسبرين", "Aspirin"}, {"أسبرين", "Aspirin"}, {"اكامول", "Acetaminophen"},
            {"acamol", "Acetaminophen"}, {"باراسيتامول", "Acetaminophen"}, {"paracetamol", "Acetaminophen"},
            {"ليبيتور", "Atorvastatin"}, {"اتورفاستاتين", "Atorvastatin"}, {"lipitor", "Atorvastatin"},
            {"ايبوبروفين", "Ibuprofen"}, {"ادفيل", "Ibuprofen"}, {"advil", "Ibuprofen"},
            {"metformin", "Metformin"}, {"sitagliptin", "Sitagliptin"}, {"empagliflozin", "Empagliflozin"},
            {"semaglutide", "Semaglutide"}, {"gliclazide", "Gliclazide"}, {"aspirin", "Aspirin"},
            {"acetaminophen", "Acetaminophen"}, {"atorvastatin", "Atorvastatin"}, {"ibuprofen", "Ibuprofen"}};

    // "4 tablets" / "٥٠٠ ملغ" / "10 units" → number + unit
    private static final Pattern DOSE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(mg|mcg|ml|iu|units?|g|tablets?|pills?|ملغم|ملغ|مغ|مل|وحدات|وحدة|وحده|حبات|حبة|حبه)?(?![a-z\\u0600-\\u06FF])");

    // true if the message asks to ADD a medication (not "how do I add ...", that is the app guide)
    public static boolean isAddRequest(String text) {
        String q = " " + ChatBotEngine.clean(text.toLowerCase()) + " ";
        if (ChatBotEngine.has(q, QUESTION_WORDS)) {
            return false;
        }
        return ChatBotEngine.has(q, ADD_WORDS) && (ChatBotEngine.has(q, MED_WORDS) || knownName(q) != null);
    }

    // true if the message asks what a medication is ("what is metformin", "معلومات عن دوا ...")
    public static boolean isInfoRequest(String text) {
        String q = " " + ChatBotEngine.clean(text.toLowerCase()) + " ";
        if (!ChatBotEngine.has(q, INFO_WORDS)) {
            return false;
        }
        return ChatBotEngine.has(q, MED_WORDS) || knownName(q) != null;
    }

    // Reads the name, dose and time from the message
    public static MedRequest parse(String text) {
        MedRequest r = new MedRequest();
        String q = toLatinDigits(text.toLowerCase());

        // 1) the dose
        Matcher m = DOSE.matcher(q);
        if (m.find()) {
            String unit = m.group(2);
            r.dosage = m.group(1);
            if (unit != null) {
                r.dosage = r.dosage + " " + englishUnit(unit);
            }
            q = q.substring(0, m.start()) + " " + q.substring(m.end());
        }

        // 2) the time
        String cleaned = " " + ChatBotEngine.clean(q) + " ";
        for (int i = TIME_WORDS.length - 1; i >= 0; i--) {
            String[] words = new String[TIME_WORDS[i].length - 1];
            for (int j = 1; j < TIME_WORDS[i].length; j++) {
                words[j - 1] = TIME_WORDS[i][j];
            }
            if (r.category.isEmpty() && ChatBotEngine.has(cleaned, words)) {
                int index = Integer.parseInt(TIME_WORDS[i][0]);
                r.category = CATEGORIES[index];
                r.time = TIMES[index];
            }
        }

        // 3) the name = the words that are left
        String known = knownName(cleaned);
        if (known != null) {
            r.name = known;
            return r;
        }
        String[] tokens = cleaned.trim().split("\\s+");
        String name = "";
        for (int i = 0; i < tokens.length; i++) {
            String w = tokens[i];
            if (w.isEmpty() || isSkipWord(w) || isTimeWord(w)) {
                continue;
            }
            if (!name.isEmpty()) {
                name = name + " ";
            }
            name = name + w;
        }
        if (!name.isEmpty() && !ChatBotEngine.isArabic(name)) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        r.name = name;
        return r;
    }

    // Only the name, for "what is ..." questions
    public static String nameOf(String text) {
        return parse(text).name;
    }

    // true if the word is not part of the name
    private static boolean isSkipWord(String w) {
        for (int i = 0; i < SKIP_WORDS.length; i++) {
            if (SKIP_WORDS[i].equals(w)) {
                return true;
            }
        }
        return false;
    }

    // true if the word is a time word ("الصبح", "بالليل", "morning")
    private static boolean isTimeWord(String w) {
        for (int i = 0; i < TIME_WORDS.length; i++) {
            for (int j = 1; j < TIME_WORDS[i].length; j++) {
                String t = TIME_WORDS[i][j];
                if (w.equals(t) || (ChatBotEngine.isArabic(t) && w.contains(t))) {
                    return true;
                }
            }
        }
        return false;
    }

    // The English name if the message has a known Arabic / brand name
    private static String knownName(String cleaned) {
        String padded = " " + cleaned + " ";
        for (int i = 0; i < NAMES.length; i++) {
            if (padded.contains(NAMES[i][0])) {
                return NAMES[i][1];
            }
        }
        return null;
    }

    // "ملغ" → "mg"
    private static String englishUnit(String unit) {
        if (unit.equals("ملغ") || unit.equals("ملغم") || unit.equals("مغ")) {
            return "mg";
        }
        if (unit.equals("مل")) {
            return "ml";
        }
        if (unit.equals("وحدة") || unit.equals("وحده") || unit.equals("وحدات") || unit.startsWith("unit")) {
            return "units";
        }
        if (unit.equals("حبة") || unit.equals("حبه") || unit.equals("حبات") || unit.startsWith("tablet") || unit.startsWith("pill")) {
            return "tablet(s)";
        }
        return unit;
    }

    // "٥٠٠" → "500"
    private static String toLatinDigits(String text) {
        String result = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '٠' && c <= '٩') {
                c = (char) ('0' + (c - '٠'));
            }
            result = result + c;
        }
        return result;
    }

    /**
     * Reads the official FDA drug label (openFDA) of the medication and sends a short text:
     * what it is used for + the main warning + the source.
     */
    public static void lookup(final String name, final boolean arabic, final InfoCallback callback) {
        if (name.isEmpty() || ChatBotEngine.isArabic(name)) {
            callback.onInfo(pick(arabic,
                    "ℹ To read the official information, write the medication name in English (e.g. \"what is metformin\").",
                    "ℹ عشان أجيبلك المعلومات الرسمية، اكتب اسم الدوا بالإنجليزي (مثلاً \"what is metformin\")."));
            return;
        }
        String url;
        try {
            String search = "openfda.generic_name:\"" + name + "\" OR openfda.brand_name:\"" + name + "\"";
            url = "https://api.fda.gov/drug/label.json?search=" + URLEncoder.encode(search, "UTF-8") + "&limit=1";
        } catch (Exception e) {
            return;
        }
        ApiHelper.get(url, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onInfo(readLabel(response, name, arabic));
            }

            @Override
            public void onError(String error) {
                callback.onInfo(pick(arabic,
                        "ℹ I couldn't find \"" + name + "\" in the FDA drug labels (or there is no internet). Ask your pharmacist.",
                        "ℹ ما لقيت \"" + name + "\" بنشرات الأدوية تبعت FDA (أو ما في إنترنت). اسأل الصيدلي."));
            }
        });
    }

    // Takes the important parts from the FDA answer (JSON)
    private static String readLabel(String json, String name, boolean arabic) {
        try {
            JSONObject label = new JSONObject(json).getJSONArray("results").getJSONObject(0);
            String generic = name;
            JSONObject openfda = label.optJSONObject("openfda");
            if (openfda != null && openfda.optJSONArray("generic_name") != null) {
                generic = openfda.getJSONArray("generic_name").getString(0);
            }
            String uses = firstOf(label, new String[]{"indications_and_usage", "purpose"});
            String warning = firstOf(label, new String[]{"boxed_warning", "warnings", "warnings_and_cautions"});

            String text = pick(arabic, "📋 " + generic + " - from the official drug label:\n",
                    "📋 " + generic + " - من النشرة الرسمية للدوا (بالإنجليزي):\n");
            if (!uses.isEmpty()) {
                text = text + pick(arabic, "• Used for: ", "• بيستعمل لـ: ") + shorten(uses, 300) + "\n";
            }
            if (!warning.isEmpty()) {
                text = text + pick(arabic, "• ⚠ Warning: ", "• ⚠ تحذير: ") + shorten(warning, 220) + "\n";
            }
            return text + pick(arabic,
                    "📚 Source: U.S. FDA drug label (open.fda.gov)\nThis is general information, not medical advice - "
                            + "take your medication exactly as your doctor said.",
                    "📚 المصدر: نشرة الدوا الرسمية من FDA الأمريكية (open.fda.gov)\nهاي معلومات عامة مش استشارة طبية - "
                            + "خذ دواك بالضبط متل ما حكالك الدكتور.");
        } catch (Exception e) {
            return pick(arabic, "ℹ No official label found for \"" + name + "\". Ask your pharmacist.",
                    "ℹ ما في نشرة رسمية لـ \"" + name + "\". اسأل الصيدلي.");
        }
    }

    // The first field of the label that exists ("" if none)
    private static String firstOf(JSONObject label, String[] fields) {
        for (int i = 0; i < fields.length; i++) {
            JSONArray arr = label.optJSONArray(fields[i]);
            if (arr != null && arr.length() > 0) {
                String text = arr.optString(0, "").replaceAll("\\s+", " ").trim();
                // remove the title of the part ("1 INDICATIONS AND USAGE", "Purpose", "WARNINGS")
                text = text.replaceFirst("^(?i)[0-9.\\s]*(indications (and|&) usage|indications|uses?|purposes?|"
                        + "boxed warning|warnings and precautions|warnings|warning)[:\\s]*", "");
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    // Cuts a long text at the end of a sentence
    private static String shorten(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        int dot = cut.lastIndexOf(". ");
        if (dot > max / 2) {
            return cut.substring(0, dot + 1);
        }
        return cut + "...";
    }

    // English or Arabic text
    private static String pick(boolean arabic, String english, String arabicText) {
        if (arabic) {
            return arabicText;
        }
        return english;
    }
}
