package com.cloublab.aitraffic;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.cloublab.aitraffic.helper.Camera2Helper;
import com.cloublab.aitraffic.helper.CameraUtils;
import com.cloublab.aitraffic.helper.FaceDetectorHelper;
import com.cloublab.aitraffic.helper.FaceRecognitionHelper;
import com.cloublab.aitraffic.helper.JsonDatabase;
import com.cloublab.aitraffic.helper.OverlayViewBox;
import com.cloublab.aitraffic.helper.SurfaceViewAspectRatio;
import java.util.ArrayList;
import java.util.List;

public class FaceRegister extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 100;
    private static final int MAX_IMAGES = 5;
    private FaceRecognitionHelper faceRecognitionHelper;
    private FaceDetectorHelper faceDetectorHelper;
    private Camera2Helper camera2Helper;
    private OverlayViewBox overlayView;
    private final List<float[]> collectedEmbeddings = new ArrayList<>();
    private EditText nameInput;
    private Button captureBtn, saveBtn;
    private boolean isProcessing = false;
    private boolean isCapturing = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_register);

        SurfaceViewAspectRatio surfaceView = findViewById(R.id.surface);
        overlayView = findViewById(R.id.overlay);
        nameInput = findViewById(R.id.nameInput);
        captureBtn = findViewById(R.id.captureBtn);
        saveBtn = findViewById(R.id.saveBtn);

        saveBtn.setEnabled(false);

        captureBtn.setOnClickListener(v -> takePicture());
        saveBtn.setOnClickListener(v -> saveFace());

        if(checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }

        faceDetectorHelper = new FaceDetectorHelper(this, "blaze_face_short_range.tflite", (result, image)->{
            runOnUiThread(() -> {
                overlayView.setFaceBoxes(result, image.getWidth(), image.getHeight());
                isProcessing = false;
            });
        });
        faceRecognitionHelper = new FaceRecognitionHelper(this, "mobile_face_net.tflite");

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                camera2Helper = new Camera2Helper(FaceRegister.this, holder, new Size(640, 480), image -> {processImage(image);});
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

        if(faceDetectorHelper != null)
            faceDetectorHelper.detectAsync(bitmap, () -> isProcessing = false);

        if(!isCapturing){
            isProcessing = false;
            return;
        }
        
        float[] embedding = faceRecognitionHelper.getFaceEmbedding(bitmap);
        if(embedding != null){
            collectedEmbeddings.add(embedding);
            runOnUiThread(()-> Toast.makeText(this, "Face added " + collectedEmbeddings.size() + "/" + MAX_IMAGES, Toast.LENGTH_SHORT).show());
        }else {
            runOnUiThread(()-> Toast.makeText(this, "Face not found!!!", Toast.LENGTH_SHORT).show());
        }

        if(collectedEmbeddings.size() >= MAX_IMAGES){
            runOnUiThread(()->{
                captureBtn.setEnabled(false);
                saveBtn.setEnabled(true);
            });
        }
        isCapturing = false;
        isProcessing = false;
    }

    private void takePicture() {
        if(collectedEmbeddings.size() >= MAX_IMAGES){
            return;
        }

        isCapturing = true;
    }

    private void saveFace(){
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter Name", Toast.LENGTH_SHORT).show();
            return;
        }

        float[] avgEmbedding = faceRecognitionHelper.averageEmbedding(collectedEmbeddings);

        JsonDatabase db = new JsonDatabase(this);
        List<float[]> singleList = new ArrayList<>();
        singleList.add(avgEmbedding);
        db.saveEmbedding(name, singleList);
        Toast.makeText(this, "Face Save", Toast.LENGTH_LONG).show();
        finish();
    }



}