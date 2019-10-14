package com.example.camerabioexample_android;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.camerabioexample_android.camerabiomanager.CallbackCameraBio;
import com.example.camerabioexample_android.camerabiomanager.CameraBioManager;

public class MainActivity extends AppCompatActivity implements CallbackCameraBio {

    protected static final int REQUEST_CAMERA_PERMISSION = 1;
    Button bt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


       Button bt = findViewById(R.id.btNew);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraBioManager cb = new CameraBioManager(MainActivity.this);
                cb.startCamera();
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    public void onSuccessCaptureDocument(String base64) {

    }

    @Override
    public void onSuccessCapture(String base64) {
            bt.setText("PRONTO");
    }

    @Override
    public void onFailedCapture(String description) {

    }

}
