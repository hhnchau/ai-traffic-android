package com.cloublab.aitraffic;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.cloublab.aitraffic.helper.Camera2Utils;
import com.cloublab.aitraffic.helper.CameraXHelper;
import com.cloublab.aitraffic.helper.FaceNetHelper;
import com.cloublab.aitraffic.helper.OverlayView;
import com.cloublab.aitraffic.helper.VibrationHelper;
import com.cloublab.aitraffic.model.FaceData;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;


public class FaceNetDetector extends AppCompatActivity {
    private PreviewView preview;
    private OverlayView overlayView;
    private CameraXHelper cameraXHelper;
    private FaceDetector faceDetector;
    private FaceNetHelper faceNetHelper;
    private boolean isProcessing = false;
    private ArrayList<FaceData> embeddings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout layout = new FrameLayout(this);
        setContentView(layout);
        preview = new PreviewView(this);
        layout.addView(preview);

        overlayView = new OverlayView(this);
        layout.addView(overlayView);

        faceNetHelper = new FaceNetHelper(this, false);
        embeddings = faceNetHelper.readEmbedding();


        for (int i = 0; i < embeddings.size(); i++) {
            float distance = l2Norm(embeddings.get(0).embedding, embeddings.get(i).embedding);
            Log.d("TAG===", "Compare 0 vs " + i + ": " + distance);
        }


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

        cameraXHelper = new CameraXHelper(this, this, preview, true, new Size(480, 640), this::processImage);

    }

    private void processImage(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }
        isProcessing = true;

        preview.post(() -> {
            Bitmap bitmap = preview.getBitmap();
            imageProxy.close();

            if (bitmap == null) {
                isProcessing = false;
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {

                MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                FaceDetectorResult result = faceDetector.detect(mpImage);

                List<Detection> detections = result.detections();
                List<RectF> faceBoxes = new ArrayList<>();
                List<String> names = new ArrayList<>();

                for (Detection detection : detections) {
                    RectF box = detection.boundingBox();
                    faceBoxes.add(box);

                    Bitmap cropped = Camera2Utils.cropToBoundingBox(bitmap, box);
                    float[] embedding = faceNetHelper.getFaceEmbedding(cropped);
                    if (embedding != null && embeddings != null && embeddings.size() > 0) {


                        ArrayList<FaceData> embs = new ArrayList<>();
                        embs.add(new FaceData("A", embedding));
                        String name = faceNetHelper.compare(embeddings.get(0).embedding, embeddings, "l2", 0.55f);


                        //String name = faceNetHelper.compare(embedding, embeddings, "l2", 0.55f);
                        names.add(name);
                        if (!name.equals("Unknown")) VibrationHelper.vibrate(this, 200);
                    } else {
                        names.add("Unknown");
                    }
                }

                runOnUiThread(() -> {
                    overlayView.setFaces(faceBoxes, names, bitmap.getWidth(), bitmap.getHeight());
                    isProcessing = false;
                });
            });
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraXHelper.shutdown();
    }

    public float l2Norm(float[] x1, float[] x2) {
        if (x1 == null || x2 == null || x1.length != x2.length) {
            throw new IllegalArgumentException("Embeddings must be non-null and of same length");
        }

        float sum = 0f;
        for (int i = 0; i < x1.length; i++) {
            float diff = x1[i] - x2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }
}