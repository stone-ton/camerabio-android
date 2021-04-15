package com.example.camerabioexample_android;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.camerabioandroid.camerabiomanager.CallbackCameraBio;
import com.example.camerabioandroid.camerabiomanager.CameraBioManager;

public class MainActivity extends AppCompatActivity implements CallbackCameraBio {

    Activity act;
    CameraBioManager cb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        act = this;

        Button bt = findViewById(R.id.btNew);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cb = new CameraBioManager(MainActivity.this, act);
                cb.startCameraDocument(501);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onSuccessCaptureDocument(String base64) {
        Log.w("CALAZANS", "onSuccessCaptureDocument");
        Toast.makeText(this, "onSuccessCaptureDocument", Toast.LENGTH_SHORT);
        cb.stopCamera();
    }

    @Override
    public void onSuccessCapture(String base64) {
        Log.w("CALAZANS", "onSuccessCapture");
        Toast.makeText(this, "onSuccessCapture", Toast.LENGTH_SHORT);
        cb.stopCamera();
    }

    @Override
    public void onFailedCapture(String description) {
        Log.w("CALAZANS", "onFailedCapture");
        Toast.makeText(this, "onFailedCapture", Toast.LENGTH_SHORT);
        cb.stopCamera();
    }
}
