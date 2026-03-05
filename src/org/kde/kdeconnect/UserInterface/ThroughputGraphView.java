/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Real-time throughput graph for OPDL metrics panel.
 */
public class ThroughputGraphView extends View {
    private final Paint graphPaint;
    private final Paint gridPaint;
    private final Paint textPaint;
    private final LinkedList<Float> throughputPoints;
    private float maxThroughput = 1000f; // MB/s
    
    public ThroughputGraphView(Context context) {
        super(context);
        graphPaint = createPaint(Color.parseColor("#4CAF50"), 4f);
        gridPaint = createPaint(Color.parseColor("#333333"), 1f);
        textPaint = createPaint(Color.WHITE, 2f);
        textPaint.setTextSize(24f);
        throughputPoints = new LinkedList<>();
    }
    
    public ThroughputGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        graphPaint = createPaint(Color.parseColor("#4CAF50"), 4f);
        gridPaint = createPaint(Color.parseColor("#333333"), 1f);
        textPaint = createPaint(Color.WHITE, 2f);
        textPaint.setTextSize(24f);
        throughputPoints = new LinkedList<>();
    }
    
    private Paint createPaint(int color, float strokeWidth) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        return paint;
    }
    
    public void addThroughputPoint(float throughput) {
        throughputPoints.add(throughput);
        if (throughputPoints.size() > 50) {
            throughputPoints.removeFirst();
        }
        maxThroughput = Math.max(maxThroughput, throughput * 1.2f);
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        // Draw grid
        drawGrid(canvas, width, height);
        
        // Draw throughput graph
        if (throughputPoints.size() > 1) {
            drawThroughputGraph(canvas, width, height);
        }
        
        // Draw max value
        canvas.drawText(String.format("%.0f MB/s", maxThroughput), 20, 40, textPaint);
    }
    
    private void drawGrid(Canvas canvas, int width, int height) {
        // Vertical grid lines
        for (int i = 0; i <= 10; i++) {
            float x = width * i / 10f;
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        
        // Horizontal grid lines
        for (int i = 0; i <= 5; i++) {
            float y = height * i / 5f;
            canvas.drawLine(0, y, width, y, gridPaint);
        }
    }
    
    private void drawThroughputGraph(Canvas canvas, int width, int height) {
        float pointSpacing = (float) width / Math.max(1, throughputPoints.size() - 1);
        
        for (int i = 1; i < throughputPoints.size(); i++) {
            float x1 = (i - 1) * pointSpacing;
            float y1 = height - (throughputPoints.get(i - 1) / maxThroughput) * height;
            float x2 = i * pointSpacing;
            float y2 = height - (throughputPoints.get(i) / maxThroughput) * height;
            
            canvas.drawLine(x1, y1, x2, y2, graphPaint);
        }
    }
}