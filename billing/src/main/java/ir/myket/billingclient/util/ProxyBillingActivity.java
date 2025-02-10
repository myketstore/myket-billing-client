package ir.myket.billingclient.util;

import static ir.myket.billingclient.IabHelper.RESPONSE_BUY_INTENT;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.ResultReceiver;

import androidx.core.content.IntentSanitizer;

import java.util.Objects;

import ir.myket.billingclient.IabHelper;

public class ProxyBillingActivity extends Activity {
    public static final String BILLING_RECEIVER_KEY = "billing_receiver";
    public static final String PURCHASE_RESULT = "purchase_result";
    private static final int REQUEST_CODE = 100;

    private final IABLogger iabLogger = new IABLogger();
    private ResultReceiver purchaseBillingReceiver;

    /**
     * @noinspection Convert2MethodRef
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        iabLogger.logDebug("Launching Store billing flow");
        setRequestedOrientation(
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ?
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        try {
            purchaseBillingReceiver = (ResultReceiver) getIntent().getParcelableExtra(BILLING_RECEIVER_KEY);
            if (getIntent().getParcelableExtra(RESPONSE_BUY_INTENT) instanceof PendingIntent) {
                PendingIntent pendingIntent = getIntent().getParcelableExtra(RESPONSE_BUY_INTENT);
                startIntentSenderForResult(
                        pendingIntent.getIntentSender(), REQUEST_CODE, new Intent(), 0, 0, 0);

            } else if (getIntent().getParcelableExtra(RESPONSE_BUY_INTENT) instanceof Intent) {
                Intent forward = getIntent().getParcelableExtra(RESPONSE_BUY_INTENT);
                Objects.requireNonNull(forward).putExtra(IAB.KEY_SDK_VERSION, IAB.SDK_VERSION);
                ComponentName componentName = forward.resolveActivity(getPackageManager());
                forward.setComponent(componentName);
                Intent intent = new IntentSanitizer.Builder()
                        .allowComponent(componentName)
                        .allowType(s -> true)
                        .allowPackage(IabHelper.getMarketId(getApplicationContext()))
                        .allowAction(s -> true)
                        .allowData(uri -> true)
                        .allowExtra("DEVELOPER_PAYLOAD", o -> o != null)
                        .allowExtra("SKU", o -> o != null)
                        .allowExtra("PACKAGE_NAME", o -> o != null)
                        .allowExtra("ITEM_TYPE", o -> o != null)
                        .allowExtra("SDK_VERSION", o -> o != null)
                        .allowExtra("purchaseType", o -> o != null)
                        .allowExtra("dynamicPriceToken", o -> o != null)
                        .allowExtra("extraData", o -> o != null)
                        .allowExtra("dealerPackageName", o -> o != null)
                        .allowExtra("sku", o -> o != null)
                        .allowExtra("devPayload", o -> o != null)
                        .build()
                        .sanitizeByFiltering(forward);

                startActivityForResult(intent, REQUEST_CODE);
            } else {
                iabLogger.logWarn("parcelableExtra RESPONSE_BUY_INTENT is not pendingInstall or intent");
                purchaseBillingReceiver.send(RESULT_FIRST_USER, getReceiverResult(null));
                finish();
            }
        } catch (Throwable e) {
            iabLogger.logWarn("Got exception while trying to start a purchase flow: " + e);
            purchaseBillingReceiver.send(RESULT_FIRST_USER, getReceiverResult(null));
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            iabLogger.logWarn("Got purchases updated result with resultCode " + resultCode);
            purchaseBillingReceiver.send(resultCode, getReceiverResult(data));
            finish();
        }
    }

    private Bundle getReceiverResult(Intent data) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(PURCHASE_RESULT, data);
        return bundle;
    }
}