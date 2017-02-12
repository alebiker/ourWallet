package it.abapp.mobile.shoppingtogether.ocr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.albori.android.utilities.Utilities;

/**
 * Created by alex on 09/10/15.
 */
public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final float minScale = 1;
    private static final float maxScale = 3  ;
    private static final String TAG = "HighlighterSurfaceView";
    private float redundantXSpace;
    private float redundantYSpace;
    private float mBmpWidth, mBmpHeight;
    private float bottom;
    private float right;
    private float scaleMappingRatio;
    private int bmWidth, bmHeight;
    private boolean initialized;
    private float focusX;
    private float focusY;
    private float startY,startX;
    private float nearEdgeX,nearEdgeY;
    private boolean inSelection, inHightlighting;
    private Paint bitmapShape;
    private Paint paintShapeName;
    private Paint paintShapePrice;
    private Paint paintShape;
    private Paint paintShapeDetName;
    private Paint paintShapeDetPrice;
    private boolean needInitialize;
    private int srfWidth, srfHeight;


    private class ScaleVector {
        float x;
        float y;
        float z;
    }

    private Bitmap bitmap;
    private RectF mRect;

    /** List of rect in scaled bitmap reference that represent the selected part of image */
    private List<RectF> mNameRects;
    private List<RectF> mPriceRects;
    private HashMap<Integer,Map.Entry<RectF,Boolean>> mNameDetRects;
    private HashMap<Integer,Map.Entry<RectF,Boolean>> mPriceDetRects;
    private MyThread thread;

    private Uri pathBitmap;
    private ScaleGestureDetector detector;

    /** Matrix that represent all the transformation (translation,scaling) from the original
     * image to the actual representation in the view.
     * */
    private Matrix mCanvasTranf;

    /** matrix that represent the scale (tipically down) from the full-resolution bitmap to the imported one */
    private Matrix mImportImgT;
    private Matrix mBitmapMatrix;


    float mScaleVector = 1,saveScale = 1;

    public MySurfaceView(Context context) {
        super(context);
        init();
    }

    public MySurfaceView(Context context,
                         AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MySurfaceView(Context context,
                         AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void init() {
        mRect = new RectF();
        mNameRects = new ArrayList<>();
        mPriceRects = new ArrayList<>();
        mNameDetRects = new HashMap<>();
        mPriceDetRects = new HashMap<>();
        thread = new MyThread(getHolder(), this);
        getHolder().addCallback(this);
        detector = new ScaleGestureDetector(getContext(), new ScaleListener());
        setFocusable(true);
        initialized = false;
        inSelection = false;
        inHightlighting = false;
        mCanvasTranf = new Matrix();
        mImportImgT = new Matrix();
        mBitmapMatrix = new Matrix();
        mCanvasTranf = new Matrix();
        setShaper();
    }

    public void setImage(Uri imgUri){
        pathBitmap = imgUri;
        //Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Width, Width, true);
        //this.bitmap = scaled;
    }

    private void setShaper(){
        bitmapShape = new Paint();
        bitmapShape.setAntiAlias(true);
        bitmapShape.setFilterBitmap(true);
        bitmapShape.setDither(true);

        paintShapeName = new Paint();
        paintShapeName.setColor(Color.RED);
        paintShapeName.setStyle(Paint.Style.STROKE);
        paintShapeName.setStrokeWidth(2);

        paintShapePrice = new Paint();
        paintShapePrice.setColor(Color.YELLOW);
        paintShapePrice.setStyle(Paint.Style.STROKE);
        paintShapePrice.setStrokeWidth(2);

        paintShapeDetName = new Paint();
        paintShapeDetName.setColor(Color.GRAY);
        paintShapeDetName.setStyle(Paint.Style.STROKE);
        paintShapeDetName.setStrokeWidth(2);

        paintShapeDetPrice = new Paint();
        paintShapeDetPrice.setColor(Color.GRAY);
        paintShapeDetPrice.setStyle(Paint.Style.STROKE);
        paintShapeDetPrice.setStrokeWidth(2);

        paintShape = new Paint();
        paintShape.setColor(Color.BLACK);
        paintShape.setStyle(Paint.Style.STROKE);
        paintShape.setStrokeWidth(3);
    }


    public void enterInSelection() {
        inSelection = true;
    }

    public void endSelection() {
        inSelection = false;
        mRect = null;
    }

    public void enterHighlighting() {
        inHightlighting = true;

        int contrast = 1;
        int brightness = -150;
        ColorMatrix cm = new ColorMatrix(new float[]
                {
                        contrast, 0, 0, 0, brightness,
                        0, contrast, 0, 0, brightness,
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                });

        bitmapShape.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    public void endHighlighting() {
        inHightlighting = false;

        int contrast = 1;
        int brightness = 5;
        ColorMatrix cm = new ColorMatrix(new float[]
                {
                        contrast, 0, 0, 0, brightness,
                        0, contrast, 0, 0, brightness,
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                });

        bitmapShape.setColorFilter(new ColorMatrixColorFilter(cm));

        for(Map.Entry<RectF,Boolean> rect : this.mNameDetRects.values()){
            rect.setValue(false);
        }
        for(Map.Entry<RectF,Boolean> rect : this.mPriceDetRects.values()){
            rect.setValue(false);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(!initialized)
            return;

            //Bitmap scaled = Bitmap.createScaledBitmap(rawBitmap, Width, height, false);
        if(bitmap != null){

            canvas.save();

            //bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.my_pic);
            //canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.drawColor(Color.BLUE);//To make background

            // apply scaling/translation to fit bitmap
            canvas.concat(mBitmapMatrix);
            canvas.concat(mCanvasTranf);

            canvas.drawBitmap(bitmap, 0, 0, bitmapShape);

            // print all set name and price rects
            for(RectF name : mNameRects)
                canvas.drawRect(name, paintShapeName);

            for (RectF price : mPriceRects)
                canvas.drawRect(price, paintShapePrice);

            // print all set name and price rects
            for (Map.Entry<RectF,Boolean> name : mNameDetRects.values()) {
                    canvas.drawRect(name.getKey(), name.getValue() ? paintShapeName : paintShapeDetName);
            }

            for (Map.Entry<RectF,Boolean> price : mPriceDetRects.values()) {
                    canvas.drawRect(price.getKey(), price.getValue() ? paintShapePrice : paintShapeDetName);
            }

            // draw selection rect
            if(inSelection && mRect != null)
                synchronized (mRect) {
                    canvas.drawRect(mRect, paintShape);
                }

            float[] values = new float[9];
            mCanvasTranf.getValues(values);

            canvas.restore();
            //Log.d("OnDraw - SCALING", "scalex" + Float.toString(values[0]));
            //Log.d("OnDraw - SCALING", "scaley" + Float.toString(values[4]));
            //Log.d("OnDraw - TRANS", "transx" + values[2]);
            //Log.d("OnDraw - TRANS", "transy" + values[5]);
        }

    }


    public void pause() {
        // kill drawing thread
        boolean retry = true;
        thread.startrun(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                // we will try it again and again...
            }
        }
    }

    public void resume() {
        thread = new MyThread(getHolder(), this);
    }

    private void initialize(int width, int height){
        try {
            srfWidth = width;
            srfHeight = height;
            Matrix scale = new Matrix();
            bitmap = Utilities.loadImage(pathBitmap, width, height, scale);
            mImportImgT.set(scale);

            initBitmapMatrix(width, height);
            initialized = true;
        }catch(Exception e){
            Log.e(TAG, "Errors occurs in initialization!");
        }
    }

    /** Init the BitmapMatrix to fit the imported bitmap to the canvas space */
    private void initBitmapMatrix(int surfaceWidth, int surfaceHeight) {
        // Fit to screen.
        if(bitmap != null) {
            float scale;
            float scaleX = (float) surfaceWidth / (float) bitmap.getWidth();
            float scaleY = (float) surfaceHeight / (float) bitmap.getHeight();
            scale = Math.min(scaleX, scaleY);
            mBitmapMatrix.setScale(scale, scale);
            scaleMappingRatio = saveScale / scale;

            // Center the image
            redundantYSpace = (float) surfaceHeight - (scale * (float) bitmap.getHeight());
            redundantXSpace = (float) surfaceWidth - (scale * (float) bitmap.getWidth());
            redundantYSpace /= (float) 2;
            redundantXSpace /= (float) 2;

            mBitmapMatrix.postTranslate(redundantXSpace, redundantYSpace);

            mBmpWidth = surfaceWidth - 2 * redundantXSpace;
            mBmpHeight = surfaceHeight - 2 * redundantYSpace;
            right = surfaceWidth * saveScale - surfaceWidth - (2 * redundantXSpace * saveScale);
            bottom = surfaceHeight * saveScale - surfaceHeight - (2 * redundantYSpace * saveScale);
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int Width, int height) {
        initialize(Width, height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        thread.startrun(true);
        thread.start();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        boolean retry = true;
        thread.startrun(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                // we will try it again and again...
            }
        }

    }

    public void setActiveRect(RectF rect) {
        if(rect == null){
            this.mRect = null;
            return;
        }
        if(mRect != null){
            synchronized (mRect) {
                mRect = getRectFromScreenToScaledImg(rect);
            }
        }
        else
            mRect = getRectFromScreenToScaledImg(rect);
    }

    /** Set highlighted a previously detected rect */
    public void setHighlightedRect(int rectHash){
        if(mNameDetRects.containsKey(rectHash)) {
            mNameDetRects.get(rectHash).setValue(true);
            return;
        }
        if(mPriceDetRects.containsKey(rectHash))
            mPriceDetRects.get(rectHash).setValue(true);
    }

    /** Input a rect referenced to the full-res image, scaling it and adds to the name rect list */
    public void addNameRect(RectF rect) {
        if(rect == null){
            Log.e(TAG, "Null rect not accepted!");
            return;
        }
        synchronized (mNameRects) {
            RectF scaledRect = new RectF();
            mImportImgT.mapRect(scaledRect, rect);
            mNameRects.add(scaledRect);
        }
    }

    /** Input a rect referenced to the full-res image, scaling it and adds to the price rect list */
    public void addPriceRect(RectF rect) {
        if(rect == null){
            Log.e(TAG, "Null rect not accepted!");
            return;
        }
        synchronized (mPriceRects) {
            RectF scaledRect = new RectF();
            mImportImgT.mapRect(scaledRect, rect);
            mPriceRects.add(scaledRect);
        }
    }

    public RectF getRectFromScreenToScaledImg(RectF rect){
        RectF scaledRect = new RectF();
        RectF origRect = new RectF();
        Matrix iMatrix = new Matrix();
        if(mCanvasTranf.invert(iMatrix)) {
            iMatrix.mapRect(scaledRect, rect);
        }
        if(mBitmapMatrix.invert(iMatrix)) {
            iMatrix.mapRect(origRect, scaledRect);
        }
        return origRect;
    }

    public RectF getRectFromScaledImgToFullResImg(RectF rect){
        RectF origRect = new RectF();
        Matrix iScaled = new Matrix();
        if(mImportImgT.invert(iScaled)) {
            mImportImgT.invert(iScaled);
            iScaled.mapRect(origRect, rect);
        }
        return origRect;
    }

    /** Project screen points to full resolution image pixel */
    public int[] projScreen2FullImage(float[] touchPoint) {
        float[] scaledImagePoint = new float[touchPoint.length];
        float[] origImagePoint = new float[touchPoint.length];
        float[] fullImagePoint = new float[touchPoint.length];
        int[] fullImagePixel = new int[touchPoint.length];
        Matrix iMatrix = new Matrix();

        if(mCanvasTranf.invert(iMatrix))
            iMatrix.mapPoints(scaledImagePoint, touchPoint);

        if (mBitmapMatrix.invert(iMatrix))
            iMatrix.mapPoints(origImagePoint, scaledImagePoint);

        if(mImportImgT.invert(iMatrix))
            iMatrix.mapPoints(fullImagePoint, origImagePoint);

        for(int i = 0; i < fullImagePoint.length; i++)
            fullImagePixel[i] = (int) fullImagePoint[i];
        return fullImagePixel;
    }

    /** Import a new detected name rect in full-res image reference */
    public void addDetectedNameText(int rectHash, Rect rect) {
        RectF rectF = new RectF(rect);
        this.mImportImgT.mapRect(rectF);
        Map.Entry<RectF, Boolean> entry = new AbstractMap.SimpleEntry<>(rectF, false);
        this.mNameDetRects.put(rectHash, entry);
    }

    public void addDetectedPriceText(int rectHash, Rect rect) {
        RectF rectF = new RectF(rect);
        this.mImportImgT.mapRect(rectF);
        Map.Entry<RectF, Boolean> entry = new AbstractMap.SimpleEntry<>(rectF, false);
        this.mPriceDetRects.put(rectHash, entry);
    }

    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getAction() & MotionEvent.ACTION_MASK;
        int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;

        if (event.getPointerCount() == 2) {
            if (action == MotionEvent.ACTION_POINTER_DOWN && pointerIndex == 1) {
                // The various pivot coordinate codes would belong here
            }
        } else if(event.getPointerCount() == 1){
            // if is a translation action
            switch (action){
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    //Log.d("DOWN", "x" + Float.toString(startX));
                    //Log.d("DOWN", "y" + Float.toString(startY));
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = 0, dy = 0;
                    if(event.getHistorySize() > 0){
                        if(inSelection || inHightlighting){
                           if(event.getX() * scaleMappingRatio < nearEdgeX){
                               //TODO launch thread that continue to translate to left
                               // until a MOVE out of the edgeRegion or ACTION.UP
                           }else if (srfWidth * scaleMappingRatio  - event.getX() < nearEdgeX){
                               //TODO launch thread that continue to translate to right
                           }

                        }else {
                            /*
                            Log.d("MOVE", "history size" + event.getHistorySize());
                            for (int i = 0; i < event.getHistorySize(); i++) {
                                Log.d("MOVE", "hx" + event.getHistoricalX(i));
                                Log.d("MOVE", "hy" + event.getHistoricalY(i));
                            }
                            */
                            dx = event.getX() - event.getHistoricalX(0);
                            dy = event.getY() - event.getHistoricalY(0);
                        }
                    }
                    startX = event.getX();
                    startY = event.getY();
                    //Log.d("MOVE", "x" + Float.toString(startX));
                    //Log.d("MOVE", "y" + Float.toString(startY));
                    //Log.d("MOVE", "dx" + Float.toString(dx));
                    //Log.d("MOVE", "dy" + Float.toString(dy));
                    //Log.d("MOVE", "history size" + event.getHistorySize());
                    mCanvasTranf.postTranslate(dx, dy);

                    printTranslation();

                    break;
            }
        }

        detector.onTouchEvent(event); // Calls the Scale Gesture Detector
        return true;
    }

    private void printTranslation() {
        float[] m = new float[9];
        mCanvasTranf.getValues(m);
        //Log.d("TRANSLATE", "dx" + m[Matrix.MTRANS_X]);
        //Log.d("TRANSLATE", "dy" + m[Matrix.MTRANS_Y]);
    }

    private void printScale() {
        float[] m = new float[9];
        mCanvasTranf.getValues(m);
        //Log.d("SCALE", "dx" + m[Matrix.MSCALE_X]);
        //Log.d("SCALE", "dy" + m[Matrix.MSCALE_Y]);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        private float[] m = new float[9];

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = (float) Math.min(
                    Math.max(.8f, detector.getScaleFactor()), 1.2);
            float origScale = saveScale;
            saveScale *= mScaleFactor;
            if (saveScale > maxScale) {
                saveScale = maxScale;
                mScaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }

            right = srfWidth * saveScale - srfWidth
                    - (2 * redundantXSpace * saveScale);
            bottom = srfHeight * saveScale - srfHeight
                    - (2 * redundantYSpace * saveScale);
            if (mBmpWidth * saveScale <= srfWidth
                    || mBmpHeight * saveScale <= srfHeight) {
                mCanvasTranf.postScale(mScaleFactor, mScaleFactor, srfWidth / 2, srfHeight / 2);
                if (mScaleFactor < 1) {
                    mCanvasTranf.getValues(m);
                    float x = m[Matrix.MTRANS_X];
                    float y = m[Matrix.MTRANS_Y];
                    if (mScaleFactor < 1) {
                        if (Math.round(mBmpWidth * saveScale) < srfWidth) {
                            if (y < -bottom)
                                mCanvasTranf.postTranslate(0, -(y + bottom));
                            else if (y > 0)
                                mCanvasTranf.postTranslate(0, -y);
                        } else {
                            if (x < -right)
                                mCanvasTranf.postTranslate(-(x + right), 0);
                            else if (x > 0)
                                mCanvasTranf.postTranslate(-x, 0);
                        }
                    }
                }
            } else {
                mCanvasTranf.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
                mCanvasTranf.getValues(m);
                float x = m[Matrix.MTRANS_X];
                float y = m[Matrix.MTRANS_Y];
                if (mScaleFactor < 1) {
                    if (x < -right)
                        mCanvasTranf.postTranslate(-(x + right), 0);
                    else if (x > 0)
                        mCanvasTranf.postTranslate(-x, 0);
                    if (y < -bottom)
                        mCanvasTranf.postTranslate(0, -(y + bottom));
                    else if (y > 0)
                        mCanvasTranf.postTranslate(0, -y);
                }
            }

            //Log.d("SCALING","mScaleFactor"+mScaleFactor);
            printScale();
            return true;
        }
    }

    public class MyThread extends Thread {

        private SurfaceHolder msurfaceHolder;
        private MySurfaceView mSurfaceView;
        private boolean mrun = false;

        public MyThread(SurfaceHolder holder, MySurfaceView mSurfaceView) {

            this.msurfaceHolder = holder;
            this.mSurfaceView = mSurfaceView;
        }

        public void startrun(boolean run) {

            mrun = run;
        }

        @SuppressLint("WrongCall")
        @Override
        public void run() {

            super.run();
            Canvas canvas;
            while (mrun) {
                canvas = null;
                try {
                    canvas = msurfaceHolder.lockCanvas(null);
                    synchronized (msurfaceHolder) {
                        mSurfaceView.onDraw(canvas);
                    }
                } finally {
                    if (canvas != null) {
                        msurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    }

}
