package it.alborile.mobile.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;

import com.albori.android.utilities.Utilities;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import it.alborile.mobile.ocr.client.OCRWork;
import it.alborile.mobile.ocr.client.OCRWorkProcessor;
import it.alborile.mobile.ocr.client.Ocr;
import it.alborile.mobile.ocr.client.OcrResult;
import it.alborile.mobile.textdetect.client.TextDetectWork;
import it.alborile.mobile.textdetect.client.TextDetector;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by alex on 09/03/16.
 */
@RunWith(Parameterized.class)
public class OCRWorkSetupTest {
    private Context mContext;

    private List<Rect> mRects;
    private Bitmap mBitmap;
    private Uri mUri;
//    private Ocr.Parameters mParams;
    private boolean mExpectedVldResult;
    private static int mRun;

    private OCRWork mWork;

    @Before
    public void initTargetContext() {
        mContext = InstrumentationRegistry.getTargetContext();
        assertThat(mContext, notNullValue());
        Utilities.getInstance(mContext);
    }


    /**
     * Constructor that takes in the values specified in
     * {@link OCRWorkSetupTest#data()}. The values need to be saved to fields in order
     * to reuse them in your tests.
     */
    public OCRWorkSetupTest(Object bitmap, Object uri, Object rects,
                            boolean expectedResult) {
        mBitmap = (bitmap instanceof String) ? null : (Bitmap)bitmap;
        mUri = (uri instanceof String) ? null : (Uri)uri;
        mRects = (rects instanceof String) ? null : (List<Rect>)rects;
        mExpectedVldResult = expectedResult;

        Ocr.Parameters params = new Ocr.Parameters();

        mWork = new OCRWork(mUri, mBitmap, params, mRects) {
            @Override
            public void processOcrWorkRequest(OCRWorkProcessor processor, Bitmap mBitmap, List<Rect> rects) {

            }

            @Override
            public void onProcessResult(OcrResult results, Rect rect) {

            }

            @Override
            public void onCompletedResults(List<OcrResult> results) {

            }
        };
    }

    @Parameterized.Parameters
    public static Collection params() {
        try {
            Utilities.getInstance(InstrumentationRegistry.getTargetContext());
            Uri validUri = Uri.parse("content://media/external/images/media/35519");
            Uri wrongUri = Uri.parse("content://media/external/images/media/355199");
            Uri nValidUri = Uri.parse("");
            Bitmap validBitmap = Utilities.loadImage(validUri);
            Bitmap wrongBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            List<Rect> validRects = new ArrayList<>();
            List<Rect> invalidRects = new ArrayList<>();
            Rect validRect = new Rect(10, 10, 20, 20);
            Rect nValidRect = new Rect(10, 10, 10, 10);
            Rect outBoundRect = new Rect(10, 10, 500, 150);
            validRects.add(validRect);
            validRects.add(validRect);
            validRects.add(validRect);
            invalidRects.add(validRect);
            invalidRects.add(nValidRect);
            //      Bitmap            Uri        Rect        setupValidation
            return Arrays.asList(new Object[][]{
                    {wrongBitmap, validUri, validRects, false},
                    {wrongBitmap, validUri, invalidRects, false},
                    {wrongBitmap, validUri, null, false},
                    {wrongBitmap, nValidUri, validRects, false},
                    {wrongBitmap, nValidUri, invalidRects, false},
                    {wrongBitmap, nValidUri, null, false},
                    {wrongBitmap, null, validRects, true},
                    {wrongBitmap, null, invalidRects, false},
                    {wrongBitmap, null, null, true},
                    {validBitmap, validUri, validRects, true},
                    {validBitmap, validUri, invalidRects, false},
                    {validBitmap, validUri, null, true},
                    {validBitmap, nValidUri, validRects, false},
                    {validBitmap, nValidUri, invalidRects, false},
                    {validBitmap, nValidUri, null, false},
                    {validBitmap, null, validRects, true},
                    {validBitmap, null, invalidRects, false},
                    {validBitmap, null, null, true},
                    {null, validUri, validRects, true},
                    {null, validUri, invalidRects, false},
                    {null, validUri, null, true},
                    {null, nValidUri, validRects, false},
                    {null, nValidUri, invalidRects, false},
                    {null, nValidUri, null, false}
            });
        }catch (Exception e){
            return null;
        }
    }

    @Test
    public void SetupValidationTest() {
        if (mRun == 25){
            mRun = mRun;
        }
        try {
            Method method = OCRWork.class.getDeclaredMethod("setupValidation");
            method.setAccessible(true);
            boolean valid = (boolean) method.invoke(mWork);
            assertThat(valid, is(equalTo(mExpectedVldResult)));
            mRun++;
        }catch (Exception e){
            e.printStackTrace();
            fail();
        }
    }

    /*
    @Test
    public void RectValidationTest() {

        try {
            Method method = TextDetectWork.class.getDeclaredMethod("validateRect", Rect.class, Bitmap.class);
            method.setAccessible(true);
            boolean valid = (boolean) method.invoke(mWork, mRects, mBitmap);
            assertThat(valid, is(equalTo(mExpectedVldResult)));
        }catch (Exception e){
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void ImagesMatcherTest() {

        try {
            Method method = TextDetectWork.class.getDeclaredMethod("checkValidImages", Bitmap.class, Uri.class);
            method.setAccessible(true);
            boolean valid = (boolean) method.invoke(mWork, mBitmap, mUri);
            assertThat(valid, is(equalTo(mExpectedVldResult)));
        }catch (Exception e){
            e.printStackTrace();
            fail();
        }
    }
    */

}


