package com.example.glumotion.glucose;

import com.example.glumotion.R;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.BaseActivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * "Log Blood Sugar" page (like the design):
 * value + unit (mg/dL or mmol/L), meal context, how do you feel, notes, Done.
 * The value is always SAVED in mg/dL (mmol/L × 18).
 */
public class LogGlucoseActivity extends BaseActivity {

    public static final String EXTRA_DATE_MILLIS = "date_millis";

    private EditText etValue, etNote;
    private TextView tvUnitMg, tvUnitMmol, tvLiveStatus, btnDone;
    private ChipGroup chipGroupFeelings;

    // The context buttons (array of views) and the selected one
    private final ArrayList<TextView> contextButtons = new ArrayList<>();
    private String selectedContext = "Fasting";
    private String unit = AppPrefs.UNIT_MG;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_glucose);
        setupBackBar("Log Blood Sugar");
        isSubPage = true;
        setupBottomNav(R.id.nav_dm);

        etValue = findViewById(R.id.etGlucoseBig);
        etNote = findViewById(R.id.etGlucoseNote);
        tvUnitMg = findViewById(R.id.tvUnitMg);
        tvUnitMmol = findViewById(R.id.tvUnitMmol);
        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        btnDone = findViewById(R.id.btnGlucoseDone);
        chipGroupFeelings = findViewById(R.id.chipGroupFeelings);

        buildContextGrid();
        buildFeelingChips();
        setupUnitToggle();
        setupValueWatcher();
        selectContext(guessContext());

        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
    }

    // Creates the meal context buttons in 2 columns (like a grid)
    private void buildContextGrid() {
        LinearLayout grid = findViewById(R.id.llContextGrid);
        LinearLayout row = null;
        int margin = dp(4);

        for (int i = 0; i < GlucoseReading.CONTEXTS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            final String context = GlucoseReading.CONTEXTS[i];
            TextView button = new TextView(this);
            button.setText(context);
            button.setTextSize(13);
            button.setPadding(dp(14), dp(10), dp(14), dp(10));
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectContext(context);
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(margin, margin, margin, margin);
            row.addView(button, params);
            contextButtons.add(button);
        }
        // Odd number → empty space so the last button stays half width
        if (GlucoseReading.CONTEXTS.length % 2 == 1) {
            View space = new View(this);
            row.addView(space, new LinearLayout.LayoutParams(0, 1, 1));
        }
    }

    // Colors the selected context green
    private void selectContext(String context) {
        selectedContext = context;
        for (int i = 0; i < contextButtons.size(); i++) {
            TextView b = contextButtons.get(i);
            if (b.getText().toString().equals(context)) {
                b.setBackgroundResource(R.drawable.bg_option_selected);
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setBackgroundResource(R.drawable.bg_option_unselected);
                b.setTextColor(0xFF1D2623);
            }
        }
    }

    // Guess by the hour (the user can change it)
    private String guessContext() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 9) {
            return "Fasting";
        } else if (hour < 12) {
            return "After Breakfast";
        } else if (hour < 16) {
            return "After Lunch";
        } else if (hour < 21) {
            return "After Dinner";
        }
        return "Bedtime";
    }

    // Creates the "How do you feel?" chips (more than one can be chosen)
    private void buildFeelingChips() {
        for (int i = 0; i < GlucoseReading.FEELINGS.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(GlucoseReading.FEELINGS[i]);
            chip.setCheckable(true);
            chipGroupFeelings.addView(chip);
        }
    }

    // mg/dL ↔ mmol/L buttons
    private void setupUnitToggle() {
        tvUnitMg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setUnit(AppPrefs.UNIT_MG);
            }
        });
        tvUnitMmol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setUnit(AppPrefs.UNIT_MMOL);
            }
        });
        setUnit(AppPrefs.getGlucoseUnit(this));
    }

    // Changes the unit (and converts the typed number)
    private void setUnit(String newUnit) {
        double typed = parse(etValue.getText().toString());
        if (typed > 0 && !newUnit.equals(unit)) {
            if (newUnit.equals(AppPrefs.UNIT_MMOL)) {
                etValue.setText(String.valueOf(Math.round(typed / 18.0 * 10) / 10.0));
            } else {
                etValue.setText(String.valueOf(Math.round(typed * 18)));
            }
        }
        unit = newUnit;
        boolean mg = unit.equals(AppPrefs.UNIT_MG);
        if (mg) {
            tvUnitMg.setBackgroundResource(R.drawable.bg_option_selected);
            tvUnitMg.setTextColor(0xFFFFFFFF);
            tvUnitMmol.setBackgroundResource(0);
            tvUnitMmol.setTextColor(0xFF6B7671);
        } else {
            tvUnitMmol.setBackgroundResource(R.drawable.bg_option_selected);
            tvUnitMmol.setTextColor(0xFFFFFFFF);
            tvUnitMg.setBackgroundResource(0);
            tvUnitMg.setTextColor(0xFF6B7671);
        }
        updateLiveStatus();
    }

    // The Done button works only when there is a number (TextWatcher)
    private void setupValueWatcher() {
        etValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLiveStatus();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // Shows "Normal / Low / High" while typing
    private void updateLiveStatus() {
        int mg = getValueInMg();
        btnDone.setEnabled(mg > 0);
        if (mg <= 0) {
            tvLiveStatus.setText("");
            return;
        }
        tvLiveStatus.setText(mg + " mg/dL • " + GlucoseReading.getStatus(mg));
        tvLiveStatus.setTextColor(GlucoseReading.getStatusColor(mg));
    }

    // The typed value converted to mg/dL (0 if empty)
    private int getValueInMg() {
        double typed = parse(etValue.getText().toString());
        if (typed <= 0) {
            return 0;
        }
        if (unit.equals(AppPrefs.UNIT_MMOL)) {
            return (int) Math.round(typed * 18);
        }
        return (int) Math.round(typed);
    }

    // Checks and saves the reading in Users / uid / GlucoseReadings
    private void save() {
        final int mg = getValueInMg();
        if (mg < 20 || mg > 600) {
            toast("Value must be 20-600 mg/dL (1.1-33.3 mmol/L)");
            return;
        }

        // Selected chips → "Tired, Thirsty"
        String feelings = "";
        for (int i = 0; i < chipGroupFeelings.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupFeelings.getChildAt(i);
            if (chip.isChecked()) {
                if (!feelings.isEmpty()) {
                    feelings = feelings + ", ";
                }
                feelings = feelings + chip.getText().toString();
            }
        }

        GlucoseReading reading = new GlucoseReading(mg, selectedContext);
        reading.setFeelings(feelings);
        reading.setNote(etNote.getText().toString().trim());

        // If the user came from an older day in the DM screen → save it on that day
        long dayMillis = getIntent().getLongExtra(EXTRA_DATE_MILLIS, 0);
        if (dayMillis > 0) {
            Calendar now = Calendar.getInstance();
            Calendar time = Calendar.getInstance();
            time.setTimeInMillis(dayMillis);
            time.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
            time.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
            reading.setTimestamp(time.getTimeInMillis());
        }

        if (!GlucoseReading.save(reading)) {
            toast("Not logged in!");
            return;
        }
        AppPrefs.setGlucoseUnit(this, unit);

        // 🚨 للتجربة بدون حساس: قراءة يدوية خطيرة (أقل من 55 أو أكثر من 300) بتبعث تنبيه الطوارئ والمدرب
        // لتفعيل: شيل الـ // (شوف الخطوات بـ EmergencyHelper.java)
        // EmergencyHelper.checkAndAlert(this, mg);

        // Low / high → safety message before closing
        if (mg < GlucoseReading.LOW_LIMIT) {
            showWarningAndClose("Low blood sugar ⚠",
                    "Eat or drink 15g of fast carbs (juice, glucose tablets), wait 15 minutes and measure again. If it doesn't go up - call for help.");
        } else if (mg > 250) {
            showWarningAndClose("High blood sugar ⚠",
                    "Drink water and follow your doctor's plan. If you have type 1 diabetes, check ketones.");
        } else {
            toast("Saved: " + mg + " mg/dL (" + GlucoseReading.getStatus(mg) + ")");
            finish();
        }
    }

    // Warning dialog, then back to the DM screen
    private void showWarningAndClose(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    // dp → pixels
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // Text → number
    private double parse(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
