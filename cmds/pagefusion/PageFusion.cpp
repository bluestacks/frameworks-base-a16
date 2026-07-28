#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
// A16DBG:P2 a16 bionic: -D__BIONIC_NO_PAGE_SIZE_MACRO disables the PAGE_SIZE macro globally;
// pagefusion uses it as a compile-time const (x86_64 guest = 4KB pages, same as a13).
#ifndef PAGE_SIZE
#define PAGE_SIZE 4096
#endif
#ifndef PAGE_MASK
#define PAGE_MASK (~(PAGE_SIZE - 1))
#endif
#include <limits.h>
#include <string.h>
#include <dirent.h>
#include <getopt.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <cutils/properties.h>
#include <android/log.h>
#include <map>
#include <string>
#include "list.h"
#include "RTVboxGuest.h"
#include "util.h"

using namespace std;

#define SUFFIX32    "_32"
#define SUFFIX64    "_64"

#define PAGE_ALIGN(addr)    (((addr)+PAGE_SIZE-1)&PAGE_MASK)

static bool debug = false;

enum {
    STATUS_OK = 0,      /*Multiple instances and this feature is supported*/
    STATUS_ONE_INSTANCE,/*Only one instance*/
    STATUS_UNSUPPORTED  /*Pagefusion is not allowed*/
};

enum MODULETYPE
{
    TYPE_SO = 0,    /*Dynamic library*/
    TYPE_EXE,       /*Executable file*/
    TYPE_FONT,      /*Font file and hyb file - Hyphenation*/
    TYPE_FRAMEWORK, /*System apk & jar*/
    TYPE_OAT,       /*oat & dex with oat*/
    TYPE_KERNEL = 99/*Kernel text section*/
};

/*The descriptor of one module*/
typedef struct _SharedModule
{
    LIST            node;           /*Doubly linked list*/
    char            name[128];      /*File name*/
    char            version[16];    /*Module version*/
    unsigned long   base_address;   /*Base address*/
    unsigned long   size;           /*Module size*/
    unsigned long   file_offset;    /*Offset in file. After experiments, this value is basically 0*/
    uint8_t*        page_bmap;      /*Bitmap of page location. If set, the page is present in RAM*/
    MODULETYPE      type;           /*Module type*/
    unsigned int    age;            /*Module age*/
    bool            bRegistered;    /*Whether the module is registered*/
    bool            bMapped;        /*Whether the module is mapped */
}SHAREDMODULE, *PSHAREDMODULE;

/*Match pattern of specified Module*/
typedef struct _ModulePattern
{
    MODULETYPE  type;                               /*Module type*/
    bool (*match)( char* name, char* permissions ); /*match function*/
}MODULEPATTERN, *PMODULEPATTERN;

/*Global objects*/
static char s_ModuleVersion[16] = "";       /*The Module version*/
static SHAREDMODULE* s_SharedModuleList = NULL;         /*Module List*/
static std::map<std::string, PSHAREDMODULE> s_ModuleMap;/*Module map*/

/**
 * @brief Create a new shared module and init it.
 */
PSHAREDMODULE CreateSharedModule()
{
    PSHAREDMODULE module = (PSHAREDMODULE)calloc( 1, sizeof(SHAREDMODULE) );
    if( module )
    {
        INIT_LIST_HEAD( &module->node );
    }
    else
    {
        ALOGE( "No enough memory!\n" );
    }
    return module;
}

/**
 * Free one node
 */
void FreeSharedModule( PSHAREDMODULE module )
{
    if( module )
    {
        list_del( &module->node );
        if( module->page_bmap )
        {
            free( module->page_bmap );
        }
        free( module );
    }
}

/**
 * @brief Free the shared module list.
 */
void FreeSharedModuleList()
{
    if( s_SharedModuleList )
    {
        PSHAREDMODULE module;
        struct list_head *pos;
        struct list_head *n;
        list_for_each_safe( pos, n, &s_SharedModuleList->node )
        {
            module = list_entry( pos, SHAREDMODULE, node );
            FreeSharedModule( module );
        }
    }
}

/**
 * @brief Get module's version, i.e. 7.1_32
 */
const char* GetModuleVersion( void )
{
    if( strlen( s_ModuleVersion ) == 0 )
    {
        char tempBuff[ PROPERTY_VALUE_MAX ];
        char szROMVersion[ PROPERTY_VALUE_MAX ];
        char szSuffix[4];

        if( property_get( "ro.build.version.release", szROMVersion, NULL) > 0 && property_get("ro.zygote", tempBuff, NULL) > 0 )
        {
            if (!strncmp(tempBuff, "zygote32", strlen("zygote32")))
            {
                strncpy( szSuffix, SUFFIX32, 4 );
            }
            else
            {
                strncpy( szSuffix, SUFFIX64, 4 );
            }

            memset( s_ModuleVersion, 0, sizeof(s_ModuleVersion) );
            snprintf( s_ModuleVersion, sizeof(s_ModuleVersion), "%s%s", szROMVersion, szSuffix );
        }
    }
    return &s_ModuleVersion[0];
}

/**
 * @brief Create a new shared module and put it into the list.
 */
PSHAREDMODULE AddSharedModule( const char* path_name, unsigned long base_address, unsigned long size, MODULETYPE type,
                                unsigned long file_offset )
{
    PSHAREDMODULE module = CreateSharedModule();
    if( module )
    {
        /* If the file path of a module is too long, exceeding 128 bytes, forbid to create it */
        if( strlen(path_name) > sizeof(module->name)-1 )
        {
            ALOGW( "Because module name is too long, can't create it. Its len %d, name '%s'\n", (int)strlen(path_name), path_name );
            return NULL;
        }

        strncpy( module->name, path_name, sizeof(module->name)-1 );
        strncpy( module->version, GetModuleVersion(), sizeof(module->version)-1 );
        module->base_address = base_address;
        module->size = size;
        module->type = type;
        module->file_offset = file_offset;

        /*Allocate some memory for saving the bitmap. The default setting page status is not in memory. */
        uint32_t bmapSize = ((size/PAGE_SIZE)+7)/sizeof(uint8_t);
        module->page_bmap = (uint8_t*)calloc( 1, bmapSize );
        if( !module->page_bmap )
        {
            ALOGE( "No enough memory!\n" );
            FreeSharedModule( module );
            return NULL;
        }

        list_add_tail( &module->node, &s_SharedModuleList->node );
    }
    return module;
}

/**
 * @brief Check whether a file is a dynamic library and can be shared
 */
bool MatchSOModule( char* name, char* permissions )
{
    /* Almost all dynamic libraries can be shared, except for a few non-PIC dynamic libraries.
     * Generally, each dynamic library will map at least two segments, and only the first one
     * with executable permissions can be used for sharing
     */
    if( EndsWith( name, ".so" ) && permissions[0] == 'r' && permissions[2] == 'x' )
    {
        if( IsPICDynamicModule( name ) )
        {
            return true;
        }
        ALOGW( "Module %s is non PIC!\n", name );
    }
    return false;
}

/**
 * @brief Check whether a file is a executable file and can be shared
 */
bool MatchExeModule( char* name, char* permissions )
{
    /* All executalbe files can be shared. Generally, each file will map three segments.
     * Only the first segment with executable permission can be used for sharing.
     */
    if( permissions[0] == 'r' && permissions[2] == 'x'
        && IsElfExe( name ) )
    {
        return true;
    }
    return false;
}

/**
 * @brief Check whether a file is a font or hyb file and can be shared
 */
bool MatchFontModule( char* name, char* permissions )
{
    /* All fonts can be shared. Their permissions are 'r--s'.*/
    if( permissions[0] == 'r' && permissions[3] == 's' && StartsWith( name, "/system/fonts/") )
    {
        if( EndsWith( name, ".ttf") || EndsWith( name, ".ttc") )
        {
            return true;
        }
    }
    return false;
}

/**
 * @brief Check whether a file is a apk or jar file and can be shared
 */
bool MatchFrameworkModule( char* name, char* permissions )
{
    static const char* FRAMEWORKFILELIST[] =
    {
        "/system/usr/icu/icudt56l.dat",
        "/system/usr/share/zoneinfo/tzdata"
    };

    /* Some framework files including apk, jar and ICU files can be shared.
     * Their permissions are 'r--s'.
     */
    if( permissions[0] == 'r' && permissions[3] == 's' && StartsWith( name, "/system/" ) )
    {
        for( unsigned int i = 0; i < _countof(FRAMEWORKFILELIST); i++ )
        {
            if( strncmp( name, FRAMEWORKFILELIST[i], strlen(FRAMEWORKFILELIST[i]) ) == 0 )
            {
                return true;
            }
        }

        if( EndsWith( name, ".apk" ) || EndsWith( name, ".jar") )
        {
            return true;
        }
    }

    return false;
}

/**
 * @brief Check whether a file is a oat or dex file and can be shared
 */
bool MatchOATModule( char* name, char* permissions )
{
    /* For OAT and dex files, only the first one including rodata section can be shared.
     * Their permissions is r--p. The second segment is a executable segment. But Android
     * will be modified according to the user's historical operations at runtime in order
     * to improve the performance of the execution.
     */
    if( permissions[0] == 'r' && permissions[2] == '-' && permissions[3] == 'p'
        && StartsWith( name, "/data/dalvik-cache/" ) )
    {
        if( (EndsWith( name, ".oat") || EndsWith( name, "@classes.dex")) && IsDynamicLibrary( name ) )
        {
            return true;
        }
    }

    return false;
}


/*Pattern list*/
MODULEPATTERN s_ModulePatterns[] = {
    { TYPE_SO,          MatchSOModule   },
    { TYPE_EXE,         MatchExeModule  },
    { TYPE_FONT,        MatchFontModule },
    { TYPE_FRAMEWORK,   MatchFrameworkModule },
    { TYPE_OAT,         MatchOATModule },
};

/**
 * @brief Statistics module of specified type
 */
void StatisticsModule( MODULETYPE type )
{
    unsigned long totalSize = 0;
    unsigned long totalMapSize = 0;
    int cnt = 0;
    PSHAREDMODULE module;
    struct list_head* pos;

    list_for_each( pos, &s_SharedModuleList->node )
    {
        module = list_entry( pos, SHAREDMODULE, node );
        if( module->type != type )
        {
            continue;
        }

        cnt++;
        totalSize += module->size;
        totalMapSize += PAGE_SIZE * GetBitmapSetNum( &module->page_bmap[0], module->size/PAGE_SIZE );
    }
    if (debug) ALOGI( "Statistics: module type=%d, count=%d, total size=%lu, total mapped size=%lu\n", type, cnt, totalSize, totalMapSize );
}

static inline uint8_t ASMProbeReadByte(const void *pvByte)
{
    /** We have verified that the compiler actually doesn't optimize this away. (intel & gcc, AMD & gcc) */
    uint8_t u8;
    __asm__ __volatile__("movb (%1), %0\n\t"
    : "=r" (u8)
    : "r" (pvByte));
    return u8;
}

/**
 * @brief Map all modules to virtual address of this process
 */
void MapAllModules(void)
{
    PSHAREDMODULE module;
    struct list_head* pos;
    list_for_each( pos, &s_SharedModuleList->node )
    {
        module = list_entry( pos, SHAREDMODULE, node );
        if( module->bMapped )
        {
            continue;
        }
        module->base_address = 0;

        /* Skip the module whose pages are not present in RAM*/
        if( GetBitmapSetNum( &module->page_bmap[0], module->size/PAGE_SIZE ) == 0 )
        {
            continue;
        }

        int fd = open( module->name, O_RDONLY );
        if( fd > 0 )
        {
            void* base_addr = mmap(NULL, module->size, PROT_READ, MAP_PRIVATE, fd, module->file_offset );
            if( !base_addr )
            {
                ALOGW( "Map %s failed!\n", module->name );
            }
            else/*touch the module file*/
            {
                uint8_t* pageAddr = (uint8_t*)base_addr;
                for( unsigned long i = 0; i < module->size; i += PAGE_SIZE )
                {
                    /*Only touch the page which is present in RAM*/
                    if( IsBitSet( &module->page_bmap[0], i/PAGE_SIZE ))
                    {
                        /* Should we use attribute((used)) for variable 'c' as an aggressive compiler option
                         * may remove this call if 'c' is not being referenced below. Although, I think this
                         * is not likely as the called function can have side affects that impact the correctness
                         * of the program - but just in case.
                         * We must add static modification, otherwise get a compile warning: 'used' attribute ignored [-Wignored-attributes]
                         * But the compiler will remove this call when using 'static uint8_t c __attribute__((used))',
                         * so keep it.
                         */
                        __unused uint8_t c = ASMProbeReadByte( pageAddr );
                    }
                    pageAddr += PAGE_SIZE;
                }

                module->base_address = (unsigned long)base_addr;
                module->bMapped = true;
            }
            close( fd );
        }
        else
        {
            ALOGW( "Failed to open file %s!\n", module->name );
        }
    }
}

/**
 * @brief Unmap all modules
 */
void UnmapAllModules()
{
    if(!s_SharedModuleList)
        return;

    PSHAREDMODULE module;
    struct list_head* pos;
    list_for_each( pos, &s_SharedModuleList->node )
    {
        module = list_entry( pos, SHAREDMODULE, node );

        /* unmap can be called on the module which have been mapped into pagefusion address space. */
        if( module->bMapped && module->base_address && module->type != TYPE_KERNEL )
        {
            munmap( (void*)module->base_address, (size_t)module->size );
            module->bMapped = false;
        }
    }
}

/**
 * @brief Check whether only one instance is running or this function is not supported
 */
int CheckOneInstanceRunningOrUnsupported(void)
{
    char name[32] = "PageFusionServiceNeedRun";
    char version[16] = "7.1";
    VBOXOSFAMILY osType = VBOXOSFAMILY_Linux32;
    VMMDEVSHAREDREGIONDESC RegionDesc;

    /* @TODO After making a new hypercall in VBox, modify the following implementation.*/
    RegionDesc.GCRegionAddr = (RTGCPTR64)0x60000000;/*Any value*/
    RegionDesc.cbRegion = (uint32_t)0x1000;
    int rc = VbglR3RegisterSharedModule( name, version, (RTGCPTR64)0x60000000, 0x1000, 1, &RegionDesc, osType );
    if( rc == VINF_SUCCESS )
    {
        VbglR3UnregisterSharedModule( name, version, (RTGCPTR64)0x60000000, 0x1000 );
        return STATUS_OK;
    }
    else if( rc == VERR_IGNORED )
    {
        if(debug) ALOGI("Only one instance is running. Disable this feature.\n" );
        return STATUS_ONE_INSTANCE;
    }
    /*else rc == VERR_NOT_SUPPORTED */

    ALOGW("This feature is not supported.\n" );
    return STATUS_UNSUPPORTED;
}

/**
 * @brief Register all modules
 */
int RegisterAllModules(void)
{
    PSHAREDMODULE module;
    struct list_head* pos;

    /* Walk the list and register each module */
    list_for_each( pos, &s_SharedModuleList->node )
    {
        module = list_entry( pos, SHAREDMODULE, node );
        if( module->bRegistered || !module->bMapped )continue;

        VBOXOSFAMILY osType = VBOXOSFAMILY_Linux32;
        if( strstr( module->version, SUFFIX64 ))osType = VBOXOSFAMILY_Linux64;

        /* If dividing a module into multiple regions based on whether the page is in RAM,
         * you will find that different VM instances have different numbers of regions,
         * and Vbox will find the differences and consider the module as unshareable. So all modules
         * are registered with only one region. When VBox is doing the check operation,
         * it'll ignore those pages which are not present in RAM.
         */
        VMMDEVSHAREDREGIONDESC RegionDesc;
        RegionDesc.GCRegionAddr = (RTGCPTR64)module->base_address;
        RegionDesc.cbRegion = (uint32_t)module->size;
        int rc = VbglR3RegisterSharedModule( module->name, module->version, (RTGCPTR64)module->base_address,
                                            (uint32_t)module->size, 1, &RegionDesc, osType );

        if( rc != VINF_SUCCESS )
        {
            ALOGW("Falled to register the module %s, verion=%s! rc = %d\n", module->name, module->version, rc );
            return rc;
        }
        module->bRegistered = true;

        if (debug)
        {
            ALOGI( "Regiser module %s: %s, base address=0x%08lx, memsize=0x%lx, version=%s, type=%d\n", module->name,
                    rc == VINF_SUCCESS?"ok":"fail", module->base_address, module->size, module->version, module->type );
        }
    }

    return VINF_SUCCESS;
}

/**
 * @brief Unregister all modules
 */
void UnregisterAllModules(void)
{
    if(!s_SharedModuleList)
        return;

    PSHAREDMODULE module;
    struct list_head* pos;

    list_for_each( pos, &s_SharedModuleList->node )
    {
        module = list_entry( pos, SHAREDMODULE, node );
        if( !module->bRegistered )continue;

        int rc = VbglR3UnregisterSharedModule( module->name, module->version, (RTGCPTR64)module->base_address,
                                            (uint32_t)module->size );

        if( rc != VINF_SUCCESS )
        {
            ALOGE("Unregister %s failed!\n", module->name );
        }
        if (debug) ALOGI( "Unregister module %s: %s\n", module->name, rc == VINF_SUCCESS?"ok":"fail" );
    }
}

/**
 * @brief Unregister, unmap and delete old modules which have been unloaded
 */
void UnregisterOutdatedModules( unsigned int expectedAge )
{
    PSHAREDMODULE module;
    struct list_head* pos;
    struct list_head* tmpNode;

    list_for_each_safe( pos, tmpNode, &s_SharedModuleList->node )
    {
        module = list_entry( pos, SHAREDMODULE, node );
        if( module->age == expectedAge )continue;

        if( module->bRegistered )
        {
            int rc = VbglR3UnregisterSharedModule( module->name, module->version, (RTGCPTR64)module->base_address,
                    (uint32_t)module->size );

            if( rc != VINF_SUCCESS )
            {
                ALOGE("Unregister %s failed!\n", module->name );
            }
            else
            {
                module->bRegistered = false;
            }
            if (debug) ALOGI( "Unregister module %s: %s\n", module->name, rc == VINF_SUCCESS?"ok":"fail" );
        }/*else skip the unregistered modules*/

        if( module->bRegistered == false && module->bMapped )
        {
            munmap( (void*)module->base_address, (size_t)module->size );
            module->bMapped = false;
        }

        /* When a module was released, it must be removed from std::map.*/
        string strName = module->name;
        s_ModuleMap.erase( strName );
        FreeSharedModule( module );
    }
}

/**
 * @brief Create a module for sharing kernel text section. It refers to Sumedh's POC.
 */
void CreateKernelSharedModule( int age )
{
    const char* name = "vmlinux";
    string strName = name;
    PSHAREDMODULE mod;

    map<string,PSHAREDMODULE>::iterator it = s_ModuleMap.find( strName );
    if( it == s_ModuleMap.end() )
    {
        char line[256];

        FILE* fp = fopen( "/proc/kinfo", "r" );
        if( fp )
        {
            memset( line, 0, sizeof(line));
            if( fgets( line, sizeof(line), fp ) != NULL )
            {
                unsigned long ktext_start, ktext_end;
                if( sscanf( line, "%lx-%lx", &ktext_start, &ktext_end ) == 2 )
                {
                    ktext_start = PAGE_ALIGN(ktext_start);
                    ktext_end = (ktext_end + PAGE_SIZE) & PAGE_MASK;

                    mod = AddSharedModule( name, ktext_start, ktext_end - ktext_start, TYPE_KERNEL, 0 );
                    if( mod )
                    {
                        s_ModuleMap.insert( std::pair<string,PSHAREDMODULE>(strName, mod) );
                        mod->age = age;
                        mod->bMapped = true;/*No need to map Kernel */
                    }
                }
            }/*else return on error*/
            fclose( fp );
        }
    }
    else
    {
        mod = it->second;
        mod->age = age;
    }
}

/**
 * @brief Check whether the module is a file
 */
bool IsValidModule( const char* name )
{
    if( name[0] == '['
        || StartsWith( name, "/dev/" )
        || StartsWith( name, "/sys/" ))/*strncmp + strlen is much more efficient than strstr*/
    {
        return false;
    }
    return true;
}

/**
 * @brief Build the bitmap. Each bit represents whether the page is present in RAM
 */
bool BuildModuleRegionPresentBitmap( int pid, PSHAREDMODULE mod )
{
    bool ret_val = true;
    char mapFilePath[MAX_PATH];
    int fd;

    snprintf( mapFilePath, sizeof(mapFilePath), "/proc/%d/pagemap", pid );
    fd = open( mapFilePath, O_RDONLY );
    if( fd < 0 )
    {
        ALOGW("Failed to access the file %s!\n", mapFilePath );
        return false;
    }

    /* Walk the pagemaps and record those pages which are present in RAM.*/
    int startIdx = mod->base_address/PAGE_SIZE;
    int lastIdx = startIdx + mod->size/PAGE_SIZE;

    for( int pageIdx = startIdx; pageIdx < lastIdx; pageIdx++ )
    {
        uint64_t data;
        uint64_t offsetPageMap = pageIdx * sizeof(data);
        if( pread( fd, &data, sizeof(data), offsetPageMap ) != sizeof(data) )
        {
            ALOGE( "Error reading pagemap for offset addr 0x%llx\n", (unsigned long long)offsetPageMap );
            ret_val = false;
            break;
        }

        /* 63bit: If set, the page is present in RAM.
         * 61bit: If set, the page is a file-mapped page.
         */
        #define RAM_PRESENT_MASK    ((((uint64_t)1)<<63)|(((uint64_t)1)<<61))
        if( (data & RAM_PRESENT_MASK) == RAM_PRESENT_MASK )
        {
            SetBit( &mod->page_bmap[0], pageIdx - startIdx );
        }
    }

    close( fd );
    return ret_val;
}

/**
 * @brief Read memory map file of specified process and get all modules
 */
void WalkProcessMemMaps( int pid, unsigned int age )
{
    char mapFilePath[MAX_PATH];
    FILE* fpMapFile;
    char line[512];

    snprintf( mapFilePath, sizeof(mapFilePath), "/proc/%d/maps", pid );
    fpMapFile = fopen( mapFilePath, "r" );
    if( !fpMapFile )
    {
        return;
    }

    /*scan all modules*/
    memset( line, 0, sizeof(line));
    while( fgets( line, sizeof(line), fpMapFile ) != NULL )
    {
        unsigned long base_address;
        unsigned long end_address;
        char permissions[8];
        unsigned long offset = 0;
        int dev_major = 0;
        int dev_minor = 0;
        int inode = 0;                  /*device inode*/
        char path_name[MAX_PATH];

        /*parse the line*/
        memset( path_name, 0, sizeof(path_name ));
        memset( permissions, 0, sizeof(permissions));
        int n = sscanf( line, "%08lx-%08lx %s %08lx %02x:%02x %d    %s\n",
                        &base_address, &end_address, permissions, &offset,
                        &dev_major, &dev_minor, &inode, path_name );
        if( n != 8 )
        {
            continue;
        }

        /*skip particular files*/
        TrimString( path_name );
        if( strlen( path_name ) == 0 || !IsValidModule( path_name ) || permissions[1] == 'w' )
        {
            continue;
        }

        /*  Check whether it is an existing module first, the performance will be better */
        PSHAREDMODULE mod;
        string strName = path_name;

        map<string,PSHAREDMODULE>::iterator it = s_ModuleMap.find( strName );
        if( it == s_ModuleMap.end() )
        {
            /* Find the module and insert into the list */
            for( unsigned int i = 0; i < _countof( s_ModulePatterns ); i++ )
            {
                /*Check whether it's a valid module */
                PMODULEPATTERN pattern = &s_ModulePatterns[i];
                if( pattern->match && pattern->match( path_name, permissions ) )
                {
                    /* For OAT and dex files, only rodata section can be shared.
                     * And the first two pages must be ignored, which include ELF header and OAT header.
                     */
                    if( pattern->type == TYPE_OAT )
                    {
                        base_address += 2*PAGE_SIZE;
                        offset += 2*PAGE_SIZE;
                    }

                    mod = AddSharedModule( path_name, base_address, end_address - base_address, pattern->type, offset );
                    if( mod && BuildModuleRegionPresentBitmap( pid, mod ))
                    {
                        s_ModuleMap.insert( std::pair<string,PSHAREDMODULE>(strName, mod) );
                        mod->age = age;
                    }

                    break;
                }/*else continue*/
            }
        }
        else/*update the age of existing module*/
        {
            mod = it->second;
            mod->age = age;
        }
    }

    fclose( fpMapFile );
}

static void sigaction_exception(int signal, siginfo_t *si, void *arg)
{
    ucontext_t *ctx = (ucontext_t *)arg;

    /* We are on linux x86, the returning IP is stored in RIP (64bit) or EIP (32bit).
       In this example, the length of the offending instruction is 6 bytes.
       So we skip the offender ! */
    #if __WORDSIZE == 64
        ALOGI("[%d]Caught SIGNAL(%d), addr %p, RIP 0x%lx\n", getpid(), signal, si->si_addr, ctx->uc_mcontext.gregs[REG_RIP]);
        ctx->uc_mcontext.gregs[REG_RIP] += 2;
    #else
        ALOGI("[%d]Caught SIGNAL(%d), addr %p, EIP 0x%x\n", getpid(), signal, si->si_addr, ctx->uc_mcontext.gregs[REG_EIP]);
        ctx->uc_mcontext.gregs[REG_EIP] += 2;
    #endif

    UnregisterAllModules();
    UnmapAllModules();
    s_ModuleMap.clear();
    FreeSharedModuleList();
    VbglR3Uninit();
    exit( -1 );
}

/**
 * @brief Init the- callback of signals
 */
void InitSignalAction( void )
{
    struct sigaction sa;
    memset( &sa, 0, sizeof(sa) );
    sigemptyset( &sa.sa_mask );
    sa.sa_sigaction = sigaction_exception;
    sa.sa_flags = SA_SIGINFO;
    sigaction( SIGSEGV, &sa, NULL );
    sigaction( SIGINT,  &sa, NULL );
    sigaction( SIGTERM, &sa, NULL );
}

/**
 * @brief Print the usage message
 */
static void Usage(void)
{
    fprintf( stderr,
            "usage: pagefusion [-h] [--no-exe] [--no-font] [--no-framework]\n"
            "   -h:             this message\n"
            "   --no-exe:       disable sharing of executable files\n"
            "   --no-font:      disable sharing of font files\n"
            "   --no-framework: disable sharing of framework files\n");
}

/**
 * @brief Parse arguments
 */
void ParseArgs( int argc, char** argv )
{
    struct option long_opts[] =
    {
        { "no-exe", 0, NULL, 'x' },
        { "no-font", 0, NULL, 'f' },
        { "no-framework", 0, NULL, 'w' },
        { NULL, 0, NULL, 0 }
    };
    int c;
    int opt_index = 0;
    while(( c = getopt_long( argc, argv, "h", long_opts, &opt_index )) != -1)
    {
        switch( c )
        {
            case 'f':
                s_ModulePatterns[TYPE_FONT].match = NULL;
                break;

            case 'w':
                s_ModulePatterns[TYPE_FRAMEWORK].match = NULL;
                break;

            case 'x':
                s_ModulePatterns[TYPE_EXE].match = NULL;
                break;

            case '?':
            case 'h':
                Usage();
                exit( 0 );
                break;

            default:
                break;
        }
    }
}


int main(int argc, char** argv)
{
    int ret_val = 0;
    int pid_self = getpid();
    int rc;
    char value[PROPERTY_VALUE_MAX];

    property_get("bst.debug.pagefusion", value, "0");
    debug = atoi(value) > 0 ? true : false;

    /*Parse arguments*/
    ParseArgs( argc, argv );

    InitSignalAction();

    ALOGI( "Init page sharing service.\n" );
    if( VINF_SUCCESS != VbglR3Init() || VINF_SUCCESS != VgsvcPageSharingInit() )
    {
        ALOGE( "Failed to init page sharing service!" );
        return -1;
    }

    /*Init the shared module list.*/
    s_SharedModuleList = CreateSharedModule();
    s_ModuleMap.clear();

    /* Each loop, the age value of the found module will be increased by 1.
       Modules not found will remain unchanged.
       Those modules that have not been added are outdated and should be unregistered. */
    unsigned int age = 1;

    /*Run a loop for scanning process and performing share operation */
    do
    {
        int featureStatus = CheckOneInstanceRunningOrUnsupported();
        if( featureStatus == STATUS_UNSUPPORTED )
        {
            break;
        }
        else if( featureStatus == STATUS_ONE_INSTANCE )
        {
            /*If only one instance is running, check the status faster */
            if(debug) ALOGI( "Waiting 15 seconds for next loop\n");
            sleep( 15 );
            continue;
        }/*else multiple instances & supported */

        /*Scan all processes and get all modules which can be shared*/
        struct dirent* dirEntry;
        DIR* dirProc = opendir( "/proc" );
        if( !dirProc )
        {
            ret_val = -1;
            ALOGE( "Failed to open proc directory!" );
            break;
        }

        while( (dirEntry = readdir( dirProc )))
        {
            char c = dirEntry->d_name[0];
            if( dirEntry->d_type != DT_DIR || c > '9' || c < '0' )/*Only open the directory representing the process*/
            {
                continue;
            }

            /*scan the map file and get all modules*/
            int pid = atoi( dirEntry->d_name );
            if( pid != pid_self )
            {
                WalkProcessMemMaps( pid, age );
            }
        }
        closedir( dirProc );

        /*
         * The freezing issue on AMD machine has been resolved. The sharing of the kernel text section can be reopened.
         */
        CreateKernelSharedModule( age );

        /*delete outdated modules*/
        UnregisterOutdatedModules( age );
        age++;

        /*Map the files of all modules to the virtual memory of this process*/
        MapAllModules();
        if (debug) ALOGI( "RegisterAllModules ...\n" );
        rc = RegisterAllModules();
        if( rc != VINF_SUCCESS )
        {
            ALOGW( "Failed to register all modules!\n" );
            break;
        }

        if (debug) ALOGI( "Check shared modules ...\n" );
        rc = VbglR3CheckSharedModules();
        if( rc != VINF_SUCCESS )
        {
            ALOGW( "Failed to check all modules!\n" );
            break;
        }

        StatisticsModule( TYPE_SO );
        StatisticsModule( TYPE_EXE );
        StatisticsModule( TYPE_FONT );
        StatisticsModule( TYPE_FRAMEWORK );
        StatisticsModule( TYPE_OAT );

        /*Sleep for 60 seconds and proceed to the next scan*/
        if (debug) ALOGI( "Waiting 60 seconds for next loop\n");
        sleep( 60 );
    }while(1);

    StatisticsModule( TYPE_SO );
    StatisticsModule( TYPE_EXE );
    StatisticsModule( TYPE_FONT );
    StatisticsModule( TYPE_FRAMEWORK );
    StatisticsModule( TYPE_OAT );

    if (debug) ALOGI( "UnregisterAllModules ...\n" );
    UnregisterAllModules();
    UnmapAllModules();

    s_ModuleMap.clear();
    FreeSharedModuleList();
    VbglR3Uninit();
    return ret_val;
}
