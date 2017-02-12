package it.alborile.mobile.ocr.client;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.albori.android.utilities.Utilities;


/**
 * Created by alex on 05/02/16.
 */
//TODO use only one Ocr client object
    //TODO check instantiation of ocr service
public class OCRWorkProcessor implements Ocr.ResultCallback, Ocr.CompletionCallback {
    private final Context mContext;
    protected Queue<OCRWork> mWorkQueue;
    protected List<OCRWork> worksInProcess;
    private OcrRequestProducerAsyncTask mRqstProducer;
    protected int worksEnqueued = 0, worksStarted = 0, jobsFinished = 0, worksCanceled = 0, jobsAborted = 0;
    private static final String TAG = "OCRWorkProcessor";
    private Ocr mOcr;
    private HashMap<Long,CompletionCallback> mCompletitionCb;
    private HashMap<Long,ResultCallback> mResultCb;
    private HashMap<Long,Ocr.Job> mJobMap;
    private List<Ocr.Job> mJobs;
    private Status mStatus;


    /** Status of the OCRWork processor
     * {@link BUSY}: the processor was started to send request to OCR service and not all results
     * are received
     *
     * */
    public enum Status{
        BUSY, STOP, PENDING, FINISH
    }

    public OCRWorkProcessor(Context context) {
        this(context, null);
        Utilities.getInstance(context);
    }

    public OCRWorkProcessor(Context context, List<OCRWork> workList) {
        this.mContext = context;
        if (workList != null)
            this.mWorkQueue = new ConcurrentLinkedQueue<>(workList);
        else
            this.mWorkQueue = new ConcurrentLinkedQueue<>();
        this.mJobs = new ArrayList<>();
        this.worksInProcess = new ArrayList<>();
        this.mJobMap = new HashMap<>();
        this.mCompletitionCb = new HashMap<>();
        this.mResultCb = new HashMap<>();
        this.mStatus = Status.PENDING;
    }

    public int getCompleted() {
        return jobsFinished;
    }

    public int getAborted() {
        return jobsAborted;
    }

    /**
     * Return the status of the request elaboration.
     * @return The status of enum type Status of the processor request elaboration
     */
    public Status getStatus(){
        return mStatus;
    }

    public boolean isProducerAlive(){
        if (mRqstProducer != null)
            return mRqstProducer.getStatus().equals(AsyncTask.Status.RUNNING);
        return false;
    }

    public void enqueue(OCRWork work){
        work.attachProcessor(this);
        synchronized (mWorkQueue) {
            mWorkQueue.add(work);
            mWorkQueue.notify();
            worksEnqueued++;
        }
    }

    synchronized public void start(){
        mRqstProducer = new OcrRequestProducerAsyncTask();
        mStatus = Status.BUSY;

        // set the callback that is executed if the Ocr service is connected successfully
        Ocr.InitCallback onInit = new Ocr.InitCallback() {
            @Override
            public void onInitialized(int status) {
                if(status == Ocr.STATUS_SUCCESS){
                    Utilities.executeAsyncTask(mRqstProducer);
                }
            }
        };

        mOcr = new Ocr(mContext, onInit);

        // set the callback for the results
        mOcr.setResultCallback(this);
        mOcr.setCompletionCallback(this);
    }

    /**
     * Stopping all pending request not started yet and release it
     */
    synchronized public void stop() {
        stopLooperTaskIfNeeded();

        OCRWork work;
        synchronized (mWorkQueue) {
            while ((work = mWorkQueue.poll()) != null) {
                work.release();
                worksCanceled++;
                mWorkQueue.notify();
            }
        }

        // Abort any started request
        for (Ocr.Job job : mJobs) {
            job.cancel();
            jobsAborted++;
            mJobMap.remove(job.getTaskId());
            mResultCb.remove(job.getTaskId());
            mCompletitionCb.remove(job.getTaskId());
        }
        mJobs.clear();

        // release the connection to ocr service
        if (mOcr != null)
            mOcr.release();

        this.notify();
        Log.d(TAG, "Canceled " + worksCanceled + " pending works");
        Log.d(TAG, "Aborted " + jobsAborted + " started jobs");
    }

    public void setParameters(Ocr.Parameters parameters) {
        mOcr.setParameters(parameters);
    }

    /* Forward the callback when the task ends, and resluts comes from service */
    @Override
    public void onResult(long token, OcrResult result) {
        if (mResultCb.containsKey(token))
            mResultCb.get(token).onTaskResult(token, result);
    }

    @Override
    public void onCompleted(long token, List<OcrResult> results) {
        if (mCompletitionCb.containsKey(token))
            mCompletitionCb.get(token).onTaskCompleted(results);
        jobFinished(token);
    }

    /**
     * Stop looper task to serve request
     */
    private void stopLooperTaskIfNeeded() {
        if (mRqstProducer != null){
            synchronized (mWorkQueue) {
                mRqstProducer.cancel(false);
                mWorkQueue.notify();
            }
        }
    }

    public long enqueueToService(Bitmap bm, CompletionCallback compCb, ResultCallback resCb) {
        Ocr.Job job = mOcr.enqueue(bm);
        if (job == null) {
            Log.e(TAG, "Text recognition call failed");
            return 0;
        }else{
            jobEnqueued(job, compCb, resCb);
            return job.getTaskId();
        }

    }

    public boolean enqueueToService(File file, CompletionCallback compCb, ResultCallback resCb) {
        Ocr.Job job = mOcr.enqueue(file);
        if (job == null) {
            Log.e(TAG, "Text recognition call failed");
            return false;
        }else{
            jobEnqueued(job, compCb, resCb);
            return true;
        }
    }

    private void jobEnqueued(Ocr.Job job, CompletionCallback compCb, ResultCallback resCb) {
        mJobs.add(job);
        mJobMap.put(job.getTaskId(), job);
        mResultCb.put(job.getTaskId(), resCb);
        mCompletitionCb.put(job.getTaskId(), compCb);
    }

    /** Notify that the OCRWork is finished and remove it from the processing list */
    synchronized void jobFinished(long token) {
        jobsFinished++;
        if (!mJobMap.containsKey(token)) {
            Log.e(TAG, "Unrecognized job is finished!");
            return;
        }
        Ocr.Job job = mJobMap.get(token);
        mJobs.remove(job);
        mJobMap.remove(token);
        if (mJobs.isEmpty())
            mStatus = OCRWorkProcessor.Status.FINISH;
        this.notify();
    }

    /** Notify that the async task was stopped and update the processor status properly */
    private void producerStopped() {
        synchronized (mWorkQueue) {
            if (!mWorkQueue.isEmpty())
                mStatus = Status.STOP;
        }
        synchronized (this){
            this.notifyAll();
        }
    }

    private class OcrRequestProducerAsyncTask extends AsyncTask<Void,Void,Boolean>{

        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean success = false;
            Log.d(TAG,"Start to processing pending request..");
            try {
                OCRWork work;
                while (!isCancelled()) {
                    synchronized (mWorkQueue) {
                        if ((work = mWorkQueue.poll()) != null) {
                            mStatus = OCRWorkProcessor.Status.BUSY;
                            work.start();
                            worksStarted++;
                        } else {
                            Log.d(TAG, "Waiting work request..");
                            mWorkQueue.wait();
                        }
                    }
                }
                success = true;
                Log.d(TAG, "Stopped");
            } catch (InterruptedException e) {
                e.printStackTrace();
                success = false;
            }finally {
                producerStopped();
                return success;
            }
        }
    }



    /**
     * Callback interface that work have to implement to handle result from ocr
     * service.
     */
    public interface CompletionCallback {
        void onTaskCompleted(List<OcrResult> results);
    }

    /**
     * Handles the callback for a single mid-recognition result.
     */
    public interface ResultCallback {
        void onTaskResult(long id, OcrResult result);
    }

}
