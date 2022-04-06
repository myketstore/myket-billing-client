package ir.myket.billingclient.util;

import android.util.Log;

public class IABLogger {

    public boolean mDebugLog = false;
    public String mDebugTag = "IabHelper";

    public void logDebug(String msg) {
        if (mDebugLog) {
            Log.d(mDebugTag, msg);
        }
    }

    public void logError(String msg) {
        Log.e(mDebugTag, "In-app billing error: " + msg);
    }

    public void logWarn(String msg) {
        Log.w(mDebugTag, "In-app billing warning: " + msg);
    }
}
