package com.example.glumotion.glucose;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Custom bar chart (Canvas): % of readings in range (70-140) for every week.
 */
public class WeeklyBarView extends View {

    private String[] labels = {"Wk 1", "Wk 2", "Wk 3", "Wk 4"};
    private int[] percents = {-1, -1, -1, -1};   // -1 = no readings that week

    private Paint barPaint, gridPaint, textPaint, valuePaint;

    public WeeklyBarView(Context context) {
        super(context);
        init();
    }

    public WeeklyBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // Prepares the paints
    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.parseColor("#5CC062"));
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#F1F3F5"));
        gridPaint.setStrokeWidth(2f);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#9CA3AF"));
        textPaint.setTextSize(26f);
        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.parseColor("#1D2623"));
        valuePaint.setTextSize(26f);
        valuePaint.setFakeBoldText(true);
    }

    // New data → draw again
    public void setData(String[] labels, int[] percents) {
        this.labels = labels;
        this.percents = percents;
        invalidate();
    }

    // Draws the grid and the bars
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float left = 60f, right = 20f, top = 30f, bottom = 50f;
        float w = getWidth() - left - right;
        float h = getHeight() - top - bottom;

        int[] grid = {100, 50, 25, 0};
        for (int i = 0; i < grid.length; i++) {
            float y = top + h * (1f - grid[i] / 100f);
            canvas.drawLine(left, y, left + w, y, gridPaint);
            canvas.drawText(String.valueOf(grid[i]), 8f, y + 9f, textPaint);
        }

        float slot = w / percents.length;
        float barWidth = slot * 0.6f;
        for (int i = 0; i < percents.length; i++) {
            float cx = left + slot * i + slot / 2f;
            float tw = textPaint.measureText(labels[i]);
            canvas.drawText(labels[i], cx - tw / 2f, getHeight() - 12f, textPaint);
            if (percents[i] < 0) {
                canvas.drawText("-", cx - 6f, top + h - 10f, textPaint);
                continue;
            }
            float barTop = top + h * (1f - percents[i] / 100f);
            canvas.drawRoundRect(new RectF(cx - barWidth / 2f, barTop, cx + barWidth / 2f, top + h), 8f, 8f, barPaint);
            String v = percents[i] + "%";
            canvas.drawText(v, cx - valuePaint.measureText(v) / 2f, barTop - 8f, valuePaint);
        }
    }
}
