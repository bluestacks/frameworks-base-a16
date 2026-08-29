package com.bluestacks.server;

import android.content.Context;
import android.content.Intent;
import android.permission.ILegacyPermissionManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.FileObserver;
import android.os.FileUtils;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;
import android.os.Process;
import android.app.ActivityManagerInternal;
import com.android.server.LocalServices;
import com.bluestacks.os.IBstFilterAppsService;
import static com.bluestacks.os.BstFilterAppsManager.ADD_ABI2_ENTRY;
import static com.bluestacks.os.BstFilterAppsManager.REMOVE_ABI2_ENTRY;
import static com.bluestacks.os.BstFilterAppsManager.ARM_MODE;
import static com.bluestacks.os.BstFilterAppsManager.X86_MODE;
import static com.bluestacks.os.BstFilterAppsManager.ARM_32_MODE;
import static com.bluestacks.os.BstFilterAppsManager.X86_32_MODE;
import static com.bluestacks.os.BstFilterAppsManager.ARM_64_MODE;
import static com.bluestacks.os.BstFilterAppsManager.X86_64_MODE;
import static com.bluestacks.os.BstFilterAppsManager.XARM_MODE;
import static com.bluestacks.os.BstFilterAppsManager.ABI_ERROR;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
/**
 * Keep track of all allowed applications.
 *
 * This will put the check on the application that a user can install and also
 * the default behavior of the portrait application i.e. whether to run the portrait
 * application in compatible mode or not.
 *
 * @hide
 */

public class BstFilterAppsService extends IBstFilterAppsService.Stub {
    private final String TAG = "BstFilterAppsService";
    private final boolean DEBUG = android.os.SystemProperties.getInt("bst.debug.bstfilter", 0) > 0;
    private static Context mContext = null;
    private PackageManager mPackageManager = null;
    private final int PORTRAIT_DISABLED = 1;
    private final int SMALL_SIZE = 2;
    private final int LARGE_SIZE = 3;

    private final int ARMDBLOAD = 0;
    private final int XPROPDBLOAD = 1;
    private final int XARCHDBLOAD = 2;
    private final int ABILISTDBLOAD = 3;

    private Map<String, String>   mAdditionalGlExtensionsList = new HashMap<String, String>();
    private Map<String, String>   mBlackList = new HashMap<String, String>();
    private Map<String, String>   mBlackListActionList = new HashMap<String, String>();
    private Map<String, String>   mDisableImeActivityList = new HashMap<String, String>();
    private Map<String, String>   mSoftKeyboardActivityList = new HashMap<String, String>();
    private Map<String, String>   mFixedScreenOrientationList = new HashMap<String, String>();
    private Map<String, String>   mSoftKeyboardActivityListModifier = new HashMap<String, String>();
    private Map<String, String>   mAccelerometerSettingList = new HashMap<String, String>();
    private Map<String, String>   mInstallerPackageList = new HashMap<String, String>();
    private Map<String, Integer>  mCustomSizeAppList = new HashMap<String, Integer>();
    private Map<String, Integer>  mUserDefinedCustomSizeAppList = new HashMap<String, Integer>();
    private Map<String, Integer>  mCustomArchAppList = new HashMap<String, Integer>();
    private Map<String, Integer>  mCameraRotationAngleAppList = new HashMap<String, Integer>();
    private HashMap<Integer,String>   mInstalledArmAppList = new HashMap();
    private Map<String, String>   mGlRendererList = new HashMap<String, String>();
    private Map<String, String>   mGlVendorList = new HashMap<String, String>();
    private HashMap<String, String>   mMemorySizeList = new HashMap<String, String>();
    private Map<String, String>   mRequiredVersionAppList = new HashMap<String, String>();
    private Map<String, String>   mBuildSupportedAppList = new HashMap<String, String>();
    private Map<String, String>   mGlModeAppList = new HashMap<String, String>();
    private Map<String, String>   mDeviceModelAppList = new HashMap<String, String>();
    private Map<String, String>   mPackageMemoryAllocator = new HashMap<String, String>();
    private Set<String>           mWhiteList = new TreeSet<String>();
    private Set<String>           mHeadsetRequiredAppList = new TreeSet<String>();
    private Set<String>           mMarketRequiredAppList = new TreeSet<String>();
    private Map<String, String>   mCustomDpiAppList = new HashMap<String, String>();
    private Set<String>           mClearInstallAppList = new TreeSet<String>();
    private Set<String>           mAmdInstallAppList = new TreeSet<String>();
    private Set<String>           mFbScreenLockAppList = new TreeSet<String>();
    private Set<String>   	      mPvrtcAppList = new TreeSet<String>();
    private Set<String>   	      mS3tcAppList = new TreeSet<String>();
    private Set<String>   	      mGlv1notimplAppList = new TreeSet<String>();
    private Set<String>   	      mXarchAppList = new TreeSet<String>();
    private Set<String>   	      defaultLandscacpeOrientationList = new TreeSet<String>();
    private Set<String>           mForceVibrateList = new TreeSet<String>();
    private Set<String>           mShowBstActivityInfoList = new TreeSet<String>();
    private Set<String>           mXpropAbiList = new TreeSet<String>();
    private Set<String>   	      mBluestacksPartnerAppList = new TreeSet<String>();
    private Set<String>   	      mFixedDisplayRotationList = new TreeSet<String>();
    private Set<String>   	      mModifyDisplayRotationList = new TreeSet<String>();
    private HashMap<Integer,String>   mInstalledxABIAppsList = new HashMap();
    private HashMap<Integer,String>   mInstalledxARCHAppsList = new HashMap();
    private HashMap<Integer,String>   mInstalledABIListAppsList = new HashMap();
    private Set<String>   	      mGl3AppList = new TreeSet<String>();
    private Map<String, String>   mLibLoaderList = new HashMap<String, String>();
    private Set<String>   	      mForceDisableGl3AppList = new TreeSet<String>();
    private Set<String>  	      mZeroMappedPageAppList = new TreeSet<String>();
    private Set<String>   	      mModifysdPathAppList = new TreeSet<String>();
    private Set<String>   	      mPGAGl3AppList = new TreeSet<String>();
    private Set<String>   	      mAGAGl3AppList = new TreeSet<String>();
    private HashMap<String, String>  mNSleepSecList = new HashMap<String, String>();
    private Set<String>  	      mXcpuAppList = new TreeSet<String>();
    private Set<String>  	      mNdkTranslationAppList = new TreeSet<String>();
    private Set<String>   	      mAstcAppList = new TreeSet<String>();
    private Set<String>   	      mPatchRelocAppList = new TreeSet<String>();
    private Map<String, String>   mPgaGlVersionList = new HashMap<String, String>();
    private Map<String, String>   mAgaGlVersionList = new HashMap<String, String>();
    private Set<String>   	      mHardwareDisableAstcAppList = new TreeSet<String>();
    private Set<String>   	      mForcedDexOptInstallFlagList = new TreeSet<String>();
    private Set<String>   	      mUsingOldHoudiniAppList = new TreeSet<String>();
    private Set<String>   	      mInputDevicesExposedAppList = new TreeSet<String>();
    private Set<String>   	      mArmAbiListApp = new TreeSet<String>();
    private Set<String>           mIgnoreLargeHeapAppList = new TreeSet<String>();
    private Set<String>           mClearCodeCacheApp = new TreeSet<String>();
    private Set<String>           mForcedInputDisabledAppList = new TreeSet<String>();
    private Set<String>           mImageDetectionEnabledAppList = new TreeSet<String>();
    private Map<String, String>   mCustomHeapSizeAppList = new HashMap<String, String>();
    private Set<String>           mHideProcMapsAppList = new TreeSet<String>();
    private Set<String>           mIl2cppAppList = new TreeSet<String>();
    private Set<String>           mEnableNativeGamePadAppList = new TreeSet<String>();
    private Set<String>           mPreloadBstHookLibraryAppList = new TreeSet<String>();
    private Set<String>           mSuppressedNotificationAppList = new TreeSet<String>();
    private Set<String>           mMacrosDisabledAppList = new TreeSet<String>();
    private Set<String>           mShowFeedbackPopupAppList = new TreeSet<String>();
    private Map<String, Float>    mFfmpegConfidenceAppList = new HashMap<String, Float>();
    private Set<String>           mUsingSwappyAppList = new TreeSet<String>();
    private Map<String, String>   mMouseCursorStyleAppList = new HashMap<String, String>();
    private Map<String, Integer>  mCameraSensorRotationAppList = new HashMap<String, Integer>();
    private Map<String, Integer>  mCameraInitRotationAppList = new HashMap<String, Integer>();
    private Set<String>           mUE4GLFlushAppList = new TreeSet<String>();
    private HashMap<String, String>  mModelPropAppList = new HashMap<String, String>();
    private Set<String>           mPMFAppList = new TreeSet<String>();
    private Set<String>           mGetNowggAccountsList = new TreeSet<String>();
    private Set<String>           mGoogleSignInReqdAppList = new TreeSet<String>();
    private Map<String, Map<String, String>> mMouseActionConfiguredAppList = new HashMap<String, Map<String, String>>();
    private Map<String, String>   mVulkanReqdAppList = new HashMap<String, String>();
    private Set<String>           mForceKillAppList = new TreeSet<String>();
    private Map<String, String>   mGameDefaultSettingAppList = new HashMap<String, String>();
    private Set<String>           mHppDisabledAppList = new TreeSet<String>();
    private Set<String>           mHotFixAppList = new TreeSet<String>();
    private Set<String>           mGLInvalidateFramebufferFilterAppList = new TreeSet<String>();
    private Map<String, Integer>  mUnityFlickerAppList = new HashMap<String, Integer>();
    private Map<String, String>   mDefaultProfileAppList = new HashMap<String, String>();
    private Map<String, Integer>  mXperfModeList = new HashMap<String, Integer>();
    private Set<String>           mIntelGLDispatchComputeFlushAppList = new TreeSet<String>();
    private Set<String>           mDhdgList = new TreeSet<String>();
    private Set<String>           mGlNativeSyncDisabledAppList = new TreeSet<String>();
    private Set<String>           mAngleDisabledAppList = new TreeSet<String>();
	private Set<String>           mOneStorePayAppList = new TreeSet<String>();
    private Set<String>           mGLProgramBinaryDisabledAppList = new TreeSet<String>();
    private Set<String>           mGLUnmapBufferPerfDisabledAppList = new TreeSet<String>();
    private Map<String, String>   mGLShaderWorkaroundList = new HashMap<String, String>();
    private Set<String>           mDrmEnabledAppList = new TreeSet<String>();
    private Set<String>           mEtherNetTypeAppList = new TreeSet<String>();
    private Set<String>           mTexTargetCheckAppList = new TreeSet<String>();
    private Map<String, Integer>  mFixedSurfaceRotationAppList = new HashMap<String, Integer>();
    private List<String>          mIapSettingList = new ArrayList<String>();
    private Map<String, Integer>  mIapSettingPkgIndexMap = new HashMap<String, Integer>();
    private Object                mIapSettingLock = new Object();
    private Set<String>           mGlMapBufferRangeHostDisabledAppList = new TreeSet<String>();
    private Set<String>           mIgnoreSyncTimeoutAppList = new TreeSet<String>();
    private Set<String>           mGLVBOCacheDisableAppList = new TreeSet<String>();
    private Set<String>           mBlockEditWhenComposingAppList = new TreeSet<String>();
    private Map<String, String>   mGLHostInfoList = new HashMap<String, String>();
    private Map<String, String>   mGLExtensionsIgnoreList = new HashMap<String, String>();
    private Map<String, String>   mVkHostInfoList = new HashMap<String, String>();
    private Map<String, String>   mVKDeviceExtIgnoreList = new HashMap<String, String>();
    private Set<String>           mEGLSurfaceIgnoreAppList = new TreeSet<String>();
    private Set<String>           mHardKeyBoardAppList = new TreeSet<String>();
    private Set<String>           mIntelAutoGLFlushDisabledAppList = new TreeSet<String>();
    private Set<String>           mRotateDisabledAppList = new TreeSet<String>();
    private Map<String, Boolean>  mUnreal5AppMap = new HashMap<String, Boolean>();
    private Set<String>           mUE5PBDisabledAppList = new TreeSet<String>();
    private Set<String>           mUEEGLCrashFixAppList = new TreeSet<String>();
    private Set<String>   	      mBptcAppList = new TreeSet<String>();
    private Set<String>           mExtractNativeLibsAppList = new TreeSet<String>();
    private Set<String>           mDefaultXYDpiAppList = new TreeSet<String>();
    private Map<String, Integer>  mPScoreAboveAppList = new HashMap<String, Integer>();
    private Set<String>           mGlMapBufferRangeReadOnceAppList = new TreeSet<String>();
    private Set<String>           mFbCompleteCheckDisabledAppList = new TreeSet<String>();

    private boolean mListsInited = false;
    private String  mBrowserUrl  = "";

    private static final String configFilePath = "/data/downloads/";
    private static final String bstMiscFilePath = "/data/downloads/.tmp/";

    // To maintain uid;packageName entries for apps installed in arm mode.
    private static final String bstABI2Apps = bstMiscFilePath + ".bstABI2Apps";
    // To maintain uid;packageName entries for apps installed in arm mode and xprop entry in config.db.
    private static final String bstxABIApps = bstMiscFilePath + ".bstxABIApps";
    // To maintain uid;packageName entries for apps CPU_ABI/CPU_ABI2
    private static final String bstxARCHApps = bstMiscFilePath + ".bstXarchApps";
    // To maintain uid;packageName entries for apps
    private static final String bstAbilistApps = bstMiscFilePath + ".bstAbilistApps";
    // To store packageNames and there respective permissions/
    private static final String bstPermsFilePath = configFilePath + ".dp/";
    // To store nanosleep time values for apps installed with modifier nsleepsecs.
    private static final String bstNSleepFilePath = bstMiscFilePath + ".nsleep";
    // Top store packageNames for which we need to hide proc maps info related to bluestacks
    private static final String bstHideProcMapsFilePath = bstMiscFilePath + ".hpm";
    // To store custom ro.product.model values for apps (error 70 issue)
    private static final String bstModelPropFilePath = bstMiscFilePath + ".modelProp";
    // To store packageNames for which we need fixed promon emulator detection
    private static final String bstPMFFilePath = bstMiscFilePath + ".pmf";
    // To store those package name, for which we are providing Vulkan support
    private static final String vulkanAppList = configFilePath+".VulkanAppList";
    // To maintain uid;packageName entries for apps
    private static final String bstXcpuApps = bstMiscFilePath + ".bstXcpuApps";
    // To maintain uid;packageName entries for apps
    private static final String bstNdkTranslationApps = bstMiscFilePath + ".bstNdkTranslationApps";
    // To maintain uid;packageName entries for apps
    private static final String bstZeroMappedPageApps = bstMiscFilePath + ".bstZeroMappedPageApps";
    // To maintain uid;packageName entries for apps
    private static final String bstMemorySizeApps = bstMiscFilePath + ".bstMemorySizeApps";

    // This is the object monitoring /data/data/.
    private FileObserver mObserver;
    // This is the object monitoring /data/downloads/.
    private FileObserver mObserver2;
    // This is the object monitoring /data/downloads/.dp/
    private FileObserver newPermsXmlObserver;
    // Thread to read long files like config.db, whitelist.db etc.
    private Thread readFileThread;

    private final int OBSERVER_EVENTS = FileObserver.DELETE | FileObserver.CLOSE_WRITE |
        FileObserver.ATTRIB | FileObserver.MOVED_FROM | FileObserver.MOVED_TO; // | FileObserver.MODIFY;

    private final int isAmdMachine = Integer.parseInt(SystemProperties.get("bst.config.amd","0"));
    ILegacyPermissionManager mPermMgr;

    public BstFilterAppsService(Context context)
    {
        super();
        mContext = context;
        // initing various Lists
        initLists();
        mPermMgr = (ILegacyPermissionManager) ServiceManager.getService("legacy_permission");
        // Observes directory /data/downloads/.dp for any new xml files.
        bstMonitorNewPermsFilePath();
    }

    // ConfigDbParser class to Parse config.db file and add all the rulestores
    private class ConfigDbParser {
        ArrayList<RuleStore> ruleStores;
        ConfigDbParser(String json) {
            JSONObject rootNode = null;
            try {
                rootNode = new JSONObject(json);
                JSONArray jsonArray = rootNode.getJSONArray("packages");
                ruleStores = new ArrayList<RuleStore>();
                for(int i = 0; i < jsonArray.length(); i++) {
                    RuleStore ruleStore =  new RuleStore(jsonArray.getJSONObject(i));
                    ruleStores.add(ruleStore);
                }
            } catch (JSONException e) {
                Slog.e(TAG,"error in  : ConfigDbParser " + e.getMessage());
            }
        }

        public ArrayList<RuleStore> getRuleStores() {
            return ruleStores;
        }
    }

    // RuleStore class to handles multiple Rules for a package
    private class RuleStore {
        private String pkgName;
        private String pkgFilter = "";
        private ArrayList<Rule> rules;

         RuleStore(JSONObject jsonObject) {
            try {
                pkgName = jsonObject.getString("pkgname");

                if (jsonObject.has("pkg_exception")) {
                    pkgFilter = jsonObject.getString("pkg_exception");
                }
                JSONArray rulesJsonArray = jsonObject.getJSONArray("rules");
                rules = new ArrayList<Rule>();
                for(int i = 0; i < rulesJsonArray.length();i++) {
                    rules.add(new Rule(rulesJsonArray.getJSONObject(i)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        public String getPkgName() {
            return pkgName;
        }

        public String getPkgFilter() {
             return pkgFilter;
        }
        public ArrayList<Rule> getRules() {
            return rules;
        }
    }

    // Rule class to handle multiple modifiers for a package
    private class Rule {
        // must have feilds
        private String prodversion_gte;
        private String[] oems;
        private String[] androidImages;
        private String[] countries;
        private String[] hypervisors;
        // must have fields ends

        private String engine_state = "";
        private String ssse3Filter = "";
        private String[] oemFilter;
        private String[] androidImageFilter;

        private JSONObject modifiersFromRule;
        public Rule(JSONObject jsonObject) {
            //required fields
            try {
                prodversion_gte = jsonObject.getString("prodversion_gte").trim();
                oems = jsonObject.getString("oems").toLowerCase(Locale.ENGLISH).trim().split(",");
                countries = jsonObject.getString("countries").toLowerCase(Locale.ENGLISH).trim().split(",");
                if (jsonObject.has("android_images")) {
                    androidImages = jsonObject.getString("android_images").toLowerCase(Locale.ENGLISH).trim().split(",");
                    jsonObject.remove("android_images");
                }
                if (jsonObject.has("hypervisors")) {
                    hypervisors = jsonObject.getString("hypervisors").toLowerCase(Locale.ENGLISH).trim().split(",");
                    jsonObject.remove("hypervisors");
                }
                if (jsonObject.has("engine_state")) {
                    engine_state = jsonObject.getString("engine_state").toLowerCase(Locale.ENGLISH).trim();
                    jsonObject.remove("engine_state");
                }
                if (jsonObject.has("ssse3")) {
                    ssse3Filter = jsonObject.getString("ssse3").toLowerCase(Locale.ENGLISH).trim();
                    jsonObject.remove("ssse3");
                }
                if (jsonObject.has("oem_exception")) {
                    oemFilter = jsonObject.getString("oem_exception").toLowerCase(Locale.ENGLISH).trim().split(",");
                    jsonObject.remove("oem_exception");
                }
                if (jsonObject.has("android_image_exception")) {
                    androidImageFilter = jsonObject.getString("android_image_exception").toLowerCase(Locale.ENGLISH).trim().split(",");
                    jsonObject.remove("android_image_exception");
                }
                jsonObject.remove("prodversion_gte");
                jsonObject.remove("oems");
                jsonObject.remove("countries");
                modifiersFromRule = jsonObject;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        public JSONObject getModifiersFromRule() {
            return modifiersFromRule;
        }

        // check if a rule is valid for currentBuild,currentCountry,currentOem,currentAndroidImage current engine state and ssse3 instruction support
        public boolean isRuleValid(String currentBuild, String currentCountry, String currentOem, String currentAndroidImage, String currentHypervisor, String currentEngineState, String machineHasSSSE3Support )
        {
            try {
                return isValidForCurrentBuild(currentBuild) && isValidForCurrentCountry(currentCountry) && isValidForCurrentOem(currentOem) && isValidForCurrentAndroidImage(currentAndroidImage)
                    && isValidForCurrentHypervisor(currentHypervisor) && isValidForCurrentEngineState(currentEngineState) && isValidForMachineCapabilities(machineHasSSSE3Support);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        private boolean isValidForCurrentBuild(String currentBuild) {
            String currentBuildVersionParts[] = currentBuild.split("\\.");
            String prodversion_gteParts[] = prodversion_gte.split("\\.");
            for(int i = 0; i < currentBuildVersionParts.length && !currentBuildVersionParts[i].isEmpty(); i++)
            {
                int num1 = Integer.parseInt(currentBuildVersionParts[i]);
                int num2 = Integer.parseInt(prodversion_gteParts[i]);
                if(num1  < num2)
                    return false;
                else if (num1 == num2)
                    continue;
                else
                    break;
            }
            return true;
        }
        private boolean isValidForCurrentOem(String currentOem) {
            return ((Arrays.asList(oems).contains("*") || Arrays.asList(oems).contains(currentOem.toLowerCase(Locale.ENGLISH)))
                    && ((oemFilter != null && oemFilter.length > 0) ? (!Arrays.asList(oemFilter).contains(currentOem.toLowerCase(Locale.ENGLISH))) : true));
        }
        private boolean isValidForCurrentAndroidImage(String currentAndroidImage) {
            // supporting case if bst.config.image is not set or no entry for android_images in config.db
            if (currentAndroidImage.equals("") || androidImages == null)
                return true;
            return ((Arrays.asList(androidImages).contains("*") || Arrays.asList(androidImages).contains(currentAndroidImage.toLowerCase(Locale.ENGLISH)))
                    && ((androidImageFilter != null && androidImageFilter.length > 0) ? (!Arrays.asList(androidImageFilter).contains(currentAndroidImage.toLowerCase(Locale.ENGLISH))) : true));
        }
        private boolean isValidForCurrentHypervisor(String currentHypervisor) {
            // supporting case if bst.status.hypervisor is not set or no entry for hypervisors in config.db
            if (currentHypervisor.equals("") || hypervisors == null)
                return true;
            return (Arrays.asList(hypervisors).contains("*") || Arrays.asList(hypervisors).contains(currentHypervisor.toLowerCase(Locale.ENGLISH)));
        }
        private boolean isValidForCurrentCountry(String currentCountry) {
            return (Arrays.asList(countries).contains("*") || Arrays.asList(countries).contains(currentCountry.toLowerCase(Locale.ENGLISH)));
        }
        private boolean isValidForCurrentEngineState(String currentEngineState) {
            return ("").equals(engine_state) || engine_state.equals(currentEngineState.toLowerCase(Locale.ENGLISH));
        }
        private boolean isValidForMachineCapabilities(String machineHasSSSE3Support) {
            // if ssse3 not present in rule, return true
            return  ("").equals(ssse3Filter) || ssse3Filter.equals(machineHasSSSE3Support.toLowerCase(Locale.ENGLISH));
        }
    }

    /*
     * This function monitors the path and checks if there is any new xml file other that apps.xml and system.xml
     * If present it will assign the required permissions to the packages mentioned in the file.
     * It is used to grant permissions to bluestacks packages that are updated from cloud.
     */
    private void bstMonitorNewPermsFilePath() {
        newPermsXmlObserver = new FileObserver(bstPermsFilePath, OBSERVER_EVENTS) {
            public void onEvent(int event, String path) {
                try {
                    if (DEBUG) Slog.d(TAG, "event=" + event + ", path=" + path);
                    if (path.endsWith(".xml")) {
                        String fullPath = bstPermsFilePath + path;
                        if (mPermMgr == null)
                            mPermMgr = (ILegacyPermissionManager) ServiceManager.getService("legacy_permission");
                        File file = new File(fullPath);
                        if (file.exists())
                            mPermMgr.assignPermissionsToBstApps(fullPath);
                        else
                            Slog.w(TAG, "No such file present " + fullPath);
                    }
                } catch (Exception ex) {
                    Slog.e(TAG, "bstMonitorNewPermsFilePath: exception " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        };
        newPermsXmlObserver.startWatching();
    }

    private String isRegexMatch(String wildcardPkgName,String pkgName)
    {
        try {
            if (wildcardPkgName.equalsIgnoreCase(pkgName))
                return wildcardPkgName;

            if (wildcardPkgName.contains("*"))
            {
                String substr = "(\\\\.)", regex = "\\.";
                String substr2 = "(.*)", regex2 = "\\*";
                //replacing . in entry got from config file with \.
                //replacing * in entry got from config file with (.*)
                String packageNameWithRegex = wildcardPkgName.replaceAll(regex, substr);
                packageNameWithRegex = packageNameWithRegex.replaceAll(regex2, substr2);

                if (pkgName.matches(packageNameWithRegex)) {
                    if(DEBUG) Slog.d(TAG, "package " + pkgName + " matched with " + wildcardPkgName + " in this list\n");
                    return wildcardPkgName;
                }
            }
        }
        catch (Exception e)
        {
            Slog.e(TAG, "Exception in regex matching exception: " + e);
            e.printStackTrace();
        }
        return null;
    }

    private String isPackageMatchedWilcard(Map tempHashMap, String pkgName)
    {
        return isPackageMatchedWilcard(tempHashMap.keySet(),pkgName);
    }

    private String isPackageMatchedWilcard(Set tempTreeSet, String pkgName)
    {
        if(tempTreeSet.contains("~" + pkgName))
        {
            if(DEBUG) Slog.d(TAG,"pkgName present with ~ :" + pkgName + " Returning null");
            return null;
        }

        String matched = isPackageMatchedWildcardInternal(tempTreeSet, "~" + pkgName);
        if (matched != null && matched.startsWith("~"))
            return null;

        return isPackageMatchedWildcardInternal(tempTreeSet, pkgName);

    }

    private String isPackageMatchedWildcardInternal(Set tempTreeSet, String pkgName)
    {
        String maxLenghtRegexMatched = "";
        Iterator<String> it = tempTreeSet.iterator();
        while (it.hasNext())
        {
            String wildcardPkgName = it.next();
            if (wildcardPkgName == null || wildcardPkgName.trim().length() <= 0)
                break;
            String matched = isRegexMatch(wildcardPkgName,pkgName);

            if(matched != null && matched.length() > maxLenghtRegexMatched.length())
            {
                maxLenghtRegexMatched = matched;
            }

        }
        if (maxLenghtRegexMatched.length() > 0) {
            if(DEBUG) Slog.d(TAG,"pkgName matched with  :" + maxLenghtRegexMatched);
            return maxLenghtRegexMatched;
        }

        return null;
    }

    // function to remove package entry from UserDefinedCustomSizeApplist
    private void removeEntryFromUserDefinedCustomSizeAppList(String packageName) {
        synchronized (mUserDefinedCustomSizeAppList)
        {
            if (mUserDefinedCustomSizeAppList.containsKey(packageName))
            {
                if (DEBUG) Slog.d(TAG, "Removing " + packageName + " from UserDefinedCustomSizeApplist \n");
                mUserDefinedCustomSizeAppList.remove(packageName);
            }
        }
    }

    // function to add package exception list entry into the corresponding map
    private void removePackageEntryFromList(Map tempHashMap, String pkgException, Object value) {

        if (pkgException.length() > 0) {
            String pkgRemoveList[] =  pkgException.split(",");
            for (int i=0 ;i < pkgRemoveList.length; i++) {
                if (tempHashMap.containsKey(pkgRemoveList[i])) {
                    if (tempHashMap.get(pkgRemoveList[i]).equals(value)) {
                        tempHashMap.remove(pkgRemoveList[i]);
                        if (DEBUG)
                            Slog.d(TAG, "Removing package: " + pkgRemoveList[i] + " from Map \n");
                    }
                }
                if (DEBUG)
                    Slog.d(TAG, "Adding pkg_exception package: ~" + pkgRemoveList[i] + " to Map \n");
                tempHashMap.put("~"+pkgRemoveList[i],value);
            }
        }
    }

    // function to add package exception list entry into the corresponding list
    private void removePackageEntryFromList(Set tempTreeSet, String pkgException) {

        if (pkgException.length() > 0) {
            String pkgRemoveList[] =  pkgException.split(",");
            for (int i=0; i < pkgRemoveList.length; i++) {
                if (DEBUG)
                    Slog.d(TAG, "Adding pkg_exception package: ~" + pkgRemoveList[i] + " to List \n");
                tempTreeSet.add("~" + pkgRemoveList[i]);
                tempTreeSet.remove(pkgRemoveList[i]);
            }
        }
    }

    /**
     * @hide
     */
    public boolean isSmallScreenApp(String pkgName)
    {
        boolean found = false;

        if (pkgName == null) {
            Slog.e(TAG, "invalid arguments to isSmallScreenApp, pkgName = " + pkgName);
            return false;
        }

        initLists();

        if(DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in smallSized List\n");
        synchronized (mCustomSizeAppList)
        {
            if (mCustomSizeAppList.containsKey(pkgName))
            {
                if(mCustomSizeAppList.get(pkgName) == SMALL_SIZE) {
                    if(DEBUG) Slog.d(TAG, pkgName + " is in SmallSize AppList\n");
                    found = true;
                }
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomSizeAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (mCustomSizeAppList.get(wildcardMatchedPackageName) == SMALL_SIZE)
                    {
                        if(DEBUG) Slog.d(TAG, pkgName + " is in SmallSize AppList\n");
                        found = true;
                    }

                }
            }
        }
        // If no cloud config found for this particular app, check user defined custom size app list
        if (!found) {
            synchronized (mUserDefinedCustomSizeAppList)
            {
                if (mUserDefinedCustomSizeAppList.containsKey(pkgName))
                {
                    if(mUserDefinedCustomSizeAppList.get(pkgName) == SMALL_SIZE) {
                        if(DEBUG) Slog.d(TAG, pkgName + " is in User Defined SmallSize AppList\n");
                        found = true;
                    }
                }
            }
        }
        return found;
    }


    /**
     * This list is to populate the apps which we will installed in arm mode with
     * native bridge enable to load x86 arch libraries in runtime.
     * @hide
     */
    public boolean isXArmApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in Xarm ABI list\n");
        if (pkgName == null) {
            Slog.e(TAG, "invalid arguments to isXArmApp, pkgName = " + pkgName);
            return false;
        }

        initLists();

        synchronized (mCustomArchAppList)
        {
            if(mCustomArchAppList.containsKey(pkgName) && (mCustomArchAppList.get(pkgName) == XARM_MODE))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " found in Xarm ABI List");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomArchAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (mCustomArchAppList.get(wildcardMatchedPackageName) == XARM_MODE)
                    {
                        if (DEBUG) Slog.d(TAG, pkgName + " is in Xarm ABI List\n");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean forceArmInstall(String pkgName)
    {
        if(DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in forceArmInstall List\n");
        if (pkgName == null) {
            Slog.e(TAG, "invalid arguments to forceArmInstall, pkgName = " + pkgName);
            return false;
        }

        initLists();

        boolean isAmdAppMarkerFound = isAmdInstallApp(pkgName);
        if (isAmdAppMarkerFound)
        {
            return true;
        }

        synchronized (mCustomArchAppList)
        {
            int configVal;
            if(mCustomArchAppList.containsKey(pkgName)) {
                configVal = mCustomArchAppList.get(pkgName);
                if (configVal == ARM_MODE || configVal == ARM_32_MODE || configVal == ARM_64_MODE || configVal == XARM_MODE) {
                    if (DEBUG) Slog.d(TAG, pkgName + " found in forced in forceArmInstall");
                    return true;
                }
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomArchAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    configVal = mCustomArchAppList.get(wildcardMatchedPackageName);
                    if (configVal == ARM_MODE || configVal == ARM_32_MODE || configVal == ARM_64_MODE || configVal == XARM_MODE)
                    {
                        if(DEBUG) Slog.d(TAG, pkgName + " matches wildchar setup : " + wildcardMatchedPackageName + " in forceArmInstall\n");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * @hide
     */
    public boolean forceX86Install(String pkgName)
    {
        if(DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in forceX86Install List\n");
        if (pkgName == null) {
            Slog.e(TAG, "invalid arguments to forceX86Install, pkgName = " + pkgName);
            return false;
        }

        initLists();

        boolean isAmdAppMarkerFound = isAmdInstallApp(pkgName);
        if (isAmdAppMarkerFound)
        {
            return false;
        }

        synchronized (mCustomArchAppList)
        {
            int configVal;
            if(mCustomArchAppList.containsKey(pkgName)) {
                configVal = mCustomArchAppList.get(pkgName);
                if (configVal == X86_MODE || configVal == X86_32_MODE || configVal == X86_64_MODE) {
                    if (DEBUG) Slog.d(TAG, pkgName + " found in forced in forceX86Install");
                    return true;
                }
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomArchAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    configVal = mCustomArchAppList.get(wildcardMatchedPackageName);
                    if (configVal == X86_MODE || configVal == X86_32_MODE || configVal == X86_64_MODE)
                    {
                        if(DEBUG) Slog.d(TAG, pkgName + " matches wildchar setup : " + wildcardMatchedPackageName + " in forceX86Install\n");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public int getForceAbiInstallMode(String pkgName)
    {
        if (pkgName == null) {
            Slog.e(TAG, "invalid arguments to getForceInstallMode, pkgName = " + pkgName);
            return ABI_ERROR;
        }

        initLists();

        synchronized (mCustomArchAppList)
        {
            if(mCustomArchAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " found in CustomArchAppList");
                return mCustomArchAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomArchAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " matches wildchar setup : " + wildcardMatchedPackageName + " in CustomArchAppList\n");
                    return mCustomArchAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        Slog.d(TAG, pkgName + " not found int CustomArchAppList");
        return ABI_ERROR;
    }

    /**
     * Function to determine whether app installed with given uid is installed as ARM app or not.
     * @hide
     */
    public boolean isArmApp(int uid)
    {
        if(DEBUG) Slog.d(TAG, "Trying to look if app with uid " + uid + " is installed in arm mode");
        boolean result = false;

        if (uid <= 0)
        {
            Slog.e(TAG, "invalid uid send as argument in isArmApp, uid: " + uid);
            return false;
        }

        uid = mapUid(uid);
        initLists();

        synchronized(mInstalledArmAppList)
        {
            if (mInstalledArmAppList.containsKey(uid))
            {
                if (DEBUG) Slog.d(TAG, "uid: " + uid + " with pkgname" + mInstalledArmAppList.get(uid) + "found in InstalledArmApps list");
                result = true;
            }
        }
        return result;
    }

    /*
     * Load whitelist
     */
    private void loadWhitelist()
    {
        /* clear old whitelist */
        synchronized (mWhiteList)
        {
            mWhiteList.clear();
        }

        if(DEBUG) Slog.d(TAG,"loading White List");
        // Firstly look for the file in /data/data folder. If its not present over
        // there then check at /data/downloads location.
        // Merge the result of these with the user's setting file, if there is a
        // confict take the settings from later file as final verdict.

        File db = new File(configFilePath + ".wl.db");
        if (!db.exists())
        {
            db = new File(configFilePath +"whitelist.db");
        }
        readWhitelistFile(db);
        db = new File(configFilePath + ".wl_user.db");
        readWhitelistFile(db);

    }

    private void readWhitelistFile(File db)
    {
        int index = -1;
        int fullPropIndex = -1;
        Scanner input = null;
        Slog.d(TAG, "reading file: " + db);
        try {
            // If no such file exists, return
            if (!db.exists())
                return;

            input = new Scanner(db);

            while (input.hasNext()) {
                String pkgLine = input.nextLine().trim();
                String pkg;
                if(DEBUG) Slog.d(TAG, "Parsing " + pkgLine + " for whitelist\n");
                index = pkgLine.indexOf(';');
                if (index == -1) {
                    pkg = pkgLine;
                } else {
                    pkg = pkgLine.substring(0, index);
                }

                if(DEBUG)  Slog.d(TAG, "Adding " + pkg + " to whitelist \n");
                synchronized (mWhiteList)
                {
                    mWhiteList.add(pkg);
                    if (pkg.startsWith("~"))
                    {
                        pkg = pkg.substring(1);
                        mWhiteList.remove(pkg);
                    }
                }
            }
            input.close();
        } catch (Exception e) {
            Slog.e(TAG, "Exception in trying to parse whitelist: " + e);
            e.printStackTrace();
            if (input != null)
                input.close();

        }
    }

    private void loadIapConfigSettings() {
        synchronized (mIapSettingLock)
        {
            mIapSettingList.clear();
            mIapSettingPkgIndexMap.clear();
        }

        File db = new File( configFilePath + ".iap_config");

        readIapConfigSettings(db);
    }

    private void readIapConfigSettings(File db)
    {
        if(DEBUG) Slog.d(TAG, "reading file: " + db);
        try {
            // If no such file exists, return
            if (!db.exists())
                return;
            String json_input_data = loadConfigDb(db);

            JSONObject rootNode = new JSONObject(json_input_data);
            JSONArray jsonArray = rootNode.getJSONArray("custom_iap_apps");
            synchronized (mIapSettingLock)
            {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject itemNode = jsonArray.getJSONObject(i);
                    mIapSettingList.add(itemNode.getJSONObject("data").toString());

                    JSONArray pkgArray = itemNode.getJSONArray("show_on_packages");
                    for (int j = 0; j < pkgArray.length(); j++) {
                        mIapSettingPkgIndexMap.put(pkgArray.getString(j), i);
                    }
                }
            }
        } catch(Exception e) {
            Slog.e(TAG, "Exception in trying to parse iap configs: " + e);
            e.printStackTrace();
        }
    }

    private String loadConfigDb(File db) {
        String json = null;
        try {
            InputStream is = new FileInputStream(db);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            Slog.e(TAG,"error in  : loadConfigDb " + ex.getMessage());
            return null;
        }
        return json;
    }

    private void readCustomAppSettings(File db)
    {
        if(DEBUG) Slog.d(TAG, "reading file: " + db);
        try {
            // If no such file exists, return
            if (!db.exists())
                return;
            String currentBuildVersion = SystemProperties.get("bst.version", "");
            String currentCountry= SystemProperties.get("bst.country", "");
            String currentOem = SystemProperties.get("bst.oem", "");
            String currentAndroidImage = SystemProperties.get("bst.android_image", "");
            String currentHypervisor = SystemProperties.get("bst.status.hypervisor", "");
            String currentEngineState = SystemProperties.getInt("bst.status.raw_mode", 0) == 0 ? "plus" : "raw";
            String machineHasSSSE3Support = SystemProperties.getInt("bst.status.ssse3_available", 1) > 0 ? "true" : "false";

            String json_input_data = loadConfigDb(db);
            if (json_input_data != null) {
                ConfigDbParser dbParser = null;
                dbParser = new ConfigDbParser(json_input_data);

                if (dbParser.getRuleStores() != null) {
                    for (RuleStore entry : dbParser.getRuleStores()) {
                        String packageName = entry.getPkgName();
                        String origPackageName = packageName;
                        String pkgException = entry.getPkgFilter();
                        for (Rule rule : entry.getRules()) {
                            // check if rule is valid, if not check other rule.
                            if (!(rule.isRuleValid(currentBuildVersion, currentCountry, currentOem, currentAndroidImage, currentHypervisor, currentEngineState, machineHasSSSE3Support)))
                                continue;

                            JSONObject modifiersFromRule = rule.getModifiersFromRule();

                            Iterator<String> iter = modifiersFromRule.keys();

                            while (iter.hasNext()) {
                                packageName = origPackageName;
                                String key = iter.next();
                                String value = modifiersFromRule.get(key).toString().trim();
                                if ((key.equalsIgnoreCase("headset"))) {
                                    synchronized (mHeadsetRequiredAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to HeadsetRequiredAppList \n");
                                        mHeadsetRequiredAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from HeadsetRequiredAppList \n");
                                            mHeadsetRequiredAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mHeadsetRequiredAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("fixscreen"))) {
                                    synchronized (mFixedScreenOrientationList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to ScreenorientationList \n");
                                        mFixedScreenOrientationList.put(packageName, value);
                                    }
                                    if (packageName.startsWith("~")) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Removing " + origPackageName + " from ScreenorientationList \n");
                                        mFixedScreenOrientationList.remove(packageName.substring(1));
                                    }
                                    removePackageEntryFromList(mFixedScreenOrientationList,pkgException,value);
                                }
                                else if ((key.equalsIgnoreCase("mkt"))) {
                                    synchronized (mMarketRequiredAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to MarketRequiredAppList \n");
                                        mMarketRequiredAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from MarketRequiredAppList \n");
                                            mMarketRequiredAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mMarketRequiredAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("fbscreenlock"))) {
                                    synchronized (mFbScreenLockAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to FbScreenLockAppList \n");
                                        mFbScreenLockAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from FbScreenLockAppList \n");
                                            mFbScreenLockAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mFbScreenLockAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("pvrtc"))) {
                                    synchronized (mPvrtcAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to PvrtcAppList \n");
                                        mPvrtcAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from PvrtcAppList \n");
                                            mPvrtcAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPvrtcAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("s3tc"))) {
                                    synchronized (mS3tcAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to S3tcAppList \n");
                                        mS3tcAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from S3tcAppList \n");
                                            mS3tcAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mS3tcAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("glv1notimp"))) {
                                    synchronized (mGlv1notimplAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to Glv1notimplAppList \n");
                                        mGlv1notimplAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from Glv1notimplAppList \n");
                                            mGlv1notimplAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGlv1notimplAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("dpi"))) {
                                    synchronized (mCustomDpiAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to customDpiAppList \n");
                                        mCustomDpiAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from customDpiAppList \n");
                                            mCustomDpiAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCustomDpiAppList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("full"))) {
                                    synchronized (mCustomSizeAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to portraitDisabledList \n");
                                        mCustomSizeAppList.put(packageName, PORTRAIT_DISABLED);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from portraitDisabledList \n");
                                            mCustomSizeAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCustomSizeAppList,pkgException, PORTRAIT_DISABLED);
                                    }
                                    // Remove this package entry from User defined custome app size list if present
                                    removeEntryFromUserDefinedCustomSizeAppList(packageName);
                                }
                                else if ((key.equalsIgnoreCase("large"))) {
                                    synchronized (mCustomSizeAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to LargeSizeAppList \n");
                                        mCustomSizeAppList.put(packageName, LARGE_SIZE);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from LargeSizeAppList \n");
                                            mCustomSizeAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCustomSizeAppList,pkgException,LARGE_SIZE);
                                    }
                                    // Remove this package entry from User defined custome app size list if present
                                    removeEntryFromUserDefinedCustomSizeAppList(packageName);
                                }
                                else if ((key.equalsIgnoreCase("small"))) {
                                    synchronized (mCustomSizeAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to SmallSizeAppList \n");
                                        mCustomSizeAppList.put(packageName, SMALL_SIZE);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from SmallSizeAppList \n");
                                            mCustomSizeAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCustomSizeAppList,pkgException,SMALL_SIZE);
                                    }
                                    // Remove this package entry from User defined custome app size list if present
                                    removeEntryFromUserDefinedCustomSizeAppList(packageName);
                                }
                                else if (key.equalsIgnoreCase("arch")) {
                                    int mode = ABI_ERROR;
                                    if (DEBUG)
                                        Slog.d(TAG, "Adding " + packageName + "to CustomArchAppList with value : " + value);
                                    if (value.equalsIgnoreCase("arm")) {
                                        mode = ARM_MODE;
                                    } else if (value.equalsIgnoreCase("x86")) {
                                        mode = X86_MODE;
                                    } else if (value.equalsIgnoreCase("arm_32")) {
                                        mode = ARM_32_MODE;
                                    } else if (value.equalsIgnoreCase("x86_32")) {
                                        mode = X86_32_MODE;
                                    } else if (value.equalsIgnoreCase("arm_64")) {
                                        mode = ARM_64_MODE;
                                    } else if (value.equalsIgnoreCase("x86_64")) {
                                        mode = X86_64_MODE;
                                    } else if (value.equalsIgnoreCase("xarm")) {
                                        mode = XARM_MODE;
                                    }
                                    synchronized (mCustomArchAppList) {
                                        if (mode != ABI_ERROR) {
                                            mCustomArchAppList.put(packageName, mode);
                                        } else if (DEBUG){
                                            Slog.d(TAG, "Wrong value for arch entry for packagename " + packageName);
                                        }
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + packageName + " from CustomArchAppList");
                                            if (mCustomArchAppList.containsKey(packageName.substring(1))) {
                                                if (mCustomArchAppList.get(packageName.substring(1)) == mode) {
                                                     mCustomArchAppList.remove(packageName.substring(1));
                                                }
                                            }
                                        }
                                        removePackageEntryFromList(mCustomArchAppList,pkgException,mode);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("clear"))) {
                                    synchronized (mClearInstallAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to ClearInstallAppList \n");
                                        mClearInstallAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from ClearInstallAppList \n");
                                            mClearInstallAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mClearInstallAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("amd"))) {
                                    synchronized (mAmdInstallAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to AmdInstallAppList \n");
                                        mAmdInstallAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from AmdInstallAppList \n");
                                            mAmdInstallAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mAmdInstallAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("xarch"))) {
                                    synchronized (mXarchAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to XarchAppList \n");
                                        mXarchAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from XarchAppList \n");
                                            mXarchAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mXarchAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("camera")) {
                                    synchronized (mCameraRotationAngleAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mCameraRotationAngleAppList \n");
                                        if (value.equalsIgnoreCase("90"))
                                            mCameraRotationAngleAppList.put(packageName, 90);
                                        else if (value.equalsIgnoreCase("180"))
                                            mCameraRotationAngleAppList.put(packageName, 180);
                                        else
                                            mCameraRotationAngleAppList.put(packageName, 270);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mCameraRotationAngleAppList \n");
                                            mCameraRotationAngleAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCameraRotationAngleAppList,pkgException,Integer.parseInt(value));
                                    }
                                }
                                else if ((key.equalsIgnoreCase("deflandorientation"))) {
                                    synchronized (defaultLandscacpeOrientationList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to defaultLandscapeOrientationList \n");
                                        defaultLandscacpeOrientationList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from defaultLandscapeOrientationList \n");
                                            defaultLandscacpeOrientationList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(defaultLandscacpeOrientationList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("forcevibrate"))) {
                                    synchronized (mForceVibrateList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to forceVibrateList\n");
                                        mForceVibrateList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from forceVibrateList\n");
                                            mForceVibrateList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mForcedDexOptInstallFlagList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("hi"))) {
                                    synchronized (mShowBstActivityInfoList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to hideBstActivityInfoList \n");
                                        mShowBstActivityInfoList.remove(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from hideBstActivityInfoList \n");
                                            mShowBstActivityInfoList.add(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mShowBstActivityInfoList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("xprop"))) {
                                    synchronized (mXpropAbiList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to showDefaultCpuAbiList \n");
                                        mXpropAbiList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from showDefaultCpuAbiList \n");
                                            mXpropAbiList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mXpropAbiList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("papp"))) {
                                    synchronized (mBluestacksPartnerAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to BluestacksPartnerAppList \n");
                                        mBluestacksPartnerAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from BluestacksPartnerAppList \n");
                                            mBluestacksPartnerAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mBluestacksPartnerAppList,pkgException);
                                    }
                                }
                                // This entry is only to return fixed display ROTATION values to apps and not actually
                                // cause display rotation.
                                else if ((key.equalsIgnoreCase("fixedDispRotationVal"))) {
                                    synchronized (mFixedDisplayRotationList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to FixedDisplayRotationList \n");
                                        mFixedDisplayRotationList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from FixedDisplayRotationList \n");
                                            mFixedDisplayRotationList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mFixedDisplayRotationList,pkgException);
                                    }
                                }
                                // This entry is only to return modified display ROTATION values to apps and not actually
                                // cause display rotation.
                                else if ((key.equalsIgnoreCase("modifyDispRotationVal"))) {
                                    synchronized (mModifyDisplayRotationList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to ModifyDisplayRotationList \n");
                                        mModifyDisplayRotationList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from ModifyDisplayRotationList \n");
                                            mModifyDisplayRotationList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mModifyDisplayRotationList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("blacklist")) {
                                    String version = value + ";";
                                    synchronized (mBlackList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + version + " to blacklist \n");
                                        mBlackList.put(packageName, version);
                                        if (packageName.startsWith("~")) {
                                            mBlackList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mBlackList,pkgException,version);
                                    }
                                }
                                else if (key.equalsIgnoreCase("blacklistAction")) {
                                    synchronized (mBlackListActionList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to blacklist action list");
                                        mBlackListActionList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mBlackListActionList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mBlackListActionList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("soft") || key.equalsIgnoreCase("hard") || key.equalsIgnoreCase("pass") || key.equalsIgnoreCase("showhide")) {
                                    String activity = value;
                                    synchronized (mSoftKeyboardActivityList) {
                                        mSoftKeyboardActivityList.put(packageName, activity);
                                    }
                                    synchronized (mSoftKeyboardActivityListModifier) {
                                        mSoftKeyboardActivityListModifier.put(packageName, key);
                                    }
                                    if (packageName.startsWith("~")) {
                                        synchronized (mSoftKeyboardActivityList) {
                                            mSoftKeyboardActivityList.remove(packageName.substring(1));
                                        }
                                        synchronized (mSoftKeyboardActivityListModifier) {
                                            mSoftKeyboardActivityListModifier.remove(packageName.substring(1));
                                        }
                                    }
                                    removePackageEntryFromList(mSoftKeyboardActivityList,pkgException,activity);
                                    removePackageEntryFromList(mSoftKeyboardActivityListModifier,pkgException,key);
                                }
                                else if (key.equalsIgnoreCase("gl_extensions")) {
                                    synchronized (mAdditionalGlExtensionsList) {
                                        mAdditionalGlExtensionsList.put(packageName, value);
                                    }
                                    if (packageName.startsWith("~")) {
                                        synchronized (mAdditionalGlExtensionsList) {
                                            mAdditionalGlExtensionsList.remove(packageName.substring(1));
                                        }
                                    }
                                    removePackageEntryFromList(mAdditionalGlExtensionsList, pkgException, value);
                                }
                                else if (key.equalsIgnoreCase("acceleration")) {
                                    synchronized (mAccelerometerSettingList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to AccelerometerSetting List \n");
                                        mAccelerometerSettingList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mAccelerometerSettingList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mAccelerometerSettingList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("installer")) {
                                    synchronized (mInstallerPackageList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to InstallerPackageList List \n");
                                        mInstallerPackageList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mInstallerPackageList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mInstalledArmAppList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("Gl_vendor")) {
                                    synchronized (mGlVendorList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to GlVendorList \n");
                                        mGlVendorList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mGlVendorList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGlVendorList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("Gl_Renderer")) {
                                    synchronized (mGlRendererList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to GlRendererList \n");
                                        mGlRendererList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mGlRendererList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGlRendererList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("default_url")) {
                                    if (DEBUG)
                                        Slog.d(TAG, "Setting URL : " + value + " to Browser \n");
                                    mBrowserUrl = value;
                                }
                                else if (key.equalsIgnoreCase("vms")) {
                                    synchronized (mMemorySizeList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to MemorySizeList \n");
                                        mMemorySizeList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mMemorySizeList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mMemorySizeList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("GL3"))) {
                                    synchronized (mGl3AppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to Gl3AppList \n");
                                        mGl3AppList.add(packageName);
                                        if (DEBUG)
                                            Slog.d(TAG, "Removing " + origPackageName + " from forceDisableGl3AppList \n");
                                        mForceDisableGl3AppList.remove(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from Gl3AppList \n");
                                            mGl3AppList.remove(packageName.substring(1));
                                            if (DEBUG)
                                                Slog.d(TAG, "Adding " + packageName + " to ForceDisableGl3Applist");
                                            mForceDisableGl3AppList.add(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGl3AppList,pkgException);
                                        if (pkgException.length() > 0) {
                                            String pkgRemoveList[] =  pkgException.split(",");
                                            for (int i=0;i<pkgRemoveList.length; i++) {
                                                mForceDisableGl3AppList.add(pkgRemoveList[i]);
                                                mForceDisableGl3AppList.remove("~" + pkgRemoveList[i]);
                                            }
                                        }

                                    }
                                }
                                else if (key.equalsIgnoreCase("disableIme")) {
                                    String activityList = value + ";";
                                    synchronized (mDisableImeActivityList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + activityList + " to mDisableImeActivityList \n");
                                        mDisableImeActivityList.put(packageName, activityList);
                                        if (packageName.startsWith("~")) {
                                            mDisableImeActivityList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mDisableImeActivityList,pkgException,activityList);
                                    }
                                }
                                else if (key.equalsIgnoreCase("loadPath")) {
                                    synchronized (mLibLoaderList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to LibLoader List \n");
                                        mLibLoaderList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mLibLoaderList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mLibLoaderList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("zmpw"))) {
                                    synchronized (mZeroMappedPageAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to ZeroMappedPageAppList \n");
                                        mZeroMappedPageAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from ZeroMappedPageAppList \n");
                                            mZeroMappedPageAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mZeroMappedPageAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("reqdversions_gte"))) {
                                    synchronized (mRequiredVersionAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mRequiredVersionAppList \n");
                                        mRequiredVersionAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mRequiredVersionAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mRequiredVersionAppList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("build_supported"))) {
                                    synchronized (mBuildSupportedAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mBuildSupportedAppList \n");
                                        mBuildSupportedAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mBuildSupportedAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mBuildSupportedAppList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("glmode"))) {
                                    synchronized (mGlModeAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mGlModeAppList\n");
                                        mGlModeAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mGlModeAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGlModeAppList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("dev_model"))) {
                                    synchronized (mDeviceModelAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mDeviceModelAppList\n");
                                        mDeviceModelAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mDeviceModelAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mDeviceModelAppList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("mema"))) {
                                    synchronized (mPackageMemoryAllocator) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mPackageMemoryAllocator\n");
                                        mPackageMemoryAllocator.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mPackageMemoryAllocator.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPackageMemoryAllocator,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("mdsd"))) {
                                    synchronized (mModifysdPathAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mModifysdPathAppList \n");
                                        mModifysdPathAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mModifysdPathAppList \n");
                                            mModifysdPathAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mModifyDisplayRotationList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("AgaGl3Disabled"))) {
                                    synchronized (mAGAGl3AppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to AGAGl3AppList \n");
                                        mAGAGl3AppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from AGAGl3AppList \n");
                                            mAGAGl3AppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mAGAGl3AppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("PgaGl3Disabled"))) {
                                    synchronized (mPGAGl3AppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to PGAGl3AppList \n");
                                        mPGAGl3AppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from PGAGl3AppList \n");
                                            mPGAGl3AppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPGAGl3AppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("nsleepsecs"))) {
                                    synchronized (mNSleepSecList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "writing nanosleep time " + value + "for package: " + packageName + " \n");
                                        // Adding in a list so as to support nanosleep time values for multiple packages
                                        mNSleepSecList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mNSleepSecList \n");
                                            mNSleepSecList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mNSleepSecList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("xcpu"))) {
                                    synchronized (mXcpuAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to XcpuAppList \n");
                                        mXcpuAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from XcpuAppList \n");
                                            mXcpuAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mXcpuAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("ndk"))) {
                                    synchronized (mNdkTranslationAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to NdkTranslationAppList \n");
                                        mNdkTranslationAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from NdkTranslationAppList \n");
                                            mNdkTranslationAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mNdkTranslationAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("astc"))) {
                                    synchronized (mAstcAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to AstcAppList \n");
                                        mAstcAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from AstcAppList \n");
                                            mAstcAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mAstcAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("phrl"))) {
                                    synchronized (mPatchRelocAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to PatchRelocAppList \n");
                                        mPatchRelocAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing packageName :" + packageName + " from PatchRelocAppList \n");
                                            mPatchRelocAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPatchRelocAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("PgaGlVersion")) {
                                    synchronized (mPgaGlVersionList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to PgaGlversionList \n");
                                        mPgaGlVersionList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mPgaGlVersionList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPgaGlVersionList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("AgaGlVersion")) {
                                    synchronized (mAgaGlVersionList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to AgaGlversionList \n");
                                        mAgaGlVersionList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mAgaGlVersionList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mAgaGlVersionList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("hwastcDisabled"))) {
                                    synchronized (mHardwareDisableAstcAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to HardwareDisableAstcAppList \n");
                                        mHardwareDisableAstcAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from HardwareDisableAstcAppList \n");
                                            mHardwareDisableAstcAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mHardwareDisableAstcAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("dxflag"))) {
                                    synchronized (mForcedDexOptInstallFlagList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to ForcedDexOptInstallFlagList \n");
                                        mForcedDexOptInstallFlagList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from ForcedDexOptInstallFlagList \n");
                                            mForcedDexOptInstallFlagList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mForcedDexOptInstallFlagList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("oh"))) {
                                    synchronized (mUsingOldHoudiniAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mUsingOldHoudiniAppList \n");
                                        mUsingOldHoudiniAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mUsingOldHoudiniAppList \n");
                                            mUsingOldHoudiniAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mUsingOldHoudiniAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("ide"))) {
                                    synchronized (mInputDevicesExposedAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mInputDevicesExposedAppList \n");
                                        mInputDevicesExposedAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mInputDevicesExposedAppList \n");
                                            mInputDevicesExposedAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mInputDevicesExposedAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("abilist"))) {
                                    synchronized (mArmAbiListApp) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mArmAbiListApp \n");
                                        mArmAbiListApp.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mArmAbiListApp \n");
                                            mArmAbiListApp.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mArmAbiListApp,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("ilh"))) {
                                    synchronized (mIgnoreLargeHeapAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mIgnoreLargeHeapAppList \n");
                                        mIgnoreLargeHeapAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mIgnoreLargeHeapAppList \n");
                                            mIgnoreLargeHeapAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mIgnoreLargeHeapAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("ccc"))) {
                                    synchronized (mClearCodeCacheApp) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mClearCodeCacheApp \n");
                                        mClearCodeCacheApp.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mClearCodeCacheApp \n");
                                            mClearCodeCacheApp.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mClearCodeCacheApp,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("fid"))) { //Forced Input Disabled
                                    synchronized (mForcedInputDisabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mForcedInputDisabledAppList\n");
                                        mForcedInputDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mForcedInputDisabledAppList\n");
                                            mForcedInputDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mForcedInputDisabledAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("enableimagedetection"))) { //SmartControl using image detection Enabled
                                    synchronized (mImageDetectionEnabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mImageDetectionEnabledAppList\n");
                                        mImageDetectionEnabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mImageDetectionEnabledAppList\n");
                                            mImageDetectionEnabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mImageDetectionEnabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("shs")) { //Set Heap Size to value MB
                                    synchronized (mCustomHeapSizeAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mCustomHeapSizeAppList \n");
                                        mCustomHeapSizeAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mCustomHeapSizeAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCustomHeapSizeAppList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("hpm"))) { //Hide Proc Maps info from App
                                    synchronized (mHideProcMapsAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mHideProcMapsAppList\n");
                                        mHideProcMapsAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mHideProcMapsAppList\n");
                                            mHideProcMapsAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mHideProcMapsAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("enableNativeGamePad")) {
                                    synchronized (mEnableNativeGamePadAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mEnableNativeGamePadAppList \n");
                                        mEnableNativeGamePadAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + packageName + " from mEnableNativeGamePadAppList \n");
                                            mEnableNativeGamePadAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mEnableNativeGamePadAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("pbhl")) {
                                    synchronized (mPreloadBstHookLibraryAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mPreloadBstHookLibraryAppList \n");
                                        mPreloadBstHookLibraryAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + packageName + " from mPreloadBstHookLibraryAppList \n");
                                            mPreloadBstHookLibraryAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPreloadBstHookLibraryAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("suppressed")) {
                                    synchronized (mSuppressedNotificationAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mSuppressedNotificationAppList \n");
                                        mSuppressedNotificationAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + packageName + " from mSuppressedNotificationAppList \n");
                                            mSuppressedNotificationAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mSuppressedNotificationAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("macrosDisabled")) {
                                    synchronized (mMacrosDisabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mMacrosDisabledAppList \n");
                                        mMacrosDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + packageName + " from mMacrosDisabledAppList \n");
                                            mMacrosDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mMacrosDisabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("showFeedbackPopup")) {
                                    synchronized (mShowFeedbackPopupAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mShowFeedbackPopupAppList \n");
                                        mShowFeedbackPopupAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + packageName + " from mShowFeedbackPopupAppList \n");
                                            mShowFeedbackPopupAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mShowFeedbackPopupAppList, pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("ffmpConf")) {
                                    synchronized (mFfmpegConfidenceAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mFfmpegConfidenceAppList \n");
                                        float confidence = Float.parseFloat(value);
                                        mFfmpegConfidenceAppList.put(packageName, confidence);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mFfmpegConfidenceAppList \n");
                                            mFfmpegConfidenceAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mFfmpegConfidenceAppList, pkgException, confidence);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("swappy"))) {
                                    synchronized (mUsingSwappyAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mUsingSwappyAppList \n");
                                        mUsingSwappyAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mUsingSwappyAppList \n");
                                            mUsingSwappyAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mUsingSwappyAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("mouseCursorStyle"))) {
                                    synchronized (mMouseCursorStyleAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mMouseCursorStyleAppList\n");
                                        mMouseCursorStyleAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mMouseCursorStyleAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mMouseCursorStyleAppList, pkgException, value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("cameraSensor")) {
                                    synchronized (mCameraSensorRotationAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mCameraSensorRotationAppList \n");
                                        int cameraSensorRotation = Integer.parseInt(value);
                                        mCameraSensorRotationAppList.put(packageName, cameraSensorRotation);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mCameraSensorRotationAppList \n");
                                                mCameraSensorRotationAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCameraSensorRotationAppList,pkgException,cameraSensorRotation);
                                    }
                                }
                                else if (key.equalsIgnoreCase("cameraInit")) {
                                    synchronized (mCameraInitRotationAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mCameraInitRotationAppList \n");
                                        int cameraInitRotation = Integer.parseInt(value);
                                        mCameraInitRotationAppList.put(packageName, cameraInitRotation);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mCameraInitRotationAppList \n");
                                                mCameraInitRotationAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mCameraInitRotationAppList,pkgException,cameraInitRotation);
                                    }
                                }
                                else if (key.equalsIgnoreCase("UE4GLFlush")) {
                                    synchronized (mUE4GLFlushAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mUE4GLFlushAppList \n");
                                        mUE4GLFlushAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mUE4GLFlushAppList \n");
                                            mUE4GLFlushAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mUE4GLFlushAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("modelProp"))) {
                                    synchronized (mModelPropAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "writing model property " + value + "for package: " + packageName + " \n");
                                        // Adding in a list just to support custom ro.product.model values for multiple packages(for 70 error detection)
                                        mModelPropAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mModelPropAppList \n");
                                            mModelPropAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mModelPropAppList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("pmf"))) {
                                    synchronized (mPMFAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + "to mPMFAppList \n");
                                        mPMFAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mPMFAppList \n");
                                            mPMFAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPMFAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("getNowggAccounts")) {
                                    synchronized (mGetNowggAccountsList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGetNowggAccountsList \n");
                                        mGetNowggAccountsList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGetNowggAccountsList \n");
                                            mGetNowggAccountsList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGetNowggAccountsList, pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("googleSignInReqd")) {
                                    synchronized (mGoogleSignInReqdAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGoogleSignInReqdAppList \n");
                                        mGoogleSignInReqdAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGoogleSignInReqdAppList \n");
                                            mGoogleSignInReqdAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGoogleSignInReqdAppList, pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("mouseAction")) {
                                    synchronized (mMouseActionConfiguredAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding" + packageName + " to mMouseActionConfiguredAppList \n");
                                        Map<String, String> activityList = new HashMap<String, String>();
                                        JSONArray jsonArray = new JSONArray(value);
                                        for (int i = 0; i < jsonArray.length(); i++)
                                        {
                                            JSONObject jsonObj = jsonArray.getJSONObject(i);
                                            String activity = (String) jsonObj.get("activity");
                                            String action = (String) jsonObj.get("action");
                                            activityList.put(activity, action);
                                        }
                                        mMouseActionConfiguredAppList.put(packageName, activityList);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mMouseActionConfiguredAppList \n");
                                            mMouseActionConfiguredAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mMouseActionConfiguredAppList, pkgException, value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("gameSettingDefault")) {
                                   synchronized (mGameDefaultSettingAppList) {
                                        mGameDefaultSettingAppList.put(packageName, value);
                                    }
                                    if (packageName.startsWith("~")) {
                                        synchronized (mGameDefaultSettingAppList) {
                                            mGameDefaultSettingAppList.remove(packageName.substring(1));
                                        }
                                    }
                                    removePackageEntryFromList(mGameDefaultSettingAppList, pkgException, value);
                                 }
                                else if (key.equalsIgnoreCase("vulkan")) {
                                   synchronized (mVulkanReqdAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to mVulkanReqdAppList\n");
                                        mVulkanReqdAppList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mVulkanReqdAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mVulkanReqdAppList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("forceKill")) {
                                    synchronized (mForceKillAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                             packageName = "~" + packageName;
                                        }
                                         if (DEBUG)
                                             Slog.d(TAG, "Adding " + packageName + " to mForceKillAppList \n");
                                             mForceKillAppList.add(packageName);
                                         if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                 Slog.d(TAG, "Removing " + origPackageName + " from mForceKillAppList \n");
                                            mForceKillAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mForceKillAppList, pkgException);
                                     }
                                }
                                else if ((key.equalsIgnoreCase("hpp"))) {
                                    synchronized (mHppDisabledAppList) {
                                        if (value.equalsIgnoreCase("true")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mHppDisabledAppList\n");

                                        mHppDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mHppDisabledAppList\n");
                                                mHppDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mHppDisabledAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("hotfix"))) {
                                   synchronized (mHotFixAppList) {
                                       if (value.equalsIgnoreCase("false")) {
                                           packageName = "~" + packageName;
                                       }
                                       if (DEBUG)
                                           Slog.d(TAG, "Adding packageName :" + packageName + " to mHotFixAppList \n");
                                           mHotFixAppList.add(packageName);
                                       if (packageName.startsWith("~")) {
                                           if (DEBUG)
                                               Slog.d(TAG, "Removing " + origPackageName + " from mHotFixAppList \n");
                                           mHotFixAppList.remove(packageName.substring(1));
                                       }
                                       removePackageEntryFromList(mHotFixAppList,pkgException);
                                   }
                               }
                                else if (key.equalsIgnoreCase("GLIFBFilter")) {
                                    synchronized (mGLInvalidateFramebufferFilterAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGLInvalidateFramebufferFilterAppList \n");
                                        mGLInvalidateFramebufferFilterAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGLInvalidateFramebufferFilterAppList \n");
                                            mGLInvalidateFramebufferFilterAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGLInvalidateFramebufferFilterAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("UnityFlicker")) {
                                    synchronized (mUnityFlickerAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mUnityFlickerAppList \n");
                                        int unityFlicker = Integer.parseInt(value);
                                        mUnityFlickerAppList.put(packageName, unityFlicker);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mUnityFlickerAppList \n");
                                                mUnityFlickerAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mUnityFlickerAppList,pkgException,unityFlicker);
                                    }
                                }
                                else if (key.equalsIgnoreCase("DefaultProfile")) {
                                   synchronized (mDefaultProfileAppList) {
                                        mDefaultProfileAppList.put(packageName, value);
                                    }
                                    if (packageName.startsWith("~")) {
                                        synchronized (mDefaultProfileAppList) {
                                            mDefaultProfileAppList.remove(packageName.substring(1));
                                        }
                                    }
                                    removePackageEntryFromList(mDefaultProfileAppList, pkgException, value);
                                 }
                                 else if (key.equalsIgnoreCase("XperfMode")) {
                                    synchronized (mXperfModeList) {
                                        int XperfModeValue = 0;
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mXperfModeList \n");
                                        if (value.equalsIgnoreCase("true")) {
                                            XperfModeValue = 1;
                                        }
                                        else if (value.equalsIgnoreCase("false")) {
                                            XperfModeValue = 0;
                                        } else {
                                            XperfModeValue = Integer.parseInt(value);
                                        }
                                        mXperfModeList.put(packageName, XperfModeValue);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mXperfModeList \n");
                                            mXperfModeList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mXperfModeList, pkgException, XperfModeValue);
                                    }
                                }
                                else if (key.equalsIgnoreCase("IntelDCFlush")) {
                                    synchronized (mIntelGLDispatchComputeFlushAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mIntelGLDispatchComputeFlushAppList \n");
                                        mIntelGLDispatchComputeFlushAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mIntelGLDispatchComputeFlushAppList \n");
                                            mIntelGLDispatchComputeFlushAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mIntelGLDispatchComputeFlushAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("dhdg")) {
                                    synchronized (mDhdgList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mDhdgList \n");
                                        mDhdgList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mDhdgList \n");
                                            mDhdgList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mDhdgList, pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GlNativeSyncDisabled")) {
                                    synchronized (mGlNativeSyncDisabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGlNativeSyncDisabledAppList \n");
                                        mGlNativeSyncDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGlNativeSyncDisabledAppList \n");
                                            mGlNativeSyncDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGlNativeSyncDisabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("AngleDisabled")) {
                                    synchronized (mAngleDisabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mAngleDisabledAppList \n");
                                        mAngleDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mAngleDisabledAppList \n");
                                            mAngleDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mAngleDisabledAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("oneStorePay"))) {
                                    synchronized (mOneStorePayAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mOneStorePayAppList\n");

                                        mOneStorePayAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mOneStorePayAppList\n");
                                            mOneStorePayAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mOneStorePayAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GLPB")) {
                                    synchronized (mGLProgramBinaryDisabledAppList) {
                                        if (value.equalsIgnoreCase("true")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGLProgramBinaryDisabledAppList \n");
                                        mGLProgramBinaryDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGLProgramBinaryDisabledAppList \n");
                                            mGLProgramBinaryDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGLProgramBinaryDisabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GLUBPerf")) {
                                    synchronized (mGLUnmapBufferPerfDisabledAppList) {
                                        if (value.equalsIgnoreCase("true")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGLUnmapBufferPerfDisabledAppList \n");
                                        mGLUnmapBufferPerfDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGLUnmapBufferPerfDisabledAppList \n");
                                            mGLUnmapBufferPerfDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGLUnmapBufferPerfDisabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GLShaderWorkaround")) {
                                    synchronized (mGLShaderWorkaroundList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to GLShaderWorkaroundList \n");
                                        mGLShaderWorkaroundList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mGLShaderWorkaroundList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGLShaderWorkaroundList,pkgException,value);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("drm"))) {
                                    synchronized (mDrmEnabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mDrmEnabledAppList\n");

                                        mDrmEnabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mDrmEnabledAppList\n");
                                                mDrmEnabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mDrmEnabledAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("etherNetType"))) {
                                    synchronized (mEtherNetTypeAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mEtherNetTypeAppList\n");

                                        mEtherNetTypeAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG) {
                                                Slog.d(TAG, "Removing " + origPackageName + " from mEtherNetTypeAppList\n");
                                            }
                                            mEtherNetTypeAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mEtherNetTypeAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("TTCDisabled")) {
                                    synchronized (mTexTargetCheckAppList) {
                                        if (value.equalsIgnoreCase("true")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mTexTargetCheckAppList \n");
                                        mTexTargetCheckAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mTexTargetCheckAppList \n");
                                            mTexTargetCheckAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mTexTargetCheckAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("IgnoreSyncTimeout")) {
                                    synchronized (mIgnoreSyncTimeoutAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mIgnoreSyncTimeoutAppList \n");

                                        mIgnoreSyncTimeoutAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mIgnoreSyncTimeoutAppList \n");
                                            mIgnoreSyncTimeoutAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mIgnoreSyncTimeoutAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("fixedSurfaceRotation")) {
                                    synchronized (mFixedSurfaceRotationAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mFixedSurfaceRotationAppList \n");
                                        int fixedSurfaceRotation = Integer.parseInt(value);
                                        mFixedSurfaceRotationAppList.put(packageName, fixedSurfaceRotation);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mFixedSurfaceRotationAppList \n");
                                                mFixedSurfaceRotationAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mFixedSurfaceRotationAppList,pkgException,fixedSurfaceRotation);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GLMBRH")) {
                                    synchronized (mGlMapBufferRangeHostDisabledAppList) {
                                        if (value.equalsIgnoreCase("true")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGlMapBufferRangeHostDisabledAppList \n");
                                        mGlMapBufferRangeHostDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGlMapBufferRangeHostDisabledAppList \n");
                                            mGlMapBufferRangeHostDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGlMapBufferRangeHostDisabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GLVBOCacheDisable")) {
                                    synchronized (mGLVBOCacheDisableAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGLVBOCacheDisableList \n");
                                        mGLVBOCacheDisableAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGLVBOCacheDisableAppList \n");
                                            mGLVBOCacheDisableAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGLVBOCacheDisableAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("BEWC")) {
                                    synchronized (mBlockEditWhenComposingAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mBlockEditWhenComposingAppList \n");
                                        mBlockEditWhenComposingAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mBlockEditWhenComposingAppList \n");
                                            mBlockEditWhenComposingAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mBlockEditWhenComposingAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GLHostInfo")) {
                                    synchronized (mGLHostInfoList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to GLHostInfoList \n");
                                        mGLHostInfoList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mGLHostInfoList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGLHostInfoList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("gl_extensions_ignore")) {
                                    synchronized (mGLExtensionsIgnoreList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to GLExtensionsIgnoreList \n");
                                        mGLExtensionsIgnoreList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mGLExtensionsIgnoreList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGLExtensionsIgnoreList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("VkHostInfo")) {
                                    synchronized (mVkHostInfoList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to VkHostInfoList \n");
                                        mVkHostInfoList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mVkHostInfoList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mVkHostInfoList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("vk_device_ext_ignore")) {
                                    synchronized (mVKDeviceExtIgnoreList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + ";" + value + " to VKDeviceExtIgnoreList \n");
                                        mVKDeviceExtIgnoreList.put(packageName, value);
                                        if (packageName.startsWith("~")) {
                                            mVKDeviceExtIgnoreList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mVKDeviceExtIgnoreList,pkgException,value);
                                    }
                                }
                                else if (key.equalsIgnoreCase("EGLSurfaceIgnore")) {
                                    synchronized (mEGLSurfaceIgnoreAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mEGLSurfaceIgnoreAppList \n");
                                        mEGLSurfaceIgnoreAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mEGLSurfaceIgnoreAppList \n");
                                            mEGLSurfaceIgnoreAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mEGLSurfaceIgnoreAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("HardKeyBoard")) {
                                    synchronized (mHardKeyBoardAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mHardKeyBoardAppList \n");
                                        mHardKeyBoardAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mHardKeyBoardAppList \n");
                                            mHardKeyBoardAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mHardKeyBoardAppList, pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("iagf")) {
                                    synchronized (mIntelAutoGLFlushDisabledAppList) {
                                        if (value.equalsIgnoreCase("true")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mIntelAutoGLFlushDisabledAppList \n");
                                        mIntelAutoGLFlushDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mIntelAutoGLFlushDisabledAppList \n");
                                            mIntelAutoGLFlushDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mIntelAutoGLFlushDisabledAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("RotateDisabled"))) {
                                    synchronized (mRotateDisabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding packageName :" + packageName + " to mRotateDisabledAppList\n");
                                        mRotateDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mRotateDisabledAppList\n");
                                            mRotateDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mRotateDisabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("UE5PBDisabled")) {
                                    synchronized (mUE5PBDisabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mUE5PBDisabledAppList \n");
                                        mUE5PBDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mUE5PBDisabledAppList \n");
                                            mUE5PBDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mUE5PBDisabledAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("UEEGLCrashFix")) {
                                    synchronized (mUEEGLCrashFixAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mUEEGLCrashFixAppList \n");
                                        mUEEGLCrashFixAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mUEEGLCrashFixAppList \n");
                                            mUEEGLCrashFixAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mUEEGLCrashFixAppList,pkgException);
                                    }
                                }
                                else if ((key.equalsIgnoreCase("bptc"))) {
                                    synchronized (mBptcAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to BptcAppList \n");
                                        mBptcAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from BptcAppList \n");
                                            mBptcAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mBptcAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("extractNativeLibs")) {
                                    synchronized (mExtractNativeLibsAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mExtractNativeLibsAppList \n");
                                        mExtractNativeLibsAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mExtractNativeLibsAppList \n");
                                            mExtractNativeLibsAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mExtractNativeLibsAppList, pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("DefaultXYDpi")) {
                                    synchronized (mDefaultXYDpiAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mDefaultXYDpiAppList \n");
                                        mDefaultXYDpiAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mDefaultXYDpiAppList \n");
                                            mDefaultXYDpiAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mDefaultXYDpiAppList, pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("PScoreAbove")) {
                                    synchronized (mPScoreAboveAppList) {
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mPScoreAboveAppList \n");
                                        int score = Integer.parseInt(value);
                                        mPScoreAboveAppList.put(packageName, score);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mPScoreAboveAppList \n");
                                                mPScoreAboveAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mPScoreAboveAppList, pkgException, score);
                                    }
                                }
                                else if (key.equalsIgnoreCase("GLMBRRO")) {
                                    synchronized (mGlMapBufferRangeReadOnceAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mGlMapBufferRangeReadOnceAppList \n");
                                        mGlMapBufferRangeReadOnceAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mGlMapBufferRangeReadOnceAppList \n");
                                            mGlMapBufferRangeReadOnceAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mGlMapBufferRangeReadOnceAppList,pkgException);
                                    }
                                }
                                else if (key.equalsIgnoreCase("FBCCDisabled")) {
                                    synchronized (mFbCompleteCheckDisabledAppList) {
                                        if (value.equalsIgnoreCase("false")) {
                                            packageName = "~" + packageName;
                                        }
                                        if (DEBUG)
                                            Slog.d(TAG, "Adding " + packageName + " to mFbCompleteCheckDisabledAppList \n");
                                        mFbCompleteCheckDisabledAppList.add(packageName);
                                        if (packageName.startsWith("~")) {
                                            if (DEBUG)
                                                Slog.d(TAG, "Removing " + origPackageName + " from mFbCompleteCheckDisabledAppList \n");
                                            mFbCompleteCheckDisabledAppList.remove(packageName.substring(1));
                                        }
                                        removePackageEntryFromList(mFbCompleteCheckDisabledAppList,pkgException);
                                    }
                                }
                            }
                            // one rule parsing finished, no need to parse other rule..
                            // as more specific rules are above generic rule, if we have applied a more specific rule, do not apply generic rule
                            break;
                        }
                    }
                } else {
                    Slog.d(TAG, "readCustomAppSettings: exception in parsing rules");
                }
            }

            // Let us dump the mNSleepSecList to a persistent file.
            writeMapWithStringValue(bstNSleepFilePath, mNSleepSecList);
            List<String> packages = new ArrayList<String>();

            // Dumping package names for app needed to hide proc maps into a file
            writeTreeSetWithString(bstHideProcMapsFilePath, mHideProcMapsAppList);

            // dump the mModelPropAppList to a persistent file for custom model property read.
            writeMapWithStringValue(bstModelPropFilePath, mModelPropAppList);

            // Dumping package names for app needed fixed promon detection into a file
            writeTreeSetWithString(bstPMFFilePath, mPMFAppList);

            // dump the mXcpuAppList to a persistent file.
            writeTreeSetWithString(bstXcpuApps, mXcpuAppList);

            // dump the mNdkTranslationAppList to a persistent file.
            writeTreeSetWithString(bstNdkTranslationApps, mNdkTranslationAppList);

            // dump the mZeroMappedPageAppList to a persistent file.
            writeTreeSetWithString(bstZeroMappedPageApps, mZeroMappedPageAppList);

            // dump the mZeroMappedPageAppList to a persistent file.
            writeMapWithStringValue(bstMemorySizeApps, mMemorySizeList);

            createMemoryInfoFile(mMemorySizeList);

        } catch (Exception e) {
            Slog.e(TAG, "Exception in trying to parse CustomSizeAppList: " + e);
            e.printStackTrace();
        }
    }

    /*
     * getConfiguration info for the package (reqdversions_gte, build_supported, glmode, dev_model).
     * @hide
     */
    public String getConfigInfoForPackage(String pkg) {
        String result = "Entries not available for this package";
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject();
            jsonObject.put("pkgName", pkg);

            String reqdversion = getRequiredVersion(pkg);
            String build_supported = getBuildsSupported(pkg);
            if (reqdversion != null && build_supported != null) {
                jsonObject.put("reqdversions_gte", reqdversion);
                jsonObject.put("build_supported", build_supported);
            }

            String glmode = getGlMode(pkg);
            if (glmode != null) {
                jsonObject.put("glmode", glmode);
            }

            String dev_model = getDeviceModels(pkg);
            if (dev_model != null) {
                jsonObject.put("dev_model", dev_model);
            }

            String mema = getMemoryAllocatorForPackage(pkg);
            if (mema != null) {
                jsonObject.put("mema", mema);
            }
            boolean blacklistDisabled = false;
            try {
                PackageInfo packageInfo = mPackageManager.getPackageInfo(pkg, PackageManager.GET_CONFIGURATIONS);
                blacklistDisabled = isBlackListed(pkg, packageInfo.versionName)
                    && getBlackListAction(pkg).equalsIgnoreCase("disable");
            } catch (Exception ex) {
                Slog.e(TAG, "Exception: " + ex.getMessage());
                ex.printStackTrace();
                result = "Exception in adding blacklist entry";
            }
            jsonObject.put("blacklistDisabled", blacklistDisabled);

            if (DEBUG) Slog.d(TAG, "result: " + jsonObject.toString());
            return jsonObject.toString();
        } catch (Exception ex) {
            Slog.e(TAG, "Exception:" + ex.getMessage());
            ex.printStackTrace();
            result = "Exception in getConfigInfoForPackage";
        }

        return result;
    }

    /*
     * Load CustomSizeAppList and HeadsetRequiredAppList
     */
    private void loadAppSettingLists()
    {
        /* clear custom size app list */
        synchronized (mCustomSizeAppList)
        {
            mCustomSizeAppList.clear();
        }

        /* clear old HeadsetRequiredAppList */
        synchronized (mHeadsetRequiredAppList)
        {
            mHeadsetRequiredAppList.clear();
        }

        /* clear old MarketRequiredAppList */
        synchronized (mMarketRequiredAppList)
        {
            mMarketRequiredAppList.clear();
        }

        /* clear old Arch specific AppsList */
        synchronized (mCustomArchAppList)
        {
            mCustomArchAppList.clear();
        }

        /* clear old customDpiAppList */
        synchronized (mCustomDpiAppList)
        {
            mCustomDpiAppList.clear();
        }

        /* clear old mFixedScreenOrientationList */
        synchronized (mFixedScreenOrientationList)
        {
            mFixedScreenOrientationList.clear();
        }

        /* clear old mGlv1notimplAppList */
        synchronized (mGlv1notimplAppList)
        {
            mGlv1notimplAppList.clear();
        }

        /* clear old mS3tcAppList */
        synchronized (mS3tcAppList)
        {
            mS3tcAppList.clear();
        }

        /* clear old mFbScreenLockAppList */
        synchronized (mFbScreenLockAppList)
        {
            mFbScreenLockAppList.clear();
        }

        /* clear old mPvrtcAppList */
        synchronized (mPvrtcAppList)
        {
            mPvrtcAppList.clear();
        }

        /* clear old mClearInstallAppList */
        synchronized (mClearInstallAppList)
        {
            mClearInstallAppList.clear();
        }

        /* clear old mAmdInstallAppList */
        synchronized (mAmdInstallAppList)
        {
            mAmdInstallAppList.clear();
        }

        /* clear old mXarchAppList */
        synchronized (mXarchAppList)
        {
            mXarchAppList.clear();
        }

        /* clear old mCameraRotationAngleAppList */
        synchronized (mCameraRotationAngleAppList)
        {
            mCameraRotationAngleAppList.clear();
        }


        /* clear old defaultLandscacpeOrientationList */
        synchronized (defaultLandscacpeOrientationList)
        {
            defaultLandscacpeOrientationList.clear();
        }

        /* clear old forceVibrateList */
        synchronized (mForceVibrateList)
        {
            mForceVibrateList.clear();
        }

        /* clear old HideBstActivityInfoList */
        synchronized (mShowBstActivityInfoList)
        {
            mShowBstActivityInfoList.clear();
        }

        /* clear old ShowDefaultCpuAbiList */
        synchronized (mXpropAbiList)
        {
            mXpropAbiList.clear();
        }


        /* clear old mBluestacksPartnerAppList */
        synchronized (mBluestacksPartnerAppList)
        {
            mBluestacksPartnerAppList.clear();
        }

        /* clear old mFixedDisplayRotationList */
        synchronized (mFixedDisplayRotationList)
        {
            mFixedDisplayRotationList.clear();
        }
        /* clear old mModifyDisplayRotationList */
        synchronized (mModifyDisplayRotationList)
        {
            mModifyDisplayRotationList.clear();
        }
        // clear old SoftKeyboard activities list
        synchronized (mSoftKeyboardActivityList)
        {
            mSoftKeyboardActivityList.clear();
        }
        // clear old SoftKeyboard activities list
        synchronized (mSoftKeyboardActivityListModifier)
        {
            mSoftKeyboardActivityListModifier.clear();
        }
        //clear old GlExtensions list
        synchronized (mAdditionalGlExtensionsList)
        {
            mAdditionalGlExtensionsList.clear();
        }

        // clear old AccelerometerSetting  list
        synchronized (mAccelerometerSettingList)
        {
            mAccelerometerSettingList.clear();
        }

        //clear old GlRendererList
        synchronized (mGlRendererList)
        {
            mGlRendererList.clear();
        }

        //clear old GlVendorList
        synchronized (mGlVendorList)
        {
           mGlVendorList.clear();
        }

        //clear old MemorySizeList
        synchronized (mMemorySizeList)
        {
            mMemorySizeList.clear();
        }

        //clear old Gl3List
        synchronized (mGl3AppList)
        {
           mGl3AppList.clear();
        }

        // clear old mDisableImeActivityList
        synchronized (mDisableImeActivityList)
        {
            mDisableImeActivityList.clear();
        }

        //clear old Libloaderlist
        synchronized (mLibLoaderList)
        {
           mLibLoaderList.clear();
        }

        //clear old ForceDisableGl3AppList
        synchronized (mForceDisableGl3AppList)
        {
           mForceDisableGl3AppList.clear();
        }

        //clear old ZeroMappedPageAppList
        synchronized (mZeroMappedPageAppList)
        {
           mZeroMappedPageAppList.clear();
        }

        //clear old ForcedDexOptInstallFlagList
        synchronized (mForcedDexOptInstallFlagList)
        {
            mForcedDexOptInstallFlagList.clear();
        }

        //clear old mRequiredVersionAppList
        synchronized (mRequiredVersionAppList)
        {
           mRequiredVersionAppList.clear();
        }

        //clear old mBuildSupportedAppList
        synchronized (mBuildSupportedAppList)
        {
           mBuildSupportedAppList.clear();
        }

        //clear old mGlModeAppList
        synchronized (mGlModeAppList)
        {
           mGlModeAppList.clear();
        }

        //clear old mDeviceModelAppList
        synchronized (mDeviceModelAppList)
        {
           mDeviceModelAppList.clear();
        }

        //clear old mPackageMemoryAllocator
        synchronized (mPackageMemoryAllocator)
        {
           mPackageMemoryAllocator.clear();
        }

        //clear old ModifysdPathAppList
        synchronized (mModifysdPathAppList)
        {
           mModifysdPathAppList.clear();
        }

        //clear old AGAGl3List
        synchronized (mAGAGl3AppList)
        {
           mAGAGl3AppList.clear();
        }

        //clear old PGAGl3List
        synchronized (mPGAGl3AppList)
        {
           mPGAGl3AppList.clear();
        }

        //clear old XcpuAppList
        synchronized (mXcpuAppList)
        {
           mXcpuAppList.clear();
        }

        //clear old NdkTranslationAppList
        synchronized (mNdkTranslationAppList)
        {
           mNdkTranslationAppList.clear();
        }

        /* clear old mAstcAppList */
        synchronized (mAstcAppList)
        {
            mAstcAppList.clear();
        }

        //clear old PgaGlVersionList
        synchronized (mPgaGlVersionList)
        {
           mPgaGlVersionList.clear();
        }

        //clear old PgaGlVersionList
        synchronized (mAgaGlVersionList)
        {
           mAgaGlVersionList.clear();
        }

        /* clear old blacklist */
        synchronized (mBlackList)
        {
            /*
             * Load Blacklist (packages not compatible/allowed with our software)
             * File contains the entries in the form - packageName;versionName
             * if * is given at version place, all version of the specific
             * package is not allowed
             * if no version is given, all version of that package is not allowed
             * else if version is starting with ~, all versions except mentioned
             * one is not allowed
             * else if version is specified, that particular version of that app
             * is not allowed
             */
            mBlackList.clear();
        }
        /* clear old blacklist action list */
        synchronized (mBlackListActionList)
        {
            mBlackListActionList.clear();
        }
        /* clear mNSleepSecList*/
        synchronized (mNSleepSecList)
        {
            mNSleepSecList.clear();
        }
        /* clear old mPatchRelocAppList */
        synchronized (mPatchRelocAppList)
        {
            mPatchRelocAppList.clear();
        }

        /* clear old mHardwareDisableAstcAppList */
        synchronized (mHardwareDisableAstcAppList)
        {
            mHardwareDisableAstcAppList.clear();
        }

        /* clear old mPatchRelocAppList */
        synchronized (mPatchRelocAppList)
        {
            mPatchRelocAppList.clear();
        }

        /* clear old mUsingOldHoudiniAppList */
        synchronized (mUsingOldHoudiniAppList)
        {
            mUsingOldHoudiniAppList.clear();
        }

        /* clear old mInputDevicesExposedAppList*/
        synchronized (mInputDevicesExposedAppList)
        {
            mInputDevicesExposedAppList.clear();
        }

        /* clear old mArmAbiListApp*/
        synchronized (mArmAbiListApp)
        {
            mArmAbiListApp.clear();
        }

        /* clear old mIgnoreLargeHeapAppList*/
        synchronized (mIgnoreLargeHeapAppList)
        {
            mIgnoreLargeHeapAppList.clear();
        }

        /* clear old mClearCodeCacheApp*/
        synchronized (mClearCodeCacheApp)
        {
            mClearCodeCacheApp.clear();
        }

        /* clear old mForcedInputDisabledAppList*/
        synchronized (mForcedInputDisabledAppList)
        {
            mForcedInputDisabledAppList.clear();
        }

        /* clear old mImageDetectionEnabledAppList*/
        synchronized (mImageDetectionEnabledAppList)
        {
            mImageDetectionEnabledAppList.clear();
        }

        /* clear old CustomHeapSizeAppList */
        synchronized (mCustomHeapSizeAppList)
        {
            mCustomHeapSizeAppList.clear();
        }

        /* clear old mHideProcMapsAppList*/
        synchronized (mHideProcMapsAppList)
        {
            mHideProcMapsAppList.clear();
        }

        /* clear old mEnableNativeGamePadAppList*/
        synchronized (mEnableNativeGamePadAppList)
        {
            mEnableNativeGamePadAppList.clear();
        }

        /* clear old mPreloadBstHookLibraryAppList*/
        synchronized (mPreloadBstHookLibraryAppList)
        {
            mPreloadBstHookLibraryAppList.clear();
        }

        /* clear old mSuppressedNotificationAppList*/
        synchronized (mSuppressedNotificationAppList)
        {
            mSuppressedNotificationAppList.clear();
        }

        /* clear old mMacrosDisabledAppList*/
        synchronized (mMacrosDisabledAppList)
        {
            mMacrosDisabledAppList.clear();
        }

        /* clear old mShowFeedbackPopupAppList*/
        synchronized (mShowFeedbackPopupAppList)
        {
            mShowFeedbackPopupAppList.clear();
        }

        /* clear old mFfmpegConfidenceAppList*/
        synchronized(mFfmpegConfidenceAppList)
        {
            mFfmpegConfidenceAppList.clear();
        }

        /* clear old mUsingSwappyAppList*/
        synchronized (mUsingSwappyAppList)
        {
            mUsingSwappyAppList.clear();
        }

        /* clear old mMouseCursorStyleAppList*/
        synchronized (mMouseCursorStyleAppList)
        {
            mMouseCursorStyleAppList.clear();
        }

        /* clear old mCameraSensorRotationAppList*/
        synchronized (mCameraSensorRotationAppList)
        {
            mCameraSensorRotationAppList.clear();
        }

        synchronized (mCameraInitRotationAppList)
        {
            mCameraInitRotationAppList.clear();
        }

        synchronized (mUE4GLFlushAppList)
        {
            mUE4GLFlushAppList.clear();
        }

        synchronized (mModelPropAppList)
        {
            mModelPropAppList.clear();
        }

        synchronized (mPMFAppList)
        {
            mPMFAppList.clear();
        }

        synchronized (mGetNowggAccountsList)
        {
            mGetNowggAccountsList.clear();
        }

        synchronized (mGoogleSignInReqdAppList)
        {
            mGoogleSignInReqdAppList.clear();
        }
        synchronized (mMouseActionConfiguredAppList)
        {
            mMouseActionConfiguredAppList.clear();
        }
        synchronized (mVulkanReqdAppList)
        {
            mVulkanReqdAppList.clear();
        }
        synchronized (mForceKillAppList)
        {
            mForceKillAppList.clear();
        }
        synchronized (mGameDefaultSettingAppList)
        {
            mGameDefaultSettingAppList.clear();
        }
        synchronized (mHppDisabledAppList)
        {
            mHppDisabledAppList.clear();
        }
        synchronized (mXperfModeList)
        {
            mXperfModeList.clear();
        }

        synchronized (mHotFixAppList)
        {
            mHotFixAppList.clear();
        }

        synchronized (mGLInvalidateFramebufferFilterAppList)
        {
            mGLInvalidateFramebufferFilterAppList.clear();
        }

        synchronized (mUnityFlickerAppList)
        {
            mUnityFlickerAppList.clear();
        }

        synchronized (mDefaultProfileAppList)
        {
            mDefaultProfileAppList.clear();
        }

        synchronized (mIntelGLDispatchComputeFlushAppList)
        {
            mIntelGLDispatchComputeFlushAppList.clear();
        }

        synchronized (mDhdgList)
        {
            mDhdgList.clear();
        }

        synchronized (mGlNativeSyncDisabledAppList)
        {
            mGlNativeSyncDisabledAppList.clear();
        }

        synchronized (mAngleDisabledAppList)
        {
            mAngleDisabledAppList.clear();
        }

        synchronized (mOneStorePayAppList)
        {
            mOneStorePayAppList.clear();
        }

        synchronized (mGLProgramBinaryDisabledAppList)
        {
            mGLProgramBinaryDisabledAppList.clear();
        }

        synchronized (mGLUnmapBufferPerfDisabledAppList)
        {
            mGLUnmapBufferPerfDisabledAppList.clear();
        }

        //clear old GLShaderWorkaroundList
        synchronized (mGLShaderWorkaroundList)
        {
           mGLShaderWorkaroundList.clear();
        }

        synchronized (mDrmEnabledAppList)
        {
            mDrmEnabledAppList.clear();
        }

        synchronized (mEtherNetTypeAppList)
        {
            mEtherNetTypeAppList.clear();
        }

        synchronized (mTexTargetCheckAppList)
        {
            mTexTargetCheckAppList.clear();
        }

        synchronized (mFixedSurfaceRotationAppList)
        {
            mFixedSurfaceRotationAppList.clear();
        }

        synchronized (mGlMapBufferRangeHostDisabledAppList)
        {
            mGlMapBufferRangeHostDisabledAppList.clear();
        }

        synchronized (mIgnoreSyncTimeoutAppList)
        {
            mIgnoreSyncTimeoutAppList.clear();
        }

        synchronized (mGLVBOCacheDisableAppList)
        {
            mGLVBOCacheDisableAppList.clear();
        }

        synchronized (mBlockEditWhenComposingAppList)
        {
            mBlockEditWhenComposingAppList.clear();
        }

        synchronized (mGLHostInfoList)
        {
           mGLHostInfoList.clear();
        }

        synchronized (mGLExtensionsIgnoreList)
        {
           mGLExtensionsIgnoreList.clear();
        }

        synchronized (mVkHostInfoList)
        {
           mVkHostInfoList.clear();
        }

        synchronized (mVKDeviceExtIgnoreList)
        {
           mVKDeviceExtIgnoreList.clear();
        }

        synchronized (mEGLSurfaceIgnoreAppList)
        {
            mEGLSurfaceIgnoreAppList.clear();
        }

        synchronized (mHardKeyBoardAppList)
        {
            mHardKeyBoardAppList.clear();
        }

        synchronized (mIntelAutoGLFlushDisabledAppList)
        {
            mIntelAutoGLFlushDisabledAppList.clear();
        }

        synchronized (mRotateDisabledAppList)
        {
            mRotateDisabledAppList.clear();
        }

        synchronized (mUnreal5AppMap)
        {
            mUnreal5AppMap.clear();
        }

        synchronized (mUE5PBDisabledAppList)
        {
            mUE5PBDisabledAppList.clear();
        }

        synchronized (mUEEGLCrashFixAppList)
        {
            mUEEGLCrashFixAppList.clear();
        }

        /* clear old mBptcAppList */
        synchronized (mBptcAppList)
        {
            mBptcAppList.clear();
        }

        synchronized (mExtractNativeLibsAppList)
        {
           mExtractNativeLibsAppList.clear();
        }

        synchronized (mDefaultXYDpiAppList)
        {
            mDefaultXYDpiAppList.clear();
        }

        synchronized (mPScoreAboveAppList)
        {
            mPScoreAboveAppList.clear();
        }

        synchronized (mGlMapBufferRangeReadOnceAppList)
        {
            mGlMapBufferRangeReadOnceAppList.clear();
        }

        synchronized (mFbCompleteCheckDisabledAppList)
        {
            mFbCompleteCheckDisabledAppList.clear();
        }

        if(DEBUG) Slog.d(TAG,"loading config Db");

        // Giving 1st preference to /data/downloads/.config_user.db file, is this file is not present then we check for .config.db in /data/downloads/
        // if this file is also not present we read /data/downloads/config.db
        String mConfigFile = ".config_user.db";
        File db = new File( configFilePath + mConfigFile);
        // if file length is less than 100 or db is not a file, we try to delete it and read next file in priority
        // a sample config file with a complete entry has length > 100, so adding a sanity check for the same
        if (!db.isFile() || db.length() < 100) {
            if(DEBUG) Slog.d(TAG,"not reading " + db.toString() + " as file length is " + db.length());
            mConfigFile = ".config.db";
            db = new File(configFilePath + mConfigFile);
            if (!db.isFile() || db.length() < 100) {
                if(DEBUG) Slog.d(TAG,"not reading " + db.toString() + " as file length is " + db.length());
                mConfigFile = "config.db";
                db = new File(configFilePath + mConfigFile);
            }
        }
        SystemProperties.set("bst.config.config_file", mConfigFile);

        readCustomAppSettings(db);
    }

    /* Different list that is used to save user preference for a particular app.
     * Currently, we are using it to determine whether to run the app in portrait mode
     * or in landscape mode forcefully.
     * Cloud config takes priority over user setting i.e. if no cloud configuration
     * is set for a specific app and user selected a custom mode for that app, user's choice
     * will be honored.
     */
    private void loadUserDefinedCustomAppSettings()
    {
        /* Clear old user defined custome size apps list */
        synchronized (mUserDefinedCustomSizeAppList)
        {
            mUserDefinedCustomSizeAppList.clear();
        }

        if (DEBUG) Slog.d(TAG, "Loading user defined custom app settings list");

        Scanner input = null;
        try {
            File db = new File(configFilePath + ".app.settings");

            // If no such file exists, return
            if (!db.exists())
                return;

            input = new Scanner(db);

            while (input.hasNext()) {
                String pkgLine = input.nextLine().trim();
                if (DEBUG) Slog.d(TAG, "Parsing " + pkgLine + " for customAppSettings\n");
                int index = pkgLine.lastIndexOf(';');
                if (index > 0) {
                    String  pkg = pkgLine.substring(0, index);
                    String propValue = pkgLine.substring(index + 1);

                    synchronized (mCustomSizeAppList) {
                        if (mCustomSizeAppList.containsKey(pkg))
                            // Entry already present, just ignore this value.
                            continue;
                    }
                    synchronized (mUserDefinedCustomSizeAppList) {
                        if (propValue.equalsIgnoreCase("full"))
                        {
                            if(DEBUG)  Slog.d(TAG, "Adding " + pkg + " to UserDefinedFullSizeApplist \n");
                            mUserDefinedCustomSizeAppList.put(pkg, PORTRAIT_DISABLED);
                        }
                        else if (propValue.equalsIgnoreCase("small"))
                        {
                            if(DEBUG)  Slog.d(TAG, "Adding " + pkg + " to UserDefinedSmallSizeAppList \n");
                            mUserDefinedCustomSizeAppList.put(pkg, SMALL_SIZE);
                        }
                    }
                }
            }
            input.close();
        } catch (Exception e) {
            Slog.e(TAG, "Exception in trying to parse UserDefinedCustomSizeApplist: " + e.getMessage());
            e.printStackTrace();
            if (input != null)
                input.close();
        }
    }


    /**
     * @hide
     */
    public List<String> getAccelerometerList()
    {

        ArrayList<String> values = new ArrayList<String>();
        synchronized (mAccelerometerSettingList)
        {
            Iterator it = mAccelerometerSettingList.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry entry = (Map.Entry)it.next();
                if(DEBUG) Slog.d(TAG,  entry.getKey() + " = " + entry.getValue());
                String pkgLine = entry.getKey() + ";acceleration=" + entry.getValue();
                values.add(pkgLine);
            }
        }
        return values;
    }

    /**
     * Provide list of apps having headset modifier in config.db
     * @hide
     */
    public List<String> getHeadSetList()
    {
        ArrayList<String> values = new ArrayList<String>();
        synchronized (mHeadsetRequiredAppList)
        {
            for(String pkgLine : mHeadsetRequiredAppList)
            {
                if(DEBUG) Slog.d(TAG,  "entry is for headsetlist: " + pkgLine);
                values.add(pkgLine);
            }
        }
        return values;
    }

    /*
     * Load list of apps installed in arm mode in system.
     */
    private void loadInstalledAbi2AppsList()
    {
        /* Clear old installed arm apps list */
        synchronized (mInstalledArmAppList)
        {
            mInstalledArmAppList.clear();
        }

        /* Clear old xprop apps list*/
        synchronized(mInstalledxABIAppsList)
        {
            mInstalledxABIAppsList.clear();
        }

        /* Clear old xarch apps list*/
        synchronized(mInstalledxARCHAppsList)
        {
            mInstalledxARCHAppsList.clear();
        }

        /* Clear old abilist apps list*/
        synchronized(mInstalledABIListAppsList)
        {
            mInstalledABIListAppsList.clear();
        }

        if (DEBUG) Slog.d(TAG, "Loading abi2 apps list");

        File armDb = new File(bstABI2Apps);
        readInstalledABIAppsListFile(armDb, ARMDBLOAD);

        File xpropDb = new File(bstxABIApps);
        readInstalledABIAppsListFile(xpropDb, XPROPDBLOAD);

        File xarchDb = new File(bstxARCHApps);
        readInstalledABIAppsListFile(xarchDb, XARCHDBLOAD);

        File abilistDb = new File(bstAbilistApps);
        readInstalledABIAppsListFile(abilistDb, ABILISTDBLOAD);
    }

    private void readInstalledABIAppsListFile(File db, int entryMode) {
        Scanner input = null;
        try {
            // if no such file exists, return.
            if (!db.exists())
                return;

            input = new Scanner(db);
            while (input.hasNext()) {
                String entry = input.nextLine().trim();
                String[] parts = entry.split(";");
                if (entryMode == ARMDBLOAD) {
                    synchronized (mInstalledArmAppList) {
                        if (DEBUG) Slog.d(TAG, "Adding entry of " + parts[0] + " with package name : " + parts[1] + " in arm map");
                        mInstalledArmAppList.put(Integer.parseInt(parts[0]), parts[1]);
                    }
                } else if (entryMode == XPROPDBLOAD) {
                    synchronized (mInstalledxABIAppsList) {
                        if (DEBUG) Slog.d(TAG, "Adding entry of " + parts[0] + " with package name : " + parts[1] + " in xprop map");
                        mInstalledxABIAppsList.put(Integer.parseInt(parts[0]), parts[1]); }
                } else if (entryMode == XARCHDBLOAD) {
                    synchronized (mInstalledxARCHAppsList) {
                        if (DEBUG) Slog.d(TAG, "Adding entry of " + parts[0] + " with package name : " + parts[1] + " in xarch map");
                        mInstalledxARCHAppsList.put(Integer.parseInt(parts[0]), parts[1]); }
                } else if (entryMode == ABILISTDBLOAD) {
                    synchronized (mInstalledABIListAppsList) {
                        if (DEBUG) Slog.d(TAG, "Adding entry of " + parts[0] + " with package name : " + parts[1] + " in abilist map");
                        mInstalledABIListAppsList.put(Integer.parseInt(parts[0]), parts[1]); }
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error reading file: " + db.getPath());
            e.printStackTrace();
        } finally {
            try {
                if (input != null)
                    input.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * Monitor changes to whitelist
     */
    private void monitorLists()
    {
        if(DEBUG)  Slog.d(TAG, "Starting the whitelist and other custom lists monitor\n");

        /*
        mObserver = new FileObserver("/data/data/", OBSERVER_EVENTS) {
            public void onEvent(int event, String path) {
            }
        };
        mObserver.startWatching();
        */

        mObserver2 = new FileObserver(configFilePath, OBSERVER_EVENTS) {
            public void onEvent(int event, String path) {
                if (path.equals("whitelist.db")) {
                    if(DEBUG)   Slog.i(TAG, "System Whitelist has been modified.(" + event + ") Reloading ...\n");
                    loadWhitelist();
                }
                else if (path.equals("config.db") || path.equals(".config_user.db") || path.equals(".config.db")) {
                    if(DEBUG) Slog.i(TAG, "App Settings has been modified.(" + event + ") Reloading ...\n");
                    loadAppSettingLists();
                    sendBroadcastListsReload();
                }
                else if (path.equals(".wl.db") || path.equals(".wl_user.db")) {
                    if(DEBUG)     Slog.i(TAG, "Data Whitelist has been modified.(" + event + ") Reloading ...\n");
                    loadWhitelist();
                }
                else if (path.equals(".app.settings")) {
                    if(DEBUG)     Slog.i(TAG, "User defined Custom App Settings has been modified.(" + event + ") Reloading ...\n");
                    loadUserDefinedCustomAppSettings();
                }
                else if (path.equals(".iap_config")) {
                    if(DEBUG)     Slog.i(TAG, "IAP Settings has been modified.(" + event + ") Reloading ...\n");
                    loadIapConfigSettings();
                }
            }
        };
        mObserver2.startWatching();
    }

    private void sendBroadcastListsReload()
    {
        // sending a broadcast,so that apps like BstCommandProcessor can reload their local
        // copies of the list.
        Intent intent = new Intent();
        intent.setAction("BST.FILTER.SERVICE.LISTS_CHANGED");
        Slog.i(TAG, "Lists modified.. Sending Broadcast ...\n");
        mContext.sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }

    /*
     * Load the whitelist and initialise the monitor
     * @hide
     */
    private void initLists()
    {
        if(mListsInited) return;

        if(readFileThread == null) {
            readFileThread = new Thread(() -> {
                Slog.d (TAG, "Trying to initialize whitelist and other custom AppLists\n");
                loadWhitelist();
                loadAppSettingLists();
                loadUserDefinedCustomAppSettings();
                loadIapConfigSettings();
                loadInstalledAbi2AppsList();       //This is to load list of apps installed in arm mode in system.
                monitorLists();

                mListsInited = true;
            });
        }
        //check if list is already initializing and wait for its completion
        if(readFileThread.isAlive()) {
                Slog.d(TAG, "List Initializing right now. Wait\n");
                try {
                    readFileThread.join();
                } catch(InterruptedException e){
                    Slog.e(TAG, "Exception caught while initializing lists " + e);
                }
                return;
        }

        readFileThread.start();
    }

    /**
     * @hide
     */
    public boolean isFbScreenLockApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in FbScreenLock app list\n");
        initLists();

        synchronized (mFbScreenLockAppList)
        {
            if (mFbScreenLockAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mFbScreenLockAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in FbScreenLock App List\n");
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Our primitive pvrtc app  check
     * @hide
     */
    public boolean isPvrtcApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in pvrtc app list\n");
        initLists();

        synchronized (mPvrtcAppList)
        {
            if (mPvrtcAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mPvrtcAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in Pvrtc App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isS3tcApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in s3tc app list\n");
        initLists();

        synchronized (mS3tcAppList)
        {
            if (mS3tcAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mS3tcAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in S3tc App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * If bst.config.isamd is true and ;amd is present, then
     * install this app as an arm app
     */

    private boolean isAmdInstallApp(String pkgName)
    {

        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in AmdInstall package list if it is an amd machine\n");
        initLists();

        // exit rightaway, if its not am amd machine (;amd doesn't matter then).
        if (isAmdMachine == 0)
        {
            if (DEBUG) Slog.d(TAG, "not an Amd machine, so returning false");
            return false;
        }

        synchronized (mAmdInstallAppList)
        {
            if (mAmdInstallAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mAmdInstallAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isClearInstallApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in clearInstall package list\n");
        initLists();

        synchronized (mClearInstallAppList)
        {
            if (mClearInstallAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mClearInstallAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Our primitive glv1notimp check
     * @hide
     */
    public boolean isGlv1notimpApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in glv1notimp package list\n");
        initLists();

        synchronized (mGlv1notimplAppList)
        {
            if (mGlv1notimplAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGlv1notimplAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Our primitive whitelist check
     * @hide
     */
    public boolean isWhiteListed(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in allowed package list\n");
        initLists();

        synchronized (mWhiteList)
        {
            if ((mWhiteList.isEmpty()) || (mWhiteList.contains(pkgName)))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mWhiteList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Our primitive blacklist check
     * @hide
     */
    public boolean isBlackListed(String pkg, String version)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkg + ";" + version + " in  blacklist");
        if (pkg == null || version == null) {
            Slog.e(TAG, "Invalid arguments to isBlackListed, pkg = " + pkg + " version = " + version);
            return false;
        }

        initLists();
        boolean invertFlag = false;
        boolean retval = false;
        synchronized(mBlackList)
        {
            if (!mBlackList.containsKey(pkg))
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mBlackList, pkg);
                if( wildcardMatchedPackageName == null)
                    return false;
                else
                    pkg = wildcardMatchedPackageName;
            }

            String storedVersions = mBlackList.get(pkg);
            if (storedVersions.charAt(0) == '~') {
                invertFlag = true;
                storedVersions = storedVersions.substring(1);
            }
            for (String storedVersion : storedVersions.split(";")) {
                String matched = isRegexMatch(storedVersion, version);
                if(matched != null && matched.length() > 0) {
                    retval = true;
                    break;
                }
            }

        }
        retval = invertFlag ? !retval : retval;
        if (DEBUG) Slog.d(TAG, "isBlacklisted: pkg=" + pkg + " version=" + version + " returning " + retval);
        return retval;
    }

    /**
     * Our primitive blacklist action check
     * @hide
     */
    public String getBlackListAction(String pkg)
    {
        String action = "";
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkg  + " in  blacklist action list");
        if (pkg == null) {
            Slog.e(TAG, "Invalid arguments to isBlackListed, pkg = " + pkg);
            return action;
        }

        initLists();

        synchronized (mBlackListActionList)
        {
            if (!mBlackListActionList.containsKey(pkg))
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mBlackListActionList, pkg);
                if(wildcardMatchedPackageName == null)
                    return action;
                else
                    pkg = wildcardMatchedPackageName;
            }

            action = mBlackListActionList.get(pkg);
        }
        return action;
    }
    /**
     * Our primitive blacklist check
     * @hide
     */
    public boolean isSoftKeyboardRequired(String pkg, String activity)
    {
        boolean res = false;
        int index = -1;

        if (pkg == null || activity == null) {
            Slog.e(TAG, "Invalid arguments to isSoftKeyboardRequired, pkg = " + pkg + " activity = " + activity);
            return false;
        }

        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkg + ";" + activity + " in  softkeyboard_activity list\n");
        initLists();
        synchronized(mSoftKeyboardActivityList)
        {
            if (!mSoftKeyboardActivityList.containsKey(pkg))
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mSoftKeyboardActivityList, pkg);
                if( wildcardMatchedPackageName == null)
                    return false;
                else
                    pkg = wildcardMatchedPackageName;
            }

            String flattenCurrentActivity = activity.trim();
            index = activity.indexOf('/');
            if (index != -1)
            {
                if (DEBUG) Slog.d(TAG, " orig activity: " + activity + " pkg: " + activity.substring(0, index) + " activity: " + activity.substring(index+1));
                flattenCurrentActivity = new StringBuilder().append(activity.substring(0,index)).append(activity.substring(index+1)).toString();
            }

            //ROB-10786: Let the soft configuration item support multiple activities, and each activity is separated by a comma
            String values = mSoftKeyboardActivityList.get(pkg);
            String[] ativityList = values.split(",");
            if (ativityList != null) {
                for (int i = 0; i < ativityList.length; i++) {
                    String storedActivity = ativityList[i];
                    index = storedActivity.indexOf('/');
                    if (index != -1)
                    {
                        if (DEBUG) Slog.d(TAG, " orig stored activity: " + storedActivity + " pkg: " + storedActivity.substring(0, index) + " activity: " + storedActivity.substring(index+1));
                        storedActivity = new StringBuilder().append(storedActivity.substring(0,index)).append(storedActivity.substring(index+1)).toString();
                    }
                    if (DEBUG) Slog.d(TAG, "storedActivity : " + storedActivity + " orig activity: " + activity + " flattenCurrentActivity : " + flattenCurrentActivity);
                    if (storedActivity.equals("*"))
                        res = true;
                    else if (storedActivity.equalsIgnoreCase(activity))
                        res = true;
                    else if (storedActivity.equalsIgnoreCase(flattenCurrentActivity))
                        res = true;
                    if (res)
                        break;
                }
            }

        }
        if (DEBUG) Slog.d(TAG, "isSoftKeyboardRequired(" + pkg + "," + activity + ") returning " + res);
        return res;
    }

    /**
     * Our primitive softkeyboard_activity_modifier check
     * @hide
     */
    public String getSoftKeyboardModifier(String pkg)
    {
        String res = "soft";

        if (pkg == null) {
            Slog.e(TAG, "invalid arguments to getSoftKeyboardModifier, pkg = " + pkg);
            return res;
        }

        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkg + " in getSoftKeyboardModifier");
        initLists();

        synchronized(mSoftKeyboardActivityListModifier)
        {
            if (!mSoftKeyboardActivityListModifier.containsKey(pkg))
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mSoftKeyboardActivityListModifier,pkg);
                if (wildcardMatchedPackageName == null)
                    return res;
                else
                    pkg = wildcardMatchedPackageName;
            }
            res = mSoftKeyboardActivityListModifier.get(pkg);
        }

        if (DEBUG) Slog.d(TAG, "in getSoftKeyboardModifier for " + pkg + " returning " + res);
        return res;
    }

    public String getAdditionalGlExtensions(String pkg)
    {
        String res = "";

        if (pkg == null) {
            Slog.w(TAG, "pkgname for getAdditionalGlExtensions is null, setting to foobar");
            pkg = "foobar";
        }

        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkg + "in mAdditionalGlExtensionsList");
        initLists();

        synchronized (mAdditionalGlExtensionsList)
        {
            if (!mAdditionalGlExtensionsList.containsKey(pkg))
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mAdditionalGlExtensionsList, pkg);
                if (wildcardMatchedPackageName == null)
                    return res;
                else
                    pkg = wildcardMatchedPackageName;
            }
            res = mAdditionalGlExtensionsList.get(pkg);
        }

        if (DEBUG) Slog.d(TAG, "in getAdditionalGlExtensions for " + pkg + " returning " + res);
        return res;
    }


    /**
     * Our primitive check for applications to determine whether they need google market or not to run properly.
     * @hide
     */
    public boolean isMarketRequired(String pkgName)
    {
        boolean found = false;

        if (pkgName == null)
        {
            Slog.e(TAG, "invalid arguments to isMarketRequired, pkgName = " + pkgName);
            return found;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in MarketRequired AppList\n");
        initLists();

        synchronized (mMarketRequiredAppList)
        {
            if (mMarketRequiredAppList.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " required Market App to work smoothly\n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mMarketRequiredAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " is in portrait App List\n");
                    found = true;
                }
            }
        }

        return found;
    }

    /**
     * Our primitive check for applications to determine whether they need headset plugged in or not.
     * @hide
     */
    public boolean isHeadsetRequired(String pkgName)
    {
        boolean found = false;

        if (pkgName == null)
        {
            Slog.e(TAG, "invalid arguments to isHeadsetRequired, pkgName = " + pkgName);
            return found;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in HeadsetRequired AppList\n");
        initLists();

        synchronized (mHeadsetRequiredAppList)
        {
            if (mHeadsetRequiredAppList.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " required Headset plugged in to work smoothly\n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mHeadsetRequiredAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " required Headset plugged in to work smoothly\n");
                    found = true;
                }
            }
        }

        return found;
    }

    /**
     * Our primitive check for portrait applications, whether to run in portrait mode or not.
     * @hide
     */
    public boolean isPortraitDisabled(String pkgName)
    {
        boolean found = false;
        if (pkgName == null)
        {
            Slog.e(TAG, "invalid arguments to isPortraitDisabled, pkgName = " + pkgName);
            return found;
        }

        initLists();

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in portrait disabled List\n");
        synchronized (mCustomSizeAppList)
        {
            if (mCustomSizeAppList.containsKey(pkgName))
            {
                if (mCustomSizeAppList.get(pkgName) == PORTRAIT_DISABLED)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " is in Full Screen App list\n");
                    found = true;
                }
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomSizeAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (mCustomSizeAppList.get(wildcardMatchedPackageName) == PORTRAIT_DISABLED)
                    {
                        if(DEBUG) Slog.d(TAG, pkgName + " is in Full Screen App list\n");
                        found = true;
                    }
                }
            }
        }
        // If no cloud config found for this particular app, check user defined custom size app list
        if (!found) {
            synchronized (mUserDefinedCustomSizeAppList)
            {
                if (mUserDefinedCustomSizeAppList.containsKey(pkgName))
                {
                    if(mUserDefinedCustomSizeAppList.get(pkgName) == PORTRAIT_DISABLED) {
                        if(DEBUG) Slog.d(TAG, pkgName + " is in User Defined Full Screen App List\n");
                        found = true;
                    }
                }
            }
        }

        return found;
    }

    /**
     * @hide
     */
    public boolean isXarchApp(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in Xarch App List.    mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid: " + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);
        synchronized (mXarchAppList)
        {
            if (mXarchAppList.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " is in Xarch App List\n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mXarchAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in Xarch App List\n");
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * @hide
     */
    public boolean isXarchApp2(String pkgName)
    {
        boolean found = false;

        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to isXCpuAbi, pkgName: " + pkgName);
            return found;
        }

        initLists();

        synchronized(mXarchAppList)
        {
            if (mXarchAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, "pkg: " + pkgName + " found in mXarchAppList");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mXarchAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * @hide
     */
    public boolean forceLandscapeOrientationIfUnspecified(String packageName)
    {
        boolean found = false;
        initLists();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + packageName + " in defaultLandscapeOrientationList.");
        if (packageName == null)
        {
            Slog.e(TAG, "invalid arguments to forceLandscapeOrientationIfUnspecified, pkgName = " + packageName);
            return found;
        }

        synchronized (defaultLandscacpeOrientationList)
        {
            if (defaultLandscacpeOrientationList.contains(packageName))
            {
                if(DEBUG) Slog.d(TAG, packageName + " is in defaultLandscacpeOrientationList \n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(defaultLandscacpeOrientationList,packageName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, packageName + " is in defaultLandscacpeOrientationList \n");
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * @hide
     */
    public String getCustomDpi(String pkgName)
    {
        String res = "";
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in custome DPI App list\n");
        if (pkgName == null)
        {
            Slog.e(TAG, "invalid arguments to custome DPI App, pkgName = " + pkgName);
            return res;
        }

        initLists();

        synchronized (mCustomDpiAppList)
        {
            if (mCustomDpiAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in custome DPI App List\n");
                res = mCustomDpiAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomDpiAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in custome DPI App List\n");
                    res = mCustomDpiAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * This function takes callingPkgName and currentPkgName as arguments and returns true or false
     * If callingPkgName is present in mFixedScreenOrientationList and currentPkgName in present as value in
     * that list then it returns true, this means we want to fix the orientation of currentPkgName as that of
     * callingPkgName
     * @hide
     */
    public boolean isScreenOrientationFixed(String callingPkgName, String currentPkgName)
    {
        boolean found = false;
        String matchedCallingPkgName = null;
        if(DEBUG) Slog.d(TAG, " Trying to look for " + callingPkgName + " currentPkgName " +currentPkgName +"  in Screenorientation App List\n");
        if (callingPkgName == null || currentPkgName == null)
        {
            Slog.e(TAG, "invalid arguments to isScreenorientationFixed, callingPkgName = " + callingPkgName + " currentPkgName = " +currentPkgName);
            return found;
        }

        initLists();

        synchronized (mFixedScreenOrientationList)
        {
            if (mFixedScreenOrientationList.containsKey(callingPkgName))
            {
                matchedCallingPkgName = callingPkgName;
            }
            else
            {
                matchedCallingPkgName = isPackageMatchedWilcard(mFixedScreenOrientationList,callingPkgName);
            }

            if (matchedCallingPkgName != null) {
                if(DEBUG) Slog.d(TAG, callingPkgName + "--matched to: "+matchedCallingPkgName +" in Screenorientation App List\n");
                String values = mFixedScreenOrientationList.get(matchedCallingPkgName);
                String[] parts = values.split(",");
                for(int i = 0; i < parts.length; i++) {
                    if (isRegexMatch(parts[i],currentPkgName) != null) {
                        found = true;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Check for applications to determine whether they need camera rotation or not .
     * @hide
     */
    public int isCameraRotationRequired(String pkgName)
    {
        int rotationAngle = 0;
        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to isCameraRotationRequired, pkgName = " + pkgName);
            return rotationAngle;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in CameraRotationAngle AppList\n");
        initLists();

        synchronized (mCameraRotationAngleAppList)
        {
            if (mCameraRotationAngleAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " requires camera rotation to  work properly");
                rotationAngle = mCameraRotationAngleAppList.get(pkgName);
                if(DEBUG) Slog.d(TAG,"The rotation Angle for package: " + pkgName + " is : " + rotationAngle);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCameraRotationAngleAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " requires camera rotation to work properly");
                    rotationAngle = mCameraRotationAngleAppList.get(wildcardMatchedPackageName);
                    if(DEBUG) Slog.d(TAG,"The rotation Angle for package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + rotationAngle);

                }
            }
        }

        return rotationAngle;
    }

    /**
     * Check for applications for which screen needs to be vibrated irrespective of they are in front or not.
     * @hide
     */
    public boolean isForceVibrate(String pkgName) {
        boolean forceVibrate = false;
        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to forceVibrate, pkgName = " + pkgName);
            return forceVibrate;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in forceVibrate AppList\n");
        initLists();

        synchronized (mForceVibrateList)
        {
            if (mForceVibrateList.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " found in forceVibrate AppList");
                forceVibrate = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mForceVibrateList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in forceVibrate App List\n");
                    forceVibrate = true;
                }
            }
        }

        return forceVibrate;
    }

    // Bug 6203 Pocket Knights crashes on launch.
    // Some apps try to query packageManager for intentActivities to determine if it is
    // running on bluestacks.
    // This call is to determine if the package is the one from which we need to hide the
    // activity info of bluestacks packages.
    /**
     * @hide
     */
    public boolean isHideBstActivityInfo(String pkgName)
    {
        boolean found = true;

        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to isHideBstActivityInfo, pkgName: " + pkgName);
            return found;
        }

        initLists();

        synchronized(mShowBstActivityInfoList)
        {
            if (mShowBstActivityInfoList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, "pkg: " + pkgName + " found in hidebstactivityinfo list");
                found = false;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mShowBstActivityInfoList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    found = false;
                }
            }
        }
        return found;
    }

    // This list is to populate the apps which if installed in arm mode, we want to show the default
    // system cpu abi build properties. Else they crash when returning {armeabi-v7a - armeabi}.
    // ex. com.netease.TCYM.uc, com.zmxyol.union.baidu, com.youzu.wzqj.ad.baidu.
    /*
     * @hide
     */
    public boolean isXCpuAbi(String pkgName)
    {
        boolean found = false;

        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to isXCpuAbi, pkgName: " + pkgName);
            return found;
        }

        initLists();

        synchronized(mXpropAbiList)
        {
            if (mXpropAbiList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, "pkg: " + pkgName + " found in mXpropAbiList");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mXpropAbiList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * Our primitive isBluestacksPartnerApp check
     * @hide
     */
    public boolean isBluestacksPartnerApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in BluestacksPartnerApp list\n");
        initLists();

        synchronized (mBluestacksPartnerAppList)
        {
            if (mBluestacksPartnerAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in BluestacksPartnerApp List\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mBluestacksPartnerAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in BluestacksPartnerApp List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Our getAccelerometerSetting check
     * @hide
     */

    public String getAccelerometerSetting(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in Accelerometer list\n");
        initLists();

        synchronized (mAccelerometerSettingList)
        {
            if (mAccelerometerSettingList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in AccelerometerSettingList List\n");
                return mAccelerometerSettingList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mAccelerometerSettingList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in AccelerometerSettingList List\n");
                    return mAccelerometerSettingList.get(wildcardMatchedPackageName);
                }
            }
        }
        return null;
    }

    /**
     * Our getInstallerPkg check
     * @hide
     */
    public String getInstallerPkg(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in getInstallerPkg list\n");
        initLists();

        synchronized (mInstallerPackageList)
        {
            if (mInstallerPackageList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in getInstallerPkg List\n");
                return mInstallerPackageList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mInstallerPackageList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in getInstallerPkg List\n");
                    return mInstallerPackageList.get(wildcardMatchedPackageName);
                }
            }
        }
        return null;
    }

    /**
     * getGlRenderer value for app
     * @hide
     */
    public String getGlRenderer(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GlRenderer list\n");
        initLists();
        String res = "";

        synchronized (mGlRendererList)
        {
            if (mGlRendererList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in GlRenderer List\n");
                res = mGlRendererList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGlRendererList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GlRenderer List\n");
                    res = mGlRendererList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * getGlVendor value for app
     * @hide
     */
    public String getGlVendor(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GlVendor list\n");
        initLists();
        String res = "";
        synchronized (mGlVendorList)
        {
            if (mGlVendorList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in GlVendor List\n");
                res = mGlVendorList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGlVendorList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GlVendor List\n");
                    res = mGlVendorList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * get default URL for Browser app
     * @hide
     */
    public String getBrowserUrl()
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for URL for Browser \n");
        initLists();
        String res = "";
        res=mBrowserUrl;
        return res;
    }

    /**
     * getMemorySize value for app and prepare file to be mounted to /proc/meminfo
     * @hide
     */
    public String getMemorySize(int uid)
    {
        String res = "";
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in MemorySize App List.");
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning empty value");
            return res;
        }
        String pkgName = str[0];
        if (DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);
         synchronized (mMemorySizeList)
        {
            if (mMemorySizeList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in MemorySize List\n");
                res = mMemorySizeList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mMemorySizeList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in MemorySize List\n");
                    res = mMemorySizeList.get(wildcardMatchedPackageName);
                }
            }
        }

        if (res.length() > 0) {

             String resKb = Long.toString(Long.parseLong(res)/1024);
             File newMemFile = new File("/data/downloads/.tmp/xmeminfo." + res);
             File origMemFile = new File("/etc/xmeminfo");
             try {

                 if (newMemFile.getParentFile()==null)
                     newMemFile.getParentFile().mkdirs();

                 if (newMemFile.exists() || !origMemFile.exists()) {
                     return res;
                 }
                 if (!newMemFile.exists()) {
                     newMemFile.createNewFile();
                     if (FileUtils.setPermissions(newMemFile,
                            FileUtils.S_IRUSR | FileUtils.S_IWUSR |
                            FileUtils.S_IRGRP | FileUtils.S_IROTH | FileUtils.S_IWGRP | FileUtils.S_IWOTH, -1, -1) != 0) {
                         Slog.e(TAG, "Failed to change permissions for the file");
                     }

                     BufferedReader reader = new BufferedReader(new FileReader(origMemFile));
                     String line = "", oldtext = "";
                     while((line = reader.readLine()) != null)
                     {
                         oldtext += line + "\r\n";
                     }
                     reader.close();

                     String oldmem = "        1817980";
                     String newMem = String.format("%1$"+oldmem.length()+ "s", resKb);
                     String newtext = oldtext.replaceAll("MemTotal:        1817980 kB", "MemTotal:"+ newMem + " kB");

                     FileWriter writer = new FileWriter(newMemFile);
                     writer.write(newtext);
                     writer.close();
                 }
             }
             catch (Exception e) {
                 Slog.w(TAG,"Exception in copying file " + origMemFile + " to " + newMemFile);
                 e.printStackTrace();
             }

        }
        return res;
   }


    /**
     * Check if app is GL3 specific or not
     * @hide
     */
    public boolean isGL3App(String pkgName)
    {
        boolean res = false;
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in Gl3App list\n");
        initLists();

        synchronized (mGl3AppList)
        {
            if (mGl3AppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in Gl3App List\n");
                res = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGl3AppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in Gl3App List\n");
                    res = true ;
                }
            }
        }
        return res;
    }


    /**
     * Check if GL3 is disabled for this app or not
     * @hide
     */
    public boolean isGL3Disabled(String pkgName)
    {
        boolean res = false;
        if (DEBUG)
            Slog.d(TAG, "Trying to look for " + pkgName + " in forceDisableGl3App list\n");
        initLists();

        synchronized (mForceDisableGl3AppList)
        {
            if (mForceDisableGl3AppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in forceDisableGl3App List\n");
                res = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mForceDisableGl3AppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in forceDisableGl3App List\n");
                    res = true ;
                }
            }
        }
        return res;
    }

    /**
     * Case 12502: Returning alternate native library path of app with given uid to be loaded
     * via houdini This fixes crash seen in app com.gmt.gpdaybreak.
     * @hide
     */
    public String isLoadLibHackReqd(int uid)
    {
        String res = "";
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in Libloader App List.   mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return res;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);
         synchronized (mLibLoaderList)
        {
            if (mLibLoaderList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in Libloader List\n");
                res = mLibLoaderList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mLibLoaderList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in Libload List\n");
                    res = mLibLoaderList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
   }

   /**
    * Check if this app requires zero page to be mapped for working properly.
    * Some of the apps like BlackDesert M(com.pearlabyss.blackdesertm), frxx(com.gameplay.frxx.android) are crashing in translator but
    * with this hack seems to be working fine.
    * @hide
    * */
    public boolean isZeroMappedPageReqd(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in ZeroMappedPage App List. mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + ", found package :" + pkgName);
        synchronized (mZeroMappedPageAppList)
        {
            if (mZeroMappedPageAppList.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " is in ZeroMappedPage App List");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mZeroMappedPageAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in ZeroMappedPage App List");
                    found = true;
                }
            }
        }
        return found;
    }


    /**
     * Apps like Case 14080,12660 takes time to extract its data in sdcard folder, so now
     * we check if sdcard path need to modify for app
     * @hide
     */
    public boolean isModifysdPathReqd(String pkgName)
    {
        boolean res = false;
        if (DEBUG)
            Slog.d(TAG, "Trying to look for " + pkgName + " in ModifysdPathApp list\n");
        initLists();

        synchronized (mModifysdPathAppList)
        {
            if (mModifysdPathAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in ModifysdPathApp List\n");
                res = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mModifysdPathAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in ModifysdPathApp List\n");
                    res = true ;
                }
            }
        }
        return res;
    }

    /**
     * Check if  AGAGL3 is disabled for app or not
     * @hide
     */
    public boolean isAGAGL3Disabled(String pkgName)
    {
        boolean res = false;
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in AGAGl3App list\n");
        initLists();

        synchronized (mAGAGl3AppList)
        {
            if (mAGAGl3AppList.contains(pkgName) )
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in AGAGl3App List\n");
                res = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mAGAGl3AppList, pkgName);

                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in AGAGl3App List\n");
                    res = true ;
                }
            }
        }
        return res;
    }


    /**
     * Check if PGAGL3 is disabled for app or not
     * @hide
     */
    public boolean isPGAGL3Disabled(String pkgName)
    {
        boolean res = false;
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in PGAGl3App list\n");
        initLists();

        synchronized (mPGAGl3AppList)
        {
            if (mPGAGl3AppList.contains(pkgName) )
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in PGAGl3App List\n");
                res = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mPGAGl3AppList, pkgName);

                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in PGAGl3App List\n");
                    res = true ;
                }
            }
        }
        return res;
    }
    /**
     * This would check if the entry of the package and it's activity is present in the list.
     * If present then iskeyboardenabled call to windows in InputMethodManagerService will not be sent.
     * possible entries:
     * 1. pkgName : "*"                   #iskeyboardenabled call will not be sent for any activity of the package.
     * 2. pkgName : "activity1,activity2" #the call will be blocked for only the specified activities i.e. activity1 and activity2.
     * 3. pkgName : "~activity"           #the call will be blocked for all the activites except one in the list.
     * @hide
     */
    public boolean isIMEDisabled(String pkg, String activity)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkg + ";" + activity + " in  mDisableImeActivityList");
        if (pkg == null) {
            Slog.e(TAG, "Invalid arguments to mDisableImeActivityList, pkg = " + pkg + " activity = " + activity);
            return false;
        }

        initLists();

        activity = activity + ";";
        synchronized(mDisableImeActivityList)
        {
            if (!mDisableImeActivityList.containsKey(pkg))
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mDisableImeActivityList, pkg);
                if( wildcardMatchedPackageName == null)
                    return false;
                else
                    pkg = wildcardMatchedPackageName;
            }

            String storedActivities = mDisableImeActivityList.get(pkg);
            if (storedActivities.equals("*;"))
                return true;

            if (activity.equals(";"))
                    return false;

            if (storedActivities.charAt(0) == '~')
            {
                if(!storedActivities.substring(1).contains(activity))
                    return true;
            }
            else if (storedActivities.contains(activity))
                return true;

        }
        return false;
    }

    /**
     * get the minimum appplayer version that is required to run the app.
     * @hide
     */
    private String getRequiredVersion(String pkgName) {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mRequiredVersionAppList\n");
        initLists();
        String res = null;

        synchronized (mRequiredVersionAppList)
        {
            if (mRequiredVersionAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mRequiredVersionAppList\n");
                res = mRequiredVersionAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mRequiredVersionAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mRequiredVersionAppList\n");
                    res = mRequiredVersionAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * get the build (N, KK, *) on which the app runs.
     * @hide
     */
    private String getBuildsSupported(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mBuildSupportedAppList\n");
        initLists();
        String res = null;

        synchronized (mBuildSupportedAppList)
        {
            if (mBuildSupportedAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mBuildSupportedAppList\n");
                res = mBuildSupportedAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mBuildSupportedAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mBuildSupportedAppList\n");
                    res = mBuildSupportedAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * get the glmodes on which the apps runs (gl, dx, agl)
     * @hide
     */
    private String getGlMode(String pkgName)
    {
        if (DEBUG) Slog.d(TAG , "Trying to look for " + pkgName + " in mGlModeAppList\n");
        String res = null;

        synchronized (mGlModeAppList)
        {
            if (mGlModeAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mGlModeAppList\n");
                res = mGlModeAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGlModeAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mGlModeAppList\n");
                    res = mGlModeAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * get the device models on which the app runs.
     * @hide
     */
    private String getDeviceModels(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mDeviceModelAppList\n");
        initLists();
        String res = null;

        synchronized (mDeviceModelAppList)
        {
            if (mDeviceModelAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mDeviceModelAppList\n");
                res = mDeviceModelAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mDeviceModelAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mDeviceModelAppList\n");
                    res = mDeviceModelAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * returns the memory allocator using which the app runs fine.
     * @hide
     */
    private String getMemoryAllocatorForPackage(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mPackageMemoryAllocator\n");
        initLists();
        String res = null;

        synchronized (mPackageMemoryAllocator)
        {
            if (mPackageMemoryAllocator.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mPackageMemoryAllocator\n");
                res = mPackageMemoryAllocator.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mPackageMemoryAllocator, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mPackageMemoryAllocator\n");
                    res = mPackageMemoryAllocator.get(wildcardMatchedPackageName);
                }
            }
        }
        if (res != null && !res.trim().equals("jem") && !res.trim().equals("dlm")) {
            Slog.w(TAG, "The memory allocator is set to some unknown value: " + res);
            res = null;
        }
        return res;
    }

    public boolean isFixedDisplayRotationApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in FixedDisplayRotation app list\n");
        initLists();

        synchronized (mFixedDisplayRotationList)
        {
            if (mFixedDisplayRotationList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mFixedDisplayRotationList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in FixedDisplayRotation App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Bug 8230: com.icantw.wing can't click in game.
     * The issue seen is clickListeners/touchListeners in game does not respond to clicks on view. This app queries defaultDisplays
     * rotation to determine default orientation of the device. Since we return rotation as ROTATION_0 but with Display width x greater
     * than Display height y. Based on these values, the app calculates default Landscape orientation which results in click not working.
     * By default, this calculation results in portrait as default orientation since rotation retrieved is ROTATION_90/270.
     * Modifying ROTAION_0 to 90/270 for such apps which have entry of ;rotated in settings.
     * @hide
     */
    public boolean isModifyDisplayRotationApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in ModifyDisplayRotation app list\n");
        initLists();

        synchronized (mModifyDisplayRotationList)
        {
            if (mModifyDisplayRotationList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mModifyDisplayRotationList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in ModifyDisplayRotation App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /* Dump entries in a given Maplist */
    private void dumpMap(Map mp) {
        if(DEBUG) Slog.d(TAG, "In dumpMap function\n");
        synchronized (mp)
        {
            Iterator it = mp.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry)it.next();
                if(DEBUG) Slog.d(TAG,  entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    // mInstalledArmAppList - this list is loaded once, update this in memory as an when arm apps are installed/removed.
    // mXpropAbiList - this list is static, since it is controlled via config.db
    // mInstalledxABIAppsList - this list is loaded once, update this in memory as an when xprop entry is modified for an app.
    // mInstalledxARCHAppsList - this list is loaded once, update this in memory as an when xarch entry is modified for an app.
    // mInstalledABIListAppsList - this list is loaded once, update this in memory as an when abilist entry is modified for an app.
    // If this API is called during APP_INSTALL/APP_REMOVED action. The arguments passed are the package uid, name and action
    // to be performed.
    // The action list is as below:
    // ADD_ABI2_ENTRY - arm abi install, update both lists.
    // REMOVE_ABI2_ENTRY - app removed/abi change install, update both lists.
    /*
     * @hide
     */
    public void updateAbiEntry(int uid, String packageName, int action) {
        boolean modifyxAbiEntry = false;
        boolean modifyxArchEntry = false;
        boolean modifyArmAbiListEntry = false;

        // some sanity checks
        if (uid < 10000 || packageName == null || packageName.isEmpty()) {
            Slog.e(TAG, "invalid entries to be updated uid: " + uid + " and package: " + packageName + " , skipping it");
        }

        uid = mapUid(uid);
        if (action == ADD_ABI2_ENTRY) {
            // Check is uid entry already present in arm list.
            synchronized (mInstalledArmAppList) {
                if (!mInstalledArmAppList.containsKey(uid))
                    mInstalledArmAppList.put(uid, packageName);
            }
            // Check if we need to update xprop entry for this install.
            if (isXCpuAbi(packageName)) {
                synchronized (mInstalledxABIAppsList) {
                    if (!mInstalledxABIAppsList.containsKey(uid)) {
                        modifyxAbiEntry = true;
                        mInstalledxABIAppsList.put(uid, packageName);
                    }
                }
            } else {
                synchronized (mInstalledxABIAppsList) {
                    if (mInstalledxABIAppsList.containsKey(uid)) {
                        modifyxAbiEntry = true;
                        mInstalledxABIAppsList.remove(uid);
                    }
                }
            }
            if (isXarchApp2(packageName)) {
                synchronized (mInstalledxARCHAppsList) {
                    if (!mInstalledxARCHAppsList.containsKey(uid)) {
                        modifyxArchEntry = true;
                        mInstalledxARCHAppsList.put(uid, packageName);
                    }
                }
            } else {
                synchronized (mInstalledxARCHAppsList) {
                    if (mInstalledxARCHAppsList.containsKey(uid)) {
                        modifyxArchEntry = true;
                        mInstalledxARCHAppsList.remove(uid);
                    }
                }
            }
            if (isShowArmAbiListApp2(packageName)) {
                synchronized (mInstalledABIListAppsList) {
                    if (!mInstalledABIListAppsList.containsKey(uid)) {
                        modifyArmAbiListEntry = true;
                        mInstalledABIListAppsList.put(uid, packageName);
                    }
                }
            } else {
                synchronized (mInstalledABIListAppsList) {
                    if (mInstalledABIListAppsList.containsKey(uid)) {
                        modifyArmAbiListEntry = true;
                        mInstalledABIListAppsList.remove(uid);
                    }
                }
            }
        } else if (action == REMOVE_ABI2_ENTRY) {
            // check for uid entry in arm list
            synchronized (mInstalledArmAppList) {
                if (mInstalledArmAppList.containsKey(uid))
                    mInstalledArmAppList.remove(uid);
            }
            // Check for xprop entry of this result
            // Update ONLY the mInstalledxABIAppsList,
            // DO NOT update mXpropAbiList as it is more generic and controlled via config.db.
            synchronized (mInstalledxABIAppsList) {
                if (mInstalledxABIAppsList.containsKey(uid)) {
                    modifyxAbiEntry = true;
                    mInstalledxABIAppsList.remove(uid);
                }
            }
            // Check for xarch entry of this result
            // Update ONLY the mInstalledxARCHAppsList,
            // DO NOT update mXarchAppList as it is more generic and controlled via config.db.
            synchronized (mInstalledxARCHAppsList) {
                if (mInstalledxARCHAppsList.containsKey(uid)) {
                    modifyxArchEntry = true;
                    mInstalledxARCHAppsList.remove(uid);
                }
            }
            // Check for abilist entry of this result
            // Update ONLY the mInstalledABIListAppsList,
            // DO NOT update mArmAbiListApp as it is more generic and controlled via config.db.
            synchronized (mInstalledABIListAppsList) {
                if (mInstalledABIListAppsList.containsKey(uid)) {
                    modifyArmAbiListEntry = true;
                    mInstalledABIListAppsList.remove(uid);
                }
            }
        } else {
            Slog.e(TAG, "invalid ACTION in updateAbiEntry, action: " + action);
            return;
        }

        // Dump the entries into the filesystem
        writeAbiEntry(bstABI2Apps, mInstalledArmAppList);
        if (modifyxAbiEntry)
            writeAbiEntry(bstxABIApps, mInstalledxABIAppsList);
        if (modifyxArchEntry)
            writeAbiEntry(bstxARCHApps, mInstalledxARCHAppsList);
        if (modifyArmAbiListEntry)
            writeAbiEntry(bstAbilistApps, mInstalledABIListAppsList);
    }

    private void writeAbiEntry(String fileName, HashMap<Integer, String> map) {
        File outputFile = new File(fileName);
        FileWriter fw;
        BufferedWriter out = null;
        try {
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }
            if (FileUtils.setPermissions(fileName, FileUtils.S_IRUSR |
                        FileUtils.S_IWUSR | FileUtils.S_IRGRP | FileUtils.S_IROTH, -1, -1) != 0) {
                Slog.e(TAG, "Failed to change permissions for the file");
                        }
            fw = new FileWriter(outputFile, false);
            out = new BufferedWriter(fw);
            Iterator<HashMap.Entry<Integer, String>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                HashMap.Entry<Integer, String> ent = it.next();
                String entry = ent.getKey().toString() + ";".toString() + ent.getValue().toString();
                out.write(entry);
                out.newLine();
            }
            out.flush();
        } catch (Exception e) {
            Slog.e(TAG, "File Access Error: Not Able to write Data into " + fileName);
        } finally {
            try {
                if (out != null) {
                    out.close();
                    if (DEBUG) Slog.i(TAG, "Data written into " + fileName);
                }

                if (outputFile.length() == 0)
                    outputFile.delete();
            } catch (IOException e) {}
        }
    }

    // It is a generic func to write Map<String, String> to a designated file.
    private void writeMapWithStringValue(String fileName, HashMap<String, String> map) {
        File outputFile = new File(fileName);
        FileWriter fw;
        BufferedWriter out = null;
        try {
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }
            if (FileUtils.setPermissions(fileName, FileUtils.S_IRUSR |
                        FileUtils.S_IWUSR | FileUtils.S_IRGRP | FileUtils.S_IROTH, -1, -1) != 0) {
                Slog.e(TAG, "Failed to change permissions for the file");
                        }
            fw = new FileWriter(outputFile, false);
            out = new BufferedWriter(fw);
            // ROB-16097: To ensure configuration priority, MAP entries are sorted in the following order:
            // com.example.xxx > com.example.* > *
            Map<String, String> sortedMap = new TreeMap<>((k1, k2) -> {
                // All matches "*" are placed last
                if (k1.equals("*") && !k2.equals("*")) return 1;
                if (!k1.equals("*") && k2.equals("*")) return -1;
                if (k1.equals("*") && k2.equals("*")) return 0;

                // The wildcard "*" ends in the middle
                if (k1.endsWith("*") && !k2.endsWith("*")) return 1;
                if (!k1.endsWith("*") && k2.endsWith("*")) return -1;
                if (k1.endsWith("*") && k2.endsWith("*")) return k1.compareTo(k2);

                // Ordinary keys are sorted first in lexicographical order
                return k1.compareTo(k2);
            });
            sortedMap.putAll(map);

            Iterator<Map.Entry<String, String>> it = sortedMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> ent = it.next();
                String entry = ent.getKey().toString() + ";".toString() + ent.getValue().toString();
                out.write(entry);
                out.newLine();
            }
        } catch (Exception e) {
            Slog.e(TAG, "File Access Error: Not Able to write Data into " + fileName);
        } finally {
            try {
                if (out != null) {
                    out.close();
                    if (DEBUG) Slog.i(TAG, "Data written into " + fileName);
                }

                if (outputFile.length() == 0)
                    outputFile.delete();
            } catch (IOException e) {}
        }
    }

    private void createMemoryInfoFile(HashMap<String, String> map) {
        Iterator<HashMap.Entry<String, String>> it = map.entrySet().iterator();
        while (it.hasNext()) {
             HashMap.Entry<String, String> ent = it.next();
             String memorySize = ent.getValue().toString();

             String memorySizeKb = Long.toString(Long.parseLong(memorySize)/1024);
             File newMemFile = new File("/data/downloads/.tmp/xmeminfo." + memorySize);
             File origMemFile = new File("/etc/xmeminfo");
             try {

                 if (newMemFile.getParentFile()==null)
                     newMemFile.getParentFile().mkdirs();

                 if (newMemFile.exists() || !origMemFile.exists()) {
                     continue;
                 }
                 if (!newMemFile.exists()) {
                     newMemFile.createNewFile();
                     if (FileUtils.setPermissions(newMemFile,
                            FileUtils.S_IRUSR | FileUtils.S_IWUSR |
                            FileUtils.S_IRGRP | FileUtils.S_IROTH | FileUtils.S_IWGRP | FileUtils.S_IWOTH, -1, -1) != 0) {
                         Slog.e(TAG, "Failed to change permissions for the file");
                     }

                     BufferedReader reader = new BufferedReader(new FileReader(origMemFile));
                     String line = "", oldtext = "";
                     while((line = reader.readLine()) != null)
                     {
                         oldtext += line + "\r\n";
                     }
                     reader.close();

                     String oldmem = "        1817980";
                     String newMem = String.format("%1$"+oldmem.length()+ "s", memorySizeKb);
                     String newtext = oldtext.replaceAll("MemTotal:        1817980 kB", "MemTotal:"+ newMem + " kB");

                     FileWriter writer = new FileWriter(newMemFile);
                     writer.write(newtext);
                     writer.close();
                 }
             }
             catch (Exception e) {
                 Slog.w(TAG,"Exception in copying file " + origMemFile + " to " + newMemFile);
                 e.printStackTrace();
             }
        }
    }

    private void writeTreeSetWithString(String fileName, Set<String> appList) {
        File outputFile = new File(fileName);
        FileWriter fw;
        BufferedWriter out = null;
        try {
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }
            if (FileUtils.setPermissions(fileName, FileUtils.S_IRUSR |
                        FileUtils.S_IWUSR | FileUtils.S_IRGRP | FileUtils.S_IROTH, -1, -1) != 0) {
                Slog.e(TAG, "Failed to change permissions for the file");
                        }
            fw = new FileWriter(outputFile, false);
            out = new BufferedWriter(fw);
            Iterator<String> it = appList.iterator();
            while (it.hasNext()) {
                String entry = it.next();
                entry = entry.concat(";");
                out.write(entry);
            }
        } catch (Exception e) {
            Slog.e(TAG, "File Access Error: Not Able to write Data into " + fileName);
        } finally {
            try {
                if (out != null) {
                    out.close();
                    if (DEBUG) Slog.i(TAG, "Data written into " + fileName);
                }

                if (outputFile.length() == 0)
                    outputFile.delete();
            } catch (IOException e) {}
        }
    }



    /**
    * Check if this app requires to show original cpuinfo to arm apps
    * with this hack , case BS4-413 : com.stove.epic7.google seems to be working fine.
    * @hide
    * */
    public boolean isShowOrigCpuinfoArmApp(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in  XcpuApp List. mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + ", found package :" + pkgName);
        synchronized (mXcpuAppList)
        {
            if (mXcpuAppList.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " is in XcpuApp List");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mXcpuAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in XcpuApp List");
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * Our primitive astc app  check
     * @hide
     */
    public boolean isAstcApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in Astc app list\n");
        initLists();

        synchronized (mAstcAppList)
        {
            if (mAstcAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mAstcAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in Astc App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Using the patched version of houdini for apps like com.netmarble.lineageII
     * @hide
     */
    public boolean isPatchRelocApp(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in PatchRelocApp List.    mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);
        synchronized (mPatchRelocAppList)
        {
            if (mPatchRelocAppList.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " is in PatchRelocApp List\n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mPatchRelocAppList, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in PatchReloc App List\n");
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * getPgaGlVersion value for app
     * @hide
     */
    public String getPgaGlVersion(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in PgaGlVersion list\n");
        initLists();
        String res = "";
        synchronized (mPgaGlVersionList)
        {
            if (mPgaGlVersionList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in PgaGlVersion List\n");
                res = mPgaGlVersionList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mPgaGlVersionList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in PgaGlVersion List\n");
                    res = mPgaGlVersionList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * getAgaGlVersion value for app
     * @hide
     */
    public String getAgaGlVersion(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in AgaGlVersion list\n");
        initLists();
        String res = "";
        synchronized (mAgaGlVersionList)
        {
            if (mAgaGlVersionList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in AgaGlVersion List\n");
                res = mAgaGlVersionList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mAgaGlVersionList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in AgaGlVersion List\n");
                    res = mAgaGlVersionList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /*
     * Our primitive app check to disable hardware based Astc
     * @hide
     */
    public boolean isHardwareAstcDisabledApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in HardwareAstcDisabledApp list\n");
        initLists();

        synchronized (mHardwareDisableAstcAppList)
        {
            if (mHardwareDisableAstcAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mHardwareDisableAstcAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in HardwareAstcDisabledApp List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Our primitive app check to force Install Flag when performing dexopt after reboot or on upgrade
     * @hide
     */
    public boolean isForcedDexoptWithInstallFlag(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in ForcedDexOptInstallFlagList list\n");
        initLists();

        synchronized (mForcedDexOptInstallFlagList)
        {
            if (mForcedDexOptInstallFlagList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mForcedDexOptInstallFlagList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in ForcedDexOptInstallFlagList List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Using the old version of houdini for apps for which
     * entry is present in mUsingOldHoudiniAppList
     * @hide
     */
    public boolean isAppUsingOldHoudini(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in mUsingOldHoudiniAppList mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);

        synchronized (mUsingOldHoudiniAppList)
        {
            if (mUsingOldHoudiniAppList.contains(pkgName))
            {
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mUsingOldHoudiniAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    found = true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, "returning " + found + " for pkg " + pkgName);
        return found;
    }

    /**
     * Exposing keyboard and mouse devices
     * to the apps with the entry in the list.
     * @hide
     */
    public boolean areInputDevicesExposed(String packageName)
    {
        boolean found = false;
        initLists();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + packageName + " in mInputDevicesExposedAppList");

        synchronized (mInputDevicesExposedAppList)
        {
            if (mInputDevicesExposedAppList.contains(packageName))
            {
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mInputDevicesExposedAppList, packageName);
                if(wildcardMatchedPackageName != null)
                {
                    found = true;
                }
            }
        }

        String activity = SystemProperties.get("bst.config.top_activity_name", "*");
        boolean softEntryPresent = isSoftKeyboardRequired(packageName, activity);
        boolean softModifier = getSoftKeyboardModifier(packageName).equals("soft");
        if (softEntryPresent && softModifier) {
            Slog.d(TAG, "The package " + packageName + " have both soft and ide entries so ignoring ide entry for now");
            found = false;
        }
        if (DEBUG) Slog.d(TAG, "returning " + found + " for pkg " + packageName);
        return found;
    }

    /**
     * Case BS4-5738: Call of Duty Mobile app fix for low graphics mode in settings, App uses tencent hawk sdk
     * whose initialisation sequence (java + native) fails when Build.SUPPORTED_ABIS listcontains x86, however returning
     * only arm abi list (32/64 or both) results in proper initialization resulting in high graphics mode seen in in-app settings
     * tab.
     * Returning true for apps containing 'abilist' entry in config to show modified abi list in BUILD class.
     * @hide
     */
    public boolean isShowArmAbiListApp(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, "Trying to look for " + uid + " in armAbiList.    mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);
        synchronized (mArmAbiListApp)
        {
            if (mArmAbiListApp.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + "is in ArmAbi App List\n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mArmAbiListApp, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + "is in ArmAbi App List\n");
                    found = true;
                }
            }
        }
        return found;
    }

    private boolean isShowArmAbiListApp2(String pkgName)
    {
        boolean found = false;

        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to isShowArmAbiListApp2, pkgName: " + pkgName);
            return found;
        }

        initLists();

        synchronized (mArmAbiListApp)
        {
            if (mArmAbiListApp.contains(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + "is in ArmAbi App List\n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mArmAbiListApp, pkgName);
                if( wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + "is in ArmAbi App List\n");
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * @hide
     */
    public boolean isIgnoreLargeHeapApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mIgnoreLargeHeapAppList app list\n");
        initLists();

        synchronized (mIgnoreLargeHeapAppList)
        {
            if (mIgnoreLargeHeapAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mIgnoreLargeHeapAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mIgnoreLargeHeapAppList App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isClearCodeCacheApp(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, "Trying to look for " + uid + " in mClearCodeCache App list.    mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);
        synchronized (mClearCodeCacheApp)
        {
            if (mClearCodeCacheApp.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mClearCodeCacheApp List\n");
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mClearCodeCacheApp, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mClearCodeCacheApp List\n");
                    found = true;
                }
            }
        }
        return found;
    }
    /**
     * @hide
     */
    public boolean isForcedInputDisabledApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mForcedInputDisabledAppList app list\n");
        initLists();

        synchronized (mForcedInputDisabledAppList)
        {
            if (mForcedInputDisabledAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mForcedInputDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mForcedInputDisabledAppList App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isImageDetectionEnabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mImageDetectionEnabledAppList app list\n");
        initLists();

        synchronized (mImageDetectionEnabledAppList)
        {
            if (mImageDetectionEnabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mImageDetectionEnabledAppList App List\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mImageDetectionEnabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mImageDetectionEnabledAppList App List\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mImageDetectionEnabledAppList App List\n");
        return false;
    }

   /**
    * Set heap growth limit size for app
    * @hide
    */
    public int getHeapGrowthLimitSize(String pkgName)
    {
        initLists();
        int heapGrowthLimitSize = 0; // Heap growth limit in MB
        synchronized (mCustomHeapSizeAppList)
        {
            if (mCustomHeapSizeAppList.containsKey(pkgName))
            {
                heapGrowthLimitSize = Integer.parseInt(mCustomHeapSizeAppList.get(pkgName));
                if (DEBUG) Slog.d(TAG, pkgName + " is in mCustomHeapSizeAppList with heap growth limit " + heapGrowthLimitSize + "MB\n");
                return heapGrowthLimitSize;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCustomHeapSizeAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    heapGrowthLimitSize = Integer.parseInt(mCustomHeapSizeAppList.get(wildcardMatchedPackageName));
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mCustomHeapSizeAppList with heap growth limit " + heapGrowthLimitSize + "MB\n");
                    return heapGrowthLimitSize;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mCustomHeapSizeAppListList\n");
        return -1;
    }

    /**
     * @hide
     */
    public void updateIl2cppPkgs(String packageName)
    {
        // sanity check
        if (packageName == null || packageName.isEmpty()) {
            Slog.e(TAG, "invalid argument, package: " + packageName + " not adding in il2cpp applist, skipping it");
            return;
        }
        synchronized (mIl2cppAppList) {
            if (packageName.startsWith("~")) {
                String pkg = packageName.substring(1);
                if (mIl2cppAppList.contains(pkg)) {
                    if (DEBUG) Slog.d(TAG, "Removing " + pkg + " from mIl2cppAppList");
                    mIl2cppAppList.remove(pkg);
                }
            } else {
                if (DEBUG) Slog.d(TAG, "Adding " + packageName + " in mIl2cppAppList");
                mIl2cppAppList.add(packageName);
            }
        }
    }

    /**
     * @hide
     */
    public boolean isIl2cppApp(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);

        try {
            mPackageManager = mContext.getPackageManager();
            if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in mIl2cppAppList mPackageManager :" + mPackageManager);
            String[] str = mPackageManager.getPackagesForUid(uid);
            if (str == null)
            {
                Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
                return found;
            }
            String pkgName = str[0];
            if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);

            synchronized (mIl2cppAppList)
            {
                if (mIl2cppAppList.contains(pkgName)) {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mIl2cppAppList");
                    found = true;
                }
            }
            if (DEBUG) Slog.d(TAG, pkgName + " is not in mIl2cppAppList");
        } catch (Exception ex) {
            Slog.d(TAG, "Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        return found;
    }

    /**
     * @hide
     */
    public boolean isHideProcMapsApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mHideProcMapsAppList app list\n");
        initLists();

        synchronized (mHideProcMapsAppList)
        {
            if (mHideProcMapsAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mHideProcMaps App List\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mHideProcMapsAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mHideProcMapsAppList App List\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mHideProcMapsAppList App List\n");
        return false;
    }

    /**
     * @hide
     */
    public boolean isEnableNativeGamePad(String pkgName)
    {
        initLists();
        synchronized (mEnableNativeGamePadAppList)
        {
            if (mEnableNativeGamePadAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mEnableNativeGamePadAppList App List " + "\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mEnableNativeGamePadAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mEnableNativeGamePadAppList App List " + "\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mEnableNativeGamePadAppList\n");
        return false;
    }

    /**
     * @hide
     */
    public boolean isPreloadBstHookLibraryApp(String pkgName)
    {
        initLists();
        synchronized (mPreloadBstHookLibraryAppList)
        {
            if (mPreloadBstHookLibraryAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mPreloadBstHookLibraryAppList App List " + "\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mPreloadBstHookLibraryAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mPreloadBstHookLibraryAppList App List " + "\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mPreloadBstHookLibraryAppList\n");
        return false;
    }

    /**
     * @hide
     */
    public boolean isSuppressedNotificationApp(String pkgName)
    {
        initLists();
        synchronized (mSuppressedNotificationAppList)
        {
            if (mSuppressedNotificationAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mSuppressedNotificationAppList App List " + "\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mSuppressedNotificationAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mSuppressedNotificationAppList App List " + "\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mSuppressedNotificationAppList\n");
        return false;
    }

    /**
     * @hide
     */
    public boolean isMacrosDisabledApp(String pkgName)
    {
        initLists();
        synchronized (mMacrosDisabledAppList)
        {
            if (mMacrosDisabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mMacrosDisabledAppList App List " + "\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mMacrosDisabledAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mMacrosDisabledAppList App List " + "\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mMacrosDisabledAppList\n");
        return false;
    }

    /**
     * @hide
     */
    public boolean showFeedbackPopup(String pkgName)
    {
        initLists();
        synchronized (mShowFeedbackPopupAppList)
        {
            if (mShowFeedbackPopupAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mShowFeedbackPopupAppList App List " + "\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mShowFeedbackPopupAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mShowFeedbackPopupAppList App List " + "\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mShowFeedbackPopupAppList\n");
        return false;
    }

    /**
     * Check ffmpeg confidence for applications.
     * @hide
     */
    public float ffmpegConfidenceForApp(String pkgName)
    {
        float confidence = 0.0f;
        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to ffmpegConfidenceForApp, pkgName = " + pkgName);
            return confidence;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in ffmpegConfidence AppList\n");
        initLists();

        synchronized (mFfmpegConfidenceAppList)
        {
            if (mFfmpegConfidenceAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " requires custom ffmpeg confidence");
                confidence = mFfmpegConfidenceAppList.get(pkgName);
                if(DEBUG) Slog.d(TAG,"The confidence for package: " + pkgName + " is : " + confidence);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mFfmpegConfidenceAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " requires custom ffmpeg confidence");
                    confidence = mFfmpegConfidenceAppList.get(wildcardMatchedPackageName);
                    if(DEBUG) Slog.d(TAG,"The confidence for package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + confidence);
                }
            }
        }

        return confidence;
    }

    /**
     * Using the swappy.so for apps for which
     * entry is present in mUsingSwappyAppList
     * @hide
     */
    public boolean isAppUsingSwappy(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();
        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in mUsingSwappyAppList mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);

        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(pkgName, 0);
            if(applicationInfo != null)
            {
                File swappyFile = new File(applicationInfo.nativeLibraryDir, "libswappy.so");
                if (swappyFile.exists()) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
        }

        synchronized (mUsingSwappyAppList)
        {
            if (mUsingSwappyAppList.contains(pkgName))
            {
                found = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mUsingSwappyAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    found = true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, "returning " + found + " for pkg " + pkgName);
        return found;
    }

    /**
     * get the custom MouseCursorStyle for the app
     * @hide
     */
    public String getMouseCursorStyle(String pkgName)
    {
        if (DEBUG) Slog.d(TAG , "Trying to look for " + pkgName + " in mMouseCursorStyleAppList\n");
        String res = "";

        synchronized (mMouseCursorStyleAppList)
        {
            if (mMouseCursorStyleAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mMouseCursorStyleAppList\n");
                res = mMouseCursorStyleAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mMouseCursorStyleAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mMouseCursorStyleAppList\n");
                    res = mMouseCursorStyleAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

        /**
     * Check for applications to determine whether they need camera sensor rotation.
     * @hide
     */
    public int getCameraSensorRotation(String pkgName)
    {
        int rotationAngle = 0;
        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to CameraSensorRotationAngle, pkgName = " + pkgName);
            return rotationAngle;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in CameraSensorRotation AppList\n");
        initLists();

        synchronized (mCameraSensorRotationAppList)
        {
            if (mCameraSensorRotationAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " requires camera sensor rotation to  work properly");
                rotationAngle = mCameraSensorRotationAppList.get(pkgName);
                if(DEBUG) Slog.d(TAG,"The rotation Angle for package: " + pkgName + " is : " + rotationAngle);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCameraSensorRotationAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " requires camera sensor rotation to work properly");
                    rotationAngle = mCameraSensorRotationAppList.get(wildcardMatchedPackageName);
                    if(DEBUG) Slog.d(TAG,"The rotation Angle for package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + rotationAngle);

                }
            }
        }

        return rotationAngle;
    }

    /**
     * Check for applications to determine whether they need camera init rotation.
     * @hide
     */
    public int getCameraInitRotation(String pkgName)
    {
        int rotationAngle = 0;
        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to getCameraInitRotationAngle, pkgName = " + pkgName);
            return rotationAngle;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in getCameraInitRotation AppList\n");
        initLists();

        synchronized (mCameraInitRotationAppList)
        {
            if (mCameraInitRotationAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " requires camera init rotation to  work properly");
                rotationAngle = mCameraInitRotationAppList.get(pkgName);
                if(DEBUG) Slog.d(TAG,"The init rotation Angle for package: " + pkgName + " is : " + rotationAngle);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mCameraInitRotationAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " requires camera init rotation to work properly");
                    rotationAngle = mCameraInitRotationAppList.get(wildcardMatchedPackageName);
                    if(DEBUG) Slog.d(TAG,"The init rotation Angle for package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + rotationAngle);

                }
            }
        }

        return rotationAngle;
    }

    /**
     * Check if this app requires to add glFlush() after glViewport()
     * case ROB-4410: Adding a new entry name: UE4GLFlush
     * @hide
     */
    public boolean isUE4GLFlushApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in UE4GLFlush app list\n");
        initLists();

        synchronized (mUE4GLFlushAppList)
        {
            if (mUE4GLFlushAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mUE4GLFlushAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in UE4GLFlush App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if this app needs to read now.gg accounts list
     * regardless of GET_ACCOUNTS permission
     * @hide
     */
    public boolean isGetNowggAccountsListApp(int uid)
    {
        boolean found = false;
        uid = mapUid(uid);
        initLists();

        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in mGetNowggAccountsList mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return found;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);

        synchronized (mGetNowggAccountsList)
        {
            if (mGetNowggAccountsList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGetNowggAccountsList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in getNowggAccounts App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * check if Google sign in popup needs to be shown for the app
     * @hide
     */
    public boolean isGoogleSignInRequired(String pkgName)
    {
        initLists();
        synchronized (mGoogleSignInReqdAppList)
        {
            if (mGoogleSignInReqdAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mGoogleSignInReqdAppList App List " + "\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGoogleSignInReqdAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mGoogleSignInReqdAppList App List " + "\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mGoogleSignInReqdAppList\n");
        return false;
    }

    /**
     * get the MouseAction for the activity
     * @hide
     */
    public String getMouseAction(String pkg, String activity)
    {
        String res = "";
        if (pkg == null || activity == null) {
            Slog.e(TAG, "Invalid arguments to getMouseAction, pkg = " + pkg + " activity = " + activity);
            return res;
        }

        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkg + ";" + activity + " in  mMouseActionConfiguredAppList \n");
        initLists();
        synchronized(mMouseActionConfiguredAppList)
        {
            if (!mMouseActionConfiguredAppList.containsKey(pkg))
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mMouseActionConfiguredAppList, pkg);
                if( wildcardMatchedPackageName == null)
                    return res;
                else
                    pkg = wildcardMatchedPackageName;
            }
            String flattenCurrentActivity = activity.trim();
            int index = activity.indexOf('/');
            if (index != -1)
            {
                if (DEBUG) Slog.d(TAG, "orig activity: " + activity + " pkg: " + activity.substring(0, index) + " activity: " + activity.substring(index+1));
                flattenCurrentActivity = new StringBuilder().append(activity.substring(0,index)).append(activity.substring(index+1)).toString();
            }
            Map<String,String> list = mMouseActionConfiguredAppList.get(pkg);
            Iterator<Map.Entry<String, String>> itr = list.entrySet().iterator();
            while (itr.hasNext())
            {
                Map.Entry<String, String> entry = itr.next();
                String storedActivity = entry.getKey();
                String action = entry.getValue();
                index = storedActivity.indexOf('/');
                if (index != -1)
                {
                    if (DEBUG) Slog.d(TAG, "orig stored activity: " + storedActivity + " pkg: " + storedActivity.substring(0, index) + " activity: " + storedActivity.substring(index+1));
                    storedActivity = new StringBuilder().append(storedActivity.substring(0,index)).append(storedActivity.substring(index+1)).toString();
                }
                Slog.d(TAG, "storedActivity : " + storedActivity + " orig activity: " + activity + " flattenCurrentActivity : " + flattenCurrentActivity);
                if ( storedActivity.equals("*") || storedActivity.equalsIgnoreCase(activity) || storedActivity.equalsIgnoreCase(flattenCurrentActivity)) {
                    res = action;
                    break ;
                }
            }
        }
        return res;
    }

     /**
     * checks if vulkan support needed for this app or not
     * @hide
     */
    public String isVulkanRequired(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mVulkanReqdAppList list\n");
        initLists();
        String res = "";
        synchronized (mVulkanReqdAppList)
        {
            if (mVulkanReqdAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mVulkanReqdAppList List\n");
                res = mVulkanReqdAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mVulkanReqdAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mVulkanReqdAppList List\n");
                    res = mVulkanReqdAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * checks if hotfix support needed for this app or not
     * @hide
     */
    public boolean isHotFixApp(String pkgName)
    {
        initLists();
        synchronized (mHotFixAppList)
        {
            if (mHotFixAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mHotFixAppList App List " + "\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mHotFixAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mHotFixAppList App List " + "\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mHotFixAppList\n");
        return false;
    }


     /**
     * Check if app should be force kill when closed from recent apps
     * case ROB-2374: Adding a new entry name: forceKill
     * @hide
     */
    public boolean isForceKillApp(String pkgName)
    {
        boolean res = false;
        initLists();
        synchronized (mForceKillAppList)
        {
            if (mForceKillAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mForceKillAppList App List " + "\n");
                res = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mForceKillAppList, pkgName);
                if (wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mForceKillAppList App List " + "\n");
                    res = true;
                }
            }
        }
        return res;
    }

    /**
     * @hide
     */
    public boolean isHppEnabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mHppDisabledAppList app list\n");
        initLists();

        synchronized (mHppDisabledAppList)
        {
            if (mHppDisabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mHppDisabledAppList App List\n");
                return false;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mHppDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mHppDisabledAppList App List\n");
                    return false;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mHppDisabledAppList App List\n");
        return true;
    }

    /**
     * Check if this app requires to be filtered with glInvalidateFramebuffer
     * case ROB-9468: Adding a new entry name: GLIFBFilter
     * @hide
     */
    public boolean isGLInvalidateFramebufferFilterApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GLIFBFilter app list\n");
        initLists();

        synchronized (mGLInvalidateFramebufferFilterAppList)
        {
            if (mGLInvalidateFramebufferFilterAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGLInvalidateFramebufferFilterAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GLIFBFilter App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * this is a hack for Unity flicker issue
     * case ROB-8684: Adding a new entry name: UnityFlicker
     * @hide
     */
    public int getUnityFlickerPatchRequired(String pkgName)
    {
        int unityFlicker = 0;
        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to UnityFlicker, pkgName = " + pkgName);
            return unityFlicker;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in UnityFlicker AppList\n");
        initLists();

        synchronized (mUnityFlickerAppList)
        {
            if (mUnityFlickerAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " requires unity flicker patch to  work properly");
                unityFlicker = mUnityFlickerAppList.get(pkgName);
                if(DEBUG) Slog.d(TAG,"The unity flicker patch for package: " + pkgName + " is : " + unityFlicker);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mUnityFlickerAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " requires unity flicker patch to work properly");
                    unityFlicker = mUnityFlickerAppList.get(wildcardMatchedPackageName);
                    if(DEBUG) Slog.d(TAG,"The unity flicker patch for package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + unityFlicker);

                }
            }
        }

        return unityFlicker;
    }

    /**
     * Check if this app need flush with glDispatchCompute on intel gpu
     * case ROB-10551: Adding a new entry name: IntelDCFlush
     * @hide
     */
    public boolean isIntelGLDispatchComputeFlushApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in IntelDCFlush app list\n");
        initLists();

        synchronized (mIntelGLDispatchComputeFlushAppList)
        {
            if (mIntelGLDispatchComputeFlushAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mIntelGLDispatchComputeFlushAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in IntelDCFlush App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if this app need turn on glProgramBinary
     * case ROB-10507: Adding a new entry name: GLPB
     * @hide
     */
    public boolean isGLProgramBinaryApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mGLProgramBinaryDisabledAppList\n");
        initLists();

        synchronized (mGLProgramBinaryDisabledAppList)
        {
            if (mGLProgramBinaryDisabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mGLProgramBinaryDisabledAppList\n");
                return false;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGLProgramBinaryDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mGLProgramBinaryDisabledAppList\n");
                    return false;
                }
            }
        }

        if (DEBUG) Slog.d(TAG, pkgName + " is not in mGLProgramBinaryDisabledAppList\n");
        return true;
    }

    /**
     * Check if this app need perf in glUnmapBuffer
     * case ROB-10787: Adding a new entry name: GLUBPerf
     * @hide
     */
    public boolean isGLUnmapBufferPerfApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mGLUnmapBufferPerfDisabledAppList\n");
        initLists();

        synchronized (mGLUnmapBufferPerfDisabledAppList)
        {
            if (mGLUnmapBufferPerfDisabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mGLUnmapBufferPerfDisabledAppList\n");
                return false;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGLUnmapBufferPerfDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mGLUnmapBufferPerfDisabledAppList\n");
                    return false;
                }
            }
        }

        if (DEBUG) Slog.d(TAG, pkgName + " is not in mGLUnmapBufferPerfDisabledAppList\n");
        return true;
    }

    /**
     * getGLShaderWorkaround value for app
     * @hide
     */
    public String getGLShaderWorkaround(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GLShaderWorkaround list\n");
        initLists();
        String res = "";
        synchronized (mGLShaderWorkaroundList)
        {
            if (mGLShaderWorkaroundList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in GLShaderWorkaround List\n");
                res = mGLShaderWorkaroundList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGLShaderWorkaroundList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GLShaderWorkaround List\n");
                    res = mGLShaderWorkaroundList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    private static class ActivityManagerServiceHolder {
        static final ActivityManagerInternal mActivityManagerService = LocalServices.getService(ActivityManagerInternal.class);
    };

    private int mapUid(int uid) {
        if (!Process.isIsolated(uid)) return uid;
        final ActivityManagerInternal service = ActivityManagerServiceHolder.mActivityManagerService;
        if (service == null) return uid;

        final int result = service.mapIsolatedUid(uid);
        if (DEBUG) Slog.d(TAG, String.format("mapIsolatedUid %s -> %s", uid, result));
        return result;
    }

    public String getGameDefaultSetting(String pkgName) {
        if (DEBUG) Slog.d(TAG , "Trying to look for " + pkgName + " in mGameDefaultSettingAppList\n");
        String res = "";

        synchronized (mGameDefaultSettingAppList)
        {
            if (mGameDefaultSettingAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mGameDefaultSettingAppList\n");
                res = mGameDefaultSettingAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGameDefaultSettingAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mGameDefaultSettingAppList\n");
                    res = mGameDefaultSettingAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    public String getDefaultProfile(String pkgName) {
        if (DEBUG) Slog.d(TAG , "Trying to look for " + pkgName + " in mDefaultProfileAppList\n");
        String res = "";

        synchronized (mDefaultProfileAppList)
        {
            if (mDefaultProfileAppList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mDefaultProfileAppList\n");
                res = mDefaultProfileAppList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mDefaultProfileAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mDefaultProfileAppList\n");
                    res = mDefaultProfileAppList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * case ROB-9086: High fps config, Adding a new entry name: XperfMode
     * @hide
     */
    public int getXperfMode(int uid)
    {
        int xperfModeValue = 0;
        uid = mapUid(uid);
        initLists();

        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in mXperfModeList mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning 0 by default");
            return xperfModeValue;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);

        synchronized (mXperfModeList)
        {
            if (mXperfModeList.containsKey(pkgName))
            {
                xperfModeValue = mXperfModeList.get(pkgName);
                if (DEBUG) Slog.d(TAG,"The XperfMode for package: " + pkgName + " is : " + xperfModeValue);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mXperfModeList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    xperfModeValue = mXperfModeList.get(wildcardMatchedPackageName);
                    if (DEBUG) Slog.d(TAG,"The XperfMode package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + xperfModeValue);
                }
            }
        }
        return xperfModeValue;
    }

    /**
     * @hide
     */
    public boolean isDhdg(int uid)
    {
        uid = mapUid(uid);
        initLists();

        mPackageManager = mContext.getPackageManager();
        if (DEBUG) Slog.d(TAG, " Trying to look for " + uid + " in mDhdgList mPackageManager :" + mPackageManager);
        String[] str = mPackageManager.getPackagesForUid(uid);
        if (str == null)
        {
            Slog.d(TAG, "no corresponding package found for uid :" + uid + ". Returning false by default");
            return false;
        }
        String pkgName = str[0];
        if(DEBUG) Slog.d(TAG, "corresponding to uid :" + uid + "  ,found package :" + pkgName);

        synchronized (mDhdgList)
        {
            if (mDhdgList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mDhdgList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mDhdgList App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isGlNativeSyncDisabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GlNativeSyncDisabled app list\n");
        initLists();

        synchronized (mGlNativeSyncDisabledAppList)
        {
            if (mGlNativeSyncDisabledAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGlNativeSyncDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GlNativeSyncDisabled App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isAngleDisabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in AngleDisabled app list\n");
        initLists();

        synchronized (mAngleDisabledAppList)
        {
            if (mAngleDisabledAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mAngleDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in AngleDisabledAppList App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isOneStorePay(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mOneStorePayAppList app list\n");
        initLists();

        synchronized (mOneStorePayAppList)
        {
            if (mOneStorePayAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mOneStorePayAppList App List\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mOneStorePayAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mOneStorePayAppList App List\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mOneStorePayAppList App List\n");
        return false;
    }

    /**
     * @hide
     */
    public boolean isDrmEnabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mDrmEnabledAppList app list\n");
        initLists();

        synchronized (mDrmEnabledAppList)
        {
            if (mDrmEnabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mDrmEnabledAppList App List\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mDrmEnabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mDrmEnabledAppList App List\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mDrmEnabledAppList App List\n");
        return false;
    }

    /**
     * @hide
     */
    public boolean isEtherNetType(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mEtherNetTypeAppList app list\n");
        initLists();

        synchronized (mEtherNetTypeAppList)
        {
            if (mEtherNetTypeAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mEtherNetTypeAppList App List\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mEtherNetTypeAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mEtherNetTypeAppList App List\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mEtherNetTypeAppList App List\n");
        return false;
    }

    public boolean isTexTargetCheckDisabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mTexTargetCheckAppList\n");
        initLists();

        synchronized (mTexTargetCheckAppList)
        {
            if (mTexTargetCheckAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mTexTargetCheckAppList\n");
                return false;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mTexTargetCheckAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mTexTargetCheckAppList\n");
                    return false;
                }
            }
        }

        if (DEBUG) Slog.d(TAG, pkgName + " is not in mTexTargetCheckAppList\n");
        return true;
    }

    /**
     * this is a hack for fixed surface rotation issue
     * case ROB-12546: Adding a new entry name: fixedSurfaceRotation
     * @hide
     */
    public int getFixedSurfaceRotationRequired(String pkgName)
    {
        int fixedSurfaceRotation = -1;
        if (pkgName == null || pkgName.trim().length() <= 0)
        {
            Slog.e(TAG, "invalid arguments to FixedSurfaceRotation, pkgName = " + pkgName);
            return fixedSurfaceRotation;
        }

        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in FixedSurfaceRotation AppList\n");
        initLists();

        synchronized (mFixedSurfaceRotationAppList)
        {
            if (mFixedSurfaceRotationAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " requires fixed surface rotation to  work properly");
                fixedSurfaceRotation = mFixedSurfaceRotationAppList.get(pkgName);
                if(DEBUG) Slog.d(TAG,"The fixed surface rotation for package: " + pkgName + " is : " + fixedSurfaceRotation);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mFixedSurfaceRotationAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " requires fixed surface rotation to work properly");
                    fixedSurfaceRotation = mFixedSurfaceRotationAppList.get(wildcardMatchedPackageName);
                    if(DEBUG) Slog.d(TAG,"The fixed surface rotation for package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + fixedSurfaceRotation);

                }
            }
        }

        return fixedSurfaceRotation;
    }

    /**
     * @hide
     */
    public String getIapSetting(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in iap setting list\n");
        initLists();

        synchronized (mIapSettingLock)
        {
            if (mIapSettingPkgIndexMap.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in iap setting list\n");
                int index = mIapSettingPkgIndexMap.get(pkgName);
                if (index > -1 && index < mIapSettingList.size()) {
                    return mIapSettingList.get(index);
                }
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mIapSettingPkgIndexMap, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in iap setting list\n");
                    int index = mIapSettingPkgIndexMap.get(wildcardMatchedPackageName);
                    if (index > -1 && index < mIapSettingList.size()) {
                        return mIapSettingList.get(index);
                    }
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " get iap setting failure\n");
        return null;
    }

    /**
     * @hide
     */
    public boolean isGlMapBufferRangeHost(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mGlMapBufferRangeHostDisabledAppList\n");
        initLists();

        synchronized (mGlMapBufferRangeHostDisabledAppList)
        {
            if (mGlMapBufferRangeHostDisabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mGlMapBufferRangeHostDisabledAppList\n");
                return false;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGlMapBufferRangeHostDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mGlMapBufferRangeHostDisabledAppList\n");
                    return false;
                }
            }
        }

        if (DEBUG) Slog.d(TAG, pkgName + " is not in mGlMapBufferRangeHostDisabledAppList\n");
        return true;
    }

    /**
     * @hide
     */
    public boolean isIgnoreSyncTimeout(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in IgnoreSyncTimeout app list\n");
        initLists();

        synchronized (mIgnoreSyncTimeoutAppList)
        {
            if (mIgnoreSyncTimeoutAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mIgnoreSyncTimeoutAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in IgnoreSyncTimeout App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if this app need GLVBOCache Disable
     * case ROB-13471: Adding a new entry name: GLVBOCacheDisable
     * @hide
     */
    public boolean isGLVBOCacheDisableApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GLVBOCache Disable app list\n");
        initLists();

        synchronized (mGLVBOCacheDisableAppList)
        {
            if (mGLVBOCacheDisableAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGLVBOCacheDisableAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GLVBOCache Disable App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isBlockEditWhenComposing(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in BlockEditWhenComposing app list\n");
        initLists();

        synchronized (mBlockEditWhenComposingAppList)
        {
            if (mBlockEditWhenComposingAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mBlockEditWhenComposingAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in BlockEditWhenComposing App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * getGLHostInfo value for app
     * @hide
     */
    public String getGLHostInfo(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GLHostInfo list\n");
        initLists();
        String res = "";
        synchronized (mGLHostInfoList)
        {
            if (mGLHostInfoList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in GLHostInfo List\n");
                res = mGLHostInfoList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGLHostInfoList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GLHostInfo List\n");
                    res = mGLHostInfoList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * getGLExtensionsIgnore value for app
     * @hide
     */
    public String getGLExtensionsIgnore(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in GLExtensionsIgnore list\n");
        initLists();
        String res = "";
        synchronized (mGLExtensionsIgnoreList)
        {
            if (mGLExtensionsIgnoreList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in GLExtensionsIgnore List\n");
                res = mGLExtensionsIgnoreList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGLExtensionsIgnoreList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in GLExtensionsIgnore List\n");
                    res = mGLExtensionsIgnoreList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * getVkHostInfo value for app
     * @hide
     */
    public String getVkHostInfo(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in VkHostInfo list\n");
        initLists();
        String res = "";
        synchronized (mVkHostInfoList)
        {
            if (mVkHostInfoList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in VkHostInfo List\n");
                res = mVkHostInfoList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mVkHostInfoList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in VkHostInfo List\n");
                    res = mVkHostInfoList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    /**
     * getVKDeviceExtIgnore value for app
     * @hide
     */
    public String getVKDeviceExtIgnore(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in VKDeviceExtIgnore list\n");
        initLists();
        String res = "";
        synchronized (mVKDeviceExtIgnoreList)
        {
            if (mVKDeviceExtIgnoreList.containsKey(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in VKDeviceExtIgnore List\n");
                res = mVKDeviceExtIgnoreList.get(pkgName);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mVKDeviceExtIgnoreList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in VKDeviceExtIgnore List\n");
                    res = mVKDeviceExtIgnoreList.get(wildcardMatchedPackageName);
                }
            }
        }
        return res;
    }

    public boolean isEGLSurfaceIgnore(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in EGLSurfaceIgnore app list\n");
        initLists();

        synchronized (mEGLSurfaceIgnoreAppList)
        {
            if (mEGLSurfaceIgnoreAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mEGLSurfaceIgnoreAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in EGLSurfaceIgnoreAppList App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isHardKeyBoard(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in HardKeyBoard app list\n");
        initLists();

        synchronized (mHardKeyBoardAppList)
        {
            if (mHardKeyBoardAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mHardKeyBoardAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in HardKeyBoard App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if this app need auto glFlush on intel gpu
     * case ROB-14931: Adding a new entry name: IntelAutoGLFlush
     * @hide
     */
    public boolean isIntelAutoGLFlushApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mIntelAutoGLFlushDisabledAppList\n");
        initLists();

        synchronized (mIntelAutoGLFlushDisabledAppList)
        {
            if (mIntelAutoGLFlushDisabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mIntelAutoGLFlushDisabledAppList\n");
                return false;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mIntelAutoGLFlushDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mIntelAutoGLFlushDisabledAppList\n");
                    return false;
                }
            }
        }

        if (DEBUG) Slog.d(TAG, pkgName + " is not in mIntelAutoGLFlushDisabledAppList\n");
        return true;
    }

    /**
     * @hide
     */
    public boolean isRotateDisabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mRotateDisabledAppList app list\n");
        initLists();
        synchronized (mRotateDisabledAppList)
        {
            if (mRotateDisabledAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in mRotateDisabledAppList App List\n");
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mRotateDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mRotateDisabledAppList App List\n");
                    return true;
                }
            }
        }
        if (DEBUG) Slog.d(TAG, pkgName + " is not in mRotateDisabledAppList App List\n");
        return false;
    }

    private final boolean searchUnreal5Lib(String path)
    {
        File file = new File(path);
        File[] subFiles = file.listFiles();
        for (File sub : subFiles)
        {
            String subname = sub.getName();
            if (sub.isDirectory())
            {
                String subpath = path + "/" + subname;
                if(searchUnreal5Lib(subpath))
                    return true;
            }
            else
            {
                if (subname.equals("libUnreal.so"))
                {
                    if (DEBUG) Slog.d(TAG, "This is a Unreal5 game: " + path + "\n");
                    return true;
                }
            }
        }
        return false;
    }

    private final String composeApkLibPath(String path, String pkg) {
        File file = new File(path);
        File[] subFiles = file.listFiles();
        for (File sub : subFiles) {
            if (sub.isDirectory()) {
                String subname = sub.getName();
                String subpath = path + "/" + subname;
                File file2 = new File(subpath);
                File[] subFiles2 = file2.listFiles();
                for (File sub2 : subFiles2) {
                    String subname2 = sub2.getName();
                    if (subname2.startsWith(pkg)){
                        String subpath2 = subpath + "/" + subname2 + "/" + "lib";
                        return subpath2;
                    }
                }
            }
        }
        return null;
    }

    private final boolean checkIfUnreal5Game(String pkgName)
    {
        if(pkgName == null)
            return false;
        String apkpath = null;
        int index = pkgName.indexOf(":psoprogramservice");
        if (index > 0)
        {
            String subPkgName = pkgName.substring(0, index);
            apkpath = composeApkLibPath("/data/app", subPkgName);
        }
        else
        {
            apkpath = composeApkLibPath("/data/app", pkgName);
        }
        if(apkpath == null)
            return false;
        return searchUnreal5Lib(apkpath);
    }

    /**
     * @hide
     */
    public boolean isUnreal5App(String pkgName)
    {
        boolean res = false;
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " is Unreal5 App\n");
        synchronized (mUnreal5AppMap)
        {
            if (mUnreal5AppMap.containsKey(pkgName))
                return mUnreal5AppMap.get(pkgName);
            else
            {
                res = checkIfUnreal5Game(pkgName);
                mUnreal5AppMap.put(pkgName, res);
            }
        }
        return res;
    }

    /**
     * @hide
     */
    public boolean isUE5PBDisabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in UE5PBDisabled app list\n");
        initLists();
        synchronized (mUE5PBDisabledAppList)
        {
            if (mUE5PBDisabledAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mUE5PBDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in UE5PBDisabled App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isBptcApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in Bptc app list\n");
        initLists();

        synchronized (mBptcAppList)
        {
            if (mBptcAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mBptcAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in Bptc App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check the value of extractNativeLibs to determine whether native libraries need to be extracted for the application.
     * @hide
     */
    public boolean isExtractNativeLibs(String pkgName)
    {
        boolean res = false;
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in ExtractNativeLibsApp list\n");
        initLists();

        synchronized (mExtractNativeLibsAppList)
        {
            if (mExtractNativeLibsAppList.contains(pkgName))
            {
                if (DEBUG) Slog.d(TAG, pkgName + " is in ExtractNativeLibsApp List\n");
                res = true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mExtractNativeLibsAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in ExtractNativeLibsApp List\n");
                    res = true ;
                }
            }
        }
        return res;
    }

    /**
     * @hide
     */
    public boolean isDefaultXYDpi(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in DefaultXYDpi app list\n");
        initLists();

        synchronized (mDefaultXYDpiAppList)
        {
            if (mDefaultXYDpiAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mDefaultXYDpiAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in DefaultXYDpi App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public int getPScoreAbove(String pkgName)
    {
        if(DEBUG) Slog.d(TAG, " Trying to look for " + pkgName + " in PScoreAbove AppList\n");
        initLists();

        int score = 0;
        synchronized (mPScoreAboveAppList)
        {
            if (mPScoreAboveAppList.containsKey(pkgName))
            {
                if(DEBUG) Slog.d(TAG, pkgName + " requires PScoreAbove to change default settings");
                score = mPScoreAboveAppList.get(pkgName);
                if(DEBUG) Slog.d(TAG,"The PScoreAbove for package: " + pkgName + " is : " + score);
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mPScoreAboveAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if(DEBUG) Slog.d(TAG, pkgName + " requires PScoreAbove to change default settings");
                    score = mPScoreAboveAppList.get(wildcardMatchedPackageName);
                    if(DEBUG) Slog.d(TAG,"The PScoreAbove for package: " + pkgName + " matched with : " + wildcardMatchedPackageName + " is : " + score);

                }
            }
        }
        return score;
    }

    /**
     * @hide
     */

    /**
     * @hide
     */
    public boolean isUEEGLCrashFixApp(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in UEEGLCrashFix app list\n");
        initLists();

        synchronized (mUEEGLCrashFixAppList)
        {
            if (mUEEGLCrashFixAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mUEEGLCrashFixAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in UEEGLCrashFix App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isMapBufRangeReadOnceEnabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mGlMapBufferRangeReadOnce app list\n");
        initLists();

        synchronized (mGlMapBufferRangeReadOnceAppList)
        {
            if (mGlMapBufferRangeReadOnceAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mGlMapBufferRangeReadOnceAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in mGlMapBufferRangeReadOnce App List\n");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @hide
     */
    public boolean isFbCompleteCheckDisabled(String pkgName)
    {
        if (DEBUG) Slog.d(TAG, "Trying to look for " + pkgName + " in mFbCompleteCheckDisabledAppList app list\n");
        initLists();

        synchronized (mFbCompleteCheckDisabledAppList)
        {
            if (mFbCompleteCheckDisabledAppList.contains(pkgName))
            {
                return true;
            }
            else
            {
                String wildcardMatchedPackageName = isPackageMatchedWilcard(mFbCompleteCheckDisabledAppList, pkgName);
                if(wildcardMatchedPackageName != null)
                {
                    if (DEBUG) Slog.d(TAG, pkgName + " is in isFbCompleteCheckDisabled App List\n");
                    return true;
                }
            }
        }
        return false;
    }
}
