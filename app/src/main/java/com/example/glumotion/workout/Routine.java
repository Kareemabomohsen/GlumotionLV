package com.example.glumotion.workout;

import com.example.glumotion.R;

import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A workout routine (Chest & Triceps, Back & Biceps, Legs & Abs)
 * = a name + some info + a list (ArrayList) of exercises.
 */
public class Routine {

    private String title;
    private int minutes;
    private int kcal;
    private String muscles;
    private int iconRes;
    private ArrayList<Exercise> exercises;

    // Only for routines the user created (Hevy style) - saved in Users/uid/Routines/id
    private String id;
    private String day = "Any day";

    // Constructor
    public Routine(String title, int minutes, int kcal, String muscles, int iconRes) {
        this.title = title;
        this.minutes = minutes;
        this.kcal = kcal;
        this.muscles = muscles;
        this.iconRes = iconRes;
        this.exercises = new ArrayList<>();
    }

    // Adds an exercise to the routine
    public void addExercise(Exercise exercise) {
        exercises.add(exercise);
    }

    // Total number of sets in the routine
    public int getTotalSets() {
        int total = 0;
        for (int i = 0; i < exercises.size(); i++) {
            total = total + exercises.get(i).getSets();
        }
        return total;
    }

    // Converts the user routine to a HashMap for Firebase
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", title);
        map.put("day", day);
        map.put("updatedAt", System.currentTimeMillis());
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < exercises.size(); i++) {
            Exercise ex = exercises.get(i);
            Map<String, Object> exMap = new HashMap<>();
            exMap.put("name", ex.getName());
            exMap.put("muscle", ex.getPrimaryMuscle());
            exMap.put("sets", ex.getSets());
            exMap.put("reps", ex.getReps());
            exMap.put("rest", ex.getRestSeconds());
            list.add(exMap);
        }
        map.put("exercises", list);
        return map;
    }

    // Reads a user routine back from Firebase
    public static Routine fromSnapshot(DataSnapshot snap) {
        String name = snap.child("name").getValue(String.class);
        if (name == null) {
            return null;
        }
        Routine routine = new Routine(name, 0, 0, "", R.drawable.ic_workout);
        routine.setId(snap.getKey());
        String day = snap.child("day").getValue(String.class);
        if (day != null) {
            routine.setDay(day);
        }
        String muscles = "";
        for (DataSnapshot exSnap : snap.child("exercises").getChildren()) {
            String exName = exSnap.child("name").getValue(String.class);
            if (exName == null) {
                continue;
            }
            String muscle = exSnap.child("muscle").getValue(String.class);
            if (muscle == null) {
                muscle = "-";
            }
            int sets = readInt(exSnap, "sets", 3);
            int rest = readInt(exSnap, "rest", 90);
            String reps = exSnap.child("reps").getValue(String.class);
            if (reps == null) {
                reps = "10";
            }
            routine.addExercise(Exercise.createCustom(exName, muscle, sets, reps, rest));
            if (!muscles.contains(muscle)) {
                if (!muscles.isEmpty()) {
                    muscles = muscles + ", ";
                }
                muscles = muscles + muscle;
            }
        }
        routine.muscles = muscles;
        // Rough estimate: every set ≈ 2.5 minutes with rest, ≈ 18 kcal
        routine.minutes = (int) (routine.getTotalSets() * 2.5);
        routine.kcal = routine.getTotalSets() * 18;
        return routine;
    }

    // Helper: reads a number from Firebase
    private static int readInt(DataSnapshot snap, String key, int defaultValue) {
        Long value = snap.child(key).getValue(Long.class);
        if (value == null) {
            return defaultValue;
        }
        return value.intValue();
    }

    // "Bench Press, Squat, Deadlift +2"
    public String getExercisesPreview() {
        String text = "";
        for (int i = 0; i < exercises.size() && i < 3; i++) {
            if (i > 0) {
                text = text + ", ";
            }
            text = text + exercises.get(i).getName();
        }
        if (exercises.size() > 3) {
            text = text + " +" + (exercises.size() - 3);
        }
        return text;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
    public void setTitle(String title) { this.title = title; }

    public String getTitle() { return title; }
    public int getMinutes() { return minutes; }
    public int getKcal() { return kcal; }
    public String getMuscles() { return muscles; }
    public int getIconRes() { return iconRes; }
    public ArrayList<Exercise> getExercises() { return exercises; }
}
