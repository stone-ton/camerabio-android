package com.example.camerabioexample_android.camerabiomanager.camera;

import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.List;

public class ImageSize {

    private static boolean DEBUG = false;
    private static final String TAG = "ImageSize";

    // ideal proportion to biometry
    public static final int MAX_JPEG_WIDTH = 1280;
    public static final int MAX_JPEG_HEIGHT = 720;

    public static Size chooseOptimalJpegSize(List<Size> sizeJpegList, float width, float height) {

        final float ASPECT_TOLERANCE = 0.4f;
        double minDiff = Double.MAX_VALUE;

        if (DEBUG) Log.d(TAG, "<< select jpeg >>");

        Size optimalJpegSize = null;

        // valida rotação da tela - portrait ou landscape
        if (height > width) {
            float temp = width;
            width = height;
            height = temp;
        }

        float aspectRatioScreen = width / height;

        int targetHeight = (int)height;

        for (Size size : sizeJpegList) {

            if (size.getWidth() > MAX_JPEG_WIDTH) {
                if (DEBUG) Log.d(TAG, "Size excluded: " + size.toString());
                continue;
            }
            else {
                if (DEBUG) Log.d(TAG, "Size added: " + size.toString());
            }

            double ratio = (double) size.getWidth() / size.getHeight();

            if (Math.abs(ratio - aspectRatioScreen) > ASPECT_TOLERANCE) {
                continue;
            }

            int  diffHeight = Math.abs(size.getHeight() - targetHeight);

            if (diffHeight < minDiff) {
                optimalJpegSize = size;
                minDiff = diffHeight;
            }
        }

        if (optimalJpegSize == null) {
            minDiff = Double.MAX_VALUE;

            for (Size size : sizeJpegList) {
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalJpegSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }

        return optimalJpegSize;
    }

    public static Size getOptimalPreviewSize(Size[] sizes, float width, float height, int facing) {

        float ASPECT_TOLERANCE = 0.1f;

        // valida rotação da tela - portrait ou landscape
        if (height > width) {
            float temp = width;
            width = height;
            height = temp;
        }

        float targetRatio = width / height;

        List<Size> cameraSizes = new ArrayList<>();

        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            for (Size size : sizes) {

                if ((size.getWidth() > MAX_JPEG_WIDTH && size.getHeight() > MAX_JPEG_HEIGHT) || size.getWidth() == size.getHeight()) {
                    continue;
                }

                float maxWidthItem = size.getWidth();
                float maxHeightItem = size.getHeight();

                // valida a altura e largura da tela x opções disponiveis
                if (maxWidthItem <= width && maxHeightItem <= height) {
                    cameraSizes.add(size);
                }
            }
        }

        if (cameraSizes.isEmpty()) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        float targetHeight = height;

        while (optimalSize == null) {

            for (Size size : cameraSizes) {
                double ratio = (double) size.getWidth() / size.getHeight();

                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;

                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
            ASPECT_TOLERANCE += 0.1f;
        }

        if(optimalSize.getHeight() > 1024) {

            Size size = new Size((optimalSize.getWidth() / 2) , (optimalSize.getWidth() / 2));
            optimalSize = size;

        }

        float larg = 1280 / targetRatio;
        optimalSize = new Size(1280, (int) larg);


        return optimalSize;

    }


    public static Camera.Size getOptimalPreviewSizeBack(List<Camera.Size> sizes, float w, float h, int facing, boolean portrait) {

        float ASPECT_TOLERANCE = 0.1f;

        // valida rotação da tela - portrait ou landscape
        if (h > w) {
            float temp = w;
            w = h;
            h = temp;
        }

        float targetRatio = w / h;

        List<Camera.Size> cameraSizes = new ArrayList<>();

        if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            for (Camera.Size size : sizes) {

                if ((size.width > MAX_JPEG_WIDTH && size.height > MAX_JPEG_HEIGHT) || size.width == size.height) {
                    continue;
                }

                float maxWidthItem = size.width;
                float maxHeightItem = size.height;

                // valida a altura e largura da tela x opções disponiveis
                if (maxWidthItem <= w && maxHeightItem <= h) {
                    cameraSizes.add(size);
                }
            }
        }

        if (cameraSizes.isEmpty()) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        float targetHeight = h;

        while (optimalSize == null) {

            for (Camera.Size size : cameraSizes) {
                double ratio = (double) size.width / size.height;

                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;

                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
            ASPECT_TOLERANCE += 0.1f;
        }


//        if (optimalSize == null) {
//            minDiff = Double.MAX_VALUE;
//
//            for (Camera.Size size : cameraSizes) {
//                if (Math.abs(size.height - targetHeight) < minDiff) {
//                    optimalSize = size;
//                    minDiff = Math.abs(size.height - targetHeight);
//                }
//            }
//        }

        if(optimalSize.height > 1024) {

            // Size size = new Size((optimalSize.width / 2) , (optimalSize.height / 2));

            Camera cam = Camera.open();
            Camera.Size size = cam.new Size((optimalSize.width / 2) , (optimalSize.height / 2));
            optimalSize = size;

        }



        return optimalSize;
    }


}
