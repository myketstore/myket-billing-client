package ir.myket.billingclient.util;


import static ir.myket.billingclient.IabHelper.BILLING_RESPONSE_RESULT_OK;
import static ir.myket.billingclient.IabHelper.IABHELPER_MISSING_TOKEN;
import static ir.myket.billingclient.IabHelper.IABHELPER_REMOTE_EXCEPTION;
import static ir.myket.billingclient.IabHelper.IABHELPER_SEND_INTENT_FAILED;
import static ir.myket.billingclient.IabHelper.IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE;
import static ir.myket.billingclient.IabHelper.ITEM_TYPE_INAPP;
import static ir.myket.billingclient.IabHelper.ITEM_TYPE_SUBS;
import static ir.myket.billingclient.IabHelper.RESPONSE_BUY_INTENT;
import static ir.myket.billingclient.IabHelper.getResponseDesc;
import static ir.myket.billingclient.util.ProxyBillingActivity.BILLING_RECEIVER_KEY;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;

import java.util.List;

import ir.myket.billingclient.IabHelper;
import ir.myket.billingclient.util.communication.BillingSupportCommunication;
import ir.myket.billingclient.util.communication.OnServiceConnectListener;

public class ServiceIAB extends IAB {

    // Keys for the response from getPurchaseConfig
    private static final String INTENT_V2_SUPPORT = "INTENT_V2_SUPPORT";
    // Connection to the service
    private IInAppBillingService mService;
    private ServiceConnection mServiceConn;
    // Is an asynchronous operation in progress?
    // (only one at a time can be in progress)
    private boolean mAsyncInProgress = false;
    // (for logging/debugging)
    // if mAsyncInProgress == true, what asynchronous operation is in progress?
    private String mAsyncOperation = "";

    public ServiceIAB(IABLogger logger, String packageName, String bindAddress, String mSignatureBase64) {
        super(logger, packageName, bindAddress, mSignatureBase64);
    }

    public void connect(Context context, final OnServiceConnectListener listener) {
        logger.logDebug("Starting in-app billing setup.");
        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(final ComponentName name) {
                logger.logDebug("Billing service disconnected.");
                mService = null;
            }

            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                logger.logDebug("Billing service connected.");
                if (disposed()) {
                    return;
                }
                mSetupDone = true;
                mService = IInAppBillingService.Stub.asInterface(service);
                listener.connected();
            }
        };

        Intent serviceIntent = new Intent(bindAddress);
        serviceIntent.setPackage(marketId);

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> intentServices;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intentServices = pm.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intentServices = pm.queryIntentServices(serviceIntent, PackageManager.MATCH_DISABLED_COMPONENTS);
            } else {
                intentServices = pm.queryIntentServices(serviceIntent, 0);
            }
        }

        if (!intentServices.isEmpty()) {
            try {
                boolean result = context.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
                if (!result) {
                    listener.couldNotConnect();
                }
            } catch (Exception e) {
                logger.logDebug("Billing service can't connect. result = false");
                listener.couldNotConnect();
            }
        } else {
            logger.logDebug("Billing service can't connect. result = false");
            listener.couldNotConnect();
        }
    }

    @Override
    public void isBillingSupported(int apiVersion, String packageName,
                                   BillingSupportCommunication communication) {
        try {
            logger.logDebug("Checking for in-app billing 3 support.");

            // check for in-app billing v3 support
            int response = mService.isBillingSupported(apiVersion, packageName, ITEM_TYPE_INAPP);
            if (response != BILLING_RESPONSE_RESULT_OK) {
                mSubscriptionsSupported = false;
                communication.onBillingSupportResult(response);
                return;
            }
            logger.logDebug("In-app billing version 3 supported for " + packageName);

            // check for v3 subscriptions support
            response = mService.isBillingSupported(apiVersion, packageName, ITEM_TYPE_SUBS);
            if (response == BILLING_RESPONSE_RESULT_OK) {
                if ("ir.mservices.market".equalsIgnoreCase(marketId)) {
                    logger.logDebug("Myket not supported subscription type");
                    mSubscriptionsSupported = false;
                } else {
                    logger.logDebug("Subscriptions AVAILABLE.");
                    mSubscriptionsSupported = true;
                }
            } else {
                logger.logDebug("Subscriptions NOT AVAILABLE. Response: " + response);
            }

            communication.onBillingSupportResult(BILLING_RESPONSE_RESULT_OK);

        } catch (RemoteException e) {
            communication.remoteExceptionHappened(new IabResult(IABHELPER_REMOTE_EXCEPTION,
                    "RemoteException while setting up in-app billing."));
            e.printStackTrace();
        }

    }

    @Override
    public void launchPurchaseFlow(Context mContext, Activity act, String sku, String itemType,
                                   IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {

        if (isAsyncOperationInProgress()) {
            logger.logWarn("Can't start async operation launchPurchaseFlow because another async operation is in progress.");
            listener.onIabPurchaseFinished(new IabResult(IabHelper.IABHELPER_ERROR_BASE, "Can't start async operation launchPurchaseFlow because another async operation is in progress."), null);
            return;
        }

        flagStartAsync("launchPurchaseFlow");
        IabResult result;
        if (itemType.equals(ITEM_TYPE_SUBS) && !mSubscriptionsSupported) {
            IabResult r = new IabResult(IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE,
                    "Subscriptions are not available.");
            flagEndAsync();
            if (listener != null) {
                listener.onIabPurchaseFinished(r, null);
            }
            return;
        }

        try {
            logger.logDebug("Constructing buy intent for " + sku + ", item type: " + itemType);

            Bundle configBundle = mService.getPurchaseConfig(apiVersion);
            if (configBundle != null && configBundle.getBoolean(INTENT_V2_SUPPORT)) {
                logger.logDebug("launchBuyIntentV2 for " + sku + ", item type: " + itemType);
                launchBuyIntentV2(mContext, act, sku, itemType, listener, extraData);
            } else {
                logger.logDebug("launchBuyIntent for " + sku + ", item type: " + itemType);
                launchBuyIntent(mContext, act, sku, itemType, listener, extraData);
            }
        } catch (IntentSender.SendIntentException e) {
            logger.logError("SendIntentException while launching purchase flow for sku " + sku);
            e.printStackTrace();
            flagEndAsync();

            result = new IabResult(IABHELPER_SEND_INTENT_FAILED, "Failed to send intent.");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
        } catch (RemoteException e) {
            logger.logError("RemoteException while launching purchase flow for sku " + sku);
            e.printStackTrace();
            flagEndAsync();

            result = new IabResult(IABHELPER_REMOTE_EXCEPTION,
                    "Remote exception while starting purchase flow");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
        }
    }


    private void launchBuyIntentV2(
            Context context,
            Activity act,
            String sku,
            String itemType,
            IabHelper.OnIabPurchaseFinishedListener listener,
            String extraData
    ) throws RemoteException {
        String packageName = context.getPackageName();

        Bundle buyIntentBundle = mService.getBuyIntentV2(apiVersion, packageName, sku, itemType, extraData);
        int response = getResponseCodeFromBundle(buyIntentBundle);
        if (response != BILLING_RESPONSE_RESULT_OK) {
            logger.logError("Unable to buy item, Error response: " + getResponseDesc(response));
            flagEndAsync();
            IabResult result = new IabResult(response, "Unable to buy item");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
            return;
        }

        Intent purchaseIntent = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
        logger.logDebug("Launching buy intent for " + sku);
        mPurchaseListener = listener;
        mPurchasingItemType = itemType;

        Intent intent = new Intent(act, ProxyBillingActivity.class);
        intent.putExtra(RESPONSE_BUY_INTENT, purchaseIntent);
        intent.putExtra(BILLING_RECEIVER_KEY, purchaseResultReceiver);
        act.startActivity(intent);
    }

    private void launchBuyIntent(
            Context context,
            Activity act,
            String sku,
            String itemType,
            IabHelper.OnIabPurchaseFinishedListener listener,
            String extraData
    ) throws RemoteException, IntentSender.SendIntentException {

        String packageName = context.getPackageName();

        Bundle buyIntentBundle = mService.getBuyIntent(apiVersion, packageName, sku, itemType, extraData);
        int response = getResponseCodeFromBundle(buyIntentBundle);
        if (response != BILLING_RESPONSE_RESULT_OK) {
            logger.logError("Unable to buy item, Error response: " + getResponseDesc(response));
            flagEndAsync();
            IabResult result = new IabResult(response, "Unable to buy item");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
            return;
        }


        PendingIntent pendingIntent = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
        logger.logDebug("Launching buy intent for " + sku);
        mPurchaseListener = listener;
        mPurchasingItemType = itemType;

        Intent intent = new Intent(act, ProxyBillingActivity.class);
        intent.putExtra(RESPONSE_BUY_INTENT, pendingIntent);
        intent.putExtra(BILLING_RECEIVER_KEY, purchaseResultReceiver);
        act.startActivity(intent);
    }

    @Override
    public void consume(Context context, Purchase itemInfo) throws IabException {
        try {
            String token = itemInfo.getToken();
            String sku = itemInfo.getSku();
            if (token == null || token.equals("")) {
                logger.logError("Can't consume " + sku + ". No token.");
                throw new IabException(IABHELPER_MISSING_TOKEN, "PurchaseInfo is missing token for sku: "
                        + sku + " " + itemInfo);
            }

            logger.logDebug("Consuming sku: " + sku + ", token: " + token);
            int response = mService.consumePurchase(apiVersion, context.getPackageName(), token);
            if (response == BILLING_RESPONSE_RESULT_OK) {
                logger.logDebug("Successfully consumed sku: " + sku);
            } else {
                logger.logDebug("Error consuming consuming sku " + sku + ". " + getResponseDesc(response));
                throw new IabException(response, "Error consuming sku " + sku);
            }
        } catch (RemoteException e) {
            throw new IabException(IABHELPER_REMOTE_EXCEPTION,
                    "Remote exception while consuming. PurchaseInfo: " + itemInfo, e);
        }
    }

    @Override
    public Bundle getPurchases(int billingVersion, String packageName, String itemType,
                               String continueToken) throws RemoteException {
        return mService.getPurchases(billingVersion, packageName, itemType, continueToken);
    }

    @Override
    public Bundle getSkuDetails(int billingVersion, String packageName, String itemType,
                                Bundle querySkus) throws RemoteException {
        return mService.getSkuDetails(apiVersion, packageName,
                itemType, querySkus);
    }

    @Override
    public void flagStartAsync(String operation) {
        if (mAsyncInProgress) throw new IllegalStateException("Can't start async operation (" +
                operation + ") because another async operation(" + mAsyncOperation + ") is in progress.");
        mAsyncOperation = operation;
        mAsyncInProgress = true;
        logger.logDebug("Starting async operation: " + operation);
    }

    @Override
    public void flagEndAsync() {
        logger.logDebug("Ending async operation: " + mAsyncOperation);
        mAsyncOperation = "";
        mAsyncInProgress = false;
    }

    @Override
    public boolean isAsyncOperationInProgress() {
        return mAsyncInProgress;
    }

    @Override
    public void dispose(Context context) {
        logger.logDebug("Unbinding from service.");
        if (context != null && mService != null) {
            context.unbindService(mServiceConn);
        }

        mPurchaseListener = null;
        mServiceConn = null;
        mService = null;
        super.dispose(context);
    }
}
