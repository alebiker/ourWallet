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

package it.alborile.mobile.ocr.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import it.alborile.mobile.textdetect.client.IOTextDetector;
import it.alborile.mobile.textdetect.client.IOTextDetectorCallback;
import it.alborile.mobile.ocr.client.IOcr;
import it.alborile.mobile.ocr.client.IOcrCallback;
import it.alborile.mobile.ocr.client.Intents;
import it.alborile.mobile.ocr.client.Language;
import it.alborile.mobile.ocr.client.Ocr;
import it.alborile.mobile.ocr.client.Ocr.Parameters;
import it.alborile.mobile.ocr.client.OcrResult;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import it.alborile.mobile.ocr.service.OcrTaskProcessor.OcrTaskListener;
import it.alborile.mobile.textdetect.service.TextDetectorTaskProcessor;
import it.alborile.mobile.textdetect.client.TextDetector;

/**
 * Provides services for recognizing text in images. Specifically handles
 * binding the service to clients.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class OcrService extends Service {
    private static final String TAG = "OcrService";

    private final OcrService mSelf = this;

    private RemoteCallbackList<IOcrCallback> mCallbacks;
    private RemoteCallbackList<IOTextDetectorCallback> mTDCallbacks;
    private HashMap<Integer, IOcrCallback> mCallbacksMap;
    private HashMap<Integer, IOTextDetectorCallback> mTDCallbacksMap;
    private LanguageManager mLanguageManager;
    private OcrTaskProcessor mOcrTaskProcessor;
    private TextDetectorTaskProcessor mTextDetTaskProcessor;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service starting...");

        mCallbacks = new RemoteCallbackList<IOcrCallback>();
        mTDCallbacks = new RemoteCallbackList<IOTextDetectorCallback>();
        mCallbacksMap = new HashMap<Integer, IOcrCallback>();
        mTDCallbacksMap = new HashMap<Integer, IOTextDetectorCallback>();

        mLanguageManager = new LanguageManager(this);
        mLanguageManager.loadLanguages();

        mOcrTaskProcessor = new OcrTaskProcessor(mLanguageManager.getDatapath());
        mOcrTaskProcessor.setListener(mOcrTaskListener);

        mTextDetTaskProcessor = new TextDetectorTaskProcessor();
        mTextDetTaskProcessor.setListener(mTextDetectorTaskListener);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");

        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service is shutting down...");

        unregisterReceiver(mBroadcastReceiver);

        mCallbacks.kill();

        super.onDestroy();
    }

    private void ocrApplyDefaults(Parameters params) {
        // TODO(alanv): Make sure language choice is valid
        /*
         * boolean override = mPrefs.getBoolean("override_pref", false); if
         * (override || config.language == null) { config.language =
         * mPrefs.getString("lang_pref", "eng"); } if (override ||
         * !config.variables.containsKey(Config.VAR_ACCURACYVSPEED)) {
         * config.variables.put(Config.VAR_ACCURACYVSPEED,
         * mPrefs.getString("speed_pref", "0")); } if (override ||
         * !config.debug) { config.debug = mPrefs.getBoolean("debug_pref",
         * false); }
         */
    }

    private void tdApplyDefaults(TextDetector.Parameters params) {
        // TODO(alanv): Make sure language choice is valid
        /*
         * boolean override = mPrefs.getBoolean("override_pref", false); if
         * (override || config.language == null) { config.language =
         * mPrefs.getString("lang_pref", "eng"); } if (override ||
         * !config.variables.containsKey(Config.VAR_ACCURACYVSPEED)) {
         * config.variables.put(Config.VAR_ACCURACYVSPEED,
         * mPrefs.getString("speed_pref", "0")); } if (override ||
         * !config.debug) { config.debug = mPrefs.getBoolean("debug_pref",
         * false); }
         */
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Intents.Service.ACTION_OCR.equals(intent.getAction())) {
            for (String category : intent.getCategories()) {
                if (Intents.Service.CATEGORY.equals(category))
                    return mOcrBinder;
            }
        }
        if (Intents.Service.ACTION_TEXT_DETECTION.equals(intent.getAction())) {
            for (String category : intent.getCategories()) {
                if (Intents.Service.CATEGORY.equals(category))
                    return mTextDetBinder;
            }
        }

        return null;
    }

    /**
     * Attempts to cancel the OCR processor job with the specified token
     * belonging to the specified process ID.
     *
     * @param pid
     * @param token
     */
    private boolean cancel(int pid, long token) {
        return mOcrTaskProcessor.cancel(pid, token);
    }

    /**
     * Attempts to cancel all OCR processor jobs belonging to the specified
     * process ID.
     *
     * @param pid
     */
    private boolean stop(int pid) {
        return mOcrTaskProcessor.cancelAll(pid);
    }

    /**
     * Returns the version number of the OCR package. This version number is the
     * versionCode from the AndroidManifest.xml
     *
     * @return The version number of the OCR package
     */
    private int getVersion() {
        PackageManager manager = mSelf.getPackageManager();
        PackageInfo info = new PackageInfo();

        try {
            info = manager.getPackageInfo(mSelf.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();

            return -1;
        }

        return info.versionCode;
    }

    private long ocrEnqueueData(int pid, byte[] data, Parameters params) {
        if (data == null || data.length == 0) {
            Log.e(TAG, "Attempted to enqueue null or empty data");
            return Ocr.INVALID_TOKEN;
        }

        if (params == null) {
            Log.e(TAG, "Attempted to enqueue null params");
            return Ocr.INVALID_TOKEN;
        }

        ocrApplyDefaults(params);
        return mOcrTaskProcessor.enqueueData(pid, data, params);
    }

    private long ocrEnqueueFile(int pid, String filename, Parameters params) {
        if (filename == null) {
            Log.e(TAG, "Attempted to enqueue null filename");
            return Ocr.INVALID_TOKEN;
        }

        if (params == null) {
            Log.e(TAG, "Attempted to enqueue null params");
            return Ocr.INVALID_TOKEN;
        }

        final File file = new File(filename);

        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "Attempted to enqueue unreadable file");
            return Ocr.INVALID_TOKEN;
        }

        ocrApplyDefaults(params);

        return mOcrTaskProcessor.enqueueFile(pid, file, params);
    }

    private void ocrSetCallback(int pid, IOcrCallback callback) {
        if (callback != null) {
            mCallbacks.register(callback);
            mCallbacksMap.put(pid, callback);
        } else {
            callback = mCallbacksMap.get(pid);

            if (callback != null) {
                mCallbacks.unregister(callback);
            }
        }
    }

    private long textDetectorEnqueueData(int pid, byte[] data, TextDetector.Parameters params) {
        if (data == null || data.length == 0) {
            Log.e(TAG, "Attempted to enqueue null or empty data");
            return Ocr.INVALID_TOKEN;
        }

        if (params == null) {
            Log.e(TAG, "Attempted to enqueue null params");
            return Ocr.INVALID_TOKEN;
        }

        tdApplyDefaults(params);

        return mTextDetTaskProcessor.enqueueData(pid, data, params);
    }

    private long textDetectorEnqueueFile(int pid, String filename, TextDetector.Parameters params) {
        if (filename == null) {
            Log.e(TAG, "Attempted to enqueue null filename");
            return Ocr.INVALID_TOKEN;
        }

        if (params == null) {
            Log.e(TAG, "Attempted to enqueue null params");
            return Ocr.INVALID_TOKEN;
        }

        final File file = new File(filename);

        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "Attempted to enqueue unreadable file");
            return Ocr.INVALID_TOKEN;
        }

        tdApplyDefaults(params);

        return mTextDetTaskProcessor.enqueueFile(pid, file, params);
    }

    private void textDetectorSetCallback(int pid, IOTextDetectorCallback callback) {
        if (callback != null) {
            mTDCallbacks.register(callback);
            mTDCallbacksMap.put(pid, callback);
        } else {
            callback = mTDCallbacksMap.get(pid);

            if (callback != null) {
                mTDCallbacks.unregister(callback);
            }
        }
    }

    private final IOTextDetector.Stub mTextDetBinder = new IOTextDetector.Stub() {
        @Override
        public void setCallback(IOTextDetectorCallback callback) throws RemoteException {
            mSelf.textDetectorSetCallback(getCallingPid(), callback);
        }

        @Override
        public long enqueueData(byte[] jpegData, TextDetector.Parameters params) throws RemoteException {
            return mSelf.textDetectorEnqueueData(getCallingPid(), jpegData, params);
        }

        @Override
        public long enqueueFile(String filename, TextDetector.Parameters params) throws RemoteException {
            return mSelf.textDetectorEnqueueFile(getCallingPid(), filename, params);
        }

        @Override
        public boolean cancel(long taskId) throws RemoteException {
            //TODO
            return false;
        }

        @Override
        public boolean stop() throws RemoteException {
            //TODO
            return false;
        }

        @Override
        public int getVersion() {
            return mSelf.getVersion();
        }
    };

    private final IOcr.Stub mOcrBinder = new IOcr.Stub() {
        @Override
        public void setCallback(IOcrCallback cb) {
            mSelf.ocrSetCallback(getCallingPid(), cb);
        }

        @Override
        public long enqueueData(byte[] jpegData, Parameters params) {
            return mSelf.ocrEnqueueData(getCallingPid(), jpegData, params);
        }

        @Override
        public long enqueueFile(String filename, Parameters params) {
            return mSelf.ocrEnqueueFile(getCallingPid(), filename, params);
        }

        @Override
        public boolean cancel(long token) {
            return mSelf.cancel(getCallingPid(), token);
        }

        @Override
        public boolean stop() {
            return mSelf.stop(getCallingPid());
        }

        @Override
        public int getVersion() {
            return mSelf.getVersion();
        }

        @Override
        public boolean reloadLanguages() {
            return mLanguageManager.loadLanguages();
        }

        @Override
        public List<Language> getAvailableLanguages() {
            return mLanguageManager.getAvailable();
        }

        @Override
        public String getDatapath() {
            File datapath = mLanguageManager.getDatapath();

            return datapath == null ? null : datapath.getAbsolutePath();
        }

        @Override
        public String getTessdata() {
            File tessdata = mLanguageManager.getTessdata();

            return tessdata == null ? null : tessdata.getAbsolutePath();
        }
    };

    private final TextDetectorTaskProcessor.TextDetectorTaskListener mTextDetectorTaskListener = new TextDetectorTaskProcessor.TextDetectorTaskListener() {
        @Override
        public void onCompleted(int pid, long token, ArrayList<Rect> results) {
            IOTextDetectorCallback callback = mTDCallbacksMap.get(pid);

            if (callback == null) {
                return;
            }

            try {
                callback.onCompleted(token, results);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private final OcrTaskListener mOcrTaskListener = new OcrTaskListener() {
        @Override
        public void onResult(int pid, long token, OcrResult result) {
            final IOcrCallback callback = mCallbacksMap.get(pid);

            if (callback == null) {
                return;
            }

            try {
                callback.onResult(token, result);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCompleted(int pid, long token, ArrayList<OcrResult> results) {
            IOcrCallback callback = mCallbacksMap.get(pid);

            if (callback == null) {
                return;
            }

            try {
                callback.onCompleted(token, results);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())
                    || Intent.ACTION_MEDIA_UNMOUNTED.equals(intent.getAction())) {
                mLanguageManager.loadLanguages();
            } else if (Intent.ACTION_MEDIA_EJECT.equals(intent.getAction())) {
                mOcrTaskProcessor.abort();
                Log.i(TAG, "Preparing for media eject");
            }
        }
    };
}
