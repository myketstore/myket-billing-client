package ir.myket.billingclient.util.communication;


import ir.myket.billingclient.util.IabResult;

public interface BillingSupportCommunication {
    void onBillingSupportResult(int response);

    void remoteExceptionHappened(IabResult result);
}
