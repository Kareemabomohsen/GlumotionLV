package com.example.glumotion.common;

import com.example.glumotion.medications.MedicationModel;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * All the Firebase references of the app in ONE place.
 * Medications are always saved in: Users / uid / Medications / medId
 * (the questions screen, the "add medication" screen and the medications list all use these methods)
 */
public class FRef {

    // Firebase Authentication
    public static FirebaseAuth getAuth() {
        return FirebaseAuth.getInstance();
    }

    // Firebase Realtime Database
    public static FirebaseDatabase getDatabase() {
        return FirebaseDatabase.getInstance("https://glumotion-d07da-default-rtdb.firebaseio.com");
    }

    // Users node
    public static DatabaseReference getRefUsers() {
        return getDatabase().getReference("Users");
    }

    // The logged in user (or null)
    public static FirebaseUser getCurrentUser() {
        return getAuth().getCurrentUser();
    }

    // Users / uid of the logged in user (or null if nobody is logged in)
    public static DatabaseReference getRefCurrentUser() {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            return null;
        }
        return getRefUsers().child(user.getUid());
    }

    // The ONE place for all medications: Users / uid / Medications
    public static DatabaseReference getRefMedications() {
        DatabaseReference userRef = getRefCurrentUser();
        if (userRef == null) {
            return null;
        }
        return userRef.child("Medications");
    }

    // Saves one new medication with its own unique key (push) → never overwrites the previous one
    public static Task<Void> saveMedication(MedicationModel med) {
        DatabaseReference medsRef = getRefMedications();
        if (medsRef == null) {
            return null;
        }
        String key = medsRef.push().getKey();
        med.setId(key);
        return medsRef.child(key).setValue(med.toMap());
    }

    // Changes only the status (Taken / Skipped / Pending) of one medication
    public static void updateMedicationStatus(String medId, String status) {
        DatabaseReference medsRef = getRefMedications();
        if (medsRef == null || medId == null) {
            return;
        }
        // Status + the day it was set (tomorrow it starts again as Pending)
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("status", status);
        updates.put("statusDate", DateUtil.todayKey());
        medsRef.child(medId).updateChildren(updates);
    }
}
