package com.example.glumotion.medications;

import com.example.glumotion.common.DateUtil;

import com.google.firebase.database.Exclude;

import java.util.HashMap;
import java.util.Map;

/**
 * One medication of the user.
 * Saved in Firebase under Users / uid / Medications / id
 */
public class MedicationModel {

    // Timing options shown to the user (arrays: label → category → default time)
    public static final String[] TIMING_LABELS = {
            "Morning - before breakfast",
            "Morning - after breakfast",
            "Afternoon - with lunch",
            "Evening - with dinner",
            "Bedtime"
    };
    public static final String[] TIMING_CATEGORIES = {"Morning", "Morning", "Afternoon", "Evening", "Bedtime"};
    public static final String[] TIMING_TIMES = {"7:30 AM", "8:30 AM", "1:00 PM", "7:00 PM", "10:00 PM"};

    private String id;
    private String name;
    private String dosage;
    private String time;
    private String timingCategory; // Morning, Afternoon, Evening, Bedtime
    private String frequency;      // Daily
    private String instruction;    // e.g. "Morning - before breakfast"
    private String status;         // Taken, Skipped, Pending
    private String statusDate;     // the day the status was set ("2026-11-02") - every new day starts as Pending

    // Empty constructor - needed by Firebase
    public MedicationModel() {
    }

    // Full constructor
    public MedicationModel(String id, String name, String time, String timingCategory, String frequency, String instruction, String status) {
        this.id = id;
        this.name = name;
        this.time = time;
        this.timingCategory = timingCategory;
        this.frequency = frequency;
        this.instruction = instruction;
        this.status = status;
        this.dosage = "";
    }

    // Creates a new daily medication from the index of the timing option
    public static MedicationModel create(String name, String dosage, int timingIndex) {
        if (timingIndex < 0 || timingIndex >= TIMING_LABELS.length) {
            timingIndex = 0;
        }
        MedicationModel med = new MedicationModel(null, name, TIMING_TIMES[timingIndex],
                TIMING_CATEGORIES[timingIndex], "Daily", TIMING_LABELS[timingIndex], "Pending");
        med.setDosage(dosage);
        return med;
    }

    // Creates a new daily medication from a time category (used by the "Add medication" screen)
    public static MedicationModel createForCategory(String name, String dosage, String category, String time) {
        MedicationModel med = new MedicationModel(null, name, time, category, "Daily", "Take as prescribed", "Pending");
        med.setDosage(dosage);
        return med;
    }

    // Converts the object to a HashMap for Firebase (the id is the key, so it's saved too)
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("dosage", dosage);
        map.put("time", time);
        map.put("timingCategory", timingCategory);
        map.put("frequency", frequency);
        map.put("instruction", instruction);
        map.put("status", status);
        map.put("statusDate", statusDate);
        return map;
    }

    // Short text for lists: "Metformin 500mg" (@Exclude = not saved in Firebase)
    @Exclude
    public String getDisplayName() {
        if (dosage == null || dosage.isEmpty()) {
            return name;
        }
        return name + " " + dosage;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getTimingCategory() { return timingCategory; }
    public void setTimingCategory(String timingCategory) { this.timingCategory = timingCategory; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    // The status of TODAY: a "Taken" from yesterday counts as "Pending" today (daily medications)
    @Exclude
    public String getTodayStatus() {
        if (status == null || statusDate == null || !statusDate.equals(DateUtil.todayKey())) {
            return "Pending";
        }
        return status;
    }

    public String getStatusDate() { return statusDate; }
    public void setStatusDate(String statusDate) { this.statusDate = statusDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
