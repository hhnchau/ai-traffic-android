package com.cloublab.aitraffic.helper;

import android.content.Context;
import android.graphics.Bitmap;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.List;

public class FaceRecognitionHelper {
    private final Interpreter tflite;

    public FaceRecognitionHelper(Context context, String modelFile) {
        try {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, modelFile);
            tflite = new Interpreter(tfliteModel);
        } catch (IOException e) {
            throw new RuntimeException("Error reading model", e);
        }
    }

    public float[] averageEmbedding(List<float[]> embeddings){
        int length = embeddings.get(0).length;
        float[] avg = new float[length];
        for(float[] emb: embeddings){
            for(int i = 0; i < length; i++){
                avg[i] += emb[i];
            }
        }
        for(int i = 0; i < length; i++){
            avg[i] /= embeddings.size();
        }
        return avg;
    }

    public float[] getFaceEmbedding(Bitmap bitmap) {
        int inputSize = 112;
        int outputSize = 192;
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
        ByteBuffer input = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder());

        for (int y = 0; y < inputSize; y++) {
            for (int x = 0; x < inputSize; x++) {
                int pixel = resized.getPixel(x, y);
                float r = ((pixel >> 16) & 0xFF);
                float g = ((pixel >> 8) & 0xFF);
                float b = (pixel & 0xFF);

                // Normalize to [-1, 1]
                input.putFloat((r-127.5f)/128f);
                input.putFloat((g-127.5f)/128f);
                input.putFloat((b-127.5f)/128f);
            }
        }

        float[][] output = new float[1][outputSize];
        tflite.run(input, output);
        return l2Normalize(output[0]);
    }

    private float[] l2Normalize(float[] embedding){
        float norm = 0f;
        for(float val: embedding){
            norm += val * val;
        }
        norm = (float) Math.sqrt(norm);
        float[] result = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++){
            result[i] = embedding[i] / norm;
        }
        return result;
    }
}