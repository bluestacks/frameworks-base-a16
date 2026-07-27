package com.bluestacks.server;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.StrictMode;
import android.os.SystemProperties;
import android.util.BstUtils;
import android.util.Log;
import android.util.Slog;

import com.bluestacks.os.IBstUtilsService;
import com.android.server.wm.WindowManagerService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;
import java.util.Map;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;

import com.bluestacks.os.BstUtilsManager;

/**
 * General BlueStacks Utilities.
 * @hide 
 */
public class BstUtilsService extends IBstUtilsService.Stub {
    private final String TAG = "BstUtilsService";
    private final boolean BST_DBG = SystemProperties.getInt("bst.debug.bstutils", 0) > 0 ? true : false;
    private static Context mContext = null;
    private String mHttpsConnResponse = "";
    private WindowManagerService mWindowManagerService;

    private final int GET_APK_DOWNLOAD_SOURCE = 0;
    private final int GET_SESSION_TOKEN = 1;

    /**
     * @hide
     */
    public BstUtilsService(Context context)
    {
        super();
        Slog.d(TAG, "Setting bstutils context here, context = " + context);
        mContext = context;
    }

    public void setWindowManager(WindowManagerService wms) {
        if (BST_DBG) Slog.d(TAG, "setWindowManager");
        mWindowManagerService = wms; 
    }

    public void setBstProposedRotation(int rotation) {
        if (BST_DBG) Slog.d(TAG, "setBstProposedRotation rotation = " + rotation);
        mWindowManagerService.setBstProposedRotation(rotation);
    }

    /**
     * Calling BstCommandProcessor to alter service/component state.
     * We can't perform this operation directly from here because of permission restrictions.
     * Also, we can't call BstCommandProcessor directly from its caller as caller don't
     * have any valid context associated with it.
     *
     * @hide
     */
    public void setServiceComponentState(String service, boolean enabled) {
        if (mContext == null)
        {
            Slog.e(TAG, "in setServiceComponentState, context is not set yet, returning now");
            return;
        }

        // Setting service component state only when the caller is a system app.
        // Returning of the call is made by any third-party app.
        if (!allowAccess(Binder.getCallingUid())) {
            Slog.d(TAG, "Call to this function is not allowed so returning");
            return;
        }
        Intent intent = new Intent();
        ComponentName cn = new ComponentName("com.bluestacks.BstCommandProcessor",
                "com.bluestacks.BstCommandProcessor.BstCommandProcessorService");
        intent.setAction("setServiceComponentState");
        intent.setComponent(cn);
        intent.putExtra("service", service);
        intent.putExtra("state", enabled);
        Slog.d(TAG, "Set state of 3rd party service: " + service + " to " + enabled);
        mContext.startServiceAsUser(intent, Process.myUserHandle());
    }

    private boolean allowAccess(int uid) {
        boolean systemApp = false;
        String callingApp = "";
        try {
            callingApp = mContext.getPackageManager().getNameForUid(uid);
            if (uid > 1000) {
                PackageInfo pkgInfo = mContext.getPackageManager().getPackageInfo(callingApp, PackageManager.GET_CONFIGURATIONS);
                systemApp = pkgInfo.applicationInfo.isSystemApp() || pkgInfo.applicationInfo.isUpdatedSystemApp();
            } else {
                systemApp = true;
            }
        } catch (Exception ex) {
            Log.d(TAG, "Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        if (systemApp) {
            Slog.d(TAG, "granting access to the calling uid " + callingApp);
            return true;
        }
        return false;
    }

    /**
     * Our hack to set System Properties ;)
     * @hide
     */
    public boolean setProperty(String key,String value)
    {
        int uid = Binder.getCallingUid();
        boolean allowed = allowAccess(uid);
        // Setting property only if the caller is a systemApp or the property to be set is "bst.config.referrerpackage"
        if (allowed || "bst.config.referrerpackage".equals(key)) {
            Slog.d(TAG,"setting system property key : " + key + "  value : " + value);
            android.os.SystemProperties.set(key, value);
            return true;
        }
        return false;
    }

    /**
     * Get Process name from pid
     * As most of the time process name is same as that of packageName, this is quite useful information.
     * @hide
     */
    public String getAppNameFromPid(int pid)
    {
        //Calling function defined in BstUtils.java
        return BstUtils.getAppNameFromPid(pid);
    }

    /**
     * Checks if ime needs to be shown
     * @hide
     */
    public boolean isBstSoftKeyboardEnabled()
    {
        return android.provider.Settings.Secure.getInt(mContext.getContentResolver(),
                android.provider.Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, 0) == 1;
    }

    /**
     * Update showIme Value
     * @hide
     */
    public void setBstSoftKeyboardStatus(boolean show)
    {
        android.provider.Settings.Secure.putInt(mContext.getContentResolver(),
                android.provider.Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, show ? 1 : 0);
    }

    /**
     * Gets the Apk Download/Update source.
     * Returns Cloud response as String (containing Apk download/Update source).
     * @hide
     */
    public String getApkDownloadSource(String packageName,String app_install_type)
    {
        mHttpsConnResponse = "";
        final CountDownLatch latch = new CountDownLatch(1);
        Thread apkDownloadHandlerThread = new Thread(new HttpsConnHandlerThread(packageName, app_install_type, latch, GET_APK_DOWNLOAD_SOURCE));
        apkDownloadHandlerThread.start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return mHttpsConnResponse ;
    }

    /**
     * Gets the Session Token
     * Returns session token as String as callback Response
     * on ResultReceiver object.
     * @hide
     */
    public void getSessionToken(String packageName, ResultReceiver callback)
    {
        String sessionToken = "";
        Thread getSessionTokenHandlerThread = new Thread(new HttpsConnHandlerThread(packageName, GET_SESSION_TOKEN, callback));
        getSessionTokenHandlerThread.start();
    }

    /**
     * Launches the Apps Store, based on the cloud response.
     * @hide
     */
    public void launchAppStore(String packageName, String apkUpdateSource)
    {
        Intent intent = new Intent();
        ComponentName cn = new ComponentName("com.bluestacks.BstCommandProcessor",
                "com.bluestacks.BstCommandProcessor.BstCommandProcessorService");
        intent.setComponent(cn);
        intent.setAction("launchAppStore");
        intent.putExtra("packageName", packageName);
        intent.putExtra("response", apkUpdateSource);
        mContext.startService(intent);
    }

    private class HttpsConnHandlerThread implements Runnable {
        private String mPkgName;
        private String mAppInstallType;
        private CountDownLatch mLatch = null;
        private int mConnType = -1;
        private ResultReceiver mCallback = null;
        private int mStatus = BstUtilsManager.STATUS_OK;

        public HttpsConnHandlerThread(String pkgName , int connType, ResultReceiver callback) {
            mPkgName = pkgName;
            mConnType = connType;
            mCallback = callback;
        }

        public HttpsConnHandlerThread(String pkgName,String app_install_type,CountDownLatch latch, int connType) {
            mPkgName = pkgName;
            mAppInstallType = app_install_type;
            mLatch = latch ;
            mConnType = connType;
        }

        @Override
        public void run() {
            HttpURLConnection conn = null;
            String cloudUrl = "";
            String host = SystemProperties.get("bst.bluestacks_cloud_url", "https://cloud.bluestacks.com");
            ContentValues values = new ContentValues();
            switch (mConnType) {
                case GET_APK_DOWNLOAD_SOURCE :
                    cloudUrl = host + "/app_player/get_apk_download_source";
                    values.put("app_install_type", mAppInstallType);
                    values.put("app_versionCode", getPackageInfo(mPkgName, "versioncode"));
                    values.put("app_versionName", getPackageInfo(mPkgName, "versionname"));
                    break;
                case GET_SESSION_TOKEN :
                    cloudUrl = host + "/app_player/get_session_token";
                    break;
                default :
            }
            try {
                String caCode = SystemProperties.get("bst.device_country_code");
                String pcode = SystemProperties.get("bst.device_profile_code");
                String caSelector = SystemProperties.get("bst.device_carrier_code");
                String abiList = SystemProperties.get("bst.abi_list");
                String googleAdId = SystemProperties.get("bst.android_google_ad_id", "");

                values.put("device_country_code", caCode);
                values.put("device_profile_code", pcode);
                values.put("device_carrier_code", caSelector);
                values.put("supported_abis", abiList);
                values.put("app_package", mPkgName);
                values.put("android_google_ad_id", googleAdId);
                values.put("android_id", SystemProperties.get("bst.android_id", ""));
                values.put("android_image", SystemProperties.get("bst.android_image", ""));
                values.put("bluestacks_account_id", SystemProperties.get("bst.bluestacks_account_id", ""));
                values.put("campaign_hash", SystemProperties.get("bst.campaign_hash", ""));
                values.put("country", SystemProperties.get("bst.country", ""));
                values.put("guid", SystemProperties.get("bst.guid", ""));
                values.put("hypervisor", SystemProperties.get("bst.status.hypervisor", ""));
                values.put("install_id", SystemProperties.get("bst.install_id", ""));
                values.put("instance", SystemProperties.get("bst.instance", ""));
                values.put("locale", SystemProperties.get("bst.locale", ""));
                values.put("machine_id", SystemProperties.get("bst.machine_id", ""));
                values.put("oem", SystemProperties.get("bst.oem", ""));
                values.put("player_version", SystemProperties.get("bst.version", ""));
                values.put("session_id", SystemProperties.get("bst.status.session_id", ""));
                values.put("version_machine_id", SystemProperties.get("bst.version_machine_id", ""));

                URL url = new URL(cloudUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(getQuery(values));
                writer.flush();
                writer.close();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) { //success
                    if (BST_DBG) Log.d(TAG, "HTTP Connection established with " + cloudUrl);
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String inputLine;
                        StringBuffer result = new StringBuffer();

                        while ((inputLine = in.readLine()) != null) {
                            result.append(inputLine);
                        }
                        mHttpsConnResponse = result.toString();
                    } catch (IOException ioe) {
                        mStatus = BstUtilsManager.HTTPS_RESPONSE_READ_ERROR;
                        ioe.printStackTrace();
                    }
                } else {
                    Log.e(TAG, "Failed to establish connection :" + responseCode + ", at " + cloudUrl);
                    mStatus = BstUtilsManager.HTTP_CONNECTION_ERROR;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while establishing HttpsConnection : "+ e.getMessage());
                mStatus = BstUtilsManager.HTTP_CONNECTION_ERROR;
                if (BST_DBG) e.printStackTrace();
            } finally {
                conn.disconnect();
            }
            processResult();
        }
        private void processResult() {
            switch (mConnType) {
                case GET_APK_DOWNLOAD_SOURCE :
                    mLatch.countDown();
                    break;

                case GET_SESSION_TOKEN :
                    String sessionToken = "";
                    if (mHttpsConnResponse != null && mHttpsConnResponse.trim().length() > 0) {
                        try {
                            JSONObject jsonObject = new JSONObject(mHttpsConnResponse);
                            sessionToken = jsonObject.getString("token");
                        } catch (Exception ex) {
                            Log.w(TAG, "Exception in getSessionToken : " + ex.getMessage());
                            mStatus = BstUtilsManager.JSON_PARSING_ERROR;
                            ex.printStackTrace();
                        }
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("packageName", mPkgName);
                    bundle.putString("sessionToken", sessionToken);
                    mCallback.send(mStatus, bundle);
                    break;

                default :
            }
        }
    }

    private String getQuery(ContentValues values) throws IOException
    {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Object> entry : values.valueSet())
        {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey().toString(), "UTF-8"));
            result.append("=");
            if (entry.getValue() != null)
                result.append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
        }
        return result.toString();
    }

    private String getPackageInfo(String packageName, String infoType) {
        String res = "";
        try {
            PackageInfo pkgInfo = mContext.getPackageManager().getPackageInfo(packageName, 0);
            switch (infoType) {
                case "versionname" :
                    res = pkgInfo.versionName;
                    break;
                case "versioncode" :
                    res = Integer.toString(pkgInfo.versionCode);
                    break;
            }
        } catch (Exception ex) {
            Log.e(TAG, "Exception: " + ex.getMessage());
            if (BST_DBG) ex.printStackTrace();
        }
        return res;
    }
}
