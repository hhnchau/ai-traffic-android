package com.cloublab.aitraffic.helper;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonDatabase {
    private SharedPreferences prefs;

    public JsonDatabase(Context context) {
        prefs = context.getSharedPreferences("FaceData", MODE_PRIVATE);
    }

    public void saveEmbedding(String name, List<float[]> embeddingsList) {
        try {
            String jsonString = prefs.getString("face_json", null);
            JSONArray users;
            if (jsonString != null) {
                JSONObject root = new JSONObject(jsonString);
                users = root.getJSONArray("users");
            } else {
                users = new JSONArray();
            }
            JSONObject userObj = new JSONObject();
            userObj.put("name", name);

            JSONArray embedArray = new JSONArray();
            for (float[] embedding : embeddingsList) {
                JSONArray single = new JSONArray();
                for(float v: embedding) {
                    single.put(v);
                }
                embedArray.put(single);
            }

            userObj.put("embeddings", embedArray);
            users.put(userObj);

            JSONObject root = new JSONObject();
            root.put("users", users);

            prefs.edit().putString("face_json", root.toString()).apply();
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public String loadEmbedding() {
        return prefs.getString("face_json", null);
    }

    public Map<String, float[]> loadEmbeddings() {
        Map<String, float[]> map = new HashMap<>();
        try {
            String jsonString = prefs.getString("face_json", null);
            if(jsonString == null) return map;

            JSONObject root = new JSONObject(jsonString);
            JSONArray users = root.getJSONArray("users");

            for(int i = 0; i< users.length();i++){
                JSONObject user = users.getJSONObject(i);
                String name = user.getString("name");
                JSONArray embedArray = user.getJSONArray("embeddings");

                List<float[]> vectors = new ArrayList<>();

                for(int j = 0; j < embedArray.length(); j++){
                    JSONArray vecArray = embedArray.getJSONArray(j);
                    float[] vec = new float[vecArray.length()];
                    for(int k = 0; k < vecArray.length(); k++){
                        vec[k] = (float) vecArray.getDouble(k);
                    }
                    vectors.add(vec);
                }
                float[] average = averageEmbedding(vectors);
                map.put(name, average);
            }

        }catch (Exception e){
            e.printStackTrace();
        }

        return map;
    }

    public String findNearestFace(float[] embedding, float threshold){
        Map<String, float[]> database = loadEmbeddings();
        String bestMatch = "Unknown";
        float minDistance = Float.MAX_VALUE;

        for (Map.Entry<String, float[]>entry: database.entrySet()){
            float dist = cosineDistance(entry.getValue(), embedding);
            if(dist < minDistance && dist < threshold){
                minDistance = dist;
                bestMatch = entry.getKey();
            }
        }
        return bestMatch;
    }

    private float cosineDistance (float[] a, float[] b){
        float dot = 0f, normA = 0f, normB = 0f;
        for(int i = 0; i < a.length; i++){
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return 1 - (dot / (float)(Math.sqrt(normA) * Math.sqrt(normB)));
    }

    public float[] averageEmbedding(List<float[]> embeddings){
        if(embeddings.isEmpty()) return new float[0];
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

}