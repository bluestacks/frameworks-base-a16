#ifndef _UTIL_H_H
#define _UTIL_H_H

#define MAX_PATH    (260)
#define _countof(a) (sizeof(a)/sizeof(a[0]))

/**
 * @brief Read the data of one file
 */
bool GetFileData( const char* file_path, unsigned char* data_buffer, size_t buffer_size );

/**
 * @brief Check whether the file is a dynamic library
 */
bool IsDynamicLibrary( const char* file_path );

/**
 * @brief Check whether the file is a executable file
 */
bool IsElfExe( const char* file_path );

/**
 * @brief Check whether the module is PIC
 */
bool IsPICDynamicModule( const char* file_path );

/**
 * @brief Remove the trailing newlines, spaces and tabs character
 */
void TrimString( char* data );

/**
 * @brief Get the file extension
 */
bool GetFileExtension( const char* file_name, char* extension, const int extension_max_size );

/**
 * @brief Set one bit
 */
static inline void SetBit( unsigned char* data, int idxBit )
{
    int n = idxBit/8;
    int m = idxBit%8;
    data[n] = data[n] | (1<<m);
}

/**
 * @brief Check whether one bit is set
 */
static inline bool IsBitSet( unsigned char* data, int idxBit )
{
    int n = idxBit/8;
    int m = idxBit%8;
    return !!( data[n] & (1<<m) );
}

/**
 * @brief count the maped page number in one bitmap
 */
int GetBitmapSetNum( unsigned char* bitmap, int totalBits );

/**
 * @brief Tests if this string starts with the specified prefix.
 */
static inline bool StartsWith( const char* str, const char* prefix )
{
    return strncmp( str, prefix, strlen(prefix)) == 0;
}

/**
 * @brief Tests if this string ends with the specified suffix.
 */
static inline bool EndsWith( const char* str, const char* suffix )
{
    int n = strlen( suffix );
    int m = strlen( str );
    if( m < n )return false;

    return strncmp( &str[m-n], suffix, n ) == 0;
}

#endif

