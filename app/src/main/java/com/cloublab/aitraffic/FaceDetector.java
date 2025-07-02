package com.cloublab.aitraffic;

import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.cloublab.aitraffic.helper.Camera2Helper;
import com.cloublab.aitraffic.helper.SurfaceViewAspectRatio;

public class FaceDetector extends AppCompatActivity {
    private Camera2Helper camera2Helper;
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
    }
}
