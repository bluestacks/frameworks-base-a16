/***********************************************************************
# Copyright (C) 2020 BlueStack Systems, Inc.
# All Rights Reserved
#
# THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF BLUESTACK SYSTEMS, INC.
# The copyright notice above does not evidence any actual or intended
# publication of such source code.
#************************************************************************/

package com.bluestacks.server;

import android.content.Context;
import android.util.Slog;
import android.os.SystemProperties;

import com.bluestacks.os.IBstHostCallService;

/**
 * BlueStacks Host Call Service.
 * @hide
 */
public class BstHostCallService extends IBstHostCallService.Stub {
    private static final String TAG = "BstHostCallService";
    private static final boolean DBG = false || SystemProperties.getInt("bst.debug.bsthostcall", 0) > 0 ? true : false;
    private final Context mContext;

    /**
     * @hide
     */
    public BstHostCallService(Context context)
    {
        super();
        if (DBG) Slog.d(TAG, "Initializing BstHostCallService with context: " + context);
        mContext = context;
        // Loading native lib
        System.loadLibrary("hostcall_jni");
        int rval = native_init();
        if (rval < 0) {
            Slog.e(TAG, "native_init failed");
            throw new IllegalStateException("Failed to init hostcall lib"); // Is an Exception not error.
        }
    }

    /**
     * @hide
     */
    public int initImeStatus(String imeName) {
        if (DBG) Slog.d(TAG, "in BstHostCallService initImeStatus, imeName = " + imeName);
        return native_initImeStatus(imeName);
    }

    /**
     * @hide
     */
    public int initInputDebuggingStatus(boolean showTouches, boolean showPointerLocation) {
        if (DBG) Slog.d(TAG, "in BstHostCallService initInputDebuggingStatus, showTouches = " + showTouches + ", showPointerLocation = " + showPointerLocation);
        return native_initInputDebuggingStatus(showTouches, showPointerLocation);
    }

    /**
     * @hide
     */
    public int syncInstalledApps(String jsonData) {
        if (DBG) Slog.d(TAG, "in BstHostCallService syncInstalledApps, jsonData = " + jsonData);
        return native_syncInstalledApps(jsonData);
    }

    /**
     * @hide
     */
    public int onOrientationChange(int orientation) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onOrientationChange, orientation = " + orientation);
        return native_onOrientationChange(orientation);
    }

    /**
     * @hide
     */
    public int onImeChange(String imeName) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onImeChange, selected ime = " + imeName);
        return native_onImeChange(imeName);
    }

    /**
     * @hide
     */
    public int onTextEditModeChange(boolean isTextEditMode) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onTextEditModeChange, TextEditMode enabled = " + isTextEditMode);
        return native_onTextEditModeChange(isTextEditMode);
    }

    /**
     * @hide
     */
    public int onActivityDisplayed(String pkg, String activity, String callingPackage) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onActivityDisplayed, package: " + pkg + " activity: " + activity + " callingPackage: "+callingPackage);
        return native_onActivityDisplayed(pkg, activity, callingPackage);
    }

    /**
     * @hide
     */
    public int exportFiles(String folder) {
        if (DBG) Slog.d(TAG, "in BstHostCallService exportFiles, folder: " + folder);
        return native_exportFiles(folder);
    }

    /**
     * @hide
     */
    public int startImportFiles(String type, boolean isMultipleAllowed) {
        if (DBG) Slog.d(TAG, "in BstHostCallService startImportFiles");
        return native_startImportFiles(type, isMultipleAllowed);
    }

    /**
     * @hide
     */
    public int startExportFiles() {
        if (DBG) Slog.d(TAG, "in BstHostCallService startExportFiles");
        return native_startExportFiles();
    }

    /**
     * @hide
     */
    public int launchBsx() {
        if (DBG) Slog.d(TAG, "in BstHostCallService launchBsx");
        return native_launchBsx();
    }

    /**
     * @hide
     */
    public int setClipboardText(String text) {
        if (DBG) Slog.d(TAG, "in BstHostCallService setClipboardText");
        return native_setClipboardText(text);
    }

    /**
     * @hide
     */
    public int onVolumeChanged(boolean mute, int volume) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onVolumeChanged, mute: " + mute + " volume: " + volume);
        return native_onVolumeChanged(mute, volume);
    }

    /**
     * @hide
     */
    public int onGoogleLoginCompleted(String emailId) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onGoogleLoginCompleted, emailId: " + emailId);
        return native_onGoogleLoginCompleted(emailId);
    }

    /**
     * @hide
     */
    public int onBluestacksLoginCompleted(String emailId) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onBluestacksLoginCompleted, emailId: " + emailId);
        return native_onBluestacksLoginCompleted(emailId);
    }

    /**
     * @hide
     */
    public int setAppConfigDbParams(String pkg, boolean macrosDisabled, boolean showFeedbackPopup, String mouseCursorStyle, boolean nativeGamepad) {
        if (DBG) Slog.d(TAG, "in BstHostCallService setAppConfigDbParams, package: " + pkg + ", macrosDisabled: " + macrosDisabled + ", showFeedbackPopup: " + showFeedbackPopup + ", mouseCursorStyle: " + mouseCursorStyle + "nativeGamepad: " + nativeGamepad);
        return native_setAppConfigDbParams(pkg, macrosDisabled, showFeedbackPopup, mouseCursorStyle, nativeGamepad);
    }

    /**
     * @hide
     */
    public int googleAccountListUpdated(String emailId, int action) {
        if (DBG) Slog.d(TAG, "in BstHostCallService googleAccountListUpdated emailId: " + emailId + ", action: " + action);
        return native_googleAccountListUpdated(emailId, action);
    }

    /**
     * @hide
     */
    public boolean isAppLaunchAllowed(String pkg, boolean isFromRecentApps) {
        if (DBG) Slog.d(TAG, "in BstHostCallService isAppLaunchAllowed, package: " + pkg);
        return native_isAppLaunchAllowed(pkg, isFromRecentApps);
    }

    /**
     * @hide
     */
    public int setGoogleAdId(String googleAdId) {
        if (DBG) Slog.d(TAG, "in BstHostCallService setGoogleAdId, googleAdId: " + googleAdId);
        return native_setGoogleAdId(googleAdId);
    }

    /**
     * @hide
     */
    public int onCursorLocationChanged(int x, int y) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onCursorLocationChanged, x: " + x + ", y: " + y);
        return native_onCursorLocationChanged(x, y);
    }

    /**
     * @hide
     */
    public int onLocaleChanged(String locale) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onLocaleChanged, locale: " + locale);
        return native_onLocaleChanged(locale);
    }

    /**
     * @hide
     */
    public int onAppInstalled(String jsonData) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onAppInstalled, jsonData : " + jsonData);
        return native_onAppInstalled(jsonData);
    }

    /**
     * @hide
     */
    public int onAppUninstalled(String pkg) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onAppUninstalled, pkg : " + pkg);
        return native_onAppUninstalled(pkg);
    }

    /**
     * @hide
     */
    public int onScreenshotSaved(String fileName) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onScreenshotSaved, fileName: " + fileName);
        return native_onScreenshotSaved(fileName);
    }

    /**
     * @hide
     */
    public int createDesktopShortcut(String pkg) {
        if (DBG) Slog.d(TAG, "in BstHostCallService createDesktopShortcut, pkg: " + pkg);
        return native_createDesktopShortcut(pkg);
    }

    /**
     * @hide
     */
    public int openUrl(String url) {
        if (DBG) Slog.d(TAG, "in BstHostCallService openUrl, url: " + url);
        return native_openUrl(url);
    }

    /**
     * @hide
     */
    public int onWalletMessage(String content) {
        if(DBG) Slog.d(TAG, "in BstHostCallService onWalletMessage, content: " + content);
        return native_onWalletMessage(content);
    }

    /**
     *@hide
     */
    public int initVolume(boolean mute, int volume, int maxVolume) {
        if (DBG) Slog.d(TAG, "in BstHostCallService initVolume, mute: " + mute + " volume: " + volume + " maxVolume: " + maxVolume);
        return native_initVolume(mute, volume, maxVolume);
    }

    /**
     * @hide
     */
    public int importFilesCompleted(int status, String folder) {
        if (DBG) Slog.d(TAG, "in BstHostCallService importFilesCompleted, folder: " + folder + " status : " + status);
        return native_importFilesCompleted(status, folder);
    }

    /**
     * @hide
     */
    public int onAppNotificationReceived(String notificationJson) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onAppNotificationReceived, notificationJson: " + notificationJson);
        return native_onAppNotificationReceived(notificationJson);
    }

    /**
     * @hide
     */
    public int setUsageStatsUpdateInterval(int secs) {
        if (DBG) Slog.d(TAG, "in BstHostCallService setUsageStatsUpdateInterval, secs: " + secs);
        return native_setUsageStatsUpdateInterval(secs);
    }

    /**
     * @hide
     */
    public int onVibrate(int duration, String pkg) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onVibrate, duration: " + duration + " pkg: " + pkg);
        return native_onVibrate(duration, pkg);
    }

    /**
     * @hide
     */
    public int onConsoleModeStateChanged(boolean consoleModeState) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onConsoleModeStateChanged, consoleModeState: " + consoleModeState);
        return native_onConsoleModeStateChanged(consoleModeState);
    }

    /**
     * @hide
     */
    public int affiliateTrackingCompleted(String pkg) {
        if (DBG) Slog.d(TAG, "in BstHostCallService affiliateTrackingCompleted, pkg: " + pkg);
        return native_affiliateTrackingCompleted(pkg);
    }

    /**
     * @hide
     */
    public int onNowggAccountAdded(String accountInfoJson) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onNowggAccountAdded");
        return native_onNowggAccountAdded(accountInfoJson);
    }

    /**
     * @hide
     */
    public int onNowggAccountRemoved(String accountName) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onNowggAccountRemoved accountName : " + accountName);
        return native_onNowggAccountRemoved(accountName);
    }

    /**
     * @hide
     */
    public int onGetNowggAccount(String accountInfoJson) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onGetNowggAccount");
        return native_onGetNowggAccount(accountInfoJson);
    }

    /**
     * @hide
     */
    public int onUpdateQuestRules(String rules) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onUpdateQuestRules rules : " + rules);
        return native_onUpdateQuestRules(rules);
    }

    /**
     * @hide
     */
    public int onDifferentImagePkgClicked(String pkg) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onDifferentImagePkgClicked, pkg = " + pkg);
        return native_onDifferentImagePkgClicked(pkg);
    }

    /**
     * @hide
     */
    public int onCustomAppOrientationCompleted(String packageName) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onCustomAppOrientationCompleted packageName : " + packageName);
        return native_onCustomAppOrientationCompleted(packageName);
    }

    /**
     * @hide
     */
    public int screenRecordingStarted(boolean started, boolean showTaps, int audioSource) {
        if (DBG) Slog.d(TAG, "in BstHostCallService screenRecordingStarted started : " + started + " showTaps : " + showTaps + " audioSource : " + audioSource);
        return native_screenRecordingStarted(started, showTaps, audioSource);
    }

    /**
     * @hide
     */
    public int onAdsInfoClick() {
        if (DBG) Slog.d(TAG, "in BstHostCallService onAdsInfoClick");
        return native_onAdsInfoClick();
    }

    /**
     * @hide
     */
    public int onSetMouseAction(String packageName, String activity, String action) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onSetMouseAction packageName : " + packageName+" activity : "+activity+"  action : "+action);
        return native_onSetMouseAction(packageName, activity, action);
    }

    /**
     * @hide
     */
    public int onInstallApkCompleted(int status, String apkFileName, String errorString, String attemptId, String packageName) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onInstallApkCompleted apkFileName : " + apkFileName + " errorString : " + errorString + " attemptId : " + attemptId + " packageName : " + packageName);
        return native_onInstallApkCompleted(status, apkFileName, errorString, attemptId, packageName);
    }

    /**
     * @hide
     */
    public int handleCustomIap(String actionType, String actionData) {
        if (DBG) Slog.d(TAG, "in BstHostCallService handleCustomIap actionType : " + actionType + ", actionData: " + actionData);
        return native_handleCustomIap(actionType, actionData);
    }

    /**
     * @hide
     */
    public int removeBootLoadingScreen(boolean isAdShown) {
        if (DBG) Slog.d(TAG, "in BstHostCallService removeBootLoadingScreen isAdShown : " + isAdShown);
        return native_removeBootLoadingScreen(isAdShown);
    }

    /**
     * @hide
     */
    public int interstitialAdCompleted(String source, String action) {
        if (DBG) Slog.d(TAG, "in BstHostCallService interstitialAdCompleted source : " + source + ", action: " + action);
        return native_interstitialAdCompleted(source, action);
    }

    /**
     * @hide
     */
    public int onNowbuxUpdated() {
        if (DBG) Slog.d(TAG, "in BstHostCallService onNowbuxUpdated");
        return native_onNowbuxUpdated();
    }

    /**
     * @hide
     */
    public int commonCommand(int code, String name, String data) {
        if (DBG) Slog.d(TAG, "in BstHostCallService commonCommand, code: " + code);
        String nameStr = name == null ? "" : name;
        String dataStr = data == null ? "" : data;
        return native_commonCommand(code, nameStr, dataStr);
    }

    /**
     * @hide
     */
    public int onIAPCompleted(String source, String data) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onIAPCompleted source : " + source + ", data: " + data);
        return native_onIAPCompleted(source, data);
    }

    /**
     * @hide
     */
    public int unzipFile(String fileName, String attemptId, String source, String pkgName) {
        if (DBG) Slog.d(TAG, "in BstHostCallService unzipFile, fileName: " + fileName + " attemptId: " + attemptId + " source:" + source + " pkgName: " + pkgName);
        return native_unzipFile(fileName, attemptId, source, pkgName);
    }

    /**
     * @hide
     */
    public int allowInstallApkGameCenter(String pkg) {
        if (DBG) Slog.d(TAG, "in BstHostCallService allowInstallApkGameCenter pkg : " + pkg);
        return native_allowInstallApkGameCenter(pkg);
    }

    /**
     * @hide
     */
    public int onNowggSigninClicked(String pkg, String actionType) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onNowggSigninClicked pkg : " + pkg + ", action=" + actionType);
        return native_onNowggSigninClicked(pkg, actionType);
    }

    /**
     * @hide
     */
    public int onUiDumpCompleted(String uiDump) {
        if (DBG) Slog.d(TAG, "in BstHostCallService onUiDumpCompleted, uiDump: " + uiDump);
        return native_onUiDumpCompleted(uiDump);
    }

    private static native final int native_init();
    private static native int native_initImeStatus(String imeName);
    private static native int native_initInputDebuggingStatus(boolean showTouches, boolean showPointerLocation);
    private static native int native_syncInstalledApps(String jsonData);
    private static native int native_onOrientationChange(int orientation);
    private static native int native_onImeChange(String imeName);
    private static native int native_onTextEditModeChange(boolean isTextEditMode);
    private static native int native_onActivityDisplayed(String pkg, String activity, String callingPackage);
    private static native int native_exportFiles(String folder);
    private static native int native_startImportFiles(String type, boolean isMultipleAllowed);
    private static native int native_startExportFiles();
    private static native int native_launchBsx();
    private static native int native_setClipboardText(String text);
    private static native int native_onVolumeChanged(boolean mute, int volume);
    private static native int native_onGoogleLoginCompleted(String emailId);
    private static native int native_onBluestacksLoginCompleted(String emailId);
    private static native int native_setAppConfigDbParams(String pkg, boolean macrosDisabled, boolean showFeedbackPopup, String mouseCursorStyle, boolean nativeGamepad);
    private static native int native_googleAccountListUpdated(String emailId, int action);
    private static native boolean native_isAppLaunchAllowed(String pkg, boolean isFromRecentApps);
    private static native int native_setGoogleAdId(String googleAdId);
    private static native int native_onCursorLocationChanged(int x, int y);
    private static native int native_onLocaleChanged(String locale);
    private static native int native_onAppInstalled(String jsonData);
    private static native int native_onAppUninstalled(String pkg);
    private static native int native_onScreenshotSaved(String fileName);
    private static native int native_createDesktopShortcut(String pkg);
    private static native int native_importFilesCompleted(int status, String folder);
    private static native int native_openUrl(String url);
    private static native int native_initVolume(boolean mute, int volume, int maxVolume);
    private static native int native_onAppNotificationReceived(String notificationJson);
    private static native int native_setUsageStatsUpdateInterval(int secs);
    private static native int native_onVibrate(int duration, String pkg);
    private static native int native_onConsoleModeStateChanged(boolean consoleModeState);
    private static native int native_affiliateTrackingCompleted(String pkg);
    private static native int native_onNowggAccountAdded(String accountInfoJson);
    private static native int native_onNowggAccountRemoved(String accountName);
    private static native int native_onGetNowggAccount(String accountInfoJson);
    private static native int native_onSetMouseAction(String packageName, String activity, String action);
    private static native int native_onInstallApkCompleted(int status, String apkFileName, String errorString, String attemptId, String packageName);
    private static native int native_onUpdateQuestRules(String rules);
    private static native int native_onDifferentImagePkgClicked(String pkg);
    private static native int native_onWalletMessage(String content);
    private static native int native_onCustomAppOrientationCompleted(String pkg);
    private static native int native_screenRecordingStarted(boolean started, boolean showTaps, int audioSource);
    private static native int native_handleCustomIap(String actionType, String actionData);
    private static native int native_onAdsInfoClick();
    private static native int native_removeBootLoadingScreen(boolean isAdShown);
    private static native int native_interstitialAdCompleted(String source, String action);
    private static native int native_onNowbuxUpdated();
    private static native int native_commonCommand(int code, String name, String data);
    private static native int native_onIAPCompleted(String source, String data);
    private static native int native_unzipFile(String fileName, String attemptId, String source, String pkgName);
    private static native int native_allowInstallApkGameCenter(String pkg);
    private static native int native_onNowggSigninClicked(String pkg, String actionType);
    private static native int native_onUiDumpCompleted(String uiDump);
}
