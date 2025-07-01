package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.view.SurfaceView;

public class AspectRatioSurfaceView extends SurfaceView {
    private float aspectRatio = 4f / 3f;
    public AspectRatioSurfaceView(Context context){
        super(context);
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
