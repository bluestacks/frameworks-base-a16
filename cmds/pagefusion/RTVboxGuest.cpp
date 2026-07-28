/* Most of the codes are derived from VBoxService for windows
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
#include <android/log.h>
#include <ctype.h>
#include <cutils/properties.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <pwd.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <ucontext.h>
#include <unistd.h>

#include <algorithm>
#include <string>
#include <unordered_map>
#include <vector>
#include "RTVboxGuest.h"

static int g_File = -1;

/**
 * Implementation of VbglR3Init and VbglR3InitUser
 */
static int vbglR3Init(const char *pszDeviceName)
{
    if (g_File != -1)
        return VERR_INTERNAL_ERROR;

    /* The default implementation. (linux, solaris, freebsd, netbsd, haiku) */
    int File = open(pszDeviceName, O_RDWR);
    if (File == -1)
	{
        ALOGI("Couldn't open %s file, error %s", pszDeviceName, strerror(errno));
        return errno;
    }
	
    if (fcntl(File, F_SETFD, FD_CLOEXEC) < 0)
    {
        ALOGI("Couldn't set close on exec flag for /dev/vboxguest file");
        return errno;
    }
    g_File = File;
    return VINF_SUCCESS;
}

/**
 * Open the VBox R3 Guest Library.  This should be called by system daemons
 * and processes.
 */
int VbglR3Init(void)
{
    return vbglR3Init( VBOXGUEST_DEVICE_NAME );
}

/**
 * Close the VBox device file.
 */
void VbglR3Uninit(void)
{
    close( g_File );
}


/**
 * Internal wrapper around various OS specific ioctl implementations.
 *
 * @returns VBox status code as returned by VBoxGuestCommonIOCtl, or
 *          an failure returned by the OS specific ioctl APIs.
 *
 * @param   iFunction   The requested function.
 * @param   pvData      The input and output data buffer.
 * @param   cbData      The size of the buffer.
 *
 * @remark  Exactly how the VBoxGuestCommonIOCtl is ferried back
 *          here is OS specific. On BSD and Darwin we can use errno,
 *          while on OS/2 we use the 2nd buffer of the IOCtl.
 */
int vbglR3DoIOCtl(unsigned iFunction, void *pvData, size_t cbData)
{
    NOREF(cbData);
    if (g_File == -1)
        return VERR_INVALID_HANDLE;
		
    int rc = ioctl(g_File, iFunction, pvData);
    if (rc == 0)
        return VINF_SUCCESS;

    /* Positive values are negated VBox error status codes. */
    if (rc > 0)
        rc = -rc;
    else
        rc = errno;
    return rc;
}

int vbglR3GRPerform(VMMDevRequestHeader *pReq)
{
    return vbglR3DoIOCtl(VBOXGUEST_IOCTL_VMMREQUEST(pReq->size), pReq, pReq->size);
}

/**
 * Inline helper to determine the request size for the given operation.
 * Returns 0 if the given operation is not handled and/or supported.
 *
 * @returns Size.
 * @param   requestType     The VMMDev request type.
 */
static inline size_t vmmdevGetRequestSize(VMMDevRequestType requestType)
{
    switch (requestType)
    {
        case VMMDevReq_RegisterSharedModule:
            return sizeof(VMMDevSharedModuleRegistrationRequest);
        case VMMDevReq_UnregisterSharedModule:
            return sizeof(VMMDevSharedModuleUnregistrationRequest);
        case VMMDevReq_CheckSharedModules:
            return sizeof(VMMDevSharedModuleCheckRequest);
        case VMMDevReq_GetPageSharingStatus:
            return sizeof(VMMDevPageSharingStatusRequest);
        case VMMDevReq_DebugIsPageShared:
            return sizeof(VMMDevPageIsSharedRequest);
        case VMMDevReq_GetSessionId:
            return sizeof(VMMDevReqSessionId);
        default:
            break;
    }

    return 0;
}

/**
* Initializes a request structure.
*
* @returns VBox status code.
* @param   req             The request structure to initialize.
* @param   type            The request type.
*/
static inline int vmmdevInitRequest(VMMDevRequestHeader *req, VMMDevRequestType type)
{
    uint32_t requestSize;
    if (!req)
        return VERR_INVALID_PARAMETER;
		
    requestSize = (uint32_t)vmmdevGetRequestSize(type);
    if (!requestSize)
        return VERR_INVALID_PARAMETER;
		
    req->size        = requestSize;
    req->version     = VMMDEV_REQUEST_HEADER_VERSION;
    req->requestType = type;
    req->rc          = VERR_GENERAL_FAILURE;
    req->reserved1   = 0;
    req->reserved2   = 0;
    return VINF_SUCCESS;
}


int RTStrCopy(char *pszDst, size_t cbDst, const char *pszSrc)
{
    size_t cchSrc = strlen(pszSrc);
    if (RT_LIKELY(cchSrc < cbDst))
    {
        memcpy(pszDst, pszSrc, cchSrc + 1);
        return VINF_SUCCESS;
    }

    if (cbDst != 0)
    {
        memcpy(pszDst, pszSrc, cbDst - 1);
        pszDst[cbDst - 1] = '\0';
    }
    return VERR_BUFFER_OVERFLOW;
}

/**
 * Registers a new shared module for the VM
 *
 * @returns IPRT status code.
 * @param   pszModuleName       Module name
 * @param   pszVersion          Module version
 * @param   GCBaseAddr          Module base address
 * @param   cbModule            Module size
 * @param   cRegions            Number of shared region descriptors
 * @param   pRegions            Shared region(s)
 * @param 	enmGuestOS			Guest OS type 
 */
int VbglR3RegisterSharedModule(char *pszModuleName, char *pszVersion,
                                RTGCPTR64  GCBaseAddr, uint32_t cbModule,
                                unsigned cRegions, VMMDEVSHAREDREGIONDESC *pRegions, 
								VBOXOSFAMILY enmGuestOS )
{
    VMMDevSharedModuleRegistrationRequest *pReq;
    int rc;

    pReq = (VMMDevSharedModuleRegistrationRequest *)calloc(1, RT_OFFSETOF(VMMDevSharedModuleRegistrationRequest, aRegions[cRegions]));

    vmmdevInitRequest(&pReq->header, VMMDevReq_RegisterSharedModule);
    pReq->header.size   = RT_OFFSETOF(VMMDevSharedModuleRegistrationRequest, aRegions[cRegions]);
    pReq->GCBaseAddr    = GCBaseAddr;
    pReq->cbModule      = cbModule;
    pReq->cRegions      = cRegions;
    pReq->enmGuestOS    = enmGuestOS;
	
    for (unsigned i = 0; i < cRegions; i++)
        pReq->aRegions[i] = pRegions[i];

    if ( RTStrCopy(pReq->szName, sizeof(pReq->szName), pszModuleName) != VINF_SUCCESS
        || RTStrCopy(pReq->szVersion, sizeof(pReq->szVersion), pszVersion) != VINF_SUCCESS )
    {
        rc = VERR_BUFFER_OVERFLOW;
        goto end;
    }

    rc = vbglR3GRPerform(&pReq->header);

end:
    free(pReq);
    return rc;
}

/**
 * Unregisters a shared module for the VM
 *
 * @returns IPRT status code.
 * @param   pszModuleName       Module name
 * @param   pszVersion          Module version
 * @param   GCBaseAddr          Module base address
 * @param   cbModule            Module size
 */
int VbglR3UnregisterSharedModule(char *pszModuleName, char *pszVersion, RTGCPTR64 GCBaseAddr, uint32_t cbModule)
{
    VMMDevSharedModuleUnregistrationRequest Req;

    vmmdevInitRequest(&Req.header, VMMDevReq_UnregisterSharedModule);
    Req.GCBaseAddr    = GCBaseAddr;
    Req.cbModule      = cbModule;

    if( RTStrCopy(Req.szName, sizeof(Req.szName), pszModuleName) != VINF_SUCCESS
        || RTStrCopy(Req.szVersion, sizeof(Req.szVersion), pszVersion) != VINF_SUCCESS)
    {
        return VERR_BUFFER_OVERFLOW;
    }
    return vbglR3GRPerform(&Req.header);
}


/**
 * Checks registered modules for shared pages
 *
 * @returns IPRT status code.
 */
int VbglR3CheckSharedModules()
{
    VMMDevSharedModuleCheckRequest Req;

    vmmdevInitRequest(&Req.header, VMMDevReq_CheckSharedModules);
    return vbglR3GRPerform(&Req.header);
}


/**
 * Query the session ID of this VM.
 *
 * The session id is an unique identifier that gets changed for each VM start,
 * reset or restore.  Useful for detection a VM restore.
 *
 * @returns IPRT status code.
 * @param   pu64IdSession       Session id (out).  This is NOT changed on
 *                              failure, so the caller can depend on this to
 *                              deal with backward compatibility (see
 *                              VBoxServiceVMInfoWorker() for an example.)
 */
static int VbglR3GetSessionId(uint64_t *pu64IdSession)
{
    VMMDevReqSessionId Req;

    vmmdevInitRequest(&Req.header, VMMDevReq_GetSessionId);
    Req.idSession = 0;
    int rc = vbglR3GRPerform(&Req.header);
    if (RT_SUCCESS(rc))
        *pu64IdSession = Req.idSession;

    return rc;
}

static uint64_t         g_idSession = 0;
/** @interface_method_impl{VBOXSERVICE,pfnInit} */
int VgsvcPageSharingInit(void)
{
    int rc;

    rc = VbglR3GetSessionId( &g_idSession );
    if ( RT_FAILURE(rc) )
    {
        if ( rc == VERR_IO_GEN_FAILURE )
            VGSvcVerbose(0, "PageSharing: Page sharing support is not available by the host\n");
        else
            VGSvcError("vgsvcPageSharingInit: Failed with rc=%d\n", rc);

        rc = VERR_GENERAL_FAILURE;
    }
    else
	{
        ALOGI( "Got session id %lx\n", (unsigned long)g_idSession );
    }

    return rc;
}
