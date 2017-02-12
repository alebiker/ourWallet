package it.alborile.mobile.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import it.alborile.mobile.ocr.client.OCRWork;
import it.alborile.mobile.ocr.client.OCRWorkProcessor;
import it.alborile.mobile.ocr.client.Ocr;
import it.alborile.mobile.ocr.client.OcrResult;
import it.alborile.mobile.textdetect.client.TextDetectWork;
import it.alborile.mobile.textdetect.client.TextDetectWorkProcessor;
import it.alborile.mobile.textdetect.client.TextDetector;

import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by alex on 08/03/16.
 */
public class OcrTest {

    private Context mContext;
    private boolean exceptionRaise;

    @Before
    public void initTargetContext() {
        mContext = InstrumentationRegistry.getTargetContext();
        assertThat(mContext, notNullValue());
    }

    @Test
    public void OneValidWorkWithOneRegion() {
        try {
            OCRWorkProcessor workProcessor = new OCRWorkProcessor(mContext);

            final int[] textdetected = {0};
            final Rect[] rect = new Rect[1];
            List<Rect> inputRects = new ArrayList<>();
            inputRects.add(new Rect(400, 168, 445, 189));
            Uri imgUri = Uri.parse("content://media/external/images/media/35519");
            Ocr.Parameters params = new Ocr.Parameters();
            params.setFlag(Ocr.Parameters.FLAG_SMALL_DETECTION, true);
            params.setFlag(Ocr.Parameters.FLAG_NUMBER_DETECTION, true);
            params.setFlag(Ocr.Parameters.FLAG_ALIGN_TEXT, true);

            OCRWork nameDetectWork = new OCRWork(imgUri, params, inputRects) {
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

            workProcessor.enqueue(nameDetectWork);

            workProcessor.start();

            if (workProcessor.getStatus() != OCRWorkProcessor.Status.FINISH){
                synchronized (workProcessor){
                    workProcessor.wait();
                }
            }

            assertTrue(workProcessor.getCompleted() == 1);
            assertTrue(workProcessor.getAborted() == 0);

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

}
