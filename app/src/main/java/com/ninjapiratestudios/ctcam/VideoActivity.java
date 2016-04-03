package com.ninjapiratestudios.ctcam;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class VideoActivity extends Activity { // implements TextureView
    public final static String LOG_TAG = "VIDEO_ACTIVITY";
    private CameraRecorder cameraRecorder;
    private int cameraAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Prepare camera and OpenGL
        initializeCameraRecorder();

        // TODO Temporary, replace when record button is clicked
        recordButtonListenerTemp();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    /**
     * Release resources and end current processing.
     */
    @Override
    protected void onPause() {
        super.onPause();
        cameraRecorder.releaseMediaResource();
        cameraRecorder.releaseCameraResource();
    }

    // TODO Remove once CameraFragment component is finished
    private void recordButtonListenerTemp() {
        cameraRecorder.displayFileNameDialog();
    }

    /**
     * Initializes the CameraRecorder class that is necessary for video
     * recording and OpenGL functionality.
     *
     */
    private void initializeCameraRecorder() {
        while (cameraRecorder == null) {
            cameraRecorder = CameraRecorder.newInstance(this);
            if (cameraRecorder == null) {
                // Attempt to acquire camera 3 times if there is an error
                // retrieving
                // reference.
                if (cameraAttempts == 3) {
                    Log.e(LOG_TAG, "3 Attempts failed to get camera reference");
                    // TODO Provide graceful app exit in future iteration
                    finish();
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "Main UI thread sleep error.");
                        // TODO Provide graceful app exit in future iteration
                        finish();
                    }
                    cameraAttempts++;
                }
            } else {
                cameraAttempts = 0;
            }
        }
    }
}
