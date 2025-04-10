/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.vending.billing;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import java.util.ArrayList;

public class InAppBillingServiceImpl extends IInAppBillingService.Stub {

    private static final int RESULT_OK = 0;

    @Override
    public int isBillingSupported(int apiVersion, String packageName, String type) throws RemoteException {
        return RESULT_OK;
    }

    @Override
    public Bundle getSkuDetails(int apiVersion, String packageName, String type, Bundle skusBundle) throws RemoteException {
        Bundle response = new Bundle();
        response.putInt("RESPONSE_CODE", RESULT_OK);
        ArrayList<String> detailsList = new ArrayList<>();

        String detail = "{\"productId\":\"example_sku\",\"type\":\"inapp\",\"price\":\"$1.99\",\"title\":\"Example Item\",\"description\":\"An example item description.\"}";
        detailsList.add(detail);

        response.putStringArrayList("DETAILS_LIST", detailsList);
        return response;
    }

    @Override
    public Bundle getBuyIntent(int apiVersion, String packageName, String sku, String type, String developerPayload) throws RemoteException {
        Bundle response = new Bundle();
        response.putInt("RESPONSE_CODE", RESULT_OK);

        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(null, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        response.putParcelable("BUY_INTENT", pendingIntent);

        return response;
    }

    @Override
    public Bundle getPurchases(int apiVersion, String packageName, String type, String continuationToken) throws RemoteException {
        Bundle response = new Bundle();
        response.putInt("RESPONSE_CODE", RESULT_OK);

        ArrayList<String> purchaseItemList = new ArrayList<>();
        ArrayList<String> purchaseDataList = new ArrayList<>();
        ArrayList<String> dataSignatureList = new ArrayList<>();

        purchaseItemList.add("example_sku");
        purchaseDataList.add("{\"orderId\":\"order123\",\"packageName\":\"" + packageName + "\",\"productId\":\"example_sku\",\"purchaseTime\":1620000000000,\"purchaseToken\":\"token123\"}");
        dataSignatureList.add("signed_data_here");

        response.putStringArrayList("INAPP_PURCHASE_ITEM_LIST", purchaseItemList);
        response.putStringArrayList("INAPP_PURCHASE_DATA_LIST", purchaseDataList);
        response.putStringArrayList("INAPP_DATA_SIGNATURE_LIST", dataSignatureList);

        return response;
    }

    @Override
    public int consumePurchase(int apiVersion, String packageName, String purchaseToken) throws RemoteException {
        return RESULT_OK;
    }

    @Override
    public Bundle getBuyIntentV2(int apiVersion, String packageName, String sku, String type, String developerPayload) throws RemoteException {
        Bundle response = new Bundle();
        response.putInt("RESPONSE_CODE", RESULT_OK);

        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(null, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        response.putParcelable("BUY_INTENT", pendingIntent);

        return response;
    }

    @Override
    public Bundle getPurchaseConfig(int apiVersion) throws RemoteException {
        Bundle config = new Bundle();
        config.putBoolean("INTENT_V2_SUPPORT", true);
        return config;
    }

    @Override
    public Bundle acknowledgePurchase(int apiVersion, String packageName, String purchaseToken) throws RemoteException {
        Bundle response = new Bundle();
        response.putInt("RESPONSE_CODE", RESULT_OK);
        return response;
    }

    @Override
    public Bundle getPurchaseHistory(int apiVersion, String packageName, String type, String continuationToken) throws RemoteException {
        Bundle response = new Bundle();
        response.putInt("RESPONSE_CODE", RESULT_OK);

        ArrayList<String> historyList = new ArrayList<>();
        historyList.add("{\"productId\":\"example_sku\",\"purchaseTime\":1610000000000,\"purchaseToken\":\"token_hist_1\"}");

        response.putStringArrayList("INAPP_PURCHASE_DATA_LIST", historyList);
        return response;
    }

    @Override
    public Bundle isFeatureSupported(int apiVersion, String packageName, String feature) throws RemoteException {
        Bundle response = new Bundle();
        response.putInt("RESPONSE_CODE", RESULT_OK);
        response.putBoolean("FEATURE_SUPPORTED", true);
        return response;
    }
} 
