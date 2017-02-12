package it.abapp.mobile.shoppingtogether.ocr;

/**
 * Created by alex on 07/03/16.
 */

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

import it.abapp.mobile.shoppingtogether.R;
import it.alborile.mobile.ocr.client.Ocr;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static it.abapp.mobile.shoppingtogether.Matchers.defaultNameShopList;
import static it.abapp.mobile.shoppingtogether.Matchers.hasAllObjects;
import static it.abapp.mobile.shoppingtogether.Matchers.withListSize;

/**
 * JUnit4 Ui Tests for {@link OCRActivity} using the {@link AndroidJUnitRunner}.
 * This class uses the JUnit4 syntax for tests.
 * <p>
 * With the new AndroidJUnit runner you can run both JUnit3 and JUnit4 tests in a single test
 * suite. The {@link AndroidRunnerBuilder} which extends JUnit's
 * {@link AllDefaultPossibilitiesBuilder} will create a single {@link
 * TestSuite} from all tests and run them.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class OCRActivityTest {

    Activity mActivity;
    /**
     * A JUnit {@link Rule @Rule} to launch your activity under test. This is a replacement
     * for {@link ActivityInstrumentationTestCase2}.
     * <p>
     * Rules are interceptors which are executed for each test method and will run before
     * any of your setup code in the {@link Before @Before} method.
     * <p>
     * {@link ActivityTestRule} will create and launch of the activity for you and also expose
     * the activity under test. To get a reference to the activity you can use
     * the {@link ActivityTestRule#getActivity()} method.
     */
    @Rule
    public ActivityTestRule<OCRActivity> mActivityRule = new ActivityTestRule<>(
            OCRActivity.class, true, false);

    @Test
    public void ValidNameValidPriceRegionTest() {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), OCRActivity.class);
        HashMap<Rect,Rect> rects = new HashMap<>();
        rects.put(new Rect(22, 166, 118, 181), new Rect(402, 170, 440, 186));
        Ocr.Parameters params = new Ocr.Parameters();
        params.setLanguage("eng");
        params.setFlag(Ocr.Parameters.FLAG_DETECT_TEXT, false);
        params.setFlag(Ocr.Parameters.FLAG_SPELLCHECK, false);

        intent.putExtra(Intents.Recognize.EXTRA_INPUT, Uri.parse("content://media/external/images/media/35519"));
        intent.putExtra(Intents.Recognize.EXTRA_PARAMETERS, params);
        intent.putExtra(Intents.Recognize.EXTRA_RECTS, rects);
        mActivity = mActivityRule.launchActivity(intent);

        waitUntilOCRActivityFinish();
        checkListEntriesCount(1);
    }

    @Test
    public void InvalidNameValidPriceRegionTest() {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), OCRActivity.class);
        HashMap<Rect,Rect> rects = new HashMap<>();
        rects.put(new Rect(10, 10, 15, 15), new Rect(402, 170, 440, 186));
        Ocr.Parameters params = new Ocr.Parameters();
        params.setLanguage("eng");
        params.setFlag(Ocr.Parameters.FLAG_DETECT_TEXT, false);
        params.setFlag(Ocr.Parameters.FLAG_SPELLCHECK, false);

        intent.putExtra(Intents.Recognize.EXTRA_INPUT, Uri.parse("content://media/external/images/media/35519"));
        intent.putExtra(Intents.Recognize.EXTRA_PARAMETERS, params);
        intent.putExtra(Intents.Recognize.EXTRA_RECTS, rects);
        mActivity = mActivityRule.launchActivity(intent);

        waitUntilOCRActivityFinish();
        onView(withId(R.id.ocr_activity_list)).check(matches(hasAllObjects(defaultNameShopList())));
        checkListEntriesCount(1);
    }

    @Test
    public void InvalidImageTest() {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), OCRActivity.class);
        HashMap<Rect,Rect> rects = new HashMap<>();
        rects.put(new Rect(10, 10, 15, 15), new Rect(402, 170, 440, 186));
        Ocr.Parameters params = new Ocr.Parameters();
        params.setLanguage("eng");
        params.setFlag(Ocr.Parameters.FLAG_DETECT_TEXT, false);
        params.setFlag(Ocr.Parameters.FLAG_SPELLCHECK, false);

        intent.putExtra(Intents.Recognize.EXTRA_INPUT, Uri.parse(""));
        intent.putExtra(Intents.Recognize.EXTRA_PARAMETERS, params);
        intent.putExtra(Intents.Recognize.EXTRA_RECTS, rects);
        mActivity = mActivityRule.launchActivity(intent);

        try {
            synchronized (this) {
                wait(1500);
            }
            onView(withText(mActivity.getString(R.string.ocr_activity_bad_input_dialog_msg))).check(matches(isDisplayed()));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void InvalidRectTest() {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), OCRActivity.class);
        Ocr.Parameters params = new Ocr.Parameters();
        params.setLanguage("eng");
        params.setFlag(Ocr.Parameters.FLAG_DETECT_TEXT, false);
        params.setFlag(Ocr.Parameters.FLAG_SPELLCHECK, false);

        intent.putExtra(Intents.Recognize.EXTRA_INPUT, Uri.parse("content://media/external/images/media/35519"));
        intent.putExtra(Intents.Recognize.EXTRA_PARAMETERS, params);

        mActivity = mActivityRule.launchActivity(intent);

        try {
            synchronized (this) {
                wait(1500);
            }
            onView(withText(mActivity.getString(R.string.ocr_activity_bad_input_dialog_msg))).check(matches(isDisplayed()));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void DefaultParamsTest() {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), OCRActivity.class);
        HashMap<Rect,Rect> rects = new HashMap<>();
        rects.put(new Rect(10, 10, 15, 15), new Rect(402, 170, 440, 186));

        intent.putExtra(Intents.Recognize.EXTRA_INPUT, Uri.parse("content://media/external/images/media/35519"));
        //intent.putExtra(Intents.Recognize.EXTRA_PARAMETERS, null);
        intent.putExtra(Intents.Recognize.EXTRA_RECTS, rects);
        mActivity = mActivityRule.launchActivity(intent);

        waitUntilOCRActivityFinish();
        onView(withId(R.id.ocr_activity_list)).check(matches(hasAllObjects(defaultNameShopList())));
        checkListEntriesCount(1);
    }

    @Test
    public void StopOCRProcessing(){
        //TODO
    }

    @Test
    public void PauseAndResumeOCRProcessing(){
        //TODO
    }

    public void waitUntilOCRActivityFinish(){
        synchronized (mActivity) {
            while (!((OCRActivity) mActivity).isFiniskTask()) {
                try {
                    mActivity.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    public void checkListEntriesCount(int size) {
        onView(withId(R.id.ocr_activity_list)).check(matches(withListSize(size)));
    }


}
