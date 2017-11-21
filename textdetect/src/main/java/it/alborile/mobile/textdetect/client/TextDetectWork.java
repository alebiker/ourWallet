package it.alborile.mobile.textdetect.client;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.List;

import com.albori.android.utilities.Utilities;


/**
 * Class that represent the recognition instance of the bitmap in specific mRect
 * Can be used to perform ocr scanning on only specified parts of bitmap
* */

abstract public class TextDetectWork implements TextDetectWorkProcessor.CompletionCallback {

    private final Rect mRect;
    private final Bitmap mBitmap;
    private final Uri mPath;
    private TextDetector.Parameters mParams;
    private TextDetectWorkProcessor mParentProcessor;
    private String mName;
    private boolean isFullAnalysis;

    private static final String TAG  = "TextDetectionWork";
    private Status mStatus = null;
    private Setup mSetup = null;

    private enum Status{
        PENDING, PROCESSING, COMPLETED, ABORTED, RELEASED
    }

    private enum Setup{
        IMG, IMG_PATH, PATH
    }

    public TextDetectWork(Uri imgPath, TextDetector.Parameters params) {
        this(imgPath, null, params, null);
    }

    public TextDetectWork(Bitmap image, TextDetector.Parameters params, Rect rect) {
        this(null, image, params, rect);
    }

    //TODO(aleb) not fully supported
    public TextDetectWork(Uri imgPath, TextDetector.Parameters params, Rect rect) {
        this(imgPath, null, params, rect);
    }

    public TextDetectWork(Uri imgPath, Bitmap image, TextDetector.Parameters params, Rect rect) {
        this.mPath = imgPath;
        this.mBitmap = (image != null) ? image.copy(Bitmap.Config.ARGB_8888, false) : null;
        this.mParams = params;
        this.mStatus = Status.PENDING;
        this.mRect = rect;
        if (imgPath == null && mBitmap == null)
            throw new IllegalArgumentException("At least one from Bitmap or Uri should be valid!");

        if (mPath != null && mBitmap != null){
            mSetup = Setup.IMG_PATH;
        }else if (mBitmap != null){
            mSetup = Setup.IMG;
        }else {
            mSetup = Setup.PATH;
        }
    }

    public void attachProcessor(TextDetectWorkProcessor processor){
        mParentProcessor = processor;
    }

    public Status getStatus() {
        return mStatus;
    }

    public void setmName(String name){
        this.mName = name;
    }

    protected File getImgFile(){
        return new File(Utilities.getPathFromContentFormat(mPath));
    }

    public void setBitmap(){

    }

    /**
     * Start the TextDetectionWork to send and enqueued to the tex detection service
     */
    public void start(){

        Log.d(TAG, mPath + " Sending request..");
        try {
            if(!setupValidation())
                throw new IllegalArgumentException("Bad TextDetection work setup!");
            mParentProcessor.setParameters(mParams);

            // if there are any rect, send only the path of the image
            if (isFullAnalysis) {
                mParentProcessor.enqueueToService(getImgFile(), this);
            } else {
                Bitmap bm;
                if (mBitmap == null) {
                    bm = Utilities.loadImage(this.mPath);
                } else {
                    bm = mBitmap;
                }
                Bitmap region = Bitmap.createBitmap(bm, mRect.left, mRect.top, mRect.right - mRect.left, mRect.bottom - mRect.top);
                Log.d(TAG, mPath + " enqueued bitmap of " + region.getWidth() + "x" + region.getHeight());
                mParentProcessor.enqueueToService(region, this);
                region.recycle();

            }
            mStatus = Status.PROCESSING;
        }catch(Exception e){
            e.printStackTrace();
            Log.e(TAG, "Error in work initialization, aborted!");
            notifyAborted();
        } finally {
            freeResources();
        }
    }

    /** Check if the passed parameters are conforms */
    private boolean setupValidation(){
        boolean valid = true;
        switch (mSetup){
            case IMG_PATH:
                if (!checkValidBitmap(mBitmap)) return false;
                if (!Utilities.isValidImage(mPath)) return false;
                if (!checkMatchingImages(mBitmap, mPath)) return false;
                break;
            case IMG:
                if (!checkValidBitmap(mBitmap)) return false;
                break;
            case PATH:
                if (!Utilities.isValidImage(mPath)) return false;
                break;
        }
        // Rect validation
        isFullAnalysis = (mRect == null) ? true : false;
        if (!isFullAnalysis){
            switch (mSetup){
                case IMG_PATH:
                case IMG:
                    valid = validateRect(mRect, mBitmap);
                    break;
                case PATH:
                    valid = validateRect(mRect, mPath);
                    break;
            }
            if (!valid){
                Log.e(TAG,"Subregion not conform to the whole image!");
            }
        }
        return valid;
    }

    private boolean checkValidBitmap(Bitmap bmp) {
        if (bmp.isRecycled()) return false;
        if (bmp.getHeight() <= 0 && bmp.getWidth() <= 0) return false;
        return true;
    }

    /** Check if the {@link Uri} path link to the same bmp (resolution checking) */
    private boolean checkMatchingImages(Bitmap bmp, Uri path) {
        if (bmp != null && path != null){
            int[] width = new int[1];
            int[] height = new int[1];
            if (!Utilities.isValidImage(path, width, height))
                return false;
            if (bmp.getWidth() != width[0] || bmp.getHeight() != height[0]
                    && (width[0] == 0 && height[0] == 0))
                return false;
        }else if(bmp != null && (bmp.getWidth() == 0 && bmp.getHeight() == 0)){
            return false;
        }
        return true;
    }

    /** Validate if the rect specify a subregion of the passed image */
    private boolean isRectSubregion(Rect rect, int width, int height){
        rect.sort();
        if(rect.isEmpty() || rect.left + rect.width() > width
                || rect.top + rect.height() > height){
            return false;
        }
        return true;
    }

    /** Validate if the rect specify a subregion of the passed image */
    private boolean validateRect(Rect rect, Uri uri){
        int[] imgWidth = new int[1];
        int[] imgHeight = new int[1];
        if(Utilities.isValidImage(uri, imgWidth, imgHeight)){
            return isRectSubregion(rect, imgWidth[0], imgHeight[0]);
        }else {
            throw new IllegalArgumentException("Invalid passed Uri!");
        }
    }

    /** Check if the rect is a subregion of the input bitmap */
    private boolean validateRect(Rect rect, Bitmap bmp){
        return isRectSubregion(rect, bmp.getWidth(), bmp.getHeight());
    }

    public void release(){
        freeResources();
        mStatus = Status.RELEASED;
    }

    private void freeResources() {
        if (mBitmap != null)
            mBitmap.recycle();
    }

    abstract public void processTextDetectionWorkRequest(TextDetectWork processor, Bitmap mBitmap, List<Rect> rects);

    abstract public void onCompletedResults(List<Rect> results);


    @Override
    public void onCompleted(List<Rect> results) {
        toFullImageProjection(results);
        onCompletedResults(results);
        notifyCompleted();
    }

    /** Project all the result rects from the relative coordinate of bounded rect,
     *  to a whole image system reference if the work was a subregion analysis */
    private void toFullImageProjection(List<Rect> results) {
        if(this.mRect != null) {
            for (Rect rect : results) {
                rect.offset(this.mRect.left, this.mRect.top);
            }
        }
    }

    private void notifyCompleted() {
        mStatus = Status.COMPLETED;
        mParentProcessor.workFinished();
    }

    private void notifyAborted() {
        mStatus = Status.ABORTED;
        mParentProcessor.workAborted();
    }



}
