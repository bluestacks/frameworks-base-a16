package com.bluestacks.os;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

/** @hide */
public class BstHostCallManager {
    private static final String TAG = "BstHostCallManager";

    /** Calling app context. */
    private final Context mContext;

    private final IBstHostCallService mBstHostCallService;

    /**
     * @hide
     */
    public BstHostCallManager(Context context, IBstHostCallService service) {
        mContext = context;
        mBstHostCallService = service;
    }

    /**
     * @hide
     */
    public int initImeStatus(String imeName) {
        try {
            return mBstHostCallService.initImeStatus(imeName);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int initInputDebuggingStatus(boolean showTouches, boolean showPointerLocation) {
        try {
            return mBstHostCallService.initInputDebuggingStatus(showTouches, showPointerLocation);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int syncInstalledApps(String jsonData) {
        try {
            return mBstHostCallService.syncInstalledApps(jsonData);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onOrientationChange(int orientation) {
        try {
            return mBstHostCallService.onOrientationChange(orientation);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onImeChange(String imeName) {
        if (imeName == null) throw new IllegalArgumentException("imeName is null");
        try {
            return mBstHostCallService.onImeChange(imeName);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onTextEditModeChange(boolean isTextEditMode) {
        try {
            return mBstHostCallService.onTextEditModeChange(isTextEditMode);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onActivityDisplayed(String pkg, String activity, String callingPackage) {
        if (pkg == null) throw new IllegalArgumentException("pkg is null");
        if (activity == null) throw new IllegalArgumentException("activity is null");
        try {
            return mBstHostCallService.onActivityDisplayed(pkg, activity, callingPackage);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int exportFiles(String folder) {
        if (folder == null) throw new IllegalArgumentException("folder is null");
        try {
            return mBstHostCallService.exportFiles(folder);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int startImportFiles(String type, boolean isMultipleAllowed) {
        try {
            return mBstHostCallService.startImportFiles(type, isMultipleAllowed);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int startExportFiles() {
        try {
            return mBstHostCallService.startExportFiles();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int launchBsx() {
        try {
            return mBstHostCallService.launchBsx();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int setClipboardText(String text) {
        if (text == null) throw new IllegalArgumentException("text is null");
        try {
            //sending string upto the length 16351, otherwise it breaks in getHostBoundBuffer.
            return mBstHostCallService.setClipboardText(text.substring(0, Math.min(text.length(), 16351)));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onVolumeChanged(boolean mute, int volume) {
        try {
            return mBstHostCallService.onVolumeChanged(mute, volume);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onGoogleLoginCompleted(String emailId) {
        if (emailId == null) throw new IllegalArgumentException("emailId is null");
        try {
            return mBstHostCallService.onGoogleLoginCompleted(emailId);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onBluestacksLoginCompleted(String emailId) {
        if (emailId == null) throw new IllegalArgumentException("emailId is null");
        try {
            return mBstHostCallService.onBluestacksLoginCompleted(emailId);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int setAppConfigDbParams(String pkg, boolean macrosDisabled, boolean showFeedbackPopup, String mouseCursorStyle, boolean nativeGamepad) {
        if (pkg == null) throw new IllegalArgumentException("package is null");
        try {
            return mBstHostCallService.setAppConfigDbParams(pkg, macrosDisabled, showFeedbackPopup, mouseCursorStyle, nativeGamepad);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * @hide
     */
    public int googleAccountListUpdated(String emailId, int action) {
        if (emailId == null) throw new IllegalArgumentException("emailId is null");
        try {
            return mBstHostCallService.googleAccountListUpdated(emailId, action);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public boolean isAppLaunchAllowed(String pkg, boolean isFromRecentApps) {
        if (pkg == null) throw new IllegalArgumentException("package is null");
        try {
            return mBstHostCallService.isAppLaunchAllowed(pkg, isFromRecentApps);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int setGoogleAdId(String googleAdId) {
        if (googleAdId == null) throw new IllegalArgumentException("googleAdId is null");
        try {
            return mBstHostCallService.setGoogleAdId(googleAdId);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onCursorLocationChanged(int x, int y) {
        try {
            return mBstHostCallService.onCursorLocationChanged(x, y);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onLocaleChanged(String locale) {
        try {
            return mBstHostCallService.onLocaleChanged(locale);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onAppInstalled(String jsonData) {
        try {
            return mBstHostCallService.onAppInstalled(jsonData);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onAppUninstalled(String pkg) {
        try {
            return mBstHostCallService.onAppUninstalled(pkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onScreenshotSaved(String fileName) {
        try {
            return mBstHostCallService.onScreenshotSaved(fileName);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int createDesktopShortcut(String pkg) {
        try {
            return mBstHostCallService.createDesktopShortcut(pkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int importFilesCompleted(int status, String folder) {
        try {
            return mBstHostCallService.importFilesCompleted(status, folder);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int openUrl(String url) {
        try {
            return mBstHostCallService.openUrl(url);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onWalletMessage(String content) {
        try {
            return mBstHostCallService.onWalletMessage(content);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *@hide
     */
    public int initVolume(boolean mute, int volume, int maxVolume) {
        try {
            return mBstHostCallService.initVolume(mute, volume, maxVolume);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onAppNotificationReceived(String notificationJson) {
        try {
            return mBstHostCallService.onAppNotificationReceived(notificationJson);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int setUsageStatsUpdateInterval(int secs) {
        try {
            return mBstHostCallService.setUsageStatsUpdateInterval(secs);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onVibrate(int duration, String pkg) {
        try {
            return mBstHostCallService.onVibrate(duration, pkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onConsoleModeStateChanged(boolean consoleModeState) {
        try {
            return mBstHostCallService.onConsoleModeStateChanged(consoleModeState);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int affiliateTrackingCompleted(String pkg) {
        try {
            return mBstHostCallService.affiliateTrackingCompleted(pkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onNowggAccountAdded(String accountInfoJson) {
        try {
            return mBstHostCallService.onNowggAccountAdded(accountInfoJson);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onNowggAccountRemoved(String accountName) {
        try {
            return mBstHostCallService.onNowggAccountRemoved(accountName);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onGetNowggAccount(String accountInfoJson) {
        try {
            return mBstHostCallService.onGetNowggAccount(accountInfoJson);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onSetMouseAction(String packageName, String activity, String action) {
        try {
            return mBstHostCallService.onSetMouseAction(packageName, activity, action);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onInstallApkCompleted(int status, String apkFileName, String errorString, String attemptId, String packageName) {
        try {
            return mBstHostCallService.onInstallApkCompleted(status, apkFileName, errorString, attemptId, packageName);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onUpdateQuestRules(String rules) {
        try {
            return mBstHostCallService.onUpdateQuestRules(rules);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onDifferentImagePkgClicked(String pkg) {
        try {
            return mBstHostCallService.onDifferentImagePkgClicked(pkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onCustomAppOrientationCompleted(String pkg) {
        try {
            return mBstHostCallService.onCustomAppOrientationCompleted(pkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int screenRecordingStarted(boolean started, boolean showTaps, int audioSource) {
        try {
            return mBstHostCallService.screenRecordingStarted(started, showTaps, audioSource);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int handleCustomIap(String actionType, String actionData) {
        try {
            return mBstHostCallService.handleCustomIap(actionType, actionData);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onAdsInfoClick() {
        try {
            return mBstHostCallService.onAdsInfoClick();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int removeBootLoadingScreen(boolean isAdShown) {
        try {
            return mBstHostCallService.removeBootLoadingScreen(isAdShown);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int interstitialAdCompleted(String source, String action) {
        try {
            return mBstHostCallService.interstitialAdCompleted(source, action);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onNowbuxUpdated() {
        try {
            return mBstHostCallService.onNowbuxUpdated();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int commonCommand(int code, String name, String data) {
        try {
            return mBstHostCallService.commonCommand(code, name, data);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onIAPCompleted(String source, String data) {
        try {
            return mBstHostCallService.onIAPCompleted(source, data);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int unzipFile(String fileName, String attemptId, String source, String pkgName) {
        try {
            return mBstHostCallService.unzipFile(fileName, attemptId, source, pkgName);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int allowInstallApkGameCenter(String pkg) {
        try {
            return mBstHostCallService.allowInstallApkGameCenter(pkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onNowggSigninClicked(String pkg, String actionType) {
        try {
            return mBstHostCallService.onNowggSigninClicked(pkg, actionType);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @hide
     */
    public int onUiDumpCompleted(String uiDump) {
        try {
            return mBstHostCallService.onUiDumpCompleted(uiDump);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
