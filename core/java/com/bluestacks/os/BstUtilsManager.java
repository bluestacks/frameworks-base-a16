/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.bluestacks.os;

import android.content.Context;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.RemoteException;

/** @hide */
public class BstUtilsManager {
    private static final String TAG = "BstUtilsManager";

    private Context mContext;
    private static IBstUtilsService mService;
    private Handler mMainHandler;

    public static final int STATUS_OK = 0;

    public static final int HTTP_CONNECTION_ERROR = -100;
    public static final int HTTPS_RESPONSE_READ_ERROR = -101;
    public static final int JSON_PARSING_ERROR = -102;

    /**
     * @hide
     */
    public BstUtilsManager(Context context, IBstUtilsService service) {
        mContext = context;
        mService = service;
        mMainHandler = new Handler(mContext.getMainLooper());
    }

    /**
     * @hide used for testing only
     */
    public BstUtilsManager(Context context, IBstUtilsService service, Handler handler) {
        mContext = context;
        mService = service;
        mMainHandler = handler;
    }

    /**
     * Used to set system properties.
     * @hide
     */
    public static boolean setProperty(String key, String value) {
        if (key == null || value == null) throw new IllegalArgumentException("key|value is null");
        try {
            return mService.setProperty(key, value);
        } catch (RemoteException e) {
            // will never happen
            throw new RuntimeException(e);
        }

    }

    /**
     * Gets an BstUtilsManager instance associated with a Context.
     * @hide
     */
    public static BstUtilsManager get(Context context) {
        if (context == null) throw new IllegalArgumentException("context is null");
        return (BstUtilsManager) context.getSystemService(Context.BST_UTILS);
    }

    public void setBstProposedRotation(int rotation) {
        try {
            mService.setBstProposedRotation(rotation);
        } catch (RemoteException e) {
            // will never happen
            throw new RuntimeException(e);
        }
    }
    /**
     * Gets the Application name associated with the passed pid.
     * @hide
     */
    public String getAppNameFromPid(int pid) {
        if (pid == -1) throw new IllegalArgumentException("pid is null");
        try {
            return mService.getAppNameFromPid(pid);
        } catch (RemoteException e) {
            // will never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Calling BstCommandProcessor to alter service/component state.
     * @hide
     */
    public void setServiceComponentState(String service, boolean enabled){
        if (service == null) throw new IllegalArgumentException("service|attribute is null");
        try {
            mService.setServiceComponentState(service, enabled);
        } catch (RemoteException e) {
            // will never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns true if system will display soft keyboard
     * @hide
     */

    public boolean isBstSoftKeyboardEnabled() {
        try {
            return  mService.isBstSoftKeyboardEnabled();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Update value for soft keyboard, if true is passed soft keyboard will be enabled
     * otherwise softkeyboard will not be enabled
     * @hide
     */
    public void setBstSoftKeyboardStatus(boolean show) {
        try {
             mService.setBstSoftKeyboardStatus(show);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the Apk Download/Update source.
     * Returns Cloud response as String (containing Apk download/Update source).
     * @hide
     */
    public String getApkDownloadSource(String packageName,String appInstallType) {
        try {
            return mService.getApkDownloadSource(packageName,appInstallType);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Launches the Apps Store, based on the cloud response.
     * @hide
     */
    public void launchAppStore(String packageName, String apkUpdateSource) {
        try {
            mService.launchAppStore(packageName, apkUpdateSource);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the Session Token
     * Returns session token as String as callback Response
     * on ResultReceiver object.
     * @hide
     */
    public void getSessionToken(String packageName, ResultReceiver resultReceiver) {
        try {
            mService.getSessionToken(packageName, resultReceiver);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
