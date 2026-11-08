package com.example.glumotion.glucose;

import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;

import com.google.firebase.database.Exclude;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * One blood sugar reading (mg/dL).
 * Saved in Firebase under Users / uid / GlucoseReadings / id
 * Used by the Blood Sugar (DM) screen and by the workout (reading before training).
 */
public class GlucoseReading {

    // Target range used in the whole app (same as the DM screen: 70-140 mg/dL)
    public static final int LOW_LIMIT = 70;
    public static final int TARGET_HIGH = 140;
    public static final int HIGH_LIMIT = 180;

    // When the reading was taken (shown in a spinner)
    public static final String[] CONTEXTS = {"Fasting", "Before Breakfast", "After Breakfast", "Before Lunch",
            "After Lunch", "Before Dinner", "After Dinner", "Bedtime", "Other"};

    // "How do you feel?" chips of the log screen
    public static final String[] FEELINGS = {"😊 Normal", "😫 Tired", "🤢 Nauseous", "💧 Thirsty", "😵 Dizzy", "🍽 Hungry"};

    private String id;
    private int value;
    private String context;
    private long timestamp;
    private String date; // "2026-11-02" - makes it easy to search by day in the Firebase console
    private String feelings = "";
    private String note = "";

    // Empty constructor - needed by Firebase
    public GlucoseReading() {
    }

    // Constructor for a new reading (time = now)
    public GlucoseReading(int value, String context) {
        this.value = value;
        this.context = context;
        this.timestamp = System.currentTimeMillis();
    }

    // Converts to HashMap for Firebase
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("value", value);
        map.put("context", context);
        map.put("timestamp", timestamp);
        map.put("date", DateUtil.dateKey(getCalendar()));
        map.put("feelings", feelings);
        map.put("note", note);
        return map;
    }

    // Saves a new reading in Users / uid / GlucoseReadings (returns false if not logged in)
    public static boolean save(GlucoseReading reading) {
        if (FRef.getRefCurrentUser() == null) {
            return false;
        }
        FRef.getRefCurrentUser().child("GlucoseReadings").push().setValue(reading.toMap());
        return true;
    }

    // "Low" / "Normal" / "Elevated" / "High"
    public static String getStatus(int value) {
        if (value < LOW_LIMIT) {
            return "Low";
        }
        if (value <= TARGET_HIGH) {
            return "Normal";
        }
        if (value <= HIGH_LIMIT) {
            return "Elevated";
        }
        return "High";
    }

    // Color of every status (red / green / orange / red)
    public static int getStatusColor(int value) {
        String status = getStatus(value);
        if (status.equals("Normal")) {
            return 0xFF059669;
        }
        if (status.equals("Elevated")) {
            return 0xFFE67E22;
        }
        return 0xFFD63031;
    }

    // True if the value is inside 70-140
    public static boolean isInRange(int value) {
        return value >= LOW_LIMIT && value <= TARGET_HIGH;
    }

    /**
     * Safety message before exercise (general guidance for people with diabetes).
     * Returns the text to show in the dialog.
     */
    public static String getWorkoutAdvice(int value) {
        if (value < LOW_LIMIT) {
            return "Too low to train! Eat 15g of fast carbs (juice / glucose tablets), wait 15 minutes and check again before you start.";
        }
        if (value < 90) {
            return "A bit low for exercise. Eat 15-30g of carbs before you start and keep a snack with you.";
        }
        if (value <= 250) {
            return "Good range for training. Drink water and keep a fast-carb snack nearby.";
        }
        return "High reading. If you have type 1 diabetes, check ketones first and avoid intense exercise if ketones are present. Ask your doctor if this happens often.";
    }

    // Calendar of the reading time
    @Exclude
    public Calendar getCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return cal;
    }

    // "08:00 AM"
    @Exclude
    public String getTimeText() {
        Calendar cal = getCalendar();
        return DateUtil.formatTime12(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getFeelings() { return feelings; }
    public void setFeelings(String feelings) { this.feelings = feelings; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
