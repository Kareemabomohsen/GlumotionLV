package com.example.glumotion.devices;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;

import android.os.Bundle;

/**
 * 🔒 Explains why GluMotion asks for the Health Connect permissions.
 * Android 14 opens this page from the permission screen (Manifest: activity-alias with
 * VIEW_PERMISSION_USAGE + HEALTH_PERMISSIONS) - without it Android doesn't let the app ask.
 */
public class HealthPrivacyActivity extends BaseActivity {

    // Shows the explanation text
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_privacy);
        setupBackBar("🔒 Health data");
    }
}
