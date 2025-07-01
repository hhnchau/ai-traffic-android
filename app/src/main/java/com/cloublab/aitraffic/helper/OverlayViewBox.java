package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class OverlayViewBox extends View {
    private final Paint boxPaint = new Paint();
    private List<Rect> faceBoxes = new ArrayList<>();
    private int previewWidth = 640, previewHeight = 480;
    private boolean isFrontCamera = true;

    public OverlayViewBox(Context context){
        super(context);
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
    }

    public void setPreviewSize(int w, int h){
        this.previewWidth = w;
        this.previewHeight = h;
    }

    public void setFaceBoxes(List<Rect> faces, boolean isFrontCamera){
        this.faceBoxes = faces;
        this.isFrontCamera =isFrontCamera;
        postInvalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float viewWidth = getWidth();
        float viewHeight = getHeight();


        float scaleX = viewWidth / (float )previewWidth;
        float scaleY = viewHeight / (float) previewHeight;

        float scale = Math.min(scaleX, scaleY);

        float offsetX = (viewWidth - previewWidth * scale) / 2;
        float offsetY = (viewHeight - previewHeight * scale) / 2;

        for(Rect face: faceBoxes){

            float left = face.left * scaleX + offsetX;
            float top  = face.top * scaleY + offsetY;
            float right  = face.right * scaleX + offsetX;
            float bottom  = face.bottom * scaleY + offsetY;

            if(isFrontCamera){
                float mirroredLeft = getWidth() - right;
                float mirroredRight = getWidth() - left;

                left = mirroredLeft;
                right = mirroredRight;
            }

            canvas.drawRect(left, top, right, bottom, boxPaint);
        }
    }
}
