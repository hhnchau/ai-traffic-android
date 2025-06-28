package com.cloublab.aitraffic;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.cloublab.aitraffic.helper.FaceRecognitionHelper;
import com.cloublab.aitraffic.helper.JsonDatabase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FaceRegister extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 100;
    private static final int MAX_IMAGES = 5;

    private TextureView textureView;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private FaceRecognitionHelper faceHelper;
    private List<float[]> collectedEmbeddings = new ArrayList<>();
    private FaceDetector detector;
    private EditText nameInput;
    private Button captureBtn, saveBtn;
    private int imageCount = 0;
    private CameraCaptureSession cameraSession;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_register);

        faceHelper = new FaceRecognitionHelper(this);
        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        );

        textureView = findViewById(R.id.textureView);
        nameInput = findViewById(R.id.nameInput);
        captureBtn = findViewById(R.id.captureBtn);
        saveBtn = findViewById(R.id.saveBtn);

        saveBtn.setEnabled(false);

        captureBtn.setOnClickListener(v -> takePicture());
        saveBtn.setOnClickListener(v -> saveFace());

        if(checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }else {
            startCamera();
        }
    }

    private void startCamera() {
        HandlerThread thread = new HandlerThread("CameraBackground");
        thread.start();
        backgroundHandler = new Handler(thread.getLooper());

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String frontCameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id;
                    break;
                }
            }

            if (frontCameraId == null) return;

            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            manager.openCamera(frontCameraId, new CameraDevice.StateCallback() {

                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {

                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {

                }
            }, backgroundHandler);

        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void createSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(640, 480);
            Surface previewSurface = new Surface(texture);
            Surface imageSurface = imageReader.getSurface();

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageSurface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraSession = session;
                    try {
                        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(previewSurface);
                        session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        if (cameraDevice == null || imageReader == null || cameraSession == null) return;

        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireNextImage();
                if (image != null) {
                    Bitmap bitmap = convertYUVToBitmap(image);
                    bitmap = rotateBitmap(bitmap, 270, true);
                    detectFace(bitmap);
                    image.close();
                }
            }, backgroundHandler);

            cameraSession.capture(captureBuilder.build(), null, backgroundHandler);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void detectFace(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image).addOnSuccessListener(faces -> {
            if (!faces.isEmpty()) {
                Face face = faces.get(0);
                Rect bounds = face.getBoundingBox();

                int x = Math.max(bounds.left, 0);
                int y = Math.max(bounds.top, 0);
                int w = Math.max(bounds.width(), bitmap.getWidth() - x);
                int h = Math.max(bounds.height(), bitmap.getHeight() - y);

                if (w > 0 && h > 0 && x + w <= bitmap.getWidth() && y + h <= bitmap.getHeight()) {
                    Bitmap faceBitmap = Bitmap.createBitmap(bitmap, x, y, w, h);
                    float[] emb = faceHelper.getFaceEmbedding(faceBitmap);
                    collectedEmbeddings.add(emb);
                    imageCount++;
                    Toast.makeText(this, "Đã chụp " + imageCount + "/" + MAX_IMAGES, Toast.LENGTH_SHORT).show();
                    faceBitmap.recycle();

                    if (imageCount >= MAX_IMAGES) {
                        captureBtn.setEnabled(false);
                        saveBtn.setEnabled(true);
                    }
                }
            } else {
                Toast.makeText(this, "Không tìm thấy khuôn mặt!", Toast.LENGTH_SHORT).show();
            }
            bitmap.recycle();
        }).addOnFailureListener(e -> {
            e.printStackTrace();
            bitmap.recycle();
        });
    }

    private void saveFace() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Nhập tên!", Toast.LENGTH_SHORT).show();
            return;
        }
        JsonDatabase db = new JsonDatabase(this);
        db.saveEmbedding(name, collectedEmbeddings);
        Toast.makeText(this, "Đã lưu khuôn mặt!", Toast.LENGTH_LONG).show();
        finish();
    }

    private Bitmap convertYUVToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer y = planes[0].getBuffer();
        ByteBuffer u = planes[1].getBuffer();
        ByteBuffer v = planes[2].getBuffer();

        int ySize = y.remaining();
        int uSize = u.remaining();
        int vSize = v.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        y.get(nv21, 0, ySize);
        v.get(nv21, ySize, vSize);
        u.get(nv21, ySize + vSize, uSize);

        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0,0, image.getWidth(), image.getHeight()), 100, out);
        byte[] jpegBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int angle, boolean mirror) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        if (mirror) matrix.postScale(-1, 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

}