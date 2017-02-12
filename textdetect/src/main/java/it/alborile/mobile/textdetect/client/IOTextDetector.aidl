// IOTextDetector.aidl
package it.alborile.mobile.textdetect.client;

// Declare any non-default types here with import statements
import it.alborile.mobile.textdetect.client.IOTextDetectorCallback;
import it.alborile.mobile.textdetect.client.TextDetector;

interface IOTextDetector {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void setCallback(in IOTextDetectorCallback callback);
    long enqueueData(in byte[] jpegData, in TextDetector.Parameters params);
    long enqueueFile(in String filename, in TextDetector.Parameters params);
    boolean cancel(in long taskId);
    boolean stop();
    int getVersion();
}
