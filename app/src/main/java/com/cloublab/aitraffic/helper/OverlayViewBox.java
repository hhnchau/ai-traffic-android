package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult;

import java.util.List;


public class OverlayViewBox extends View {
    private static final int BOUNDING_RECT_TEXT_PADDING = 8;
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint bgPaint = new Paint();
    private FaceDetectorResult result;
    private float scaleFactor = 1f;
    private final RectF finalRectF = new RectF();
    private final Rect bounds = new Rect();

    public OverlayViewBox(Context context){
        super(context);
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);

        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setStrokeWidth(50f);

        bgPaint.setColor(Color.BLACK);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setStrokeWidth(50f);
    }


    public void setFaceBoxes(FaceDetectorResult result, int imageWidth, int imageHeight){
       this.result = result;
        scaleFactor = Math.min(getWidth() * 1f / imageWidth, getHeight() * 1f / imageHeight);
        postInvalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if(result == null) return;

        List<Detection> detections = result.detections();
        for(Detection detection: detections){
            RectF rectF = detection.boundingBox();
            float top = rectF.top * scaleFactor;
            float bottom = rectF.bottom * scaleFactor;
            float left = rectF.left * scaleFactor;
            float right = rectF.right * scaleFactor;
            finalRectF.set(left, top, right, bottom);
            canvas.drawRect(finalRectF, boxPaint);

            Category category = detection.categories().get(0);
            String drawableText = category.categoryName() + " " + category.score();
            bgPaint.getTextBounds(drawableText, 0, drawableText.length(),bounds);
            int textWidth = bounds.width();
            int textHeight = bounds.height();

            canvas.drawRect(left, top, left + textWidth + BOUNDING_RECT_TEXT_PADDING, top + textHeight + BOUNDING_RECT_TEXT_PADDING, bgPaint);

            canvas.drawText(drawableText, left, top + bounds.height(), textPaint);
        }
    }
}
