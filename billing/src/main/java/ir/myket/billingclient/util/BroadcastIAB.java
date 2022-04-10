package ir.myket.billingclient.util;

import static ir.myket.billingclient.IabHelper.BILLING_RESPONSE_RESULT_OK;
import static ir.myket.billingclient.IabHelper.IABHELPER_ERROR_BASE;
import static ir.myket.billingclient.IabHelper.IABHELPER_MISSING_TOKEN;
import static ir.myket.billingclient.IabHelper.RESPONSE_BUY_INTENT;
import static ir.myket.billingclient.IabHelper.getResponseDesc;
import static ir.myket.billingclient.util.ProxyBillingActivity.BILLING_RECEIVER_KEY;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import ir.myket.billingclient.IabHelper;
import ir.myket.billingclient.util.communication.BillingSupportCommunication;
import ir.myket.billingclient.util.communication.OnConnectListener;

public class BroadcastIAB extends IAB {

    public static final String PACKAGE_NAME_KEY = "packageName";
    public static final String API_VERSION_KEY = "apiVersion";
    public static final String SECURE_KEY = "secure";

    public static final String SUBSCRIPTION_SUPPORT_KEY = "subscriptionSupport";
    public static final String SKU_KEY = "sku";
    public static final String ITEM_TYPE_KEY = "itemType";
    public static final String DEVELOPER_PAYLOAD_KEY = "developerPayload";
    public static final String TOKEN_KEY = "token";
    public static final String ping = ".ping";
    public static final String billingSupport = ".billingSupport";
    public static final String purchaseAction = ".purchase";
    public static final String skuDetailAction = ".skuDetail";
    public static final String getPurchaseAction = ".getPurchase";
    public static final String consumeAction = ".consume";
    private static final String marketPostAction = ".iab";
    public static final String receivePingAction = ping + marketPostAction;
    public static final String receiveBillingSupport = billingSupport + marketPostAction;
    public static final String receivePurchaseAction = purchaseAction + marketPostAction;
    public static final String receiveSkuDetailAction = skuDetailAction + marketPostAction;
    public static final String receiveGetPurchaseAction = getPurchaseAction + marketPostAction;
    public static final String receiveConsumeAction = consumeAction + marketPostAction;
    private static final String MYKET_MARKET_ID = "3c97c0b07a6f4a0d1ae1cf8816396560";
    private static final String BAZAAR_MARKET_ID = "6c02ea10518a07556a7b44e930478cb9";
    private static final int MYKET_VERSION_CODE_WITH_BROADCAST = 900;
    private static final int BAZAAR_VERSION_CODE_WITH_BROADCAST = 801301;
    private final Context context;
    private final String signatureBase64;

    private AbortableCountDownLatch consumePurchaseLatch;
    private int consumePurchaseResponse;

    private AbortableCountDownLatch getSkuDetailLatch;
    private Bundle skuDetailBundle;

    private AbortableCountDownLatch getPurchaseLatch;
    private Bundle getPurchaseBundle;

    private IABReceiverCommunicator iabReceiver = null;
    private WeakReference<OnConnectListener> connectListenerWeakReference;
    private WeakReference<BillingSupportCommunication> billingSupportWeakReference;
    private WeakReference<Activity> launchPurchaseActivityWeakReference;

    public BroadcastIAB(Context context, IABLogger logger, String marketId, String bindAddress,
                        String mSignatureBase64) {
        super(logger, marketId, bindAddress, mSignatureBase64);
        this.context = context;
        this.signatureBase64 = mSignatureBase64 != null ? mSignatureBase64 : "secureBroadcastKey";
    }

    @Override
    public boolean connect(Context context, OnConnectListener listener) {

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(marketId, 0);
            int versionCode;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                versionCode = (int) pInfo.getLongVersionCode();
            } else {
                versionCode = pInfo.versionCode;
            }

            if (checkMarketHasBroadCast(versionCode)) {
                createIABReceiver();
                registerBroadcast();
                trySendPingToMarket();
                connectListenerWeakReference = new WeakReference<>(listener);
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return false;
    }

    private boolean checkMarketHasBroadCast(int versionCode) {
        String marketMD5 = getStringMD5(marketId);
        if (TextUtils.isEmpty(marketMD5)) {
            return false;
        } else {
            if (marketMD5.equals(MYKET_MARKET_ID)) {
                return versionCode > MYKET_VERSION_CODE_WITH_BROADCAST;
            } else if (marketMD5.equals(BAZAAR_MARKET_ID)) {
                return versionCode > BAZAAR_VERSION_CODE_WITH_BROADCAST;
            }
        }
        return false;
    }

    public String getStringMD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuffer sb = new StringBuffer();
            for (byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }

    private void createIABReceiver() {
        iabReceiver = intent -> {
            logger.logDebug("new message received in broadcast");
            String intentAction = intent.getAction();
            if (intentAction == null) {
                logger.logError("action is null");
                return;
            }

            if (!signatureBase64.equals(intent.getStringExtra(SECURE_KEY))) {
                logger.logError("broadcastSecure key is not valid");
                return;
            }

            if (disposed()) {
                return;
            }

            String action = intentAction.replace(marketId, "");
            switch (action) {
                case receivePingAction:
                    OnConnectListener listener = safeGetFromWeakReference(connectListenerWeakReference);
                    mSetupDone = true;
                    if (listener != null) {
                        listener.connected();
                    }
                    break;
                case receivePurchaseAction:
                    handleLaunchPurchaseResponse(intent.getExtras());
                    break;

                case receiveBillingSupport:
                    logger.logDebug("billingSupport message received in broadcast");
                    handleBillingSupport(intent.getExtras());
                    break;

                case receiveConsumeAction:
                    consumePurchaseResponse = getResponseCodeFromIntent(intent);
                    if (consumePurchaseLatch != null) {
                        consumePurchaseLatch.countDown();
                    }
                    break;

                case receiveSkuDetailAction:
                    skuDetailBundle = intent.getExtras();
                    if (getSkuDetailLatch != null) {
                        getSkuDetailLatch.countDown();
                    }
                    break;
                case receiveGetPurchaseAction:
                    getPurchaseBundle = intent.getExtras();
                    if (getPurchaseLatch != null) {
                        getPurchaseLatch.countDown();
                    }
                    break;
            }
        };
    }

    private void handleLaunchPurchaseResponse(Bundle extras) {
        int response = getResponseCodeFromBundle(extras);
        if (response != BILLING_RESPONSE_RESULT_OK) {
            logger.logError("Unable to buy item, Error response: " + getResponseDesc(response));
            flagEndAsync();
            IabResult result = new IabResult(response, "Unable to buy item");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
            return;
        }

        Intent purchaseIntent = extras.getParcelable(RESPONSE_BUY_INTENT);
        logger.logDebug("Launching buy intent");

        Activity activity = safeGetFromWeakReference(launchPurchaseActivityWeakReference);
        if (activity == null) {
            return;
        }

        // Launching an invisible activity that will handle the purchase result
        Intent intent = new Intent(activity, ProxyBillingActivity.class);
        intent.putExtra(RESPONSE_BUY_INTENT, purchaseIntent);
        intent.putExtra(BILLING_RECEIVER_KEY, purchaseResultReceiver);
        activity.startActivity(intent);
    }

    private void handleBillingSupport(Bundle bundle) {

        mSubscriptionsSupported = bundle.getBoolean(SUBSCRIPTION_SUPPORT_KEY);
        BillingSupportCommunication billingListener = safeGetFromWeakReference(billingSupportWeakReference);
        if (billingListener != null) {
            billingListener.onBillingSupportResult(getResponseCodeFromBundle(bundle));
        }
    }

    private <T> T safeGetFromWeakReference(WeakReference<T> onConnectListenerWeakReference) {
        if (onConnectListenerWeakReference == null) {
            return null;
        }
        return onConnectListenerWeakReference.get();
    }

    private void registerBroadcast() {
        IABReceiver.addObserver(iabReceiver);
    }

    private void trySendPingToMarket() {
        Intent intent = getNewIntentForBroadcast();
        intent.setAction(getAction(ping));
        context.sendBroadcast(intent);
    }

    private Intent getNewIntentForBroadcast() {
        Intent intent = new Intent();
        String marketPackageName = marketId;
        intent.setPackage(marketPackageName);
        Bundle bundle = new Bundle();
        bundle.putString(PACKAGE_NAME_KEY, context.getPackageName());
        bundle.putString(SECURE_KEY, signatureBase64);
        intent.putExtras(bundle);
        return intent;
    }

    @Override
    public void isBillingSupported(int apiVersion, String packageName,
                                   BillingSupportCommunication communication) {
        billingSupportWeakReference = new WeakReference<>(communication);

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(getAction(billingSupport));
        intent.putExtra(PACKAGE_NAME_KEY, packageName);
        intent.putExtra(API_VERSION_KEY, apiVersion);
        context.sendBroadcast(intent);
    }

    @Override
    public void launchPurchaseFlow(Context mContext, Activity act, String sku, String itemType,
                                   IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        launchPurchaseActivityWeakReference = new WeakReference<>(act);

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(getAction(purchaseAction));
        intent.putExtra(SKU_KEY, sku);
        intent.putExtra(ITEM_TYPE_KEY, itemType);
        intent.putExtra(API_VERSION_KEY, apiVersion);
        intent.putExtra(DEVELOPER_PAYLOAD_KEY, extraData);
        context.sendBroadcast(intent);

        mPurchaseListener = listener;
        mPurchasingItemType = itemType;
    }

    @Override
    public void consume(Context mContext, Purchase itemInfo) throws IabException {
        String token = itemInfo.getToken();
        String sku = itemInfo.getSku();
        if (token == null || token.equals("")) {
            logger.logError("Can't consume " + sku + ". No token.");
            throw new IabException(IABHELPER_MISSING_TOKEN, "PurchaseInfo is missing token for sku: "
                    + sku + " " + itemInfo);
        }

        logger.logDebug("Consuming sku: " + sku + ", token: " + token);

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(getAction(consumeAction));
        intent.putExtra(TOKEN_KEY, token);
        intent.putExtra(API_VERSION_KEY, apiVersion);
        mContext.sendBroadcast(intent);

        consumePurchaseLatch = new AbortableCountDownLatch(1);


        try {
            consumePurchaseLatch.await(60, TimeUnit.SECONDS);
            if (consumePurchaseResponse == BILLING_RESPONSE_RESULT_OK) {
                logger.logDebug("Successfully consumed sku: " + sku);
            } else {
                logger.logDebug("Error consuming consuming sku " + sku + ". " +
                        getResponseDesc(consumePurchaseResponse));
                throw new IabException(consumePurchaseResponse, "Error consuming sku " + sku);
            }
        } catch (InterruptedException e) {
            throw new IabException(IABHELPER_ERROR_BASE, "Error consuming sku " + sku);
        }
    }

    @Override
    public Bundle getSkuDetails(int billingVersion, String packageName, String itemType,
                                Bundle querySkus) throws RemoteException {

        skuDetailBundle = null;

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(getAction(skuDetailAction));
        intent.putExtra(ITEM_TYPE_KEY, itemType);
        intent.putExtra(PACKAGE_NAME_KEY, packageName);
        intent.putExtra(API_VERSION_KEY, billingVersion);
        intent.putExtras(querySkus);
        context.sendBroadcast(intent);

        getSkuDetailLatch = new AbortableCountDownLatch(1);
        try {
            getSkuDetailLatch.await();
            return skuDetailBundle;

        } catch (InterruptedException e) {
            logger.logWarn("error happened while getting sku detail for " + packageName);
        }

        return new Bundle();
    }

    @Override
    public Bundle getPurchases(int billingVersion, String packageName, String itemType,
                               String continueToken) {

        getPurchaseBundle = null;

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(getAction(getPurchaseAction));
        intent.putExtra(ITEM_TYPE_KEY, itemType);
        intent.putExtra(PACKAGE_NAME_KEY, packageName);
        intent.putExtra(API_VERSION_KEY, billingVersion);
        intent.putExtra(TOKEN_KEY, continueToken);
        context.sendBroadcast(intent);

        getPurchaseLatch = new AbortableCountDownLatch(1);
        try {
            getPurchaseLatch.await();
            return getPurchaseBundle;

        } catch (InterruptedException e) {
            logger.logWarn("error happened while getting sku detail for " + packageName);
        }

        return new Bundle();
    }

    @Override
    public void dispose(Context context) {
        super.dispose(context);
        if (iabReceiver != null) {
            IABReceiver.removeObserver(iabReceiver);
        }
        if (consumePurchaseLatch != null) {
            consumePurchaseLatch.abort();
        }

        if (getSkuDetailLatch != null) {
            getSkuDetailLatch.abort();
        }

        if (getPurchaseLatch != null) {
            getPurchaseLatch.abort();
        }
        iabReceiver = null;
    }

    private String getAction(String action) {
        return marketId + action;
    }
}
