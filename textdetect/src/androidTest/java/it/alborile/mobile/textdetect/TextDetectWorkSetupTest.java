package it.alborile.mobile.textdetect;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.test.suitebuilder.annotation.SmallTest;

import com.albori.android.utilities.Utilities;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import it.alborile.mobile.textdetect.client.TextDetectWork;
import it.alborile.mobile.textdetect.client.TextDetector;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by alex on 09/03/16.
 */
@RunWith(Parameterized.class)
public class TextDetectWorkSetupTest {
    private Context mContext;

    private Rect mRect;
    private Bitmap mBitmap;
    private Uri mUri;
//    private TextDetector.Parameters mParams;
    private boolean mExpectedVldResult;
    private static int mRun;

    private TextDetectWork mWork;

    @Before
    public void initTargetContext() {
        mContext = InstrumentationRegistry.getTargetContext();
        assertThat(mContext, notNullValue());
        Utilities.getInstance(mContext);
    }


    /**
     * Constructor that takes in the values specified in
     * {@link TextDetectWorkSetupTest#data()}. The values need to be saved to fields in order
     * to reuse them in your tests.
     */
    public TextDetectWorkSetupTest(Object bitmap, Object uri, Object rect,
                                   boolean expectedResult) {
        mBitmap = (bitmap instanceof String) ? null : (Bitmap)bitmap;
        mUri = (uri instanceof String) ? null : (Uri)uri;
        mRect = (rect instanceof String) ? null : (Rect)rect;
        mExpectedVldResult = expectedResult;

        TextDetector.Parameters params = new TextDetector.Parameters();

        mWork = new TextDetectWork(mUri, mBitmap, params, mRect) {
            @Override
            public void processTextDetectionWorkRequest(TextDetectWork processor, Bitmap mBitmap, List<Rect> rects) {

            }

            @Override
            public void onCompletedResults(List<Rect> results) {

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
            Rect validRect = new Rect(10, 10, 20, 20);
            Rect nValidRect = new Rect(10, 10, 10, 10);
            Rect outBoundRect = new Rect(10, 10, 500, 150);
            //      Bitmap            Uri        Rect        setupValidation
            return Arrays.asList(new Object[][]{
                    {wrongBitmap, validUri, validRect, false},
                    {wrongBitmap, validUri, nValidRect, false},
                    {wrongBitmap, validUri, outBoundRect, false},
                    {wrongBitmap, validUri, null, false},
                    {wrongBitmap, nValidUri, validRect, false},
                    {wrongBitmap, nValidUri, nValidRect, false},
                    {wrongBitmap, nValidUri, outBoundRect, false},
                    {wrongBitmap, nValidUri, null, false},
                    {wrongBitmap, null, validRect, true},
                    {wrongBitmap, null, nValidRect, false},
                    {wrongBitmap, null, outBoundRect, false},
                    {wrongBitmap, null, null, true},
                    {validBitmap, validUri, validRect, true},
                    {validBitmap, validUri, nValidRect, false},
                    {validBitmap, validUri, outBoundRect, false},
                    {validBitmap, validUri, null, true},
                    {validBitmap, nValidUri, validRect, false},
                    {validBitmap, nValidUri, nValidRect, false},
                    {validBitmap, nValidUri, outBoundRect, false},
                    {validBitmap, nValidUri, null, false},
                    {validBitmap, null, validRect, true},
                    {validBitmap, null, nValidRect, false},
                    {validBitmap, null, outBoundRect, false},
                    {validBitmap, null, null, true},
                    {null, validUri, validRect, true},
                    {null, validUri, nValidRect, false},
                    {null, validUri, outBoundRect, false},
                    {null, validUri, null, true},
                    {null, nValidUri, validRect, false},
                    {null, nValidUri, nValidRect, false},
                    {null, nValidUri, outBoundRect, false},
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
            Method method = TextDetectWork.class.getDeclaredMethod("setupValidation");
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
            boolean valid = (boolean) method.invoke(mWork, mRect, mBitmap);
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


