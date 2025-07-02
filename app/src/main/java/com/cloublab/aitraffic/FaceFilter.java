package com.cloublab.aitraffic;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
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

import com.cloublab.aitraffic.helper.SurfaceViewAspectRatio;
import com.cloublab.aitraffic.helper.Camera2Helper;
import com.cloublab.aitraffic.helper.OverlayViewBox;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;

import com.google.mediapipe.tasks.vision.facedetector.FaceDetector;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class FaceFilter extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Camera2Helper camera2Helper;
    private OverlayViewBox overlayView;
    private boolean isProcessing = false;
    private FaceDetector detector;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout layout = new FrameLayout(this);
        setContentView(layout);

        SurfaceViewAspectRatio surfaceView = new SurfaceViewAspectRatio(this);
        surfaceView.setAspectRatio(4f/3f);
        layout.addView(surfaceView);

        overlayView = new OverlayViewBox(this);
        layout.addView(overlayView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        FaceDetector.FaceDetectorOptions options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(
                        BaseOptions.builder()
                                .setModelAssetPath("blaze_face_short_range.tflite")
                                .build()
                ).setRunningMode(RunningMode.LIVE_STREAM)
                .setMinDetectionConfidence(0.5f)
                .setResultListener((result, image)->{
                    runOnUiThread(()->{
                        overlayView.setFaceBoxes(result, image.getHeight(), image.getWidth());
                        isProcessing = false;
                    });

                })
                .setErrorListener(error->{
                    Log.e("FACE_VISION", "Detection error: " + error.getMessage());
                    isProcessing = false;
                })
                .build();

        try {
            detector = FaceDetector.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e("FACE_VISION", "Init detector error", e);
        }

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                startCamera(holder);
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

    private void startCamera(SurfaceHolder holder){
        camera2Helper = new Camera2Helper(this, holder, new Size(640, 480), this::processImage);
        camera2Helper.start();
    }

    /*FACE BOUNDING BOX*/
    private void processImage(Image image){
        if (isProcessing) {
            image.close();
            return;
        }
        isProcessing = true;

        try {

            Bitmap bitmap =yuvToBitmap(image);
            image.close();
            if(bitmap.getConfig() != Bitmap.Config.ARGB_8888){
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            }

            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            detector.detectAsync(mpImage, System.currentTimeMillis());
        }catch (Exception e){
            Log.e("FACE_VISION", "Error creating MPImage", e);
            image.close();
            isProcessing = false;
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
