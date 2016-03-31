package com.ninjapiratestudios.ctcam;

import java.util.ArrayList;

/**
 * Created by jjdixie on 3/13/16.
 */
public class TrackingData {
    //storing prevPosition and prevSpeed in case data is needed by different threads for anything
    public int[] prevPosition = new int[4]; //left x, right x, bottom y, top y
    public float[] prevSpeed = new float[2]; //in x,y pixel change per analysis? + means right or up, - left or down
    public int[] currentPosition = new int[4]; //left x, right x, bottom y, top y
    public float[] currentSpeed = new float[2];
    public ArrayList<int[]> potentialColors = new ArrayList<int[]>();
}
