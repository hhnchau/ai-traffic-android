package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult;

public class FaceDetectorHelper {
    public interface Callback{
        void onResults(FaceDetectorResult result, Bitmap bitmap);
    }
    private final FaceDetector faceDetector;

    public FaceDetectorHelper(Context context, String modelFile, Callback callback){
        FaceDetector.FaceDetectorOptions options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelFile).build())
                .setMinDetectionConfidence(0.5f)
                .setResultListener((r,i)->{
                    MPImage tmp = i;
                    //Bitmap bitmap = MPImageU
                            //if(callback != null) callback.onResults(r, bitmap);
                })
                .setErrorListener(e-> Log.e("FACE_DETECTOR", e.getMessage()))
                .build();
        faceDetector = FaceDetector.createFromOptions(context, options);
    }

    public void detectAsync(Bitmap bitmap){
        faceDetector.detectAsync(new BitmapImageBuilder(bitmap).build(), System.nanoTime());
    }
}
