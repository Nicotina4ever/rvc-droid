#include "NativeLog.h"

#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>

#define NLOG_TAG "NativeLog"

static std::string gLogPath;
static std::mutex gLogMutex;

void nativeLogSetPath(const char* path) {
    std::lock_guard<std::mutex> lock(gLogMutex);
    gLogPath = path ? path : "";
}

static void writeFile(const char* path, const std::string& line) {
    if (!path || !*path) return;
    FILE* f = fopen(path, "ab");
    if (!f) return;
    fwrite(line.data(), 1, line.size(), f);
    fclose(f);
}

void logFile(const char* fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    time_t now = time(nullptr);
    struct tm tmv;
    localtime_r(&now, &tmv);
    char ts[32];
    strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", &tmv);

    std::lock_guard<std::mutex> lock(gLogMutex);
    if (gLogPath.empty()) {
        __android_log_print(ANDROID_LOG_INFO, NLOG_TAG, "%s", buf);
        return;
    }
    std::string line = std::string("[") + ts + "] " + buf + "\n";
    writeFile(gLogPath.c_str(), line);
    __android_log_print(ANDROID_LOG_INFO, NLOG_TAG, "%s", buf);
}

static void crashHandler(int sig, siginfo_t* si, void* uc) {
    char buf[256];
    const char* name = "?";
    switch (sig) {
        case SIGSEGV: name = "SIGSEGV"; break;
        case SIGABRT: name = "SIGABRT"; break;
        case SIGBUS:  name = "SIGBUS";  break;
        case SIGILL:  name = "SIGILL";  break;
        case SIGFPE:  name = "SIGFPE";  break;
        default: break;
    }
    uintptr_t pc = 0;
#if defined(__aarch64__)
    if (uc) {
        ucontext_t* ctx = (ucontext_t*)uc;
        pc = ctx->uc_mcontext.pc;
    }
#elif defined(__arm__)
    if (uc) {
        ucontext_t* ctx = (ucontext_t*)uc;
        pc = ctx->uc_mcontext.arm_pc;
    }
#endif
    snprintf(buf, sizeof(buf), "NATIVE CRASH: %s at pc=0x%llx addr=%p", name, (unsigned long long)pc,
             si && si->si_addr ? si->si_addr : nullptr);
    logFile("%s", buf);

    // restore default handler and die for real
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = SIG_DFL;
    sigaction(sig, &sa, nullptr);
    raise(sig);
}

void installCrashHandlers() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crashHandler;
    sa.sa_flags = SA_SIGINFO;
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGABRT, &sa, nullptr);
    sigaction(SIGBUS, &sa, nullptr);
    sigaction(SIGILL, &sa, nullptr);
    sigaction(SIGFPE, &sa, nullptr);
    logFile("crash handlers installed");
}