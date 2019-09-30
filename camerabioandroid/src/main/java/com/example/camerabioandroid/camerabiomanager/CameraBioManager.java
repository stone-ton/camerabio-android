package com.example.camerabioandroid.camerabiomanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CameraBioManager {

    protected static final int REQUEST_CAMERA_PERMISSION = 1;


    CallbackCameraBio cbc;
    Activity context;

    protected SelfieActivity sAc;
    protected DocumentActivity dAc;

    public static final int RG_FRENTE = 501;
    public static final int RG_VERSO = 502;
    public static final int CNH = 4;
    public static final int NONE = 99;

    public CameraBioManager(CallbackCameraBio callbackBioCamera) {

        this.cbc = callbackBioCamera;
        context = (Activity) this.cbc;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(context, new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CAMERA_PERMISSION);
            return;
        }

    }

    public void startCamera(){

        sAc = new SelfieActivity();
        sAc.setCameraBioManager(this);
        Intent intent = new Intent(context, sAc.getClass());
        context.startActivity(intent);

    }

    public void startCameraDocument(int DOCUMENT_TYPE) {

        dAc = new DocumentActivity();
        dAc.setCameraBioManager(this);
        Intent intent = new Intent(context, dAc.getClass());
        intent.putExtra("DOCUMENT_TYPE", DOCUMENT_TYPE);
        context.startActivity(intent);

    }

    public void capture(String base64) {

        cbc.onSuccessCapture(base64);
        sAc.finish();


    }

    public void captureDocument(String base64) {

        cbc.onSuccessCaptureDocument(base64);
        dAc.finish();

    }

}
