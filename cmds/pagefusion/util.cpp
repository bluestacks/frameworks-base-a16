#include <stdio.h>
#include <stdlib.h>
#include <elf.h>
#include <string.h>
#include "util.h"

union Elf_Ehdr {
    Elf32_Ehdr header32;
    Elf64_Ehdr header64;
};

union Elf_Phdr {
    Elf32_Phdr phdr32;
    Elf64_Phdr phdr64;
};

union Elf_Dyn {
    Elf32_Dyn dyn32;
    Elf64_Dyn dyn64;
};

/**
 * @brief Read the data of one file
 */
bool GetFileData( const char* file_path, unsigned char* data_buffer, size_t buffer_size )
{
    size_t n = 0;
    FILE* fp = fopen( file_path, "rb" );
    if( fp )
    {
        n = fread( data_buffer, 1, buffer_size, fp );
        fclose( fp );
    }
    return n == buffer_size;
}

/**
 * @brief allocate a memory and read one region of one file from specified offset
 */
unsigned char* ReadFileRegionData( const char* file_path, long offset, size_t region_len )
{
    if( offset < 0 || region_len == 0 )
    {
        return NULL;
    }

    unsigned char* data = (uint8_t*)malloc( region_len );
    if( data )
    {
        size_t n = 0;
        FILE* fp = fopen( file_path, "rb" );
        if( fp )
        {
            if( fseek( fp, offset, SEEK_SET ) == 0)
            {
                n = fread( data, 1, region_len, fp );
            }
            fclose( fp );
        }

        if( n != region_len )
        {
            free( data );
            data = NULL;
        }
    }
    return data;
}

/**
 * @brief Get file class if the file is an ELF binary
 * @retval ELFCLASSNONE - Invalid class
 * @retval ELFCLASS32 - 32–bit objects
 * @retval ELFCLASS64 - 64–bit objects
 */
static uint8_t GetElfClass(const char* file_path)
{
    uint8_t ident[EI_NIDENT];
    if (GetFileData(file_path, ident, EI_NIDENT))
    {
        if (ident[EI_MAG0] == 0x7F && ident[EI_MAG1] == 'E'
            && ident[EI_MAG2] == 'L' && ident[EI_MAG3] == 'F')
        {
            return ident[EI_CLASS];
        }
    }
    return ELFCLASSNONE;
}

/**
 * @brief Read ELF header that can be 32 bit or 64 bit
 */
static bool ReadElfHeader(const char* file_path, Elf_Ehdr *header, uint8_t elf_class)
{
    return GetFileData(file_path, (unsigned char*)header,
            elf_class == ELFCLASS32 ? sizeof(Elf32_Ehdr) : sizeof(Elf64_Ehdr));
}

static inline uint16_t ELF_GetType(Elf_Ehdr *header, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? header->header32.e_type : header->header64.e_type;
}

static inline long ELF_GetPHOffset(Elf_Ehdr *header, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? header->header32.e_phoff : header->header64.e_phoff;
}

static inline uint16_t ELF_GetPHEntSize(Elf_Ehdr *header, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? header->header32.e_phentsize : header->header64.e_phentsize;
}

static inline uint16_t ELF_GetPHEntNum(Elf_Ehdr *header, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? header->header32.e_phnum : header->header64.e_phnum;
}

static inline Elf_Phdr* ElfPH_GetEntry(uint8_t* phtable, int idx, uint8_t elf_class)
{
    if (elf_class == ELFCLASS32)
        return (Elf_Phdr*) &((Elf32_Phdr*)phtable)[idx];
    else
        return (Elf_Phdr*) &((Elf64_Phdr*)phtable)[idx];
}

static inline uint32_t ElfPHEnt_GetType(Elf_Phdr *phdr, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? phdr->phdr32.p_type : phdr->phdr64.p_type;
}

static inline long ElfPHEnt_GetOffset(Elf_Phdr *phdr, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? (long)phdr->phdr32.p_offset : (long)phdr->phdr64.p_offset;
}

static inline size_t ElfPHEnt_GetSize(Elf_Phdr *phdr, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? (size_t)phdr->phdr32.p_filesz : (size_t)phdr->phdr64.p_filesz;
}

static inline int ElfPHEnt_GetNum(size_t dynsize, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? (int)(dynsize / sizeof(Elf32_Dyn)) : (int)(dynsize / sizeof(Elf64_Dyn));
}

static inline Elf_Dyn* ElfDyn_GetEntry(uint8_t* dyntable, int idx, uint8_t elf_class)
{
    if (elf_class == ELFCLASS32)
        return (Elf_Dyn*) &((Elf32_Dyn*)dyntable)[idx];
    else
        return (Elf_Dyn*) &((Elf64_Dyn*)dyntable)[idx];
}

static inline long ElfDyn_GetTag(Elf_Dyn *dynhdr, uint8_t elf_class)
{
    return elf_class == ELFCLASS32 ? (long)dynhdr->dyn32.d_tag : (long)dynhdr->dyn64.d_tag;
}

/**
 * @brief Check whether the file is a dynamic library
 */
bool IsDynamicLibrary( const char* file_path )
{
    uint8_t elf_class = GetElfClass(file_path);
    if (elf_class != ELFCLASSNONE)
    {
        Elf_Ehdr header;
        if(ReadElfHeader(file_path, &header, elf_class))
            return ELF_GetType(&header, elf_class) == ET_DYN;
    }

    return false;
}

/**
 * @brief Check whether the file is a executable file
 */
bool IsElfExe( const char* file_path )
{
    uint8_t elf_class = GetElfClass(file_path);
    if (elf_class != ELFCLASSNONE)
    {
        Elf_Ehdr header;
        if(ReadElfHeader(file_path, &header, elf_class))
        {
            uint16_t type = ELF_GetType(&header, elf_class);
            if (type == ET_EXEC || (type == ET_DYN && strstr(file_path, "/system/bin/")))
                return true;
        }
    }

    return false;
}

/**
 * @brief Check whether the module is PIC
 */
bool IsPICDynamicModule(const char* file_path)
{
    uint8_t elf_class = GetElfClass(file_path);
    if (elf_class == ELFCLASSNONE)
        return false;

    Elf_Ehdr header;
    if(!ReadElfHeader(file_path, &header, elf_class))
        return false;

    /* Check the file is a dynamic file */
    if (ELF_GetType(&header, elf_class) != ET_DYN)
        return false;

    /* Read the program header table.*/
    long phoff = ELF_GetPHOffset(&header, elf_class);
    uint16_t phentsize = ELF_GetPHEntSize(&header, elf_class);
    uint16_t phnum = ELF_GetPHEntNum(&header, elf_class);
    uint32_t phtable_size = ((uint32_t)phnum) * ((uint32_t)phentsize);
    uint8_t* phtable = ReadFileRegionData(file_path, phoff, phtable_size);
    if (!phtable)
        return false;

    bool bPIC = false;
    uint8_t* dyntable = NULL;
    int dynentnum;
    long dynoff = 0;
    size_t dynsize;
    int i;

    /* Find DYNAMIC section*/
    for (i = 0; (uint16_t)i < phnum; i++)
    {
        Elf_Phdr* phdr = ElfPH_GetEntry(phtable, i, elf_class);
        if (ElfPHEnt_GetType(phdr, elf_class) == PT_DYNAMIC)
        {
            dynoff = ElfPHEnt_GetOffset(phdr, elf_class);
            dynsize = ElfPHEnt_GetSize(phdr, elf_class);
            break;
        }
    }
    if (dynoff == 0)/* DYNAMIC section not found */
        goto cleanup_and_exit;

    /*Read the DYNAMIC section.*/
    dyntable = ReadFileRegionData(file_path, dynoff, dynsize);
    if (!dyntable)
        goto cleanup_and_exit;

    dynentnum = ElfPHEnt_GetNum(dynsize, elf_class);
    bPIC = true;

    /* Walk all entry and check whether TEXTREL not exists. */
    for (i = 0; i < dynentnum; i++)
    {
        Elf_Dyn* dynhdr = ElfDyn_GetEntry(dyntable, i, elf_class);
        if (ElfDyn_GetTag(dynhdr, elf_class) == DT_TEXTREL)
        {
            bPIC = false;
            break;
        }
    }

cleanup_and_exit:
    if (phtable)
        free(phtable);
    if (dyntable)
        free(dyntable);

    return bPIC;
}


/**
 * @brief Remove the trailing newlines, spaces and tabs character
 */
void TrimString( char* data )
{
    int n = strlen( data );
    for( int i = n-1; i >= 0; i-- )
    {
        char c = data[ i ];
        if( c == '\n' || c == 0x20 || c == '\t' )
        {
            data[ i ] = '\0';
        }
        else
        {
            break;
        }
    }
}

/**
 * @brief Get Get the file extension
 */
bool GetFileExtension( const char* file_name, char* extension, const int extension_max_size )
{
    const char* pDot = strrchr( file_name, '.' );
    if( pDot )
    {
        pDot++;
        if( *pDot == '\0' )return false;
        strncpy( extension, pDot, extension_max_size );
        return true;
    }

    return false;
}

/**
 * @brief count the maped page number in one bitmap
 */
int GetBitmapSetNum( unsigned char* bitmap, int totalBits )
{
    int n = 0;
    for( int i = 0; i < totalBits; i++ )
    {
        if( IsBitSet( bitmap, i ))
        {
            n++;
        }
    }
    return n;
}



