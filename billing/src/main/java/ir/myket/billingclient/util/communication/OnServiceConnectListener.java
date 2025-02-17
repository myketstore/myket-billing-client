package ir.myket.billingclient.util.communication;

public interface OnServiceConnectListener {
    void connected();

    void couldNotConnect();
}
