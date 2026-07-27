package android.util;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.FileUtils;
import android.os.Process;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.SystemProperties;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.json.JSONObject;

import com.bluestacks.os.IBstFilterAppsService;

/** @hide */
public final class BstUtils {
    private static final String TAG = "BstUtils";
    private static final String TAG_BST_REFERRAL = "BstUtils-Affiliate";
    private static final boolean DEBUG = false;
    private static final boolean HIDE_ACCESSIBILITY_SERVICES =
            SystemProperties.getBoolean("bst.config.hide_accessibility_services", true);
    private static final boolean DEBUG_BST_REFERRAL = DEBUG || android.os.SystemProperties.getInt("bst.debug.referral", 0) > 0 ? true : false;
    private static final boolean DEBUG_PACKAGE_INFO = DEBUG || android.os.SystemProperties.getInt("bst.debug.packageinfo", 0) > 0 ? true : false;
    private static final boolean BST_SHOW_APPS = SystemProperties.getInt("bst.debug.bst_show_apps", 0) == 0 ? false : true;
    private static final int mBstAffiliateTestingValue = SystemProperties.getInt("bst.debug.affiliate.test", 0);
    private static String savedPackageName = null;
    private static int savedPid = 0;
    private static String bstSimSerialNumber = null;

    private BstUtils() {
    }

    /*
     * Used to read Files.
     * @hide
     */
    public static String readFile(String file)
    {
        final StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        BufferedReader reader = null;
        String retStr = null;
        try {
            reader = new BufferedReader( new FileReader (file));
            String line  = null;
            StringBuilder stringBuilder = new StringBuilder();
            String ls = System.getProperty("line.separator");
            while( ( line = reader.readLine() ) != null ) {
                stringBuilder.append( line );
                stringBuilder.append( ls );
            }
            retStr = stringBuilder.toString();
        }
        catch (Exception e) {
            if (DEBUG) {
                Slog.e(TAG, "Exception in readFile : " + file );
                e.printStackTrace();
            }
        }
        finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return retStr;
    }

    /*
     * Get Process name from pid
     * As most of the time process name is same as that of packageName, this is quite useful information
     * @hide
     */
    public static String getAppNameFromPid(int pid)
    {
        String trimmedProcName = null;
        if(DEBUG) Slog.d(TAG, "in getAppNameFromPid(), pid: "+ pid);
        if (pid == savedPid && savedPackageName != null) return savedPackageName;

        String procFile = new StringBuilder("/proc/").append(pid).append("/cmdline").toString();
        String procName = readFile(procFile);
        if (procName != null && procName.length() > 0)
            trimmedProcName = procName.trim();

        if (trimmedProcName == null || trimmedProcName.length() <= 0 || trimmedProcName.equalsIgnoreCase("/system/bin/arm-runtime"))
        {
            procFile = new StringBuilder("/proc/").append(pid).append("/comm").toString();
            procName = readFile(procFile);
            if (procName != null && procName.length() > 0)
                trimmedProcName = procName.trim();
        }
        if (trimmedProcName != null && trimmedProcName.length() > 0)
        {
            if(DEBUG) Slog.d(TAG, "in getAppNameFromPid(), appname for pid: "+ pid + " is " + trimmedProcName);
            // not caching proces name if process name is yet to be resolved
            if (!trimmedProcName.equals("zygote") && !trimmedProcName.equals("system_server") && !trimmedProcName.equals("<pre-initialized>")) {
                savedPackageName = trimmedProcName;
                savedPid = pid;
            }
            return trimmedProcName;
        }
        if (DEBUG) Slog.w(TAG, "Error in finding the appName whose pid is " + pid);
        return null;
    }

    /* This function will return true if we have to modify the values in the referrer api
     * else we will return the original values.
     *
     * @hide
     */
    public static String bstModifyReferrerApiValues(String packageName, int pid, HashMap<String, String> mBstReferralInstallTimeList, HashMap<String, ArrayList<Long>> mBstInstallTimeList, String installReferrerValue) {
        long bstClickTime = 0L;
        long firstInstallTime = 0L;
        boolean referrerPresent = false;
        boolean skipReferral = false;
        boolean isUpdated = false;
        String referral = "";
        String source = "";
        String reason = "";
        JSONObject response = new JSONObject();

        try {
            response.put("success", false);

            if (mBstAffiliateTestingValue == 1) {
                packageName = null;
            }

            // Package name check
            if (packageName == null || packageName.trim().length() < 1) {
                reason = "Invalid argument: packageName";
                response.put("reason", reason);

                if(DEBUG_BST_REFERRAL) Log.w(TAG_BST_REFERRAL, "Not modifying values (pid: " + pid + ")" + ", reason = " + reason);

                return response.toString();
            }

            // Referrer app or not?
            String referralData = mBstReferralInstallTimeList.getOrDefault(packageName, "{}");
            JSONObject referralDataObj = new JSONObject(referralData);
            referral = referralDataObj.optString("referrer", "");
            referrerPresent = (referralData != null && referral != null && referral.trim().length() > 0) ? true : false; // If referrerPresent

            if (mBstAffiliateTestingValue == 2) {
                referrerPresent = false;
            }

            if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "referrerPresent : " + referrerPresent);
            if (!referrerPresent) {
                if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "Not doing any modification for packageName = " + packageName + " referralData is NULL");
                response.put("reason", "referrerNotPresent");
                return response.toString();
            }

            // Third party referrer or organic?
            if (thirdPartyReferred(packageName)) {
                reason = "third party referred";
                response.put("reason", reason);

                if (DEBUG_BST_REFERRAL) Log.w(TAG_BST_REFERRAL, "Not modifying values for package " + packageName + ", reason = " + reason);

                return response.toString();
            }

            // install time check
            ArrayList<Long> pkgInstallTimeData = mBstInstallTimeList.get(packageName);

            if (mBstAffiliateTestingValue == 4) {
                pkgInstallTimeData = null;
            }

            if (pkgInstallTimeData == null || pkgInstallTimeData.size() < 2) {
                // should never reach here for affiliated app installed by us
                reason = "install time data not present : it should not reach here";
                response.put("reason", reason);

                if(DEBUG_BST_REFERRAL) Log.w(TAG_BST_REFERRAL, "Not modifying values for packageName: " + packageName + "(" + pid + "), possibly not an affiliated app, reason = " + reason);

                return response.toString();
            }

            firstInstallTime = pkgInstallTimeData.get(0);
            isUpdated = pkgInstallTimeData.get(1) > 0 ? true : false;
            if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "packageName = " + packageName + " isUpdated = " + isUpdated + " firstInstallTime = " + firstInstallTime);

            // First install or app update scenario?
            if (isUpdated) {
                if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "Not doing any modification for packageName = " + packageName + " package is updated, isUpdated = " + isUpdated);
                response.put("reason", "package is updated");
                return response.toString();
            }

            /** BlueStacks have offer for this package. */
            bstClickTime = referralDataObj.optLong("mod_referrer_click_timestamp", 0L); // final click time as recorded by our HomeService
            skipReferral = referralDataObj.optBoolean("skip_referrer", false);          // true if referral is to be skipped.
            source  = referralDataObj.optString("calling_source", "");                  // calling Source

            if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "Referrer data: packageName = " + packageName + ", referrerPresent = " + referrerPresent + ", skipReferral = " + skipReferral + ", bstClickTime = " + bstClickTime + ", firstInstallTime = " + firstInstallTime + ", current time = " + System.currentTimeMillis() / 1000 + ", source = " + source);
            if (mBstAffiliateTestingValue == 5) {
                skipReferral = true;
            }
            // Skip referral flag present or not in cloud?
            if (skipReferral) {
                reason = "skipReferral true packageName : " + packageName;
                response.put("reason", reason);

                if (DEBUG_BST_REFERRAL) Log.w(TAG_BST_REFERRAL, "Not doing any modification for packageName = " + packageName + ", reason = " + reason);
                return response.toString();
            }

            if (mBstAffiliateTestingValue == 6) {
                bstClickTime = 0;
            }
            // basic check for final URL hit time
            if (bstClickTime <= 0) {
                reason = "Invalid final_url_hit_time (bstClickTime) " + bstClickTime;
                response.put("reason", reason);

                if (DEBUG_BST_REFERRAL) Log.w(TAG_BST_REFERRAL, "Not doing any modification for packageName = " + packageName + ", reason = " + reason);

                return response.toString();
            }

            if (mBstAffiliateTestingValue == 7) {
                bstClickTime = firstInstallTime - 1;
            }

            // referrer_click_timestamp_seconds < install_begin_timestamp_seconds < packageInstallTime
            if ((firstInstallTime - bstClickTime) < 3) {
                reason = "firstInstallTime : " + firstInstallTime + " close to bstClickTime : " + bstClickTime;
                response.put("reason", reason);

                if (DEBUG_BST_REFERRAL) Log.w(TAG_BST_REFERRAL, "Not doing any modification for packageName = " + packageName + ", firstInstallTime = " + firstInstallTime + ", current time = " + System.currentTimeMillis() / 1000 + ", reason = " + reason);
                return response.toString();
            }

            if (mBstAffiliateTestingValue == 8) {
                throw new Exception("Exception based on debug affiliate value");
            }

            if (source.equals("packageEnqueued") && installReferrerValue != null
                    && installReferrerValue.contains("not%20set")) {
                if (DEBUG_BST_REFERRAL) Log.w(TAG_BST_REFERRAL, "GoogleAd? packageName = " + packageName + ", reason = installed via googleAd: actual_install_referrer = " + installReferrerValue);
            }

            if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "Modifying the values for packageName = " + packageName);
            response.put("success", true);
            return response.toString();
        } catch (Exception ex) {
            Log.w(TAG_BST_REFERRAL, "Exception while checking if we should modify the referral values: " + ex.getMessage());
            if (DEBUG_BST_REFERRAL) ex.printStackTrace();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            return "{\"success\": \"false\",\"reason\": \"exception: " + ex.getMessage() + sw.toString() + " \"}";
        }
    }

    /* This function checks if referrer for the package is already present.
     * If true we will not send the click event and will not drop the
     * original INSTALL_REFERRER intent.
     * @hide
     */
    public static boolean thirdPartyReferred(String pkgName) {
        HashSet<String> bstReferrerPresent = new HashSet<String>();
        bstReferrerPresent  = (HashSet<String>) loadListFromFile("/data/downloads/.aff/.prf", bstReferrerPresent);
        boolean bstThirdPartyReferrerPresent = ((bstReferrerPresent != null && bstReferrerPresent.contains(pkgName))
                || pkgName.equals(SystemProperties.get("bst.config.referrerpackage")));
        if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "pkgName = " + pkgName + ", bstThirdPartyReferrerPresent = " + bstThirdPartyReferrerPresent);

        if (mBstAffiliateTestingValue == 3) {
            bstThirdPartyReferrerPresent = true;
        }

        return bstThirdPartyReferrerPresent;
    }

    /*
     * Helper function to load a list from the file
     * @hide
     */
    public static Object loadListFromFile(String fileName, Object listObject) {
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        ByteArrayInputStream bais = null;
        Object resultObject = listObject;
        HashMap<String, String> encodedStringObject = null;
        try {
            File file = new File(fileName);
            // file == null is always true
            if (!file.exists()) {
                if (DEBUG_BST_REFERRAL) Log.d(TAG_BST_REFERRAL, "file : " + file + " does not exist so returning");
                return resultObject;
            }

            fis = new FileInputStream(file);
            ois = new ObjectInputStream(fis);
            encodedStringObject = (HashMap<String, String>) ois.readObject();
            ois.close();
            String encoded = encodedStringObject.get("object");
            byte[] decoded = Base64.decode(encoded.getBytes(), 1);
            bais = new ByteArrayInputStream(decoded);
            ois = new ObjectInputStream(bais);
            resultObject = ois.readObject();
        } catch (Exception e) {
            Log.w(TAG_BST_REFERRAL, "error in loading list from file " + fileName);
            if (DEBUG_BST_REFERRAL) e.printStackTrace();
        } finally {
            try {
                if (ois != null)
                    ois.close();
                if (fis != null)
                    fis.close();
                if (bais != null)
                    bais.close();
            } catch (Exception e) {
                //Ignore
                Log.d(TAG_BST_REFERRAL, "exception: " + e.getMessage());
                if (DEBUG_BST_REFERRAL) e.printStackTrace();
            }
        }
        if (DEBUG) Log.d(TAG_BST_REFERRAL, "in loadListFromFile, resultObject dump: " + resultObject);
        return resultObject;
    }


    /*
     * Helper function to write listObject to file fileName
     * @hide
     */
    public static boolean  writeListToFile(Object listObject, String fileName) {
        FileOutputStream fos = null;
        ByteArrayOutputStream fileOut = null;
        ObjectOutputStream out = null;
        HashMap<String, String> encodedStringObject = new HashMap<String, String>();
        try {
            File file = new File(fileName);
            fileOut = new ByteArrayOutputStream();
            out = new ObjectOutputStream(fileOut);
            out.writeObject(listObject);
            String encoded = new String(Base64.encode(fileOut.toByteArray(), 1));
            out.close();
            encodedStringObject.put("object", encoded);
            fos = new FileOutputStream(file);
            out = new ObjectOutputStream(fos);
            out.writeObject(encodedStringObject);
            if (FileUtils.setPermissions(fileName,
                        FileUtils.S_IRUSR | FileUtils.S_IWUSR |
                        FileUtils.S_IRGRP | FileUtils.S_IROTH, -1, -1) != 0) {
                Log.e(TAG, "Failed to change permissions for the file");
                return false;
            }
            if (DEBUG_BST_REFERRAL) Log.d(TAG, "Serialized appreferral data is saved in " + fileName);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (out != null)
                    out.close();
                if (fileOut != null)
                    fileOut.close();
                if (fos != null)
                    fos.close();
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
            if (DEBUG_BST_REFERRAL) Log.d(TAG, "ListDump: " + listObject);
        }
        return false;
    }

    /**
     * Returns the serial number of the SIM, if applicable and returns null if it is
     * unavailable.
     */
    public static String getBstSimSerialNumber(){
        // Creating fake Sim ICC over here as we don't have real SIM card
        // ICCID number is of 19 or 20 digits long and is normally printed on the SIM Card.
        // Sample ICCID number =                                89 + CC + Issuer Identifier + Individual Account Identifier
        // Sample (for US T-Mobile Sim: )                       89 + 01 + 260 + 462481589557    (19 digit)
        // Sample (for Canada Rogers Sim: )                     89 + 302 + 720 + 513002151866 (20-digit)
        // Sample (for Japan SoftBank Sim: )                    89 + 81 + 200 + 513674691577 (19-digit)
        // Sample (for Ireland O2 Sim: )                        89 + 353 + 02 + 133530350621 (19-digit)
        // Sample (for NewZealand XT Mobile Telecom Sim: )      89 + 64 + 050 + 021903427310 (19-digit)

        //String BstSimSerialNumber = "8901260";  //for T-Mobile USA
        //String BstSimSerialNumber = "89302720";  //for Rogers Canada
        //String BstSimSerialNumber = "8981200";  //for SoftBank Japan
        //String BstSimSerialNumber = "8935302";  //for O2 Ireland
        //String BstSimSerialNumber = "8964050";  //for XT Mobile Telecom NewZealand
        if (bstSimSerialNumber != null && bstSimSerialNumber.trim().length() > 18) {
            return bstSimSerialNumber;
        }

        bstSimSerialNumber = SystemProperties.get("gsm.sim.bstserial", "8901260");
        String bstSimIssuerId = SystemProperties.get("bst.sim_issuer_id", "");
        String bstSimSuffix = SystemProperties.get("bst.sim_suffix", "");
        bstSimSerialNumber += bstSimIssuerId + bstSimSuffix;
        if(DEBUG) Log.d (TAG, "simSerialNumber: " + bstSimSerialNumber + " bstSimSuffix: "
                + bstSimSuffix + " bstSimIssuerId: " + bstSimIssuerId);
        return bstSimSerialNumber;
    }

    // This function is for hiding BlueStacks specific packages from other apps apart from bluestacks app or android default app
    // returns true - hide bluestacks package related info
    public static boolean hideBlueStacksPkg(String packageName, boolean isAppPrivileged) {
        if (DEBUG_PACKAGE_INFO && packageName != null) Log.v(TAG, "checking for bluestacks app : " + packageName + " calling package isAppPrivileged ? " + isAppPrivileged);
        return !isAppPrivileged && !BST_SHOW_APPS && (packageName != null && packageName.startsWith("com.bluestacks") );
    }

    // Function returns whether or not calling package can see Bluestacks package related info
    public static boolean bstIsCallingAppPrivileged(int callingUid, String callingApp) {
        if (DEBUG_PACKAGE_INFO && callingApp != null) Log.v(TAG, "checking if callingApp : " + callingApp + " is privileged or not : ");
        return (callingUid == Process.SYSTEM_UID ||
                (callingApp == null ||
                 (callingApp.equals("zygote")
                  || callingApp.equals("<pre-initialized>")
                  || callingApp.equals("system_server")
                  || callingApp.startsWith("com.uncube.")
                  || callingApp.startsWith("com.bluestacks.")
                  || callingApp.startsWith("com.google.")
                  || callingApp.startsWith("com.android.")
                  || callingApp.startsWith("android.process.media"))));
    }

    // Function returns custom dpi value if we config dpi entry for specific app
    public static int GetCustomDpi() {
        try {
            int ppid = android.os.Process.myPpid();
            if (ppid != 1 && ppid != 2 && Binder.getCallingUid() >= 10000) {
                String CustomDpi = "";
                int pid = android.os.Process.myPid();
                String callingApp = BstUtils.getAppNameFromPid(pid);
                IBstFilterAppsService bstfilter = IBstFilterAppsService.Stub.asInterface(ServiceManager.getService(Context.BST_FILTER_APPS));
                if (bstfilter != null) CustomDpi = bstfilter.getCustomDpi(callingApp);
                if (CustomDpi.equals("160") || CustomDpi.equals("240") || CustomDpi.equals("320") || CustomDpi.equals("400") || CustomDpi.equals("480")) {
                    int CustomDpiValue = Integer.parseInt(CustomDpi);
                    if (DEBUG) Log.d(TAG, "Fake density:" + CustomDpi + " for pkg:" + callingApp);
                    return CustomDpiValue;
                }
            }
        } catch (Exception ex) {
            Log.d (TAG, "Exception while Fake density: " + ex);
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * Filter out hidden accessibility services from the list
     * This method hides specified services from third-party applications
     */
    public static List<AccessibilityServiceInfo> filterHiddenServices(
            List<AccessibilityServiceInfo> services, int callingUid) {
        if (!HIDE_ACCESSIBILITY_SERVICES || services == null || services.isEmpty()) {
            return services;
        }

        // If caller is not third-party, return original list
        if (callingUid < Process.FIRST_APPLICATION_UID) {
            if (DEBUG) {
                Slog.i(TAG,
                        "filterHiddenServices: system caller detected, skipping filter for UID: "
                                + callingUid);
            }
            return services;
        }

        if (DEBUG) {
            Slog.i(TAG, "filterHiddenServices: filtering for third-party caller UID: " + callingUid
                    + ", input count: " + services.size());
        }

        List<AccessibilityServiceInfo> filteredServices = new ArrayList<>();
        int filteredCount = 0;

        for (AccessibilityServiceInfo service : services) {
            try {
                ResolveInfo resolveInfo = service.getResolveInfo();
                if (resolveInfo == null || resolveInfo.serviceInfo == null) {
                    filteredServices.add(service);
                    continue;
                }

                String packageName = resolveInfo.serviceInfo.packageName;

                if (packageName.startsWith("gg.now.") || packageName.startsWith("com.bluestacks.")) {
                    if (DEBUG) {
                        Slog.i(TAG, "Hiding accessibility service from third-party app: "
                                + packageName + " for UID: " + callingUid);
                    }
                    filteredCount++;
                } else {
                    filteredServices.add(service);
                }
            } catch (Exception e) {
                Slog.e(TAG, "Error while filtering accessibility service, allowing service", e);
                filteredServices.add(service);
            }
        }

        if (DEBUG) {
            Slog.i(TAG, "filterHiddenServices: filtered " + filteredCount
                    + " services, remaining: " + filteredServices.size()
                    + " for UID: " + callingUid);
        }

        return filteredServices;
    }

    public static String filterHiddenServices(String enabledServices, int callingUid) {
        if (!HIDE_ACCESSIBILITY_SERVICES || enabledServices == null || enabledServices.isEmpty()) {
            return enabledServices;
        }

        if (callingUid < Process.FIRST_APPLICATION_UID) {
            if (DEBUG) {
                Slog.i(TAG,
                        "filterHiddenServices: system caller detected, skipping filter for UID: "
                                + callingUid);
            }
            return enabledServices;
        }

        String[] services = enabledServices.split(":");
        StringBuilder filtered = new StringBuilder();

        for (String service : services) {
            if (service.isEmpty() || service.startsWith("gg.now.")
                    || service.startsWith("com.bluestacks.")) {
                continue;
            }

            if (filtered.length() > 0) {
                filtered.append(':');
            }
            filtered.append(service);
        }

        if (DEBUG) {
            Slog.i(TAG, "filterHiddenServices: final accessibility services: " + filtered);
        }

        return filtered.toString();
    }

    public static List<ResolveInfo> applyAccessibilityServiceFilter(
            List<ResolveInfo> resolveInfos, int callingUid) {
        if (!HIDE_ACCESSIBILITY_SERVICES || resolveInfos == null || resolveInfos.isEmpty()) {
            return resolveInfos;
        }

        if (DEBUG) {
            Slog.d(TAG, "applyAccessibilityServiceFilter called by UID: " + callingUid
                    + ", input count: " + resolveInfos.size());
        }

        if (callingUid < Process.FIRST_APPLICATION_UID) {
            if (DEBUG) {
                Slog.d(TAG, "Caller is system UID: " + callingUid);
            }
            return resolveInfos;
        }

        int filteredCount = 0;
        for (int i = resolveInfos.size() - 1; i >= 0; i--) {
            final ResolveInfo info = resolveInfos.get(i);
            if (info != null && info.serviceInfo != null) {
                String packageName = info.serviceInfo.packageName;
                String serviceName = info.serviceInfo.name;

                if (packageName.startsWith("gg.now.") || packageName.startsWith("com.bluestacks.")) {
                    String fullServiceName = packageName + "/" + serviceName;
                    if (DEBUG) {
                        Slog.d(TAG, "Filtering out hidden accessibility service: " + fullServiceName
                                + " for UID: " + callingUid);
                    }
                    resolveInfos.remove(i);
                    filteredCount++;
                }
            }
        }

        if (DEBUG) {
            Slog.d(TAG, "applyAccessibilityServiceFilter filtered " + filteredCount
                    + " services, remaining: " + resolveInfos.size() + " for UID: " + callingUid);
        }

        return resolveInfos;
    }
}
