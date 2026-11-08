package com.example.glumotion.glucose;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GlucoseTrendChartView extends View {

    private Paint gridPaint;
    private Paint axisTextPaint;
    private Paint targetPaintUpper;
    private Paint targetPaintLower;
    private Paint linePaint;
    private Paint pointOuterPaint;
    private Paint pointInnerPaint;
    private Paint maxCurvePaint;
    private Paint minCurvePaint;

    // Data of the chart (demo values until setData() is called)
    private String[] xLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private final int[] yValues = {220, 180, 140, 100, 60};

    private float[] avgData = {120f, 125f, 110f, 135f, 120f, 145f, 115f};
    private float[] maxData = {140f, 160f, 140f, 175f, 150f, 195f, 135f};
    private float[] minData = {90f, 95f, 85f, 100f, 90f, 105f, 85f};

    private Paint emptyTextPaint;

    private final Path maxPath = new Path();
    private final Path minPath = new Path();
    private final Path avgPath = new Path();
    private float[] xCoords = new float[7];

    public GlucoseTrendChartView(Context context) {
        super(context);
        init();
    }

    public GlucoseTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlucoseTrendChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Gives the chart real data from Firebase (called by DMActivity).
     * All arrays must have the same length. Empty arrays = "No readings" message.
     */
    public void setData(String[] labels, float[] avg, float[] min, float[] max) {
        xLabels = labels;
        avgData = avg;
        minData = min;
        maxData = max;
        xCoords = new float[labels.length];
        invalidate(); // draw again
    }

    // Keeps a value inside the drawing area (60 - 220)
    private float clamp(float value) {
        if (value < 60f) {
            return 60f;
        }
        if (value > 220f) {
            return 220f;
        }
        return value;
    }

    private void init() {
        emptyTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyTextPaint.setColor(Color.parseColor("#9CA3AF"));
        emptyTextPaint.setTextSize(36f);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#F3F4F6"));
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);

        axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisTextPaint.setColor(Color.parseColor("#9CA3AF"));
        axisTextPaint.setTextSize(30f);

        targetPaintUpper = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaintUpper.setColor(Color.parseColor("#F87171"));
        targetPaintUpper.setStrokeWidth(2.5f);
        targetPaintUpper.setStyle(Paint.Style.STROKE);
        targetPaintUpper.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0));

        targetPaintLower = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaintLower.setColor(Color.parseColor("#60A5FA"));
        targetPaintLower.setStrokeWidth(2.5f);
        targetPaintLower.setStyle(Paint.Style.STROKE);
        targetPaintLower.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0));

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#00C853"));
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointOuterPaint.setColor(Color.parseColor("#00C853"));
        pointOuterPaint.setStyle(Paint.Style.FILL);

        pointInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointInnerPaint.setColor(Color.WHITE);
        pointInnerPaint.setStyle(Paint.Style.FILL);

        maxCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maxCurvePaint.setColor(Color.parseColor("#FCA5A5"));
        maxCurvePaint.setStrokeWidth(3f);
        maxCurvePaint.setStyle(Paint.Style.STROKE);
        maxCurvePaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0));

        minCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minCurvePaint.setColor(Color.parseColor("#93C5FD"));
        minCurvePaint.setStrokeWidth(3f);
        minCurvePaint.setStyle(Paint.Style.STROKE);
        minCurvePaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float paddingLeft = 80f;
        float paddingRight = 30f;
        float paddingTop = 30f;
        float paddingBottom = 70f;

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        float minY = 60f;
        float maxY = 220f;

        // 1. Draw horizontal grid lines & Y-axis labels
        for (int val : yValues) {
            float y = paddingTop + chartHeight * (1f - (val - minY) / (maxY - minY));

            // Grid Line
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint);

            // Text Label
            String label = String.valueOf(val);
            float textWidth = axisTextPaint.measureText(label);
            canvas.drawText(label, paddingLeft - textWidth - 16f, y + 10f, axisTextPaint);
        }

        // 2. Target threshold lines (Upper=140, Lower=70)
        float upperY = paddingTop + chartHeight * (1f - (140f - minY) / (maxY - minY));
        canvas.drawLine(paddingLeft, upperY, width - paddingRight, upperY, targetPaintUpper);

        float lowerY = paddingTop + chartHeight * (1f - (70f - minY) / (maxY - minY));
        canvas.drawLine(paddingLeft, lowerY, width - paddingRight, lowerY, targetPaintLower);

        // No data → show a message
        if (xLabels.length == 0) {
            String msg = "No readings yet";
            float w = emptyTextPaint.measureText(msg);
            canvas.drawText(msg, (width - w) / 2f, paddingTop + chartHeight / 2f, emptyTextPaint);
            return;
        }

        // Calculate X coordinates of the points (one point → in the middle)
        if (xLabels.length == 1) {
            xCoords[0] = paddingLeft + chartWidth / 2f;
        } else {
            float stepX = chartWidth / (xLabels.length - 1);
            for (int i = 0; i < xLabels.length; i++) {
                xCoords[i] = paddingLeft + i * stepX;
            }
        }

        // 3. Draw Max and Min curves
        maxPath.reset();
        minPath.reset();
        avgPath.reset();

        for (int i = 0; i < xLabels.length; i++) {
            float avgY = paddingTop + chartHeight * (1f - (clamp(avgData[i]) - minY) / (maxY - minY));
            float maxYPos = paddingTop + chartHeight * (1f - (clamp(maxData[i]) - minY) / (maxY - minY));
            float minYPos = paddingTop + chartHeight * (1f - (clamp(minData[i]) - minY) / (maxY - minY));

            if (i == 0) {
                maxPath.moveTo(xCoords[i], maxYPos);
                minPath.moveTo(xCoords[i], minYPos);
                avgPath.moveTo(xCoords[i], avgY);
            } else {
                float prevX = xCoords[i - 1];
                float currX = xCoords[i];
                float midX = (prevX + currX) / 2f;

                float prevMaxY = paddingTop + chartHeight * (1f - (clamp(maxData[i - 1]) - minY) / (maxY - minY));
                maxPath.cubicTo(midX, prevMaxY, midX, maxYPos, currX, maxYPos);

                float prevMinY = paddingTop + chartHeight * (1f - (clamp(minData[i - 1]) - minY) / (maxY - minY));
                minPath.cubicTo(midX, prevMinY, midX, minYPos, currX, minYPos);

                avgPath.lineTo(currX, avgY);
            }
        }

        canvas.drawPath(maxPath, maxCurvePaint);
        canvas.drawPath(minPath, minCurvePaint);

        // 4. Draw Main Trend Line (Avg)
        canvas.drawPath(avgPath, linePaint);

        // 5. Draw Data Points on Avg line & X-axis Labels
        for (int i = 0; i < xLabels.length; i++) {
            float avgY = paddingTop + chartHeight * (1f - (clamp(avgData[i]) - minY) / (maxY - minY));

            // Outer Green Circle
            canvas.drawCircle(xCoords[i], avgY, 12f, pointOuterPaint);
            // Inner White Circle
            canvas.drawCircle(xCoords[i], avgY, 5f, pointInnerPaint);

            // X-axis label
            String xLabel = xLabels[i];
            float textWidth = axisTextPaint.measureText(xLabel);
            canvas.drawText(xLabel, xCoords[i] - textWidth / 2f, height - 15f, axisTextPaint);
        }
    }
}