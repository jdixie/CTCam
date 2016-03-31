package com.ninjapiratestudios.ctcam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Created by jjdixie on 1/30/16.
 * The idea here is to create the rectangle overlay that will outline the tracked subject.
 * This may well be temporary after I plug in OpenGL ES
 */
public class Overlay extends View {
    private Paint paint = new Paint();
    Overlay(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.YELLOW);
        paint.setStrokeWidth(10);

        //rectangle center
        int x = canvas.getWidth()/2;
        int y = canvas.getHeight()/2;
        int xRadius = canvas.getHeight()/3;
        int yRadius = canvas.getHeight()/3;

        //draw guide box
        canvas.drawRect(x-xRadius, y-yRadius, x+xRadius, y+yRadius, paint);
    }
}
