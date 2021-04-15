package com.example.camerabioexample_android.camerabiomanager;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.example.camerabioexample_android.R;
import com.example.camerabioexample_android.camerabiomanager.bitmap.ImageUtils;
import com.example.camerabioexample_android.camerabiomanager.camera.AutoFitTextureView;
import com.example.camerabioexample_android.camerabiomanager.camera.CaptureImageProcessor;
import com.example.camerabioexample_android.camerabiomanager.camera.ImageProcessor;
import com.example.camerabioexample_android.camerabiomanager.camera.ImageSize;
import com.example.camerabioexample_android.camerabiomanager.exif.Exif;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Camera2Base extends BaseActivity implements View.OnClickListener {

    protected Boolean DEBUG = true;
    protected static Activity activity;
    protected static ImageProcessor imageProcessor;
    protected static CaptureImageProcessor captureImageProcessor;

    protected static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    protected static final String FRAGMENT_DIALOG = "dialog";
    protected Integer facing;
    protected int screenOrientation = Surface.ROTATION_90;

    // tamanho ideal da imagem da biometria
    protected static final int BIOMETRY_IMAGE_WIDTH = 1280;
    protected static final int BIOMETRY_IMAGE_HEIGHT = 720;

    protected static final float PROPORTION_BIOMETRY = ((float)BIOMETRY_IMAGE_WIDTH / (float)BIOMETRY_IMAGE_HEIGHT);

    // tamanho minimo da biometria
    protected static final int BIOMETRY_IMAGE_WIDTH_MIN = 640;
    protected static final int BIOMETRY_IMAGE_HEIGHT_MIN = 360;

    protected Size jpegCaptureSize = new Size(BIOMETRY_IMAGE_WIDTH, BIOMETRY_IMAGE_HEIGHT);

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    protected static final String TAG = "Camera2Base";

    protected static final int STATE_PREVIEW = 0;
    protected static final int STATE_WAITING_LOCK = 1;
    protected static final int STATE_WAITING_PRECAPTURE = 2;
    protected static final int STATE_WAITING_NON_PRECAPTURE = 3;
    protected static final int STATE_PICTURE_TAKEN = 4;

    protected int SCREEN_WIDTH = 0;
    protected int SCREEN_HEIGHT = 0;
    protected float ASPECT_RATIO_ERROR_RANGE = 0.1f;

    protected String cameraId;
    protected AutoFitTextureView textureView;

    protected CameraCaptureSession captureSession;
    protected CameraDevice cameraDevice;
    protected CameraManager cameraManager;

    protected Size previewSize;
    protected Size jpegSize;

    protected HandlerThread backgroundThread;

    protected Handler backgroundHandler;
    protected Handler backgroundHandlerFace;

    protected ImageReader imageReader;
    protected ImageReader imageReaderFace;

    protected CaptureRequest.Builder previewRequestBuilder;
    protected int state = STATE_PREVIEW;
    protected Semaphore cameraOpenCloseLock = new Semaphore(1);
    protected int sensorOrientation;
    private final Object surfaceTextureLock = new Object();
    private final Object dimensionLock = new Object();
    private SurfaceTexture previewSurfaceTexture;


    protected final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            if (DEBUG) { Log.d(TAG, "Surface size change"); }
            configureTransform(width, height);
            updatePreviewBufferSize();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            if (DEBUG) { Log.d(TAG, "Surface texture update"); }
            updatePreviewBufferSize();
        }

    };

    private void updatePreviewBufferSize() {
        if (DEBUG) { Log.d(TAG, "Update preview buffer size"); }

        synchronized (surfaceTextureLock) {
            if (previewSurfaceTexture != null) {
                previewSurfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            }
        }
    }

    protected final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            if (DEBUG) { Log.d(TAG, "Camera opened"); }
            cameraOpenCloseLock.release();
            Camera2Base.this.cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            if (DEBUG) { Log.d(TAG, "Camera disconnected"); }
            cameraOpenCloseLock.release();
            cameraDevice.close();
            Camera2Base.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            if (DEBUG) { Log.d(TAG, "Camera error"); }
            cameraOpenCloseLock.release();
            cameraDevice.close();
            Camera2Base.this.cameraDevice = null;
            finish();
        }

    };

    protected final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            backgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
        }

    };

    protected int CURR_IMAGE_COUNT = 0;

    protected static final Object lock = new Object();

    private final Lock imageReaderLock = new ReentrantLock(true /*fair*/);

    protected final ImageReader.OnImageAvailableListener onImageFaceAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            imageReaderLock.lock();

            try {
                Image image = reader.acquireLatestImage();

                if (image == null) {
                    return;
                }
                CURR_IMAGE_COUNT++;

                if (CURR_IMAGE_COUNT % 3 != 0) {
                    image.close();
                    return;
                }
                else {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    final byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    if (DEBUG && CURR_IMAGE_COUNT % 20 == 0) {
                        byte[] jpegData = ImageUtils.YUV420ToJPEG(image);
                        saveReceivedImage(jpegData, jpegData.length, String.valueOf(CURR_IMAGE_COUNT));
                    }

                    final int w = image.getWidth();
                    final int h = image.getHeight();
                    final int f = image.getFormat();
                    image.close();
                    buffer = null;

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            imageProcessor.process(bytes, w, h, f);
                        }
                    });
                }

            } catch(Exception ex){
                Log.d(TAG, ex.getMessage());
            } finally {
                imageReaderLock.unlock();
            }
        }

    };

    private static void saveReceivedImage(Bitmap bitmap, String imageName) throws Exception {
        File path = new File(activity.getExternalFilesDir(null), "frame");
        if(!path.exists()){
            path.mkdirs();
        }
        File outFile = new File(path, imageName + ".jpeg");
        String absPath = outFile.getAbsolutePath();
        Log.d(TAG, "Path image: " + absPath);
        FileOutputStream outputStream = new FileOutputStream(outFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        outputStream.close();
    }

    private static void saveReceivedImage(byte[] imageByteArray, int numberOfBytes, String imageName){
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, numberOfBytes);
            saveReceivedImage(bitmap, imageName);
        } catch (Exception e) {
            Log.e(TAG, "Saving received message failed with", e);
        }
    }

    protected CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

        protected void process(CaptureResult result) {

            switch (state) {

                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    if (afState == null) {
                        captureStillPicture();
                    }
                    else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {

                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            state = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        }
                        else {
                            takePicture();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {

                    // CONTROL_AE_STATE can be null on some devices
//                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//
//                    if (aeState == null
//                            || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
//                            || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
//                        state = STATE_WAITING_NON_PRECAPTURE;
//                    }

                    state = STATE_WAITING_NON_PRECAPTURE;


                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {

                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    @Override
    public void onResume() {
        if (DEBUG) Log.d(TAG, "OnResume");
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.d(TAG, "OnPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    protected void setupCameraOutputs(int width, int height) {

        if (DEBUG) { Log.d(TAG, "Setup camera"); }

        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {

            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                // obtem as caracteristicas da camera
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                if (DEBUG) {
                    Log.d(TAG, "screen width: " + width);
                    Log.d(TAG, "screen height: " + height);
                }

                loadImageSizes(map, width, height);

                if (DEBUG) {
                    Log.d(TAG, "jpeg width: " + jpegSize.getWidth());
                    Log.d(TAG, "jpeg height: " + jpegSize.getHeight());
                }

                // buffer de captura
                createImageReader();

                if (DEBUG) {
                    Log.d(TAG, "preview width: " + previewSize.getWidth());
                    Log.d(TAG, "preview height: " + previewSize.getHeight());
                }

                loadOrientations(characteristics);

                updateTextureView();

                this.cameraId = cameraId;

                return;
            }
        } catch (CameraAccessException ex) {
            Log.d(TAG, ex.toString());
        } catch (NullPointerException ex) {
            showAlert(getString(R.string.camera_error));
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
        }
    }

    private void updateTextureView() {
        int orientation = getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
        } else {
            textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
        }
    }

    private void loadImageSizes(StreamConfigurationMap map, int width, int height) {

        boolean portrait = isPortrait(getScreenOrientation());

        // Utilizado a proporção ideal para biometria
        jpegSize = ImageSize.chooseOptimalJpegSize(
                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                BIOMETRY_IMAGE_WIDTH,
                BIOMETRY_IMAGE_HEIGHT);


        // proporção ideal para tela
       /* previewSize = ImageSize.getOptimalPreviewSize(
                map.getOutputSizes(SurfaceTexture.class),
                width,
                height,
                facing); */

//        if(previewSize.getWidth() == 1440 && previewSize.getHeight() == 720) {
//            previewSize = new Size(1280, 720);
//        }

        previewSize = new Size(1280, 720);

    }

    private void loadOrientations(CameraCharacteristics characteristics) {
        screenOrientation = getScreenOrientation();
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    private void createImageReader() {

        if ((imageReader != null && imageReaderFace != null) || jpegSize == null || previewSize == null) return;

        imageReader = ImageReader.newInstance(
                jpegSize.getWidth(),
                jpegSize.getHeight(),
                ImageFormat.JPEG,
                2);

        // proporção para o buffer
        imageReaderFace = ImageReader.newInstance(
                previewSize.getWidth(),
                previewSize.getHeight(),
                ImageFormat.YUV_420_888, 3);

        imageReader.setOnImageAvailableListener(
                onImageAvailableListener,
                backgroundHandler);

        imageReaderFace.setOnImageAvailableListener(
                onImageFaceAvailableListener,
                backgroundHandlerFace);
    }

    protected int getScreenOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to " +
                            "portrait.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to " +
                            "landscape.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    protected boolean isPortrait(int orientation) {
        boolean portrait = false;
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                portrait = true;
        }
        return portrait;
    }

    protected void openCamera(int width, int height) {

        if (DEBUG) { Log.d(TAG, "Open camera"); }

        setupCameraOutputs(width, height);
        configureTransform(width, height);
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {

            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            if (DEBUG) { Log.d(TAG, "Manager Open camera"); }

            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.d(TAG, e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    protected void reopenCamera() {

        if (DEBUG) Log.d(TAG, "Reopen camera");

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {

                    closeCamera();

                    createImageReader();

                    cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);

                } catch (CameraAccessException e) {
                    Log.d("Erro de acesso a camera: " + TAG, e.toString());
                }
            }
        });
    }

    protected void closeCamera() {
        if (DEBUG) { Log.d(TAG, "Close camera"); }

        try {
            cameraOpenCloseLock.acquire();

            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }

            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }

            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }

            if (null != imageReaderFace) {
                imageReaderFace.close();
                imageReaderFace = null;
            }

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    protected void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        backgroundHandlerFace = new Handler(backgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
            backgroundHandlerFace = null;
        } catch (InterruptedException ex) {
            Log.d(TAG, ex.getMessage());
        }
    }

    protected void createCameraPreviewSession() {
        if (DEBUG) { Log.d(TAG, "Create preview session"); }

        try {

            if (previewSize == null
                    || textureView == null
                    || cameraDevice == null
                    || imageReader == null
                    || imageReaderFace == null
                    || cameraDevice == null) return;

            previewSurfaceTexture = textureView.getSurfaceTexture();

            assert previewSurfaceTexture != null;

            if (DEBUG) {
                Log.d(TAG, "preview width: " + previewSize.getWidth());
                Log.d(TAG, "preview height: " + previewSize.getHeight());
            }

            updatePreviewBufferSize();

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(previewSurfaceTexture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(imageReaderFace.getSurface());

            List<Surface> surfaceList = Arrays.asList(surface, imageReader.getSurface(), imageReaderFace.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    surfaceList,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;

                            try {

                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_MODE,
                                        CameraMetadata.CONTROL_MODE_AUTO);

                                captureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        captureCallback,
                                        backgroundHandler);

                            } catch (CameraAccessException e) {
                                Log.d(TAG, e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );

        } catch (CameraAccessException ex) {
            Log.d(TAG, ex.getMessage());
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    protected void configureTransform(int viewWidth, int viewHeight) {

        if (DEBUG) { Log.d(TAG, "Configure transform"); }

        synchronized (dimensionLock) {
            if (null == textureView || null == previewSize || null == activity) {
                return;
            }

            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());

            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / previewSize.getHeight(),
                        (float) viewWidth / previewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            }

            textureView.setTransform(matrix);
        }
    }

    protected void takePicture() {

        if (DEBUG) { Log.d(TAG, "Take picture"); }

        if (previewRequestBuilder == null || captureSession == null) {
            return;
        }

        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            // Tell #captureCallback to wait for the precapture sequence to be set.
            state = STATE_WAITING_PRECAPTURE;

            captureSession.capture(
                    previewRequestBuilder.build(),
                    captureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void captureStillPicture() {

        if (DEBUG) { Log.d(TAG, "Capture still picture"); }

        try {

            if (null == activity || null == cameraDevice) {
                return;
            }

            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureBuilder.addTarget(imageReader.getSurface());

            captureBuilder.set(
                    CaptureRequest.CONTROL_MODE,
                    CameraMetadata.CONTROL_MODE_AUTO);

            // jpeg orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(getScreenOrientation()));

            captureSession.stopRepeating();
            captureSession.abortCaptures();
            captureSession.capture(captureBuilder.build(), null, null);

        } catch (CameraAccessException ex) {
            Log.d(TAG, ex.toString());
        }
    }

    protected int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }

    @Override
    public void onClick(View view) {
        takePicture();
    }

    protected static class ImageSaver implements Runnable {

        protected final Image image;

        ImageSaver(Image image) {
            this.image = image;
        }

        @Override
        public void run() {

            try {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                int jpegOrientation = Exif.getOrientation(bytes);

                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                bytes = null;
                buffer = null;

                // valida se o jpeg deve ser rotacionado
                switch (jpegOrientation) {
                    case 0:
                        bitmap = ImageUtils.rotateBitmap(bitmap, 90, false);
                        break;
                    case 180:
                        bitmap = ImageUtils.rotateBitmap(bitmap, -90, false);
                        break;
                    case 270:
                        bitmap = ImageUtils.rotateBitmap(bitmap, -180, false);
                        break;
                    default:
                        break;
                }

                String base64 = ImageUtils.toBase64JPEG(bitmap, false);
                bitmap = null;

                 captureImageProcessor.capture(base64);
                base64 = null;

            } catch (Exception ex) {
                Log.d(TAG, ex.getMessage());
            } finally {
                image.close();
            }
        }

    }

    protected void setMaxSizes(){
        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        SCREEN_HEIGHT = displaySize.x;
        SCREEN_WIDTH = displaySize.y;
    }

    public int getHeightPixels() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    public int getWidthPixels() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    protected int getImageFirebaseVisionRotation() {
        try {
            return getRotationCompensation(cameraId, activity, getApplicationContext());
        } catch (Exception ex) {
            return 0;
        }
    }
    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected int getRotationCompensation(String cameraId, Activity activity, Context context)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);

        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        CameraManager cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360;

        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        int result;
        switch (rotationCompensation) {
            case 0:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                break;
            case 90:
                result = FirebaseVisionImageMetadata.ROTATION_90;
                break;
            case 180:
                result = FirebaseVisionImageMetadata.ROTATION_180;
                break;
            case 270:
                result = FirebaseVisionImageMetadata.ROTATION_270;
                break;
            default:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                Log.e(TAG, "Bad rotation value: " + rotationCompensation);
        }
        return result;
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}