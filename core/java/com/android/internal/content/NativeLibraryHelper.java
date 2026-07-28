/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.internal.content;

import static android.content.pm.PackageManager.INSTALL_FAILED_NO_MATCHING_ABIS;
import static android.content.pm.PackageManager.INSTALL_SUCCEEDED;
import static android.content.pm.PackageManager.NO_NATIVE_LIBRARIES;
import static android.system.OsConstants.S_IRGRP;
import static android.system.OsConstants.S_IROTH;
import static android.system.OsConstants.S_IRWXU;
import static android.system.OsConstants.S_IXGRP;
import static android.system.OsConstants.S_IXOTH;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.parsing.ApkLiteParseUtils;
import android.content.pm.parsing.PackageLite;
import android.content.pm.parsing.result.ParseResult;
import android.content.pm.parsing.result.ParseTypeImpl;
import android.os.Build;
import android.os.IBinder;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.incremental.IIncrementalService;
import android.os.incremental.IncrementalManager;
import android.os.incremental.IncrementalStorage;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Slog;

import dalvik.system.CloseGuard;
import dalvik.system.VMRuntime;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.pm.PackageParser;
import android.os.FileUtils;
import android.os.SystemProperties;
import android.util.Features;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.bluestacks.os.IBstFilterAppsService;
import static com.bluestacks.os.BstFilterAppsManager.ABI_ERROR;
import static com.bluestacks.os.BstFilterAppsManager.ARM_MODE;
import static com.bluestacks.os.BstFilterAppsManager.X86_MODE;
import static com.bluestacks.os.BstFilterAppsManager.ARM_32_MODE;
import static com.bluestacks.os.BstFilterAppsManager.X86_32_MODE;
import static com.bluestacks.os.BstFilterAppsManager.ARM_64_MODE;
import static com.bluestacks.os.BstFilterAppsManager.X86_64_MODE;
import static com.bluestacks.os.BstFilterAppsManager.XARM_MODE;

class ABIResponse {
    boolean haveUnityLibs = false;
    boolean installAppInArmMode = false;

    public ABIResponse(boolean haveUnityLibs, boolean installAppInArmMode) {
        this.haveUnityLibs = haveUnityLibs;
        this.installAppInArmMode = installAppInArmMode;
    }

    public boolean checkIfUnityLibsPresent() {
        return haveUnityLibs;
    }

    public boolean isAppInstallModeArm() {
        return installAppInArmMode;
    }
}

/**
 * Native libraries helper.

/**
 * Native libraries helper.
 *
 * @hide
 */
public class NativeLibraryHelper {
    private static final String TAG = "NativeHelper";
    private static final boolean DEBUG_NATIVE = false;

    public static final String LIB_DIR_NAME = "lib";
    public static final String LIB64_DIR_NAME = "lib64";

    // Special value for indicating that the cpuAbiOverride must be clear.
    public static final String CLEAR_ABI_OVERRIDE = "-";

    // A16DBG:P2:FW-CORE-APP-14 BST native lib ABI override (a13)
    private static final boolean BST_DEBUG =
            SystemProperties.getInt("bst.debug.nativelibhelper", 0) > 0;
    private static boolean is32BitArch = Build.SUPPORTED_64_BIT_ABIS.length == 0;
    private static final String cpuAbiX86_64 = "x86_64";
    private static final String cpuAbiX86 = "x86";
    private static final String cpuAbiArmv8_64 = "arm64-v8a";
    private static final String cpuAbiArmv7 = "armeabi-v7a";
    private static final String cpuAbiArm = "armeabi";

    static IBstFilterAppsService bstfilter;

    /**
     * A handle to an opened package, consisting of one or more APKs. Used as
     * input to the various NativeLibraryHelper methods. Allows us to scan and
     * parse the APKs exactly once instead of doing it multiple times.
     *
     * @hide
     */
    public static class Handle implements Closeable {
        private final CloseGuard mGuard = CloseGuard.get();
        private volatile boolean mClosed;

        final String[] apkPaths;
        final long[] apkHandles;
        final boolean multiArch;
        final boolean extractNativeLibs;
        final boolean debuggable;

        final boolean pageSizeCompatDisabled;
        final String pkgName;
        final String apkDir;

        public static Handle create(File packageFile) throws IOException {
            final ParseTypeImpl input = ParseTypeImpl.forDefaultParsing();
            final ParseResult<PackageLite> ret = ApkLiteParseUtils.parsePackageLite(input.reset(),
                    packageFile, /* flags */ 0);
            if (ret.isError()) {
                throw new IOException("Failed to parse package: " + packageFile,
                        ret.getException());
            }
            return create(ret.getResult());
        }

        public static Handle create(PackageLite lite) throws IOException {
            boolean isPageSizeCompatDisabled = lite.getPageSizeCompat()
                    == ApplicationInfo.PAGE_SIZE_APP_COMPAT_FLAG_MANIFEST_OVERRIDE_DISABLED;
            return create(lite.getAllApkPaths(), lite.isMultiArch(), lite.isExtractNativeLibs(),
                    lite.isDebuggable(), isPageSizeCompatDisabled, lite.getPackageName(), lite.getPath());
        }

        public static Handle create(List<String> codePaths, boolean multiArch,
                boolean extractNativeLibs, boolean debuggable, boolean isPageSizeCompatDisabled,
                String pkgName, String apkDir)
                throws IOException {
            final int size = codePaths.size();
            final String[] apkPaths = new String[size];
            final long[] apkHandles = new long[size];
            for (int i = 0; i < size; i++) {
                final String path = codePaths.get(i);
                apkPaths[i] = path;
                apkHandles[i] = nativeOpenApk(path);
                if (apkHandles[i] == 0) {
                    // Unwind everything we've opened so far
                    for (int j = 0; j < i; j++) {
                        nativeClose(apkHandles[j]);
                    }
                    throw new IOException("Unable to open APK: " + path);
                }
            }

            if (pkgName == null && apkDir == null && !codePaths.isEmpty()) {
                apkDir = codePaths.get(0);
                if (apkDir != null && apkDir.endsWith(".apk")) {
                    apkDir = new File(apkDir).getParent();
                }
            }
            return new Handle(apkPaths, apkHandles, multiArch, extractNativeLibs, debuggable,
                    isPageSizeCompatDisabled, pkgName, apkDir);
        }

        public static Handle create(List<String> codePaths, boolean multiArch,
                boolean extractNativeLibs, boolean debuggable, boolean isPageSizeCompatDisabled)
                throws IOException {
            return create(codePaths, multiArch, extractNativeLibs, debuggable,
                    isPageSizeCompatDisabled, null, null);
        }

        public static Handle createFd(PackageLite lite, FileDescriptor fd) throws IOException {
            final long[] apkHandles = new long[1];
            final String path = lite.getBaseApkPath();
            apkHandles[0] = nativeOpenApkFd(fd, path);
            if (apkHandles[0] == 0) {
                throw new IOException("Unable to open APK " + path + " from fd " + fd);
            }

            boolean isPageSizeCompatDisabled = lite.getPageSizeCompat()
                    == ApplicationInfo.PAGE_SIZE_APP_COMPAT_FLAG_MANIFEST_OVERRIDE_DISABLED;

            return new Handle(new String[]{path}, apkHandles, lite.isMultiArch(),
                    lite.isExtractNativeLibs(), lite.isDebuggable(), isPageSizeCompatDisabled,
                    lite.getPackageName(), lite.getPath());
        }

        Handle(String[] apkPaths, long[] apkHandles, boolean multiArch,
                boolean extractNativeLibs, boolean debuggable, boolean isPageSizeCompatDisabled,
                String pkgName, String apkDir) {
            this.apkPaths = apkPaths;
            this.apkHandles = apkHandles;
            this.multiArch = multiArch;
            this.extractNativeLibs = extractNativeLibs;
            this.debuggable = debuggable;
            this.pageSizeCompatDisabled = isPageSizeCompatDisabled;
            this.pkgName = pkgName;
            this.apkDir = apkDir;
            mGuard.open("close");
        }

        @Override
        public void close() {
            for (long apkHandle : apkHandles) {
                nativeClose(apkHandle);
            }
            mGuard.close();
            mClosed = true;
        }

        @Override
        protected void finalize() throws Throwable {
            if (mGuard != null) {
                mGuard.warnIfOpen();
            }
            try {
                if (!mClosed) {
                    close();
                }
            } finally {
                super.finalize();
            }
        }
    }

    private static native long nativeOpenApk(String path);
    private static native long nativeOpenApkFd(FileDescriptor fd, String debugPath);
    private static native void nativeClose(long handle);

    private static native long nativeSumNativeBinaries(long handle, String cpuAbi);

    private native static int nativeCopyNativeBinaries(long handle, String sharedLibraryPath,
            String abiToCopy, boolean extractNativeLibs, boolean debuggable,
            boolean pageSizeCompatDisabled);

    private static native AlignmentResult nativeCheckAlignment(
            long handle,
            String sharedLibraryPath,
            String abi,
            boolean extractNativeLibs,
            boolean debuggable);

    private static long sumNativeBinaries(Handle handle, String abi) {
        long sum = 0;
        for (long apkHandle : handle.apkHandles) {
            sum += nativeSumNativeBinaries(apkHandle, abi);
        }
        return sum;
    }

    /**
     * Copies native binaries to a shared library directory.
     *
     * @param handle APK file to scan for native libraries
     * @param sharedLibraryDir directory for libraries to be copied to
     * @return {@link PackageManager#INSTALL_SUCCEEDED} if successful or another
     *         error code from that class if not
     */
    public static int copyNativeBinaries(Handle handle, File sharedLibraryDir, String abi) {
        for (long apkHandle : handle.apkHandles) {
            int res = nativeCopyNativeBinaries(apkHandle, sharedLibraryDir.getPath(), abi,
                    handle.extractNativeLibs, handle.debuggable, handle.pageSizeCompatDisabled);
            if (res != INSTALL_SUCCEEDED) {
                return res;
            }
        }
        return INSTALL_SUCCEEDED;
    }

    // bstAbiList: Try first with bst modified abi list, if valid index found, return, else
    // abiList: Try with system default abi List.
    public static int findSupportedAbi(Handle handle, String[] supportedAbis, String[] bstAbiList) {
        int copyRet = 0, i;

        if (bstAbiList != null) {
            if (BST_DEBUG) Slog.d(TAG, "findSupportedAbi wrapper called for " + handle.apkDir + " with bst abi list" + Arrays.toString(bstAbiList));
            // We have a bst overridden list
            copyRet = findSupportedAbi(handle, bstAbiList);
            if (BST_DEBUG) Slog.d(TAG, "findSupportedAbi return " + copyRet + " for bstAbiList");
        }

        if (copyRet == NO_NATIVE_LIBRARIES)
            return copyRet;

        if (bstAbiList == null || (copyRet < 0 && copyRet != NO_NATIVE_LIBRARIES)) {
            // Either bstAbiList is null or We have some error with bstAbiList, try with default abiList
            if (BST_DEBUG) Slog.d(TAG, "findSupportedAbi wrapper, trying default list");
            copyRet = findSupportedAbi(handle, supportedAbis);
        } else {
            // Determine the abi index corresponding to actual system abiList rather than bstAbiList.
            if (BST_DEBUG) Slog.d(TAG, "findSupportedAbi called successfully for bstAbiList");
            String determinedAbi = bstAbiList[copyRet];
            for (i = 0; i < supportedAbis.length; i++) {
                if (determinedAbi.equals(supportedAbis[i])) {
                    copyRet = i;
                    break;
                }
            }
        }

        if (BST_DEBUG) {
            if (copyRet == NO_NATIVE_LIBRARIES)
                Slog.e(TAG, "findSupportedAbi wrapper abi return: " + copyRet + " for : " + handle.apkDir);
            else
                Slog.e(TAG, "findSupportedAbi wrapper abi return: " + supportedAbis[copyRet] + " for : " + handle.apkDir);
        }
        return copyRet;
    }

    /**
     * Checks if a given APK contains native code for any of the provided
     * {@code supportedAbis}. Returns an index into {@code supportedAbis} if a matching
     * ABI is found, {@link PackageManager#NO_NATIVE_LIBRARIES} if the
     * APK doesn't contain any native code, and
     * {@link PackageManager#INSTALL_FAILED_NO_MATCHING_ABIS} if none of the ABIs match.
     */
    public static int findSupportedAbi(Handle handle, String[] supportedAbis) {
        int finalRes = NO_NATIVE_LIBRARIES;
        for (long apkHandle : handle.apkHandles) {
            final int res = nativeFindSupportedAbi(apkHandle, supportedAbis);
            if (res == NO_NATIVE_LIBRARIES) {
                // No native code, keep looking through all APKs.
            } else if (res == INSTALL_FAILED_NO_MATCHING_ABIS) {
                // Found some native code, but no ABI match; update our final
                // result if we haven't found other valid code.
                if (finalRes < 0) {
                    finalRes = INSTALL_FAILED_NO_MATCHING_ABIS;
                }
            } else if (res >= 0) {
                // Found valid native code, track the best ABI match
                if (finalRes < 0 || res < finalRes) {
                    finalRes = res;
                }
            } else {
                // Unexpected error; bail
                return res;
            }
        }
        return finalRes;
    }

    private native static int nativeFindSupportedAbi(long handle, String[] supportedAbis);

    // Convenience method to call removeNativeBinariesFromDirLI(File)
    public static void removeNativeBinariesLI(String nativeLibraryPath) {
        if (nativeLibraryPath == null) return;
        removeNativeBinariesFromDirLI(new File(nativeLibraryPath), false /* delete root dir */);
    }

    /**
     * Remove the native binaries of a given package. This deletes the files
     */
    public static void removeNativeBinariesFromDirLI(File nativeLibraryRoot,
            boolean deleteRootDir) {
        if (DEBUG_NATIVE) {
            Slog.w(TAG, "Deleting native binaries from: " + nativeLibraryRoot.getPath());
        }

        /*
         * Just remove any file in the directory. Since the directory is owned
         * by the 'system' UID, the application is not supposed to have written
         * anything there.
         */
        if (nativeLibraryRoot.exists()) {
            final File[] files = nativeLibraryRoot.listFiles();
            if (files != null) {
                for (int nn = 0; nn < files.length; nn++) {
                    if (DEBUG_NATIVE) {
                        Slog.d(TAG, "    Deleting " + files[nn].getName());
                    }

                    if (files[nn].isDirectory()) {
                        removeNativeBinariesFromDirLI(files[nn], true /* delete root dir */);
                    } else if (!files[nn].delete()) {
                        Slog.w(TAG, "Could not delete native binary: " + files[nn].getPath());
                    }
                }
            }
            // Do not delete 'lib' directory itself, unless we're specifically
            // asked to or this will prevent installation of future updates.
            if (deleteRootDir) {
                if (!nativeLibraryRoot.delete()) {
                    Slog.w(TAG, "Could not delete native binary directory: " +
                            nativeLibraryRoot.getPath());
                }
            }
        }
    }

    /**
     * @hide
     */
    public static void createNativeLibrarySubdir(File path) throws IOException {
        if (!path.isDirectory()) {
            path.delete();

            if (!path.mkdir()) {
                throw new IOException("Cannot create " + path.getPath());
            }

            try {
                Os.chmod(path.getPath(), S_IRWXU | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH);
            } catch (ErrnoException e) {
                throw new IOException("Cannot chmod native library directory "
                        + path.getPath(), e);
            }
        } else if (!SELinux.restorecon(path)) {
            throw new IOException("Cannot set SELinux context for " + path.getPath());
        }
    }

    private static long sumNativeBinariesForSupportedAbi(Handle handle, String[] abiList) {
        int abi;
        if (!handle.multiArch) {
            String[] bstAbiList = getBstAbiOverride(handle.apkDir, null);
            // BST: The abi index returned here will be according to the default abiList, hence
            // no further sanity needed.
            abi = findSupportedAbi(handle, abiList, bstAbiList);
        } else
            abi = findSupportedAbi(handle, abiList);

        if (BST_DEBUG && abi >= 0) Slog.d(TAG, "sumNativeBinariesForSupportedAbi abi: " + abiList[abi] + " for pkg: " + handle.apkDir);

        if (abi >= 0) {
            if ((abiList[abi].equals(cpuAbiArmv8_64) || abiList[abi].equals(cpuAbiArmv7) || abiList[abi].equals(cpuAbiArm))
                    && (!handle.multiArch) && isAppHavingXArmLibs(handle)) {
                String determinedAbi = (abiList[abi].equals(cpuAbiArmv8_64)) ? cpuAbiX86_64 : cpuAbiX86;
                return sumNativeBinaries(handle, determinedAbi);
            } else {
                return sumNativeBinaries(handle, abiList[abi]);
            }
        } else {
            return 0;
        }
    }
    // BST wrapper
    // Trying with our modified bstAbiList. If installation fails, retrying with system
    // default abiList.
    public static int copyNativeBinariesForSupportedAbi(Handle handle, File libraryRoot,
            String[] abiList, String[] bstAbiList, boolean useIsaSubdir, boolean isIncremental) throws IOException {
        int copyRet = 0, i;

        if (bstAbiList != null) {
            if (BST_DEBUG) Slog.d(TAG, "inside copyNativeBinariesForSupportedAbi wrapper for: " + handle.apkDir + " def abi: " +
                    Arrays.toString(abiList) + " bst abi list: " + Arrays.toString(bstAbiList));
            copyRet = copyNativeBinariesForSupportedAbi(handle, libraryRoot, bstAbiList, useIsaSubdir,isIncremental);
            if (BST_DEBUG) Slog.d(TAG, "copyNativeBinariesForSupportedAbi return: " + copyRet + " with bstAbiList");
        }

        if (copyRet == NO_NATIVE_LIBRARIES)
            return copyRet;

        if (bstAbiList == null || (copyRet < 0 && copyRet != NO_NATIVE_LIBRARIES)) {
            // Either bstAbiList is null or We have some error with bstAbiList, try with default abiList
            if (BST_DEBUG) Slog.d(TAG, "copyNativeBinariesForSupportedAbi wrapper, trying default list");
            copyRet = copyNativeBinariesForSupportedAbi(handle, libraryRoot, abiList, useIsaSubdir, isIncremental);
        } else {
            // Determine the abi index corresponding to actual system abiList rather than bstAbiList.
            if (BST_DEBUG) Slog.d(TAG, "copyNativeBinariesForSupportedAbi called successfully for bstAbiList");
            String determinedAbi = bstAbiList[copyRet];
            int adbiListIdx = PackageManager.INSTALL_FAILED_NO_MATCHING_ABIS;
            for (i = 0; i < abiList.length; i++) {
                if (determinedAbi.equals(abiList[i])) {
                    adbiListIdx = i;
                    if (BST_DEBUG) Slog.e(TAG, "determined abi index: " + i + " and abi value: " + abiList[i]);
                    break;
                }
            }
            copyRet = adbiListIdx;
        }

        if (copyRet < 0)
            return copyRet;

        //BS4-12736: in bgp64, fix for il2cpp arm apps which requires oh:true(removing oh:true config dependency)
        updateIl2cpp(abiList[copyRet], libraryRoot, handle.apkDir);
        createArmMarker(abiList[copyRet], libraryRoot, useIsaSubdir);
        return copyRet;
    }


    public static int copyNativeBinariesForSupportedAbi(Handle handle, File libraryRoot,
            String[] abiList, boolean useIsaSubdir, boolean isIncremental) throws IOException {
        /*
         * If this is an internal application or our nativeLibraryPath points to
         * the app-lib directory, unpack the libraries if necessary.
         */
        int abi = findSupportedAbi(handle, abiList);
        if (abi < 0) {
            return abi;
        }

        /*
         * If we have a matching instruction set, construct a subdir under the native
         * library root that corresponds to this instruction set.
         */
        final String supportedAbi = abiList[abi];
        final String instructionSet = VMRuntime.getInstructionSet(supportedAbi);
        final File subDir;
        if (useIsaSubdir) {
            subDir = new File(libraryRoot, instructionSet);
        } else {
            subDir = libraryRoot;
        }

        if (isIncremental) {
            int res =
                    incrementalConfigureNativeBinariesForSupportedAbi(handle, subDir, supportedAbi);
            if (res != PackageManager.INSTALL_SUCCEEDED) {
                // TODO(b/133435829): the caller of this function expects that we return the index
                // to the supported ABI. However, any non-negative integer can be a valid index.
                // We should fix this function and make sure it doesn't accidentally return an error
                // code that can also be a valid index.
                return res;
            }
            return abi;
        }

        // For non-incremental, use regular extraction and copy
        createNativeLibrarySubdir(libraryRoot);
        if (subDir != libraryRoot) {
            createNativeLibrarySubdir(subDir);
        }

        // Even if extractNativeLibs is false, we still need to check if the native libs in the APK
        // are valid. This is done in the native code.
        int copyRet = copyNativeBinaries(handle, subDir, supportedAbi);
        if (copyRet != PackageManager.INSTALL_SUCCEEDED) {
            return copyRet;
        }

        return abi;
    }

    public static int copyNativeBinariesWithOverride(Handle handle, File libraryRoot,
            String abiOverride, boolean isIncremental) {
        try {
            if (handle.multiArch) {
                // Warn if we've set an abiOverride for multi-lib packages..
                // By definition, we need to copy both 32 and 64 bit libraries for
                // such packages.
                if (abiOverride != null && !CLEAR_ABI_OVERRIDE.equals(abiOverride)) {
                    Slog.w(TAG, "Ignoring abiOverride for multi arch application.");
                }

                int copyRet = PackageManager.NO_NATIVE_LIBRARIES;
                if (Build.SUPPORTED_32_BIT_ABIS.length > 0) {
                    copyRet = copyNativeBinariesForSupportedAbi(handle, libraryRoot,
                            Build.SUPPORTED_32_BIT_ABIS, true /* use isa specific subdirs */,
                            isIncremental);
                    if (copyRet < 0 && copyRet != PackageManager.NO_NATIVE_LIBRARIES &&
                            copyRet != PackageManager.INSTALL_FAILED_NO_MATCHING_ABIS) {
                        Slog.w(TAG, "Failure copying 32 bit native libraries; copyRet=" +copyRet);
                        return copyRet;
                    }
                }

                if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                    copyRet = copyNativeBinariesForSupportedAbi(handle, libraryRoot,
                            Build.SUPPORTED_64_BIT_ABIS, true /* use isa specific subdirs */,
                            isIncremental);
                    if (copyRet < 0 && copyRet != PackageManager.NO_NATIVE_LIBRARIES &&
                            copyRet != PackageManager.INSTALL_FAILED_NO_MATCHING_ABIS) {
                        Slog.w(TAG, "Failure copying 64 bit native libraries; copyRet=" +copyRet);
                        return copyRet;
                    }
                }
            } else {
                String cpuAbiOverride = null;
                if (CLEAR_ABI_OVERRIDE.equals(abiOverride)) {
                    cpuAbiOverride = null;
                } else if (abiOverride != null) {
                    cpuAbiOverride = abiOverride;
                }

                String[] bstAbiList = getBstAbiOverride(handle.apkDir, handle.pkgName);

                String[] abiList = (cpuAbiOverride != null) ?
                        new String[] { cpuAbiOverride } : Build.SUPPORTED_ABIS;
                if (Build.SUPPORTED_64_BIT_ABIS.length > 0 && cpuAbiOverride == null &&
                        hasRenderscriptBitcode(handle)) {
                    abiList = Build.SUPPORTED_32_BIT_ABIS;
                }

                int copyRet = copyNativeBinariesForSupportedAbi(handle, libraryRoot, abiList,
                        bstAbiList, true /* use isa specific subdirs */, isIncremental);
                if (copyRet < 0 && copyRet != PackageManager.NO_NATIVE_LIBRARIES) {
                    Slog.w(TAG, "Failure copying native libraries [errorCode=" + copyRet + "]");
                    return copyRet;
                }
            }

            return PackageManager.INSTALL_SUCCEEDED;
        } catch (IOException e) {
            Slog.e(TAG, "Copying native libraries failed", e);
            return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }
    }

    /**
     * Checks alignment of APK and native libraries for 16KB device
     *
     * @param handle APK file to scan for native libraries
     * @param libraryRoot directory for libraries
     * @param abiOverride abiOverride for package
     * @return {@link AlignmentResult} if successful or null
     */
    public static AlignmentResult checkAlignmentForCompatMode(
            Handle handle,
            String libraryRoot,
            boolean nativeLibraryRootRequiresIsa,
            String abiOverride) {
        // Keep the code below in sync with copyNativeBinariesForSupportedAbi
        int abi = findSupportedAbi(handle, Build.SUPPORTED_64_BIT_ABIS);
        if (abi < 0) {
            return null;
        }

        final String supportedAbi = Build.SUPPORTED_64_BIT_ABIS[abi];
        final String instructionSet = VMRuntime.getInstructionSet(supportedAbi);
        String subDir = libraryRoot;
        if (nativeLibraryRootRequiresIsa) {
            subDir += "/" + instructionSet;
        }

        int mode = ApplicationInfo.PAGE_SIZE_APP_COMPAT_FLAG_UNDEFINED;
        List<LibraryAlignmentInfo> unalignedLibraries = new ArrayList<LibraryAlignmentInfo>();

        for (long apkHandle : handle.apkHandles) {
            AlignmentResult res =
                    nativeCheckAlignment(
                            apkHandle,
                            subDir,
                            Build.SUPPORTED_64_BIT_ABIS[abi],
                            handle.extractNativeLibs,
                            handle.debuggable);
            if (res == null) {
                return null;
            }
            mode |= res.flags;
            if (res.unalignedLibraries != null) {
                Collections.addAll(unalignedLibraries, res.unalignedLibraries);
            }
        }

        return new AlignmentResult(mode, unalignedLibraries.toArray(new LibraryAlignmentInfo[0]));
    }

    public static long sumNativeBinariesWithOverride(Handle handle, String abiOverride)
            throws IOException {
        long sum = 0;
        if (handle.multiArch) {
            // Warn if we've set an abiOverride for multi-lib packages..
            // By definition, we need to copy both 32 and 64 bit libraries for
            // such packages.
            if (abiOverride != null && !CLEAR_ABI_OVERRIDE.equals(abiOverride)) {
                Slog.w(TAG, "Ignoring abiOverride for multi arch application.");
            }

            if (Build.SUPPORTED_32_BIT_ABIS.length > 0) {
                sum += sumNativeBinariesForSupportedAbi(handle, Build.SUPPORTED_32_BIT_ABIS);
            }

            if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                sum += sumNativeBinariesForSupportedAbi(handle, Build.SUPPORTED_64_BIT_ABIS);
            }
        } else {
            String cpuAbiOverride = null;
            if (CLEAR_ABI_OVERRIDE.equals(abiOverride)) {
                cpuAbiOverride = null;
            } else if (abiOverride != null) {
                cpuAbiOverride = abiOverride;
            }

            String[] abiList = (cpuAbiOverride != null) ?
                    new String[] { cpuAbiOverride } : Build.SUPPORTED_ABIS;
            if (Build.SUPPORTED_64_BIT_ABIS.length > 0 && cpuAbiOverride == null &&
                    hasRenderscriptBitcode(handle)) {
                abiList = Build.SUPPORTED_32_BIT_ABIS;
            }

            sum += sumNativeBinariesForSupportedAbi(handle, abiList);
        }
        return sum;
    }

    private static void updateIl2cpp(String abi, File libraryRoot, String codePath) {
        if (!abi.equals(cpuAbiArmv8_64)) return;

        final String libpath = libraryRoot.getPath() + "/arm64/";
        final boolean isIl2cpp = isFileExists(libpath + "libunity.so") && isFileExists(libpath + "libil2cpp.so");
        if (isIl2cpp) {
            try {
                final PackageParser.PackageLite pkg = PackageParser.parsePackageLite(new File(codePath), 0);
                if (bstfilter == null) bstfilter = IBstFilterAppsService.Stub.asInterface(ServiceManager.getService(Context.BST_FILTER_APPS));
                bstfilter.updateIl2cppPkgs(pkg.packageName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isFileExists(String filename) {
        return (new File(filename)).exists();
    }

    private static void createArmMarker(String abi, File libraryRoot, boolean useIsaSubdir) {
        File sharedLibraryDir;
        // If the installation is successful, create BlueStacks ARM App Marker file if its not already
        // there, so that we can use it later on to determine that we are dealing with the ARM APP.
        // We directly use the abi String passed as argument here since the result is a success, which gurantees the abi installation.
        if (useIsaSubdir) {
            final String instructionSet = VMRuntime.getInstructionSet(abi);
            sharedLibraryDir = new File(libraryRoot, instructionSet);
        } else {
            sharedLibraryDir = libraryRoot;
        }

        if(abi.startsWith("arm")) {
            try {
                File armMarker = new File (sharedLibraryDir, Features.GetArmAppMarker());
                if (armMarker.createNewFile()) {
                    FileUtils.setPermissions(armMarker.getAbsolutePath(), FileUtils.S_IRUSR | FileUtils.S_IWUSR
                            | FileUtils.S_IRGRP | FileUtils.S_IXUSR | FileUtils.S_IXGRP
                            | FileUtils.S_IXOTH | FileUtils.S_IROTH, -1, -1);
                    if (BST_DEBUG) Slog.e(TAG, "Successfully created arm marker in " + sharedLibraryDir);
                }
            } catch (Exception ex) {
                Slog.e(TAG, "Exception while creating a new empty ARM marker file: " + ex);
            }
            // We want to remove any x86 installed lib dir (default/force install) if the current install is
            // arm abi. Also, we need to remove arm 32 bit folder if re-installed in 64 bit mode and vice versa.
            // Brute force attemp both x86 and x86_64 remove.
            String sharedLibArmPath = "";
            if (abi.equals("armeabi-v7a") || abi.equals("armeabi"))
                sharedLibArmPath = libraryRoot.getPath() + "/arm64";
            else
                sharedLibArmPath = libraryRoot.getPath() + "/arm";
            String sharedLibX8664Path = libraryRoot.getPath() + "/x86_64";
            String sharedLibX86Path = libraryRoot.getPath() + "/x86";
            removeNativeBinariesFromDirLI(new File(sharedLibArmPath), true);
            removeNativeBinariesFromDirLI(new File(sharedLibX8664Path), true);
            removeNativeBinariesFromDirLI(new File(sharedLibX86Path), true);
        } else {
            // this is not an arm app
            // Just delete arm marker file, if is exists as it is quite possible that update of an arm app
            // starts supporting the x86 libraries. In that case, we need to remove this marker file, to make
            // the updated x86 app run successfully
            try {
                File armMarker = new File (sharedLibraryDir, Features.GetArmAppMarker());
                if (armMarker.exists()) {
                    armMarker.delete();
                }
            } catch (Exception e) {
                Slog.e(TAG, "Exception while deleting ARM marker file: " + e);
            }
            // We want to remove any arm installed lib dir (default/force install) if the current install is
            // x86 abi. Also, we need to remove x86 32 bit folder if re-installed in 64 bit mode and vice versa.
            // Brute force attemp both arm and arm_64 remove.
            String sharedLibX86Path = "";
            if (abi.equals("x86"))
                sharedLibX86Path = libraryRoot.getPath() + "/x86_64";
            else
                sharedLibX86Path = libraryRoot.getPath() + "/x86";
            String sharedLibArm64Path = libraryRoot.getPath() + "/arm64";
            String sharedLibArmPath = libraryRoot.getPath() + "/arm";
            removeNativeBinariesFromDirLI(new File(sharedLibX86Path), true);
            removeNativeBinariesFromDirLI(new File(sharedLibArm64Path), true);
            removeNativeBinariesFromDirLI(new File(sharedLibArmPath), true);
        }
    }
    public static String[] getBstAbiOverride(String codePath, String pkgName) {

        String[] cpuAbi = null;
        PackageParser.PackageLite pkgInfo;
        ABIResponse unityCheckResult;
        ArrayList<String> abiValueSupported = getSupportedAbi();

        if (bstfilter == null) {
            bstfilter = IBstFilterAppsService.Stub.asInterface(
                    ServiceManager.getService(Context.BST_FILTER_APPS));
        }

        // BST: Since this is called via PackageManagerSession:extractNativeLibraries
        // we need to handle the abiList here also for apps having forced install via bst
        // settings.
        // Determining the packageName here
        if (pkgName == null) {
            try {
                pkgInfo = PackageParser.parsePackageLite(new File(codePath), 0);
                if (pkgInfo == null) {
                    Slog.e(TAG, "getBstAbiOverride unable to get pkgInfo from packageParser for path " + codePath);
                } else {
                    pkgName = pkgInfo.packageName;
                    pkgInfo = null;
                }
            } catch (Exception e) {
                Slog.e(TAG, "Exception getting pkgName from pacakgeParser:" + e);
            }
            // Runtime.getRuntime().gc();
        }

        // bst.status.ssse3_available property value is based on whether cpu supports ssse3 instruction set.
        // 0 - ssse3 is NOT supported.
        // 1 - ssse3 is supported.
        int isSsse3Supported = SystemProperties.getInt("bst.status.ssse3_available", 0);

        if (pkgName == null) {
            Slog.e(TAG, "getBstAbiOverride unable to determine pkgName, returning here for " + codePath);
            return cpuAbi;
        }
        try {
            if (bstfilter.forceArmInstall(pkgName)) {
                int abiBitVal = bstfilter.getForceAbiInstallMode(pkgName);

                if (abiBitVal == ABI_ERROR) {
                    Slog.e(TAG, "Error installing force Arm app" + pkgName + " as invalid 64/32 bit val: " + abiBitVal);
                    return cpuAbi;
                }

                if (abiBitVal == ARM_MODE) {
                    Slog.e(TAG, "Installing ARM 64/32 App");
                    if (is32BitArch) {
                        cpuAbi = new String[2];
                        cpuAbi[0] = cpuAbiArmv7;
                        cpuAbi[1] = cpuAbiArm;
                    } else {
                        cpuAbi = new String[3];

                        // if abiValueSupported has support for ARMV8_64 keep it at first priority, otherwise give least priority
                       if (abiValueSupported.contains("arm64"))  {
                            cpuAbi[0] = cpuAbiArmv8_64;
                            cpuAbi[1] = cpuAbiArmv7;
                            cpuAbi[2] = cpuAbiArm;
                        } else {
                            cpuAbi[0] = cpuAbiArmv7;
                            cpuAbi[1] = cpuAbiArm;
                            cpuAbi[2] = cpuAbiArmv8_64;
                        }
                    }
                } else if (abiBitVal == ARM_64_MODE) {
                    if (is32BitArch) {
                        Slog.e(TAG, "Installing ARM 32 App as the system does not support 64 bit abi");
                        cpuAbi = new String[2];
                        cpuAbi[0] = cpuAbiArmv7;
                        cpuAbi[1] = cpuAbiArm;
                    } else {
                        Slog.e(TAG, "Installing ARM 64 App");
                        cpuAbi = new String[1];
                        cpuAbi[0] = cpuAbiArmv8_64;
                    }
                } else if (abiBitVal == ARM_32_MODE) {
                    Slog.e(TAG, "Installing ARM 32 App");
                    cpuAbi = new String[2];
                    cpuAbi[0] = cpuAbiArmv7;
                    cpuAbi[1] = cpuAbiArm;
                } else if (abiBitVal == XARM_MODE) {
                    if (is32BitArch) {
                        cpuAbi = new String[2];
                        cpuAbi[0] = cpuAbiArmv7;
                        cpuAbi[1] = cpuAbiArm;
                    } else {
                        cpuAbi = new String[3];
                        if (abiValueSupported.contains("arm64"))  {
                            cpuAbi[0] = cpuAbiArmv8_64;
                            cpuAbi[1] = cpuAbiArmv7;
                            cpuAbi[2] = cpuAbiArm;
                        } else {
                            cpuAbi[0] = cpuAbiArmv7;
                            cpuAbi[1] = cpuAbiArm;
                            cpuAbi[2] = cpuAbiArmv8_64;
                        }
                    }
                }
            } else if (bstfilter.forceX86Install(pkgName)) {
                int abiBitVal = bstfilter.getForceAbiInstallMode(pkgName);
                if (abiBitVal == ABI_ERROR) {
                    Slog.e(TAG, "Error installing force X86 app" + pkgName + " as invalid 64/32 bit val: " + abiBitVal);
                    return cpuAbi;
                }

                if (abiBitVal == X86_MODE) {
                    Slog.e(TAG, "Installing X86 64/32 App");
                    if (is32BitArch) {
                        cpuAbi = new String[1];
                        cpuAbi[0] = cpuAbiX86;
                    } else {
                        cpuAbi = new String[2];
                        // if abiValueSupported has support for X86_64 keep it at first priority, otherwise give least priority
                        if (abiValueSupported.contains("x64"))  {
                            cpuAbi[0] = cpuAbiX86_64;
                            cpuAbi[1] = cpuAbiX86;
                        } else {
                            cpuAbi[0] = cpuAbiX86;
                            cpuAbi[1] = cpuAbiX86_64;
                        }
                    }
                } else if (abiBitVal == X86_64_MODE) {
                    cpuAbi = new String[1];
                    if (is32BitArch) {
                        Slog.e(TAG, "Installing X86 32 App as system does not support 64 bit abi");
                        cpuAbi[0] = cpuAbiX86;
                    } else {
                        Slog.e(TAG, "Installing X86 64 App");
                        cpuAbi[0] = cpuAbiX86_64;
                    }
                } else if (abiBitVal == X86_32_MODE) {
                    Slog.e(TAG, "Installing X86 32 App");
                    cpuAbi = new String[1];
                    cpuAbi[0] = cpuAbiX86;
                }
            } else if ((unityCheckResult = isAppHavingUnityLibs(codePath)) != null &&
                    unityCheckResult.checkIfUnityLibsPresent()) {
                boolean runInArmMode = false;
                if (isSsse3Supported == 0)
                    runInArmMode = true;
                else
                    runInArmMode = unityCheckResult.isAppInstallModeArm();

                if (runInArmMode) {
                    Slog.e(TAG, "Installing ARM Unity App libs");
                    if (is32BitArch) {
                        cpuAbi = new String[2];
                        cpuAbi[0] = cpuAbiArmv7;
                        cpuAbi[1] = cpuAbiArm;
                    } else {
                        cpuAbi = new String[3];
                        if (abiValueSupported.contains("arm64")){
                            cpuAbi[0] = cpuAbiArmv8_64;
                            cpuAbi[1] = cpuAbiArmv7;
                            cpuAbi[2] = cpuAbiArm;
                        }
                        else {
                            cpuAbi[0] = cpuAbiArmv7;
                            cpuAbi[1] = cpuAbiArm;
                            cpuAbi[2] = cpuAbiArmv8_64;
                        }
                    }
                }
            }
        } catch(Exception exc) {
            Slog.e(TAG, "getBstAbiOverride cannot call BstFilterApps : " + exc);
            exc.printStackTrace();
        }
        if (cpuAbi == null) {
            return getAbiListForAbiValue();
        }
        else {
            return cpuAbi;
        }
    }

    public static String[] getAbiListForAbiValue()
    {
        ArrayList<String> abiList = new ArrayList<>();
        //x86_64,x86,arm64-v8a,armeabi-v7a,armeabi

        ArrayList<String> abiValueSupported = getSupportedAbi();

        if (abiValueSupported.contains("x64"))
            abiList.add(cpuAbiX86_64);

        if (abiValueSupported.contains("x86"))
            abiList.add(cpuAbiX86);

        if (abiValueSupported.contains("arm64"))
                abiList.add(cpuAbiArmv8_64);

        if (abiValueSupported.contains("arm"))  {
            abiList.add(cpuAbiArmv7);
            abiList.add(cpuAbiArm);
        }

        if (abiList.size() == 0) {
            return null;
        }

        String [] arr = new String[abiList.size()];
        return abiList.toArray(arr);
    }

    private static ArrayList<String> getSupportedAbi() {
        String abiList = SystemProperties.get("bst.abi_list", "x86,arm");
        String abiValueArr[] = abiList.split(",");
        ArrayList<String >abiValueSupported = new ArrayList<String >();
        for(String val:abiValueArr) {
            abiValueSupported.add(val);
        }
        return abiValueSupported;
    }

    // BST: Trying to determine if app is support for xarm abi.
    private static boolean isAppHavingXArmLibs(Handle handle) {
        final File codeFile =  (handle.apkDir != null) ? new File(handle.apkDir) : null;
        if (codeFile != null && codeFile.exists()) {
            try {
                final PackageParser.PackageLite lite = PackageParser.parsePackageLite(codeFile, 0);
                if (DEBUG_NATIVE) {
                    Slog.e(TAG, lite.packageName + " packagename xarm application.");
                }
                if (bstfilter == null) {
                    bstfilter = IBstFilterAppsService.Stub.asInterface(
                            ServiceManager.getService(Context.BST_FILTER_APPS));
                }
                return bstfilter.isXArmApp(lite.packageName);

            } catch (Exception e) {
                // Ignored; we tried our best
            }
        }
        return false;
    }

    // BST: Trying to determine if app is having unity libs.
    // Chinese apps having unity libs do not work correctly if installed in x86 mode and we
    // have to manually add arm entry in config db.
    private static ABIResponse isAppHavingUnityLibs(String apkDirPath)
    {
        String[] abi_arm = null, abi_x86 = null;
        if (is32BitArch) {
            abi_arm = new String[2];
            abi_arm[0] = cpuAbiArmv7;
            abi_arm[1] = cpuAbiArm;

            abi_x86 = new String[1];
            abi_x86[0] = cpuAbiX86;
        } else {
            abi_arm = new String[3];
            abi_arm[0] = cpuAbiArmv8_64;
            abi_arm[1] = cpuAbiArmv7;
            abi_arm[2] = cpuAbiArm;

            abi_x86 = new String[2];
            abi_x86[0] = cpuAbiX86_64;
            abi_x86[1] = cpuAbiX86;
        }

        boolean unityLibsPresent = false, appInstallModeArm = false;
        int count_arm = 0, count_x86 = 0;

        try
        {
            // If the apkDirPath is not an apk path, we try to retieve the base apk of the package.
            if (!apkDirPath.endsWith(".apk")) {
                //  Check Xapk lib path first.
                String libApkDirPath = apkDirPath;
                apkDirPath = getXapkLibPath(apkDirPath);
                if (apkDirPath == null) {
                   apkDirPath = getApkPath(libApkDirPath);
                }
            }

            // If for some reason unable to determine .apk path, return false as no way to open zipfile.
            if (apkDirPath == null) {
                unityLibsPresent = false; // Assuming that unity libs are not present.
                return new ABIResponse(unityLibsPresent, appInstallModeArm);
            }

            ZipFile zipFile = new ZipFile(apkDirPath);
            count_arm = maxUnityLibsInFolder(zipFile, abi_arm);
            count_x86 = maxUnityLibsInFolder(zipFile, abi_x86);
            if (count_arm > 0 || count_x86 > 0)
                unityLibsPresent = true; // Unity libs are present in the app.
            else
                unityLibsPresent = false; // Unity libs are not present in the app.
            /**
             * We will install arm libs in following 2 cases.
             * 1. Unity libs present in x86 folder are less than 3
             * 2. If number of unity libs in x86 folders are less than number of unity libs in arm folders.
             */
            if (unityLibsPresent && (count_x86 < 3 || count_x86 < count_arm)) {
                appInstallModeArm = true; // Install the app in arm mode
                return new ABIResponse(unityLibsPresent, appInstallModeArm);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        appInstallModeArm = false; // Install the app in x86 mode
        return new ABIResponse(unityLibsPresent, appInstallModeArm);
    }

    // This function will return the maximum number of unity libraries
    // present in folders with different abis.
    private static int maxUnityLibsInFolder(ZipFile zipFile, String[] abi) {
        String[] libs = {"libunity.so", "libmain.so","libmono.so", "libil2cpp.so"};
        int count_max = 0, count = 0;
        boolean foundMonoOrIl2Cpp = false;
        String apkLib = "lib";
        for (int i = 0; i < abi.length && (count < (libs.length - 1)); i++) {
            count = 0;
            foundMonoOrIl2Cpp = false;
            for (int j = 0; j < libs.length; j++) {
                ZipEntry zipEntry = zipFile.getEntry(apkLib+"/"+abi[i]+"/"+libs[j]);
                if (zipEntry != null) {
                    if (!libs[j].equals("libmono.so") && !libs[j].equals("libil2cpp.so"))
                        count++;
                    else if (!foundMonoOrIl2Cpp) {
                        count++;
                        foundMonoOrIl2Cpp = true;
                    }
                }
            }

            if (count > count_max) {
                count_max = count;
            }
        }

        return count_max;
    }

    private static String getApkPath(String apkDir) {
        File apkDirFile = new File(apkDir);
        String path = null;
        final File[] files = apkDirFile.listFiles();
        if (files != null) {
            for (int n = 0; n < files.length; n++) {
                path = files[n].getAbsolutePath();
                if (path.endsWith(".apk")) {
                    break;
                }
            }
        }
        return path;
    }

    private static String getXapkLibPath(String apkDir) {
        File apkDirFile = new File(apkDir);
        String path = null;
        final File[] files = apkDirFile.listFiles();
        if (files != null) {
            for (int n = 0; n < files.length; n++) {
                path = files[n].getAbsolutePath();
                if (path.endsWith("v8a.apk") || path.endsWith("v7a.apk")) {
                    break;
                }
                path = null;
            }
        }
        return path;
    }

    /**
     * Configure the native library files managed by Incremental Service. Makes sure Incremental
     * Service will create native library directories and set up native library binary files in the
     * same structure as they are in non-incremental installations.
     *
     * @param handle The Handle object that contains all apk paths.
     * @param libSubDir The target directory to put the native library files, e.g., lib/ or lib/arm
     * @param abi The abi that is supported by the current device.
     * @return Integer code if installation succeeds or fails.
     */
    private static int incrementalConfigureNativeBinariesForSupportedAbi(Handle handle,
            File libSubDir, String abi) {
        final String[] apkPaths = handle.apkPaths;
        if (apkPaths == null || apkPaths.length == 0) {
            Slog.e(TAG, "No apks to extract native libraries from.");
            return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }

        final IBinder incrementalService = ServiceManager.getService(Context.INCREMENTAL_SERVICE);
        if (incrementalService == null) {
            //TODO(b/133435829): add incremental specific error codes
            return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }
        final IncrementalManager incrementalManager = new IncrementalManager(
                IIncrementalService.Stub.asInterface(incrementalService));
        final File apkParent = new File(apkPaths[0]).getParentFile();
        IncrementalStorage incrementalStorage =
                incrementalManager.openStorage(apkParent.getAbsolutePath());
        if (incrementalStorage == null) {
            Slog.e(TAG, "Failed to find incremental storage");
            return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }

        String libRelativeDir = getRelativePath(apkParent, libSubDir);
        if (libRelativeDir == null) {
            return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }

        for (int i = 0; i < apkPaths.length; i++) {
            if (!incrementalStorage.configureNativeBinaries(apkPaths[i], libRelativeDir, abi,
                    handle.extractNativeLibs)) {
                return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
            }
        }
        return PackageManager.INSTALL_SUCCEEDED;
    }

    private static String getRelativePath(File base, File target) {
        try {
            final Path basePath = base.toPath();
            final Path targetPath = target.toPath();
            final Path relativePath = basePath.relativize(targetPath);
            if (relativePath.toString().isEmpty()) {
                return "";
            }
            return relativePath.toString();
        } catch (IllegalArgumentException ex) {
            Slog.e(TAG, "Failed to find relative path between: " + base.getAbsolutePath()
                    + " and: " + target.getAbsolutePath());
            return null;
        }
    }

    // We don't care about the other return values for now.
    private static final int BITCODE_PRESENT = 1;

    private static native int hasRenderscriptBitcode(long apkHandle);

    public static boolean hasRenderscriptBitcode(Handle handle) throws IOException {
        for (long apkHandle : handle.apkHandles) {
            final int res = hasRenderscriptBitcode(apkHandle);
            if (res < 0) {
                throw new IOException("Error scanning APK, code: " + res);
            } else if (res == BITCODE_PRESENT) {
                return true;
            }
        }
        return false;
    }

    /**
     * A container to hold the complete result of a native library alignment check.
     */
    public static class AlignmentResult {
        @ApplicationInfo.PageSizeAppCompatFlags
        public final int flags;
        public final LibraryAlignmentInfo[] unalignedLibraries;

        public AlignmentResult(@ApplicationInfo.PageSizeAppCompatFlags int flags,
                LibraryAlignmentInfo[] unalignedLibraries) {
            this.flags = flags;
            this.unalignedLibraries = unalignedLibraries;
        }
    }
}
