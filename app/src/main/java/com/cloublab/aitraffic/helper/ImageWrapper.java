package com.cloublab.aitraffic.helper;

import android.graphics.Bitmap;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;

public class ImageWrapper {
    private final Bitmap bitmap;
    private final MPImage mpImage;

    public ImageWrapper(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.mpImage = new BitmapImageBuilder(bitmap).build();
    }

    public Bitmap getBitmap(){
        return bitmap;
    }

    public MPImage getMpImage(){
        return  mpImage;
    }

    public void close(){
        mpImage.close();
    }

}
