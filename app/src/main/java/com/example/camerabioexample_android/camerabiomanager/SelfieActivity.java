package com.example.camerabioexample_android.camerabiomanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Camera;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import androidx.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.example.camerabioexample_android.R;
import com.example.camerabioexample_android.camerabiomanager.bitmap.ImageUtils;
import com.example.camerabioexample_android.camerabiomanager.camera.CaptureImageProcessor;
import com.example.camerabioexample_android.camerabiomanager.camera.ImageProcessor;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import java.util.List;
import java.util.concurrent.TimeUnit;


public class SelfieActivity extends Camera2Base implements ImageProcessor, CaptureImageProcessor {

    public static final float COMPENSATION_EYE = 0.05f;
    public static int total = 0;
    private FirebaseVisionFaceDetector firebaseVisionFaceDetector;
    private FirebaseVisionImageMetadata metadata;
    private TextView countdownView;

    private final String[] mensagens = new String[] {
            "Centraliza o rosto",
            "Centraliza o rosto",//"Incline o celular para trás",
            "Centraliza o rosto",//"Incline o celular para frente",
            "Aproxime o rosto",
            "Afaste o rosto",
            "Gire um pouco a esquerda",
            "Gire um pouco a direita",
            "Rosto não identificado",
            "Rosto inclinado"} ;

    private int erroIndex = -1;
    private boolean faceOK = true;

    private View lineTopView;
    private View lineBottomView;
    private View lineLeftView;
    private View lineRightView;

    private ImageView rectangleImageView;
    private ImageButton takePictureImageButton;
    private Toast toast;

    private float posVerticalLineLeft = 0.0f;
    private float posVerticalLineRight = 0.0f;
    private float posHorizontalLineBottom = 0.0f;
    private float posHorizontalLineTop = 0.0f;

    private int primaryColor = Color.parseColor("#2980ff");

    // manter TRUE para exibir as linhas (deixar desabilitado)
    private boolean showLines = false;

    // variáveis do firebase
    private FirebaseVisionFace  firebaseVisionFace;
    private FirebaseVisionPoint leftEyePosition;
    private FirebaseVisionPoint rightEyePosition;
    private FirebaseVisionPoint nosePosition;
    private FirebaseVisionImage[] visionImage = new FirebaseVisionImage[1];

    // posição dos olhos
    private float leftEyePosX = 0f;
    private float leftEyePosY = 0f;
    private float rightEyePosX = 0f;
    private float rightEyePosY = 0f;
    private float nosePosY = 0f;

    float diffNose = 0f;
    float noseRange = 0f;

    // rotação da cabeça
    private float headPosition = 0f;

    // utilizado para calcular as linhas de base (showLines)
    float aspectRatioRelative = 0f;

    // diferença entre os olhos
    final float minDiffEye = 100f;
    final float maxDiffEye = 190f;
    float densityFactor = 2f;
    float densityMultiply = 2f;

    // área em % da tela permitida para enquadramento na horizontal
    float percentHorizontalRange = 25f;
    // área em % da tela permitida para enquadramento na vertical
    float percentVerticalRange = 30f;
    float percentOffsetVerticalRange = 30f;

    // altura da imagem no celular
    float screenWidth = 0f;
    float screenHeight = 0f;

    float aspectRatioBioEye = 1f;

    private GradientDrawable rectangleDrawable;

    private String origin = "";
    private boolean initialized = false;

    // contador
    private CountDownTimer countDownTimer;
    private Boolean[] countDownCancelled = new Boolean[] { Boolean.FALSE };
    private Boolean isRequestImage;
    private Boolean autoCapture;
    private Boolean countRegressive;


    private static CameraBioManager cameraBioManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        isFront = true;

        Bundle b = getIntent().getExtras();
//        cameraBioManager = (CameraBioManager) b.getParcelable("CLASS");
//
//        CallbackCameraBio callbackCameraBio = cameraBioManager.cbc;


        if (b != null) {
            origin = b.getString("origin");
        }

        if (DEBUG) Log.d(TAG, "from activity: " + origin);

        isRequestImage = false;

        super.activity = SelfieActivity.this;
        super.imageProcessor = this;
        super.captureImageProcessor = this;

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_selfie);


        takePictureImageButton = findViewById(R.id.take_picture);
        takePictureImageButton .setOnClickListener(this);
        textureView = findViewById(R.id.texture);
        rootView = findViewById(R.id.root_view);
        autoCapture = true;
        countRegressive = true;

        setMaxSizes();

        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                        .setMinFaceSize(1.0f)
                        .enableTracking()
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .build();

        firebaseVisionFaceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        countdownView = findViewById(R.id.tvCountdown);
        countdownView.setVisibility(View.INVISIBLE);

        rectangleImageView = findViewById(R.id.rectangle);
        rectangleDrawable = ((GradientDrawable) rectangleImageView.getBackground());

        lineTopView = findViewById(R.id.lineTop);
        lineBottomView = findViewById(R.id.lineBottom);
        lineLeftView = findViewById(R.id.lineLeft);
        lineRightView = findViewById(R.id.lineRight);

    }

    @Override
    public void onBackPressed() {
        countDownCancelled[0] = Boolean.TRUE;
        destroyTimer();
        activity.finish();
    }

    public void setCameraBioManager(CameraBioManager cameraBioManager) {
        this.cameraBioManager = cameraBioManager;
    }

    @Override
    public void process(byte[] image, final int w, final int h, int f) {

        if (!initialized && DEBUG) {
            Log.d(TAG, "width  buffer: " + w);
            Log.d(TAG, "height buffer: " + h);
        }

        if (countDownCancelled[0]) {
            return;
        }

        init(w, h);

        if (metadata == null) {
            metadata = new FirebaseVisionImageMetadata.Builder()
                    .setWidth(w)
                    .setHeight(h)
                    .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                    .setRotation(getImageFirebaseVisionRotation())
                    .build();
        }


        visionImage[0] = FirebaseVisionImage.fromByteArray(image, metadata);


        firebaseVisionFaceDetector
                .detectInImage(visionImage[0])
                .addOnSuccessListener(this, new OnSuccessListener<List<FirebaseVisionFace>>() {

                    @Override
                    public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {

                        visionImage[0] = null;

                        if (firebaseVisionFaces.size() > 0) {
                            firebaseVisionFace = firebaseVisionFaces.get(0);

                            leftEyePosition = firebaseVisionFace.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE).getPosition();
                            rightEyePosition = firebaseVisionFace.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE).getPosition();
                            nosePosition = firebaseVisionFace.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE).getPosition();

                            if (leftEyePosition != null && rightEyePosition != null) {

                                headPosition = firebaseVisionFace.getHeadEulerAngleY();

                                // Left Eye  -------------
                                leftEyePosX = (h - rightEyePosition.getX());
                                leftEyePosY = (rightEyePosition.getY());

                                // Right Eye -------------
                                rightEyePosX = (h - leftEyePosition.getX());
                                rightEyePosY = (leftEyePosition.getY());

                                nosePosY = nosePosition.getY();
                                // set to GC -------------
                                leftEyePosition = null;
                                rightEyePosition = null;
                                firebaseVisionFace = null;

                                faceOK = true;
                                erroIndex = -1;

                                // Distancia entre olhos
                                float diffEye = Math.abs(rightEyePosX - leftEyePosX);
                                diffEye = (diffEye * aspectRatioBioEye);

                                diffNose = (nosePosY - leftEyePosY);
                                noseRange = (diffEye / 3);
                                float maxDiffNose = (noseRange * (float)2.2);

                                if (DEBUG) {
                                    Log.d(TAG, "Entre olhos: " + diffEye);
                                    Log.d(TAG, "Olho esquerdo (X): " + leftEyePosX);
                                    Log.d(TAG, "Olho esquerdo (Y): " + leftEyePosY);
                                    Log.d(TAG, "Olho direito (X): " + rightEyePosX);
                                    Log.d(TAG, "Olho direito (Y): " + rightEyePosY);
                                }

                                // Olhos fora do enquadramento na horizontal
                                if (leftEyePosX < posVerticalLineLeft || rightEyePosX > posVerticalLineRight) {
                                    erroIndex = 0;
                                    faceOK = false;
                                }
                                // Olhos fora do enquadramento na vertical
                                else if (leftEyePosY < posHorizontalLineTop || leftEyePosY > posHorizontalLineBottom) {
                                    // olhos muito acima
                                    if (leftEyePosY < posHorizontalLineTop) {
                                        erroIndex = 1;
                                    }
                                    // olhos muito abaixo
                                    else {
                                        erroIndex = 2;
                                    }
                                    faceOK = false;
                                }
                                // Rosto muito próximo
                                else if (diffEye < minDiffEye) {
                                    erroIndex = 3;
                                    faceOK = false;
                                }
                                // Rosto muito afastado
                                else if (diffEye > maxDiffEye) {
                                    erroIndex = 4;
                                    faceOK = false;
                                }
                                // Vire a esquerda
                                else if (headPosition < -16) {
                                    erroIndex = 5;
                                    faceOK = false;
                                }
//                                // Vire a direita
                                else if (headPosition > 16) {
                                    erroIndex = 6;
                                    faceOK = false;
                                }
                                // rotação da cabeça (direita & esquerda)
                                else if (((Math.abs(rightEyePosY - leftEyePosY)) > 20) || ((Math.abs(leftEyePosY - rightEyePosY)) > 20)) {
                                    erroIndex = 8;
                                    faceOK = false;
                                }
                                // celular muito inclinado referente ao olhos
                                else if (diffNose < noseRange || diffNose > maxDiffNose) {
                                    //Log.d(TAG, "celular muito inclinado referente ao olhos");
                                    erroIndex = 0;
                                    faceOK = false;
                                }

                                if (faceOK) {
                                    markBlue();
                                    takePictureImageButton.setEnabled(true);
                                }
                                else {
                                    markRed();
                                    takePictureImageButton.setEnabled(false);
                                }
                                // exibe as grides em tela (caso ativo)
                                if (showLines) {
                                    addHorizontalLineBottom(posHorizontalLineBottom * aspectRatioRelative);
                                    addHorizontalLineTop((posHorizontalLineTop * aspectRatioRelative));
                                    addVerticalLineLeft(posVerticalLineLeft * aspectRatioRelative);
                                    addVerticalLineRight(posVerticalLineRight * aspectRatioRelative);
                                    showLines = false;
                                }
                            }
                            else {
                                erroIndex = 7;
                                markRed();
                                takePictureImageButton.setEnabled(false);
                            }
                        }
                        else {
                            erroIndex = 7;
                            markRed();
                            takePictureImageButton.setEnabled(false);
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                if (erroIndex != -1) {
                                    showFastToast(mensagens[erroIndex]);
                                } else if (toast != null) {
                                    toast.cancel();
                                }
                            }
                        });
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception ex) {
                        Log.d(TAG, "Erro: " + ex.toString());
                    }
                });
    }

    private void destroyTimer () {

        if (countDownTimer != null) {
            countdownView.setVisibility(View.INVISIBLE);
            countDownTimer.cancel();
            countDownTimer = null;
            countdownView.setText("3");
        }
    }

    private void createTimer () {

        if (countDownTimer == null && isRequestImage == false) {

            countdownView.setVisibility(View.VISIBLE);

            countDownTimer = new CountDownTimer(4000, 1000) {

                public void onTick(long millisUntilFinished) {
                    countDownCancelled[0] = Boolean.FALSE;
                    countdownView.setText("" + String.format("%d",
                            TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
                                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))));


                }

                public void onFinish() {
                    if (!countDownCancelled[0]) {
                        //isRunning = false;
                        isRequestImage = true;
                        destroyTimer();
                        takePicture();
                    }
                }

            };
            countDownTimer.start();
        }
    }

    private void autoCapture () {

        if(countDownTimer == null && isRequestImage == false) {

            countDownTimer = new CountDownTimer(2000, 1000) {

                public void onTick(long millisUntilFinished) {

                }

                public void onFinish() {
                    //isRunning = false;
                    isRequestImage = true;
                    destroyTimer();
                    takePicture();
                }

            }.start();
        }
    }

    public static float convertPixelsToDp(float px, Context context){
        return px / ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static float convertDpToPixel(float dp, Context context){
        return dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    private void markBlue() {

        erroIndex =-1;

        if (toast != null) {
            toast.cancel();
        }

        if (autoCapture && countRegressive && !countDownCancelled[0]) {
            createTimer();
        }
        else if (autoCapture) {
            autoCapture();
        }

        int size = 18;
        if (screenWidth > 1600) {
            size = 34;
        }
        rectangleDrawable.setStroke(size, primaryColor);
    }

    private void markRed() {
        destroyTimer();

        if (!countDownCancelled[0]) {
            int size = 18;
            if (screenWidth > 1600) {
                size = 34;
            }
            rectangleDrawable.setStroke(size, Color.RED);
        }
    }

    private void init(float widthBuffer, float heightBuffer) {

        if (!initialized) {

            // tela
            screenWidth = getWidthPixels();
            screenHeight = getHeightPixels();

            // construcao do aspect ratio (show lines)
            boolean isBufferPortrait = widthBuffer < heightBuffer;

            float aspectRatioBuffer = isBufferPortrait ? widthBuffer / heightBuffer : heightBuffer / widthBuffer;
            float imageHeightScreen = screenWidth / aspectRatioBuffer;
            float imageHeightBuffer = BIOMETRY_IMAGE_HEIGHT / aspectRatioBuffer;

            aspectRatioRelative = imageHeightScreen / (isBufferPortrait ? heightBuffer : widthBuffer);
            aspectRatioBioEye = imageHeightBuffer / (isBufferPortrait ? heightBuffer : widthBuffer);

            // validação para os casos em que o tamanho do buffer é maior que o esperado
            aspectRatioBioEye = (aspectRatioBioEye < 1f ? 1f : aspectRatioBioEye);

            if (DEBUG) Log.d(TAG, "Aspect Ratio BIO: " + aspectRatioBioEye);

            // linhas delimitadoras
            float refWidth = isBufferPortrait ? widthBuffer : heightBuffer;
            float refHeight = isBufferPortrait ? heightBuffer : widthBuffer;

            float topOffet = (refHeight * (percentOffsetVerticalRange) / 100);
            float heightRange = (refHeight * (percentVerticalRange) / 100);

            // densidade
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            densityFactor = metrics.density;

            posVerticalLineLeft = (refWidth * (percentHorizontalRange / 100));
            posVerticalLineRight = (refWidth * ((percentHorizontalRange / 100) * 3));
            posHorizontalLineTop = topOffet;
            posHorizontalLineBottom = topOffet + heightRange;
            initialized = true;
        }
    }
    private void addHorizontalLineBottom(float bottom) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams((int)(screenWidth), 10);
        layoutParams.setMargins(0,(int)bottom,0,0);
        lineBottomView.setLayoutParams(layoutParams);
    }

    private void addHorizontalLineTop(float top) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams((int)(screenWidth), 10);
        layoutParams.setMargins(0,(int)top,0,0);
        lineTopView.setLayoutParams(layoutParams);
    }

    private void addVerticalLineLeft(float left) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(10, (int)(screenHeight));
        layoutParams.setMargins((int)left,0,0,0);
        lineLeftView.setLayoutParams(layoutParams);
    }

    private void addVerticalLineRight(float right) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(10, (int)(screenHeight));
        layoutParams.setMargins((int)right,0,0,0);
        lineRightView.setLayoutParams(layoutParams);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.take_picture: {
                destroyTimer();
                countDownCancelled[0] = Boolean.TRUE;
                isRequestImage = true;
                super.takePicture();
                break;
            }
        }
    }

    @Override
    public void capture(String base64) {

        if (base64 != null) {
            this.cameraBioManager.captureDocument(base64);
            this.finish();
        } else {
            showErrorMessage("Erro ao recuperar imagem capturada");
        }
    }


    private void showErrorMessage(String message) {

        reopenCamera();

    }

    protected void showFastToast(final String message) {

        try {
            if (toast == null) {
                toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.TOP|Gravity.CENTER, 0, 0);
            }
            toast.setText(message);
            toast.show();
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
        }
    }
}