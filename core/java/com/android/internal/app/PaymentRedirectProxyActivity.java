package com.android.internal.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.bluestacks.os.BstHostCallManager;

import android.content.SharedPreferences;

// A16DBG:P2:FW-CORE-APP-8 Google Play IAP redirect proxy (a13)
public class PaymentRedirectProxyActivity extends Activity {
    private static final int REQ_CODE = 100;
    private static final String SHARED_CONFIG_FILE_NAME = "bst_sp_iap_settings";
    private static final String SP_KEY_DONT_SHOW_AGAIN = "sp_dont_show_again";
    private static final String SP_KEY_IAP_CLICK_ACTION_TYPE = "sp_click_action_type";
    private static final String BST_IAP_SETTING_KEY = "bst_iap_setting";

    private Bundle origBundle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        origBundle = getIntent().getExtras();

        SharedPreferences sp = getSharedPreferences(SHARED_CONFIG_FILE_NAME, Context.MODE_PRIVATE);

        Intent chooseIntent = new Intent();
        chooseIntent.setClassName(
                "gg.now.billing.interceptor",
                "gg.now.billing.interceptor.PaymentChooserActivity");
        chooseIntent.putExtra("bst_from_pkg", getPackageName());
        chooseIntent.putExtra("bst_from_app_version", getAppVersionName());
        chooseIntent.putExtra(
                "bst_dont_show_agin", sp.getBoolean(SP_KEY_DONT_SHOW_AGAIN, false));
        chooseIntent.putExtra(
                "bst_click_action_type", sp.getString(SP_KEY_IAP_CLICK_ACTION_TYPE, "PlayStoreIAP"));
        if (origBundle != null) {
            chooseIntent.putExtra(BST_IAP_SETTING_KEY, origBundle.getString(BST_IAP_SETTING_KEY));
        }
        startActivityForResult(chooseIntent, REQ_CODE);
    }

    private String getAppVersionName() {
        String versionName = "";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionName;
    }

    private static void cancelPurchase(final Context context) {
        Intent intent = new Intent("com.android.vending.billing.PURCHASES_UPDATED");
        intent.setPackage(context.getApplicationContext().getPackageName());
        intent.putExtra("RESPONSE_CODE", 1);
        intent.putExtra("DEBUG_MESSAGE", "Billing dialog closed.");
        context.sendBroadcast(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE && resultCode == RESULT_OK) {
            boolean dontShowAgain = data.getBooleanExtra("bst_dont_show_agin", false);
            String actionType = data.getStringExtra("action_type");
            String actionData = data.getStringExtra("action_data");
            if (dontShowAgain) {
                SharedPreferences.Editor editor =
                        getSharedPreferences(SHARED_CONFIG_FILE_NAME, Context.MODE_PRIVATE).edit();
                editor.putBoolean(SP_KEY_DONT_SHOW_AGAIN, true);
                editor.putString(SP_KEY_IAP_CLICK_ACTION_TYPE, actionType);
                editor.apply();
            }
            if (TextUtils.equals(actionType, "PlayStoreIAP")) {
                Intent launchIntent = new Intent();
                launchIntent.setClassName(this, "com.android.billingclient.api.ProxyBillingActivity");
                launchIntent.putExtras(origBundle);
                launchIntent.removeExtra(BST_IAP_SETTING_KEY);
                launchIntent.putExtra("bst_hooked", true);
                startActivity(launchIntent);
                finish();
                return;
            } else if (TextUtils.equals(actionType, "OpenGuestUrl")) {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setData(Uri.parse(actionData));
                startActivity(viewIntent);
                cancelPurchase(this);
                finish();
                return;
            } else if (TextUtils.equals(actionType, "ApplicationBrowser")
                    || TextUtils.equals(actionType, "UserBrowser")) {
                BstHostCallManager bstHostCallManagerService =
                        (BstHostCallManager) getSystemService(Context.BST_HOST_CALL);
                if (bstHostCallManagerService != null) {
                    bstHostCallManagerService.handleCustomIap(actionType, actionData);
                }
                cancelPurchase(this);
                finish();
                return;
            }
        }
        cancelPurchase(this);
        finish();
    }
}
