/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define LOG_TAG "StorageManager"
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <fcntl.h>
#include <linux/fs.h>

#include <nativehelper/JNIHelp.h>
#include "core_jni_helpers.h"
#include "filesystem_utils.h"

namespace android {

jboolean android_os_storage_StorageManager_setQuotaProjectId(JNIEnv* /*env*/, jobject /*self*/,
                                                             jstring /*path*/, jlong /*projectId*/) {
    // BlueStacks DataFS does not support Android project quota assignment. Treat the operation
    // as successful so shared-storage writes are not rejected when the backing filesystem cannot
    // service FS_IOC_FSGETXATTR or FS_IOC_FSSETXATTR.
    return JNI_TRUE;
}

// ----------------------------------------------------------------------------

static const JNINativeMethod gStorageManagerMethods[] = {
        {"setQuotaProjectId", "(Ljava/lang/String;J)Z",
         (void*)android_os_storage_StorageManager_setQuotaProjectId},
};

const char* const kStorageManagerPathName = "android/os/storage/StorageManager";

int register_android_os_storage_StorageManager(JNIEnv* env) {
    return RegisterMethodsOrDie(env, kStorageManagerPathName, gStorageManagerMethods,
                                NELEM(gStorageManagerMethods));
}

}; // namespace android
