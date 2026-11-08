package com.example.glumotion.medications;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;

import android.os.Bundle;


/**
 * Screen that only holds AddMedicationFragment.
 * (Before, this Activity had its own copy of the save code with different fields - now there is ONE save code)
 */
public class AddMedicationActivity extends BaseActivity {

    // Shows the AddMedicationFragment inside the container
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.medications);
        setupBackBar("Add Medication");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.medications_container, new AddMedicationFragment())
                    .commit();
        }
    }
}
