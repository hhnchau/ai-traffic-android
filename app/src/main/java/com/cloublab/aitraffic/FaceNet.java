package com.cloublab.aitraffic;

import android.os.Bundle;
import android.util.Size;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.cloublab.aitraffic.helper.CameraXHelper;


public class FaceNet extends AppCompatActivity {
    private CameraXHelper cameraXHelper;
    private boolean isProcessing = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout layout = new FrameLayout(this);
        setContentView(layout);
        PreviewView preview = new PreviewView(this);
        layout.addView(preview);

        cameraXHelper = new CameraXHelper(this, this, preview, true, new Size(640, 480), this::processImage);

    }

    private void processImage(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }
        isProcessing = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraXHelper.shutdown();
    }
}
