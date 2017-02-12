package it.alborile.mobile.ocr.client;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import com.albori.android.utilities.Utilities;


/**
 * Class that represent the recognition instance of the bitmap in specific rects
 * Can be used to perform ocr scanning on only specified parts of bitmap
* */

abstract public class OCRWork implements OCRWorkProcessor.CompletionCallback,OCRWorkProcessor.ResultCallback {

    private final List<Rect> rects;
    private final Bitmap mBitmap;
    private final Uri mPath;
    private Ocr.Parameters mParams;
    private OCRWorkProcessor mParentProcessor;
    private String name;
    private boolean isFullAnalysis;

    private static final String TAG  = "OCRWork";
    private Status mStatus = null;
    private Setup mSetup = null;

    private HashMap<Long, Rect> mTaskId;

    private enum Status{
        PENDING, PROCESSING, COMPLETED, ABORTED, RELEASED
    }

    private enum Setup{
        IMG, IMG_PATH, PATH
    }

    public OCRWork(Bitmap image, Ocr.Parameters params) {
        this(image, params, null);
    }

    public OCRWork(Uri imgPath, Ocr.Parameters params) {
        this(imgPath,null,params,null);
    }

    public OCRWork(Bitmap image, Ocr.Parameters params, List<Rect> rects) {
        this(null, image, params, rects);
    }

    public OCRWork(Uri imgPath, Ocr.Parameters params, List<Rect> rect) {
        this(imgPath, null, params, rect);
    }

    public OCRWork(Uri imgPath, Bitmap image, Ocr.Parameters params, List<Rect> rects) {
        this.rects = rects;
        this.mPath = imgPath;
        this.mBitmap = (image != null) ? image.copy(Bitmap.Config.ARGB_8888, false) : null;
        this.mParams = params;
        this.mStatus = Status.PENDING;
        mTaskId = new HashMap<>();
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

    public void attachProcessor(OCRWorkProcessor processor){
        mParentProcessor = processor;
    }

    public Status getStatus() {
        return mStatus;
    }

    public void setName(String name){
        this.name = name;
    }

    //TODO fix to retrieve file from absolute path
    protected File getImgUri(){
        return new File(Utilities.getInstance(null).getPathFromContentFormat(mPath));
    }

    public void setBitmap(){

    }

    /**
     * Start the OCRWork to enqueue the tasks to the service
     */
    public void start(){
        Log.d(TAG, name+" Sending request..");
        try {
            if (!setupValidation())
                throw new IllegalArgumentException("Bad OCR work setup!");
            mParentProcessor.setParameters(mParams);

            // if there are any rect, send only the path of the image
            if (isFullAnalysis) {
                mParentProcessor.enqueueToService(getImgUri(), this, this);
            } else {
                Bitmap bm;
                if (mBitmap == null) {
                    bm = Utilities.loadImage(this.mPath);
                } else {
                    bm = mBitmap;
                }
                for (Rect rect : rects) {
                    Bitmap region = Bitmap.createBitmap(bm, rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
                    long id = mParentProcessor.enqueueToService(region, this, this);
                    mTaskId.put(id, rect);
                    //TODO recycle if is a copy of bm
                    region.recycle();
                }
            }
            mStatus = Status.PROCESSING;
        }catch (Exception e){
            e.printStackTrace();
            notifyAborted();
        }finally {
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
        isFullAnalysis = (rects == null) ? true : false;
        if (!isFullAnalysis){
            switch (mSetup){
                case IMG_PATH:
                case IMG:
                    valid = validateRects(rects, mBitmap);
                    break;
                case PATH:
                    valid = validateRects(rects, mPath);
                    break;
            }
            if (!valid){
                Log.e(TAG,"Subregion not conform to the whole image!");
            }
        }
        return valid;
    }

    private boolean checkValidBitmap(Bitmap bmp) {
        if (bmp.isRecycled()) {
            Log.d(TAG, "Bitmap is recycled!");
            return false;
        }
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

    /** Validate if the rects specify subregions of the passed image */
    private boolean validateRects(List<Rect> rects, Uri uri){
        int[] imgWidth = new int[1];
        int[] imgHeight = new int[1];
        for (Rect rect : rects){
            if (Utilities.isValidImage(uri, imgWidth, imgHeight)){
                if (!isRectSubregion(rect, imgWidth[0], imgHeight[0]))
                    return false;
            }else {
                throw new IllegalArgumentException("Invalid passed Uri!");
            }
        }
        return true;
    }

    /** Check if the rects are a subregion of the input bitmap */
    private boolean validateRects(List<Rect> rects, Bitmap bmp){
        for (Rect rect : rects) {
            if (!isRectSubregion(rect, bmp.getWidth(), bmp.getHeight()))
                return false;
        }
        return true;
    }

    private void freeResources() {
        if (mBitmap != null)
            mBitmap.recycle();
    }

    public void release(){
        freeResources();
        mStatus = Status.RELEASED;
    }

    /* User method, override this method to handle result when task ends */
    abstract public void processOcrWorkRequest(OCRWorkProcessor processor, Bitmap mBitmap, List<Rect> rects);

    abstract public void onProcessResult(OcrResult results, Rect rect);

    abstract public void onCompletedResults(List<OcrResult> results);



    /* Callbacks called by processor for*/
    @Override
    public void onTaskCompleted(List<OcrResult> results) {
        onCompletedResults(results);
        notifyCompleted();
    }

    @Override
    public void onTaskResult(long id, OcrResult result) {
        Log.d(TAG + "" + name, "Result found");
        Rect rect = mTaskId.get(id);
        onProcessResult(result, rect);
    }

    private void notifyCompleted() {
        mStatus = Status.COMPLETED;
        //mParentProcessor.workFinished();
    }

    private void notifyAborted() {
        mStatus = Status.ABORTED;
        //mParentProcessor.workAborted();
    }


}
