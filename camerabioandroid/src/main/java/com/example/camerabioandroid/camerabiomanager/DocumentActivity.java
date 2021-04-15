package com.example.camerabioandroid.camerabiomanager;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
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

import androidx.annotation.NonNull;


import com.example.camerabioandroid.R;
import com.example.camerabioandroid.camerabiomanager.camera.CaptureImageProcessor;
import com.example.camerabioandroid.camerabiomanager.camera.ImageProcessor;
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


public class DocumentActivity extends Camera2DocumentBase implements ImageProcessor, CaptureImageProcessor {

    public static final float COMPENSATION_EYE = 0.05f;
    public static int total = 0;

    private TextView countdownView;


    private int erroIndex = -1;
    private boolean faceOK = true;

    private View lineTopView;
    private View lineBottomView;
    private View lineLeftView;
    private View lineRightView;

    private ImageView rectangleImageView;
    private ImageButton takePictureImageButton;
    private Toast toast;

    private ImageView ivMask;

    private float posVerticalLineLeft = 0.0f;
    private float posVerticalLineRight = 0.0f;
    private float posHorizontalLineBottom = 0.0f;
    private float posHorizontalLineTop = 0.0f;

    private int primaryColor = Color.parseColor("#2980ff");

    // manter TRUE para exibir as linhas (deixar desabilitado)
    private boolean showLines = false;

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


    private Boolean isRequestImage;

    private static CameraBioManager cameraBioManager;

    private int DOCUMENT_TYPE;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Bundle b = getIntent().getExtras();
        this.DOCUMENT_TYPE = b.getInt("DOCUMENT_TYPE");

        if (b != null) {
            origin = b.getString("origin");
        }

        if (DEBUG) Log.d(TAG, "from activity: " + origin);

        isRequestImage = false;

        super.activity = DocumentActivity.this;
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

        ivMask  = findViewById(R.id.iv_mask);

        if(DOCUMENT_TYPE == 501) {
            ivMask.setImageResource(R.drawable.frame_rg_frente);
            ivMask.setVisibility(View.VISIBLE);
        }else if(DOCUMENT_TYPE == 502) {
            ivMask.setImageResource(R.drawable.frame_rg_verso);
            ivMask.setVisibility(View.VISIBLE);
        }else if(DOCUMENT_TYPE == 4) {
            ivMask.setImageResource(R.drawable.frame_cnh);
            ivMask.setVisibility(View.VISIBLE);
        }else{
            ivMask.setVisibility(View.GONE);
        }

        countdownView = findViewById(R.id.tvCountdown);
        countdownView.setVisibility(View.GONE);

        setMaxSizes();


        rectangleImageView = findViewById(R.id.rectangle);
        rectangleDrawable = ((GradientDrawable) rectangleImageView.getBackground());


    }

    @Override
    public void onBackPressed() {
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

        init(w, h);


    }


    public static float convertPixelsToDp(float px, Context context){
        return px / ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static float convertDpToPixel(float dp, Context context){
        return dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
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
        Log.i("SPRENGEL", "com.example.camerabioandroid.camerabiomanager.DocumentActivity: onClick");
        if (view.getId() == R.id.take_picture) {
            Log.i("SPRENGEL", "onClick: take_picture");
            isRequestImage = true;
            super.takePicture();
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