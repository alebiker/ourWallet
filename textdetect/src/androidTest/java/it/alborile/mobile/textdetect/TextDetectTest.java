package it.alborile.mobile.textdetect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
public class TextDetectTest {
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
            TextDetectWorkProcessor workProcessor = new TextDetectWorkProcessor(mContext);

            final int[] textdetected = {0};
            final Rect[] rect = new Rect[1];
            Rect inputRect = new Rect(400, 168, 445, 189);
            Uri imgUri = Uri.parse("content://media/external/images/media/35519");
            TextDetector.Parameters params = new TextDetector.Parameters();
            params.setFlag(TextDetector.Parameters.FLAG_SMALL_DETECTION, true);
            params.setFlag(TextDetector.Parameters.FLAG_NUMBER_DETECTION, true);
            params.setFlag(TextDetector.Parameters.FLAG_ALIGN_TEXT, true);

            TextDetectWork nameDetectWork = new TextDetectWork(imgUri, params, inputRect) {
                @Override
                public void processTextDetectionWorkRequest(TextDetectWork processor, Bitmap mBitmap, List<Rect> rects) {

                }

                @Override
                public void onCompletedResults(List<Rect> results) {
                    rect[0] = results.get(0);
                }
            };

            workProcessor.enqueue(nameDetectWork);

            workProcessor.start();

            if (workProcessor.getStatus() != TextDetectWorkProcessor.Status.IDLE){
                synchronized (workProcessor){
                    workProcessor.wait();
                }
            }

            synchronized (this){
                wait(200);
            }
            assertTrue(!workProcessor.isProducerAlive());

            assertTrue(workProcessor.getWorksCompleted() == 1);
            assertTrue(workProcessor.getJobsAborted() == 0);

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void OneInvalidWorkWithOneRegion() {
        try {

            final int[] textdetected = {0};
            final Rect[] rect = new Rect[1];
            Rect inputRect = new Rect();
            Uri imgUri = Uri.parse("");
            TextDetector.Parameters params = new TextDetector.Parameters();

            TextDetectWorkProcessor workProcessor = new TextDetectWorkProcessor(mContext);

            TextDetectWork nameDetectWork = new TextDetectWork(imgUri, params, inputRect) {
                @Override
                public void processTextDetectionWorkRequest(TextDetectWork processor, Bitmap mBitmap, List<Rect> rects) {

                }

                @Override
                public void onCompletedResults(List<Rect> results) {
                    rect[0] = results.get(0);
                }
            };
            assertTrue(workProcessor.getStatus().equals(TextDetectWorkProcessor.Status.IDLE));
            workProcessor.enqueue(nameDetectWork);
            assertTrue(workProcessor.getStatus().equals(TextDetectWorkProcessor.Status.READY));
            workProcessor.start();

            if (workProcessor.getStatus() != TextDetectWorkProcessor.Status.IDLE){
                synchronized (workProcessor){
                    workProcessor.wait();
                }
            }
            assertTrue(workProcessor.getWorksCompleted() == 0);
            assertTrue(workProcessor.getJobsAborted() == 1);

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void StopProcessingTest() {
        try {
            final TextDetectWorkProcessor workProcessor = new TextDetectWorkProcessor(mContext);

            final int[] textdetected = {0};
            final List<Rect> rects = new ArrayList<>();
            //Rect inputRect = new Rect(400, 168, 445, 189);
            Uri imgUri = Uri.parse("content://media/external/images/media/35519");
            TextDetector.Parameters params = new TextDetector.Parameters();
            params.setFlag(TextDetector.Parameters.FLAG_SMALL_DETECTION, true);
            params.setFlag(TextDetector.Parameters.FLAG_NUMBER_DETECTION, true);
            params.setFlag(TextDetector.Parameters.FLAG_ALIGN_TEXT, true);

            TextDetectWork work = new TextDetectWork(imgUri, params) {
                @Override
                public void processTextDetectionWorkRequest(TextDetectWork processor, Bitmap mBitmap, List<Rect> rects) {

                }

                @Override
                public void onCompletedResults(List<Rect> results) {
                    rects.addAll(results);
                }
            };

            workProcessor.enqueue(work);

            workProcessor.start();

            while (workProcessor.getWorksStarted() == 0){
                synchronized (workProcessor){
                    workProcessor.wait(10);
                }
            }

            workProcessor.stop();

            while (!workProcessor.getStatus().equals(TextDetectWorkProcessor.Status.STOPPED)){
                synchronized (workProcessor){
                    workProcessor.wait();
                }
            }

            synchronized (this){
                wait(200);
            }

            assertTrue(!workProcessor.isProducerAlive());


            assertTrue(workProcessor.getWorksCompleted() == 0);
            assertTrue(workProcessor.getJobsAborted() > 0);
            assertTrue(rects.isEmpty());

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
}
