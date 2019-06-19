package com.example.camerabioexample_android.camerabiomanager;

public interface CallbackCameraBio {

    public void onSuccessCapture(String base64);
    public void onFailedCapture(String description);

}
