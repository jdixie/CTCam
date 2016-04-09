package com.ninjapiratestudios.ctcam;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.SurfaceView;

public class Analyzer extends Thread{
    private boolean isColorSelected = false;
    private Mat rgba;
    private Scalar blobColorRgba;
    private Scalar blobColorHsv;
    private ColorBlobDetector detector;
    private Mat spectrum;
    private Size SPECTRUM_SIZE;
    private Scalar CONTOUR_COLOR;
    CameraAccessFrame cameraAccessFrame;
    private int frameWidth, frameHeight;
    private boolean readyForFrame = false;
    private boolean frameAnalyzed = true;
    private boolean stopRecording = false;
    List<MatOfPoint> contours;
    ArrayList<Point> centroids;

    public Analyzer(int w, int h) {
        frameWidth = w;
        frameHeight = h;
        rgba = new Mat(frameHeight, frameWidth, CvType.CV_8UC4);
        detector = new ColorBlobDetector();
        spectrum = new Mat();
        blobColorRgba = new Scalar(255);
        blobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
        Mat frame = new Mat(frameHeight + (frameHeight / 2), frameWidth, CvType.CV_8UC1);
        cameraAccessFrame = new CameraAccessFrame(frame, frameWidth, frameHeight);
        centroids = new ArrayList<Point>();
    }

    public void onCameraViewStopped() {
        rgba.release();
    }

    //Load frame as Mat and get contours/blobs
    public void onCameraFrame(byte[] frame) {
        if(readyForFrame) {
            cameraAccessFrame.put(frame);
            rgba = cameraAccessFrame.rgba();
            readyForFrame = false;
        }
    }

    public void onStartRecord() {
        if(!readyForFrame && frameAnalyzed)
            readyForFrame = true;
        start();
    }

    public void onStopRecord(){
        stopRecording = true;
    }

    //analyze frame for contours based on the color(s)
    public void analyze(){
        frameAnalyzed = false;

        if (isColorSelected) {
            detector.process(rgba);
            contours = detector.getContours();
            Log.e("Contours count", " " + contours.size());
            //Imgproc.drawContours(rgba, contours, -1, CONTOUR_COLOR);

            //find centroids
            Moments moments;
            Point p;
            centroids.clear();
            for(int i = 0; i < contours.size(); i++){
                moments = Imgproc.moments(contours.get(i));
                p = new Point();

                p.x = moments.get_m10() / moments.get_m00();
                p.y = moments.get_m01() / moments.get_m00();
                Log.i("Centroid", p.x + ", " + p.y);
                centroids.add(p);
            }

            Mat colorLabel = rgba.submat(4, 68, 4, 68);
            colorLabel.setTo(blobColorRgba);

            Mat spectrumLabel = rgba.submat(4, 4 + spectrum.rows(), 70, 70 + spectrum.cols());
            spectrum.copyTo(spectrumLabel);

        }

        //TODO: move the motor appropriately

        readyForFrame = true;
        frameAnalyzed = true;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    public void liveCalibrate(){
        int x = frameWidth / 2;
        int y = frameHeight / 2;

        Rect rect = new Rect();

        rect.x = x - 4;
        rect.y = y - 4;

        rect.width = 8;
        rect.height = 8;

        Mat regionRgba = rgba.submat(rect);

        Mat regionHsv = new Mat();
        Imgproc.cvtColor(regionRgba, regionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        //calculate average color of selection
        blobColorHsv = Core.sumElems(regionHsv);
        int pointCount = rect.width*rect.height;
        for (int i = 0; i < blobColorHsv.val.length; i++)
            blobColorHsv.val[i] /= pointCount;

        blobColorRgba = converScalarHsv2Rgba(blobColorHsv);

        Log.i("Color", "Selected rgba color: (" + blobColorRgba.val[0] + ", " + blobColorRgba.val[1] +
                ", " + blobColorRgba.val[2] + ", " + blobColorRgba.val[3] + ")");

        detector.setHsvColor(blobColorHsv);

        Imgproc.resize(detector.getSpectrum(), spectrum, SPECTRUM_SIZE);

        regionRgba.release();
        regionHsv.release();

        isColorSelected = true;
    }

    @Override
    public void run(){
        if (!isColorSelected) {
            while(readyForFrame){
                //wait for first frame
            }
            liveCalibrate();
        }
        while(!interrupted()) {
            if (!readyForFrame) {
                analyze();
            }
            if (stopRecording) {
                readyForFrame = false;
                frameAnalyzed = true;
                stopRecording = false;
                interrupt();
            }
        }
    }


    private class CameraAccessFrame implements CameraFrame {
        private Mat mYuvFrameData;
        private Mat mRgba;
        private int mWidth;
        private int mHeight;
        private Bitmap mCachedBitmap;
        private boolean mRgbaConverted;
        private boolean mBitmapConverted;

        @Override
        public Mat gray() {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth);
        }

        @Override
        public Mat rgba() {
            if (!mRgbaConverted) {
                Imgproc.cvtColor(mYuvFrameData, mRgba,
                        Imgproc.COLOR_YUV2BGR_NV12, 4);
                mRgbaConverted = true;
            }
            return mRgba;
        }

        // @Override
        // public Mat yuv() {
        // return mYuvFrameData;
        // }

        @Override
        public synchronized Bitmap toBitmap() {
            if (mBitmapConverted)
                return mCachedBitmap;

            Mat rgba = this.rgba();
            Utils.matToBitmap(rgba, mCachedBitmap);

            mBitmapConverted = true;
            return mCachedBitmap;
        }

        public CameraAccessFrame(Mat Yuv420sp, int width, int height) {
            super();
            mWidth = width;
            mHeight = height;
            mYuvFrameData = Yuv420sp;
            mRgba = new Mat();

            this.mCachedBitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
        }

        public synchronized void put(byte[] frame) {
            mYuvFrameData.put(0, 0, frame);
            invalidate();
        }

        public void release() {
            mRgba.release();
            mCachedBitmap.recycle();
        }

        public void invalidate() {
            mRgbaConverted = false;
            mBitmapConverted = false;
        }
    }

    public interface CameraFrame extends CvCameraViewFrame {
        Bitmap toBitmap();

        @Override
        Mat rgba();

        @Override
        Mat gray();
    }
}
