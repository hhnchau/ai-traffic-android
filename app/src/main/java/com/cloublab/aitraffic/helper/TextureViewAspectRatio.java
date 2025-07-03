package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.TextureView;

public class TextureViewAspectRatio extends TextureView {
    private float aspectRatio = 4f / 3f;
    public TextureViewAspectRatio(Context context){
        super(context);
    }

    public TextureViewAspectRatio(Context context, AttributeSet attrs) {
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
