package com.cloublab.aitraffic;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TensorOpen extends AppCompatActivity {
    private static final String TAG = "AI-OPENCV";
    private TextureView textureView;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private CameraManager cameraManager;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tensor_open);

        textureView = findViewById(R.id.texture);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 1);
        }else {
            // Setup Camera
            setupCamera();
        }

        Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if(textureView != null){
                    Log.e(TAG, "[---GET-TEXTURE---]");
                    //Bitmap bitmap = textureView.getBitmap();
                    //processImage(bitmap);
                }
                handler.postDelayed(this,2000);
            }
        };
        handler.post(runnable);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                // Setup Camera
                setupCamera();
            }else {
                // Permission denied
                Log.e(TAG, "Permission denied");
            }
        }
    }

    private void initResource(CameraManager cameraManager){

        try {
            for(String id: cameraManager.getCameraIdList()){
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(Objects.equals(lensFacing, CameraCharacteristics.LENS_FACING_BACK)){
                    cameraId = id;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if(OpenCVLoader.initLocal()){
            Log.e(TAG, "Success");
        }else {
            Log.e(TAG, "Fail");
        }

        try {
            Interpreter tflite = new Interpreter(loadTfModel());
            Log.d(TAG, "Model loaded successfully.");
            List<String> labels = FileUtil.loadLabels(this, "labels.txt");
            Log.d(TAG, "Labels loaded successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupCamera(){
        if(cameraManager == null){
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        }

        if(textureView.isAvailable()){
            //Open Camera
            openCamera();
        }else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "SurfaceTexture is now available, opening camera...");
                    // Open Camera
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "SurfaceTexture is changed...");
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    Log.d(TAG, "SurfaceTexture is destroy...");
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                    //Log.d(TAG, "SurfaceTexture is update...");
                }
            });
        }
    }

    private void openCamera(){
        if(backgroundHandler == null){
            startBackgroundThread();
        }

        if(cameraManager == null){
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        }

        try {

            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                return;
            }
            cameraManager.openCamera(cameraManager.getCameraIdList()[0], new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    createCameraPreview(camera);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                }
            }, backgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreview(CameraDevice cameraDevice){
        if(backgroundHandler == null){
            startBackgroundThread();
        }
        SurfaceTexture texture = textureView.getSurfaceTexture();
        if(texture != null) {
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);
            try {
                CaptureRequest.Builder  captureRequestBuild = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuild.addTarget(surface);

                cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback(){

                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.setRepeatingRequest(captureRequestBuild.build(), null, backgroundHandler);
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
    }

    private MappedByteBuffer loadTfModel() throws IOException {
        try (AssetFileDescriptor fileDescriptor = getAssets().openFd("model_trained.tflite");
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())){
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    private void startBackgroundThread(){
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    private void stopBackgroundThread(){
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void processImage(Bitmap bitmap){
        if(bitmap == null){
            Log.e(TAG, "Bitmap is null");
            return;
        }

        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);

        Imgproc.equalizeHist(mat, mat);

        Mat resizeMat = new Mat();
        Imgproc.resize(mat, resizeMat, new Size(32,32));

        resizeMat.convertTo(resizeMat, CvType.CV_32F, 1.0/255);

        float[][][][] input = new float[1][32][32][1];
        for(int i = 0; i < 32; i++){
            for(int j = 0; j < 32;j++){
                input[0][i][j][0]= (float) resizeMat.get(i,j)[0];
            }
        }

//        float [][] output = new float[1][label.size()];
//        Interpreter tflite = null; //new Interpreter(loadTfModel());
//        tflite.run(input, output);
//
//        int classIndex = getMaxIndex(output[0]);
//        float probability = output[0][classIndex];
//
//        if(probability > 1){
//            // Valid
//            String label = labels.get(classIndex);
//        }else {
//            // Invalid
//        }



    }

    private int getMaxIndex(float[] array){
        int maxIndex = 0;
        float maxValue = array[0];
        for(int i = 1; i < array.length; i++){
            if(array[i] > maxValue){
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBackgroundThread();
    }
}
