package com.cloublab.aitraffic.helper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class CameraUtils {
    public static Bitmap yuvToBitmap(Image image, boolean isFrontCamera) {
        YuvImage yuvImage = convertYUV420888ToYuvImage(image);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        Matrix matrix = new Matrix();
        matrix.postRotate(270);

        if(isFrontCamera)
            matrix.postScale(-1, 1);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static YuvImage convertYUV420888ToYuvImage(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
    }

    public static Bitmap cropToBoundingBox(Bitmap bitmap, RectF rectF){
        int left = Math.max(0, (int)rectF.left);
        int top = Math.max(0, (int)rectF.top);
        int right = Math.min(bitmap.getWidth(), (int)rectF.right);
        int bottom = Math.min(bitmap.getHeight(), (int)rectF.bottom);

        int  width = right - left;
        int height = bottom - top;

        if(width <= 0 || height <= 0 || left >= bitmap.getWidth() || top >= bitmap.getHeight()){
            return null;
        }

        return Bitmap.createBitmap(bitmap, left, top, width, height);
    }

}
