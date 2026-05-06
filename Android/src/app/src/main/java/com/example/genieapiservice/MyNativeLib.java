package com.example.genieapiservice;

public class MyNativeLib {
    static {
        System.loadLibrary("JNIGenieAPIService");
        System.loadLibrary("GenieAPIService");
    }

    public native void runService(String[] args);
    public native void stopService();
}
