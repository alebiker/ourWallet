package it.alborile.mobile.textdetect.service;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

import it.alborile.mobile.ocr.client.Ocr;
import it.alborile.mobile.textdetect.client.TextDetector;

import com.googlecode.leptonica.android.Constants;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.Pixa;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.leptonica.android.Convert;
import com.googlecode.leptonica.android.Scale;


import com.albori.android.utilities.Utilities;

import static com.googlecode.leptonica.android.WriteFile.writeBitmap;


/**
 * Created by alex on 23/02/16.
 */
public class TextDetectorTaskProcessor {
    private static final String TAG = "TextDetTaskProcessor";

    /** The wrapper for the native Hydrogen instance. */
    private final HydrogenTextDetector mTextDetector;

    /** List of queued tasks with the current task at the front. */
    private final LinkedList<TextDetectorTask> mTaskQueue;

    private final Handler mHandler;

    /** Object that receives recognition results. */
    private TextDetectorTaskListener mListener;

    private AsyncOcrTask mCurrentTask;

    /**
     * Creates a new OCR task processor using the given data path.
     *
     *
     */
    public TextDetectorTaskProcessor() {
        mHandler = new Handler();
        mTextDetector = new HydrogenTextDetector();
        mTaskQueue = new LinkedList<TextDetectorTask>();
    }

    /**
     * Sets the object that receives recognition results.
     *
     * @param listener The object that receives recognition results.
     */
    public void setListener(TextDetectorTaskListener listener) {
        mListener = listener;
    }

    /**
     * Cancels a job, stopping it if it is currently running or removing it from
     * the job queue if it is not.
     *
     * @param pid The ID of the requesting process.
     * @param token The task ID of the OCR job to cancel.
     */
    public boolean cancel(int pid, long token) {
        synchronized (mTaskQueue) {
            if (mCurrentTask != null && mCurrentTask.mPid == pid && mCurrentTask.mToken == token) {
                mCurrentTask.requestStop();
                return true;
            }

            final ListIterator<TextDetectorTask> it = mTaskQueue.listIterator();

            while (it.hasNext()) {
                final TextDetectorTask task = it.next();

                if (task.pid == pid && task.token == token) {
                    it.remove();
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Cancels all jobs for a given process ID.
     *
     * @param pid The ID of the requesting process.
     */
    public boolean cancelAll(int pid) {
        boolean removed = false;

        synchronized (mTaskQueue) {
            if (mCurrentTask != null && mCurrentTask.mPid == pid) {
                mCurrentTask.requestStop();
            }

            final ListIterator<TextDetectorTask> it = mTaskQueue.listIterator();

            while (it.hasNext()) {
                final TextDetectorTask task = it.next();

                if (task.pid == pid) {
                    it.remove();

                    removed = true;
                }
            }
        }

        return removed;
    }

    /**
     * Cancels all jobs.
     */
    public void abort() {
        synchronized (mTaskQueue) {
            mTaskQueue.clear();
        }
    }

    /**
     * Cancels pending jobs and releases resources. No jobs should be queued
     * after calling this method.
     */
    public void shutdown() {
        abort();
        //TODO freeresources
    }

    /**
     * Enqueues a new byte array for processing.
     *
     * @param pid
     * @param data
     * @param params
     * @return The task ID of the enqueued job.
     */
    public long enqueueData(int pid, byte[] data, TextDetector.Parameters params) {
        return enqueueTask(new TextDetectorTask(pid, data, params));
    }

    /**
     * Enqueues a new file for processing.
     *
     * @param pid
     * @param file
     * @param params
     * @return The task ID of the enqueued job.
     */
    public long enqueueFile(int pid, File file, TextDetector.Parameters params) {
        return enqueueTask(new TextDetectorTask(pid, file, params));
    }

    private long enqueueTask(TextDetectorTask task) {
        boolean wasEmpty = false;

        synchronized (mTaskQueue) {
            wasEmpty = mTaskQueue.isEmpty();
            mTaskQueue.addLast(task);
        }

        if (wasEmpty) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    nextTask();
                }
            });
        }

        return task.token;
    }

    private void nextTask() {
        synchronized (mTaskQueue) {
            mCurrentTask = new AsyncOcrTask();
            Utilities.executeAsyncTask(mCurrentTask, mTaskQueue.getFirst());
        }
    }

    private void finishedTask() {
        boolean isEmpty = false;

        synchronized (mTaskQueue) {
            mCurrentTask = null;
            mTaskQueue.removeFirst();

            isEmpty = mTaskQueue.isEmpty();
        }

        if (!isEmpty) {
            nextTask();
        }
    }

    private class AsyncOcrTask extends AsyncTask<TextDetectorTask, Integer, ArrayList<Rect>> {
        private int mPid;
        private int mToken;
        private boolean mStopRequested = false;

        /** We'd rather not process anything larger than 720p. */
        private static final int MAX_IMAGE_AREA = 1280 * 720;

        @Override
        protected ArrayList<Rect> doInBackground(TextDetectorTask... tasks) {
            if (tasks.length == 0 || tasks[0] == null || isStopRequested()) {
                return null;
            }

            ArrayList<Rect>results = null;

            try {
                final TextDetectorTask task = tasks[0];
                final File file = task.file;
                final byte[] data = task.data;
                final Pix pix = file == null ? ReadFile.readMem(data) : ReadFile.readFile(file);
                results = new ArrayList<>();

                mPid = task.pid;
                mToken = task.token;

                // Using arrays because Java can't pass primitives by reference or
                // return tuples, but we need to get the scale and angle values.
                final float[] scale = new float[]{
                        1.0f
                };
                final float[] angle = new float[]{
                        0.0f
                };

                final Pixa pixa = processPix(task, pix, scale, angle);

                results.addAll(pixa.getBoxRects());
            }catch(Exception e){
                e.printStackTrace();
            }

            return results;
        }

        @Override
        protected void onPostExecute(ArrayList<Rect> results) {
            if (mListener == null) {
                return;
            }

            mListener.onCompleted(mPid, mToken, results);

            finishedTask();
        }

        public void requestStop() {
            mStopRequested = true;
        }

        public boolean isStopRequested() {
            return mStopRequested;
        }

        private Pixa processPix(TextDetectorTask task, Pix curr, float[] scale, float[] angle) {
            final TextDetector.Parameters params = task.params;
            final File outputDir = task.outputDir;
            final boolean debug = params.getFlag(Ocr.Parameters.FLAG_DEBUG_MODE);
            final boolean text_detection = params.getFlag(Ocr.Parameters.FLAG_DETECT_TEXT);
            Pixa pixa;

            long prev;
            long time = System.currentTimeMillis();

            Pix temp = null;

            int w = curr.getWidth();
            int h = curr.getHeight();

            Log.i(TAG, "processPix: started on pix (h:" + h + " w:" + w+ ")");

            if (outputDir != null && debug) {
                Bitmap inputBpm = writeBitmap(curr);
                WriteFile.writeImpliedFormat(curr, new File(outputDir, time + "_0_input.png"));
            }

            if (isStopRequested())
                return null;

            // Convert to 8bpp first if necessary
            if (curr.getDepth() != 8) {
                temp = Convert.convertTo8(curr);
                curr.recycle();
                curr = temp;

                if (outputDir != null && debug) {
                    WriteFile.writeImpliedFormat(curr, new File(outputDir, time + "_1_8bpp.png"));
                }
            }

            //TODO adjust shrink

            // Shrink if necessary
            int[] dimensions = curr.getDimensions();
            int area = dimensions[0] * dimensions[1];
            if (area > MAX_IMAGE_AREA) {
                scale[0] = MAX_IMAGE_AREA / (float) area;
                Log.i(TAG, "Scaling input image to a factor of " + scale[0]);
                temp = Scale.scale(curr, scale[0]);
                curr.recycle();
                curr = temp;

                if (outputDir != null && debug) {
                    WriteFile.writeImpliedFormat(curr, new File(outputDir, time + "_2_scaled.png"));
                }
            }

            pixa = detectText(curr, angle, params, debug);

            if (outputDir != null && debug) {
                pixa.writeToFileRandomCmap(new File(outputDir, time + "_5_detected.png"));
            }

            ArrayList<Bitmap> bitmaps = new ArrayList<>();
            //////////// Debug
            if (debug) {
                for (int i = 0; i < pixa.size(); i++) {
                    Pix pix = pixa.getPix(i);
                    bitmaps.add(writeBitmap(pix));
                }
            }

            return pixa;
        }



        /**
         * Scales a rectangle in-place using the specified scaling factor.
         *
         * @param rect The rectangle to scale in-place.
         * @param scale The factor by which to scale.
         */
        private void scaleRect(Rect rect, float scale) {
            rect.top /= scale;
            rect.bottom /= scale;
            rect.left /= scale;
            rect.right /= scale;
        }

    }

    public static Pixa detectText(Pix curr, float[] angle, TextDetector.Parameters params, boolean debug){
        Pixa unsorted, textRegions;

        // Transfer parameters to text detector
        HydrogenTextDetector textDetector = new HydrogenTextDetector();
        HydrogenTextDetector.Parameters hydrogenParams = textDetector.getParameters();
        hydrogenParams.debug = debug;
        hydrogenParams.skew_enabled = params.getFlag(Ocr.Parameters.FLAG_ALIGN_TEXT);
        hydrogenParams.setNumDetectionMode(params.getFlag(Ocr.Parameters.FLAG_NUMBER_DETECTION));
        hydrogenParams.setSmallMode(params.getFlag(Ocr.Parameters.FLAG_SMALL_DETECTION));

        textDetector.setParameters(hydrogenParams);

        // Run text detection (thresholding, alignment, etc.)
        textDetector.setSourceImage(curr);
        textDetector.detectText();

        // Get alignment angle
        angle[0] = textDetector.getSkewAngle();

        // Sort by increasing Y-value so that we read results in order
        unsorted = textDetector.getTextAreas();
        Log.d(TAG, "HydroTextDet - " + unsorted.size() + " text areas found");

        textRegions = unsorted.sort(Constants.L_SORT_BY_Y, Constants.L_SORT_INCREASING);
        unsorted.recycle();
        try {
            textDetector.finalize();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return textRegions;
    }

    private class TextDetectorTask {
        /* package */final int pid;
        /* package */final int token;
        /* package */final File file;
        /* package */final byte[] data;
        /* package */final TextDetector.Parameters params;
        /* package */final File outputDir;

        public TextDetectorTask(int pid, File file, TextDetector.Parameters params) {
            this(pid, file, null, params);
        }

        public TextDetectorTask(int pid, byte[] data, TextDetector.Parameters params) {
            this(pid, null, data, params);
        }

        private TextDetectorTask(int pid, File file, byte[] data, TextDetector.Parameters params) {
            this.pid = pid;
            this.token = hashCode();
            this.file = file;
            this.data = data;
            this.params = params;
            this.outputDir =
                    Utilities.createPublicDirectory(String.valueOf(System.currentTimeMillis()) + "_td");
        }
    }

    public interface TextDetectorTaskListener {
        void onCompleted(int pid, long token, ArrayList<Rect> results);
    }
}
