package com.ninjapiratestudios.ctcam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

//TODO: expand this past tutorial to encompass multiple OpenCV contours based on different colors discovered from algorithm
public class ColorBlobDetector {
    //bounds for range checking in HSV color space
    private Scalar lowerBound = new Scalar(0);
    private Scalar upperBound = new Scalar(0);
    //smallest area to be considered for OpenCV contour as percentage of total image
    private static double minContourArea = 0.1;
    //starting color range to be used to create initial bounds in HSV color space.
    //method to change programmatically provided
    private Scalar colorRadius = new Scalar(25,50,50,0);
    private Mat spectrum = new Mat();
    private List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

    //create these and reuse because Java sucks for memory management in graphical apps
    Mat pyrDownMat = new Mat();
    Mat hsvMat = new Mat();
    Mat mask = new Mat();
    Mat dilatedMask = new Mat();
    Mat hierarchy = new Mat();

    public void setColorRadius(Scalar radius) {
        colorRadius = radius;
    }

    //ready HSV color range for color matching
    public void setHsvColor(Scalar hsvColor) {
        double minH = (hsvColor.val[0] >= colorRadius.val[0]) ? hsvColor.val[0]-colorRadius.val[0] : 0;
        double maxH = (hsvColor.val[0]+colorRadius.val[0] <= 255) ? hsvColor.val[0]+colorRadius.val[0] : 255;

        lowerBound.val[0] = minH;
        upperBound.val[0] = maxH;

        lowerBound.val[1] = hsvColor.val[1] - colorRadius.val[1];
        upperBound.val[1] = hsvColor.val[1] + colorRadius.val[1];

        lowerBound.val[2] = hsvColor.val[2] - colorRadius.val[2];
        upperBound.val[2] = hsvColor.val[2] + colorRadius.val[2];

        lowerBound.val[3] = 0;
        upperBound.val[3] = 255;

        Mat spectrumHsv = new Mat(1, (int)(maxH-minH), CvType.CV_8UC3);

        for (int j = 0; j < maxH-minH; j++) {
            byte[] tmp = {(byte)(minH+j), (byte)255, (byte)255};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, spectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }

    public Mat getSpectrum() {
        return spectrum;
    }

    public void setMinContourArea(double area) {
        minContourArea = area;
    }

    public void process(Mat rgbaImage) {
        Imgproc.pyrDown(rgbaImage, pyrDownMat);
        Imgproc.pyrDown(pyrDownMat, pyrDownMat);

        Imgproc.cvtColor(pyrDownMat, hsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        Core.inRange(hsvMat, lowerBound, upperBound, mask);
        Imgproc.dilate(mask, dilatedMask, new Mat());

        List<MatOfPoint> tempContours = new ArrayList<MatOfPoint>();

        Imgproc.findContours(dilatedMask, tempContours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        //find max contour area
        double maxArea = 0;
        Iterator<MatOfPoint> each = tempContours.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea)
                maxArea = area;
        }

        //filter contours by size/area and resize to fit the original image size
        //TODO: consider a max size area to potentially eliminate floors, walls, screens and such. What would be reasonable?
        contours.clear();
        each = tempContours.iterator();
        MatOfPoint c;
        while (each.hasNext()) {
            c = each.next();
            if (Imgproc.contourArea(c) > minContourArea*maxArea) {
                Core.multiply(c, new Scalar(4,4), c);
                contours.add(c);
            }
        }
    }

    public List<MatOfPoint> getContours() {
        return contours;
    }
}
