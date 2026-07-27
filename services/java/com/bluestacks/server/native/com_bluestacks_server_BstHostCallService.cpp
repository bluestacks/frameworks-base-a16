/***********************************************************************
# Copyright (C) 2020 BlueStack Systems, Inc.
# All Rights Reserved
#
# THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF BLUESTACK SYSTEMS, INC.
# The copyright notice above does not evidence any actual or intended
# publication of such source code.
#************************************************************************/

#define LOG_TAG "BstHostCallService-JNI"

#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>

#include <utils/Log.h>
#include <utils/misc.h>

#include <cutils/properties.h>

#include "Hcall.h"
#include "Xpl.h"
#include "XthrPool.h"

XLOG_SET_MODULE (XLOG_MODULE_HCALL);

using namespace android;

static jboolean dbg = false;

static jboolean debug()
{
    char prop[PROPERTY_VALUE_MAX];
    property_get("bst.debug.bsthostcall", prop, "0");
    if (!atoi(prop)) {
        return false;
    } else {
        return true;
    }
}

typedef struct ThreadPool {
    XthrPool threadPool;
} ThreadPool;

ThreadPool g_thr_pool;

string getStringFromJstring(JNIEnv* env, jobject clazz,  jstring jString) {
    if (!jString) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "Null argument");
        return "";
    }

    const char* charStr = env->GetStringUTFChars(jString, NULL);
    string strStr = string(charStr);
    env->ReleaseStringUTFChars(jString, charStr);
    return strStr;
}

static jint native_init(JNIEnv *env, jobject clazz)
{
    ALOGD("%s: called", __func__);

    xerr_t rval = xplLibInit();
    if (rval != XERR_SUCCESS) {
        fprintf(stderr,"%s: xplLibInit failed. err = %d\n", __func__, rval);
        ALOGE("%s: xplLibInit failed. err = %d\n", __func__, rval);
        return rval;
    }

    rval = hcallLibInitGuest();
    if (rval != XERR_SUCCESS) {
        XLOGE("hcallLibInitGuest failed, error %d", rval);
        return rval;
    }

    xthrPoolInit(&g_thr_pool.threadPool, 1);

    ALOGD("%s: returning, rval = %d", __func__, rval);
    dbg |= debug();
    return rval;
}

static jint initInputDebuggingStatus(JNIEnv *env, jobject clazz, jboolean showTouches, jboolean showPointerLocation)
{
    if (dbg) ALOGD("%s: showTouches = %d showPointerLocation = %d", __func__, showTouches, showPointerLocation);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallInitInputDebuggingStatusRpc(showTouches, showPointerLocation);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d",__func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint initImeStatus(JNIEnv* env, jobject clazz, jstring imeNameStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string imeName = getStringFromJstring(env, clazz, imeNameStr);

    if (imeName.empty()) return -1;

    if (dbg) ALOGD("%s: imeName = %s", __func__, imeName.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallInitImeStatusRpc(imeName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint syncInstalledApps(JNIEnv* env, jobject clazz, jstring jsonDataStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string jsonData = getStringFromJstring(env, clazz, jsonDataStr);

    if (jsonData.empty()) return -1;

    if (dbg) ALOGD("%s: jsonData = %s", __func__, jsonData.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallSyncInstalledAppsRpc(jsonData.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onOrientationChanged(JNIEnv *env, jobject clazz, int orientation) {
    if (dbg) ALOGD("%s: orientation = %d", __func__, orientation);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnOrientationChangedRpc(orientation);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d",__func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onImeChanged(JNIEnv* env, jobject clazz, jstring imeNameStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string imeName = getStringFromJstring(env, clazz, imeNameStr);

    if (imeName.empty()) return -1;

    if (dbg) ALOGD("%s: imeName = %s", __func__, imeName.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
	        xerr_t rval = hcallOnImeChangedRpc(imeName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onTextEditModeChanged(JNIEnv* env, jobject clazz, jboolean isTextEditMode) {
    if (dbg) ALOGD("%s: isTextEditMode = %d", __func__, isTextEditMode);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
	        xerr_t rval = hcallOnTextEditModeChangedRpc(isTextEditMode);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d",__func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onActivityDisplayed(JNIEnv* env, jobject clazz, jstring pkgStr, jstring activityStr, jstring callingPackageStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string pkg = getStringFromJstring(env, clazz, pkgStr);
    string activity = getStringFromJstring(env, clazz, activityStr);
    string callingPackage = getStringFromJstring(env, clazz, callingPackageStr);

    if (pkg.empty() || activity.empty()) return -1;

    if (dbg) ALOGD("%s: package = %s, activity = %s callingPackage = %s", __func__, pkg.c_str(), activity.c_str(), callingPackage.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
	        xerr_t rval = hcallOnActivityDisplayedRpc(pkg.c_str(), activity.c_str(), callingPackage.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d",__func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint exportFiles(JNIEnv* env, jobject clazz, jstring folderStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string folder = getStringFromJstring(env, clazz, folderStr);

    if (folder.empty()) return -1;

    if (dbg) ALOGD("%s: folder = %s", __func__, folder.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallExportFilesRpc(folder.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint startImportFiles(JNIEnv* env, jobject clazz, jstring typeStr, jboolean isMultipleAllowed) {
    if (dbg) ALOGD("%s: called", __func__);
    string type = getStringFromJstring(env, clazz, typeStr);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallStartImportFilesRpc(type.c_str(), isMultipleAllowed);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint startExportFiles(JNIEnv* env, jobject clazz) {
    if (dbg) ALOGD("%s: called", __func__);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallStartExportFilesRpc();
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint launchBsx(JNIEnv* env, jobject clazz) {
    if (dbg) ALOGD("%s: called", __func__);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallLaunchBsxRpc();
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint setClipboardText(JNIEnv* env, jobject clazz, jstring textStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string text = getStringFromJstring(env, clazz, textStr);

    if (text.empty()) return -1;

    if (dbg) ALOGD("%s: text = %s", __func__, text.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallSetClipboardTextRpc(text.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onVolumeChanged(JNIEnv* env, jobject clazz, jboolean mute, jint volume) {
    if (dbg) ALOGD("%s: mute = %d, volume = %d", __func__, mute, volume);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
	        xerr_t rval = hcallOnVolumeChangedRpc(mute, volume);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onGoogleLoginCompleted(JNIEnv* env, jobject clazz, jstring emailIdStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string emailId = getStringFromJstring(env, clazz, emailIdStr);

    if (emailId.empty()) return -1;

    if (dbg) ALOGD("%s: emailId = %s", __func__, emailId.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnGoogleLoginCompletedRpc(emailId.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onBluestacksLoginCompleted(JNIEnv* env, jobject clazz, jstring emailIdStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string emailId = getStringFromJstring(env, clazz, emailIdStr);

    if (emailId.empty()) return -1;

    if (dbg) ALOGD("%s: emailId = %s", __func__, emailId.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnBluestacksLoginCompletedRpc(emailId.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint setAppConfigDbParams(JNIEnv* env, jobject clazz, jstring jPkgStr, jboolean macrosDisabled, jboolean showFeedbackPopup, jstring mouseCursorStyleStr, jboolean nativeGamepad) {
    if (dbg) ALOGD("%s: called", __func__);
    string pkg = getStringFromJstring(env, clazz, jPkgStr);
    string mouseCursorStyle = getStringFromJstring(env, clazz, mouseCursorStyleStr);

    if (pkg.empty()) return -1;

    if (dbg) ALOGD("%s: package = %s, macrosDisabled = %d, showFeedbackPopup = %d, mouseCursorStyle = %s, nativeGamepad = %d", __func__, pkg.c_str(), macrosDisabled, showFeedbackPopup, mouseCursorStyle.c_str(), nativeGamepad);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallSetAppConfigDbParamsRpc(pkg.c_str(), macrosDisabled, showFeedbackPopup, mouseCursorStyle.c_str(), nativeGamepad);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint googleAccountListUpdated(JNIEnv* env, jobject clazz, jstring emailIdStr, jint action) {
    if (dbg) ALOGD("%s: called", __func__);
    string emailId  = getStringFromJstring(env, clazz, emailIdStr);

    if (emailId.empty()) return -1;

    if (dbg) ALOGD("%s: emailId = %s, action = %d", __func__, emailId.c_str(), action);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallGoogleAccountListUpdatedRpc(emailId.c_str(), action);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jboolean isAppLaunchAllowed(JNIEnv* env, jobject clazz, jstring pkgStr, jboolean isFromRecentApps) {
    //This is a blocking call as we wait for the response
    //from host whether to launch the package or not.
    if (dbg) ALOGD("%s: called", __func__);
    string pkg = getStringFromJstring(env, clazz, pkgStr);

    if (pkg.empty()) return true;

    if (dbg) ALOGD("%s: pkg = %s, isFromRecentApps = %d", __func__, pkg.c_str(), isFromRecentApps);
    jboolean rval = hcallIsAppLaunchAllowedRpc(pkg.c_str(), isFromRecentApps);
    if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
    return rval;
}

static jint setGoogleAdId(JNIEnv* env, jobject clazz, jstring googleAdIdStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string googleAdId = getStringFromJstring(env, clazz, googleAdIdStr);

    if (googleAdId.empty()) return -1;

    if (dbg) ALOGD("%s: googleAdId = %s", __func__, googleAdId.c_str());
    jint rval = hcallSetGoogleAdIdRpc(googleAdId.c_str());
    if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
    return rval;
}

static jint onCursorLocationChanged(JNIEnv* env, jobject clazz, jint x, jint y) {
    if (dbg) ALOGD("%s called: x = %d, y = %d", __func__, x, y);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnCursorLocationChangedRpc(x, y);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onLocaleChanged(JNIEnv* env, jobject clazz, jstring jLocaleStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string locale = getStringFromJstring(env, clazz, jLocaleStr);

    if (locale.empty()) return -1;

    if (dbg) ALOGD("%s: locale = %s", __func__, locale.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnLocaleChangedRpc(locale.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onAppInstalled(JNIEnv* env, jobject clazz, jstring jsonDataStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string jsonData = getStringFromJstring(env, clazz, jsonDataStr);

    if (dbg) ALOGD("%s: jsonData = %s", __func__, jsonData.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnAppInstalledRpc(jsonData.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("hcallOnAppInstalledRpc failed, error %d", rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onAppUninstalled(JNIEnv* env, jobject clazz, jstring pkgNameStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string pkgName = getStringFromJstring(env, clazz, pkgNameStr);

    if (pkgName.empty()) return -1;

    if (dbg) ALOGD("%s: pkgName = %s", __func__, pkgName.c_str());
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnAppUninstalledRpc(pkgName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning , rval = %d", __func__, rval);
        });
    return 0;
}

static jint onScreenshotSaved(JNIEnv* env, jobject clazz, jstring fileNameStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string fileName = getStringFromJstring(env, clazz, fileNameStr);

    if (fileName.empty()) return -1;

    if (dbg) ALOGD("%s: fileName = %s", __func__, fileName.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnScreenshotSavedRpc(fileName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint createDesktopShortcut(JNIEnv* env, jobject clazz, jstring pkg) {
    if (dbg) ALOGD("%s: called", __func__);
    string pkgName = getStringFromJstring(env, clazz, pkg);

    if (pkgName.empty()) return -1;

    if (dbg) ALOGD("%s: pkgName = %s", __func__, pkgName.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallCreateDesktopShortcutRpc(pkgName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint importFilesCompleted(JNIEnv* env, jobject clazz, jint status, jstring folder) {
    if (dbg) ALOGD("%s: called", __func__);
    string folderString = getStringFromJstring(env, clazz, folder);

    if (folderString.empty()) return -1;

    if (dbg) ALOGD("%s: folderString = %s", __func__, folderString.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnImportFilesCompletedRpc(status, folderString.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint openUrl(JNIEnv* env, jobject clazz, jstring url) {
    if (dbg) ALOGD("%s: called", __func__);
    string urlString = getStringFromJstring(env, clazz, url);

    if (urlString.empty()) return -1;

    if (dbg) ALOGD("%s: urlString = %s", __func__, urlString.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOpenUrlRpc(urlString.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onWalletMessage(JNIEnv* env, jobject clazz, jstring content) {
    if (dbg) ALOGD("%s: called", __func__);
    string contentString = getStringFromJstring(env, clazz, content);

    if (contentString.empty()) return -1;

    if (dbg) ALOGD("%s: contentString = %s", __func__, contentString.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
            {
            xerr_t rval = hcallOnWalletMessageRpc(contentString.c_str());
            if (rval != XERR_SUCCESS) {
            ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
            });
    return 0;
}

static jint initVolume(JNIEnv* env, jobject clazz, jboolean mute, jint volume, jint maxVolume) {
    if (dbg) ALOGD("%s: mute = %d, volume = %d, maxVolume = %d", __func__, mute, volume, maxVolume);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallInitVolumeRpc(mute, volume, maxVolume);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onAppNotificationReceived(JNIEnv* env, jobject clazz, jstring notificationJson) {
    if (dbg) ALOGD("%s: called", __func__);
    string notificationJsonString = getStringFromJstring(env, clazz, notificationJson);

    if (notificationJsonString.empty()) return -1;

    if (dbg) ALOGD("%s: notificationJsonString = %s", __func__, notificationJsonString.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
            {
            xerr_t rval = hcallOnAppNotificationReceivedRpc(notificationJsonString.c_str());
            if (rval != XERR_SUCCESS) {
            ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
            });
    return 0;
}

static jint setUsageStatsUpdateInterval(JNIEnv *env, jobject clazz, int secs) {
    if (dbg) ALOGD("%s: secs = %d", __func__, secs);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallSetUsageStatsUpdateIntervalRpc(secs);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d",__func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onVibrate(JNIEnv* env, jobject clazz, jint duration, jstring pkgStr) {
    ALOGD("%s called", __func__);
    string pkg = getStringFromJstring(env, clazz, pkgStr);

    if (pkg.empty()) return -1;

    if (dbg) ALOGD("%s: duration = %d, pkg = %s", __func__, duration, pkg.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnVibrateRpc(duration, pkg.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onConsoleModeStateChanged(JNIEnv* env, jobject clazz, jboolean consoleModeState) {
    if (dbg) ALOGD("%s: called consoleModeState : %d", __func__, consoleModeState);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnConsoleModeStateChangedRpc(consoleModeState);
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d",__func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint affiliateTrackingCompleted(JNIEnv* env, jobject clazz, jstring pkg) {
    if (dbg) ALOGD("%s: called", __func__);
    string pkgName = getStringFromJstring(env, clazz, pkg);

    if (pkgName.empty()) return -1;

    if (dbg) ALOGD("%s: pkgName = %s", __func__, pkgName.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnAffiliateTrackingCompletedRpc(pkgName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onNowggAccountAdded(JNIEnv* env, jobject clazz, jstring accountInfoJsonStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string accountInfoJson = getStringFromJstring(env, clazz, accountInfoJsonStr);

    if (accountInfoJson.empty()) return -1;

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnNowggAccountAddedRpc(accountInfoJson.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onNowggAccountRemoved(JNIEnv* env, jobject clazz, jstring accountNameStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string accountName = getStringFromJstring(env, clazz, accountNameStr);

    if (accountName.empty()) return -1;

    if (dbg) ALOGD("%s: accountName = %s", __func__, accountName.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnNowggAccountRemovedRpc(accountName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onGetNowggAccount(JNIEnv* env, jobject clazz, jstring accountInfoJsonStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string accountInfoJson = getStringFromJstring(env, clazz, accountInfoJsonStr);

    if (accountInfoJson.empty()) return -1;

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallAvailableNowggAccountsRpc(0, accountInfoJson.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onSetMouseAction(JNIEnv* env, jobject clazz, jstring pkgStr, jstring activityStr, jstring actionStr) {
    if (dbg) ALOGD("%s: called", __func__);

    string pkg = getStringFromJstring(env, clazz, pkgStr);
    string activity = getStringFromJstring(env, clazz, activityStr);
    string action = getStringFromJstring(env, clazz, actionStr);

    if (pkg.empty() || activity.empty()) return -1;

    if (dbg) ALOGD("%s: pkg = %s  activity = %s  action = %s" , __func__, pkg.c_str(), activity.c_str(), action.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallSetMouseActionRpc(pkg.c_str(), activity.c_str(), action.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onInstallApkCompleted(JNIEnv* env, jobject clazz, jint status, jstring apkFileNameStr, jstring errorStringStr, jstring attemptIdStr, jstring packageNameStr) {
    if (dbg) ALOGD("%s: called", __func__);

    string apkFileName = getStringFromJstring(env, clazz, apkFileNameStr);
    string errorString = getStringFromJstring(env, clazz, errorStringStr);
    string attemptId = getStringFromJstring(env, clazz, attemptIdStr);
    string packageName = getStringFromJstring(env, clazz, packageNameStr);

    if (apkFileName.empty()) return -1;

    if (dbg) ALOGD("%s: status = %d apkFileName = %s  errorString = %s  attemptId = %s packageName = %s" , __func__, status, apkFileName.c_str(), errorString.c_str(), attemptId.c_str(), packageName.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnInstallApkCompletedRpc(status, apkFileName.c_str(), errorString.c_str(), attemptId.c_str(), packageName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onUpdateQuestRules(JNIEnv* env, jobject clazz, jstring rulesStr) {
    if (dbg) ALOGD("%s: called", __func__);

    string rules = getStringFromJstring(env, clazz, rulesStr);

    if (rules.empty()) return -1;

    if (dbg) ALOGD("%s: rules = %s " , __func__, rules.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval =  hcallUpdateQuestRulesRpc(rules.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onDifferentImagePkgClicked(JNIEnv* env, jobject clazz, jstring pkgStr) {
    if (dbg) ALOGD("%s: called", __func__);

    string pkg = getStringFromJstring(env, clazz, pkgStr);

    if (pkg.empty()) return -1;

    if (dbg) ALOGD("%s: file = %s " , __func__, pkg.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
            {
                xerr_t rval = hcallOnDifferentImagePkgClickedRpc(pkg.c_str());
                if (rval != XERR_SUCCESS) {
                    ALOGE("RPC failed for %s, error %d", __func__, rval);
                }
                if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
            });
    return 0;
}

static jint onCustomAppOrientationCompleted(JNIEnv* env, jobject clazz, jstring pkgStr) {
    if (dbg) ALOGD("%s: called", __func__);

    string pkg = getStringFromJstring(env, clazz, pkgStr);

    if (pkg.empty()) return -1;

    if (dbg) ALOGD("%s: file = %s " , __func__, pkg.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
            {
                xerr_t rval = hcallOnCustomAppOrientationCompletedRpc(pkg.c_str());
                if (rval != XERR_SUCCESS) {
                    ALOGE("RPC failed for %s, error %d", __func__, rval);
                }
                if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
            });
    return 0;
}

static jint screenRecordingStarted(JNIEnv* env, jobject clazz, jboolean started, jboolean showTaps, jint audioSource) {
    if (dbg) ALOGD("%s: called screenRecordingStarted : %d showTaps : %d audioSource : %d", __func__, started, showTaps, audioSource);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
            {
                xerr_t rval = hcallScreenRecordingStartedRpc(started, showTaps, audioSource);
                if (rval != XERR_SUCCESS) {
                    ALOGE("RPC failed for %s, error %d", __func__, rval);
                }
                if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
            });
    return 0;
}

static jint handleCustomIap(JNIEnv* env, jobject clazz, jstring actionTypeStr, jstring actionDataStr) {
    if (dbg) ALOGD("%s", __func__);

    string actionType = getStringFromJstring(env, clazz, actionTypeStr);
    string actionData = getStringFromJstring(env, clazz, actionDataStr);

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallHandleCustomIAPRpc(actionType.c_str(), actionData.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onAdsInfoClick(JNIEnv* env, jobject clazz) {
    if (dbg) ALOGD("%s", __func__);
    jint rval = hcallOnAdsInfoClickRpc();
    if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
    return rval;
}

static jint removeBootLoadingScreen(JNIEnv* env, jobject clazz, jboolean isAdShown) {
    if (dbg) ALOGD("%s: enable = %d", __func__, isAdShown);
    jint rval = hcallRemoveBootLoadingScreenRpc(isAdShown);
    if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
    return rval;
}

static jint interstitialAdCompleted(JNIEnv* env, jobject clazz, jstring sourceStr, jstring actionStr) {
    if (dbg) ALOGD("%s", __func__);

    string source = getStringFromJstring(env, clazz, sourceStr);
    string action = getStringFromJstring(env, clazz, actionStr);

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallInterstitialAdCompletedRpc(source.c_str(), action.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onNowbuxUpdated(JNIEnv* env, jobject clazz) {
    if (dbg) ALOGD("%s: called", __func__);
    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnNowbuxUpdatedRpc();
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint commonCommand(JNIEnv* env, jobject clazz, jint code, jstring name, jstring data) {
    if (dbg) ALOGD("%s: called", __func__);

    string nameString = getStringFromJstring(env, clazz, name);
    string dataString = getStringFromJstring(env, clazz, data);

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallCommonCommandRpc(code, nameString.c_str(), dataString.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onIAPCompleted(JNIEnv* env, jobject clazz, jstring sourceStr, jstring dataStr) {
    if (dbg) ALOGD("%s", __func__);

    string source = getStringFromJstring(env, clazz, sourceStr);
    string data = getStringFromJstring(env, clazz, dataStr);

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnIAPCompletedRpc(source.c_str(), data.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint unzipFile(JNIEnv* env, jobject clazz, jstring fileNameStr, jstring attemptIdStr, jstring sourceStr, jstring pkgNameStr) {
    if (dbg) ALOGD("%s", __func__);

    string fileName = getStringFromJstring(env, clazz, fileNameStr);
    string attemptId = getStringFromJstring(env, clazz, attemptIdStr);
    string source = getStringFromJstring(env, clazz, sourceStr);
    string pkgName = getStringFromJstring(env, clazz, pkgNameStr);

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallUnzipFileRpc(fileName.c_str(), attemptId.c_str(), source.c_str(), pkgName.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint allowInstallApkGameCenter(JNIEnv* env, jobject clazz, jstring pkg) {
    if (dbg) ALOGD("%s: called", __func__);

    string pkgString = getStringFromJstring(env, clazz, pkg);

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallAllowInstallApkGameCenterRpc(pkgString.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onNowggSigninClicked(JNIEnv* env, jobject clazz, jstring pkg, jstring action) {
    if (dbg) ALOGD("%s: called", __func__);

    string pkgString = getStringFromJstring(env, clazz, pkg);
    string actionString = getStringFromJstring(env, clazz, action);

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnNowggSigninClickedRpc(pkgString.c_str(), actionString.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

static jint onUiDumpCompleted(JNIEnv* env, jobject clazz, jstring uiDumpStr) {
    if (dbg) ALOGD("%s: called", __func__);
    string uiDump = getStringFromJstring(env, clazz, uiDumpStr);

    if (uiDump.empty()) return -1;

    if (dbg) ALOGD("%s: uiDump = %s", __func__, uiDump.c_str());

    xthrPoolAddTask(&g_thr_pool.threadPool, [=]
        {
            xerr_t rval = hcallOnUiDumpCompletedRpc(uiDump.c_str());
            if (rval != XERR_SUCCESS) {
                ALOGE("RPC failed for %s, error %d", __func__, rval);
            }
            if (dbg) ALOGD("%s: returning, rval = %d", __func__, rval);
        });
    return 0;
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "native_init", "()I", (void *)native_init },
    { "native_initInputDebuggingStatus", "(ZZ)I", (void *)initInputDebuggingStatus },
    { "native_initImeStatus", "(Ljava/lang/String;)I", (void *)initImeStatus },
    { "native_syncInstalledApps", "(Ljava/lang/String;)I", (void *)syncInstalledApps },
    { "native_onOrientationChange", "(I)I", (void *)onOrientationChanged },
    { "native_onImeChange", "(Ljava/lang/String;)I", (void *)onImeChanged },
    { "native_onTextEditModeChange", "(Z)I", (void *)onTextEditModeChanged },
    { "native_onActivityDisplayed", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", (void *)onActivityDisplayed },
    { "native_exportFiles", "(Ljava/lang/String;)I", (void *)exportFiles},
    { "native_startImportFiles", "(Ljava/lang/String;Z)I", (void *)startImportFiles},
    { "native_startExportFiles", "()I", (void *)startExportFiles},
    { "native_launchBsx", "()I", (void*)launchBsx},
    { "native_setClipboardText", "(Ljava/lang/String;)I", (void *)setClipboardText},
    { "native_onVolumeChanged", "(ZI)I", (void *)onVolumeChanged},
    { "native_onGoogleLoginCompleted", "(Ljava/lang/String;)I", (void *)onGoogleLoginCompleted},
    { "native_onBluestacksLoginCompleted", "(Ljava/lang/String;)I", (void *)onBluestacksLoginCompleted},
    { "native_setAppConfigDbParams", "(Ljava/lang/String;ZZLjava/lang/String;Z)I", (void *)setAppConfigDbParams},
    { "native_googleAccountListUpdated", "(Ljava/lang/String;I)I", (void *)googleAccountListUpdated},
    { "native_isAppLaunchAllowed", "(Ljava/lang/String;Z)Z", (void *)isAppLaunchAllowed},
    { "native_setGoogleAdId", "(Ljava/lang/String;)I", (void *)setGoogleAdId},
    { "native_onCursorLocationChanged", "(II)I", (void *)onCursorLocationChanged},
    { "native_onLocaleChanged", "(Ljava/lang/String;)I", (void *)onLocaleChanged },
    { "native_onAppInstalled", "(Ljava/lang/String;)I", (void *)onAppInstalled },
    { "native_onAppUninstalled", "(Ljava/lang/String;)I", (void *)onAppUninstalled },
    { "native_onScreenshotSaved", "(Ljava/lang/String;)I", (void *)onScreenshotSaved },
    { "native_createDesktopShortcut", "(Ljava/lang/String;)I", (void*)createDesktopShortcut },
    { "native_importFilesCompleted", "(ILjava/lang/String;)I", (void*)importFilesCompleted },
    { "native_openUrl", "(Ljava/lang/String;)I", (void*)openUrl },
    { "native_onWalletMessage", "(Ljava/lang/String;)I", (void*)onWalletMessage },
    { "native_initVolume", "(ZII)I", (void *)initVolume},
    { "native_onAppNotificationReceived", "(Ljava/lang/String;)I", (void*)onAppNotificationReceived },
    { "native_setUsageStatsUpdateInterval", "(I)I", (void *)setUsageStatsUpdateInterval },
    { "native_onVibrate", "(ILjava/lang/String;)I", (void *)onVibrate },
    { "native_onConsoleModeStateChanged", "(Z)I", (void*)onConsoleModeStateChanged },
    { "native_affiliateTrackingCompleted", "(Ljava/lang/String;)I", (void*)affiliateTrackingCompleted },
    { "native_onNowggAccountAdded", "(Ljava/lang/String;)I", (void*)onNowggAccountAdded },
    { "native_onNowggAccountRemoved", "(Ljava/lang/String;)I", (void*)onNowggAccountRemoved },
    { "native_onGetNowggAccount", "(Ljava/lang/String;)I", (void*)onGetNowggAccount },
    { "native_onSetMouseAction", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", (void*)onSetMouseAction },
    { "native_onInstallApkCompleted", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", (void*)onInstallApkCompleted },
    { "native_onSetMouseAction", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", (void*)onSetMouseAction },
    { "native_onUpdateQuestRules", "(Ljava/lang/String;)I", (void*)onUpdateQuestRules },
    { "native_onDifferentImagePkgClicked", "(Ljava/lang/String;)I", (void*)onDifferentImagePkgClicked },
    { "native_onCustomAppOrientationCompleted", "(Ljava/lang/String;)I", (void*)onCustomAppOrientationCompleted },
    { "native_screenRecordingStarted", "(ZZI)I", (void*)screenRecordingStarted },
    { "native_handleCustomIap", "(Ljava/lang/String;Ljava/lang/String;)I", (void*)handleCustomIap },
    { "native_onAdsInfoClick", "()I", (void*)onAdsInfoClick },
    { "native_removeBootLoadingScreen", "(Z)I", (void*)removeBootLoadingScreen },
    { "native_interstitialAdCompleted", "(Ljava/lang/String;Ljava/lang/String;)I", (void*)interstitialAdCompleted },
    { "native_onNowbuxUpdated", "()I", (void *)onNowbuxUpdated},
    { "native_commonCommand", "(ILjava/lang/String;Ljava/lang/String;)I", (void*)commonCommand },
    { "native_onIAPCompleted", "(Ljava/lang/String;Ljava/lang/String;)I", (void*)onIAPCompleted },
    { "native_unzipFile", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", (void*)unzipFile },
    { "native_allowInstallApkGameCenter", "(Ljava/lang/String;)I", (void*)allowInstallApkGameCenter },
    { "native_onNowggSigninClicked", "(Ljava/lang/String;Ljava/lang/String;)I", (void*)onNowggSigninClicked },
    { "native_onUiDumpCompleted", "(Ljava/lang/String;)I", (void *)onUiDumpCompleted }
};

// This function only registers the native methods
static int register_bluestacks_server_hostCallService(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(env,
                "com/bluestacks/server/BstHostCallService", gMethods, NELEM(gMethods));
}

jint JNI_OnLoad(JavaVM* vm, void* /* reserved */)
{
    JNIEnv* env = NULL;
    jint result = -1;

    ALOGI("%s called", __func__);
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        return result;
    }
    ALOG_ASSERT(env, "Could not retrieve the env");

    if (register_bluestacks_server_hostCallService(env) != JNI_OK) {
        ALOGE("ERROR: BstHostCallService native registration failed");
        return result;
    }

    /* success -- return valid version number */
    return JNI_VERSION_1_4;
}
