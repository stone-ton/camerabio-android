package com.example.camerabioexample_android.camerabiomanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CameraBioManager implements Parcelable {

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

    protected CameraBioManager(Parcel in) {
    }

    public static final Creator<CameraBioManager> CREATOR = new Creator<CameraBioManager>() {
        @Override
        public CameraBioManager createFromParcel(Parcel in) {
            return new CameraBioManager(in);
        }

        @Override
        public CameraBioManager[] newArray(int size) {
            return new CameraBioManager[size];
        }
    };

    public void startCamera(){

        SelfieActivity sAc = new SelfieActivity();
        sAc.setCameraBioManager(this);

        Bundle bundle = new Bundle();
        bundle.putParcelable("CLASS", this);

        Intent intent = new Intent(context, sAc.getClass());
        intent.putExtras(bundle);

        context.startActivityForResult(intent, 1);
    }

    public void capture(String base64) {

        cbc.onSuccessCapture(base64);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(cbc);
        dest.writeValue(context);
    }


}
