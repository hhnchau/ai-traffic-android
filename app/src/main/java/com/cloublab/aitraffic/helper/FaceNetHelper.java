package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import com.cloublab.aitraffic.model.FaceData;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class FaceNetHelper {
    private final Context context;
    private Interpreter interpreter;
    private final ImageProcessor imageProcessor;
    public FaceNetHelper(Context context, boolean useGpu){
        this.context = context;
        Interpreter.Options options = new Interpreter.Options();
        if (useGpu){
            //options.addDelegate();
        }else {
            options.setNumThreads(4);
        }
        options.setUseXNNPACK(true);
        options.setUseNNAPI(true);

        try {
            interpreter = new Interpreter(FileUtil.loadMappedFile(context, "facenet.tflite"), options);
        } catch (IOException e) {
            e.printStackTrace();
        }

        imageProcessor = (new ImageProcessor.Builder())
                        .add(new ResizeOp(160, 160, ResizeOp.ResizeMethod.BILINEAR))
                        .add(new StandardizeOp())
                        .build();

    }

    public float[] getFaceEmbedding(Bitmap bitmap){
        return runFaceNet(convertBitmapToBuffer(bitmap))[0];
    }

    private ByteBuffer convertBitmapToBuffer(Bitmap bitmap){
        return imageProcessor.process(TensorImage.fromBitmap(bitmap)).getBuffer();
    }

    private float[][] runFaceNet(Object inputs){
        long t1 = System.currentTimeMillis();
        float[][] outputs = new float[1][128];
        try {
            interpreter.run(inputs, outputs);
        }catch (Exception e){
            e.printStackTrace();
        }
        Log.e("[FACE-NET]", "Inference Speed in ms :" + (System.currentTimeMillis()-t1));
        return outputs;
    }

    private static class StandardizeOp implements TensorOperator{
        @Override
        public TensorBuffer apply(TensorBuffer input) {
            float[] pixels = input.getFloatArray();
            float sum = 0;
            for(float pixel: pixels){
                sum += pixel;
            }

            float mean = sum / pixels.length;
            float sumSquares = 0;
            for(float pixel: pixels){
                float diff = pixel - mean;
                sumSquares += diff * diff;
            }
            float std = (float)Math.sqrt(sumSquares / pixels.length);
            std = Math.max(std, 1f / (float) Math.sqrt(pixels.length));
            for(int i = 0; i < pixels.length; i++){
                pixels[i] = (pixels[i] - mean) / std;
            }
            TensorBuffer output = TensorBuffer.createFixedSize(input.getShape(), DataType.FLOAT32);
            output.loadArray(pixels);
            return output;
        }
    }

    public String compare(float[] embedding, ArrayList<FaceData> embeddings, String metric, float threshold){
        Map<String, ArrayList<Float>> nameScoreMap = new ArrayMap<>();

        for(FaceData faceData: embeddings){
            String name = faceData.name;
            float[] knownEmbedding = faceData.embedding;
            float score = metric.equals("cosine") ? cosineSimilarity(embedding, knownEmbedding) : l2Norm(embedding, knownEmbedding);
            nameScoreMap.computeIfAbsent(name, k -> new ArrayList<>()).add(score);
        }

        ArrayList<String> names = new ArrayList<>(nameScoreMap.keySet());
        ArrayList<Float> avgScores = new ArrayList<>();

        for(ArrayList<Float> scores : nameScoreMap.values()){
            float sum = 0;
            for(float s:scores) sum += s;
            avgScores.add(sum / scores.size());
        }

        if(metric.equals("cosine")){
            float max = Collections.max(avgScores);
            int index = avgScores.indexOf(max);
            return max >= threshold ? names.get(index) : "Unknown";
        }else {
            float min = Collections.min(avgScores);
            int index = avgScores.indexOf(min);
            return min <= threshold ? names.get(index) : "Unknown";
        }
    }

    private float cosineSimilarity( float[] x1 , float[] x2 ) {
        float dot = 0;
        float mag1 = 0;
        float mag2 = 0;
        for (int i = 0; i < x1.length; i++) {
            dot += x1[i] * x2[i];
            mag1 += x1[i] * x1[i];
            mag2 += x2[i] * x2[i];
        }
        mag1 = (float) Math.sqrt(mag1);
        mag2 = (float) Math.sqrt(mag2);
        return dot / (mag1 * mag2);
    }

    private float l2Norm( float[] x1, float[] x2 ) {
        float sum = 0;
        for (int i = 0; i < x1.length; i++) {
            sum += Math.pow((x1[i] - x2[i]), 2);
        }
        return (float) Math.sqrt(sum);
    }

    @SuppressWarnings("unchecked")
    public ArrayList<FaceData> readEmbedding(){
        ArrayList<FaceData> data = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), "embedding_data.ser");
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
            data = (ArrayList<FaceData>)inputStream.readObject();
            inputStream.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return  data;
    }
    public void writeEmbedding(ArrayList<FaceData> data){
        try {
            File file = new File(context.getFilesDir(), "embedding_data.ser");
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(data);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void writeEmbeddings(String name, ArrayList<float[]> embeddings){
        ArrayList<FaceData> existData = new ArrayList<>();
        File file = new File(context.getFilesDir(), "embedding_data.ser");

        if(file.exists()){
            try{
                ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
                existData = (ArrayList<FaceData>) inputStream.readObject();
                inputStream.close();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        for (float[] emb: embeddings){
            existData.add(new FaceData(name, emb));
        }

        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(existData);
            outputStream.flush();
            outputStream.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
