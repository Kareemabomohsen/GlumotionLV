package com.example.glumotion.glucose;

import com.example.glumotion.R;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/**
 * Glucose Trends page: statistics of the last 30 days, daily pattern chart,
 * time in range by week, insights from the user's own data and estimated HbA1c.
 */
public class GlucoseTrendsActivity extends BaseActivity {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private final ArrayList<GlucoseReading> readings = new ArrayList<>();
    // day key ("2026-11-02") → ml of water / did a workout
    private final HashMap<String, Integer> waterByDay = new HashMap<>();
    private final ArrayList<String> workoutDays = new ArrayList<>();

    // Builds the screen and reads the data once
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glucose_trends);
        setupBackBar("Glucose Trends");
        isSubPage = true;
        setupBottomNav(R.id.nav_dm);
        loadData();
    }

    // Reads readings + water + workouts of the user in one request
    private void loadData() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot snap : snapshot.child("GlucoseReadings").getChildren()) {
                    GlucoseReading r = snap.getValue(GlucoseReading.class);
                    if (r != null && r.getValue() > 0) {
                        readings.add(r);
                    }
                }
                for (DataSnapshot snap : snapshot.child("DailyHealth").getChildren()) {
                    Long water = snap.child("waterMl").getValue(Long.class);
                    if (water != null) {
                        waterByDay.put(snap.getKey(), water.intValue());
                    }
                }
                for (DataSnapshot snap : snapshot.child("Workouts").getChildren()) {
                    String date = snap.child("date").getValue(String.class);
                    if (date != null && !workoutDays.contains(date)) {
                        workoutDays.add(date);
                    }
                }
                showEverything();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toast("Load failed");
            }
        });
    }

    // Readings between "fromDaysAgo" and "toDaysAgo" (e.g. 30 → 0 = last 30 days)
    private ArrayList<GlucoseReading> between(int fromDaysAgo, int toDaysAgo) {
        long now = System.currentTimeMillis();
        long from = now - fromDaysAgo * DAY;
        long to = now - toDaysAgo * DAY;
        ArrayList<GlucoseReading> list = new ArrayList<>();
        for (int i = 0; i < readings.size(); i++) {
            long t = readings.get(i).getTimestamp();
            if (t >= from && t <= to) {
                list.add(readings.get(i));
            }
        }
        return list;
    }

    // Average of a list (0 if empty)
    private int average(ArrayList<GlucoseReading> list) {
        if (list.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum = sum + list.get(i).getValue();
        }
        return sum / list.size();
    }

    // % of the list inside 70-140 (-1 if empty)
    private int timeInRange(ArrayList<GlucoseReading> list) {
        if (list.isEmpty()) {
            return -1;
        }
        int in = 0;
        for (int i = 0; i < list.size(); i++) {
            if (GlucoseReading.isInRange(list.get(i).getValue())) {
                in++;
            }
        }
        return in * 100 / list.size();
    }

    // Fills all the parts of the page
    private void showEverything() {
        ArrayList<GlucoseReading> last30 = between(30, 0);
        ArrayList<GlucoseReading> prev30 = between(60, 30);
        showStats(last30, prev30);
        showPattern();
        showWeeks();
        showInsights(last30);
        showHba1c(last30, prev30);
    }

    // The 4 cards at the top
    private void showStats(ArrayList<GlucoseReading> last30, ArrayList<GlucoseReading> prev30) {
        TextView tvAvg = findViewById(R.id.tvTrendAvg);
        TextView tvAvgSub = findViewById(R.id.tvTrendAvgSub);
        TextView tvTir = findViewById(R.id.tvTrendTir);
        TextView tvTirSub = findViewById(R.id.tvTrendTirSub);
        TextView tvHigh = findViewById(R.id.tvTrendHigh);
        TextView tvHighSub = findViewById(R.id.tvTrendHighSub);
        TextView tvLow = findViewById(R.id.tvTrendLow);
        TextView tvLowSub = findViewById(R.id.tvTrendLowSub);
        String unit = " " + AppPrefs.getGlucoseUnit(this);

        if (last30.isEmpty()) {
            tvAvgSub.setText("No readings yet");
            return;
        }
        int avg = average(last30);
        tvAvg.setText(AppPrefs.formatGlucose(this, avg) + unit);
        if (!prev30.isEmpty()) {
            int diff = avg - average(prev30);
            tvAvgSub.setText(arrow(diff) + " " + signed(diff) + " vs last month");
        } else {
            tvAvgSub.setText(last30.size() + " readings");
        }

        int tir = timeInRange(last30);
        tvTir.setText(tir + "%");
        if (!prev30.isEmpty()) {
            int diff = tir - timeInRange(prev30);
            tvTirSub.setText(arrow(diff) + " " + signed(diff) + "% vs last month");
        } else {
            tvTirSub.setText("70-140 mg/dL");
        }

        GlucoseReading high = last30.get(0);
        GlucoseReading low = last30.get(0);
        for (int i = 1; i < last30.size(); i++) {
            if (last30.get(i).getValue() > high.getValue()) {
                high = last30.get(i);
            }
            if (last30.get(i).getValue() < low.getValue()) {
                low = last30.get(i);
            }
        }
        tvHigh.setText(AppPrefs.formatGlucose(this, high.getValue()) + unit);
        tvHighSub.setText("🕓 " + DateUtil.dayName(high.getCalendar()) + ", " + DateUtil.shortDate(high.getCalendar()));
        tvLow.setText(AppPrefs.formatGlucose(this, low.getValue()) + unit);
        tvLowSub.setText("🕓 " + DateUtil.dayName(low.getCalendar()) + ", " + DateUtil.shortDate(low.getCalendar()));
    }

    // ↗ / ↘ / →
    private String arrow(int diff) {
        if (diff > 0) {
            return "↗";
        }
        if (diff < 0) {
            return "↘";
        }
        return "→";
    }

    // 5 → "+5", -3 → "-3"
    private String signed(int value) {
        if (value > 0) {
            return "+" + value;
        }
        return String.valueOf(value);
    }

    // Time of day → index of the chart (6am = 0, 8am = 1 ... 10pm = 8), -1 = outside
    private int timeSlot(GlucoseReading r) {
        Calendar c = r.getCalendar();
        double hour = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0;
        int slot = (int) Math.round((hour - 6) / 2.0);
        if (slot < 0 || slot > 8) {
            return -1;
        }
        return slot;
    }

    private static final String[] SLOT_LABELS = {"6am", "8am", "10am", "12pm", "2pm", "4pm", "6pm", "8pm", "10pm"};

    // Daily pattern of the last 7 days (average in every 2-hour slot)
    private void showPattern() {
        ArrayList<GlucoseReading> week = between(7, 0);
        int[] sum = new int[9];
        int[] count = new int[9];
        for (int i = 0; i < week.size(); i++) {
            int slot = timeSlot(week.get(i));
            if (slot >= 0) {
                sum[slot] = sum[slot] + week.get(i).getValue();
                count[slot]++;
            }
        }
        float[] values = new float[9];
        for (int i = 0; i < 9; i++) {
            if (count[i] == 0) {
                values[i] = Float.NaN;
            } else {
                values[i] = (float) sum[i] / count[i];
            }
        }
        GlucosePatternView chart = findViewById(R.id.chartPattern);
        chart.setData(SLOT_LABELS, values);
    }

    // Time in range of the last 4 weeks
    private void showWeeks() {
        int[] percents = new int[4];
        for (int w = 0; w < 4; w++) {
            // week 1 = 28-21 days ago ... week 4 = last 7 days
            percents[w] = timeInRange(between(28 - w * 7, 21 - w * 7));
        }
        WeeklyBarView chart = findViewById(R.id.chartWeeks);
        chart.setData(new String[]{"Wk 1", "Wk 2", "Wk 3", "Wk 4"}, percents);
    }

    // Average glucose of every day: day key → avg
    private HashMap<String, Integer> dailyAverages(ArrayList<GlucoseReading> list) {
        HashMap<String, Integer> sums = new HashMap<>();
        HashMap<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            String day = DateUtil.dateKey(list.get(i).getCalendar());
            int oldSum = 0;
            int oldCount = 0;
            if (sums.containsKey(day)) {
                oldSum = sums.get(day);
                oldCount = counts.get(day);
            }
            sums.put(day, oldSum + list.get(i).getValue());
            counts.put(day, oldCount + 1);
        }
        HashMap<String, Integer> result = new HashMap<>();
        for (String day : sums.keySet()) {
            result.put(day, sums.get(day) / counts.get(day));
        }
        return result;
    }

    /**
     * Insights from the user's own data (only when there is enough data):
     * 1. after-meal spikes  2. fasting trend  3. water vs glucose  4. workout days vs rest days
     */
    private void showInsights(ArrayList<GlucoseReading> last30) {
        LinearLayout container = findViewById(R.id.llInsights);
        container.removeAllViews();
        int shown = 0;

        // 1) After-meal spikes: time slot with the highest after-meal average
        int[] sum = new int[9];
        int[] count = new int[9];
        for (int i = 0; i < last30.size(); i++) {
            GlucoseReading r = last30.get(i);
            int slot = timeSlot(r);
            if (r.getContext() != null && r.getContext().startsWith("After") && slot >= 0) {
                sum[slot] = sum[slot] + r.getValue();
                count[slot]++;
            }
        }
        int bestSlot = -1;
        int bestAvg = 0;
        for (int i = 0; i < 9; i++) {
            if (count[i] >= 2 && sum[i] / count[i] > bestAvg) {
                bestAvg = sum[i] / count[i];
                bestSlot = i;
            }
        }
        if (bestSlot >= 0 && bestAvg > GlucoseReading.TARGET_HIGH) {
            addInsight(container, "📈", "Post-meal spikes", "Your glucose after meals is highest around "
                    + SLOT_LABELS[bestSlot] + " (" + bestAvg + " mg/dL). Try a lighter meal or a 10-minute walk after eating.", R.drawable.bg_insight_orange);
            shown++;
        }

        // 2) Fasting: last 15 days vs the 15 days before
        ArrayList<GlucoseReading> fastNew = new ArrayList<>();
        ArrayList<GlucoseReading> fastOld = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < last30.size(); i++) {
            GlucoseReading r = last30.get(i);
            if ("Fasting".equals(r.getContext())) {
                if (r.getTimestamp() >= now - 15 * DAY) {
                    fastNew.add(r);
                } else {
                    fastOld.add(r);
                }
            }
        }
        if (fastNew.size() >= 2 && fastOld.size() >= 2) {
            int diff = average(fastOld) - average(fastNew);
            if (diff > 0) {
                addInsight(container, "✅", "Morning fasting improving", "Your fasting glucose improved by " + diff
                        + " mg/dL in the last 2 weeks.", R.drawable.bg_insight_green);
            } else if (diff < 0) {
                addInsight(container, "⚠️", "Morning fasting is higher", "Your fasting glucose went up by " + (-diff)
                        + " mg/dL in the last 2 weeks. Talk to your doctor if it continues.", R.drawable.bg_insight_orange);
            } else {
                addInsight(container, "✅", "Fasting glucose is stable", "No change in the last 2 weeks.", R.drawable.bg_insight_green);
            }
            shown++;
        }

        HashMap<String, Integer> daily = dailyAverages(last30);

        // 3) Water: days with 2L+ vs days with less
        int wetSum = 0, wetDays = 0, drySum = 0, dryDays = 0;
        for (String day : daily.keySet()) {
            if (waterByDay.containsKey(day)) {
                if (waterByDay.get(day) >= 2000) {
                    wetSum = wetSum + daily.get(day);
                    wetDays++;
                } else {
                    drySum = drySum + daily.get(day);
                    dryDays++;
                }
            }
        }
        if (wetDays >= 2 && dryDays >= 2) {
            int wetAvg = wetSum / wetDays;
            int dryAvg = drySum / dryDays;
            int percent = (dryAvg - wetAvg) * 100 / dryAvg;
            String better = "better";
            if (percent < 0) {
                better = "worse";
                percent = -percent;
            }
            addInsight(container, "💧", "Hydration impact", "Days with 2L+ water show " + percent + "% " + better
                    + " glucose (" + wetAvg + " vs " + dryAvg + " mg/dL).", R.drawable.bg_insight_blue);
            shown++;
        }

        // 4) Workout days vs rest days
        int trainSum = 0, trainDays = 0, restSum = 0, restDays = 0;
        for (String day : daily.keySet()) {
            if (workoutDays.contains(day)) {
                trainSum = trainSum + daily.get(day);
                trainDays++;
            } else {
                restSum = restSum + daily.get(day);
                restDays++;
            }
        }
        if (trainDays >= 2 && restDays >= 2) {
            int diff = restSum / restDays - trainSum / trainDays;
            String text;
            if (diff > 0) {
                text = "On workout days your glucose averages " + diff + " mg/dL lower than rest days.";
            } else {
                text = "On workout days your glucose is not lower yet (" + (-diff) + " mg/dL higher). Check it before and after training.";
            }
            addInsight(container, "🏃", "Exercise benefit", text, R.drawable.bg_insight_green);
            shown++;
        }

        if (shown == 0) {
            addInsight(container, "📊", "Keep logging", "Insights appear after about 2 weeks of readings "
                    + "(fasting, after meals, water and workouts).", R.drawable.bg_insight_blue);
        }
    }

    // Adds one insight row
    private void addInsight(LinearLayout container, String icon, String title, String text, int background) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_insight, container, false);
        row.setBackgroundResource(background);
        ((TextView) row.findViewById(R.id.tvInsightIcon)).setText(icon);
        ((TextView) row.findViewById(R.id.tvInsightTitle)).setText(title);
        ((TextView) row.findViewById(R.id.tvInsightText)).setText(text);
        container.addView(row);
    }

    // Estimated HbA1c = (average + 46.7) / 28.7   (ADAG study formula)
    private void showHba1c(ArrayList<GlucoseReading> last30, ArrayList<GlucoseReading> prev30) {
        if (last30.size() < 5) {
            return;
        }
        int avg = average(last30);
        double a1c = Math.round((avg + 46.7) / 28.7 * 10) / 10.0;
        TextView tvHba1c = findViewById(R.id.tvHba1c);
        TextView tvChange = findViewById(R.id.tvHba1cChange);
        TextView tvBasis = findViewById(R.id.tvHba1cBasis);
        tvHba1c.setText(a1c + "%");
        tvBasis.setText("Based on your 30-day average glucose of " + avg + " mg/dL");
        if (prev30.size() >= 5) {
            double prev = Math.round((average(prev30) + 46.7) / 28.7 * 10) / 10.0;
            if (prev > a1c) {
                tvChange.setText("↓ from " + prev + "%");
            } else if (prev < a1c) {
                tvChange.setText("↑ from " + prev + "%");
            }
        }
    }
}
