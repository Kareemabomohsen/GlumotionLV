package com.example.glumotion.chat;

import com.example.glumotion.common.ApiHelper;
import com.example.glumotion.profile.ImageHelper;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 🤖 OPTIONAL: real AI answers with Google Gemini (has a free tier).
 * The built-in bot (ChatBotEngine) works without it. Gemini is used only for questions the bot doesn't know.
 *
 * ===================== كيف تفعّله =====================
 * 1) خذ مفتاح مجاني من https://aistudio.google.com/apikey
 * 2) اكتبه تحت بـ API_KEY
 * 3) بملف ChatBotActivity.java شيل الـ // من الأسطر اللي مكتوب فوقها "Gemini"
 * 4) تصوير الوجبة ببوت الأكل بيشتغل لحاله أول ما تحط المفتاح (analyzeFoodPhoto)
 * (إذا Google غيّرت اسم الموديل، غيّر MODEL لاسم الموديل الجديد من نفس الموقع)
 * ======================================================
 */
public class GeminiHelper {

    public static final String API_KEY = "PUT-YOUR-KEY-HERE";   // ← المفتاح هون
    public static final String MODEL = "gemini-2.5-flash";

    // true when a real key was written above
    public static boolean isEnabled() {
        return !API_KEY.startsWith("PUT");
    }

    // Sends the question (+ the user's data as context) and returns the answer
    public static void ask(String question, String userContext, final ApiHelper.ApiCallback callback) {
        if (!isEnabled()) {
            callback.onError("No Gemini key");
            return;
        }
        String prompt = "You are GluBot, a friendly assistant inside a diabetes app (food, workouts, blood sugar). "
                + "Answer short (max 5 lines), in the same language as the question. Never give medicine doses; "
                + "tell the user to ask his doctor for medical decisions.\nUser data: " + userContext
                + "\nQuestion: " + question;
        try {
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            JSONArray parts = new JSONArray();
            parts.put(part);
            JSONObject content = new JSONObject();
            content.put("parts", parts);
            JSONArray contents = new JSONArray();
            contents.put(content);
            JSONObject body = new JSONObject();
            body.put("contents", contents);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL
                    + ":generateContent?key=" + API_KEY;
            ApiHelper.postJson(url, body.toString(), new ApiHelper.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        // candidates[0].content.parts[0].text
                        String text = new JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                        callback.onSuccess(text.trim());
                    } catch (Exception e) {
                        callback.onError("Bad answer");
                    }
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } catch (Exception e) {
            callback.onError("Error");
        }
    }
    /**
     * 📷 Food photo → Gemini (it can "see" pictures) → JSON with the foods and their values.
     * The photo is sent as Base64 text inside the request ("inline_data").
     */
    public static void analyzeFoodPhoto(android.graphics.Bitmap photo, final ApiHelper.ApiCallback callback) {
        if (!isEnabled()) {
            callback.onError("No Gemini key");
            return;
        }
        String prompt = "You are a nutrition expert. Look at the meal in the photo and estimate every food and its weight. "
                + "Answer ONLY with JSON like this: {\"foods\":[{\"name\":\"White rice\",\"grams\":200,\"kcal\":260,"
                + "\"carbs\":56,\"protein\":5,\"fat\":1,\"sugar\":0}]} . The values are for the grams in the photo. "
                + "Use short English names. If there is no food in the photo answer {\"foods\":[]}";
        try {
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);

            JSONObject image = new JSONObject();
            image.put("mime_type", "image/jpeg");
            image.put("data", ImageHelper.bitmapToBase64(photo).replace("\n", ""));
            JSONObject imagePart = new JSONObject();
            imagePart.put("inline_data", image);

            JSONArray parts = new JSONArray();
            parts.put(textPart);
            parts.put(imagePart);
            JSONObject content = new JSONObject();
            content.put("parts", parts);
            JSONArray contents = new JSONArray();
            contents.put(content);
            JSONObject body = new JSONObject();
            body.put("contents", contents);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL
                    + ":generateContent?key=" + API_KEY;
            ApiHelper.postJson(url, body.toString(), new ApiHelper.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        String text = new JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                        callback.onSuccess(text);
                    } catch (Exception e) {
                        callback.onError("Bad answer");
                    }
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } catch (Exception e) {
            callback.onError("Error");
        }
    }
}
