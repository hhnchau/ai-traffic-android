package com.cloublab.aitraffic.model;

import java.io.Serializable;

public class FaceData implements Serializable {
    public String name;
    public float[] embedding;

    public FaceData(String name, float[] embedding) {
        this.name = name;
        this.embedding = embedding;
    }
}
