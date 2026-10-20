cmake_minimum_required(VERSION 3.22.1)
project("rvc_native")

include(FetchContent)
set(FETCHCONTENT_QUIET OFF)
FetchContent_Declare(oboe GIT_REPOSITORY https://github.com/google/oboe.git GIT_TAG 1.8.0 GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(oboe)

# Add onnxruntime as an imported library from local .so
add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libonnxruntime.so
)

add_library(rvc_native SHARED
    rvc_jni_bridge.cpp
    NativeLog.cpp
    AudioEngine.cpp
    InferenceEngine.cpp
    MasteringChain.cpp
    DSPUtils.cpp
    TrackMixer.cpp
)

target_compile_options(rvc_native PRIVATE
    -O3
    -flto=thin
    -fvisibility=hidden
)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(rvc_native PRIVATE -march=armv8-a+fp+simd -mtune=cortex-a76)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(rvc_native PRIVATE -march=armv7-a -mfpu=neon)
endif()
target_include_directories(rvc_native PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/onnxruntime_include
)
target_link_libraries(rvc_native oboe log android z onnxruntime)

# --- Host-side unit tests (opt-in, NOT compiled into the APK) ---
# Enable with: cmake -DRVC_BUILD_TESTS=ON
if(RVC_BUILD_TESTS)
    add_executable(track_mixer_pan_test
        ${CMAKE_CURRENT_SOURCE_DIR}/../../test/cpp/track_mixer_pan_test.cpp
        TrackMixer.cpp
    )
    target_include_directories(track_mixer_pan_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../../test/cpp   # android/log.h stub FIRST
        ${CMAKE_CURRENT_SOURCE_DIR}
    )
    target_compile_options(track_mixer_pan_test PRIVATE -std=c++17)
endif()

