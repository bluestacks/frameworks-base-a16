/*
 * Copyright (C) 2020-2024 BlueStack Systems, Inc.
 * All Rights Reserved
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF BLUESTACK SYSTEMS, INC.
 * The copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package com.bluestacks.os;

/** @hide */
public class BstHostCallCcCodes {
    public static final int HCALL_CC_hcallResizeResolution      = 0x1;
    public static final int HCALL_CC_hcallRestoreResolution     = 0x2;
    public static final int HCALL_CC_hcallSplitAdsShowTimes     = 0x3;
    public static final int HCALL_CC_hcallSplitAdsEnterComplete = 0x4;
    public static final int HCALL_CC_hcallSplitAdsExitComplete  = 0x5;
    public static final int HCALL_CC_hcallPasswordInputOn       = 0x6;
    public static final int HCALL_CC_hcallPasswordInputOff      = 0x7;

    public static String getHCallCcName(int code) {
        switch (code) {
            case HCALL_CC_hcallResizeResolution:
                return "HCALL_CC_hcallResizeResolution";
            case HCALL_CC_hcallRestoreResolution:
                return "HCALL_CC_hcallRestoreResolution";
            case HCALL_CC_hcallSplitAdsShowTimes:
                return "HCALL_CC_hcallSplitAdsShowTimes";
            case HCALL_CC_hcallSplitAdsEnterComplete:
                return "HCALL_CC_hcallSplitAdsEnterComplete";
            case HCALL_CC_hcallSplitAdsExitComplete:
                return "HCALL_CC_hcallSplitAdsExitComplete";
            case HCALL_CC_hcallPasswordInputOn:
                return "HCALL_CC_hcallPasswordInputOn";
            case HCALL_CC_hcallPasswordInputOff:
                return "HCALL_CC_hcallPasswordInputOff";
            default:
                return "UnknownCode";
        }

    }

}