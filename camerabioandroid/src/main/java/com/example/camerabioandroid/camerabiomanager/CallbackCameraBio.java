package com.example.camerabioandroid.camerabiomanager;

import java.io.Serializable;

public interface CallbackCameraBio extends Serializable {

    public void onSuccessCapture(String base64);
    public void onFailedCapture(String description);

}
