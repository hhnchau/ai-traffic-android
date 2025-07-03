package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private List<RectF> rectFs = new ArrayList<>();
    private List<String> names = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private final RectF finalRectF = new RectF();
    private float scaleFactor = 1f;

    public OverlayView(Context context){
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36);
    }

    public void setFaces(List<RectF> rectFs, List<String> labels, int imageWidth, int imageHeight) {
        this.rectFs = rectFs;
        this.names = labels;
        scaleFactor = Math.min(getWidth() * 1f / imageWidth, getHeight() * 1f / imageHeight);
        postInvalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        for(int i = 0; i < rectFs.size(); i++){

            float top = rectFs.get(i).top * scaleFactor;
            float bottom = rectFs.get(i).bottom * scaleFactor;
            float left = rectFs.get(i).left * scaleFactor;
            float right = rectFs.get(i).right * scaleFactor;
            finalRectF.set(left, top, right, bottom);
            canvas.drawRect(finalRectF, boxPaint);
            canvas.drawText(names.get(i), left, top - 10, textPaint);
        }
    }
}
