package com.cloublab.aitraffic;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.cloublab.aitraffic.helper.Camera2Utils;
import com.cloublab.aitraffic.helper.CameraXHelper;
import com.cloublab.aitraffic.helper.FaceNetHelper;
import com.cloublab.aitraffic.helper.OverlayView;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class FaceNetRegister extends AppCompatActivity {
    private PreviewView preview;
    private OverlayView overlayView;
    private EditText nameInput;
    private Button captureButton, saveButton;
    private CameraXHelper cameraXHelper;
    private FaceDetector faceDetector;
    private FaceNetHelper faceNetHelper;
    private boolean isProcessing = false;
    private boolean isCapturing = false;
    private final ArrayList<float[]> embeddings = new ArrayList<>();
    private static final int MAX_CAPTURE = 5;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_net_register);

        preview = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        nameInput = findViewById(R.id.nameInput);
        captureButton = findViewById(R.id.captureBtn);
        saveButton = findViewById(R.id.saveBtn);
        saveButton.setEnabled(false);

        captureButton.setOnClickListener(v->{
            if(embeddings.size() >= MAX_CAPTURE){
                return;
            }

            isCapturing = true;
        });

        saveButton.setOnClickListener(v->{
            String name = nameInput.getText().toString();
            if (name.isEmpty() || embeddings.isEmpty()) {
                Toast.makeText(this, "Name or embeddings missing", Toast.LENGTH_SHORT).show();
                return;
            }

            faceNetHelper.writeEmbeddings(name, embeddings);
            finish();
        });

        faceNetHelper = new FaceNetHelper(this, false);

        FaceDetector.FaceDetectorOptions options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder()
                        .setModelAssetPath("blaze_face_short_range.tflite")
                        .build())
                .setMinDetectionConfidence(0.5f)
                .setRunningMode(RunningMode.IMAGE)
                .build();

        try {
            faceDetector = FaceDetector.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e("FACE_NET", "Init detector error", e);
        }

        cameraXHelper = new CameraXHelper(this, this, preview, true, new Size(640, 480), this::processImage);
    }

    private void processImage(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }
        isProcessing = true;

        preview.post(()-> {
            Bitmap bitmap = preview.getBitmap();
            imageProxy.close();

            if (bitmap == null) {
                isProcessing = false;
                return;
            }

            Executors.newSingleThreadExecutor().execute(()->{

                MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                FaceDetectorResult result = faceDetector.detect(mpImage);

                List<Detection> detections = result.detections();
                if(detections.isEmpty()){
                    isProcessing = false;
                    return;
                }
                Detection detection = detections.get(0);
                RectF box = detection.boundingBox();

                runOnUiThread(() -> {
                    overlayView.setFaces(Collections.singletonList(box), Collections.singletonList(""), bitmap.getWidth(), bitmap.getHeight());
                });

                if(!isCapturing){
                    isProcessing = false;
                    return;
                }

                Bitmap cropped = Camera2Utils.cropToBoundingBox(bitmap, box);
                float[] embedding = faceNetHelper.getFaceEmbedding(cropped);
                if(embedding != null){
                    embeddings.add(embedding);
                    runOnUiThread(() -> Toast.makeText(this, "Captured " + embeddings.size() + "/" + MAX_CAPTURE, Toast.LENGTH_SHORT).show());
                }else {
                    runOnUiThread(()-> Toast.makeText(this, "Face not found!!!", Toast.LENGTH_SHORT).show());
                }

                if(embeddings.size() >= MAX_CAPTURE){
                    runOnUiThread(() -> {
                        saveButton.setEnabled(true);
                        captureButton.setEnabled(false);
                    });
                }

                isCapturing = false;
                isProcessing = false;

            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraXHelper.shutdown();
    }
}
