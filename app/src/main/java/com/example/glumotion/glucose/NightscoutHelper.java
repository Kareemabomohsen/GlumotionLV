package com.example.glumotion.glucose;

import com.example.glumotion.common.ApiHelper;
import com.example.glumotion.common.FRef;

import android.content.Context;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 🔗 Link to a WORLD app: Nightscout (free, open source, used by people with diabetes all over the world).
 * Dexcom / FreeStyle Libre sensors (CGM) upload the readings to Nightscout,
 * and here we import them into our glucose log.
 *
 * ===================== كيف تفعّلها =====================
 * 1) اعمل موقع Nightscout مجاني (nightscout.github.io) واربطه بالحساس (Dexcom / Libre).
 * 2) اكتب رابط الموقع تحت بـ NIGHTSCOUT_URL (وإذا في token اكتبه بـ NIGHTSCOUT_TOKEN).
 * 3) بملف DMActivity.java شيل الـ // من السطر:  NightscoutHelper.importLatest(this);
 * ======================================================
 */
public class NightscoutHelper {

    // ← اكتب هون رابط موقع الـ Nightscout تبعك (بدون / بالآخر)
    public static final String NIGHTSCOUT_URL = "https://YOUR-SITE.herokuapp.com";
    // ← إذا الموقع محمي، اكتب الـ token (من Admin Tools)، وإلا خليه فاضي
    public static final String NIGHTSCOUT_TOKEN = "";

    // Imports the last 20 sensor readings into Users / uid / GlucoseReadings
    public static void importLatest(final Context context) {
        if (NIGHTSCOUT_URL.contains("YOUR-SITE")) {
            Toast.makeText(context, "Write your Nightscout URL in NightscoutHelper.java", Toast.LENGTH_LONG).show();
            return;
        }
        String url = NIGHTSCOUT_URL + "/api/v1/entries/sgv.json?count=20";
        if (!NIGHTSCOUT_TOKEN.isEmpty()) {
            url = url + "&token=" + NIGHTSCOUT_TOKEN;
        }

        ApiHelper.get(url, new ApiHelper.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                int count = saveEntries(response);
                if (count > 0) {
                    Toast.makeText(context, "Imported " + count + " CGM readings", Toast.LENGTH_SHORT).show();
                }
                // ================================================================
                // 🚨 إذا آخر قراءة من الحساس خطيرة ← إشعار + SMS للطوارئ + المدرب (وقت التمرين)
                // لتفعيل: شيل الـ // من السطرين (وكمل باقي الخطوات بـ EmergencyHelper.java)
                // ================================================================
                // int latest = latestValue(response);
                // EmergencyHelper.checkAndAlert(context, latest);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(context, "Nightscout: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Answer = [{"sgv":120,"date":1730540000000,...}, ...]
     * The key is "ns_" + the time → the same reading is never saved twice.
     */
    private static int saveEntries(String json) {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return 0;
        }
        Map<String, Object> updates = new HashMap<>();
        try {
            JSONArray entries = new JSONArray(json);
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                int sgv = entry.optInt("sgv", 0);
                long date = entry.optLong("date", 0);
                if (sgv > 0 && date > 0) {
                    GlucoseReading reading = new GlucoseReading(sgv, "CGM sensor");
                    reading.setTimestamp(date);
                    updates.put("GlucoseReadings/ns_" + date, reading.toMap());
                }
            }
        } catch (Exception e) {
            return 0;
        }
        if (!updates.isEmpty()) {
            userRef.updateChildren(updates);
        }
        return updates.size();
    }

    // The newest reading of the answer (the biggest "date"), 0 if none
    public static int latestValue(String json) {
        int value = 0;
        long newest = 0;
        try {
            JSONArray entries = new JSONArray(json);
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                long date = entry.optLong("date", 0);
                if (date > newest && entry.optInt("sgv", 0) > 0) {
                    newest = date;
                    value = entry.optInt("sgv", 0);
                }
            }
        } catch (Exception e) {
            return 0;
        }
        return value;
    }
}
