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
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Slog;
import android.os.IBinder;
import java.util.List;
import java.util.ArrayList;
import android.os.ServiceManager;

/** @hide */
public class BstFilterAppsManager {
    private static final String TAG = "BstFilterAppsManager";
    private static final boolean DEBUG = android.os.SystemProperties.getInt("bst.debug.bstfilter", 0) > 0;
    private Context mContext;
    private static IBstFilterAppsService mService;
    private Handler mMainHandler;
    private static BstFilterAppsManager sInstance;

    /**
     * @hide
     */
    public static final int ADD_ABI2_ENTRY = 0;

    /**
     * @hide
     */
    public static final int REMOVE_ABI2_ENTRY = 1;

    /**
     * @hide
     */
    public static final int ABI_ERROR = -1;

    /**
     * @hide
     */
    public static final int ARM_MODE = 4;

    /**
     * @hide
     */
    public static final int X86_MODE = 5;

    /**
     * @hide
     */
    public static final int ARM_32_MODE = 6;

    /**
     * @hide
     */
    public static final int X86_32_MODE = 7;

    /**
     * @hide
     */
    public static final int ARM_64_MODE = 8;

    /**
     * @hide
     */
    public static final int X86_64_MODE = 9;

    /**
     * @hide
     */
    public static final int XARM_MODE = 10;

    /**
     * @hide
     */
    public BstFilterAppsManager(Context context, IBstFilterAppsService service) {
        mContext = context;
        mService = service;
        mMainHandler = new Handler(mContext.getMainLooper());
    }

    /**
     * @hide used for testing only
     */
    public BstFilterAppsManager(Context context, IBstFilterAppsService service, Handler handler) {
        mContext = context;
        mService = service;
        mMainHandler = handler;
    }


    /**
     * @hide
     */
    public BstFilterAppsManager(IBstFilterAppsService service) {
        mService = service;
    }

    public static BstFilterAppsManager getInstance() {
        synchronized (BstFilterAppsManager.class) {
            if (sInstance == null) {
                IBinder b = ServiceManager.getService(Context.BST_FILTER_APPS);
                if (b != null) {
                    sInstance = new BstFilterAppsManager(IBstFilterAppsService.Stub.asInterface(b));
                }
            }
            return sInstance;
        }
    }

    /**
     * @hide
     */
    public boolean isSmallScreenApp(String pkgName)
    {
        try {
            return mService.isSmallScreenApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean forceArmInstall(String pkgName)
    {
        try {
            return mService.forceArmInstall(pkgName);
        } catch(Exception e) {
             // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean forceX86Install(String pkgName)
    {
        try {
            return mService.forceX86Install(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public int getForceAbiInstallMode(String pkgName)
    {
        try {
            return mService.getForceAbiInstallMode(pkgName);
        } catch (Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return ABI_ERROR;
        }
    }

    /**
     * @hide
     */
    public boolean isFbScreenLockApp(String pkgName)
    {
        try {
            return mService.isFbScreenLockApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isPvrtcApp(String pkgName)
    {
        try {
            return mService.isPvrtcApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isS3tcApp(String pkgName)
    {
        try {
            return mService.isS3tcApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isClearInstallApp(String pkgName)
    {
        try {
            return mService.isClearInstallApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isGlv1notimpApp(String pkgName)
    {
        try {
            return mService.isGlv1notimpApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isWhiteListed(String pkgName)
    {
        try {
            return mService.isWhiteListed(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return true;
        }
    }

    /**
     * @hide
     */
    public boolean isBlackListed(String pkgName, String version)
    {
        try {
            return mService.isBlackListed(pkgName, version);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public String getBlackListAction(String pkg)
    {
        try {
            return mService.getBlackListAction(pkg);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * @hide
     */
    public boolean isSoftKeyboardRequired(String pkgName, String activity)
    {
        try {
            return mService.isSoftKeyboardRequired(pkgName, activity);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public String getSoftKeyboardModifier(String pkgName)
    {
        try {
            return mService.getSoftKeyboardModifier(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "soft";
        }
    }

    /**
     * @hide
     */
    public boolean isMarketRequired(String pkgName)
    {
        try {
            return mService.isMarketRequired(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isHeadsetRequired(String pkgName)
    {
        try {
            return mService.isHeadsetRequired(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isPortraitDisabled(String pkgName)
    {
        try {
            return mService.isPortraitDisabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public String getCustomDpi(String pkgName)
    {
        try {
            return mService.getCustomDpi(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * @hide
     */
    public boolean isXarchApp(int uid)
    {
        try {
            return mService.isXarchApp(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * This function returns true if we want to force Landscacpe orientation if app has
     * requested for a Unspecified orientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
     * otherwise it returns false.
     * @hide
     */
    public boolean forceLandscapeOrientationIfUnspecified(String pkgName)
    {
        try {
            return mService.forceLandscapeOrientationIfUnspecified(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Return true if callingPkgName is in isScreenOrientationFixed list and currentPkgName
     * is also present in that list(as value in hashmap),this means we want to have the same
     * orientation of currentPkgName as that of callingPkgName
     * @hide
     */
    public boolean isScreenOrientationFixed(String callingPkgName,String currentPkgName)
    {
        try {
            return mService.isScreenOrientationFixed(callingPkgName,currentPkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public int isCameraRotationRequired(String pkgName)
    {
        try{
            return mService.isCameraRotationRequired(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return 0;
        }
    }

    /**
     * @hide
     */
    public boolean isForceVibrate(String pkgName)
    {
        try{
            return mService.isForceVibrate(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Bug 6203 Pocket Knights crashes on launch
     * Returns true if pkgName is of one from which we want to hide activityIntent specific info when
     * it queries the packageManager
     * Currently we hide bluestacks package specific info.
     * @hide
     */
    public boolean isHideBstActivityInfo(String pkgName)
    {
        try{
            return mService.isHideBstActivityInfo(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * This call is to determine whether the app with given uid is
     * installed in arm mode in the system or not.
     * @hide
     */
    public boolean isArmApp(int uid)
    {
        try {
            return mService.isArmApp(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * We currently return arm cpu abi values to apps installed in arm mode when they query properties such as ro.product.cpu.abi,
     * ro.product.cpu.abi2 etc via getprop
     * However, some chinese apps such as com.netease.TCYM.uc, com.youzu.wzqj.ad.baidu and com.zmxyol.union.baidu crash
     * when arm values are returned
     * Hence returning the default system abi values to such apps.
     * @hide
     */
    public boolean isXCpuAbi(String pkgName)
    {
        try {
            return mService.isXCpuAbi(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isFixedDisplayRotationApp(String pkgName)
    {
        try {
            return mService.isFixedDisplayRotationApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isModifyDisplayRotationApp(String pkgName)
    {
        try {
            return mService.isModifyDisplayRotationApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Whether the given package is a partner app (When an app enquires about Bluestacks from
     * System Features we confirm them that they are running on Bluestacks).
     * @hide
     */
    public boolean isBluestacksPartnerApp(String pkgName)
    {
        try {
            return mService.isBluestacksPartnerApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public String getAccelerometerSetting(String pkgName)
    {
        try {
            return mService.getAccelerometerSetting(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return null;
        }
    }

    /**
     * Provide list of apps having acceleration modifier in config.db
     * @hide
     */
    public List<String> getAccelerometerList()
    {
        try {
            return mService.getAccelerometerList();
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            List<String> emptyList = new ArrayList<>();
            return emptyList;
        }
    }

    /**
     * Provide list of apps having headset modifier in config.db
     * @hide
     */
    public List<String> getHeadSetList()
    {
        try {
            return mService.getHeadSetList();
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            List<String> emptyList = new ArrayList<>();
            return emptyList;
        }
    }

    /**
     * @hide
     */
    public String getInstallerPkg(String pkgName)
    {
        try {
            return mService.getInstallerPkg(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return null;
        }
    }


    /**
     * Provide app specific GLRenderer value if entry exists in config.db
     * @hide
     */
    public String getGlRenderer(String pkgName)
    {
        try {
            return mService.getGlRenderer(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Provide app specific GLVendor value if entry exists in config.db
     * @hide
     */
    public String getGlVendor(String pkgName)
    {
        try {
            return mService.getGlVendor(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Provide default URL for Browser app depending on the OEMs
     * @hide
     */
    public String getBrowserUrl()
    {
        try {
            return mService.getBrowserUrl();
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Get the memory size in bytes, to be shown to app from ActivityManagerService
     * @hide
     */
    public String getMemorySize(int uid)
    {
       try {
           return mService.getMemorySize(uid);
       } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
       }
    }

    /**
     * Update abi2 package entries (for arm apps, xprop) in a file under /data/downloads.
     * @hide
     */
    public void updateAbiEntry(int uid, String pkgName, int action)
    {
        try {
            mService.updateAbiEntry(uid, pkgName, action);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
        }
    }

    /* Check if app is for Xarm ABI
     * @hide
     */
    public boolean isXArmApp(String pkgName)
    {
        try {
            return mService.isXArmApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /* Check if app is for GL3 or not
     * @hide
     */
    public boolean isGL3App(String pkgName)
    {
        try {
            return mService.isGL3App(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if GL3 is disabled for this app or not.
     * @hide
     */
    public boolean isGL3Disabled(String pkgName)
    {
        try {
            return mService.isGL3Disabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if imeDisabled for pkgName/activity
     * if imeDisabled iskeyboardenabled call will not be sent to windows.
     * @hide
     */
    public boolean isIMEDisabled(String pkgName, String activity)
    {
        try {
            return mService.isIMEDisabled(pkgName, activity);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /* Check if app requires libHoudiniLoadHack or not
     * @hide
     */
    public String isLoadLibHackReqd(int uid)
    {
       try {
           return mService.isLoadLibHackReqd(uid);
       } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
       }
    }

    /* Check if app requires ZeroMappedPageHack or not
     * @hide
     */
    public boolean isZeroMappedPageReqd(int uid)
    {
       try {
           return mService.isZeroMappedPageReqd(uid);
       } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
       }
    }

    /*
     * Get the configinfo (requiredVersions, buildsSupported, glMode, device_models) related to the package.
     * @hide
     */
    public String getConfigInfoForPackage(String packageName)
    {
        try {
            return mService.getConfigInfoForPackage(packageName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            String result = "Entries not available for this package";
            return result;
        }
    }
    /**
     * Check if sdcard path nned to be modified for app to extract its data.
     * @hide
     */
    public boolean isModifysdPathReqd(String pkgName)
    {
       try {
           return mService.isModifysdPathReqd(pkgName);
       } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
       }
    }

    /* Check if AGAGL3 is disabled for app or not
     * @hide
     */
    public boolean isAGAGL3Disabled(String pkgName)
    {
       try {
           return mService.isAGAGL3Disabled(pkgName);
       } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
       }
    }

    /* Check if PGAGL3 is disabled for app or not
     * @hide
     */
    public boolean isPGAGL3Disabled(String pkgName)
    {
       try {
           return mService.isPGAGL3Disabled(pkgName);
       } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
       }
    }

    /* Check if app requires to show original cpuinfo or not ,for arm apps only
     * @hide
     */
    public boolean isShowOrigCpuinfoArmApp(int uid)
    {
       try {
           return mService.isShowOrigCpuinfoArmApp(uid);
       } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
       }
    }

    /* check if app needs astc feature or not
     * @hide
     */
    public boolean isAstcApp(String pkgName)
    {
        try {
            return mService.isAstcApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isPatchRelocApp(int uid)
    {
        try {
            return mService.isPatchRelocApp(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Provide app specific PgaGLVersion value if entry exists in config.db
     * @hide
     */
    public String getPgaGlVersion(String pkgName)
    {
        try {
            return mService.getPgaGlVersion(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Provide app specific AgaGLVersion value if entry exists in config.db
     * @hide
     */
    public String getAgaGlVersion(String pkgName)
    {
        try {
            return mService.getAgaGlVersion(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /*
     * check if app needs hardware astc disabled feature or not
     * @hide
     */
    public boolean isHardwareAstcDisabledApp(String pkgName)
    {
        try {
            return mService.isHardwareAstcDisabledApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }


    /*
     * check if we need to force Install Flag when performing dexopt after reboot or on upgrade
     * @hide
     */
    public boolean isForcedDexoptWithInstallFlag(String pkgName)
    {
        try {
            return mService.isForcedDexoptWithInstallFlag(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }



    /**
     * @hide
     */
    public boolean isAppUsingOldHoudini(int uid)
    {
        try {
            return mService.isAppUsingOldHoudini(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     *@hide
     */
    public String getAdditionalGlExtensions(String pkgName)
    {
        try {
            return mService.getAdditionalGlExtensions(pkgName);
        } catch (Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * @hide
     */
    public boolean areInputDevicesExposed(String packageName)
    {
        try {
            return mService.areInputDevicesExposed(packageName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isShowArmAbiListApp(int uid)
    {
        try {
            return mService.isShowArmAbiListApp(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isIgnoreLargeHeapApp(String pkgName)
    {
        try {
            return mService.isIgnoreLargeHeapApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isClearCodeCacheApp(int uid)
    {
        try {
            return mService.isClearCodeCacheApp(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isForcedInputDisabledApp(String pkgName)
    {
        try {
            return mService.isForcedInputDisabledApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isImageDetectionEnabled(String pkgName)
    {
        try {
            return mService.isImageDetectionEnabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public int getHeapGrowthLimitSize(String pkgName)
    {
        try {
            return mService.getHeapGrowthLimitSize(pkgName);
        } catch (Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return -1;
        }
    }

    /**
     * @hide
     */
    public void updateIl2cppPkgs(String pkgName)
    {
        try {
            mService.updateIl2cppPkgs(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
        }
    }

    /**
     * @hide
     */
    public boolean isIl2cppApp(int uid)
    {
        try {
            return mService.isIl2cppApp(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isHideProcMapsApp(String pkgName)
    {
        try {
            return mService.isHideProcMapsApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isEnableNativeGamePad(String pkgName)
    {
        try {
            return mService.isEnableNativeGamePad(pkgName);
        }
        catch(Exception e) {
            if(DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isPreloadBstHookLibraryApp(String pkgName)
    {
        try {
            return mService.isPreloadBstHookLibraryApp(pkgName);
        }
        catch(Exception e) {
            if(DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isSuppressedNotificationApp(String pkgName)
    {
        try {
            return mService.isSuppressedNotificationApp(pkgName);
        }
        catch(Exception e) {
            if(DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isMacrosDisabledApp(String pkgName)
    {
        try {
            return mService.isMacrosDisabledApp(pkgName);
        }
        catch(Exception e) {
            if(DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean showFeedbackPopup(String pkgName)
    {
        try {
            return mService.showFeedbackPopup(pkgName);
        }
        catch(Exception e) {
            if(DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public float ffmpegConfidenceForApp(String pkgName)
    {
        try {
            return mService.ffmpegConfidenceForApp(pkgName);
        }
        catch(Exception e) {
            if(DEBUG) e.printStackTrace();
            return 0.0f;
        }
    }

    /**
     * @hide
     */
    public boolean isAppUsingSwappy(int uid)
    {
        try {
            return mService.isAppUsingSwappy(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public String getMouseCursorStyle(String pkgName)
    {
        try {
            return mService.getMouseCursorStyle(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * @hide
     */
    public int getCameraSensorRotation(String pkgName)
    {
        try{
            return mService.getCameraSensorRotation(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return 0;
        }
    }

    /**
     * @hide
     */
    public int getCameraInitRotation(String pkgName)
    {
        try{
            return mService.getCameraInitRotation(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return 0;
        }
    }

    /**
     * Provide app specific GlVersion value based on selected mode AGA or PGA
     * @hide
     */
    public int getGlVersion(String pkgName)
    {
        try {
            String glMode = SystemProperties.get("bst.graphics_engine", null);
            String glVersionString;
            if ("pga".equals(glMode)) {
                glVersionString = getPgaGlVersion(pkgName);
            } else if ("aga".equals(glMode)) {
                glVersionString = getAgaGlVersion(pkgName);
            } else {
                Slog.w(TAG, "got invalid gl mode!");
                return -1;
            }
            return parseGlVersion(glVersionString);
        } catch(Exception e) {
            if (DEBUG) e.printStackTrace();
            return -1;
        }
    }

    /**
     * @hide
     */
    public boolean isGetNowggAccountsListApp(int uid)
    {
        try {
            return mService.isGetNowggAccountsListApp(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isGoogleSignInRequired(String pkgName)
    {
        try {
            return mService.isGoogleSignInRequired(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * convert reqGLEsVersion from String to Int.
     * eg: 3.2 is returned as 0x00030002.
     */
    private int parseGlVersion(String glVersionString)
    {
        if (glVersionString != null && !glVersionString.isEmpty()) {
            String[] versions = glVersionString.split("\\.");
            int major = Integer.parseInt(versions[0]) << 16;
            int minor = Integer.parseInt(versions[1]);
            return major + minor;
        }
        return -1;
    }

    /**
     * @hide
     */
    public String getMouseAction(String pkgName, String activity)
    {
        try {
            return mService.getMouseAction(pkgName, activity);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * @hide
     */
    public String isVulkanRequired(String pkgName)
    {
        try {
            return mService.isVulkanRequired(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Check if app should be force kill when closed from recent apps
     * case ROB-2374: Adding a new entry name: forceKill
     * @hide
     */
    public boolean isForceKillApp(String pkgName)
    {
        try {
            return mService.isForceKillApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isHppEnabled(String pkgName)
    {
        try {
            return mService.isHppEnabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

     /**
     * @hide
     */
    public boolean isHotFixApp(String pkgName)
    {
        try {
            return mService.isHotFixApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public int getUnityFlickerPatchRequired(String pkgName)
    {
        try{
            return mService.getUnityFlickerPatchRequired(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return 0;
        }
    }

    /**
     * @hide
     */
    public String getDefaultProfile(String pkgName)
    {
        try {
            return mService.getDefaultProfile(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * @hide
     */
    public boolean isGLProgramBinaryApp(String pkgName)
    {
        try{
            return mService.isGLProgramBinaryApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return true;
        }
    }

    /**
     * @hide
     */
    public boolean isGLUnmapBufferPerfApp(String pkgName)
    {
        try{
            return mService.isGLUnmapBufferPerfApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return true;
        }
    }

    /**
     * Provide app specific GLShaderWorkaround value if entry exists in config.db
     * @hide
     */
    public String getGLShaderWorkaround(String pkgName)
    {
        try {
            return mService.getGLShaderWorkaround(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Gets an BstFilterAppsManager instance associated with a Context.
     * @hide
     */
    public static BstFilterAppsManager get(Context context) {
        if (context == null) throw new IllegalArgumentException("context is null");
        return (BstFilterAppsManager) context.getSystemService(Context.BST_FILTER_APPS);
    }

    /**
     * @hide
     */
    public String getGameDefaultSetting(String pkgName)
    {
        try {
            return mService.getGameDefaultSetting(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
    * case ROB-9086: High fps config, Adding a new entry name: XperfMode
    * @hide
    */
    public int getXperfMode(int uid)
    {
        try {
            return mService.getXperfMode(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return 0;
        }
    }

    /**
     * @hide
     */
    public boolean isIntelGLDispatchComputeFlushApp(String pkgName)
    {
        try{
            return mService.isIntelGLDispatchComputeFlushApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     * dhdg: disable hook droid guard
     */
    public boolean isDhdg(int uid)
    {
        try {
            return mService.isDhdg(uid);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isGlNativeSyncDisabled(String pkgName)
    {
        try {
            return mService.isGlNativeSyncDisabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if angle is disabled for this app or not.
     * @hide
     */
    public boolean isAngleDisabled(String pkgName)
    {
        try {
            return mService.isAngleDisabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isOneStorePay(String pkgName)
    {
        try {
            return mService.isOneStorePay(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isDrmEnabled(String pkgName)
    {
        try {
            return mService.isDrmEnabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isEtherNetType(String pkgName)
    {
        try {
            return mService.isEtherNetType(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if tex target check is disabled for this app or not.
     * @hide
     */
    public boolean isTexTargetCheckDisabled(String pkgName)
    {
        try {
            return mService.isTexTargetCheckDisabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return true;
        }
    }

    /**
     * @hide
     */
    public int getFixedSurfaceRotationRequired(String pkgName)
    {
        try{
            return mService.getFixedSurfaceRotationRequired(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return -1;
        }
    }

    /**
     * @hide
     */
    public String getIapSetting(String pkgName)
    {
        try {
            return mService.getIapSetting(pkgName);
        } catch(Exception e) {
            if (DEBUG) e.printStackTrace();
            return null;
        }
    }

    /**
     * @hide
     */
    public boolean isGlMapBufferRangeHost(String pkgName)
    {
        try {
            return mService.isGlMapBufferRangeHost(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return true;
        }
    }

    /**
     * @hide
     */
    public boolean isIgnoreSyncTimeout(String pkgName)
    {
        try {
            return mService.isIgnoreSyncTimeout(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isGLVBOCacheDisableApp(String pkgName)
    {
        try{
            return mService.isGLVBOCacheDisableApp(pkgName);
	} catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isBlockEditWhenComposing(String pkgName)
    {
        try {
            return mService.isBlockEditWhenComposing(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Provide app specific GLHostInfo value if entry exists in config.db
     * @hide
     */
    public String getGLHostInfo(String pkgName)
    {
        try {
            return mService.getGLHostInfo(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Provide app specific GLExtensionsIgnore value if entry exists in config.db
     * @hide
     */
    public String getGLExtensionsIgnore(String pkgName)
    {
        try {
            return mService.getGLExtensionsIgnore(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Provide app specific VkHostInfo value if entry exists in config.db
     * @hide
     */
    public String getVkHostInfo(String pkgName)
    {
        try {
            return mService.getVkHostInfo(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Provide app specific VKDeviceExtIgnore value if entry exists in config.db
     * @hide
     */
    public String getVKDeviceExtIgnore(String pkgName)
    {
        try {
            return mService.getVKDeviceExtIgnore(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return "";
        }
    }

    /**
     * Check if EGLSurface is Ignore for this app or not.
     * @hide
     */
    public boolean isEGLSurfaceIgnore(String pkgName)
    {
        try {
            return mService.isEGLSurfaceIgnore(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isHardKeyBoard(String pkgName)
    {
        try {
            return mService.isHardKeyBoard(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isIntelAutoGLFlushApp(String pkgName)
    {
        try{
            return mService.isIntelAutoGLFlushApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return true;
        }
    }

    /**
     * @hide
     */
    public boolean isRotateDisabled(String pkgName)
    {
        try {
            return mService.isRotateDisabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isUnreal5App(String pkgName)
    {
        try {
            return mService.isUnreal5App(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isUE5PBDisabled(String pkgName)
    {
        try {
            return mService.isUE5PBDisabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isBptcApp(String pkgName)
    {
        try {
            return mService.isBptcApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * Check the value of extractNativeLibs to determine whether native libraries need to be extracted for the application.
     * @hide
     */
    public boolean isExtractNativeLibs(String pkgName)
    {
        try {
            return mService.isExtractNativeLibs(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }
    
    /**
     * @hide
     */
    public boolean isDefaultXYDpi(String pkgName)
    {
        try {
            return mService.isDefaultXYDpi(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public int getPScoreAbove(String pkgName)
    {
        try{
            return mService.getPScoreAbove(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return 0;
        }
    }

    /**
     * @hide
     */
    public boolean isMapBufRangeReadOnceEnabled(String pkgName)
    {
        try {
            return mService.isMapBufRangeReadOnceEnabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    /**
     * @hide
     */
    public boolean isFbCompleteCheckDisabled(String pkgName)
    {
        try {
            return mService.isFbCompleteCheckDisabled(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }
    /**
     * @hide
     */
    public boolean isUEEGLCrashFixApp(String pkgName)
    {
        try {
            return mService.isUEEGLCrashFixApp(pkgName);
        } catch(Exception e) {
            // Catching the error and returning default behavior as if entry was not
            // present in config.db
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

}
