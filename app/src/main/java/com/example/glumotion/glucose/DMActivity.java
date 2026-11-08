package com.example.glumotion.glucose;

import com.example.glumotion.R;
import com.example.glumotion.chat.ChatBotActivity;
import com.example.glumotion.common.AppPrefs;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.DateUtil;
import com.example.glumotion.common.FRef;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Blood Sugar screen (DM = Diabetes Mellitus).
 * Log readings, move between days, see stats, a trend chart and the day's log.
 * Data: Users / uid / GlucoseReadings / id  →  {value, context, timestamp, date}
 */
public class DMActivity extends BaseActivity {

    // The chat button of this page opens the sugar bot
    @Override
    protected String getChatMode() {
        return ChatBotActivity.MODE_SUGAR;
    }

    // Views
    private TextView tvDate, tvCurrentValue, tvCurrentTime, tvCurrentStatus, tvTrendLast;
    private TextView tvAvgVal, tvInRangeVal, tvInRangeFraction, tvReadingsCount, tvReadingsSub, tvNoReadings;
    private TextView filterToday, filter7Days, filter14Days, filter30Days;
    private View viewGaugeSpacer;
    private LinearLayout llLogContainer;
    private GlucoseTrendChartView chart;

    // All readings of the user (kept in memory, updated live from Firebase)
    private final ArrayList<GlucoseReading> allReadings = new ArrayList<>();

    // The day shown on the screen and the chart range (1 = today, 7, 14, 30 days)
    private final Calendar selectedDate = Calendar.getInstance();
    private int chartDays = 7;

    private DatabaseReference readingsRef;
    private ValueEventListener readingsListener;

    // Builds the screen
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dmactivity);

        setupBottomNav(R.id.nav_dm);
        setupTopBar();
        initViews();
        setupButtons();
        selectFilter(7);
        refreshScreen();
        listenToReadings();

        // ================================================================
        // 🔗 ربط مع تطبيق عالمي (Nightscout ← Dexcom / FreeStyle Libre)
        // لتفعيل: 1) اكتب رابط موقعك بـ NightscoutHelper.NIGHTSCOUT_URL
        //          2) شيل الـ // من السطر اللي تحت
        // القراءات من الحساس (CGM) بتنضاف لحالها لسجل السكر كل ما تفتح الصفحة
        // ================================================================
        // NightscoutHelper.importLatest(this);

        // ================================================================
        // 🚨 تنبيه الطوارئ بالـ SMS (لما الحساس يشوف سكر خطير) - بدها إذن SEND_SMS
        // لتفعيل: شيل الـ // من السطر اللي تحت + شيل الـ comment عن SEND_SMS بـ AndroidManifest.xml
        // (كل الخطوات مكتوبة فوق بملف EmergencyHelper.java)
        // ================================================================
        // ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 5);
    }

    // The unit chosen in Settings (mg/dL or mmol/L) - shown again when coming back
    @Override
    protected void onResume() {
        super.onResume();
        TextView tvUnit = findViewById(R.id.tvUnit);
        tvUnit.setText(AppPrefs.getGlucoseUnit(this));
        refreshScreen();
    }

    // Stops the Firebase listener when leaving the screen
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (readingsRef != null && readingsListener != null) {
            readingsRef.removeEventListener(readingsListener);
        }
    }

    // Finds all the views
    private void initViews() {
        tvDate = findViewById(R.id.tvDate);
        tvCurrentValue = findViewById(R.id.tvCurrentValue);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus);
        tvTrendLast = findViewById(R.id.tvTrendLast);
        tvAvgVal = findViewById(R.id.tvAvgVal);
        tvInRangeVal = findViewById(R.id.tvInRangeVal);
        tvInRangeFraction = findViewById(R.id.tvInRangeFraction);
        tvReadingsCount = findViewById(R.id.tvReadingsCount);
        tvReadingsSub = findViewById(R.id.tvReadingsSub);
        tvNoReadings = findViewById(R.id.tvNoReadings);
        filterToday = findViewById(R.id.filterToday);
        filter7Days = findViewById(R.id.filter7Days);
        filter14Days = findViewById(R.id.filter14Days);
        filter30Days = findViewById(R.id.filter30Days);
        viewGaugeSpacer = findViewById(R.id.viewGaugeSpacer);
        llLogContainer = findViewById(R.id.llLogContainer);
        chart = findViewById(R.id.chartGlucoseTrend);
    }

    // Connects every button of the screen
    private void setupButtons() {
        // The 3 "add" buttons open the Log Blood Sugar page
        View.OnClickListener addListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DMActivity.this, LogGlucoseActivity.class);
                intent.putExtra(LogGlucoseActivity.EXTRA_DATE_MILLIS, selectedDate.getTimeInMillis());
                startActivity(intent);
            }
        };
        findViewById(R.id.btnAddHeader).setOnClickListener(addListener);
        findViewById(R.id.btnAddLog).setOnClickListener(addListener);
        findViewById(R.id.btnLogReadingBig).setOnClickListener(addListener);

        findViewById(R.id.btnPrevDate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeDay(-1);
            }
        });
        findViewById(R.id.btnNextDate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeDay(1);
            }
        });

        // "Trends →" opens the Glucose Trends page
        findViewById(R.id.tvTrendsLink).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DMActivity.this, GlucoseTrendsActivity.class));
            }
        });

        addFilterListener(filterToday, 1);
        addFilterListener(filter7Days, 7);
        addFilterListener(filter14Days, 14);
        addFilterListener(filter30Days, 30);
    }

    // Helper: one filter pill = one number of days
    private void addFilterListener(TextView pill, final int days) {
        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectFilter(days);
                refreshScreen();
            }
        });
    }

    // Colors the selected filter pill
    private void selectFilter(int days) {
        chartDays = days;
        setPill(filterToday, days == 1);
        setPill(filter7Days, days == 7);
        setPill(filter14Days, days == 14);
        setPill(filter30Days, days == 30);
    }

    // Helper: active pill (colored, white text) or grey pill
    private void setPill(TextView pill, boolean active) {
        if (active) {
            pill.setBackgroundResource(R.drawable.bg_pill_filter_active);
            pill.setTextColor(0xFFFFFFFF);
        } else {
            pill.setBackgroundResource(R.drawable.bg_pill_grey);
            pill.setTextColor(getResources().getColor(R.color.text_grey, getTheme()));
        }
    }

    // Moves one day back / forward (can't go to the future)
    private void changeDay(int amount) {
        Calendar test = (Calendar) selectedDate.clone();
        test.add(Calendar.DAY_OF_YEAR, amount);
        if (test.after(Calendar.getInstance())) {
            Toast.makeText(this, "This is today", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedDate.add(Calendar.DAY_OF_YEAR, amount);
        refreshScreen();
    }

    // Listens to Users/uid/GlucoseReadings - every change refreshes the screen
    private void listenToReadings() {
        DatabaseReference userRef = FRef.getRefCurrentUser();
        if (userRef == null) {
            return;
        }
        readingsRef = userRef.child("GlucoseReadings");
        readingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allReadings.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    GlucoseReading reading = snap.getValue(GlucoseReading.class);
                    if (reading != null) {
                        reading.setId(snap.getKey());
                        allReadings.add(reading);
                    }
                }
                sortByTime(allReadings);
                refreshScreen();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DMActivity.this, "Load failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        readingsRef.addValueEventListener(readingsListener);
    }

    // Sorts the readings from old to new (simple bubble sort)
    private void sortByTime(ArrayList<GlucoseReading> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                if (list.get(j).getTimestamp() > list.get(j + 1).getTimestamp()) {
                    GlucoseReading temp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, temp);
                }
            }
        }
    }

    // Readings of one day only
    private ArrayList<GlucoseReading> getReadingsOfDay(Calendar day) {
        ArrayList<GlucoseReading> result = new ArrayList<>();
        for (int i = 0; i < allReadings.size(); i++) {
            if (DateUtil.sameDay(allReadings.get(i).getCalendar(), day)) {
                result.add(allReadings.get(i));
            }
        }
        return result;
    }

    // Draws everything again for the selected day
    private void refreshScreen() {
        Calendar today = Calendar.getInstance();
        if (DateUtil.sameDay(selectedDate, today)) {
            tvDate.setText("Today, " + DateUtil.shortDate(selectedDate));
        } else {
            tvDate.setText(DateUtil.dayName(selectedDate) + ", " + DateUtil.shortDate(selectedDate));
        }

        ArrayList<GlucoseReading> dayReadings = getReadingsOfDay(selectedDate);
        showCurrentReading(dayReadings);
        showStats(dayReadings);
        showLog(dayReadings);
        showChart();
    }

    // Big number at the top = the last reading of the day
    private void showCurrentReading(ArrayList<GlucoseReading> dayReadings) {
        if (dayReadings.isEmpty()) {
            tvCurrentValue.setText("--");
            tvCurrentValue.setTextColor(0xFF9CA3AF);
            tvCurrentTime.setText("No readings yet");
            tvCurrentStatus.setVisibility(View.INVISIBLE);
            tvTrendLast.setVisibility(View.INVISIBLE);
            setGaugePosition(-1);
            return;
        }

        GlucoseReading last = dayReadings.get(dayReadings.size() - 1);
        int value = last.getValue();
        int color = GlucoseReading.getStatusColor(value);

        tvCurrentValue.setText(AppPrefs.formatGlucose(this, value));
        tvCurrentValue.setTextColor(color);
        tvCurrentTime.setText(last.getTimeText() + " • " + last.getContext());
        tvCurrentStatus.setVisibility(View.VISIBLE);
        tvCurrentStatus.setText(GlucoseReading.getStatus(value));
        tvCurrentStatus.setTextColor(color);
        setGaugePosition(value);

        // Difference from the reading before it (any day)
        int index = allReadings.indexOf(last);
        if (index > 0) {
            int diff = value - allReadings.get(index - 1).getValue();
            String sign = "";
            if (diff > 0) {
                sign = "+";
            }
            tvTrendLast.setVisibility(View.VISIBLE);
            tvTrendLast.setText(sign + diff + " from last");
        } else {
            tvTrendLast.setVisibility(View.INVISIBLE);
        }
    }

    // Moves the pin on the range bar (70 = 25%, 140 = 75% like the drawing)
    private void setGaugePosition(int value) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) viewGaugeSpacer.getLayoutParams();
        float percent = 50;
        if (value > 0) {
            percent = 25 + (value - 70) * 50f / 70f;
        }
        if (percent < 0) {
            percent = 0;
        }
        if (percent > 98) {
            percent = 98;
        }
        params.weight = percent;
        viewGaugeSpacer.setLayoutParams(params);
    }

    // The 3 small cards: average, % in range, number of readings
    private void showStats(ArrayList<GlucoseReading> dayReadings) {
        int count = dayReadings.size();
        tvReadingsCount.setText(String.valueOf(count));
        if (DateUtil.sameDay(selectedDate, Calendar.getInstance())) {
            tvReadingsSub.setText("today");
        } else {
            tvReadingsSub.setText(DateUtil.shortDate(selectedDate));
        }

        if (count == 0) {
            tvAvgVal.setText("--");
            tvInRangeVal.setText("--");
            tvInRangeFraction.setText("0/0");
            return;
        }

        int sum = 0;
        int inRange = 0;
        for (int i = 0; i < count; i++) {
            int value = dayReadings.get(i).getValue();
            sum = sum + value;
            if (GlucoseReading.isInRange(value)) {
                inRange++;
            }
        }
        tvAvgVal.setText(AppPrefs.formatGlucose(this, sum / count));
        tvInRangeVal.setText((inRange * 100 / count) + "%");
        tvInRangeFraction.setText(inRange + "/" + count);
    }

    // List of the day's readings (long press = delete)
    private void showLog(ArrayList<GlucoseReading> dayReadings) {
        llLogContainer.removeAllViews();
        if (dayReadings.isEmpty()) {
            tvNoReadings.setVisibility(View.VISIBLE);
        } else {
            tvNoReadings.setVisibility(View.GONE);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        // Newest first
        for (int i = dayReadings.size() - 1; i >= 0; i--) {
            final GlucoseReading reading = dayReadings.get(i);
            View row = inflater.inflate(R.layout.item_glucose_log, llLogContainer, false);
            TextView tvValue = row.findViewById(R.id.tvLogValue);
            TextView tvContext = row.findViewById(R.id.tvLogContext);
            TextView tvTime = row.findViewById(R.id.tvLogTime);
            TextView tvStatus = row.findViewById(R.id.tvLogStatus);

            int value = reading.getValue();
            int color = GlucoseReading.getStatusColor(value);
            int badge = getBadgeBackground(value);
            tvValue.setText(AppPrefs.formatGlucose(this, value));
            tvValue.setTextColor(color);
            tvValue.setBackgroundResource(badge);
            tvContext.setText(reading.getContext());
            String time = reading.getTimeText();
            if (reading.getFeelings() != null && !reading.getFeelings().isEmpty()) {
                time = time + " • " + reading.getFeelings();
            }
            tvTime.setText(time);
            tvStatus.setText(GlucoseReading.getStatus(value));
            tvStatus.setTextColor(color);
            tvStatus.setBackgroundResource(badge);

            // Click = show the note
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showReadingDetails(reading);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    confirmDelete(reading);
                    return true;
                }
            });
            llLogContainer.addView(row);
        }
    }

    /**
     * Builds the chart data:
     * "Today" = every reading of the day as a point.
     * 7/14/30 days = the days are grouped into max 7 groups → avg / min / max of each group.
     */
    private void showChart() {
        if (chartDays == 1) {
            ArrayList<GlucoseReading> day = getReadingsOfDay(selectedDate);
            int n = day.size();
            String[] labels = new String[n];
            float[] values = new float[n];
            for (int i = 0; i < n; i++) {
                Calendar cal = day.get(i).getCalendar();
                labels[i] = cal.get(Calendar.HOUR_OF_DAY) + ":" + DateUtil.twoDigits(cal.get(Calendar.MINUTE));
                values[i] = day.get(i).getValue();
            }
            chart.setData(labels, values, values, values);
            return;
        }

        int groupSize = (chartDays + 6) / 7; // 7 → 1 day, 14 → 2 days, 30 → 5 days
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Float> avgList = new ArrayList<>();
        ArrayList<Float> minList = new ArrayList<>();
        ArrayList<Float> maxList = new ArrayList<>();

        Calendar start = (Calendar) selectedDate.clone();
        start.add(Calendar.DAY_OF_YEAR, -(chartDays - 1));

        for (int g = 0; g * groupSize < chartDays; g++) {
            Calendar groupStart = (Calendar) start.clone();
            groupStart.add(Calendar.DAY_OF_YEAR, g * groupSize);

            int sum = 0;
            int count = 0;
            int min = 9999;
            int max = 0;
            for (int d = 0; d < groupSize && g * groupSize + d < chartDays; d++) {
                Calendar day = (Calendar) groupStart.clone();
                day.add(Calendar.DAY_OF_YEAR, d);
                ArrayList<GlucoseReading> list = getReadingsOfDay(day);
                for (int i = 0; i < list.size(); i++) {
                    int v = list.get(i).getValue();
                    sum = sum + v;
                    count++;
                    if (v < min) {
                        min = v;
                    }
                    if (v > max) {
                        max = v;
                    }
                }
            }
            // Only groups with readings become points
            if (count > 0) {
                if (groupSize == 1) {
                    labels.add(DateUtil.dayName(groupStart));
                } else {
                    labels.add(DateUtil.shortDate(groupStart));
                }
                avgList.add((float) sum / count);
                minList.add((float) min);
                maxList.add((float) max);
            }
        }

        int n = labels.size();
        String[] labelArr = new String[n];
        float[] avg = new float[n];
        float[] min = new float[n];
        float[] max = new float[n];
        for (int i = 0; i < n; i++) {
            labelArr[i] = labels.get(i);
            avg[i] = avgList.get(i);
            min[i] = minList.get(i);
            max[i] = maxList.get(i);
        }
        chart.setData(labelArr, avg, min, max);
    }

    // Green / orange / red light background of the value
    private int getBadgeBackground(int value) {
        String status = GlucoseReading.getStatus(value);
        if (status.equals("Normal")) {
            return R.drawable.bg_value_badge_green;
        }
        if (status.equals("Elevated")) {
            return R.drawable.bg_value_badge_orange;
        }
        return R.drawable.bg_value_badge_red;
    }

    // Shows all the details of a reading (feelings + note)
    private void showReadingDetails(GlucoseReading reading) {
        String text = reading.getValue() + " mg/dL • " + GlucoseReading.getStatus(reading.getValue())
                + "\n" + reading.getContext() + " • " + reading.getTimeText();
        if (reading.getFeelings() != null && !reading.getFeelings().isEmpty()) {
            text = text + "\n\nFeeling: " + reading.getFeelings();
        }
        if (reading.getNote() != null && !reading.getNote().isEmpty()) {
            text = text + "\n\nNote: " + reading.getNote();
        }
        new AlertDialog.Builder(this)
                .setTitle("Reading")
                .setMessage(text)
                .setPositiveButton("OK", null)
                .show();
    }

    // Asks before deleting a reading
    private void confirmDelete(final GlucoseReading reading) {
        new AlertDialog.Builder(this)
                .setTitle("Delete reading")
                .setMessage("Delete " + reading.getValue() + " mg/dL at " + reading.getTimeText() + "?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (readingsRef != null && reading.getId() != null) {
                            readingsRef.child(reading.getId()).removeValue();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
