package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class SurfaceViewAspectRatio extends SurfaceView {
    private float aspectRatio = 4f / 3f;
    public SurfaceViewAspectRatio(Context context){
        super(context);
    }

    public SurfaceViewAspectRatio(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAspectRatio(float ratio){
        this.aspectRatio = ratio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (width*aspectRatio);
        setMeasuredDimension(width, height);
    }
}
