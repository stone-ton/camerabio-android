package com.example.camerabioexample_android.camerabiomanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.Serializable;

public class CameraBioManager  {

    protected static final int REQUEST_CAMERA_PERMISSION = 1;


    CallbackCameraBio cbc;

    Activity context;

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
        Intent intent = new Intent(context, SelfieActivity.class);
        context.startActivityForResult(intent, 1);
    }


}
