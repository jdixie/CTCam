package com.ninjapiratestudios.ctcam;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// TODO Temporary preview class until josh's component is finished
public class CameraPreview extends SurfaceView implements SurfaceHolder
        .Callback, Camera.PreviewCallback {
    public final static String LOG_TAG = "CAMERA_PREVIEW";
    private SurfaceHolder mHolder;
    private Camera mCamera;
    SurfaceTexture camFrame;
    private byte[] buffer;
    Analyzer analyzer;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw
        // the preview.
        /*try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(LOG_TAG, "Error setting camera preview: " + e.getMessage());
        }*/
        if (this.camFrame != null)
            this.camFrame.release();
        camFrame = new SurfaceTexture(0);

        if (mCamera == null)
            return;

        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();

        // Camera.Size previewSize = sizes.get(0);
        Collections.sort(sizes, new PreviewSizeComparer());
        Camera.Size previewSize = null;
        for (Camera.Size s : sizes) {
            if (s == null)
                break;

            previewSize = s;
        }

        // List<Integer> formats = params.getSupportedPictureFormats();
        // params.setPreviewFormat(ImageFormat.NV21);

        params.setPreviewSize(previewSize.width, previewSize.height);
        mCamera.setParameters(params);

        params = mCamera.getParameters();

        int frameWidth = params.getPreviewSize().width;
        int frameHeight = params.getPreviewSize().height;

        int size = frameWidth * frameHeight;
        size = size * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8;

        analyzer = new Analyzer(frameWidth, frameHeight);

        buffer = new byte[size];
        Log.d("", "Created callback buffer of size (bytes): " + size);

        try {
            mCamera.setPreviewTexture(camFrame);
            mCamera.setPreviewDisplay(holder);
            mCamera.addCallbackBuffer(buffer);
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.i("Camera preview", "Error starting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.reconnect();
            mCamera.setPreviewTexture(camFrame);
            mCamera.setPreviewDisplay(holder);
            mCamera.addCallbackBuffer(buffer);
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.d(LOG_TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public synchronized void onPreviewFrame(byte[] frame, Camera arg1) {
        analyzer.onCameraFrame(frame);
        if (mCamera != null)
            mCamera.addCallbackBuffer(buffer);
    }

    public void onStartRecord(){
        try{
            mCamera.reconnect();
            mCamera.addCallbackBuffer(buffer);
            mCamera.setPreviewCallbackWithBuffer(this);
        }
        catch (Exception e) {
            Log.i("Camera", "Error reconnecting camera: " + e.getMessage());
        }
        analyzer.onStartRecord();
    }

    public void onStopRecord(){
        analyzer.onStopRecord();
    }

    private class PreviewSizeComparer implements Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size arg0, Camera.Size arg1) {
            if (arg0 != null && arg1 == null)
                return -1;
            if (arg0 == null && arg1 != null)
                return 1;

            if (arg0.width < arg1.width)
                return -1;
            else if (arg0.width > arg1.width)
                return 1;
            else
                return 0;
        }
    }
}
