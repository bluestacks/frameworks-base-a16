/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.performance

import android.content.Context
import android.os.PerformanceHintManager
import android.os.Process
import android.window.SystemPerformanceHinter
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import java.io.PrintWriter
import java.util.concurrent.TimeUnit

/**
 * Manages the performance hints to the system.
 */
class PerfHintController(private val mContext: Context,
                         shellInit: ShellInit,
                         private val mShellCommandHandler: ShellCommandHandler,
                         rootTdaOrganizer: RootTaskDisplayAreaOrganizer) {

    // The system perf hinter
    val hinter: SystemPerformanceHinter

    init {
        hinter = SystemPerformanceHinter(mContext,
                rootTdaOrganizer.performanceRootProvider)
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        mShellCommandHandler.addDumpCallback(this::dump, this)
        // BS-A16 / 7AO: getSystemService(PerformanceHintManager) natively calls
        // waitForService("performance_hint"), which blocks FOREVER on BST because the
        // performance_hint HAL is absent. onInit runs on wmshell.main during Shell init,
        // so this froze the Shell main looper and starved ALL posted work - including
        // transitions' requestStartTransition - leaving standard-task windows hidden.
        // ADPF session setup is optional (was null-checked) and BST does not need ADPF,
        // so skip acquiring PerformanceHintManager. (A proper performance_hint HAL stub
        // would be the comprehensive fix.) Root fix for 7AO.
    }

    fun dump(pw: PrintWriter, prefix: String?) {
        hinter.dump(pw, prefix)
    }
}
