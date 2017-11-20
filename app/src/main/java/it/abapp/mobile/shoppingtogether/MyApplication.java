package it.abapp.mobile.shoppingtogether;

import android.app.Application;
import android.content.Context;

/**
 * Created by al.borile on 20/11/2017.
 */



public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    private static final String KEY_APP_CRASHED = "KEY_APP_CRASHED";

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler( new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable exception) {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }
        } );

    }
}


