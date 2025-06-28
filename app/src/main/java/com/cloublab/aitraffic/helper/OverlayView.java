package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private List<Rect> faceRects = new ArrayList<>();
    private List<String> names = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;

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

    public void setFaces(List<Rect> rects, List<String> labels) {
        this.faceRects = rects;
        this.names = labels;
        invalidate();
    }

    public Rect mapRectToOverlay(Rect faceRect, int bitmapWidth, int bitmapHeight, int overlayWidth, int overlayHeight, boolean isFrontCamera){
        float scaleX = (float)overlayWidth / bitmapWidth;
        float scaleY = (float)overlayHeight / bitmapHeight;

        int left = Math.round(faceRect.left * scaleX);
        int top = Math.round(faceRect.top * scaleY);
        int right = Math.round(faceRect.right * scaleX);
        int bottom = Math.round(faceRect.bottom * scaleY);

        if(isFrontCamera){
            int mirroredLeft = overlayWidth - right;
            int mirroredRight = overlayWidth - left;
            left = mirroredLeft;
            right = mirroredRight;
        }
        return new Rect(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < faceRects.size(); i++) {
            canvas.drawRect(faceRects.get(i), boxPaint);
            canvas.drawText(names.get(i), faceRects.get(i).left, faceRects.get(i).top - 10, textPaint);
        }
    }
}
