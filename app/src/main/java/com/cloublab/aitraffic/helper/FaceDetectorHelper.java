package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class FaceDetectorHelper {
    public interface Callback{
        void onResults(FaceDetectorResult result, MPImage image);
    }
    private FaceDetector faceDetector;

    public FaceDetectorHelper(Context context, String modelFile, Callback callback){
        FaceDetector.FaceDetectorOptions options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelFile).build())
                .setMinDetectionConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener((r,i)->{
                    if(callback != null) callback.onResults(r, i);
                })
                .setErrorListener(e-> {
                    if(e.getMessage() != null)
                        Log.e("FACE_DETECTOR_HELPER", e.getMessage());
                })
                .build();
        try {
            faceDetector = FaceDetector.createFromOptions(context, options);
        }catch (Exception e){
            e.printStackTrace();
            Log.e("FACE_DETECTOR_HELPER", "Init detector error", e);
        }

    }

    public void detectAsync(Bitmap bitmap){
        if(faceDetector != null){
            faceDetector.detectAsync(new BitmapImageBuilder(bitmap).build(), System.nanoTime());
        }else {
            Log.e("FACE_DETECTOR_HELPER", "Fae Detector is NULL");
        }
    }

    public void detectAsync(Image image, Runnable callback){

        if(image == null || faceDetector == null){
            Log.e("FACE_DETECTOR_HELPER", "Fae Detector is NULL");
            return;
        }

        try {
            Bitmap bitmap =yuvToBitmap(image);
            image.close();
            if(bitmap.getConfig() != Bitmap.Config.ARGB_8888){
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            }

            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            faceDetector.detectAsync(mpImage, System.currentTimeMillis());
        }catch (Exception e){
            Log.e("FACE_DETECTOR_HELPER", "Error creating MPImage", e);
            image.close();
            callback.run();
        }
    }

    public static Bitmap yuvToBitmap(Image image) {
        YuvImage yuvImage = convertYUV420888ToYuvImage(image);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        matrix.postScale(-1, 1);

        return Bitmap.createBitmap(bitmap, 0,0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
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
}
