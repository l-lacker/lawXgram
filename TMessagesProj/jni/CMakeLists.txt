cmake_minimum_required(VERSION 3.16)
project(lawXgramNative LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -x assembler-with-cpp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -w -DANDROID -D_LARGEFILE_SOURCE=1 -Wno-error=implicit-function-declaration -ffunction-sections -fdata-sections -fvisibility=hidden -flto=full")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DANDROID -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden -flto=full")
add_compile_options("-Wno-builtin-macro-redefined" "-D__FILE__=__FILE_NAME__")
# exports.map hides every non-JNI symbol, so no per-archive --exclude-libs list.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -flto=full -Wl,--gc-sections -Wl,--icf=safe -Wl,-Bsymbolic")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
    enable_language(ASM)
else()
    # nasm only mattered for third-party asm we now consume as prebuilt archives,
    # so configuring must not fail on hosts without it.
    include(CheckLanguage)
    check_language(ASM_NASM)
    if (CMAKE_ASM_NASM_COMPILER)
        enable_language(ASM_NASM)
    endif()
endif()

set(CAN_COMPILE_SME FALSE CACHE INTERNAL "Disable libyuv SME" FORCE)
add_subdirectory(
        third_party/libyuv
        ${CMAKE_CURRENT_BINARY_DIR}/libyuv
        EXCLUDE_FROM_ALL)

add_library(avutil STATIC IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libavutil.a)

add_library(avformat STATIC IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libavformat.a)

add_library(avcodec STATIC IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libavcodec.a)

add_library(swresample STATIC IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libswresample.a)

add_library(swscale STATIC IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libswscale.a)

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/boringssl/lib/${ANDROID_ABI}/libcrypto.a)

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/boringssl/lib/${ANDROID_ABI}/libssl.a)

add_library(libvpx STATIC IMPORTED)
set_target_properties(libvpx PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libvpx.a)

add_library(libdav1d STATIC IMPORTED)
set_target_properties(libdav1d PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libdav1d.a)

add_library(libopus STATIC IMPORTED)
set_target_properties(libopus PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/${ANDROID_ABI}/libopus.a)

add_library(tde2e STATIC IMPORTED)
set_target_properties(tde2e PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/tde2e/${ANDROID_ABI}/libtde2e.a)

add_library(tdutils STATIC IMPORTED)
set_target_properties(tdutils PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/tde2e/${ANDROID_ABI}/libtdutils.a)

#tgnet
add_library(tgnet STATIC
        tgnet/ApiScheme.cpp
        tgnet/BuffersStorage.cpp
        tgnet/ByteArray.cpp
        tgnet/ByteStream.cpp
        tgnet/Connection.cpp
        tgnet/ConnectionSession.cpp
        tgnet/ConnectionsManager.cpp
        tgnet/ConnectionSocket.cpp
        tgnet/Datacenter.cpp
        tgnet/EventObject.cpp
        tgnet/FileLog.cpp
        tgnet/MTProtoScheme.cpp
        tgnet/NativeByteBuffer.cpp
        tgnet/Request.cpp
        tgnet/Timer.cpp
        tgnet/TLObject.cpp
        tgnet/ProxyCheckInfo.cpp
        tgnet/Handshake.cpp
        tgnet/Config.cpp)
target_compile_options(tgnet PUBLIC
        -Wall -frtti -finline-functions -ffast-math -Oz)
set_target_properties(tgnet PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(tgnet PUBLIC
        HAVE_PTHREAD)
target_include_directories(tgnet PUBLIC
        boringssl/include/)
target_link_libraries(tgnet
        crypto)

# tlottie (prebuilt Rust static library)
add_library(tlottie STATIC IMPORTED)
set_target_properties(tlottie PROPERTIES
        IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/${ANDROID_ABI}/libtlottie.a)

#sqlite
add_library(sqlite STATIC
        sqlite/sqlite3.c)
target_compile_options(sqlite PUBLIC
        -fno-strict-aliasing -Oz)
set_target_properties(sqlite PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(sqlite PUBLIC
        NULL=0 SOCKLEN_T=socklen_t LOCALE_NOT_USED ANDROID_NDK DISABLE_IMPORTGL AVOID_TABLES ANDROID_TILE_BASED_DECODE HAVE_STRCHRNUL=0 ANDROID_ARMV6_IDCT)


#voip
include(${CMAKE_HOME_DIRECTORY}/voip/CMakeLists.txt)
include(${CMAKE_HOME_DIRECTORY}/colorado/CMakeLists.txt)

set(NATIVE_LIB "neko.49")

#tmessages
add_library(${NATIVE_LIB} SHARED
        jni.c
        audio.c
        webm_encoder.c
        image.cpp
        intro/IntroRenderer.c
        SqliteWrapper.cpp
        utilities.cpp
        gifvideo.cpp
        lottie.cpp
        TgNetWrapper.cpp
        exoplayer/ffmpeg_jni.cc
        fast-edge.cpp
        genann.c
        secureid_ocr.cpp
        tde2e/bridge.cpp
)

target_compile_options(${NATIVE_LIB} PUBLIC
        -ffast-math -Oz -funroll-loops -ffast-math -fno-strict-aliasing -fno-math-errno)

target_compile_definitions(${NATIVE_LIB} PUBLIC
        BSD=1 NULL=0 SOCKLEN_T=socklen_t ANDROID_NDK DISABLE_IMPORTGL AVOID_TABLES ANDROID_TILE_BASED_DECODE __STDC_CONSTANT_MACROS ANDROID_ARMV6_IDCT OPUS_BUILD FIXED_POINT USE_ALLOCA restrict= __EMX__ LOCALE_NOT_USED HAVE_LRINT HAVE_LRINTF)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_definitions(${NATIVE_LIB} PRIVATE
            COLORADO_ENFORCE_SIGNATURE)
endif()

target_sources(${NATIVE_LIB} PRIVATE

        third_party/xiph/opusfile/src/info.c
        third_party/xiph/opusfile/src/internal.c
        third_party/xiph/opusfile/src/opusfile.c
        third_party/xiph/opusfile/src/stream.c
        third_party/xiph/ogg/src/bitwise.c
        third_party/xiph/ogg/src/framing.c)

target_include_directories(${NATIVE_LIB} PUBLIC
        third_party/xiph/opus/include
        third_party/xiph/opus/silk
        third_party/xiph/opus/silk/fixed
        third_party/xiph/opus/celt
        third_party/xiph/opus
        third_party/xiph/opusfile/include
        third_party/xiph/ogg/include
        third_party/libyuv/include
        boringssl/include
        ffmpeg/include
        emoji
        exoplayer/include
        intro
        tlottie/include
        tgcalls/
        webrtc/
        tde2e/include
)

target_link_options(${NATIVE_LIB} PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(${NATIVE_LIB} PRIVATE "-Wl,-z,common-page-size=16384")
target_link_options(${NATIVE_LIB} PRIVATE "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map")
target_link_libraries(${NATIVE_LIB}
        -Wl,--whole-archive rnnoise openh264 voipandroid -Wl,--no-whole-archive
        tgvoip
        tgcalls
        tgcalls_tp
        tgnet
        tlottie
        sqlite
        swscale
        avformat
        avcodec
        swresample
        libvpx
        libdav1d
        libopus
        tde2e
        tdutils
        yuv
        avutil
        ssl
        crypto
        jnigraphics
        log
        z
        GLESv2
        EGL
        android
        OpenSLES
        colorado
        cpufeatures)

#if (${ANDROID_ABI} STREQUAL "x86" OR ${ANDROID_ABI} STREQUAL "x86_64")
#    target_link_libraries(${NATIVE_LIB}
#            -Wl,--whole-archive vpxasm -Wl,--no-whole-archive
#            c)
#endif()

if (ANDROID AND NOT TARGET cpufeatures)
    set(CPUFEATURES_DIR "${CMAKE_ANDROID_NDK}/sources/android/cpufeatures")
    if (NOT EXISTS "${CPUFEATURES_DIR}/cpu-features.c" AND DEFINED ANDROID_NDK)
        set(CPUFEATURES_DIR "${ANDROID_NDK}/sources/android/cpufeatures")
    endif()

    include_directories("${CPUFEATURES_DIR}")
    add_library(cpufeatures STATIC "${CPUFEATURES_DIR}/cpu-features.c")
    target_link_libraries(cpufeatures PUBLIC dl)
endif()
