package ir.myket.billingclient.util;

import static ir.myket.billingclient.IabHelper.BILLING_RESPONSE_RESULT_OK;
import static ir.myket.billingclient.IabHelper.IABHELPER_BAD_RESPONSE;
import static ir.myket.billingclient.IabHelper.IABHELPER_UNKNOWN_ERROR;
import static ir.myket.billingclient.IabHelper.IABHELPER_UNKNOWN_PURCHASE_RESPONSE;
import static ir.myket.billingclient.IabHelper.IABHELPER_USER_CANCELLED;
import static ir.myket.billingclient.IabHelper.IABHELPER_VERIFICATION_FAILED;
import static ir.myket.billingclient.IabHelper.RESPONSE_CODE;
import static ir.myket.billingclient.IabHelper.RESPONSE_INAPP_PURCHASE_DATA;
import static ir.myket.billingclient.IabHelper.RESPONSE_INAPP_SIGNATURE;
import static ir.myket.billingclient.IabHelper.getResponseDesc;
import static ir.myket.billingclient.util.ProxyBillingActivity.PURCHASE_RESULT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;

import org.json.JSONException;

import ir.myket.billingclient.IabHelper;
import ir.myket.billingclient.util.communication.BillingSupportCommunication;
import ir.myket.billingclient.util.communication.OnConnectListener;

public abstract class IAB {

    private final String mSignatureBase64;
    // Are subscriptions supported?
    public boolean mSubscriptionsSupported = false;
    // Is setup done?
    public boolean mSetupDone = false;
    protected String bindAddress;
    protected String marketId;
    IABLogger logger;
    int apiVersion = 3;
    // The item type of the current purchase flow
    String mPurchasingItemType;
    // The listener registered on launchPurchaseFlow, which we have to call back when
    // the purchase finishes
    IabHelper.OnIabPurchaseFinishedListener mPurchaseListener;
    protected ResultReceiver purchaseResultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle purchaseResult) {
            IabResult result;
            // end of async purchase operation that started on launchPurchaseFlow
            flagEndAsync();

            Intent data = purchaseResult.getParcelable(PURCHASE_RESULT);

            if (data == null) {
                logger.logError("Null data in IAB activity result.");
                result = new IabResult(IABHELPER_BAD_RESPONSE, "Null data in IAB result");
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
                return;
            }

            int responseCode = getResponseCodeFromIntent(data);
            String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
            String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

            if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
                logger.logDebug("Successful resultCode from purchase activity.");
                logger.logDebug("Purchase data: " + purchaseData);
                logger.logDebug("Data signature: " + dataSignature);
                logger.logDebug("Extras: " + data.getExtras());
                logger.logDebug("Expected item type: " + mPurchasingItemType);

                if (purchaseData == null || dataSignature == null) {
                    logger.logError("BUG: either purchaseData or dataSignature is null.");
                    logger.logDebug("Extras: " + data.getExtras().toString());
                    result = new IabResult(IABHELPER_UNKNOWN_ERROR,
                            "IAB returned null purchaseData or dataSignature");
                    if (mPurchaseListener != null) {
                        mPurchaseListener.onIabPurchaseFinished(result, null);
                    }
                    return;
                }

                Purchase purchase = null;
                try {
                    purchase = new Purchase(mPurchasingItemType, purchaseData, dataSignature);
                    String sku = purchase.getSku();

                    // Verify signature
                    if (!Security.verifyPurchase(mSignatureBase64, purchaseData, dataSignature)) {
                        logger.logError("Purchase signature verification FAILED for sku " + sku);
                        result = new IabResult(IABHELPER_VERIFICATION_FAILED,
                                "Signature verification failed for sku " + sku);
                        if (mPurchaseListener != null) {
                            mPurchaseListener.onIabPurchaseFinished(result, purchase);
                        }
                        return;
                    }

                    logger.logDebug("Purchase signature successfully verified.");
                } catch (JSONException e) {
                    logger.logError("Failed to parse purchase data.");
                    e.printStackTrace();
                    result = new IabResult(IABHELPER_BAD_RESPONSE, "Failed to parse purchase data.");
                    if (mPurchaseListener != null) {
                        mPurchaseListener.onIabPurchaseFinished(result, null);
                    }
                    return;
                }

                if (mPurchaseListener != null) {
                    mPurchaseListener
                            .onIabPurchaseFinished(new IabResult(BILLING_RESPONSE_RESULT_OK, "Success"),
                                    purchase);
                }
            } else if (resultCode == Activity.RESULT_OK) {
                // result code was OK, but in-app billing response was not OK.
                logger.logDebug("Result code was OK but in-app billing response was not OK: " +
                        getResponseDesc(responseCode));
                if (mPurchaseListener != null) {
                    result = new IabResult(responseCode, "Problem purchasing item.");
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                logger.logDebug("Purchase canceled - Response: " + getResponseDesc(responseCode));
                result = new IabResult(IABHELPER_USER_CANCELLED, "User canceled.");
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
            } else {
                logger.logError("Purchase failed. Result code: " + resultCode
                        + ". Response: " + getResponseDesc(responseCode));
                result = new IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "Unknown purchase response.");
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
            }
        }
    };
    boolean mDisposed = false;

    public IAB(IABLogger logger, String marketId, String bindAddress, String mSignatureBase64) {
        this.logger = logger;
        this.marketId = marketId;
        this.bindAddress = bindAddress;
        this.mSignatureBase64 = mSignatureBase64;
    }

    public int getResponseCodeFromBundle(Bundle b) {
        Object o = b.get(RESPONSE_CODE);
        if (o == null) {
            logger.logDebug("Bundle with null response code, assuming OK (known issue)");
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) {
            return ((Integer) o).intValue();
        } else if (o instanceof Long) {
            return (int) ((Long) o).longValue();
        } else {
            logger.logError("Unexpected type for bundle response code.");
            logger.logError(o.getClass().getName());
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    int getResponseCodeFromIntent(Intent i) {
        Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null) {
            logger.logError("Intent with no response code, assuming OK (known issue)");
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) {
            return ((Integer) o).intValue();
        } else if (o instanceof Long) {
            return (int) ((Long) o).longValue();
        } else {
            logger.logError("Unexpected type for intent response code.");
            logger.logError(o.getClass().getName());
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
        }
    }

    protected boolean disposed() {
        return mDisposed;
    }

    public void dispose(Context context) {
        mSetupDone = false;
        mDisposed = true;
    }

    public abstract boolean connect(Context context, OnConnectListener listener);

    public abstract void isBillingSupported(
            int apiVersion,
            String packageName,
            BillingSupportCommunication communication);

    public abstract void launchPurchaseFlow(
            Context mContext, Activity act,
            String sku,
            String itemType,
            IabHelper.OnIabPurchaseFinishedListener listener,
            String extraData);

    public abstract void consume(Context mContext, Purchase itemInfo) throws IabException;

    public void flagStartAsync(String refresh_inventory) {
    }

    public void flagEndAsync() {
    }

    public abstract Bundle getSkuDetails(int billingVersion, String packageName, String itemType,
                                         Bundle querySkus) throws RemoteException;

    public abstract Bundle getPurchases(int billingVersion, String packageName, String itemType,
                                        String continueToken) throws RemoteException;
}
