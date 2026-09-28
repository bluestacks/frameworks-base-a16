/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.common.DisplayLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopModeUtilsTest {
    @Test
    fun isTaskBoundsEqual_stableBoundsAreEqual_returnTrue() {
        assertThat(isTaskBoundsEqual(task2Bounds, stableBounds)).isTrue()
    }

    @Test
    fun isTaskBoundsEqual_stableBoundsAreNotEqual_returnFalse() {
        assertThat(isTaskBoundsEqual(task4Bounds, stableBounds)).isFalse()
    }

    @Test
    fun isTaskWidthOrHeightEqual_stableBoundsAreEqual_returnTrue() {
        assertThat(isTaskWidthOrHeightEqual(task2Bounds, stableBounds)).isTrue()
    }

    @Test
    fun isTaskWidthOrHeightEqual_stableBoundWidthIsEquals_returnTrue() {
        assertThat(isTaskWidthOrHeightEqual(task3Bounds, stableBounds)).isTrue()
    }

    @Test
    fun isTaskWidthOrHeightEqual_stableBoundHeightIsEquals_returnTrue() {
        assertThat(isTaskWidthOrHeightEqual(task3Bounds, stableBounds)).isTrue()
    }

    @Test
    fun isTaskWidthOrHeightEqual_stableBoundsWidthOrHeightAreNotEquals_returnFalse() {
        assertThat(isTaskWidthOrHeightEqual(task1Bounds, stableBounds)).isTrue()
    }

    @Test
    fun isTaskMaximized_nonResizableTaskMatchesWidth_returnTrue() {
        val maximizeBounds = Rect(0, 0, 1000, 800)
        val taskInfo =
            RunningTaskInfo().apply {
                isResizeable = false
                configuration.windowConfiguration.bounds.set(0, 100, 1000, 700)
            }

        assertThat(isTaskMaximized(taskInfo, maximizeBounds)).isTrue()
    }

    @Test
    fun getDesktopFreeformArea_bstDesktop_usesAutoHiddenStatusBarArea() {
        val displayLayout = mock<DisplayLayout>()
        whenever(displayLayout.getStableBounds(any())).thenAnswer { invocation ->
            (invocation.arguments.first() as Rect).set(0, 32, 1000, 800)
        }

        assertThat(getDesktopFreeformArea(displayLayout, isBstDesktopModeEnabled = true))
            .isEqualTo(Rect(0, 0, 1000, 800))
    }

    private companion object {
        val task1Bounds = Rect(0, 0, 0, 0)
        val task2Bounds = Rect(1, 1, 1, 1)
        val task3Bounds = Rect(0, 1, 0, 1)
        val task4Bounds = Rect(1, 2, 2, 1)
        val stableBounds = Rect(1, 1, 1, 1)
    }
}
