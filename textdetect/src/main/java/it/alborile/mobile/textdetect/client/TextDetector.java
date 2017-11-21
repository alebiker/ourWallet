/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package it.alborile.mobile.textdetect.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;

import it.alborile.mobile.ocr.client.Intents;
import it.alborile.mobile.ocr.client.VersionAlert;

/**
 * Recognizes text in images. This abstracts away the complexities of using the
 * OCR service such as setting up the IBinder connection and handling
 * RemoteExceptions, etc. Specifically, this class initializes the OCR service
 * and pushes recognization requests across IPC for processing in the service
 * thread.
 *
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TextDetector {
    private static final String TAG = "TextDetector";

    // This is the minimum version of the Ocr service that is needed by this
    // version of the library stub.
    private static final int MIN_VER = 1;

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILURE = 1;
    public static final int STATUS_MISSING = 2;

    public static final int ERROR = -1;
    public static final int SUCCESS = 1;

    public static final long INVALID_TOKEN = -1;

    private static final int BINDER_SIZE_LIMIT = 40000;

    private int mVersion = -1;

    private IOTextDetector mIOTextDetector;

    private ServiceConnection mServiceConnection;

    private boolean mStorageAvailable;

    private boolean mSuppressAlerts;

    private WeakReference<Context> mContext;

    private ResultCallback mOnResult;

    private CompletionCallback mOnCompleted;

    private Parameters mParameters;

    /**
     * The constructor for the OCR service client. Initializes the service if
     * necessary and calls the supplied InitCallback when it's ready.
     *
     * @param context the context of the parent activity
     * @param init the callback to call on initialization
     */
    public TextDetector(Context context, InitCallback init) {
        this(context, init, false);
    }

    /**
     * The constructor for the OCR service client. Initializes the service if
     * necessary and calls the supplied InitCallback when it's ready.
     * <p>
     * You may optionally set suppressAlerts to true to turn off alert dialogs.
     *
     * @param context the context of the parent activity
     * @param init the callback to call on initialization
     * @param suppressAlerts <code>true</code> to suppress alert dialogs
     */
    public TextDetector(Context context, InitCallback init, boolean suppressAlerts) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        mContext = new WeakReference<Context>(context);
        mSuppressAlerts = suppressAlerts;
        mParameters = new Parameters();
        mParameters.setFlag(Parameters.FLAG_DEBUG_MODE, true);

        connectTextDetectionService(init);
    }

    /**
     * Sets the result callback. If text detection is enabled, this will be
     * called once for each individual box before the completion callback
     * occurs.
     *
     * @param callback
     */
    public void setResultCallback(ResultCallback callback) {
        mOnResult = callback;
    }

    /**
     * Sets the completion callback. This is called when recognition is complete
     * and receives an ArrayList of results.
     *
     * @param callback
     */
    public void setCompletionCallback(CompletionCallback callback) {
        mOnCompleted = callback;
    }

    /**
     * Enqueues an image represented as a Bitmap for OCR.
     *
     * @param bitmap The bitmap on which to perform OCR.
     * @return A Job representing the queued OCR job.
     */
    public Job enqueue(Bitmap bitmap) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap must be non-null");
        }

        // TODO(alanv): Replace this with native Bitmap conversion
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        //bitmap.compress(CompressFormat.JPEG, 85, byteStream);
        bitmap.compress(CompressFormat.JPEG, 100, byteStream);

        byte[] jpegData = byteStream.toByteArray();
        return enqueue(jpegData);
    }

    /**
     * Enqueues an image represented as JPEG-compressed bytes for OCR.
     *
     * @param jpegData The JPEG-compressed image on which to perform OCR.
     * @return A Job representing the queued OCR job.
     */
    public Job enqueue(byte[] jpegData) {
        if (jpegData == null) {
            throw new IllegalArgumentException("JPEG data must be non-null");
        }

        // If we're over the binder size limit, write to disk.
        if (jpegData.length > BINDER_SIZE_LIMIT) {
            Log.d(TAG,"Image exceed the binder transfer limit. Cache&Enqueue.");
            return cacheAndEnqueue(jpegData);
        }

        try {
            long taskId = mIOTextDetector.enqueueData(jpegData, mParameters);
            return new Job(taskId);
        } catch (DeadObjectException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Internal method that writes image bytes to disk when they exceed the
     * binder transaction limit.
     *
     * @param data The bytes to write to disk.
     * @return A Job representing the queued OCR job.
     */
    private Job cacheAndEnqueue(byte[] data) {
        Job job = null;

        try {
            File cacheDir = mContext.get().getExternalCacheDir();
            File cached = File.createTempFile("ocr", ".jpg", cacheDir);

            FileOutputStream output = new FileOutputStream(cached);
            output.write(data);
            output.close();

            job = enqueue(cached);

            if (job != null) {
                job.mCached = cached;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return job;
    }

    /**
     * Enqueues an image represented as an encoded file. The file extension must
     * match the encoding and must be one of the following formats:
     * <ol>
     * <li>JPEG</li>
     * <li>BMP</li>
     * </ol>
     *
     * @param file An encoded file containing the image to OCR.
     * @return A Job representing the queued OCR job.
     */
    public Job enqueue(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File must be non-null");
        }

        try {
            long taskId = mIOTextDetector.enqueueFile(file.getAbsolutePath(), mParameters);
            return new Job(taskId);
        } catch (DeadObjectException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Returns the OCR parameters that will be used to process new enqueue
     * requests. If changes are made, you must call setParameters to commit
     * them.
     *
     * @return The parameters used when processing new OCR requests.
     */
    public Parameters getParameters() {
        return mParameters;
    }

    /**
     * Sets the OCR parameters that will be used to process new enqueue
     * requests.
     *
     * @param parameters The parameters to use when processing new OCR requests.
     */
    public void setParameters(Parameters parameters) {
        mParameters = parameters;
    }

    /**
     * Disconnects from the Ocr service.
     * <p>
     * It is recommended that you call this as soon as you're done with the Ocr
     * object. After this call the receiving Ocr object will be unusable.
     */
    public synchronized void release() {
        mOnCompleted = null;
        mOnResult = null;

        try {
            Context context = mContext.get();

            if (context != null) {
                context.unbindService(mServiceConnection);
            }
        } catch (IllegalArgumentException e) {
            // Do nothing and fail silently since an error here indicates that
            // binding never succeeded in the first place.
        }

        mIOTextDetector = null;
        mContext = null;
    }

    /**
     * Internal method used to connect to the OCR service.
     *
     * @param init Initialization callback.
     */
    private void connectTextDetectionService(final InitCallback init) {
        // Initialize the OCR service, run the callback after the binding is
        // successful
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mIOTextDetector = IOTextDetector.Stub.asInterface(service);

                try {
                    mVersion = mIOTextDetector.getVersion();

                    // The Ocr service must be at least the min version needed
                    // by the library stub. Do not try to run the older Ocr with
                    // the newer library stub as the newer library may reference
                    // methods which are unavailable and cause a crash.

                    if (mVersion < MIN_VER) {
                        Log.e(TAG, "OCR service too old (version " + mVersion + " < " + MIN_VER
                                + ")");

                        if (!mSuppressAlerts) {
                            OnClickListener onClick = new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    postInitialized(init, STATUS_MISSING);
                                }
                            };

                            VersionAlert.createUpdateAlert(mContext.get(), null).show();
                        } else {
                            postInitialized(init, STATUS_MISSING);
                        }

                        return;
                    }

                    mStorageAvailable = Environment.getExternalStorageDirectory().exists();

                    if (!mStorageAvailable) {
                        Log.e(TAG, "External storage is not available");

                        if (!mSuppressAlerts) {
                            OnClickListener onClick = new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    postInitialized(init, STATUS_MISSING);
                                }
                            };

                            VersionAlert.createStorageAlert(mContext.get(), onClick).show();
                        } else {
                            postInitialized(init, STATUS_MISSING);
                        }

                        return;
                    }


                    // Set the callback so that we can receive completion events
                    mIOTextDetector.setCallback(mCallback);

                } catch (RemoteException e) {
                    Log.e(TAG, "Exception caught in onServiceConnected(): " + e.toString());

                    postInitialized(init, STATUS_FAILURE);

                    return;
                }

                postInitialized(init, STATUS_SUCCESS);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mIOTextDetector = null;
            }
        };

        Intent intent = new Intent(Intents.Service.ACTION_TEXT_DETECTION);
        intent.addCategory(Intents.Service.CATEGORY);
        intent.setPackage("it.abapp.mobile.shoppingtogether");
        //intent.setPackage("it.alborile.mobile");
        // Binding will fail only if the Ocr doesn't exist;
        // the OcrVersionAlert will give users a chance to install
        // the needed Ocr.

        Context context = mContext.get();

        if (!context.getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Cannot bind to OCR service, assuming not installed");

            OnClickListener onClick = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    postInitialized(init, STATUS_MISSING);
                }
            };

            if (!mSuppressAlerts) {
                VersionAlert.createInstallAlert(context, onClick).show();
            }

            return;
        }
    }

    /**
     * Passes the initialization status to the InitCallback.
     *
     * @param init The initialization callback.
     * @param status The initialization status.
     */
    private void postInitialized(final InitCallback init, final int status) {
        if (init != null) {
            init.onInitialized(status);
        }
    }

    /**
     * Cancels all active and pending OCR jobs.
     */
    public void stop() {
        if (mIOTextDetector == null) {
            Log.e(TAG, "Attempted to call stop() without a connection to Ocr service.");
            return;
        }

        try {
            mIOTextDetector.stop();
        } catch (DeadObjectException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the version number of the Ocr library that the user has
     * installed.
     *
     * @return te version number of the Ocr library that the user has installed
     */
    public int getVersion() {
        return mVersion;
    }

    /**
     * Checks if the Ocr service is installed or not
     *
     * @return a boolean that indicates whether the Ocr service is installed
     */
    public static boolean isInstalled(Context ctx) {
        Intent intent = new Intent(Intents.Service.ACTION_TEXT_DETECTION);

        PackageManager pm = ctx.getPackageManager();
        ResolveInfo info = pm.resolveService(intent, 0);

        if (info == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Handles the init callback when the Ocr service has initialized.
     * Useful for start sending the ocr request to the service
     */
    public static interface InitCallback {
        public void onInitialized(int status);
    }

    /**
     * Handles the callback for when recognition is completed.
     * Useful for handle properly the results
     */
    public static interface CompletionCallback {
        public void onCompleted(long token, List<Rect> results);
    }

    /**
     * Handles the callback for a single mid-recognition result.
     */
    public static interface ResultCallback {
        public void onResult(long token, int result);
    }

    private final IOTextDetectorCallback mCallback = new IOTextDetectorCallback.Stub() {
        @Override
        public void onCompleted(final long token, final List<Rect> results) {
            if (mOnCompleted != null) {
                mOnCompleted.onCompleted(token, results);
            }
        }
    };

    /**
     * Represents a single OCR job.
     *
     * @author alanv@google.com (Alan Viverette)
     */
    public class Job {
        long mTaskId;

        File mCached;

        Job(long taskId) {
            mTaskId = taskId;
            mCached = null;
        }

        public long getTaskId(){
            return this.mTaskId;
        }

        @Override
        protected void finalize() throws Throwable {
            // If we have a cached file, delete it when we're done.
            try {
                if (mCached != null) {
                    mCached.delete();
                }
            } finally {
                super.finalize();
            }
        }

        /**
         * Cancels this OCR job.
         */
        public void cancel() {
            try {
                mIOTextDetector.cancel(mTaskId);
            } catch (DeadObjectException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Represents a set of OCR processing parameters.
     *
     * @author alanv@google.com (Alan Viverette)
     */
    public static class Parameters implements Parcelable {

        public static final String VAR_OUT_DIR = "out_dir";

        /** Aligns horizontal text in an image */
        public static final String FLAG_ALIGN_TEXT = "align_text";

        /** Reverse the image if the text seems to be reverse */
        public static final String FLAG_REVERSE_CHECK = "reverse_checking";

        /** Write intermediate files to external storage */
        public static final String FLAG_DEBUG_MODE = "debug_mode";

        /** Number detection mode, that detect standalone numbers in images */
        public static final String FLAG_NUMBER_DETECTION = "number_detection";

        /** Small characters <15px detection */
        public static final String FLAG_SMALL_DETECTION = "small_mode";


        private Bundle mVariables;

        private Bundle mFlags;

        /**
         * Constructs a new Parameters object using the default values.
         */
        public Parameters() {
            mVariables = new Bundle();
            mFlags = new Bundle();
        }

        /**
         * Constructs a new Parameters object using the default values.
         */
        public Parameters(Parameters paramToClone) {
            mVariables = new Bundle();
            mFlags = new Bundle();

            for (String k : paramToClone.getVariableKeys()){
                setVariable(k, paramToClone.getVariable(k));
            }

            setFlag(FLAG_DEBUG_MODE,paramToClone.getFlag(FLAG_DEBUG_MODE));
            setFlag(FLAG_ALIGN_TEXT,paramToClone.getFlag(FLAG_ALIGN_TEXT));
            setFlag(FLAG_REVERSE_CHECK,paramToClone.getFlag(FLAG_REVERSE_CHECK));
            setFlag(FLAG_NUMBER_DETECTION,paramToClone.getFlag(FLAG_NUMBER_DETECTION));
            setFlag(FLAG_SMALL_DETECTION,paramToClone.getFlag(FLAG_SMALL_DETECTION));

        }

        /**
         * Sets the value of the variable identified by <code>key</code>. If the
         * value is null, removes the variable.
         *
         * @param key The key that identifies the variable to set.
         * @param value The String value to assign to the variable.
         */
        public void setVariable(String key, String value) {
            if (value == null) {
                mVariables.remove(key);
            } else {
                mVariables.putString(key, value);
            }
        }

        /**
         * Returns the value of the variable identified by <code>key</code>, or
         * <code>null</code> if it has not been set.
         *
         * @param key The key that identifies the variable to retrieve.
         * @return The value of the variable or <code>null</code> if it has not
         *         been set.
         */
        public String getVariable(String key) {
            return mVariables.getString(key);
        }

        /**
         * Returns the list of keys identifying variables that have been set.
         *
         * @return A set of Strings representing the variable keys that have
         *         been set.
         */
        public Set<String> getVariableKeys() {
            return mVariables.keySet();
        }

        /**
         * Sets the value of the flag identified by <code>key</code>. If the
         * value is <code>null</code>, removes the flag.
         *
         * @param key The key that identifies the flag to set.
         * @param value The boolean value to assign to the flag.
         */
        public void setFlag(String key, boolean value) {
            mFlags.putBoolean(key, value);
        }

        /**
         * Returns the value of the flag identified by <code>key</code>. If
         * <code>key</code> has not been set, returns <code>false</code>.
         *
         * @param key The key that identifies the flag to retrieve.
         * @return The value of the flag or <code>false</code> if it has not
         *         been set.
         */
        public boolean getFlag(String key) {
            if (!mFlags.containsKey(key)) {
                return false;
            } else {
                return mFlags.getBoolean(key);
            }
        }

        // ************************
        // * Parcelable functions *
        // ************************

        private Parameters(Parcel src) {
            readFromParcel(src);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeBundle(mVariables);
            dest.writeBundle(mFlags);
        }

        private void readFromParcel(Parcel src) {
            mVariables = src.readBundle();
            mFlags = src.readBundle();
        }

        public static final Creator<Parameters> CREATOR = new Creator<Parameters>() {
            @Override
            public Parameters createFromParcel(Parcel in) {
                return new Parameters(in);
            }

            @Override
            public Parameters[] newArray(int size) {
                return new Parameters[size];
            }
        };
    }
}
