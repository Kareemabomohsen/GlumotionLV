package com.example.glumotion.workout;

import com.example.glumotion.R;

import java.util.ArrayList;

/**
 * All the routines and exercises of the app (static data, no internet needed).
 * The routines here match the 3 cards on the Workout screen.
 */
public class WorkoutData {

    // Index of every routine (same order as the cards on the screen)
    public static final int CHEST_TRICEPS = 0;
    public static final int BACK_BICEPS = 1;
    public static final int LEGS_ABS = 2;

    private static ArrayList<Routine> routines;

    // Returns the list of routines (built only once)
    public static ArrayList<Routine> getRoutines() {
        if (routines == null) {
            routines = new ArrayList<>();
            routines.add(buildChestTriceps());
            routines.add(buildBackBiceps());
            routines.add(buildLegsAbs());
        }
        return routines;
    }

    // Returns one routine by its index
    public static Routine getRoutine(int index) {
        if (index < 0 || index >= getRoutines().size()) {
            index = 0;
        }
        return getRoutines().get(index);
    }

    // All the exercises of all routines (for the "Explanation" tab)
    public static ArrayList<Exercise> getAllExercises() {
        ArrayList<Exercise> all = new ArrayList<>();
        for (int i = 0; i < getRoutines().size(); i++) {
            all.addAll(getRoutines().get(i).getExercises());
        }
        return all;
    }

    private static ArrayList<Exercise> library;

    /**
     * Big exercise library for the "Add exercise" screen (works without internet):
     * all routine exercises + extra exercises (cardio is very good for diabetics).
     */
    public static ArrayList<Exercise> getLibrary() {
        if (library == null) {
            library = new ArrayList<>();
            library.addAll(getAllExercises());
            String[][] extra = {
                    {"Push-ups", "Chest"}, {"Machine Chest Press", "Chest"}, {"Pec Deck", "Chest"},
                    {"Overhead Press", "Shoulders"}, {"Dumbbell Shoulder Press", "Shoulders"}, {"Lateral Raise", "Shoulders"},
                    {"Rear Delt Fly", "Shoulders"}, {"Seated Cable Row", "Back"}, {"Dumbbell Row", "Back"},
                    {"T-Bar Row", "Back"}, {"Hammer Curl", "Biceps"}, {"Preacher Curl", "Biceps"},
                    {"Skull Crusher", "Triceps"}, {"Close Grip Bench Press", "Triceps"}, {"Romanian Deadlift", "Hamstrings"},
                    {"Bulgarian Split Squat", "Quads"}, {"Leg Extension", "Quads"}, {"Hip Thrust", "Glutes"},
                    {"Standing Calf Raise", "Calves"}, {"Crunches", "Abs"}, {"Russian Twist", "Abs"},
                    {"Cable Crunch", "Abs"}, {"Treadmill Walk", "Cardio"}, {"Running", "Cardio"},
                    {"Cycling", "Cardio"}, {"Elliptical", "Cardio"}, {"Jump Rope", "Cardio"}, {"Rowing Machine", "Cardio"}
            };
            for (int i = 0; i < extra.length; i++) {
                library.add(Exercise.createCustom(extra[i][0], extra[i][1], 3, "10", 60));
            }
        }
        return library;
    }

    // Finds which routine has this exercise (-1 if not found)
    public static int findRoutineOf(String exerciseName) {
        for (int i = 0; i < getRoutines().size(); i++) {
            ArrayList<Exercise> list = getRoutines().get(i).getExercises();
            for (int j = 0; j < list.size(); j++) {
                if (list.get(j).getName().equals(exerciseName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    // Finds one exercise by name (null if not found) - only the routine exercises (with tips)
    public static Exercise findExercise(String exerciseName) {
        ArrayList<Exercise> all = getAllExercises();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getName().equalsIgnoreCase(exerciseName)) {
                return all.get(i);
            }
        }
        return null;
    }

    // Chest & Triceps - 6 exercises, 4 sets each
    private static Routine buildChestTriceps() {
        int img = R.drawable.ic_chest_triceps;
        Routine r = new Routine("Chest & Triceps", 45, 320, "Pectorals, Triceps", img);

        r.addExercise(new Exercise("Barbell Bench Press", "Chest", "Triceps, Shoulders",
                4, "8-10", 8, 60, 90,
                new String[]{"Retract shoulder blades, arch upper back",
                        "Grip slightly wider than shoulder width",
                        "Lower bar to mid-chest, press up at slight angle",
                        "Press with control, avoid bouncing off chest"},
                "Add 2.5kg when completing all reps", img));

        r.addExercise(new Exercise("Incline Dumbbell Press", "Upper Chest", "Shoulders, Triceps",
                4, "10-12", 10, 22.5, 75,
                new String[]{"Bench at 30-45 degrees",
                        "Elbows about 45 degrees from the body",
                        "Lower until a deep stretch in the chest",
                        "Press up and slightly together"},
                "Go up one dumbbell size when you get 12 reps", img));

        r.addExercise(new Exercise("Cable Flyes", "Chest", "Front Shoulders",
                4, "12-15", 12, 15, 60,
                new String[]{"Slight bend in the elbows, keep it fixed",
                        "Bring hands together in a hugging motion",
                        "Squeeze the chest for 1 second",
                        "Return slowly, feel the stretch"},
                "Add one plate when 15 reps feel easy", img));

        r.addExercise(new Exercise("Dips", "Chest, Triceps", "Shoulders",
                4, "8-12", 10, 0, 90,
                new String[]{"Lean forward a little for more chest",
                        "Lower until elbows are at 90 degrees",
                        "Keep shoulders down, away from ears",
                        "Push up without locking the elbows hard"},
                "Add 2.5kg with a belt after 12 clean reps", img));

        r.addExercise(new Exercise("Tricep Pushdown", "Triceps", "Forearms",
                4, "10-12", 12, 25, 60,
                new String[]{"Elbows tight to your sides",
                        "Only the forearms move",
                        "Fully straighten the arm at the bottom",
                        "Control the way up"},
                "Add one plate when you hit 12 reps on all sets", img));

        r.addExercise(new Exercise("Overhead Tricep Extension", "Triceps (long head)", "Core",
                4, "10-12", 10, 15, 60,
                new String[]{"Keep elbows pointing forward",
                        "Lower the weight behind the head",
                        "Don't arch the lower back",
                        "Extend fully at the top"},
                "Add 2kg when all sets reach 12 reps", img));
        return r;
    }

    // Back & Biceps - 5 exercises, 3-4 sets
    private static Routine buildBackBiceps() {
        int img = R.drawable.ic_back_biceps;
        Routine r = new Routine("Back & Biceps", 40, 280, "Lats, Biceps", img);

        r.addExercise(new Exercise("Pull-ups", "Lats", "Biceps, Rear Shoulders",
                4, "6-10", 8, 0, 120,
                new String[]{"Grip a bit wider than shoulders",
                        "Pull the chest toward the bar",
                        "Drive the elbows down to your pockets",
                        "Lower all the way with control"},
                "Add reps first, then add weight with a belt", img));

        r.addExercise(new Exercise("Barbell Row", "Middle Back", "Lats, Biceps",
                4, "8-10", 8, 50, 90,
                new String[]{"Hinge forward, back flat",
                        "Pull the bar to the lower ribs",
                        "Squeeze shoulder blades together",
                        "Don't jerk with the lower back"},
                "Add 2.5kg when completing all reps", img));

        r.addExercise(new Exercise("Lat Pulldown", "Lats", "Biceps",
                3, "10-12", 8, 60, 90,
                new String[]{"Chest up, slight lean back",
                        "Pull the bar to the upper chest",
                        "Elbows go down and back",
                        "Let the arms stretch fully at the top"},
                "Add one plate when you get 12 reps", img));

        r.addExercise(new Exercise("Barbell Curl", "Biceps", "Forearms",
                3, "8-12", 10, 25, 60,
                new String[]{"Elbows stay next to the body",
                        "No swinging with the hips",
                        "Squeeze at the top",
                        "Lower slowly (2-3 seconds)"},
                "Add 1-2.5kg when all sets reach 12 reps", img));

        r.addExercise(new Exercise("Face Pull", "Rear Shoulders", "Upper Back, Rotator Cuff",
                3, "12-15", 15, 20, 60,
                new String[]{"Rope at face height",
                        "Pull toward the eyes, hands apart",
                        "Rotate the thumbs back at the end",
                        "Light weight, perfect form"},
                "Add weight only when form stays perfect", img));
        return r;
    }

    // Legs & Abs - 7 exercises, 4-5 sets
    private static Routine buildLegsAbs() {
        int img = R.drawable.ic_legs_abs;
        Routine r = new Routine("Legs & Abs", 55, 450, "Quads, Glutes, Core", img);

        r.addExercise(new Exercise("Barbell Back Squat", "Quads, Glutes", "Hamstrings",
                5, "5", 5, 80, 120,
                new String[]{"Brace core, push knees over toes",
                        "Break at hips and knees simultaneously",
                        "Drive through full foot, maintain upright torso",
                        "Keep chest up, avoid excessive forward lean"},
                "Add 5kg weekly on linear program", img));

        r.addExercise(new Exercise("Walking Lunges", "Quads, Glutes", "Hamstrings, Core",
                4, "10 each leg", 10, 12, 90,
                new String[]{"Long step, torso upright",
                        "Back knee almost touches the floor",
                        "Push through the front heel",
                        "Keep the front knee in line with the toes"},
                "Add 2kg dumbbells when all sets feel easy", img));

        r.addExercise(new Exercise("Conventional Deadlift", "Posterior Chain", "Traps, Core",
                4, "5", 5, 100, 150,
                new String[]{"Bar over mid-foot, shoulder blades over bar",
                        "Maintain neutral spine throughout",
                        "Push floor away, lock hips at top",
                        "Control the descent, don't drop the bar"},
                "Add 2.5kg when form is consistent", img));

        r.addExercise(new Exercise("Leg Press", "Quads", "Glutes",
                4, "10-12", 10, 120, 90,
                new String[]{"Feet shoulder width in the middle of the plate",
                        "Lower until knees are at 90 degrees",
                        "Don't let the lower back lift off the seat",
                        "Don't lock the knees at the top"},
                "Add 10kg when you hit 12 reps", img));

        r.addExercise(new Exercise("Leg Curl", "Hamstrings", "Calves",
                4, "10-12", 12, 35, 60,
                new String[]{"Hips pressed into the pad",
                        "Curl all the way up",
                        "Squeeze for 1 second",
                        "Lower slowly"},
                "Add one plate when you get 12 reps", img));

        r.addExercise(new Exercise("Plank", "Core", "Shoulders, Glutes",
                4, "45 sec", 45, 0, 45,
                new String[]{"Elbows under the shoulders",
                        "Body in one straight line",
                        "Squeeze glutes and abs",
                        "Breathe normally, don't hold your breath"},
                "Add 10 seconds every week", img));

        r.addExercise(new Exercise("Hanging Leg Raise", "Lower Abs", "Hip Flexors, Grip",
                4, "10-15", 12, 0, 60,
                new String[]{"Hang with straight arms, no swinging",
                        "Raise legs by curling the pelvis up",
                        "Pause at the top",
                        "Lower slowly with control"},
                "Go from bent knees to straight legs", img));
        return r;
    }
}
