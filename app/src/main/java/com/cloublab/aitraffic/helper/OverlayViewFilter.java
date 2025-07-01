package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class OverlayViewFilter extends View {
    private final Paint paint = new Paint();
    private List<PointF> mouthPositions = new ArrayList<>();
    private Bitmap mouthBitmap;

    public OverlayViewFilter(Context context){
        super(context);
    }
    public void setMouthPositions(List<PointF> positions){
        this.mouthPositions = positions;
        postInvalidate();
    }
    public void setMouthBitmap(Bitmap bitmap){
        this.mouthBitmap = bitmap;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if(mouthBitmap != null){
            float scaleX = (float)getWidth() / 640;
            float scaleY = (float)getWidth() / 480;
            for(PointF p: mouthPositions){
                float x = p.x * scaleX;
                float y = p.y * scaleY;

                // Mirror if using front camera
                x = getWidth() - x;

                float left = x - (mouthBitmap.getWidth() / 2f);
                float top = y - (mouthBitmap.getHeight() / 2f);

                canvas.drawBitmap(mouthBitmap, left, top, paint);
            }
        }
    }
}
