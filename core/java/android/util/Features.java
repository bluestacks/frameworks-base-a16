/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.util;

/**
 * Android features. Support for experimental featuers in android such running
 * ARM and x86 apps simultaneously.
 *
 * @hide
 */
public final class Features
{
    private static final String ANDROID_ARM_HAS_ARM_LIBS = "containsArmLibs.txt";

    /*
     * @hide
     */
    public static final String GetArmAppMarker()
    {
        return ANDROID_ARM_HAS_ARM_LIBS;
    }
}
