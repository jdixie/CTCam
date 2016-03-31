package com.ninjapiratestudios.ctcam;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES31;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Date;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;




/**
 * Created by jjdixie on 2/1/16.
 */
public class GLCamView extends GLSurfaceView implements SurfaceTexture.OnFrameAvailableListener {

    GLRenderer renderer;
    Analyzer analyzer;
    Camera camera;
    private SurfaceTexture surfaceTexture;
    Camera.Size previewSize;
    public static final int REGULAR_VIEW = 0;
    public static final int TRACKING_VIEW = 1;
    int viewType = 0;
    int screenWidth;
    int screenHeight;
    boolean setColorChoice = false;
    boolean updateSurfaceTexture = false;
    boolean colorSet = false;
    float xChoice, yChoice;
    float[][] colorSelected = new float[8][];
    int colorsTracked = 0;
    float deltaEThreshold = 0.0001f;
    float hueThreshold = (float)((8.0 * Math.PI) / 180.0); //radians

    private int[] pixelBuffer;
    ByteBuffer pixelData;

    GLCamView(Context c) {
        super(c);
        renderer = new GLRenderer(this);
        setEGLContextClientVersion(3);
        getHolder().setFormat(PixelFormat.RGBA_8888);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        //setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        for(int i = 0; i < 8; i++)
            colorSelected[i] = new float[4];
    }

    //toggle tracking on and off
    public void setViewType(int vt) {
        if (colorSet && vt == TRACKING_VIEW)
            viewType = vt;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        renderer.release();
        super.surfaceDestroyed(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
    }

    @Override
    public synchronized void onFrameAvailable(SurfaceTexture st) {
        updateSurfaceTexture = true;
        requestRender();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && setColorChoice == false) {
            //x, y are in terms of width x height of the screen with 0,0 the top left
            float x = e.getX();
            float y = e.getY();

            Log.d("Touch Event", "Touch at: " + x + ". " + y);
            xChoice = x;
            yChoice = screenHeight - y;
            setColorChoice = true;
        }

        return true;
    }

    @Override
    public void onPause(){
        analyzer.stopAnalyzer();
        camera.stopPreview();
        super.onPause();
    }

    public class GLRenderer implements Renderer {

        GLCamView glCamView;
        private SurfaceTexture surfaceTexture;

        float[] transformMatrix;
        float[] viewMatrix;
        float[] projectionMatrix;

        private short indices[] = {0, 1, 2, 2, 1, 3};
        private ShortBuffer indexBuffer;
        private FloatBuffer vertexBuffer;
        private FloatBuffer textureBuffer;

        private float rectCoords[] = {-1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 0.0f};
        private float texCoords[] = {0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f};

        //handles
        int[] textureHandle = new int[1];
        private int vertexShaderHandle;
        private int analyzingFragmentShaderHandle;
        private int liveViewFragmentShaderHandle;
        private int liveViewShaderProgram;
        private int analyzingShaderProgram;
        private int computeShaderHandle;
        private int luminanceShaderProgram;

        private int[] computeBuffers;
        IntBuffer lumVal;
        //private int[] lumVal = new int[256];

        private int[] trackColor = new int[4];

        int frameCount = 0;

        GLRenderer(GLCamView glcv) {
            glCamView = glcv;

            //ready index, vertex, and texture buffers
            ByteBuffer ib = ByteBuffer.allocateDirect(indices.length * 2);
            ib.order(ByteOrder.nativeOrder());
            indexBuffer = ib.asShortBuffer();
            indexBuffer.put(indices);
            indexBuffer.position(0);

            ByteBuffer bb = ByteBuffer.allocateDirect(rectCoords.length * 4);
            bb.order(ByteOrder.nativeOrder());

            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(rectCoords);
            vertexBuffer.position(0);

            ByteBuffer texturebb = ByteBuffer.allocateDirect(texCoords.length * 4);
            texturebb.order(ByteOrder.nativeOrder());

            textureBuffer = texturebb.asFloatBuffer();
            textureBuffer.put(texCoords);
            textureBuffer.position(0);
        }

        public void release() {
            surfaceTexture.release();
            GLES31.glDeleteTextures(1, textureHandle, 0);
        }

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            int[] vers = new int[2];
            GLES31.glGetIntegerv(GLES31.GL_MAJOR_VERSION, vers, 0);
            GLES31.glGetIntegerv(GLES31.GL_MINOR_VERSION, vers, 1);
            Log.d("Supported version", "" + vers[0] + "." + vers[1]);

            // Position the eye behind the origin.
            final float eyeX = 0.0f;
            final float eyeY = 0.0f;
            final float eyeZ = 1.5f;

            // We are looking toward the distance
            final float lookX = 0.0f;
            final float lookY = 0.0f;
            final float lookZ = -5.0f;

            // Set our up vector. This is where our head would be pointing were we holding the camera.
            final float upX = 0.0f;
            final float upY = 1.0f;
            final float upZ = 0.0f;

            transformMatrix = new float[16];
            viewMatrix = new float[16];
            Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

            textureHandle = new int[1];
            GLES31.glGenTextures(1, textureHandle, 0);
            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureHandle[0]);
            GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
            GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
            GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST);
            GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_NEAREST);
            surfaceTexture = new SurfaceTexture(textureHandle[0]);
            surfaceTexture.setOnFrameAvailableListener(glCamView);

            //TODO: move buffer creation here for lifecycle

            camera = Camera.open();
            try {
                camera.setPreviewTexture(surfaceTexture);
            } catch (IOException e) {
                throw new RuntimeException("Error setting camera preview to texture.");
            }

            GLES31.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);
            GLES31.glDisable(GLES31.GL_CULL_FACE);
            GLES31.glDisable(GLES31.GL_BLEND);
            GLES31.glDisable(GLES31.GL_DEPTH_TEST);
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            GLES31.glViewport(0, 0, width, height);

            // Create a new perspective projection matrix. The height will stay the same
            // while the width will vary as per aspect ratio.
            final float ratio = (float) width / height;
            final float left = -ratio;
            final float right = ratio;
            final float bottom = -1.0f;
            final float top = 1.0f;
            final float near = 1.0f;
            final float far = 10.0f;

            projectionMatrix = new float[16];
            Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, near, far);

            previewSize = camera.getParameters().getPreviewSize();
            Camera.Parameters param = camera.getParameters();
            param.setPictureSize(previewSize.width, previewSize.height);
            List<int[]> fpsRange = param.getSupportedPreviewFpsRange();
            //put some code here to actually check this
            param.setPreviewFpsRange(30000, 30000);
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height);
            param.set("orientation", "landscape");
            camera.setParameters(param);
            camera.startPreview();
            screenWidth = width;
            screenHeight = height;

            loadShaders();
            analyzer = new Analyzer(screenWidth, screenHeight);

            //start analyzer here for now. Later start it when recording is started
            analyzer.startAnalyzer();
        }

        @Override
        public void onDrawFrame(GL10 unused){
            GLES31.glClear(GLES31.GL_DEPTH_BUFFER_BIT | GLES31.GL_COLOR_BUFFER_BIT);

            float[] textureMatrix = new float[16];
            float[] modelMatrix = {1.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 0.0f, 1.0f};

            Matrix.multiplyMM(transformMatrix, 0, viewMatrix, 0, modelMatrix, 0);
            Matrix.multiplyMM(transformMatrix, 0, projectionMatrix, 0, transformMatrix, 0);


            synchronized(this){
                if(updateSurfaceTexture){
                    surfaceTexture.updateTexImage();
                    updateSurfaceTexture = false;
                }
            }
            //computeLuminance();

            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(textureMatrix);

            if(viewType == REGULAR_VIEW || setColorChoice){
                GLES31.glUseProgram(liveViewShaderProgram);

                int positionHandle = GLES31.glGetAttribLocation(liveViewShaderProgram, "position");
                int textureCoordinateHandle = GLES31.glGetAttribLocation(liveViewShaderProgram, "inputTextureCoordinate");
                int textureMatrixHandle = GLES31.glGetUniformLocation(liveViewShaderProgram, "textureMatrix");
                int transformMatrixHandle = GLES31.glGetUniformLocation(liveViewShaderProgram, "MVPMatrix");
                int textureHandleUI = GLES31.glGetUniformLocation(liveViewShaderProgram, "videoFrame");

                GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
                GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureHandle[0]);
                GLES31.glUniform1i(textureHandleUI, 0);

                GLES31.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0);
                GLES31.glUniformMatrix4fv(transformMatrixHandle, 1, false, transformMatrix, 0);

                vertexBuffer.position(0);
                GLES31.glVertexAttribPointer(positionHandle, 3, GLES31.GL_FLOAT, false, 0, vertexBuffer);
                GLES31.glEnableVertexAttribArray(positionHandle);

                textureBuffer.position(0);
                GLES31.glVertexAttribPointer(textureCoordinateHandle, 2, GLES31.GL_FLOAT, false, 0, textureBuffer);
                GLES31.glEnableVertexAttribArray(textureCoordinateHandle);

                indexBuffer.position(0);
                GLES31.glDrawElements(GLES31.GL_TRIANGLES, indices.length, GLES31.GL_UNSIGNED_SHORT, indexBuffer);
                GLES31.glDisableVertexAttribArray(positionHandle);
                GLES31.glDisableVertexAttribArray(textureCoordinateHandle);
                GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
                GLES31.glUseProgram(0);

                if(setColorChoice){
                    setColor();
                }
            }
            else{//tracking view for now
                GLES31.glUseProgram(analyzingShaderProgram);

                int positionHandle = GLES31.glGetAttribLocation(analyzingShaderProgram, "position");
                int textureCoordinateHandle = GLES31.glGetAttribLocation(analyzingShaderProgram, "inputTextureCoordinate");
                int textureMatrixHandle = GLES31.glGetUniformLocation(analyzingShaderProgram, "textureMatrix");
                int textureHandleUI = GLES31.glGetUniformLocation(analyzingShaderProgram, "videoFrame");
                int colorsTrackedHandle = GLES31.glGetUniformLocation(analyzingShaderProgram, "colorsTracked");
                //int[] inputColorHandle = new int[8];
                //for(int i = 0; i < 8; i++)
                int inputColorHandle = GLES31.glGetUniformLocation(analyzingShaderProgram, "inputColor");
                int deltaEThresholdHandle = GLES31.glGetUniformLocation(analyzingShaderProgram, "deltaEThreshold");
                int hueThresholdHandle = GLES31.glGetUniformLocation(analyzingShaderProgram, "hueThreshold");

                GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
                GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureHandle[0]);
                GLES31.glUniform1i(textureHandleUI, 0);

                GLES31.glUniform1i(colorsTrackedHandle, colorsTracked);
                float[] array = new float[32];
                for(int i = 0; i < 8; i++){
                    array[(i * 4) + 0] = colorSelected[i][0];
                    array[(i * 4) + 1] = colorSelected[i][1];
                    array[(i * 4) + 2] = colorSelected[i][2];
                    array[(i * 4) + 3] = colorSelected[i][3];
                }
                GLES31.glUniform1fv(inputColorHandle, 32, array, 0);
                //for(int i = 0; i < 8; i ++)
                //    GLES31.glUniform4f(inputColorHandle, colorSelected[i][0], colorSelected[i][1], colorSelected[i][2], colorSelected[i][3]);
                GLES31.glUniform1f(deltaEThresholdHandle, deltaEThreshold);
                GLES31.glUniform1f(hueThresholdHandle, hueThreshold);

                GLES31.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0);

                vertexBuffer.position(0);
                GLES31.glVertexAttribPointer(positionHandle, 3, GLES31.GL_FLOAT, false, 0, vertexBuffer);
                GLES31.glEnableVertexAttribArray(positionHandle);

                textureBuffer.position(0);
                GLES31.glVertexAttribPointer(textureCoordinateHandle, 2, GLES31.GL_FLOAT, false, 0, textureBuffer);
                GLES31.glEnableVertexAttribArray(textureCoordinateHandle);

                indexBuffer.position(0);
                GLES31.glDrawElements(GLES31.GL_TRIANGLES, indices.length, GLES31.GL_UNSIGNED_SHORT, indexBuffer);
                GLES31.glDisableVertexAttribArray(positionHandle);
                GLES31.glDisableVertexAttribArray(textureCoordinateHandle);
                GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
                GLES31.glUseProgram(0);

                //GLES31.glReadBuffer(GLES31.GL_BACK);
                //GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, pixelBuffer[0]);
                //glCamView.readPixels(0, 0, screenWidth, screenHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, 0);
                //Date before = new Date();
                //GLES31.glReadPixels(0, 0, screenWidth, screenHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, pixelData);
                //Date after = new Date();
                //Log.d("Time for readPixels", "" + (after.getTime() - before.getTime()) + "ms");

                //quick stub for now, write a timer later and use that instead
                frameCount++;
                if(frameCount >= 15 && !analyzer.isReady()) {
                    analyzeModifiedTexture();
                    frameCount = 0;
                }
            }

        }

        private void loadShaders() {
            AssetManager assetManager = glCamView.getContext().getAssets();
            String vertexShaderCode = "";
            String fragmentShaderCode = "";
            String computeShaderCode = "";

            //load regular shader program
            try {
                InputStream fis = assetManager.open("DirectDisplayShader.fsh");
                InputStreamReader fisr = new InputStreamReader(fis);
                BufferedReader fbr = new BufferedReader(fisr);
                StringBuilder fsb = new StringBuilder();
                String next;
                while ((next = fbr.readLine()) != null) {
                    fsb.append(next);
                    fsb.append('\n');
                }
                fragmentShaderCode = fsb.toString();

                InputStream vis = assetManager.open("DirectDisplayShader.vsh");
                InputStreamReader visr = new InputStreamReader(vis);
                BufferedReader vbr = new BufferedReader(visr);
                StringBuilder vsb = new StringBuilder();
                while ((next = vbr.readLine()) != null) {
                    vsb.append(next);
                    vsb.append('\n');
                }
                vertexShaderCode = vsb.toString();
            } catch (IOException e) {
                Log.d("Live shader load error:", e.getMessage());
                return;
            }
            int[] status = new int[1];

            vertexShaderHandle = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER);
            GLES31.glShaderSource(vertexShaderHandle, vertexShaderCode);
            GLES31.glCompileShader(vertexShaderHandle);
            GLES31.glGetShaderiv(vertexShaderHandle, GLES31.GL_COMPILE_STATUS, status, 0);
            if(status[0] == GLES31.GL_FALSE){
                Log.d("Shader","Vertex Shader: " + GLES31.glGetShaderInfoLog(vertexShaderHandle));
            }

            liveViewFragmentShaderHandle = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
            GLES31.glShaderSource(liveViewFragmentShaderHandle, fragmentShaderCode);
            GLES31.glCompileShader(liveViewFragmentShaderHandle);
            GLES31.glGetShaderiv(liveViewFragmentShaderHandle, GLES31.GL_COMPILE_STATUS, status, 0);
            if(status[0] == GLES31.GL_FALSE){
                Log.d("Shader","Live fragment Shader: " + GLES31.glGetShaderInfoLog(liveViewFragmentShaderHandle));
            }

            liveViewShaderProgram = GLES31.glCreateProgram();
            GLES31.glAttachShader(liveViewShaderProgram, vertexShaderHandle);
            GLES31.glAttachShader(liveViewShaderProgram, liveViewFragmentShaderHandle);
            GLES31.glLinkProgram(liveViewShaderProgram);

            GLES31.glGetProgramiv(liveViewShaderProgram, GLES31.GL_LINK_STATUS, status, 0);
            if (status[0] != GLES31.GL_TRUE) {
                String error = GLES31.glGetProgramInfoLog(liveViewShaderProgram);
                Log.d("Shader error", "Live shader program link error: " + error);
            }

            //load tracking shader program
            try {
                InputStream fis = assetManager.open("ThresholdShader2.fsh");
                InputStreamReader fisr = new InputStreamReader(fis);
                BufferedReader fbr = new BufferedReader(fisr);
                StringBuilder fsb = new StringBuilder();
                String next;
                while ((next = fbr.readLine()) != null) {
                    fsb.append(next);
                    fsb.append('\n');
                }
                fragmentShaderCode = fsb.toString();
            } catch (IOException e) {
                Log.d("Analyzing load error:", e.getMessage());
                return;
            }

            analyzingFragmentShaderHandle = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
            GLES31.glShaderSource(analyzingFragmentShaderHandle, fragmentShaderCode);
            GLES31.glCompileShader(analyzingFragmentShaderHandle);
            GLES31.glGetShaderiv(analyzingFragmentShaderHandle, GLES31.GL_COMPILE_STATUS, status, 0);
            if(status[0] == GLES31.GL_FALSE){
                Log.d("Shader","Fragment Shader: " + GLES31.glGetShaderInfoLog(analyzingFragmentShaderHandle));
            }

            analyzingShaderProgram = GLES31.glCreateProgram();
            GLES31.glAttachShader(analyzingShaderProgram, vertexShaderHandle);
            GLES31.glAttachShader(analyzingShaderProgram, analyzingFragmentShaderHandle);
            GLES31.glLinkProgram(analyzingShaderProgram);

            GLES31.glGetProgramiv(analyzingShaderProgram, GLES31.GL_LINK_STATUS, status, 0);
            if (status[0] == GLES31.GL_FALSE) {
                String error = GLES31.glGetProgramInfoLog(analyzingShaderProgram);
                Log.d("Shader error", "Analyzing shader program link error: " + error);
            }

            //load compute shader program
            /*try {
                InputStream cis = assetManager.open("LuminanceShader.csh");
                InputStreamReader cisr = new InputStreamReader(cis);
                BufferedReader cbr = new BufferedReader(cisr);
                StringBuilder csb = new StringBuilder();
                String next;
                while((next = cbr.readLine()) != null){
                    csb.append(next);
                    csb.append('\n');
                }
                computeShaderCode = csb.toString();
            }
            catch(IOException e){
                Log.d("Shader loading error: ", e.getMessage());
                return;
            }

            int err = GLES31.glGetError();
            Log.d("Luminnce prog", "" + err);
            computeShaderHandle = GLES31.glCreateShader(GLES31.GL_COMPUTE_SHADER);
            err = GLES31.glGetError();
            Log.d("Luminnce prog", "" + err);
            GLES31.glShaderSource(computeShaderHandle, computeShaderCode);
            err = GLES31.glGetError();
            Log.d("Luminnce prog", "" + err);
            GLES31.glCompileShader(computeShaderHandle);
            err = GLES31.glGetError();
            Log.d("Luminnce prog", "" + err);
            GLES31.glGetShaderiv(computeShaderHandle, GLES31.GL_COMPILE_STATUS, status, 0);
            if(status[0] == GLES31.GL_FALSE){
                Log.d("Shader","Compute Shader: " + GLES31.glGetShaderInfoLog(computeShaderHandle));
            }

            luminanceShaderProgram = GLES31.glCreateProgram();
            err = GLES31.glGetError();
            Log.d("Luminnce prog", "" + err);
            GLES31.glAttachShader(luminanceShaderProgram, computeShaderHandle);
            err = GLES31.glGetError();
            Log.d("Luminnce prog", "" + err);
            GLES31.glLinkProgram(luminanceShaderProgram);
            err = GLES31.glGetError();
            Log.d("Luminnce prog", "" + err);

            GLES31.glGetProgramiv(luminanceShaderProgram, GLES31.GL_LINK_STATUS, status, 0);
            if (status[0] != GLES31.GL_TRUE) {
                String error = GLES31.glGetProgramInfoLog(luminanceShaderProgram);
                Log.d("Shader","Compute Program: " + error);
            }

            computeBuffers = new int[1];
            ByteBuffer buffer = ByteBuffer.allocateDirect(256*4);
            buffer.order(ByteOrder.nativeOrder());
            lumVal = buffer.asIntBuffer();
            GLES31.glGenBuffers(1, computeBuffers, 0);
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, computeBuffers[0]);
            GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, 256 * 4, lumVal, GLES31.GL_DYNAMIC_COPY);
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, 0);*/

            pixelBuffer = new int[1];
            int size = screenWidth * screenHeight * 4;
            pixelData = ByteBuffer.allocateDirect(size);
            pixelData.order(ByteOrder.nativeOrder());
            GLES31.glGenBuffers(1, pixelBuffer, 0);
            GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, pixelBuffer[0]);
            GLES31.glBufferData(GLES31.GL_PIXEL_PACK_BUFFER, size, pixelData, GLES31.GL_DYNAMIC_READ);
            GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, 0);
        }

        private void setColor() {
            Log.d("Camera", "Cam texture: " + previewSize.width + ", " + previewSize.height);

            ByteBuffer buff = ByteBuffer.allocateDirect(4);
            buff.order(ByteOrder.nativeOrder());
            GLES31.glReadPixels((int) xChoice, (int) yChoice, 1, 1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, buff);
            colorSelected[0][0] = (buff.get(0) & 0xff) / 255.0f;
            colorSelected[0][1] = (buff.get(1) & 0xff) / 255.0f;
            colorSelected[0][2] = (buff.get(2) & 0xff) / 255.0f;
            colorSelected[0][3] = (buff.get(3) & 0xff) / 255.0f;
            setColorChoice = false;
            colorSet = true;
            colorsTracked = 1;
            setViewType(TRACKING_VIEW);
            Log.d("Color Selected", "" + colorSelected[0][0] + ", " + colorSelected[0][1] + ", " + colorSelected[0][2] +
                ", " + colorSelected[0][3]);

            //make grayscale vec3(0.2125, 0.7154, 0.721);
            float gray = colorSelected[0][0] * 0.2125f +
                         colorSelected[0][1] * 0.7154f +
                         colorSelected[0][2] * 0.0721f;
            colorSelected[0][0] = gray;
            colorSelected[0][1] = gray;
            colorSelected[0][2] = gray;
            colorSelected[0][3] = 1.0f;
        }

        private void computeLuminance(){
            GLES31.glUseProgram(luminanceShaderProgram);
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, computeBuffers[0]);
            GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureHandle[0]);
            //GLES31.glBindImageTexture(0, textureHandle[0], 0, false, 0, GLES31.GL_READ_ONLY, GLES31.GL_R32F);


            GLES31.glDispatchCompute(previewSize.width, previewSize.height, 1);
            //wait for compute to finish
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);

            //Log.d("Luminance array 0", "" + lumVal.get(0));

            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER);

            //int[] array = lumVal.array();
            /*for(int i = 0; i < 256; i++)
                Log.d("Luminance array " + i + ": ", "" + lumVal.get(i));*/
        }

        private void setColorByLiveCalibration(){

        }

        private void setColorBySelection(){

        }

        private void analyzeModifiedTexture(){
            GLES31.glReadPixels(0, 0, screenWidth, screenHeight, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, pixelData);
            analyzer.analyzeBuffer();
            /*int totalPixels = screenWidth * screenHeight;
            //ByteBuffer buff = ByteBuffer.allocateDirect(screenWidth * screenHeight * 4);
            //buff.order(ByteOrder.nativeOrder());
            Date before = new Date();
            GLES31.glReadPixels(0, 0, screenWidth, screenHeight, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, pixelData);
            Date after = new Date();
            Log.d("Time to load", "" + (after.getTime() - before.getTime()) + "ms");
            for(int i = 0; i < totalPixels; i+=4){

            }
            after = new Date();
            Log.d("Time to load and step", "" + (after.getTime() - before.getTime()) + "ms");*/
        }

        private void moveMotor(float frameDistance){

        }

    }

    public class Analyzer extends Thread {
        private boolean run = false;
        private boolean ready = false;
        private boolean analyzed = false;
        private boolean firstPass = true;
        int screenWidth, screenHeight;
        TrackingData trackingData;
        int leftBoundary;
        int rightBoundary;

        public Analyzer(int sw, int sh){
            screenWidth = sw;
            screenHeight = sh;
            leftBoundary = (screenWidth / 2) - screenWidth / 10;
            rightBoundary = (screenWidth / 2) + screenWidth / 10;
        }

        public void analyzeBuffer(){
            //data = buffer;
            ready = true;
        }

        public void startAnalyzer(){
            run = true;
            start();
        }

        public void stopAnalyzer(){
            run = false;
        }

        public boolean isReady(){
            return ready;
        }

        public boolean isAnalyzed(){
            return analyzed;
        }

        private void moveMotor(float frameDistance){
            //figure out how far we need to step motor based on frameDistance and screen res and tell the
            //pi system to do so via the bluetooth connection
        }

        @Override
        public void run() {
            while(run){
                if(ready){
                    analyzed = false;
                    //initialize a new trackingData object
                    TrackingData newData = new TrackingData();
                    newData.currentPosition[0] = screenWidth + 1; //left
                    newData.currentPosition[1] = -1; //right
                    newData.currentPosition[2] = -1; //bottom
                    newData.currentPosition[3] = screenHeight + 1; //top

                    //go through the buffer pixel by pixel - every 4th position is a new pixel in the order
                    //red, green, blue, alpha
                    int totalPixels = screenWidth * screenHeight;
                    Date before = new Date(); //for testing
                    float r,g,b,a;
                    int[] coordinate = new int[2]; //x,y
                    int pixel;
                    for(int i = 0; i < totalPixels; i+=4){
                        pixel = i / 4;
                        coordinate[0] = pixel % screenWidth;
                        coordinate[1] = pixel / screenWidth;
                        //r = pixelData.get(i);
                        //g = pixelData.get(i+1);
                        //b = pixelData.get(i+2);
                        a = pixelData.get(i+3);
                        if(a == 1){
                            //alpha 1 means it is a tracked color

                            //update position as we step
                            //TODO: factor in previous position and speed to catch wrong subjects, find a way to group pixels into objects
                            if(coordinate[0] < newData.currentPosition[0]) { //pixel is farther left than a previous found one
                                newData.currentPosition[0] = coordinate[0];
                            }
                            else if(coordinate[0] > newData.currentPosition[1]) { //pixel is farther right than a previous found one
                                newData.currentPosition[1] = coordinate[0];
                            }
                            if(coordinate[1] < newData.currentPosition[2]) { //pixel is farther below a previous found one
                                newData.currentPosition[2] = coordinate[1];
                            }
                            else if(coordinate[1] > newData.currentPosition[3]) { //pixel is farther above a previous found one
                                newData.currentPosition[3] = coordinate[1];
                            }
                        }
                    }
                    if(firstPass){
                        trackingData = new TrackingData();
                        trackingData.currentPosition[0] = newData.currentPosition[0];
                        trackingData.currentPosition[1] = newData.currentPosition[1];
                        trackingData.currentPosition[2] = newData.currentPosition[2];
                        trackingData.currentPosition[3] = newData.currentPosition[3];
                        trackingData.currentSpeed[0] = 0;
                        trackingData.currentSpeed[1] = 0;
                        firstPass = false;
                    }
                    //TODO: check surrounding potential colors and update arraylist


                    //calculate speed as average of change in pixels of boundaries. This should account
                    //for rotation as spinning in place should average to zero for example.
                    float[] xSpeed = new float[2];
                    float[] ySpeed = new float[2];
                    xSpeed[0] = newData.currentPosition[0] - trackingData.currentPosition[0];
                    xSpeed[1] = newData.currentPosition[1] - trackingData.currentPosition[1];
                    ySpeed[0] = newData.currentPosition[2] - trackingData.currentPosition[2];
                    ySpeed[1] = newData.currentPosition[3] - trackingData.currentPosition[3];
                    newData.currentSpeed[0] = (xSpeed[0] + xSpeed[1]) / 2.0f;
                    newData.currentSpeed[1] = (ySpeed[0] + ySpeed[1]) / 2.0f;

                    //update trackingData
                    trackingData = newData;

                    Date after = new Date(); //for testing
                    Log.d("Time to analyze", "" + (after.getTime() - before.getTime()) + "ms"); //for testing

                    //call movemotor with the difference between position boundary pixel and "center window" with
                    //- being left move needed and + being right move needed
                    int diff = 0;
                    if(trackingData.currentPosition[0] < leftBoundary && trackingData.currentPosition[1] > rightBoundary){
                        //do nothing - too big to fit in "center window"
                    }
                    else if(trackingData.currentPosition[0] < leftBoundary)
                        diff = trackingData.currentPosition[0] - leftBoundary;
                    else if(trackingData.currentPosition[1] > rightBoundary)
                        diff = trackingData.currentPosition[1] - rightBoundary;

                    moveMotor(diff);

                    analyzed = true;
                    ready = false;
                }
                else {
                    try {
                        synchronized (this) {
                            wait(100);
                        }
                    }
                    catch(InterruptedException e){
                        Log.d("Analyzer", "Error on wait");
                        ready = false;
                        run = false;
                    }
                }
            }
        }
    }
}

