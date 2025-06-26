package com.cloublab.aitraffic.helper;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class JsonDatabase {
    private SharedPreferences prefs;

    public JsonDatabase(Context context) {
        prefs = context.getSharedPreferences("FaceData", MODE_PRIVATE);
    }

    public void saveEmbedding(String name, float[] embedding) {
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
            for (float v : embedding) {
                embedArray.put(v);
            }

            userObj.put("embedding", embedArray);
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
}