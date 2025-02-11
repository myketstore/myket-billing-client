package ir.myket.billingclient.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.myket.billingclient.BuildConfig;
import ir.myket.billingclient.R;

public class DialogActivity extends Activity {
    private final static String MYKET_INSTALL_SCHEME = "https";
    private final static String MYKET_INSTALL_HOST = "paygiri.myket.ir";
    private final static String MYKET_INSTALL_PATH = "client-not-installed";
    private final static String MYKET_INSTALL_QUERY = "p";
    private final IABLogger iabLogger = new IABLogger();

    private static @NonNull JSONObject getJsonObject(Payload payload) {
        JSONObject json = new JSONObject();
        try {
            json.put("aal", payload.androidApiVersion);
            json.put("ver", payload.myketSdkVersion);
            json.put("aid", payload.adId);
            json.put("and", payload.androidId);
            json.put("pk", payload.packageName);
            json.put("sid", payload.skuId);
            json.put("mdl", payload.model);
            json.put("mfc", payload.manufacturer);
        } catch (JSONException e) {
            return new JSONObject();
        }
        return json;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        iabLogger.logDebug("Launching install myket activity");
        setRequestedOrientation(
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ?
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View bottomSheetView = inflater.inflate(R.layout.dialog, null);
        dialog.setContentView(bottomSheetView);
        ImageView logoType = bottomSheetView.findViewById(R.id.logo_type);
        logoType.getDrawable().setColorFilter(getResources().getColor(R.color.logo_type_color), PorterDuff.Mode.MULTIPLY);
        dialog.setOnDismissListener(d -> finish());
        bottomSheetView.findViewById(R.id.btnInstall).setOnClickListener(v -> {
            handleMyketInstallIntent();
            dialog.dismiss();
            finish();
        });
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet =
                    bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                int margin = getResources().getDimensionPixelOffset(R.dimen.bottom_dialog_horizontal_margin);
                CoordinatorLayout.LayoutParams layoutParams =
                        (CoordinatorLayout.LayoutParams) bottomSheet.getLayoutParams();
                layoutParams.setMargins(margin, 0, margin, 0);
                bottomSheet.setLayoutParams(layoutParams);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void handleMyketInstallIntent() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        final Payload p = new Payload();
        executor.execute(() -> {
            try {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(getApplicationContext());
                p.adId = adInfo.getId();
            } catch (IOException |
                     GooglePlayServicesNotAvailableException |
                     GooglePlayServicesRepairableException e) {
                p.adId = "";
            }

            handler.post(() -> {
                p.manufacturer = Build.MANUFACTURER;
                p.model = Build.MODEL;
                p.androidApiVersion = Build.VERSION.SDK_INT;
                p.myketSdkVersion = BuildConfig.SDK_VERSION;
                p.androidId = Settings.Secure.ANDROID_ID;
                p.packageName = getApplicationContext().getPackageName();
                Intent actIntent = DialogActivity.this.getIntent();
                if (actIntent != null) {
                    String sku = actIntent.getStringExtra("SKU");
                    if (sku != null) {
                        p.skuId = sku;
                    }
                }
                JSONObject json = getJsonObject(p);
                String metadata = Base64.encode(json.toString().getBytes(StandardCharsets.UTF_8));
                Uri.Builder urlBuilder = new Uri.Builder();
                urlBuilder.scheme(MYKET_INSTALL_SCHEME)
                        .authority(MYKET_INSTALL_HOST)
                        .appendPath(MYKET_INSTALL_PATH)
                        .appendQueryParameter(MYKET_INSTALL_QUERY, metadata);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(urlBuilder.build().toString()));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(getApplicationContext(), R.string.error_browser_not_found, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static class Payload {
        String adId;
        String manufacturer;
        String model;
        int androidApiVersion;
        int myketSdkVersion;
        String androidId;
        String packageName;
        String skuId;
    }
}