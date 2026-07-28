/**
 * @brief Most of the codes are derived from VBoxService for windows. 
 * Guest page sharing.
 */

/*
 * Copyright (C) 2006-2016 Oracle Corporation
 *
 * This file is part of VirtualBox Open Source Edition (OSE), as
 * available from http://www.virtualbox.org. This file is free software;
 * you can redistribute it and/or modify it under the terms of the GNU
 * General Public License (GPL) as published by the Free Software
 * Foundation, in version 2 as it comes in the "COPYING" file of the
 * VirtualBox OSE distribution. VirtualBox OSE is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY of any kind.
 */


/** @page pg_vgsvc_pagesharing VBoxService - Page Sharing
 *
 * The Page Sharing subservice is responsible for finding memory mappings
 * suitable page fusions.
 *
 * It is the driving force behind the Page Fusion feature in VirtualBox.
 * Working with PGM and GMM (ring-0) thru the VMMDev interface.  Every so often
 * it reenumerates the memory mappings (executables and shared libraries) of the
 * guest OS and reports additions and removals to GMM.  For each mapping there
 * is a filename and version as well as and address range and subsections.  GMM
 * will match the mapping with mapping with the same name and version from other
 * VMs and see if there are any identical pages between the two.
 *
 * To increase the hit rate and reduce the volatility, the service launches a
 * child process which loads all the Windows system DLLs it can.  The child
 * process is necessary as the DLLs are loaded without running the init code,
 * and therefore not actually callable for other VBoxService code (may crash).
 *
 * This is currently only implemented on Windows.  There is no technical reason
 * for it not to be doable for all the other guests too, it's just a matter of
 * customer demand and engineering time.
 *
 */

#ifndef __RTVBOXGUEST_H_H
#define __RTVBOXGUEST_H_H

#define VGSvcVerbose(log_level, ...)    ALOGI(__VA_ARGS__)
#define VGSvcError(...)                 ALOGI(__VA_ARGS__)
#define RTCALL                          __attribute__((__cdecl__, __regparm__(0)))
#define DECLCALLBACK(type)              type
#define RT_SUCCESS(rc)                 (rc == 0)
#define RT_FAILURE(rc)                 !RT_SUCCESS(rc)

#define ALOGE(...)                     ((void)__android_log_print(ANDROID_LOG_ERROR, "page-fusion", __VA_ARGS__))
#define ALOGW(...)                     ((void)__android_log_print(ANDROID_LOG_WARN, "page-fusion", __VA_ARGS__))
#define ALOGI(...)                     ((void)__android_log_print(ANDROID_LOG_INFO, "page-fusion", __VA_ARGS__))

# if __GNUC__ >= 3 && !defined(FORTIFY_RUNNING)
#  define RT_LIKELY(expr)       __builtin_expect(!!(expr), 1)
#  define RT_UNLIKELY(expr)     __builtin_expect(!!(expr), 0)
# else
#  define RT_LIKELY(expr)       (expr)
#  define RT_UNLIKELY(expr)     (expr)
# endif

/** @name Misc. Status Codes
 * @{
 */
/** Success. */
#define VINF_SUCCESS                        0

/** General failure - DON'T USE THIS!!! */
#define VERR_GENERAL_FAILURE                (-1)
/** Invalid parameter. */
#define VERR_INVALID_PARAMETER              (-2)
/** Invalid loader handle. */
#define VERR_INVALID_HANDLE                 (-4)
/** Not supported. */
#define VERR_NOT_SUPPORTED                  (-37)
/** Buffer too small to save result. */
#define VERR_BUFFER_OVERFLOW                (-41)
#define VERR_IGNORED                        (-91)
#define VERR_INTERNAL_ERROR                 (-225)
/** Device i/o: General failure. */
#define VERR_IO_GEN_FAILURE                 (-257)


#define PAGE_STATE_INVALID           0
#define PAGE_STATE_SHARED            1
#define PAGE_STATE_READ_WRITE        2
#define PAGE_STATE_READ_ONLY         3
#define PAGE_STATE_NOT_PRESENT       4


/** @def RT_BIT_32
 * Convert a bit number into a 32-bit bitmask (unsigned).
 * @param   bit     The bit number.
 */
#define RT_BIT_32(bit)                          ( UINT32_C(1) << (bit) )
/** Bit 0 -  P  - Present bit mask. */
#define X86_PTE_P                           	RT_BIT_32(0)
/** Bit 1 - R/W - Read (clear) / Write (set) bit mask. */
#define X86_PTE_RW                          	RT_BIT_32(1)
/** Invalid parameter. */
/** Invalid loader handle. */
/** Version of VMMDevRequestHeader structure. */
#define VMMDEV_REQUEST_HEADER_VERSION 		(0x10001)
#define VBOXGUEST_DEVICE_NAME          			   "/dev/vboxguest"
#define VBOXGUEST_IOCTL_CODE_(Function, Size)      _IOC(_IOC_READ|_IOC_WRITE, 'V', (Function), (Size))

/** IOCTL to VBoxGuest to perform a VMM request
 * @remark  The data buffer for this IOCtl has an variable size, keep this in mind
 *          on systems where this matters. */
#define VBOXGUEST_IOCTL_VMMREQUEST(Size)            VBOXGUEST_IOCTL_CODE_(3, (Size))

/** @def RT_GNUC_PREREQ
 * Shorter than fiddling with __GNUC__ and __GNUC_MINOR__.
 *
 * @param   a_MinMajor      Minimum major version
 * @param   a_MinMinor      The minor version number part.
 */
#define RT_GNUC_PREREQ(a_MinMajor, a_MinMinor)      RT_GNUC_PREREQ_EX(a_MinMajor, a_MinMinor, 0)

/** @def RT_GNUC_PREREQ_EX
 * Simplified way of checking __GNUC__ and __GNUC_MINOR__ regardless of actual
 * compiler used, returns @a a_OtherRet for other compilers.
 *
 * @param   a_MinMajor      Minimum major version
 * @param   a_MinMinor      The minor version number part.
 * @param   a_OtherRet      What to return for non-GCC compilers.
 */
#if defined(__GNUC__) && defined(__GNUC_MINOR__)
# define RT_GNUC_PREREQ_EX(a_MinMajor, a_MinMinor, a_OtherRet) \
    ((__GNUC__ << 16) + __GNUC_MINOR__ >= ((a_MinMajor) << 16) + (a_MinMinor))
#else
# define RT_GNUC_PREREQ_EX(a_MinMajor, a_MinMinor, a_OtherRet) (a_OtherRet)
#endif

/** @def RT_OFFSETOF
 * Our own special offsetof() variant, returns a signed result.
 *
 * This differs from the usual offsetof() in that it's not relying on builtin
 * compiler stuff and thus can use variables in arrays the structure may
 * contain. This is useful to determine the sizes of structures ending
 * with a variable length field. For gcc >= 4.4 see @bugref{7775}.
 *
 * @returns offset into the structure of the specified member. signed.
 * @param   type    Structure type.
 * @param   member  Member.
 */
#if defined(__cplusplus) && RT_GNUC_PREREQ(4, 4)
# define RT_OFFSETOF(type, member)              ( (int)(uintptr_t)&( ((type *)(void *)0x1000)->member) - 0x1000 )
#else
# define RT_OFFSETOF(type, member)              ( (int)(uintptr_t)&( ((type *)(void *)0)->member) )
#endif

/** @def NOREF
 * Keeps the compiler from bitching about an unused parameter, local variable,
 * or other stuff, will never use _Pragma are is thus more flexible.
 */
#define NOREF(var)               (void)(var)


typedef uint64_t RTGCPTR64;
typedef uint32_t RTGCPTR32;

typedef RTGCPTR64 RTGCPTR; // TODO: Based on GC Arch bits, either typedef RTGCPTR64 or RTGCPTR32

 /* Global list of guest OS families.
 */
typedef enum VBOXOSFAMILY
{
    VBOXOSFAMILY_Unknown          = 0,
    VBOXOSFAMILY_Windows32        = 1,
    VBOXOSFAMILY_Windows64        = 2,
    VBOXOSFAMILY_Linux32          = 3,
    VBOXOSFAMILY_Linux64          = 4,
    VBOXOSFAMILY_FreeBSD32        = 5,
    VBOXOSFAMILY_FreeBSD64        = 6,
    VBOXOSFAMILY_Solaris32        = 7,
    VBOXOSFAMILY_Solaris64        = 8,
    VBOXOSFAMILY_MacOSX32         = 9,
    VBOXOSFAMILY_MacOSX64         = 10,
    /** The usual 32-bit hack. */
    VBOXOSFAMILY_32BIT_HACK = 0x7fffffff
} VBOXOSFAMILY;

/**
 * VMMDev request types.
 * @note when updating this, adjust vmmdevGetRequestSize() as well
 */
typedef enum
{
    VMMDevReq_InvalidRequest             =  0,
    VMMDevReq_GetMouseStatus             =  1,
    VMMDevReq_SetMouseStatus             =  2,
    VMMDevReq_SetPointerShape            =  3,
    VMMDevReq_GetHostVersion             =  4,
    VMMDevReq_Idle                       =  5,
    VMMDevReq_GetHostTime                = 10,
    VMMDevReq_GetHypervisorInfo          = 20,
    VMMDevReq_SetHypervisorInfo          = 21,
    VMMDevReq_RegisterPatchMemory        = 22, /* since version 3.0.6 */
    VMMDevReq_DeregisterPatchMemory      = 23, /* since version 3.0.6 */
    VMMDevReq_SetPowerStatus             = 30,
    VMMDevReq_AcknowledgeEvents          = 41,
    VMMDevReq_CtlGuestFilterMask         = 42,
    VMMDevReq_ReportGuestInfo            = 50,
    VMMDevReq_ReportGuestInfo2           = 58, /* since version 3.2.0 */
    VMMDevReq_ReportGuestStatus          = 59, /* since version 3.2.8 */
    VMMDevReq_ReportGuestUserState       = 74, /* since version 4.3 */
    /**
     * Retrieve a display resize request sent by the host using
     * @a IDisplay:setVideoModeHint.  Deprecated.
     *
     * Similar to @a VMMDevReq_GetDisplayChangeRequest2, except that it only
     * considers host requests sent for the first virtual display.  This guest
     * request should not be used in new guest code, and the results are
     * undefined if a guest mixes calls to this and
     * @a VMMDevReq_GetDisplayChangeRequest2.
     */
    VMMDevReq_GetDisplayChangeRequest    = 51,
    VMMDevReq_VideoModeSupported         = 52,
    VMMDevReq_GetHeightReduction         = 53,
    /**
     * Retrieve a display resize request sent by the host using
     * @a IDisplay:setVideoModeHint.
     *
     * Queries a display resize request sent from the host.  If the
     * @a eventAck member is sent to true and there is an unqueried
     * request available for one of the virtual display then that request will
     * be returned.  If several displays have unqueried requests the lowest
     * numbered display will be chosen first.  Only the most recent unseen
     * request for each display is remembered.
     * If @a eventAck is set to false, the last host request queried with
     * @a eventAck set is resent, or failing that the most recent received from
     * the host.  If no host request was ever received then all zeros are
     * returned.
     */
    VMMDevReq_GetDisplayChangeRequest2   = 54,
    VMMDevReq_ReportGuestCapabilities    = 55,
    VMMDevReq_SetGuestCapabilities       = 56,
    VMMDevReq_VideoModeSupported2        = 57, /* since version 3.2.0 */
    VMMDevReq_GetDisplayChangeRequestEx  = 80, /* since version 4.2.4 */
#ifdef VBOX_WITH_HGCM
    VMMDevReq_HGCMConnect                = 60,
    VMMDevReq_HGCMDisconnect             = 61,
#ifdef VBOX_WITH_64_BITS_GUESTS
    VMMDevReq_HGCMCall32                 = 62,
    VMMDevReq_HGCMCall64                 = 63,
#else
    VMMDevReq_HGCMCall                   = 62,
#endif /* VBOX_WITH_64_BITS_GUESTS */
    VMMDevReq_HGCMCancel                 = 64,
    VMMDevReq_HGCMCancel2                = 65,
#endif
    VMMDevReq_VideoAccelEnable           = 70,
    VMMDevReq_VideoAccelFlush            = 71,
    VMMDevReq_VideoSetVisibleRegion      = 72,
    VMMDevReq_GetSeamlessChangeRequest   = 73,
    VMMDevReq_QueryCredentials           = 100,
    VMMDevReq_ReportCredentialsJudgement = 101,
    VMMDevReq_ReportGuestStats           = 110,
    VMMDevReq_GetMemBalloonChangeRequest = 111,
    VMMDevReq_GetStatisticsChangeRequest = 112,
    VMMDevReq_ChangeMemBalloon           = 113,
    VMMDevReq_GetVRDPChangeRequest       = 150,
    VMMDevReq_LogString                  = 200,
    VMMDevReq_GetCpuHotPlugRequest       = 210,
    VMMDevReq_SetCpuHotPlugStatus        = 211,
    VMMDevReq_RegisterSharedModule       = 212,
    VMMDevReq_UnregisterSharedModule     = 213,
    VMMDevReq_CheckSharedModules         = 214,
    VMMDevReq_GetPageSharingStatus       = 215,
    VMMDevReq_DebugIsPageShared          = 216,
    VMMDevReq_GetSessionId               = 217, /* since version 3.2.8 */
    VMMDevReq_WriteCoreDump              = 218,
    VMMDevReq_GuestHeartbeat             = 219,
    VMMDevReq_HeartbeatConfigure         = 220,
    VMMDevReq_SizeHack                   = 0x7fffffff
} VMMDevRequestType;

/**
 * Generic VMMDev request header.
 */
typedef struct
{
    /** IN: Size of the structure in bytes (including body). */
    uint32_t size;
    /** IN: Version of the structure.  */
    uint32_t version;
    /** IN: Type of the request. */
    VMMDevRequestType requestType;
    /** OUT: Return code. */
    int32_t  rc;
    /** Reserved field no.1. MBZ. */
    uint32_t reserved1;
    /** Reserved field no.2. MBZ. */
    uint32_t reserved2;
} VMMDevRequestHeader;

/**
 * Session id request structure.
 *
 * Used by VMMDevReq_GetSessionId.
 */
typedef struct
{
    /** Header */
    VMMDevRequestHeader header;
    /** OUT: unique session id; the id will be different after each start, reset or restore of the VM */
    uint64_t            idSession;
} VMMDevReqSessionId;

/**
 * Shared region description
 */
typedef struct VMMDEVSHAREDREGIONDESC
{
    RTGCPTR64           GCRegionAddr;
    uint32_t            cbRegion;
    uint32_t            u32Alignment;
} VMMDEVSHAREDREGIONDESC;

#define VMMDEVSHAREDREGIONDESC_MAX          32

/**
 * Shared module registration
 */
typedef struct
{
    /** Header. */
    VMMDevRequestHeader         header;
    /** Shared module size. */
    uint32_t                    cbModule;
    /** Number of included region descriptors */
    uint32_t                    cRegions;
    /** Base address of the shared module. */
    RTGCPTR64                   GCBaseAddr;
    /** Guest OS type. */
    VBOXOSFAMILY                enmGuestOS;
    /** Alignment. */
    uint32_t                    u32Align;
    /** Module name */
    char                        szName[128];
    /** Module version */
    char                        szVersion[16];
    /** Shared region descriptor(s). */
    VMMDEVSHAREDREGIONDESC      aRegions[1];
} VMMDevSharedModuleRegistrationRequest;


/**
 * Shared module unregistration
 */
typedef struct
{
    /** Header. */
    VMMDevRequestHeader         header;
    /** Shared module size. */
    uint32_t                    cbModule;
    /** Align at 8 byte boundary. */
    uint32_t                    u32Alignment;
    /** Base address of the shared module. */
    RTGCPTR64                   GCBaseAddr;
    /** Module name */
    char                        szName[128];
    /** Module version */
    char                        szVersion[16];
} VMMDevSharedModuleUnregistrationRequest;


/**
 * Shared module periodic check
 */
typedef struct
{
    /** Header. */
    VMMDevRequestHeader         header;
} VMMDevSharedModuleCheckRequest;

/**
 * Paging sharing enabled query
 */
typedef struct
{
    /** Header. */
    VMMDevRequestHeader         header;
    /** Enabled flag (out) */
    bool                        fEnabled;
    /** Alignment */
    bool                        fAlignment[3];
} VMMDevPageSharingStatusRequest;

#pragma pack(4)
/**
 * Page sharing status query (debug build only)
 */
typedef struct
{
    /** Header. */
    VMMDevRequestHeader         header;
    /** Page address. */
    RTGCPTR                     GCPtrPage;
    /** Page flags. */
    uint64_t                    uPageFlags;
    /** Shared flag (out) */
    bool                        fShared;
    /** Alignment */
    bool                        fAlignment[3];
} VMMDevPageIsSharedRequest;
#pragma pack()

int VbglR3Init(void);
void VbglR3Uninit(void);
int VgsvcPageSharingInit(void);
int VbglR3CheckSharedModules();
int VbglR3RegisterSharedModule(char *pszModuleName, char *pszVersion,
                                RTGCPTR64  GCBaseAddr, uint32_t cbModule,
                                unsigned cRegions, VMMDEVSHAREDREGIONDESC *pRegions,
								VBOXOSFAMILY enmGuestOS);
int VbglR3UnregisterSharedModule(char *pszModuleName, char *pszVersion, RTGCPTR64 GCBaseAddr, uint32_t cbModule);

#endif
