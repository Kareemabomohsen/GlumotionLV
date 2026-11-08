package com.example.glumotion.medications;

import com.example.glumotion.R;
import com.example.glumotion.chat.ChatBotActivity;
import com.example.glumotion.common.BaseActivity;

import android.os.Bundle;
import android.view.View;


public class MedicationsActivity extends BaseActivity {

    // Loads MedicationsFragment inside the Activity when the screen opens
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.medications);
        setupBackBar("My Medications");

        // 🤖 at the top: ask GluBot about medications (how to add one, what did I take today...)
        setupToolbarAction("🤖 Ask", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatBotActivity.open(MedicationsActivity.this, ChatBotActivity.MODE_GENERAL);
            }
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.medications_container, new MedicationsFragment())
                    .commit();
        }
    }
}
