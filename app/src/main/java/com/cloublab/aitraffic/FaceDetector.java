package com.cloublab.aitraffic;

import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.cloublab.aitraffic.helper.Camera2Helper;
import com.cloublab.aitraffic.helper.FaceDetectorHelper;
import com.cloublab.aitraffic.helper.FaceRecognitionHelper;
import com.cloublab.aitraffic.helper.OverlayViewBox;
import com.cloublab.aitraffic.helper.SurfaceViewAspectRatio;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;

public class FaceDetector extends AppCompatActivity {
    private Camera2Helper camera2Helper;
    private OverlayViewBox overlayView;
    private FaceDetectorHelper faceDetectorHelper;
    private FaceRecognitionHelper faceRecognitionHelper;
    private boolean isProcessing = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 100);
            return;
        }

        FrameLayout layout = new FrameLayout(this);
        setContentView(layout);
        SurfaceViewAspectRatio surfaceView = new SurfaceViewAspectRatio(this);
        layout.addView(surfaceView);
        overlayView = new OverlayViewBox(this);
        layout.addView(overlayView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        faceDetectorHelper = new FaceDetectorHelper(this, "blaze_face_short_range.tflite", (result, image)->{
            runOnUiThread(() -> {
                overlayView.setFaceBoxes(result, image.getWidth(), image.getHeight());
                isProcessing = false;
            });
        });
        //faceRecognitionHelper = new FaceRecognitionHelper(this);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                camera2Helper = new Camera2Helper(FaceDetector.this, holder, new Size(640, 480), image -> {processImage(image);});
                camera2Helper.start();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if(camera2Helper != null) camera2Helper.stop();
            }
        });
    }

    private void processImage(Image image){
        if (isProcessing) {
            image.close();
            return;
        }
        isProcessing = true;
        if(faceDetectorHelper != null)
            faceDetectorHelper.detectAsync(image, () -> isProcessing = false);
    }
}
