package it.abapp.mobile.shoppingtogether.ocr;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.TextView;

import it.alborile.mobile.ocr.client.Ocr;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import it.abapp.mobile.shoppingtogether.R;
import it.alborile.mobile.textdetect.client.TextDetectWork;
import it.alborile.mobile.textdetect.client.TextDetectWorkProcessor;
import it.alborile.mobile.textdetect.client.TextDetector;
import com.albori.android.utilities.Utilities;

/* Activity that provid to hilight pieces of the paper
to localize the OCR to a specific part of photo*/

public class HighliterActivity extends AppCompatActivity {

    private static final String BOUNDS_ITEMS = "bounds_items";
    private static final String RECT_NAME = "rect_name";
    private static final String RECT_PRICE = "rect_price";
    private static final String RECT = "rect";
    private static final String INPUT_URI = "input_uri";

    public final static String RECTS = "rect";
    public final static String IMG = "img";
    public static final int OCR_PROCESSING = 1;
    private static final String TAG = "HighliterActivity";
    /** Rect lists in full-res representation */
    private ArrayList<RectF> mNameRectList;
    private ArrayList<RectF> mPriceRectList;
    private List<RectItem> mNameDetected;
    private List<RectItem> mPriceDetected;
    private MySurfaceView surfaceView;
    private SurfaceHolder holder;
    private boolean mInNameSelection, inHiglightMode;
    // Rect representation of selection in screen coordinate
    private RectF mRect;
    private Uri orgImgUri;
    private boolean mInPriceSelection;
    private boolean mInItemSelection;
    private boolean mAlreadyEdited;
    private TextDetectWorkProcessor mTextDetector;
    private TextDetectionAsyncTask mTextDetectAsyncTask;
    private boolean isTextDetectionFinish;
    private int mTextRegionDetected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_highliter);
        // Create first surface with his holder(holder)
        Utilities.getInstance(this);

        surfaceView = (MySurfaceView) findViewById(R.id.ImageView);
        if(savedInstanceState == null){
            mNameRectList = new ArrayList<>();
            mPriceRectList = new ArrayList<>();
            mNameDetected = new ArrayList<>();
            mPriceDetected = new ArrayList<>();
            mRect = new RectF();
            orgImgUri = (Uri) getIntent().getParcelableExtra(Intents.Recognize.EXTRA_INPUT);
        }

        holder = surfaceView.getHolder();
        surfaceView.setOnTouchListener(onTouchSelectionListner);

        /*
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        */
    }


    public void onStart(){
        super.onStart();
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);

        // Restore state members from saved instance
        mNameRectList = savedInstanceState.getParcelableArrayList(RECT_NAME);
        mPriceRectList = savedInstanceState.getParcelableArrayList(RECT_PRICE);
        mRect = (RectF) savedInstanceState.getParcelable(RECT);
        orgImgUri = (Uri) savedInstanceState.getParcelable(INPUT_URI);

    }

    @Override
    protected void onResume(){
        super.onResume();
        setImage(orgImgUri);
        surfaceView.resume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_highliter, menu);
        menu.findItem(R.id.action_pan_mode).setVisible(false);
        menu.findItem(R.id.action_add_item).setVisible(false);
        menu.findItem(R.id.action_sel_name).setVisible(false);
        menu.findItem(R.id.action_sel_price).setVisible(false);
        menu.findItem(R.id.action_done).setVisible(false);
        menu.findItem(R.id.action_detect_text).setVisible(false);
        menu.findItem(R.id.action_start_ocr).setVisible(false);
        if(mInItemSelection){
            menu.findItem(R.id.action_pan_mode).setVisible(true);
            menu.findItem(R.id.action_done).setVisible(true);
        }else{
            menu.findItem(R.id.action_detect_text).setVisible(true);
            menu.findItem(R.id.action_add_item).setVisible(true);
        }
        if(mInNameSelection) {
            menu.findItem(R.id.action_add_item).setVisible(false);
            menu.findItem(R.id.action_sel_price).setVisible(true);
        }
        else if (mInPriceSelection)
        {
            menu.findItem(R.id.action_sel_name).setVisible(true);
            menu.findItem(R.id.action_sel_price).setVisible(false);
        }
        if(inHiglightMode){
            menu.findItem(R.id.action_detect_text).setVisible(false);
            menu.findItem(R.id.action_start_ocr).setVisible(true);
        }



        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        //noinspection SimplifiableIfStatement
        switch (item.getItemId()) {
            case R.id.action_pan_mode:
                inPanMode();
                return true;
            case R.id.action_add_item:
                addNewItemState();
                return true;
            case R.id.action_done:
                inPriceSelection(false);
                confirmAndAddRect();
                return true;
            case R.id.action_start_ocr:
                launchOCR();
                return true;
            case R.id.action_detect_text:
                detectText();
                return true;
            case R.id.action_sel_name:
                inPriceSelection(false);
                inNameSelection(true);
                return true;
            case R.id.action_sel_price:
                inNameSelection(false);
                inPriceSelection(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        surfaceView.pause();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putParcelableArrayList(RECT_NAME, mNameRectList);
        savedInstanceState.putParcelableArrayList(RECT_PRICE, mPriceRectList);
        savedInstanceState.putParcelable(RECT, mRect);
        savedInstanceState.putParcelable(INPUT_URI, orgImgUri);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }


    /* Enter in state for image regions selection of new item
    * */
    private void addNewItemState(){
        surfaceView.enterInSelection();
        mInItemSelection = true;
        mRect = new RectF();
        inNameSelection(true);
    }

    private void inPanMode(){
        if(mInNameSelection)
            inNameSelection(false);
        else if(mInPriceSelection)
            inPriceSelection(false);
        mInItemSelection = false;
        surfaceView.endSelection();
    }

    private void inNameSelection(boolean state) {
        mInNameSelection = state;
        // if is been turned off, save the active rect
        if(!mInNameSelection) {
            saveRectAsName();
            // unset the rect in camera
            clearRect();
        }
        invalidateOptionsMenu();
        Log.d(TAG, (state ? "Enter in" : "Exit from") + " name selection mode..");
    }

    private void inPriceSelection(boolean state) {
        mInPriceSelection = state;
        // if is been turned off, save the active rect
        if(!mInPriceSelection) {
            saveRectAsPrice();
            // unset the rect in camera
            clearRect();
            Log.d(TAG, (state ? "Enter in" : "Exit from") + " price selection mode..");
        }
        invalidateOptionsMenu();
    }

    private void enterHighlighting() {
        inHiglightMode = true;
        ((TextView)findViewById(R.id.activity_higlither_info_text)).setText(getString(R.string.finger_selection_hint));
        surfaceView.enterHighlighting();
        surfaceView.setOnTouchListener(onTouchHiligthingListner);
        invalidateOptionsMenu();
        Log.d(TAG, "Enter in highlighting mode..");
    }

    private void saveRectAsName() {
        // take the designed name
        if(!mRect.isEmpty()) {
            RectF rectTmp = surfaceView.getRectFromScreenToScaledImg(mRect);
            rectTmp = surfaceView.getRectFromScaledImgToFullResImg(rectTmp);
            mNameRectList.add(rectTmp);
            surfaceView.addNameRect(rectTmp);
        }
    }

    private void saveRectAsPrice() {
        // take the designed name
        if(!mRect.isEmpty()) {
            RectF rectTmp = surfaceView.getRectFromScreenToScaledImg(mRect);
            rectTmp = surfaceView.getRectFromScaledImgToFullResImg(rectTmp);
            mPriceRectList.add(rectTmp);
            surfaceView.addPriceRect(rectTmp);
        }
    }

    private void confirmAndAddRect() {
        mInItemSelection = false;
        surfaceView.endSelection();
        clearRect();
    }

    /** Projection from screen reference to full-res image coordinate */
    private void highlightPoint(float touchX, float touchY) {
        float[] screenPoints = new float[]{
                touchX, touchY
        };

        int[] imagePoints = surfaceView.projScreen2FullImage(screenPoints);

        for (RectItem name : mNameDetected) {
            Rect nameRect = name.getRect();
            if (nameRect.contains(imagePoints[0], imagePoints[1])) {
                name.setSelection(true);
                surfaceView.setHighlightedRect(name.hashCode());
            }
        }
        for (RectItem price : mPriceDetected) {
            Rect priceRect = price.getRect();
            if (priceRect.contains(imagePoints[0], imagePoints[1])) {
                price.setSelection(true);
                surfaceView.setHighlightedRect(price.hashCode());
            }
        }
    }

    private void clearHighlighted(){
        surfaceView.endHighlighting();
        for (RectItem name : mNameDetected){
            name.setSelection(false);
        }
        for (RectItem price : mPriceDetected){
            price.setSelection(false);
        }
        inHiglightMode = false;
    }

    /**  */
    private void detectText() {
        mTextDetector = new TextDetectWorkProcessor(this);
        mTextDetectAsyncTask = new TextDetectionAsyncTask();

        // from RectF to Rect
        List<Rect> nameRects = new ArrayList<>();
        List<Rect> priceRects = new ArrayList<>();
        for(RectF rectfItem : mNameRectList) {
            Rect name = new Rect();
            rectfItem.round(name);
            nameRects.add(name);
        }
        for(RectF rectfItem : mPriceRectList) {
            Rect price = new Rect();
            rectfItem.round(price);
            priceRects.add(price);
        }

        if(!nameRects.isEmpty()) {
            TextDetector.Parameters nameDetectParams = new TextDetector.Parameters();
            nameDetectParams.setFlag(TextDetector.Parameters.FLAG_DEBUG_MODE, true);
            nameDetectParams.setFlag(TextDetector.Parameters.FLAG_ALIGN_TEXT, true);

            for (Rect rect : nameRects) {
                TextDetectWork nameDetectWork = new TextDetectWork(this.orgImgUri, nameDetectParams, rect) {
                    @Override
                    public void processTextDetectionWorkRequest(TextDetectWork processor, Bitmap mBitmap, List<Rect> rects) {

                    }

                    @Override
                    public void onCompletedResults(List<Rect> results) {
                        addDetectedNameRects(results);
                    }
                };

                mTextDetector.enqueue(nameDetectWork);
            }

        }

        if (!priceRects.isEmpty()) {
            TextDetector.Parameters priceDetectParams = new TextDetector.Parameters();
            priceDetectParams.setFlag(TextDetector.Parameters.FLAG_ALIGN_TEXT, true);
            priceDetectParams.setFlag(TextDetector.Parameters.FLAG_NUMBER_DETECTION, true);
            priceDetectParams.setFlag(TextDetector.Parameters.FLAG_SMALL_DETECTION, true);

            for(Rect rect : priceRects){
                TextDetectWork priceDetectWork = new TextDetectWork(this.orgImgUri, priceDetectParams, rect) {
                    @Override
                    public void processTextDetectionWorkRequest(TextDetectWork processor, Bitmap mBitmap, List<Rect> rects) {

                    }

                    @Override
                    public void onCompletedResults(List<Rect> results) {
                        addDetectedPriceRects(results);
                    }
                };

                mTextDetector.enqueue(priceDetectWork);
            }

        }

        //TODO start only if some task enqueued
        Utilities.executeAsyncTask(mTextDetectAsyncTask, mTextDetector);
//        mTextDetectAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mTextDetector);
    }

    /** Add full-res rectangle as detected rect */
    private void addDetectedNameRects(List<Rect> results) {
        for (Rect name : results) {
            RectItem rectItem = new RectItem(name);
            mNameDetected.add(rectItem);
            surfaceView.addDetectedNameText(rectItem.hashCode(), name);
        }
        Collections.sort(mNameDetected, new RectItemComparator());
    }

    /** Add full-res rectangle as detected rect */
    private void addDetectedPriceRects(List<Rect> results) {
        for (Rect price : results){
            RectItem rectItem = new RectItem(price);
            mPriceDetected.add(rectItem);
            surfaceView.addDetectedPriceText(rectItem.hashCode(), price);
        }
        Collections.sort(mPriceDetected, new RectItemComparator());
    }

    private void launchOCR() {
        //TODO sort rect to attach one2one
        Intent detectIntent = new Intent(getApplicationContext(), OCRActivity.class);
        // from RectF to Rect
        HashMap<Rect, Rect> rectsMap = new HashMap<>();
        Rect name = null, price = null;
        Iterator<RectItem> nameIter = mNameDetected.iterator();
        Iterator<RectItem> priceIter = mPriceDetected.iterator();
        while (nameIter.hasNext()) {
            RectItem nameentry = nameIter.next();
            if (nameentry.isSelected()) {
                name = nameentry.getRect();
                while (priceIter.hasNext()) {
                    RectItem priceEntry = priceIter.next();
                    if (priceEntry.isSelected()) {
                        price = priceEntry.getRect();
                        break;
                    }
                }
            }
            if (name != null && price != null) {
                name.sort();
                price.sort();
                rectsMap.put(name, price);
            }
        }

        detectIntent.putExtra(Intents.Recognize.EXTRA_RECTS, rectsMap);

        Uri outPath;
        //TODO temporary disable the conversion to tess format
        /*
        if(!rectsMap.isEmpty())
            outPath = convertingImgToTessCompatible(orgImgUri);
        else
            outPath = orgImgUri;
            */

        outPath = orgImgUri;

        //Ver.2 eyes-free
        Ocr.Parameters params = new Ocr.Parameters();
        params.setLanguage("eng");
        params.setFlag(Ocr.Parameters.FLAG_DETECT_TEXT, false);
        params.setFlag(Ocr.Parameters.FLAG_SPELLCHECK, false);
        detectIntent.putExtra(Intents.Recognize.EXTRA_INPUT, outPath);
        detectIntent.putExtra(Intents.Recognize.EXTRA_PARAMETERS, params);

        startActivityForResult(detectIntent, OCR_PROCESSING);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OCR_PROCESSING ){
            switch (resultCode){
                case RESULT_OK:
                    setResult(RESULT_OK, data);
                    finish();
                    break;
                case OCRActivity.RESULT_EDIT:
                    // case of back press of user, refine the rects
                    break;
                case RESULT_CANCELED:
                    setResult(RESULT_CANCELED, null);
                    finish();
                    break;
            }
        }
    }


    //TODO Convert picked image to tess compatible image, discover if it is still need
    private Uri convertingImgToTessCompatible(Uri uriImg) {

        //Bitmap bitmap = Utils.fullResolution(mCurrentPhotoPath);
        //BitmapFactory.Options options = new BitmapFactory.Options();

        //No downsampling
        //options.inSampleSize = 4;

        try {
            Bitmap bitmap = Utilities.loadImage(uriImg);
            //InputStream imageStream = getContentResolver().openInputStream(uriImg);
            //Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);

            int orientation = 0;

            String scheme = uriImg.getScheme();
            if (scheme.equals("file")) {
                String imgPath = uriImg.getSchemeSpecificPart();
                ExifInterface exif = new ExifInterface(imgPath);
                orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);

            }
            else if (scheme.equals("content")){
                /* it's on the external media. */
                Cursor cursor = getContentResolver().query(uriImg,
                        new String[] { MediaStore.Images.ImageColumns.ORIENTATION }, null, null, null);

                if (cursor.getCount() != 1) {
                    throw new Exception();
                }

                cursor.moveToFirst();
                orientation = cursor.getInt(0);
            }


            Log.v(TAG, "Orient: " + orientation);

            int rotate = 0;

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }

            Log.v(TAG, "Rotation: " + rotate);

            if (rotate != 0) {

                // Getting width & height of the given image.
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }

            // Convert to ARGB_8888, required by tess
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            return Utilities.saveBitmap(bitmap, Bitmap.CompressFormat.JPEG, Utilities.PICTURES_LOCATION.EXTERNAL);

        } catch (Exception e) {
            Log.e(TAG, "Couldn't correct orientation: " + e.toString());
            return null;
        }

    }

    private void cancelRecognizing() {
        setResult(Activity.RESULT_CANCELED, null);
        finish();
    }

    private void setImage(Uri inputPath) {
        InputStream imageStream = null;
        try {
            imageStream = getContentResolver().openInputStream(inputPath);
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            imageStream.close();
            //Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions);
            surfaceView.setImage(inputPath);
            //surfaceView.setBackground(new BitmapDrawable(orgImgUri));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void DrawFocusRect(float RectLeft, float RectTop, float RectRight, float RectBottom, int color) {

        Canvas canvas = holder.lockCanvas();
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        //border's properties
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(3);
        canvas.drawRect(RectLeft, RectTop, RectRight, RectBottom, paint);


        holder.unlockCanvasAndPost(canvas);
    }

    View.OnTouchListener onTouchSelectionListner = new View.OnTouchListener() {

        public int pointerId;
        public float top;
        public float left;
        public float bottom;
        public float right;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // get pointer index from the event object
            int pointerIndex = event.getActionIndex();

            // get pointer ID if is the first ine
            if(pointerId == -1)
                pointerId = event.getPointerId(pointerIndex);

            // if MULTI-Touch deliver to scalaing listener and invalidate
            // the current one
            if(event.getPointerCount() < 3) {
                surfaceView.onTouchEvent(event);
            }
            if(event.getPointerCount() == 1 && mInItemSelection) {

                // get masked (not specific to a pointer) action
                int maskedAction = event.getActionMasked();
                // if is the same pointer
                float touchY = event.getY();
                float touchX = event.getX();
                // translate to provide the first touch visibility
                touchX -= 30;
                touchY -= 30;
                if(touchX < 0)
                    touchX = 0;
                if(touchY < 0)
                    touchY = 0;
                if (pointerId == event.getPointerId(pointerIndex)) {
                    switch (maskedAction) {

                        case MotionEvent.ACTION_DOWN:
                            top = touchY;
                            left = touchX;
                        case MotionEvent.ACTION_POINTER_DOWN: {
                            // TODO use data
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: { // a pointer was moved
                            // TODO use data
                            bottom = touchY;
                            right = touchX;
                            updateRect((int) top, (int) left, (int) right, (int) bottom);
                            break;
                        }
                        case MotionEvent.ACTION_UP:
                            bottom = touchY;
                            right = touchX;
                        case MotionEvent.ACTION_POINTER_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            // TODO use data
                            break;
                        }

                    }
                }
            }

            return true;
        }


    };

    //TODO edit to higlighting listener
    View.OnTouchListener onTouchHiligthingListner = new View.OnTouchListener() {

        public int pointerId;
        public float top;
        public float left;
        public float bottom;
        public float right;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // get pointer index from the event object
            int pointerIndex = event.getActionIndex();

            // get pointer ID if is the first ine
            if(pointerId == -1)
                pointerId = event.getPointerId(pointerIndex);

            // if MULTI-Touch deliver to scalaing listener and invalidate
            // the current one
            if(event.getPointerCount() < 3) {
                surfaceView.onTouchEvent(event);
            }
            if(event.getPointerCount() == 1) {

                // get masked (not specific to a pointer) action
                int maskedAction = event.getActionMasked();
                // if is the same pointer
                float touchY = event.getY();
                float touchX = event.getX();

                if (pointerId == event.getPointerId(pointerIndex)) {
                    switch (maskedAction) {

                        case MotionEvent.ACTION_DOWN:
                            highlightPoint(touchX, touchY);
                        case MotionEvent.ACTION_POINTER_DOWN: {
                            // TODO use data
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: { // a pointer was moved
                            // TODO use data
                            highlightPoint(touchX, touchY);
                            break;
                        }
                        case MotionEvent.ACTION_UP:
                            bottom = touchY;
                            right = touchX;
                            highlightPoint(touchX, touchY);
                        case MotionEvent.ACTION_POINTER_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            // TODO use data
                            break;
                        }

                    }
                }
            }

            return true;
        }


    };


    private void clearRect(){
        surfaceView.setActiveRect(null);
        mRect = new RectF();
    }

    /** Update tht active rect */
    private void updateRect(int top, int left, int right, int bottom) {
        mRect.top = top;
        mRect.left = left;
        mRect.right = right;
        mRect.bottom = bottom;
        surfaceView.setActiveRect(mRect);
    }


    /**
     * Launch the processor after showing the progress dialog, and wait until it has finish task
     */
    private class TextDetectionAsyncTask extends AsyncTask<TextDetectWorkProcessor,Void,Boolean>{

        ProgressDialog dialog;
        TextDetectWorkProcessor processor;


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = ProgressDialog.show(HighliterActivity.this, null, getResources().getString(R.string.text_detection_processing_loading), true, false);
            enterHighlighting();
        }

        @Override
        protected Boolean doInBackground(TextDetectWorkProcessor... ocrWorkProcessors) {
            processor = ocrWorkProcessors[0];
            processor.start();

            // wait until every request has been replied
            try {
                while (processor.getStatus() != TextDetectWorkProcessor.Status.IDLE)
                    synchronized (processor) {
                        processor.wait();
                    }
                Log.d(TAG,"Text Detection activities closed");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }


        @Override
        protected void onPostExecute(Boolean success){
            dialog.dismiss();
            isTextDetectionFinish = true;
        }
    }

    private class RectItem {

        private Rect mRect;
        private boolean mSelected;

        public RectItem(Rect mRect) {
            this.mRect = mRect;
            this.mSelected = false;
        }

        public Rect getRect() {
            return mRect;
        }

        public void setSelection(boolean selection) {
            this.mSelected = selection;
        }

        public boolean isSelected() {
            return mSelected;
        }
    }

    public class RectItemComparator implements Comparator<RectItem> {

        @Override
        public int compare(RectItem lhs, RectItem rhs) {
            return Integer.compare(lhs.getRect().centerY(), rhs.getRect().centerY());
        }
    }

}
