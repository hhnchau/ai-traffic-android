package com.cloublab.aitraffic;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.cloublab.aitraffic.helper.Camera2Helper;
import com.cloublab.aitraffic.helper.CameraUtils;
import com.cloublab.aitraffic.helper.FaceDetectorHelper;
import com.cloublab.aitraffic.helper.FaceRecognitionHelper;
import com.cloublab.aitraffic.helper.ImageWrapper;
import com.cloublab.aitraffic.helper.JsonDatabase;
import com.cloublab.aitraffic.helper.OverlayView;
import com.cloublab.aitraffic.helper.SurfaceViewAspectRatio;
import com.cloublab.aitraffic.helper.VibrationHelper;
import com.google.mediapipe.tasks.components.containers.Detection;

import java.util.ArrayList;
import java.util.List;

public class FaceDetector extends AppCompatActivity {
    private static final float THRESHOLD = 0.5f;
    private Camera2Helper camera2Helper;
    private OverlayView overlayView;
    private FaceDetectorHelper faceDetectorHelper;
    private FaceRecognitionHelper faceRecognitionHelper;
    private ImageWrapper imageWrapper;
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
        overlayView = new OverlayView(this);
        layout.addView(overlayView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        JsonDatabase db = new JsonDatabase(this);
        faceDetectorHelper = new FaceDetectorHelper(this, "blaze_face_short_range.tflite", (result, image)->{

            List<Detection> detections = result.detections();
            if(detections == null || detections.isEmpty()){
                runOnUiThread(() -> {
                    overlayView.setFaces(new ArrayList<>(), new ArrayList<>(), image.getWidth(), image.getHeight());
                    isProcessing = false;
                });
                return;
            }

            List<RectF> faceBoxes = new ArrayList<>();
            List<String> names = new ArrayList<>();

            for(Detection detection: detections){
                RectF box = detection.boundingBox();
                faceBoxes.add(box);

                Bitmap bitmapCrop = CameraUtils.cropToBoundingBox(imageWrapper.getBitmap(), box);
                float[] embedding = faceRecognitionHelper.getFaceEmbedding(bitmapCrop);

                String match = db.findNearestFace(embedding, THRESHOLD);
                if(!match.equals("Unknown")) VibrationHelper.vibrate(this, 200);
                names.add(match);
            }

            runOnUiThread(() -> {
                overlayView.setFaces(faceBoxes, names, image.getWidth(), image.getHeight());
                isProcessing = false;
            });
        });
        faceRecognitionHelper = new FaceRecognitionHelper(this, "mobile_face_net.tflite");
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                camera2Helper = new Camera2Helper(FaceDetector.this, holder, new Size(640, 480), image -> {processImage(image);});
                camera2Helper.start(true);
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

        Bitmap bitmap = CameraUtils.yuvToBitmap(image, true);
        image.close();

        imageWrapper = new ImageWrapper(bitmap);

        if(faceDetectorHelper != null)
            faceDetectorHelper.detectAsync(bitmap, () -> isProcessing = false);
    }
}
