package ir.myket.billingclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void useAppContext() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("ir.myket.billingclient", appContext.getPackageName());
    }

    @Test
    public void appVersionCodeIsPositive() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = appContext.getPackageManager();
        PackageInfo info = pm.getPackageInfo(appContext.getPackageName(), 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertTrue("Version code must be positive", info.getLongVersionCode() > 0);
        } else {
            assertTrue("Version code must be positive", info.versionCode > 0);
        }
    }

    @Test
    public void appVersionNameIsNotEmpty() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = appContext.getPackageManager();
        PackageInfo info = pm.getPackageInfo(appContext.getPackageName(), 0);

        assertNotNull("Version name should not be null", info.versionName);
        assertTrue("Version name should not be empty", info.versionName.trim().length() > 0);
    }

    @Test
    public void appHasPackageManager() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = appContext.getPackageManager();
        assertNotNull("Package manager should be available", pm);
    }

    @Test
    public void sdkVersionIsValid() {
        int sdkVersion = Build.VERSION.SDK_INT;
        assertTrue("SDK version must be greater than zero", sdkVersion > 0);
    }
}
