package com.cloublab.aitraffic;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
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
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.cloublab.aitraffic.helper.FaceRecognitionHelper;
import com.cloublab.aitraffic.helper.JsonDatabase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FaceRecognition extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private List<FaceData> knownFaces;

    private FaceRecognitionHelper faceHelper;
    private FaceDetector detector;

    private Handler backgroundHandler;

    private long lastDetectTime = 0;
    private static final long DETECT_INTERVAL = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognition);

        faceHelper = new FaceRecognitionHelper(this);

        textureView = findViewById(R.id.textureView);

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            setupModelAndCamera();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraDevice != null) cameraDevice.close();
        if(imageReader != null) imageReader.close();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupModelAndCamera();
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
        }
    }


    private void setupModelAndCamera() {
        detector = FaceDetection.getClient(
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build());

        knownFaces = loadKnownFacesFromJson();

        HandlerThread thread = new HandlerThread("CameraBackground");
        thread.start();
        backgroundHandler = new Handler(thread.getLooper());

        startCamera();
    }

    private void startCamera() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return false; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = null;
            for (String id: cameraManager.getCameraIdList()){
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT){
                    cameraId = id;
                    break;
                }
            }

            if(cameraId == null) return;

            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {

                Image image = reader.acquireLatestImage();
                if (image != null) {
                    /// Handle Image
                    if(System.currentTimeMillis() - lastDetectTime > DETECT_INTERVAL){
                        lastDetectTime = System.currentTimeMillis();
                        Bitmap bitmap = convertYUVToBitmap(image);
                        bitmap = rotateBitmap(bitmap, 270, true);
                        detectAndRecognize(bitmap);
                    }
                    image.close();
                }
            }, backgroundHandler);

            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCaptureSession();
                    }
                    @Override public void onDisconnected(@NonNull CameraDevice camera) {}
                    @Override public void onError(@NonNull CameraDevice camera, int error) {}
                }, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCaptureSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(640, 480);
            Surface previewSurface = new Surface(texture);
            Surface imageSurface = imageReader.getSurface();
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                builder.addTarget(previewSurface);
                                builder.addTarget(imageSurface);
                                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) { e.printStackTrace(); }
                        }

                        @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Bitmap convertYUVToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] jpegBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees, boolean isFrontCamera) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        if (isFrontCamera) {
            matrix.postScale(-1, 1);
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void detectAndRecognize(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image).addOnSuccessListener(faces -> {
            if(faces.size() > 0)Log.d("FACE_RECOGNITION", "=================" + faces.size());
            for (Face face : faces) {
                Rect bounds = face.getBoundingBox();

                int x = Math.max(bounds.left, 0);
                int y = Math.max(bounds.top, 0);
                int width = Math.min(bounds.width(), bitmap.getWidth() - x);
                int height = Math.min(bounds.height(), bitmap.getHeight() - y);
                if(width > 0 && height > 0 && x + width <= bitmap.getWidth() && y + height <= bitmap.getHeight()){
                    Bitmap faceBitmap = Bitmap.createBitmap(bitmap, x,y,width, height);
                    float[] embedding = faceHelper.getFaceEmbedding(faceBitmap);
                    String name = compareWithKnownFaces(embedding);
                    Log.d("FACE_RECOGNITION", Arrays.toString(embedding));
                    Log.d("FACE_RECOGNITION", "Matched: " + name);
                    if(!name.equals("Unknown"))Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
                    faceBitmap.recycle();
                }
            }
            bitmap.recycle();
        }).addOnFailureListener(e -> {
            Log.e("FACE_RECOGNITION", "Lỗi ML Kit", e);
            bitmap.recycle();
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        });
    }


    private String compareWithKnownFaces(float[] embedding) {
        float bestScore = -1;
        String bestName = "Unknown";
        for (FaceData face : knownFaces) {
            float score = cosineSimilarity(embedding, face.embedding);
            Log.d("FACE_RECOGNITION", "Score: " + score);
            if (score > bestScore && score > 0.58f) {
                bestScore = score;
                bestName = face.name;
            }
        }
        return bestName;
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<FaceData> loadKnownFacesFromJson() {
        List<FaceData> list = new ArrayList<>();
        try {
            JsonDatabase pref = new JsonDatabase(this);
            String jsonString = pref.loadEmbedding();
            if(jsonString == null) return list;

            JSONObject root = new JSONObject(jsonString);
            JSONArray users = root.getJSONArray("users");

            for (int i = 0; i < users.length(); i++) {
                JSONObject obj = users.getJSONObject(i);
                String name = obj.getString("name");
                JSONArray embed = obj.getJSONArray("embedding");
                float[] emb = new float[embed.length()];
                for (int j = 0; j < embed.length(); j++){
                    emb[j] = (float) embed.getDouble(j);
                }
                list.add(new FaceData(name, emb));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    static class FaceData {
        String name;
        float[] embedding;
        FaceData(String name, float[] embedding) {
            this.name = name;
            this.embedding = embedding;
        }
    }

}
