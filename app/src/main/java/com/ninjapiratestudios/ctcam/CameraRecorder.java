package com.ninjapiratestudios.ctcam;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.widget.FrameLayout;

import java.io.File;
import java.io.IOException;

/**
 * This class will be used to provide an object that will handle all
 * of the functions associated with the device's video camera.
 *
 * @author John Qualls
 * @version 2/27/2016
 */
public class CameraRecorder{
    public final static String LOG_TAG = "CAMERA_RECORDER";
    public final static String FILE_DIRECTORY = "Tracker_Camera";
    private Camera camera;
    private CameraPreview cameraPreview;
    private MediaRecorder mediaRecorder;
    private VideoActivity activity;
    private String fileName;

    /**
     * Static factory method that gets a reference to the camera, and sets up
     * the
     * camera preview.
     *
     * @param activity The activity used to access the camera and view
     *                 components.
     * @return A new instance of this class, or null if there was an error.
     */
    public static CameraRecorder newInstance(VideoActivity activity) {
        CameraRecorder cameraRecorder = new CameraRecorder();
        cameraRecorder.activity = activity;
        cameraRecorder.camera = getCameraInstance();
        if (cameraRecorder.camera == null) {
            return null;
        }
        cameraRecorder.cameraPreviewSetup();
        return cameraRecorder;
    }

    /**
     * Displays a FileNameDialog to the user.
     */
    public void displayFileNameDialog() {
        FileNameDialog dialog = FileNameDialog.newInstance(this);
        dialog.show(activity.getFragmentManager(), FileNameDialog.FRAGMENT_TAG);
    }

    /**
     * Starts Camera recording.
     */
    public void startRecording() {
        try {
            camera.unlock();
            setupCamera();
            mediaRecorder.start();
            camera.reconnect();
            cameraPreview.onStartRecord();
            Log.i(LOG_TAG, "Camera recording started");
        } catch (Exception e) {
            // TODO Provide graceful app exit in future iteration
            Log.i(LOG_TAG, "Exiting application due to camera setup error.");
            activity.finish();
        }
    }

    /**
     * Stops Camera recording.
     */
    public void stopRecording() {
        cameraPreview.onStopRecord();
        mediaRecorder.stop();
        releaseMediaResource();
        camera.lock();
        Log.i(LOG_TAG, "Camera recording stopped.");
    }

    public void releaseMediaResource() {

        // Release media recorder
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            Log.i(LOG_TAG, "Media resources released.");
        }
    }

    public void releaseCameraResource() {
        // Release the camera for other applications
        if (camera != null) {
            camera.lock();
            camera.stopPreview();
            camera.release();
            camera = null;
            Log.i(LOG_TAG, "Camera resources released.");
        }
    }

    /**
     * Sets the filename field used to name the user's video file.
     *
     * @param fileName The file name.
     */
    public void setFileName(String fileName) {
        if (fileName != null) {
            this.fileName = fileName;
        } else {
            this.fileName = "trackerCamera";
        }
    }

    /**
     * Prepares the camera for recording
     *
     * @throws Exception If there was an error setting up configurations for
     *                   the camera.
     */
    private void setupCamera() throws Exception {
        // Setup camera configurations for video recording
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setCamera(camera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile
                .QUALITY_HIGH));
        Log.i(LOG_TAG, "Camera configurations are set.");

        // Create a new video file
        File filesDir = retrieveFileDirectory();
        File videoFile = new File(filesDir.getPath(), fileName + ".mp4");
        mediaRecorder.setOutputFile(videoFile.toString());
        //mediaRecorder.setVideoSize(cameraPreview.getWidth(), cameraPreview.getHeight());
        mediaRecorder.setPreviewDisplay(cameraPreview.getHolder().getSurface());
        Log.i(LOG_TAG, "File saved to: " + videoFile.getPath());

        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, "IllegalStateException preparing MediaRecorder: " + e
                    .getMessage());
            releaseMediaResource();
            throw new IllegalStateException(e.getMessage());
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException preparing MediaRecorder: " + e
                    .getMessage());
            releaseMediaResource();
            throw new IOException(e.getMessage());
        }
        Log.i(LOG_TAG, "Camera successfully configured");
    }

    /**
     * A safe way to get an instance of the Camera object.
     * reference:http://developer.android.com/guide/topics/media/camera
     * .html#access-camera
     *
     * @return A reference to the device Camera, null if there was an error.
     */
    private static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get a Camera instance
        } catch (Exception e) {
            Log.e(LOG_TAG, "Camera is not available (in use or does not " +
                    "exist)");
        }
        return c; // returns null if camera is unavailable
    }

    /**
     * Retrieves the sdcard/pictures/Tracker_Camera directory. If the
     * Tracker_Camera does not exist, it is created.
     *
     * @return sdcard/pictures/Tracker_Camera as a File object.
     */
    private File retrieveFileDirectory() {
        File filesDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), FILE_DIRECTORY);
        if (!filesDir.exists()) {
            filesDir.mkdir();
        }
        return filesDir;
    }

    /**
     * Initializes the preview view for the camera.
     */
    private void cameraPreviewSetup() {
        cameraPreview = new CameraPreview(activity, camera);
        FrameLayout preview = (FrameLayout) activity.findViewById(R.id
                .camera_preview);
        preview.addView(cameraPreview);
        Log.i(LOG_TAG, "Camera preview setup successful.");
    }
}
