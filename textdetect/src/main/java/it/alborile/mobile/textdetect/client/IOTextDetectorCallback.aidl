// IOTextDetectorCallback.aidl
package it.alborile.mobile.textdetect.client;

// Declare any non-default types here with import statements

oneway interface IOTextDetectorCallback {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void onCompleted(in long token, in List<Rect> results);
}
