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

/**
 * Custom chart (drawn with Canvas): average glucose by time of day.
 * Green line + light green area under it, dashed target lines at 70 and 140.
 */
public class GlucosePatternView extends View {

    private String[] labels = {"6am", "8am", "10am", "12pm", "2pm", "4pm", "6pm", "8pm", "10pm"};
    private float[] values = new float[9];   // NaN = no readings at this time

    private Paint gridPaint, textPaint, linePaint, fillPaint, highPaint, lowPaint, emptyPaint;

    private static final float MIN_Y = 60f;
    private static final float MAX_Y = 200f;

    public GlucosePatternView(Context context) {
        super(context);
        init();
    }

    public GlucosePatternView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // Prepares the paints (colors and line styles)
    private void init() {
        for (int i = 0; i < values.length; i++) {
            values[i] = Float.NaN;
        }
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#F1F3F5"));
        gridPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#9CA3AF"));
        textPaint.setTextSize(26f);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#3BB273"));
        linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.parseColor("#223BB273"));
        fillPaint.setStyle(Paint.Style.FILL);

        highPaint = dashed("#F59E0B");
        lowPaint = dashed("#EF4444");

        emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyPaint.setColor(Color.parseColor("#9CA3AF"));
        emptyPaint.setTextSize(34f);
    }

    // Helper: a dashed line paint
    private Paint dashed(String color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor(color));
        p.setStrokeWidth(3f);
        p.setStyle(Paint.Style.STROKE);
        p.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0));
        return p;
    }

    // Gives the chart new data and draws again
    public void setData(String[] labels, float[] values) {
        this.labels = labels;
        this.values = values;
        invalidate();
    }

    // Value → y position on the screen
    private float yOf(float value, float top, float height) {
        if (value < MIN_Y) {
            value = MIN_Y;
        }
        if (value > MAX_Y) {
            value = MAX_Y;
        }
        return top + height * (1f - (value - MIN_Y) / (MAX_Y - MIN_Y));
    }

    // Draws everything
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float left = 70f, right = 20f, top = 20f, bottom = 50f;
        float w = getWidth() - left - right;
        float h = getHeight() - top - bottom;

        // Grid + Y labels
        int[] yLabels = {200, 165, 130, 95, 60};
        for (int i = 0; i < yLabels.length; i++) {
            float y = yOf(yLabels[i], top, h);
            canvas.drawLine(left, y, left + w, y, gridPaint);
            canvas.drawText(String.valueOf(yLabels[i]), 10f, y + 9f, textPaint);
        }
        canvas.drawLine(left, yOf(140, top, h), left + w, yOf(140, top, h), highPaint);
        canvas.drawLine(left, yOf(70, top, h), left + w, yOf(70, top, h), lowPaint);

        // X labels
        float step = w / (labels.length - 1);
        for (int i = 0; i < labels.length; i++) {
            float x = left + i * step;
            float tw = textPaint.measureText(labels[i]);
            canvas.drawText(labels[i], x - tw / 2f, getHeight() - 12f, textPaint);
        }

        // Line + area (only the points that have data)
        Path line = new Path();
        Path area = new Path();
        boolean started = false;
        float firstX = 0, lastX = 0;
        for (int i = 0; i < values.length; i++) {
            if (Float.isNaN(values[i])) {
                continue;
            }
            float x = left + i * step;
            float y = yOf(values[i], top, h);
            if (!started) {
                line.moveTo(x, y);
                area.moveTo(x, top + h);
                area.lineTo(x, y);
                firstX = x;
                started = true;
            } else {
                line.lineTo(x, y);
                area.lineTo(x, y);
            }
            lastX = x;
        }
        if (!started) {
            String msg = "Log readings at different times to see your pattern";
            float tw = emptyPaint.measureText(msg);
            canvas.drawText(msg, (getWidth() - tw) / 2f, top + h / 2f, emptyPaint);
            return;
        }
        area.lineTo(lastX, top + h);
        area.lineTo(firstX, top + h);
        area.close();
        canvas.drawPath(area, fillPaint);
        canvas.drawPath(line, linePaint);
    }
}
