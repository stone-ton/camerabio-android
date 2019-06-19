package com.example.camerabioexample_android.camerabiomanager.camera;

public interface ImageProcessor {

    void process(byte[] image, int w, int h, int f);
}
