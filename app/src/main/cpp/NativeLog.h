#pragma once

#include <csignal>

void nativeLogSetPath(const char* path);
void logFile(const char* fmt, ...);
void installCrashHandlers();