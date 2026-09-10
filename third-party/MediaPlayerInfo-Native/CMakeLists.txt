cmake_minimum_required(VERSION 3.24)

project(MediaPlayerInfoNative LANGUAGES CXX)

# Указываем версию Windows SDK (добавь это)
set(CMAKE_SYSTEM_VERSION 10.0.22621.0)
set(CMAKE_VS_WINDOWS_TARGET_PLATFORM_VERSION 10.0.22621.0)

if(NOT WIN32)
    message(FATAL_ERROR "MediaPlayerInfo-Native is Windows-only.")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "JAVA_HOME must point to a JDK (not a JRE).")
endif()

set(JAVA_HOME "$ENV{JAVA_HOME}")
set(JNI_INCLUDE_DIR "${JAVA_HOME}/include")
set(JNI_PLATFORM_INCLUDE_DIR "${JAVA_HOME}/include/win32")

if(NOT EXISTS "${JNI_INCLUDE_DIR}/jni.h")
    message(FATAL_ERROR "jni.h was not found under JAVA_HOME: ${JNI_INCLUDE_DIR}")
endif()

add_library(MediaPlayerInfo SHARED
    src/MediaPlayerInfo.cpp
    src/MediaSessionWrapper.cpp
    src/dllmain.cpp
)

target_include_directories(MediaPlayerInfo PRIVATE
    "${JNI_INCLUDE_DIR}"
    "${JNI_PLATFORM_INCLUDE_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}/src"
)

target_compile_definitions(MediaPlayerInfo PRIVATE
    WIN32_LEAN_AND_MEAN
    NOMINMAX
    UNICODE
    _UNICODE
    # Добавь это для доступа к новым API
    _WIN32_WINNT=0x0A00
    WINVER=0x0A00
)

target_compile_options(MediaPlayerInfo PRIVATE
    /W4
    /EHsc
)

target_link_libraries(MediaPlayerInfo PRIVATE
    windowsapp
    ole32
)

set_target_properties(MediaPlayerInfo PROPERTIES
    OUTPUT_NAME "MediaPlayerInfo"
    PREFIX ""
    SUFFIX ".dll"
    WINDOWS_EXPORT_ALL_SYMBOLS OFF
)