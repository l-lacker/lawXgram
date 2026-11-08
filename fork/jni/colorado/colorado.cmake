# Neogram APK signature check, included by TMessagesProj/jni/CMakeLists.txt (patch
# misc/signature-check) when the build passes the expected certificate, i.e. release builds with
# a release keystore. Compiled into the main library so the JNI entry point is always linked.
target_sources(${NATIVE_LIB} PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}/cert.cpp
        ${CMAKE_CURRENT_LIST_DIR}/colorado.cpp
        ${CMAKE_CURRENT_LIST_DIR}/utils.cpp)
set_source_files_properties(
        ${CMAKE_CURRENT_LIST_DIR}/cert.cpp
        ${CMAKE_CURRENT_LIST_DIR}/colorado.cpp
        ${CMAKE_CURRENT_LIST_DIR}/utils.cpp
        PROPERTIES COMPILE_OPTIONS "-Oz"
        COMPILE_DEFINITIONS "NEO_SIGNATURE_CRC=${NEO_SIGNATURE_CRC};NEO_SIGNATURE_SIZE=${NEO_SIGNATURE_SIZE};NEO_SIGNATURE_PACKAGE=\"${NEO_SIGNATURE_PACKAGE}\"")
