package com.bluestacks.os;

import android.os.ResultReceiver;

/**
  * Functions ordering here should not be altered,
  * If new functions are to added, they should be appended.
  * This order directly corresponds to ordering in
  * /frameworks/native/libs/binder/include/binder/IBstUtilsService.h
  * If order of functions is altered here, similar changes should be done in /framework/native.
  */

interface IBstUtilsService
{
    boolean setProperty(String key, String value);
    String getAppNameFromPid(int pid);
    void setServiceComponentState(String service, boolean enabled);
    boolean isBstSoftKeyboardEnabled();
    void setBstSoftKeyboardStatus(boolean show);
    void setBstProposedRotation(int rotation);
    String getApkDownloadSource(String packageName,String appInstallType);
    void launchAppStore(String packageName, String apkUpdateSource);
    void getSessionToken(String packageName, in ResultReceiver resultReceiver);
}
