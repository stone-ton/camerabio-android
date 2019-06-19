package com.example.camerabioexample_android.camerabiomanager.camera;

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

    public static Size chooseOptimalJpegSize(List<Size> sizeJpegList, float width, float height, boolean portrait) {

        if (DEBUG) Log.d(TAG, "<< select jpeg >>");

        Size optimalJpegSize = null;

        float aspectRatioScreen = width / height;

        final double ASPECT_TOLERANCE = 0.4;

        double minDiff = Double.MAX_VALUE;

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

    public static Size getOptimalPreviewSize(Size[] sizes, int w, int h, int facing, boolean portrait) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double)h / w;
        List<Size> cameraSizes = new ArrayList<>();

        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            for (Size size : sizes) {

                if (size.getWidth() > MAX_JPEG_WIDTH && size.getHeight() > MAX_JPEG_HEIGHT) {
                    continue;
                }

                int maxWidthScreen = portrait ? w : h;
                int maxHeightScreen = portrait ? h : w;
                int maxWidthItem = portrait ? size.getHeight() : size.getWidth();
                int maxHeightItem = portrait ? size.getWidth() : size.getHeight();

                // valida a altura e largura da tela x opções disponiveis
                if (maxWidthItem <= maxWidthScreen && maxHeightItem <= maxHeightScreen) {
                    cameraSizes.add(size);
                }
            }
        }

        if (cameraSizes.isEmpty()) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Size size : cameraSizes) {
            double ratio = (double) size.getWidth() / size.getHeight();

            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;

            if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;

            for (Size size : cameraSizes) {
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }

        return optimalSize;
    }

}
