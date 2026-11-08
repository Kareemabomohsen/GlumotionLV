package com.example.glumotion.workout;

import com.example.glumotion.R;

/**
 * One gym exercise (e.g. Barbell Bench Press) with everything shown in the "Explanation" tab
 * and the default numbers used in the workout logger.
 */
public class Exercise {

    private String name;
    private String primaryMuscle;
    private String secondaryMuscles;
    private int sets;
    private String reps;           // text for display: "8-10"
    private int defaultReps;       // number used as hint in the logger
    private double defaultKg;      // starting weight hint (0 = body weight)
    private int restSeconds;
    private String[] formCues;     // Array of tips
    private String progression;
    private int imageRes;          // drawable shown at the top of the card

    // Constructor
    public Exercise(String name, String primaryMuscle, String secondaryMuscles, int sets, String reps,
                    int defaultReps, double defaultKg, int restSeconds, String[] formCues,
                    String progression, int imageRes) {
        this.name = name;
        this.primaryMuscle = primaryMuscle;
        this.secondaryMuscles = secondaryMuscles;
        this.sets = sets;
        this.reps = reps;
        this.defaultReps = defaultReps;
        this.defaultKg = defaultKg;
        this.restSeconds = restSeconds;
        this.formCues = formCues;
        this.progression = progression;
        this.imageRes = imageRes;
    }

    /**
     * Creates an exercise that the user chose for his own routine (from the API or typed by him).
     * If the app knows this exercise, its tips and starting weight are copied.
     */
    public static Exercise createCustom(String name, String muscle, int sets, String reps, int restSeconds) {
        Exercise known = WorkoutData.findExercise(name);
        int defaultReps = 10;
        try {
            // "8-10" → 8
            String first = reps.split("-")[0].trim();
            defaultReps = Integer.parseInt(first);
        } catch (Exception e) {
            defaultReps = 10;
        }
        if (known != null) {
            return new Exercise(name, known.getPrimaryMuscle(), known.getSecondaryMuscles(), sets, reps,
                    defaultReps, known.getDefaultKg(), restSeconds, known.getFormCues(), known.getProgression(), known.getImageRes());
        }
        return new Exercise(name, muscle, "-", sets, reps, defaultReps, 0, restSeconds,
                new String[]{"Control the weight", "Full range of motion"}, "Add weight when all reps are easy", R.drawable.ic_workout);
    }

    // "Primary: Chest • Secondary: Triceps, Shoulders"
    public String getMusclesText() {
        return "Primary: " + primaryMuscle + " • Secondary: " + secondaryMuscles;
    }

    // "4 × 8-10"
    public String getSetsRepsText() {
        return sets + " × " + reps;
    }

    // All the cues as bullet lines
    public String getCuesText() {
        String text = "";
        for (int i = 0; i < formCues.length; i++) {
            text = text + "• " + formCues[i];
            if (i < formCues.length - 1) {
                text = text + "\n";
            }
        }
        return text;
    }

    // A safe key for Firebase ("Barbell Bench Press" → "barbell_bench_press")
    public String getKey() {
        String key = name.toLowerCase();
        key = key.replace(" ", "_");
        key = key.replace("-", "_");
        key = key.replace(".", "");
        key = key.replace("/", "_");
        key = key.replace("#", "");
        key = key.replace("$", "");
        key = key.replace("[", "");
        key = key.replace("]", "");
        return key;
    }

    // Weight text without ".0" (60.0 → "60", 22.5 → "22.5")
    public static String formatKg(double kg) {
        if (kg == (int) kg) {
            return String.valueOf((int) kg);
        }
        return String.valueOf(kg);
    }

    public String getName() { return name; }
    public String getPrimaryMuscle() { return primaryMuscle; }
    public String getSecondaryMuscles() { return secondaryMuscles; }
    public int getSets() { return sets; }
    public String getReps() { return reps; }
    public int getDefaultReps() { return defaultReps; }
    public double getDefaultKg() { return defaultKg; }
    public int getRestSeconds() { return restSeconds; }
    public String[] getFormCues() { return formCues; }
    public String getProgression() { return progression; }
    public int getImageRes() { return imageRes; }
}
