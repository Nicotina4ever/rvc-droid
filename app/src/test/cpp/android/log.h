// Minimal stub for host-side test build. NOT used in the Android build
// because the real header from the NDK is always available there.
#pragma once
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_ERROR 6
#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_DEBUG 3
#define ANDROID_LOG_VERBOSE 2
#define __android_log_print(level, tag, ...) ((void)0)
