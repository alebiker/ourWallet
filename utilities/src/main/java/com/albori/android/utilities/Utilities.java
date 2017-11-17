package com.albori.android.utilities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static android.content.ContentValues.TAG;

/**
 * Created by al.borile on 16/11/2017.
 */

public class Utilities {

    private static final String LOG_TAG = "Utilities";
    private static Utilities instance;
    private static Context mContext;

    public static <T> void executeAsyncTask(AsyncTask<T, ?, ?> asyncTask, T... params) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        else
            asyncTask.execute(params);
    }

    public static <T, I, P> void executeAsyncTask(AsyncTask<T, I, P> mCurrentTask) {
        executeAsyncTask(mCurrentTask, null);
    }

    public static Utilities getInstance(Context context) {
        if(instance == null){
            instance = new Utilities();
        }
        if(context != null)
            mContext = context;
        return instance;
    }

    public static boolean isValidImage(Uri mImgPath) {
        int[] mWidth = new int[1];
        int[] mHeight = new int[1];
        return isValidImage(mImgPath, mWidth, mHeight);
    }

    public static Bitmap loadImage(Uri mImgPath) throws IOException {
        return loadImage(mImgPath, 0, 0, null);
    }

    public static Bitmap loadImage(Uri imageUri, int reqWidth, int reqHeight, Matrix outScale) throws IOException {

//        String picPath = pathBitmap.getEncodedPath();
        InputStream input = mContext.getContentResolver().openInputStream(imageUri);
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        if(reqHeight > 0 && reqHeight < 0){
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            // set output params
            outScale.setScale(options.inSampleSize, options.inSampleSize);
        }

        input = mContext.getContentResolver().openInputStream(imageUri);
//        Bitmap bpm = BitmapFactory.decodeStream(input);
        Bitmap bpm = BitmapFactory.decodeStream(input, null, options);
        input.close();

        return bpm;
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static void deleteImage(String path) {

    }

    public static Uri saveBitmap(Bitmap bitmap, Bitmap.CompressFormat jpeg, String external) {
        return null;
    }

    public static boolean isValidImage(Uri path, int[] width, int[] height) {
        //        String picPath = pathBitmap.getEncodedPath();
        InputStream input = null;
        try {
            input = mContext.getContentResolver().openInputStream(path);

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(input, null, options);

        width[0] = options.outWidth;
        height[0] = options.outHeight;

        input.close();

        return width[0] > 0 && height[0] > 0;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Error during fetching image!");
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Error during image validation!");
            return false;
        }
    }

    public static File createPublicImageFile(String photoname, String extension) throws IOException {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), photoname+"."+extension);
        if (!file.mkdirs()) {
            Log.e(LOG_TAG, "Directory not created");
        }
        return file;
    }

    public static void galleryAddPic(String mNewCameraPhotoPath) {
//        sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + Environment.getExternalStorageDirectory())));
    }
    public static String formatToCash(float amount) {

        return "€ " + amount;
    }

    public static File createPublicDirectory(String dirName) {
        File folder = new File(Environment.getExternalStorageDirectory().getPath() +
                File.separator + dirName);
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
        if (success) {
            Log.d(TAG, "createPublicDirectory: dir " + dirName + " created");
            // Do something on success
        } else {
            Log.e(TAG, "createPublicDirectory: error during dir " + dirName + " creation!");
            // Do something else on failure
        }
        return folder;
    }

    public static class PICTURES_LOCATION {
        public static String EXTERNAL = "";
    }

    public static Uri saveBitmap (){

        return null;
    }

    public static String getPathFromContentFormat(Uri mPath){

        return null;
    }

}

