package com.cloublab.aitraffic;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.cloublab.aitraffic.helper.Camera2Helper;
import com.cloublab.aitraffic.helper.OverlayViewFilter;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FaceFilter extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Camera2Helper camera2Helper;
    private OverlayViewFilter overlayView;
    private Bitmap mouthBitmap;
    private boolean isProcessing = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout layout = new FrameLayout(this);
        setContentView(layout);

        SurfaceView surfaceView = new SurfaceView(this);
        layout.addView(surfaceView);

        overlayView = new OverlayViewFilter(this);
        layout.addView(overlayView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mouthBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.cigar);
        Bitmap resized = Bitmap.createScaledBitmap(mouthBitmap, 100, 40, true);
        overlayView.setMouthBitmap(resized);

        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
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

    private void processImage(Image image){
        if (isProcessing) {
            image.close();
            return;
        }

        isProcessing = true;

        int rotationDegrees = getRotationDegrees();

        InputImage inputImage = InputImage.fromMediaImage(image,  rotationDegrees);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(inputImage)
                .addOnSuccessListener(faces ->{
                    List<PointF> mouthPoints = new ArrayList<>();
                    for(Face face: faces){
                        FaceLandmark mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
                        if(mouth!= null){
                            mouthPoints.add(mouth.getPosition());
                        }
                    }
                    if(mouthPoints.size() > 0)Toast.makeText(this, "[" + mouthPoints.size() + "]", Toast.LENGTH_SHORT).show();
                    overlayView.setMouthPositions(mouthPoints);
                    image.close();
                    isProcessing = false;
                })
                .addOnFailureListener(e->{
                    Log.e("FACE_FILTER", "MLKit failed: ", e);
                    image.close();
                    isProcessing = false;
                });
    }

    private byte[] yuv420ToNV21(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // Copy Y
        yBuffer.get(nv21, 0, ySize);

        // Copy VU (not UV)
        byte[] uBytes = new byte[uSize];
        byte[] vBytes = new byte[vSize];
        uBuffer.get(uBytes);
        vBuffer.get(vBytes);

        for (int i = 0; i < uSize; i++) {
            nv21[ySize + i * 2] = vBytes[i];
            nv21[ySize + i * 2 + 1] = uBytes[i];
        }

        return nv21;
    }

    private int getRotationDegrees() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 90;
            case Surface.ROTATION_90: return 0;
            case Surface.ROTATION_180: return 270;
            case Surface.ROTATION_270: return 180;
            default: return 0;
        }
    }

}
