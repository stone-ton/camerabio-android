package com.example.camerabioexample_android.camerabiomanager;

import java.io.Serializable;

public interface CallbackCameraBio extends Serializable {

    public void onSuccessCapture(String base64);
    public void onSuccessCaptureDocument(String base64);
    public void onFailedCapture(String description);

}
