package com.cloublab.aitraffic;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.WindowManager;

import com.cloublab.aitraffic.helper.Camera2TextureHelper;

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
import java.util.List;
import java.util.Objects;

public class TensorOpen extends AppCompatActivity {
    private static final String TAG = "AI-OPENCV";
    private TextureView textureView;
    private Camera2TextureHelper camera2TextureHelper;
    private boolean isProcessing = false;

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
                    String cameraId = id;
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
        camera2TextureHelper = new Camera2TextureHelper(this, textureView, new android.util.Size(640, 480), this::processImage);
        camera2TextureHelper.start(true);
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

    private void processImage(Image image){
        if (isProcessing) {
            image.close();
            return;
        }
        isProcessing = true;
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

}
