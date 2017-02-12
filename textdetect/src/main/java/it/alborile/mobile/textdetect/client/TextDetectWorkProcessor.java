package it.alborile.mobile.textdetect.client;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import com.albori.android.utilities.Utilities;

/**
 * Created by alex on 05/02/16.
 */
//TODO use only one Ocr client object
public class TextDetectWorkProcessor implements TextDetector.CompletionCallback {
    private static final String TAG = "TextDetWorkProcessor";

    private final Context mContext;
    protected Queue<TextDetectWork> mWorkQueue;
    private TextDetectRequestProducerAsyncTask requestProducer;
    protected int worksEnqueued = 0, worksStarted = 0, worksFinished = 0,
            jobsFinished = 0, worksCanceled = 0, worksAborted = 0, jobsAborted = 0;
    private TextDetector mTextDetector;
    private HashMap<Long,CompletionCallback> mCompletitionCb;
    private HashMap<Long,TextDetector.Job> mJobMap;
    private List<TextDetector.Job> mJobs;
    private Semaphore semWorks;
    private Status mStatus;
    private boolean isRunning = false;



    /** INDICATES THE DIFFERENT STATUS OF THE PROCESSOR
     * {@link IDLE} The processor has any valid work in queue and every past works are completed
     * {@link READY} The processor is ready to start dequeue and send work to service
     * {@link BUSY} The processor is sending the work in the queue and waits until every response
     * {@link STOPPED} The processor has been stopped and release all enqueued works
     * comes
     * */
    public enum Status{
        BUSY, STOPPED, IDLE, READY
    }

    public TextDetectWorkProcessor(Context context) {
        this(context, null);
        Utilities.getInstance(context);
    }

    public TextDetectWorkProcessor(Context context, List<TextDetectWork> workList) {
        this.mContext = context;
        if (workList != null)
            this.mWorkQueue = new ConcurrentLinkedQueue<TextDetectWork>(workList);
        else
            this.mWorkQueue = new ConcurrentLinkedQueue<>();
        this.mCompletitionCb = new HashMap<>();
        this.mJobMap = new HashMap<>();
        this.semWorks = new Semaphore(0);
        this.mStatus = Status.IDLE;
    }

    public int getWorksCompleted() {
        return worksFinished;
    }

    public int getJobsAborted() {
        return jobsAborted;
    }

    public int getWorksStarted() {
        return worksStarted;
    }

    public int getPendingWork(){
        return mWorkQueue.size();
    }

    /**
     * Return the status of the request elaboration.
     * @return The status of enum type Status of the processor request elaboration
     */
    public Status getStatus(){
        return mStatus;
    }

    public boolean isProducerAlive(){
        return isRunning;
    }

    public void enqueue(TextDetectWork work){
        work.attachProcessor(this);
        synchronized (mWorkQueue) {
            mWorkQueue.add(work);
            semWorks.release();
            worksEnqueued++;
            updateStatus();
        }
    }

    synchronized public void start(){
        if (mStatus.equals(Status.BUSY)){
            Log.e(TAG, "Is already started!");
            return;
        }
        mStatus = Status.BUSY;
        requestProducer = new TextDetectRequestProducerAsyncTask();

        // set the callback that is executed if the Ocr service is connected successfully
        TextDetector.InitCallback onInit = new TextDetector.InitCallback() {
            @Override
            public void onInitialized(int status) {
                if(status == TextDetector.STATUS_SUCCESS){
                    mJobs = new ArrayList();
                    Utilities.executeAsyncTask(requestProducer);
                }
            }
        };

        mTextDetector = new TextDetector(mContext, onInit);

        // set the callback for the results
        mTextDetector.setCompletionCallback(this);
    }

    /**
     * Stopping all pending request not started yet and release it
     */
    synchronized public void stop(){
        mStatus = Status.STOPPED;
        stopProducerIfNeeded();

        TextDetectWork work;
        while ((work = mWorkQueue.poll()) != null) {
            synchronized (mWorkQueue) {
                work.release();
                worksCanceled++;
                mWorkQueue.notify();
            }
        }

        // Abort any started request
        for (TextDetector.Job job : mJobs){
            job.cancel();
            jobsAborted++;
            mJobMap.remove(job.getTaskId());
            mCompletitionCb.remove(job.getTaskId());
        }
        mJobs.clear();

        // release the connection to text detector service
        if (mTextDetector != null)
            mTextDetector.release();

        this.notify();
        Log.d(TAG, "Canceled " + worksCanceled + " pending works");
        Log.d(TAG, "Aborted " + jobsAborted + " started jobs");
    }


    public void setParameters(TextDetector.Parameters parameters) {
        mTextDetector.setParameters(parameters);
    }

    @Override
    public void onCompleted(long token, List<Rect> results) {
        if (mCompletitionCb.containsKey(token))
            mCompletitionCb.get(token).onCompleted(results);
        jobFinished(token);
    }

    /**
     * Stop looper task to loop request
     */
    private void stopProducerIfNeeded() {
        if (requestProducer != null) {
            requestProducer.cancel(false);
            semWorks.release();
        }
    }

    public boolean enqueueToService(Bitmap bm, CompletionCallback compCb) {
        TextDetector.Job job = mTextDetector.enqueue(bm);
        if (job == null) {
            Log.e(TAG, "Text recognition call failed");
            return false;
        }else{
            jobEnqueued(job, compCb);
            return true;
        }

    }

    public boolean enqueueToService(File file, CompletionCallback compCb) {
        TextDetector.Job job = mTextDetector.enqueue(file);
        if (job == null) {
            Log.e(TAG, "Text recognition call failed");
            return false;
        }else{
            jobEnqueued(job, compCb);
            return true;
        }
    }

    private void jobEnqueued(TextDetector.Job job, CompletionCallback compCb) {
        mJobs.add(job);
        mJobMap.put(job.getTaskId(), job);
        mCompletitionCb.put(job.getTaskId(), compCb);
    }

    /** Notify that the TextDetector Work is finished and remove it from the processing list */
    synchronized void jobFinished(long token) {
        jobsFinished++;
        if (!mJobMap.containsKey(token)) {
            Log.e(TAG, "Unrecognized job is finished!");
            return;
        }
        TextDetector.Job job = mJobMap.get(token);
        mJobs.remove(job);
        mJobMap.remove(token);

        this.notify();
    }

    /** Signal that a work is been finished and wait until the consumer has takes care of it */
    void workFinished() {
        worksFinished++;
        updateStatus();
        semWorks.release();
    }

    /** Signal that a work is been finished and wait until the consumer has takes care of it */
    void workAborted() {
        worksAborted++;
        updateStatus();
        semWorks.release();
    }

    synchronized void updateStatus(){
        if(mStatus.equals(Status.STOPPED) && !mWorkQueue.isEmpty()){
            mStatus = Status.READY;
        }
        if (worksFinished + worksAborted == worksEnqueued)
            mStatus = Status.IDLE;
    }

    /** Notify that the async task was stopped and update the processor status properly */
    private void producerStopped() {
        isRunning = false;
        synchronized (this){
            this.notifyAll();
        }
    }

    private class TextDetectRequestProducerAsyncTask extends AsyncTask<Void,Void,Boolean>{

        static final String TAG = "TextDetectProducerAT";
        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.d(TAG,"Start to processing pending request..");
            isRunning = true;
            boolean success = false;
            try {
                TextDetectWork work;
                /** Loop until inCanceled*/
                while (!(isCancelled())) {
                    synchronized (mWorkQueue) {
                        if ((work = mWorkQueue.poll()) != null) {
                            work.start();
                            worksStarted++;
                            semWorks.acquire();
                        }
                        else {
                            Log.d(TAG, "Waiting work request..");
                            semWorks.acquire();
                        }
                    }
                }
                success = true;
                Log.d(TAG,"Stopped");
            } catch (InterruptedException e) {
                e.printStackTrace();
                success = false;
            } finally {
                producerStopped();
                return success;
            }
        }
    }

    /**
     * Handles the callback for when recognition of specific work is completed.
     * Useful for handle properly the results
     */
    public interface CompletionCallback {
        void onCompleted(List<Rect> results);
    }


}
