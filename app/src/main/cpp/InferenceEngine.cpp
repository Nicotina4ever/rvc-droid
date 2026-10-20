#include "InferenceEngine.h"
#include "NativeLog.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <sys/resource.h>

#include <onnxruntime/core/session/onnxruntime_cxx_api.h>

#define LOG_TAG "InferenceEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ===========================================================================
// Costanti verificate (VERIFICA_E2E.py + rvc_python, SOLO LETTURA):
//  - filtro: butter N=5 highpass 48Hz @16k
//  - hubert: output_layer 12, no final_proj (v2)
//  - synth:  rnd esplicito, wrapper gia' moltiplica per 0.66666
//  - rmvpe:  mel librosa htk=True (128 x 513), torch.stft center reflect,
//            decode a 9 bin con soglia 0.03
//  - faiss:  IVF-Flat L2 nprobe=1, top-8, weight=(1/score)^2 normalizzato
//  - protect: feats*pitchff + feats0*(1-pitchff), pitchff=pitchf>0?1:protect
// ===========================================================================

static constexpr double kButterB[6] = {0.96996065, -4.84980323, 9.69960645, -9.69960645, 4.84980323, -0.96996065};
static constexpr double kButterA[6] = {1.0, -4.93900182, 9.75786353, -9.63954485, 4.7615068, -0.94082365};

// memoria di processo in MB (RSS via getrusage; utile per capire se il LMK
// sta per killaare il processo). Lettura leggera, chiamabile nei log.
static std::string memInfo() {
    struct rusage ru;
    if (getrusage(RUSAGE_SELF, &ru) == 0) {
        long mb = (long)(ru.ru_maxrss / 1024);
        return "rss=" + std::to_string(mb) + "MB";
    }
    return "mem=?";
}

// --- piccolo PRNG (Box-Muller su xorshift64*) per il rnd del synth ---
class Rng {
public:
    Rng() {
        uint64_t s = (uint64_t)(std::chrono::steady_clock::now().time_since_epoch().count());
        mState = s ? s : 0x9E3779B97F4A7C15ull;
    }
    double nextUnit() {
        uint64_t x = mState;
        x ^= x >> 12; x ^= x << 25; x ^= x >> 27;
        mState = x;
        return (double)(x * 0x2545F4914F6CDD1Dull >> 11) / 9007199254740992.0;
    }
    float nextGauss() {
        double u1 = std::max(nextUnit(), 1e-12), u2 = nextUnit();
        return (float)(std::sqrt(-2.0 * std::log(u1)) * std::cos(2.0 * 3.14159265358979 * u2));
    }
private:
    uint64_t mState;
};

// ===========================================================================
// FFT radix-2 (iterativo, double) per la mel a 1024 punti
// ===========================================================================
class Fft1024 {
public:
    Fft1024() {
        const int n = 1024;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; j & bit; bit >>= 1) j ^= bit;
            j ^= bit;
            mRev[i] = j;
        }
    }
    void forward(const double* in, double* re, double* im) {
        const int n = 1024;
        for (int i = 0; i < n; i++) { re[i] = in[mRev[i]]; im[i] = 0.0; }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * 3.141592653589793 / len;
            double wr = std::cos(ang), wi = std::sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1.0, ci = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double tr = cr * re[b] - ci * im[b];
                    double ti = cr * im[b] + ci * re[b];
                    re[b] = re[a] - tr; im[b] = im[a] - ti;
                    re[a] += tr; im[a] += ti;
                    double ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }
private:
    int mRev[1024];
};

// ===========================================================================
// Resampler FIR 48k -> 16k (decimazione x3 con finestra sinc-Kaiser)
// ===========================================================================
static std::vector<float> gFirKernel;
static void ensureFirKernel() {
    if (!gFirKernel.empty()) return;
    const int taps = 128;
    const double fc = 7200.0 / 48000.0;   // cutoff normalizzato
    const double beta = 10.0;
    gFirKernel.resize(taps);
    double sum = 0.0;
    const double mid = (taps - 1) / 2.0;
    for (int i = 0; i < taps; i++) {
        double x = i - mid;
        double sinc = (x == 0.0) ? 1.0 : std::sin(2.0 * 3.14159265358979 * fc * x) / (2.0 * 3.14159265358979 * fc * x);
        double z = 2.0 * (i / (double)(taps - 1)) - 1.0;
        double kaiser = (std::abs(z) < 1.0) ?
            std::sinh(beta * std::sqrt(1.0 - z * z)) / (std::sinh(beta) + 1e-12) : 1e-12;
        gFirKernel[i] = (float)(2.0 * fc * sinc * kaiser);
        sum += gFirKernel[i];
    }
    for (auto& h : gFirKernel) h = (float)(h / sum);
}

// ===========================================================================
// InferenceEngine
// ===========================================================================

InferenceEngine::InferenceEngine() : mEnv(nullptr) {
    ensureFirKernel();
    LOGI("InferenceEngine created (synth/hubert onnx, rmvpe onnx, faiss fbin)");
    mStopLive = false;
    mWorker = std::thread(&InferenceEngine::workerLoop, this);
    mWorkerRunning = true;
}

InferenceEngine::~InferenceEngine() { release(); }

std::string InferenceEngine::getSynthModelName() const { return mSynthModelName; }
std::string InferenceEngine::getVecModelName() const { return mVecModelName; }
std::string InferenceEngine::getIndexName() const { return mIndexName; }

// ---------------------------------------------------------------------------
// Caricamento modelli
// ---------------------------------------------------------------------------

bool InferenceEngine::loadSynthesizerModel(const std::string& path) {
    LOGI("Loading synth .onnx: %s", path.c_str());
    std::lock_guard<std::mutex> lock(mMutex);
    try {
        if (!mEnv) mEnv = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "RVC-Synth");
        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(4);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        auto session = std::make_unique<Ort::Session>(*mEnv, path.c_str(), opts);
        if (session->GetInputCount() < 6 || session->GetOutputCount() < 1) {
            throw std::runtime_error("synth ONNX must expose 6 inputs and 1 output");
        }
        mSynth = std::move(session);
        mSynthLoaded = true;
        mSynthPath = path;
        mSynthModelName = path.substr(path.find_last_of("/\\") + 1);
        LOGI("Synth .onnx loaded: %s", mSynthModelName.c_str());
        logFile("synth loaded: %s", mSynthModelName.c_str());
        return true;
    } catch (const std::exception& e) {
        LOGE("Failed to load synth .onnx: %s", e.what());
        logFile("synth LOAD FAILED: %s", e.what());
        mSynth.reset();
        mSynthLoaded = false;
        return false;
    }
}

bool InferenceEngine::loadContentVecModel(const std::string& path) {
    LOGI("Loading hubert .onnx: %s", path.c_str());
    std::lock_guard<std::mutex> lock(mMutex);
    try {
        if (!mEnv) mEnv = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "RVC-Hubert");
        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(4);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        auto session = std::make_unique<Ort::Session>(*mEnv, path.c_str(), opts);
        if (session->GetInputCount() < 1 || session->GetOutputCount() < 1) {
            throw std::runtime_error("HuBERT ONNX must expose an input and an output");
        }
        mHubert = std::move(session);
        mVecLoaded = true;
        mVecPath = path;
        mVecModelName = path.substr(path.find_last_of("/\\") + 1);
        LOGI("Hubert .onnx loaded: %s", mVecModelName.c_str());
        logFile("hubert loaded: %s", mVecModelName.c_str());
        return true;
    } catch (const std::exception& e) {
        LOGE("Failed to load hubert .onnx: %s", e.what());
        logFile("hubert LOAD FAILED: %s", e.what());
        mHubert.reset();
        mVecLoaded = false;
        return false;
    }
}

bool InferenceEngine::loadF0Model(const std::string& path) {
    LOGI("Loading rmvpe.onnx: %s", path.c_str());
    std::lock_guard<std::mutex> lock(mMutex);
    try {
        if (!mEnv) mEnv = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "RVC-RMVPE");
        // matrice mel librosa (htk=True) accanto al modello
        std::string melPath = path;
        size_t slash = melPath.find_last_of("/\\");
        if (slash != std::string::npos) melPath = melPath.substr(0, slash + 1);
        melPath += "rmvpe_melfb.bin";
        FILE* fm = fopen(melPath.c_str(), "rb");
        if (!fm) {
            // Fallback paths (app internal / external storage)
            const char* fallbacks[] = {
                "/data/data/com.rvc.droid/files/rmvpe_melfb.bin",
                "/data/user/0/com.rvc.droid/files/rmvpe_melfb.bin",
                "/storage/emulated/0/Android/data/com.rvc.droid/files/models/rmvpe_melfb.bin",
                "/storage/emulated/0/Android/data/com.rvc.droid/files/rmvpe_melfb.bin",
                "/storage/emulated/0/Download/Modelli/rmvpe_melfb.bin",
                "/sdcard/Download/Modelli/rmvpe_melfb.bin",
                nullptr
            };
            for (int i = 0; fallbacks[i] != nullptr; i++) {
                fm = fopen(fallbacks[i], "rb");
                if (fm) {
                    LOGI("Found rmvpe_melfb.bin at fallback: %s", fallbacks[i]);
                    logFile("f0: found mel basis at fallback: %s", fallbacks[i]);
                    melPath = fallbacks[i];
                    break;
                }
            }
        }
        if (!fm) { LOGE("Cannot open mel basis: %s", melPath.c_str());
            logFile("f0 FAILED: no mel basis at %s (and no fallbacks)", melPath.c_str());
            return false; }
        fseek(fm, 0, SEEK_END);
        long melLen = ftell(fm);
        fseek(fm, 0, SEEK_SET);
        if (melLen != (long)(128 * 513 * 4)) { fclose(fm); LOGE("Bad mel basis size");
            logFile("f0 FAILED: mel basis size %ld != 262656", melLen);
            return false; }
        mMelBasis.resize(128 * 513);
        if (fread(mMelBasis.data(), 1, (size_t)melLen, fm) != (size_t)melLen) { fclose(fm);
            logFile("f0 FAILED: mel basis read short");
            return false; }
        fclose(fm);

        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(4);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        opts.EnableCpuMemArena();
        delete mRmvpeSession;
        mRmvpeSession = new Ort::Session(*mEnv, path.c_str(), opts);
        mF0Loaded = true;
        mF0Path = path;
        LOGI("rmvpe.onnx loaded");
        logFile("f0 loaded OK: %s", path.c_str());
        return true;
    } catch (const Ort::Exception& e) {
        LOGE("Failed to load rmvpe.onnx: %s", e.what());
        logFile("f0 LOAD FAILED (ort): %s", e.what());
        mF0Loaded = false;
        return false;
    }
}

bool InferenceEngine::loadIndex(const std::string& path) {
    LOGI("Loading faiss .fbin: %s", path.c_str());
    std::lock_guard<std::mutex> lock(mMutex);
    bool ok = loadFbin(path);
    if (ok) {
        mIndexLoaded = true;
        mIndexName = path.substr(path.find_last_of("/\\") + 1);
        LOGI("Index .fbin loaded: %s (d=%d, ntotal=%lld, nlist=%d, nprobe=%d, metric=%d)",
             mIndexName.c_str(), mIndexDim, (long long)mIndexNtotal, mIndexNlist, mIndexNprobe, mIndexMetric);
    } else {
        LOGE("Failed to load index: %s", path.c_str());
        mIndexLoaded = false;
        mIndexName.clear();
        mIndexDim = 0; mIndexNlist = 0; mIndexNtotal = 0;
        mIndexCentroids.clear(); mIndexLists.clear();
        mIndexVecCacheIds.clear(); mIndexVecCacheData.clear();
        if (mIndexFbinFile) { fclose(mIndexFbinFile); mIndexFbinFile = nullptr; }
    }
    return ok;
}

bool InferenceEngine::loadFbin(const std::string& path) {
    if (mIndexFbinFile) { fclose(mIndexFbinFile); mIndexFbinFile = nullptr; }
    mIndexVecCacheIds.clear();
    mIndexVecCacheData.clear();

    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { LOGE("Cannot open .fbin: %s", path.c_str()); return false; }
    char magic[4] = {0};
    if (fread(magic, 1, 4, f) != 4 || memcmp(magic, "RVCX", 4) != 0) { fclose(f); LOGE("Bad .fbin magic"); return false; }
    int32_t d = 0, nlist = 0, nprobe = 1, metric = 1, reserved = 0;
    int64_t ntotal = 0;
    auto read = [&](void* p, size_t n) { return fread(p, 1, n, f) == n; };
    if (!read(&d, 4) || !read(&ntotal, 8) || !read(&nlist, 4) || !read(&nprobe, 4) ||
        !read(&metric, 4) || !read(&reserved, 4)) { fclose(f); return false; }
    if (d <= 0 || ntotal <= 0 || nlist <= 0) { fclose(f); LOGE("Bad .fbin header"); return false; }
    mIndexDim = d; mIndexNtotal = ntotal; mIndexNlist = nlist; mIndexNprobe = std::max(1, (int)nprobe); mIndexMetric = metric;
    mIndexCentroids.resize((size_t)nlist * d);
    if (!read(mIndexCentroids.data(), (size_t)nlist * d * 4)) { fclose(f); return false; }
    // Skip big_npy: lo leggiamo on-demand. Salvo l'offset per le seek successive.
    long long bigNpyBytes = (long long)ntotal * d * 4;
    if (fseek(f, bigNpyBytes, SEEK_CUR) != 0) { fclose(f); return false; }
    mIndexBigNpyOffset = ftell(f);
    if (mIndexBigNpyOffset < 0) { fclose(f); return false; }
    mIndexLists.resize(nlist);
    for (int li = 0; li < nlist; li++) {
        int64_t count = 0;
        if (!read(&count, 8) || count < 0 || count > 10000000) { fclose(f); return false; }
        mIndexLists[li].resize((size_t)count);
        if (count && !read(mIndexLists[li].data(), (size_t)count * 8)) { fclose(f); return false; }
    }
    mIndexFbinFile = f;  // teniamo aperto per le query
    LOGI("Index loaded (file-backed): d=%d ntotal=%lld nlist=%d nprobe=%d bigNpy=%.1fMB",
         d, (long long)ntotal, nlist, nprobe, bigNpyBytes / 1e6);
    return true;
}

// Restituisce il puntatore ai d float del vettore con id globale `gid`.
// Usa una piccola cache LRU per evitare seek ripetuti.
static const float* kDummy = nullptr;  // mai usato, placeholder silenzia warning
const float* InferenceEngine::indexGetVector(int64_t gid) {
    if (!mIndexFbinFile) return nullptr;
    const int32_t d = mIndexDim;
    // cerca in cache
    for (size_t i = 0; i < mIndexVecCacheIds.size(); i++) {
        if (mIndexVecCacheIds[i] == gid) {
            return mIndexVecCacheData.data() + i * d;
        }
    }
    // cache miss: leggi dal file
    long long off = mIndexBigNpyOffset + (long long)gid * d * 4;
#ifdef _WIN32
    if (_fseeki64(mIndexFbinFile, off, SEEK_SET) != 0) return nullptr;
#else
    if (fseek(mIndexFbinFile, off, SEEK_SET) != 0) return nullptr;
#endif
    if (mIndexVecCacheIds.size() >= (size_t)kIndexVecCache) {
        // cache piena: rimuovi il più vecchio (FIFO)
        mIndexVecCacheIds.erase(mIndexVecCacheIds.begin());
        mIndexVecCacheData.erase(mIndexVecCacheData.begin(),
                                  mIndexVecCacheData.begin() + d);
    }
    mIndexVecCacheIds.push_back(gid);
    mIndexVecCacheData.resize(mIndexVecCacheIds.size() * d);
    float* slot = mIndexVecCacheData.data() + (mIndexVecCacheIds.size() - 1) * d;
    if (fread(slot, 4, d, mIndexFbinFile) != (size_t)d) {
        // errore: rimuovi l'entry appena inserita
        mIndexVecCacheIds.pop_back();
        mIndexVecCacheData.resize(mIndexVecCacheData.size() - d);
        return nullptr;
    }
    return slot;
}

void InferenceEngine::release() {
    {
        std::unique_lock<std::mutex> lk(mQMutex);
        mStopLive = true;
        mCv.notify_all();
    }
    if (mWorker.joinable()) mWorker.join();
    mWorkerRunning = false;
    std::lock_guard<std::mutex> lock(mMutex);
    std::lock_guard<std::mutex> cl(mChainMutex);
    mHubert.reset();
    mSynth.reset();
    delete mRmvpeSession; mRmvpeSession = nullptr;
    delete mEnv; mEnv = nullptr;
    mSynthLoaded = false; mVecLoaded = false; mF0Loaded = false; mIndexLoaded = false;
    mIndexCentroids.clear(); mIndexLists.clear();
    mIndexVecCacheIds.clear(); mIndexVecCacheData.clear();
    if (mIndexFbinFile) { fclose(mIndexFbinFile); mIndexFbinFile = nullptr; }
    LOGI("Released");
}

// ---------------------------------------------------------------------------
// DSP helpers
// ---------------------------------------------------------------------------

void InferenceEngine::resampleTo16k(const float* in, int32_t inFrames, int32_t inRate,
                                    std::vector<float>& out16k) {
    if (inRate == 48000) {
        firDecimate3(in, inFrames, out16k);
    } else {
        resampleTo(in, inFrames, inRate, kTargetRate, out16k);
    }
}

void InferenceEngine::firDecimate3(const float* in, int32_t inFrames, std::vector<float>& out16k) {
    const int taps = (int)gFirKernel.size();
    const int half = taps / 2;
    if (inFrames <= 0) { out16k.clear(); return; }
    int32_t outFrames = inFrames / 3;   // floor (come load_audio/soundfile 48k->16k e' esatto)
    out16k.resize(outFrames);
    for (int32_t k = 0; k < outFrames; k++) {
        int32_t center = 3 * k;
        double acc = 0.0;
        for (int i = 0; i < taps; i++) {
            int32_t idx = center - half + i;
            float v = (idx < 0) ? in[0] : (idx >= inFrames ? in[inFrames - 1] : in[idx]);
            acc += gFirKernel[i] * v;
        }
        out16k[k] = (float)acc;
    }
}

void InferenceEngine::resampleTo(const float* in, int32_t inFrames, int32_t inRate,
                                 int32_t outRate, std::vector<float>& out) {
    if (inRate == outRate) { out.assign(in, in + inFrames); return; }
    double r = (double)outRate / (double)inRate;
    int32_t oFrames = (int32_t)(inFrames * r) + 1;
    out.resize(oFrames);
    for (int32_t i = 0; i < oFrames; i++) {
        double si = i / r;
        int32_t lo = (int32_t)si, hi = std::min(lo + 1, inFrames - 1);
        double f = si - lo;
        out[i] = (float)(in[lo] * (1.0 - f) + in[hi] * f);
    }
}

void InferenceEngine::butterFiltfilt(std::vector<float>& x) {
    // scipy.signal.filtfilt(bh, ah, x) con padlen = 3*max(6,6) = 18
    const int edge = 18;
    const int n = (int)x.size();
    if (n <= edge * 2) return;
    std::vector<double> buf(n + 2 * edge);
    for (int i = 0; i < edge; i++) buf[i] = 2.0 * x[0] - x[edge - 1 - i];
    for (int i = 0; i < n; i++) buf[edge + i] = x[i];
    for (int i = 0; i < edge; i++) buf[edge + n + i] = 2.0 * x[n - 1] - x[n - 2 - i];
    const int M = n + 2 * edge;
    // transposed direct form II (come scipy lfilter)
    double z[5] = {0, 0, 0, 0, 0};
    for (int i = 0; i < M; i++) {
        double xn = buf[i];
        double yn = kButterB[0] * xn + z[0];
        for (int k = 1; k < 5; k++) z[k - 1] = kButterB[k] * xn - kButterA[k] * yn + z[k];
        z[4] = kButterB[5] * xn - kButterA[5] * yn;
        buf[i] = yn;
    }
    // filtro all'indietro (lfilter su y[::-1])
    std::fill(z, z + 5, 0.0);
    for (int i = M - 1; i >= 0; i--) {
        double xn = buf[i];
        double yn = kButterB[0] * xn + z[0];
        for (int k = 1; k < 5; k++) z[k - 1] = kButterB[k] * xn - kButterA[k] * yn + z[k];
        z[4] = kButterB[5] * xn - kButterA[5] * yn;
        buf[i] = yn;
    }
    for (int i = 0; i < n; i++) x[i] = (float)buf[edge + i];
}

void InferenceEngine::reflectPad(const std::vector<float>& x, int32_t pad, std::vector<float>& out) {
    // np.pad(x, (pad,pad), mode='reflect'): mirror senza ripetere gli estremi
    int32_t n = (int32_t)x.size();
    out.resize(n + 2 * pad);
    for (int32_t i = 0; i < pad; i++) out[i] = x[pad - 1 - i];
    for (int32_t i = 0; i < n; i++) out[pad + i] = x[i];
    for (int32_t i = 0; i < pad; i++) out[pad + n + i] = x[n - 1 - i];
}
// ---------------------------------------------------------------------------
// Mel spectrogram (rmvpe) - replicato da rvc_python/lib/rmvpe.py
//   librosa.filters.mel(sr=16000, n_fft=1024, n_mels=128, fmin=30, fmax=8000, htk=True)
//   torch.stft(center=True, pad_mode=reflect, hann 1024, hop 160) -> mag
//   log(clamp(mel_basis @ mag, 1e-5))
// ---------------------------------------------------------------------------

void InferenceEngine::computeMel(const float* audio16k, int32_t frames, int32_t nFrames,
                                 std::vector<float>& melOut) {
    // pad reflect 512 per lato (come center=True)
    const int pad = mMelNfft / 2; // 512
    std::vector<float> padded(frames + 2 * pad);
    for (int i = 0; i < pad; i++) padded[i] = audio16k[pad - 1 - i];
    for (int i = 0; i < frames; i++) padded[pad + i] = audio16k[i];
    for (int i = 0; i < pad; i++) padded[pad + frames + i] = audio16k[frames - 1 - i];

    // finestra hann simmetrica (torch.hann_window periodic=False)
    static std::vector<double> hann;
    if (hann.empty()) {
        hann.resize(mMelNfft);
        for (int i = 0; i < mMelNfft; i++)
            hann[i] = 0.5 - 0.5 * std::cos(2.0 * 3.14159265358979 * i / (mMelNfft - 1));
    }

    melOut.resize((size_t)nFrames * mMelBins);
    static Fft1024 fft;
    std::vector<double> win(mMelNfft), re(mMelNfft), im(mMelNfft);
    std::vector<float> mag(mMelNfft / 2 + 1);

    for (int32_t t = 0; t < nFrames; t++) {
        int32_t start = t * mMelHop;
        for (int i = 0; i < mMelNfft; i++) win[i] = padded[start + i] * hann[i];
        fft.forward(win.data(), re.data(), im.data());
        for (int b = 0; b < mMelNfft / 2 + 1; b++)
            mag[b] = (float)std::sqrt(re[b] * re[b] + im[b] * im[b]);
        float* dst = melOut.data() + (size_t)t * mMelBins;
        for (int m = 0; m < mMelBins; m++) {
            float acc = 0.0f;
            const float* fb = mMelBasis.data() + (size_t)m * (mMelNfft / 2 + 1);
            for (int b = 0; b < mMelNfft / 2 + 1; b++) acc += fb[b] * mag[b];
            dst[m] = std::log(std::max(acc, 1e-5f));
        }
    }
}

// ---------------------------------------------------------------------------
// Decode F0 da salience (rmvpe.to_local_average_cents + decode)
// ---------------------------------------------------------------------------

void InferenceEngine::decodeF0(const float* hidden, int32_t nFrames, std::vector<float>& f0) {
    constexpr int k360 = 360;
    // cents_mapping = 20*arange(360) + 1997.3794084376191, pad (4,4) -> 368
    std::vector<double> cents(368);
    for (int i = 0; i < k360; i++) cents[4 + i] = 20.0 * i + 1997.3794084376191;
    for (int i = 0; i < 4; i++) { cents[i] = 0.0; cents[364 + i] = 0.0; }

    // salience pad (0,0),(4,4)
    std::vector<double> sal(nFrames * 368, 0.0);
    std::vector<int> center(nFrames);
    std::vector<double> maxx(nFrames);
    for (int t = 0; t < nFrames; t++) {
        const float* row = hidden + (size_t)t * k360;
        int best = 0; float bv = -1e30f;
        for (int i = 0; i < k360; i++) if (row[i] > bv) { bv = row[i]; best = i; }
        center[t] = best + 4;
        maxx[t] = bv;
        for (int i = 0; i < k360; i++) sal[(size_t)t * 368 + 4 + i] = row[i];
    }

    f0.resize(nFrames);
    for (int t = 0; t < nFrames; t++) {
        const double* win = sal.data() + (size_t)t * 368 + (center[t] - 4);
        double prod = 0.0, wsum = 0.0;
        for (int k = 0; k < 9; k++) {
            prod += win[k] * cents[center[t] - 4 + k];
            wsum += win[k];
        }
        double dev = (wsum > 0.0) ? prod / wsum : 0.0;
        if (maxx[t] <= 0.03) dev = 0.0;
        double f = 10.0 * std::pow(2.0, dev / 1200.0);
        f0[t] = (f == 10.0) ? 0.0f : (float)f;
    }
}

// ---------------------------------------------------------------------------
// F0 completo: mel -> rmvpe.onnx -> decode -> pitch/pitchf (get_f0 rvc_python)
// ---------------------------------------------------------------------------

void InferenceEngine::extractF0RMVPE(const float* audio16k, int32_t frames,
                                     int32_t shift, std::vector<int64_t>& pitch,
                                     std::vector<float>& pitchf) {
    pitch.clear(); pitchf.clear();
    const int32_t nMel = frames / mMelHop + 1;
    std::vector<float> mel;
    computeMel(audio16k, frames, nMel, mel);

    std::vector<float> f0;
    try {
        if (mRmvpeSession && mF0Loaded) {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> shape{1, mMelBins, nMel};
        Ort::Value in = Ort::Value::CreateTensor<float>(
            memoryInfo, mel.data(), (size_t)mMelBins * nMel, shape.data(), 3);
const char* inNames[1] = {"input"};
        Ort::AllocatorWithDefaultOptions alloc;
        std::vector<const char*> outNames;
        auto on = mRmvpeSession->GetOutputNameAllocated(0, alloc);
        outNames.push_back(on.get());
        auto res = mRmvpeSession->Run(Ort::RunOptions{nullptr}, inNames, &in, 1,
                                      outNames.data(), 1);
        if (!res.empty() && res[0].IsTensor()) {
            auto info = res[0].GetTensorTypeAndShapeInfo();
            auto outShape = info.GetShape();
            size_t n360 = 360;
            if (outShape.size() == 3) n360 = (size_t)outShape[2];
            size_t nT = (outShape.size() == 3) ? (size_t)outShape[1] : (info.GetElementCount() / n360);
            const float* data = res[0].GetTensorData<float>();
            decodeF0(data, (int32_t)std::min(nT, (size_t)nMel), f0);
f0.resize(std::min(nT, (size_t)nMel));
        }
    }
    } catch (const std::exception& e) {
        LOGW("rmvpe run failed: %s", e.what());
        logFile("rmvpe run FAILED: %s", e.what());
    }
    if ((int32_t)f0.size() < nMel) f0.resize(nMel, 0.0f);

    // f0_up_key / formant shift
    if (shift != 0) {
        double mul = std::pow(2.0, shift / 12.0);
        for (auto& v : f0) v = (float)(v * mul);
    }

    // get_f0: mappatura mel (f0_min=50, f0_max=1100)
    const double f0MelMin = 1127.0 * std::log(1.0 + 50.0 / 700.0);
    const double f0MelMax = 1127.0 * std::log(1.0 + 1100.0 / 700.0);
    pitch.clear(); pitchf.clear();
    for (size_t i = 0; i < f0.size(); i++) {
        double fm = 1127.0 * std::log(1.0 + f0[i] / 700.0);
        if (fm > 0.0) fm = (fm - f0MelMin) * 254.0 / (f0MelMax - f0MelMin) + 1.0;
        if (fm <= 1.0) fm = 1.0;
        if (fm > 255.0) fm = 255.0;
        pitch.push_back((int64_t)std::nearbyint(fm));
        pitchf.push_back(f0[i]);
    }
}

// ---------------------------------------------------------------------------
// FAISS IVF search (replica esatta faiss, validata in export_faiss_bin.py)
//   metrica L2 (score = distanza^2), nprobe liste, top-8 ascendente stabile
// ---------------------------------------------------------------------------

void InferenceEngine::indexSearch(const float* query,
                                  std::vector<int64_t>& ids, std::vector<float>& scores) {
    ids.clear(); scores.clear();
    if (!mIndexLoaded || mIndexNtotal <= 0) return;
    const int32_t d = mIndexDim;
    int32_t nprobe = std::min(mIndexNprobe, mIndexNlist);
    std::vector<int32_t> bestLists(nprobe);
    // assegnazione: distanza L2 (fp64) minima ai centroidi
    std::vector<double> cd(mIndexNlist);
    for (int c = 0; c < mIndexNlist; c++) {
        const float* cen = mIndexCentroids.data() + (size_t)c * d;
        double acc = 0.0;
        for (int j = 0; j < d; j++) { double dv = (double)query[j] - cen[j]; acc += dv * dv; }
        cd[c] = acc;
    }
    for (int k = 0; k < nprobe; k++) {
        int best = -1; double bv = 1e300;
        for (int c = 0; c < mIndexNlist; c++)
            if (cd[c] < bv) { bv = cd[c]; best = c; }
        bestLists[k] = best;
        cd[best] = 1e300;
    }
    // candidati dalle liste selezionate (in ordine di lista, stabile)
    std::vector<int64_t> candIds;
    std::vector<double> candD;
    candIds.reserve(8192); candD.reserve(8192);
    for (int k = 0; k < nprobe; k++) {
        const auto& list = mIndexLists[bestLists[k]];
        for (size_t i = 0; i < list.size(); i++) {
            int64_t gid = list[i];
            const float* v = indexGetVector(gid);
            if (!v) continue;
            double acc = 0.0;
            for (int j = 0; j < d; j++) { double dv = (double)query[j] - v[j]; acc += dv * dv; }
            candIds.push_back(gid);
            candD.push_back(acc);
        }
    }
    if (candIds.empty()) return;
    // top-8 ascendente (stabile: a parita'' vince il primo)
    const size_t k = std::min<size_t>(8, candIds.size());
    std::vector<size_t> idx(candIds.size());
    for (size_t i = 0; i < idx.size(); i++) idx[i] = i;
    auto cmp = [&](size_t a, size_t b) { return candD[a] < candD[b]; };
    std::partial_sort(idx.begin(), idx.begin() + k, idx.end(), cmp);
    for (size_t i = 0; i < k; i++) {
        ids.push_back(candIds[idx[i]]);
        scores.push_back((float)candD[idx[i]]);
    }
}

void InferenceEngine::blendWithIndex(std::vector<float>& feats, int32_t tLen) {
    if (!mIndexLoaded || mIndexNtotal <= 0 || mIndexRate <= 0.0f) return;
    if (mIndexDim != kFeatSize) {
        LOGW("blend: index dim %d != %d, skip", mIndexDim, kFeatSize);
        return;
    }
    const int32_t d = kFeatSize;
    std::vector<float> blended((size_t)tLen * d);
    std::vector<int64_t> ids;
    std::vector<float> scores;
    for (int32_t t = 0; t < tLen; t++) {
        const float* q = feats.data() + (size_t)t * d;
        float* dst = blended.data() + (size_t)t * d;
        indexSearch(q, ids, scores);
        if (ids.empty()) { memcpy(dst, q, sizeof(float) * d); continue; }
        // weight = (1/score)^2 normalizzato  (fp32, come numpy)
        size_t n = ids.size();
        std::vector<float> w(n);
        double wsum = 0.0;
        for (size_t i = 0; i < n; i++) {
            float wi = (scores[i] > 0.0f) ? 1.0f / (scores[i] * scores[i]) : 1e12f;
            w[i] = wi;
            wsum += wi;
        }
        if (wsum <= 0.0) { memcpy(dst, q, sizeof(float) * d); continue; }
        for (auto& ww : w) ww = (float)(ww / wsum);
        std::vector<double> acc(d, 0.0);
        for (size_t i = 0; i < n; i++) {
            const float* v = indexGetVector(ids[i]);
            if (!v) continue;
            double ww = w[i];
            for (int j = 0; j < d; j++) acc[j] += ww * v[j];
        }
        float rate = mIndexRate;
        for (int j = 0; j < d; j++)
            dst[j] = (float)acc[j] * rate + (1.0f - rate) * q[j];
    }
    feats.swap(blended);
}

// ---------------------------------------------------------------------------
// HuBERT phone (ONNX Runtime): source [1,L] + padding_mask [1,L] -> features [1,T,768]
// ---------------------------------------------------------------------------

void InferenceEngine::extractPhone(const std::vector<float>& audio16k,
                                   std::vector<float>& feats, int32_t& tLen) {
    feats.clear();
    tLen = 0;
    if (!mHubert || audio16k.empty()) return;
    try {
        const size_t audioSize = audio16k.size();
        std::vector<Ort::AllocatedStringPtr> inputNameHolders;
        std::vector<Ort::AllocatedStringPtr> outputNameHolders;
        std::vector<const char*> inputNames;
        std::vector<const char*> outputNames;
        Ort::AllocatorWithDefaultOptions allocator;
        inputNameHolders.reserve(2);
        outputNameHolders.reserve(1);
        for (size_t i = 0; i < mHubert->GetInputCount(); i++) {
            inputNameHolders.emplace_back(mHubert->GetInputNameAllocated(i, allocator));
            inputNames.push_back(inputNameHolders.back().get());
        }
        for (size_t i = 0; i < mHubert->GetOutputCount(); i++) {
            outputNameHolders.emplace_back(mHubert->GetOutputNameAllocated(i, allocator));
            outputNames.push_back(outputNameHolders.back().get());
        }
        Ort::MemoryInfo memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> shape{1, static_cast<int64_t>(audioSize)};
        std::vector<Ort::Value> inputs;
        inputs.reserve(inputNames.size());
        inputs.emplace_back(Ort::Value::CreateTensor<float>(
            memoryInfo, const_cast<float*>(audio16k.data()), audioSize, shape.data(), shape.size()));
        if (inputNames.size() > 1) {
            std::vector<unsigned char> paddingMask(audioSize, 0);
            std::vector<int64_t> maskShape{1, static_cast<int64_t>(audioSize)};
            inputs.emplace_back(Ort::Value::CreateTensor(
                memoryInfo, paddingMask.data(), paddingMask.size(),
                maskShape.data(), maskShape.size(), ONNX_TENSOR_ELEMENT_DATA_TYPE_BOOL));
        }
        auto outputs = mHubert->Run(Ort::RunOptions{nullptr}, inputNames.data(), inputs.data(), inputs.size(),
                                     outputNames.data(), outputNames.size());
        if (outputs.empty() || !outputs[0].IsTensor()) return;
        auto outputShape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
        if (outputShape.size() != 3 || outputShape[0] != 1 || outputShape[1] <= 0 || outputShape[2] != kFeatSize) {
            LOGW("hubert output shape inattesa");
            logFile("hubert output shape inattesa");
            return;
        }
        tLen = static_cast<int32_t>(outputShape[1]);
        const float* data = outputs[0].GetTensorData<float>();
        feats.assign(data, data + static_cast<size_t>(tLen) * kFeatSize);
    } catch (const std::exception& e) {
        LOGE("hubert forward failed: %s", e.what());
        logFile("hubert forward FAILED: %s", e.what());
        tLen = 0;
        feats.clear();
    }
}

// ---------------------------------------------------------------------------
// Synth (ONNX Runtime): 6 input (phone, phone_lengths, pitch, pitchf, ds, rnd)
// ---------------------------------------------------------------------------

bool InferenceEngine::synthesize(const std::vector<float>& feats, int32_t tLen,
                                 const std::vector<int64_t>& pitch, const std::vector<float>& pitchf,
                                 std::vector<float>& out) {
    out.clear();
    if (!mSynth || tLen <= 0 || feats.size() != static_cast<size_t>(tLen) * kFeatSize ||
        pitch.size() != static_cast<size_t>(tLen) || pitchf.size() != static_cast<size_t>(tLen)) return false;
    try {
        int32_t plen = (int32_t)pitch.size();
        int64_t plenVal = plen;
        int64_t speaker = 0;
        std::vector<float> rnd((size_t)kRndDim * plen);
        Rng rng;
        for (auto& v : rnd) v = rng.nextGauss();
        Ort::MemoryInfo memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> phoneShape{1, tLen, kFeatSize};
        std::vector<int64_t> vectorShape{1, plen};
        std::vector<int64_t> scalarShape{1};
        std::vector<int64_t> rndShape{1, kRndDim, plen};
        Ort::Value phone = Ort::Value::CreateTensor<float>(memoryInfo, const_cast<float*>(feats.data()), feats.size(), phoneShape.data(), phoneShape.size());
        Ort::Value phoneLengths = Ort::Value::CreateTensor<int64_t>(memoryInfo, &plenVal, 1, scalarShape.data(), scalarShape.size());
        Ort::Value pitchTensor = Ort::Value::CreateTensor<int64_t>(memoryInfo, const_cast<int64_t*>(pitch.data()), pitch.size(), vectorShape.data(), vectorShape.size());
        Ort::Value pitchfTensor = Ort::Value::CreateTensor<float>(memoryInfo, const_cast<float*>(pitchf.data()), pitchf.size(), vectorShape.data(), vectorShape.size());
        Ort::Value speakerTensor = Ort::Value::CreateTensor<int64_t>(memoryInfo, &speaker, 1, scalarShape.data(), scalarShape.size());
        Ort::Value rndTensor = Ort::Value::CreateTensor<float>(memoryInfo, rnd.data(), rnd.size(), rndShape.data(), rndShape.size());
        std::vector<const char*> inputNames{"phone", "phone_lengths", "pitch", "pitchf", "ds", "rnd"};
        Ort::AllocatorWithDefaultOptions allocator;
        auto outputName = mSynth->GetOutputNameAllocated(0, allocator);
        const char* outputNames[] = {outputName.get()};
        std::vector<Ort::Value> inputs;
        inputs.reserve(6);
        inputs.emplace_back(std::move(phone));
        inputs.emplace_back(std::move(phoneLengths));
        inputs.emplace_back(std::move(pitchTensor));
        inputs.emplace_back(std::move(pitchfTensor));
        inputs.emplace_back(std::move(speakerTensor));
        inputs.emplace_back(std::move(rndTensor));
        auto outputs = mSynth->Run(Ort::RunOptions{nullptr}, inputNames.data(), inputs.data(), inputs.size(), outputNames, 1);
        if (outputs.empty() || !outputs[0].IsTensor()) return false;
        auto outputShape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
        if (outputShape.size() != 3 || outputShape[0] != 1 || outputShape[1] != 1 || outputShape[2] <= 0) return false;
        const float* audio = outputs[0].GetTensorData<float>();
        out.assign(audio, audio + static_cast<size_t>(outputShape[2]));
        return true;
    } catch (const std::exception& e) {
        LOGE("synth forward failed: %s", e.what());
        logFile("synth forward FAILED: %s", e.what());
        out.clear();
        return false;
    }
}

// ---------------------------------------------------------------------------
// Catena completa (identica a VERIFICA_E2E.py / rvc_python pipeline.vc)
//   input 48k -> 16k -> normalize 0.95 -> filtfilt -> reflect pad 16000
//   -> f0 rmvpe -> hubert -> blend index -> upsample x2 -> protect
//   -> synth (rnd) -> trim 48000 -> output 48k trimmed
// ---------------------------------------------------------------------------

bool InferenceEngine::runChain(const float* in48k, int32_t n48k, int32_t inRate, int32_t shift,
                               std::vector<float>& out48k) {
    if (n48k <= 0) { out48k.clear(); return false; }
    if (!mHubert || !mSynth || !mRmvpeSession) {
        LOGW("runChain: modelli non caricati");
        return false;
    }

    // 1) 48k -> 16k
    std::vector<float> a16;
    resampleTo16k(in48k, n48k, inRate, a16);

    // 2) normalize (audio_max = max/0.95; se > 1 -> /audio_max)
    float peak = 0.0f;
    for (auto v : a16) peak = std::max(peak, std::abs(v));
    if (peak > 0.95f) {
        float scale = 0.95f / peak;
        for (auto& v : a16) v *= scale;
    }

    // 3) filtfilt butter highpass 48Hz
    butterFiltfilt(a16);

    // 3.5) Allinea la lunghezza di a16 (in samples a 16 kHz) in modo che
    // DOPO il reflect pad di 16000, la lunghezza totale sia multipla del
    // batch interno del modello RMVPE. RMVPE ha un Unet con 16 frame per
    // step di mel; mel frames = samples / 160, quindi vogliamo
    // (a16_len + 16000) multiplo di (16 * 160) = 2560 samples.
    // Senza questo allineamento, l'ultimo batch mel fallisce con
    // "Axis 2 has mismatched dimensions of 25 and 24" (o simili).
    {
        const int32_t kMelFrameGroup = 16 * 160;  // = 2560 samples = 16 mel frames
        int32_t target = (int32_t)a16.size();
        int32_t rem = (target + kTPad) % kMelFrameGroup;
        if (rem != 0) {
            int32_t pad = kMelFrameGroup - rem;
            // usa reflect pad replicando gli ultimi campioni (evita artefatti
            // di zero-padding alle alte frequenze, simile a numpy.pad 'edge')
            int32_t oldSize = target;
            a16.resize(target + pad);
            for (int32_t i = 0; i < pad; i++) {
                int32_t src = (i < kTPad) ? (oldSize - kTPad + i) : (oldSize - 1);
                if (src < 0) src = 0;
                a16[oldSize + i] = a16[src];
            }
            logFile("runChain: a16 padded by %d samples (from %d to %d) for mel-frame alignment",
                    pad, oldSize, (int)a16.size());
        }
    }

    // 4) reflect pad 16000 @16k
    std::vector<float> audio;
    reflectPad(a16, kTPad, audio);
    int32_t pLen = (int32_t)audio.size() / kWindow;

// 5) F0 (shift gia'' applicato)
    std::vector<int64_t> pitch;
    std::vector<float> pitchf;
    logFile("runChain: step F0 start (len=%d, mem=%s)", (int)audio.size(), memInfo().c_str());
    extractF0RMVPE(audio.data(), (int32_t)audio.size(), shift, pitch, pitchf);
    logFile("runChain: step F0 done (pitch=%d, mem=%s)", (int)pitch.size(), memInfo().c_str());
    if ((int32_t)pitch.size() > pLen) {
        pitch.resize(pLen);
        pitchf.resize(pLen);
    }

    // 6) hubert (su audio a 16k padded)
    std::vector<float> feats;
    int32_t tLenHu = 0;
    logFile("runChain: step hubert start (mem=%s)", memInfo().c_str());
    extractPhone(audio, feats, tLenHu);
    logFile("runChain: step hubert done (t=%d, mem=%s)", tLenHu, memInfo().c_str());
    // libera audio: il passo successivo (synth) usa solo feats e pitch
    std::vector<float>().swap(audio);
    if (tLenHu <= 0) { LOGW("runChain: hubert vuoto"); return false; }
    std::vector<float> feats0 = feats;

    // 7) blend index
    if (mIndexLoaded && mIndexRate > 0.0f) {
        std::vector<float> featsB = feats;
        blendWithIndex(featsB, tLenHu);
        feats.swap(featsB);
    }

    // 8) upsample nearest x2 (F.interpolate scale_factor=2)
    int32_t tLen2 = tLenHu * 2;
    std::vector<float> f2((size_t)tLen2 * kFeatSize);
    std::vector<float> f20((size_t)tLen2 * kFeatSize);
    for (int32_t t = 0; t < tLen2; t++) {
        int32_t s = t / 2;
        memcpy(f2.data() + (size_t)t * kFeatSize, feats.data() + (size_t)s * kFeatSize, sizeof(float) * kFeatSize);
        memcpy(f20.data() + (size_t)t * kFeatSize, feats0.data() + (size_t)s * kFeatSize, sizeof(float) * kFeatSize);
    }

    // 9) se feats < p_len: tronca pitch/pitchf
    int32_t synthLen = std::min(pLen, tLen2);
    if ((int32_t)pitch.size() > synthLen) {
        pitch.resize(synthLen);
        pitchf.resize(synthLen);
    }

// 10) protect (feats*pitchff + feats0*(1-pitchff))
    if (mProtection < 0.5f && (int32_t)pitchf.size() >= tLen2) {
        for (int32_t t = 0; t < tLen2; t++) {
            float pff = (pitchf[t] > 0.0f) ? 1.0f : mProtection;
            float* d0 = f2.data() + (size_t)t * kFeatSize;
            const float* s0 = f20.data() + (size_t)t * kFeatSize;
            for (int32_t j = 0; j < kFeatSize; j++)
                d0[j] = d0[j] * pff + s0[j] * (1.0f - pff);
        }
    }

// 11) synth
    logFile("runChain: step synth start (t2=%d pitch=%d)", (int)tLen2, (int)pitch.size());
    if (!synthesize(f2, tLen2, pitch, pitchf, out48k)) {
        logFile("runChain: synth FAILED");
        return false;
    }
    logFile("runChain: step synth done (out=%d)", (int)out48k.size());
    if (out48k.empty()) return false;

    // 12) trim 48000 each side
    if ((int32_t)out48k.size() > 2 * kTPadTgt) {
        out48k.erase(out48k.begin(), out48k.begin() + kTPadTgt);
        out48k.resize(out48k.size() - kTPadTgt);
    }
    return true;
}

// ---------------------------------------------------------------------------
// Live: worker thread - processa chunk da kLiveChunk (3s @48k)
//       process() NON blocca mai il callback audio
// ---------------------------------------------------------------------------

void InferenceEngine::workerLoop() {
    while (true) {
        std::vector<float> chunk;
        int32_t shift = 0, rate = 48000;
        {
            std::unique_lock<std::mutex> lk(mQMutex);
            mCv.wait_for(lk, std::chrono::milliseconds(50), [&] {
                return mStopLive.load() || (int32_t)mLiveIn.size() >= kLiveChunk;
            });
            if (mStopLive.load()) return;
            if ((int32_t)mLiveIn.size() < kLiveChunk) continue;
            chunk.swap(mLiveIn);
            shift = mLiveShift;
            rate = mLiveRate;
        }
        std::vector<float> out;
        {
            std::lock_guard<std::mutex> cl(mChainMutex);
            auto t0 = std::chrono::high_resolution_clock::now();
            if (!runChain(chunk.data(), (int32_t)chunk.size(), rate, shift, out)) {
                LOGW("workerLoop: chunk fallito");
                continue;
            }
            auto t1 = std::chrono::high_resolution_clock::now();
            int64_t ms = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();
            LOGI("chunk: %d frames -> %zu out in %lldms (%.2fx)",
                 (int)chunk.size(), out.size(), (long long)ms,
                 chunk.size() / (float)std::max<uint64_t>((uint64_t)ms * 48000 / 1000, 1));
        }
        std::lock_guard<std::mutex> lk(mQMutex);
        mLiveOut.insert(mLiveOut.end(), out.begin(), out.end());
        mCv.notify_all();
    }
}

void InferenceEngine::process(const float* input, int32_t numFrames, int32_t inputRate,
                              int32_t pitchShift, float* output) {
    if (!mSynthLoaded || !mVecLoaded || !mF0Loaded) {
        pitchShiftOnly(input, numFrames, inputRate, pitchShift, output);
        return;
    }
    std::lock_guard<std::mutex> lk(mQMutex);
    mLiveIn.insert(mLiveIn.end(), input, input + numFrames);
    mLiveShift = pitchShift + mFormantShift;
    mLiveRate = inputRate;
    size_t avail = mLiveOut.size();
    size_t take = std::min<size_t>(numFrames, avail);
    if (take > 0) {
        memcpy(output, mLiveOut.data(), take * sizeof(float));
        mLiveOut.erase(mLiveOut.begin(), mLiveOut.begin() + take);
    }
    if (take < (size_t)numFrames) {
        // non ancora disponibile: passa il dry signal (l''utente continua a sentirsi)
        memcpy(output + take, input + take, (numFrames - take) * sizeof(float));
    }
    mCv.notify_all();
}

// ---------------------------------------------------------------------------
// Pitch-shift only fallback (nessun modello caricato)
// ---------------------------------------------------------------------------

void InferenceEngine::pitchShiftOnly(const float* in, int32_t n, int32_t sr, int32_t semis, float* out) {
    if (semis == 0) { memcpy(out, in, n * sizeof(float)); return; }
    float r = std::pow(2.0f, semis / 12.0f);
    int32_t on = std::max((int32_t)(n / r), 1);
    for (int32_t i = 0; i < n && i < on; i++) {
        float si = i * r;
        int32_t lo = (int32_t)si, hi = std::min(lo + 1, n - 1);
        float f = si - lo;
        out[i] = in[lo] * (1.0f - f) + in[hi] * f;
    }
    for (int32_t i = on; i < n; i++) out[i] = 0.0f;
}

// ---------------------------------------------------------------------------
// Offline: file -> file (chunk 10s @48k, stessa catena, int16 con 0.99 peak)
// ---------------------------------------------------------------------------

bool InferenceEngine::processOffline(const std::string& inputPath, const std::string& outputPath,
                                     int32_t pitchShift, ProgressCallback cb, void* userData) {
    LOGI("processOffline: %s -> %s", inputPath.c_str(), outputPath.c_str());
    logFile("processOffline: %s -> %s (shift=%d)", inputPath.c_str(), outputPath.c_str(), (int)pitchShift);
    std::lock_guard<std::mutex> cl(mChainMutex);
    if (!isSynthLoaded() || !isVecLoaded() || !isF0Loaded()) {
        logFile("processOffline ABORT: missing models (synth=%d vec=%d f0=%d) synth='%s' vec='%s' f0='%s'",
                (int)isSynthLoaded(), (int)isVecLoaded(), (int)isF0Loaded(),
                mSynthPath.c_str(), mVecPath.c_str(), mF0Path.c_str());
        return false;
    }

    FILE* f = fopen(inputPath.c_str(), "rb");
    if (!f) { LOGE("Cannot open input: %s", inputPath.c_str()); logFile("processOffline: cannot open input"); return false; }

    char riff[5] = {};
    if (fread(riff, 1, 4, f) != 4 || memcmp(riff, "RIFF", 4) != 0) { fclose(f); LOGE("Not RIFF"); return false; }
    fseek(f, 8, SEEK_SET);
    char wave[5] = {};
    if (fread(wave, 1, 4, f) != 4 || memcmp(wave, "WAVE", 4) != 0) { fclose(f); LOGE("Not WAVE"); return false; }
    fseek(f, 12, SEEK_SET);

    char fmtChunk[5] = {};
    fread(fmtChunk, 1, 4, f);
    int32_t chunkSize = 0; fread(&chunkSize, 4, 1, f);
    int16_t audioFmt = 0; fread(&audioFmt, 2, 1, f);
    int16_t numChannels = 0; fread(&numChannels, 2, 1, f);
    int32_t sampleRate = 0; fread(&sampleRate, 4, 1, f);
    int32_t byteRate = 0; fread(&byteRate, 4, 1, f);
    int16_t blockAlign = 0; fread(&blockAlign, 2, 1, f);
    int16_t bitsPerSample = 0; fread(&bitsPerSample, 2, 1, f);
    if (numChannels <= 0 || sampleRate <= 0) { fclose(f); LOGE("WAV header invalido"); return false; }
    fseek(f, 20 + chunkSize, SEEK_SET);

    char dataTag[5] = {};
    int32_t dataSize = 0;
    while (1) {
        if (fread(dataTag, 1, 4, f) != 4) break; dataTag[4] = 0;
        fread(&dataSize, 4, 1, f);
        if (memcmp(dataTag, "data", 4) == 0) break;
        fseek(f, dataSize, SEEK_CUR);
    }
    if (dataSize <= 0) { fclose(f); LOGE("No data chunk"); return false; }

int64_t totalSamples = (int64_t)(dataSize) / (bitsPerSample / 8) / numChannels;
    LOGI("WAV: %lld samples, %d Hz, %d-bit, %d ch", (long long)totalSamples, sampleRate, bitsPerSample, numChannels);
    logFile("wav: %lld samples %dHz %dbit %dch", (long long)totalSamples, sampleRate, bitsPerSample, numChannels);

    // lettura mono float
    std::vector<float> floatBuffer((size_t)totalSamples);
    for (int64_t i = 0; i < totalSamples; i++) {
        float sum = 0.0f;
        for (int16_t ch = 0; ch < numChannels; ch++) {
            if (bitsPerSample == 16) {
                int16_t s = 0; fread(&s, 2, 1, f);
                sum += s / 32768.0f;
            } else if (bitsPerSample == 24) {
                unsigned char bytes[3] = {0, 0, 0}; fread(bytes, 1, 3, f);
                int32_t s = bytes[0] | (bytes[1] << 8) | (bytes[2] << 16);
                if (s & 0x800000) s |= ~0xFFFFFF;
                sum += s / 8388608.0f;
            } else if (bitsPerSample == 32) {
                float s = 0.0f; fread(&s, 4, 1, f);
                sum += s;
            }
        }
        floatBuffer[i] = sum / numChannels;
    }
    fclose(f);

    // processa per chunk da kOfflineChunk (10s)
    std::vector<float> output48k;
    output48k.reserve(totalSamples + 65536);
    int64_t processed = 0;
    while (processed < totalSamples) {
        int32_t n = (int32_t)std::min((int64_t)kOfflineChunk, totalSamples - processed);
        std::vector<float> chunkOut;
int32_t totalShift = pitchShift + mFormantShift;
        logFile("processOffline: chunk %lld/%lld running chain (shift=%d)", (long long)processed, (long long)totalSamples, (int)totalShift);
        if (!runChain(floatBuffer.data() + processed, n, sampleRate, totalShift, chunkOut)) {
            LOGE("Offline: chunk %lld fallito", (long long)processed);
            logFile("processOffline: runChain FAILED at chunk %lld", (long long)processed);
            return false;
        }
        logFile("processOffline: chunk %lld done (%zu out)", (long long)processed, chunkOut.size());
        // 48k -> sampleRate (se diverso)
        if (sampleRate != kSynthRate) {
            std::vector<float> res;
            resampleTo(chunkOut.data(), (int32_t)chunkOut.size(), kSynthRate, sampleRate, res);
            chunkOut.swap(res);
        }
        output48k.insert(output48k.end(), chunkOut.begin(), chunkOut.end());
        processed += n;
        if (cb) cb((float)processed / (float)totalSamples, userData);
        LOGI("Offline: %lld/%lld (%.0f%%)", (long long)processed, (long long)totalSamples,
             100.0 * (double)processed / totalSamples);
    }

    // scrittura WAV 16-bit (normalizzazione peak 0.99, come rvc_python)
    FILE* outFile = fopen(outputPath.c_str(), "wb");
    if (!outFile) { LOGE("Cannot write output: %s", outputPath.c_str()); return false; }

    int32_t outSr = sampleRate;
    int16_t outCh = 1, outBps = 16;
    int64_t outDataSize = (int64_t)output48k.size() * 2;
    int64_t outFileSize = 36 + outDataSize;

    fwrite("RIFF", 1, 4, outFile);
    fwrite(&outFileSize, 4, 1, outFile);
    fwrite("WAVE", 1, 4, outFile);
    fwrite("fmt ", 1, 4, outFile);
int32_t fmtSize = 16; fwrite(&fmtSize, 4, 1, outFile);
    int16_t fmtOut = 1; fwrite(&fmtOut, 2, 1, outFile);
    fwrite(&outCh, 2, 1, outFile);
    fwrite(&outSr, 4, 1, outFile);
    int32_t outByteRate = outSr * outCh * 2; fwrite(&outByteRate, 4, 1, outFile);
    int16_t outBlockAlign = outCh * 2; fwrite(&outBlockAlign, 2, 1, outFile);
    fwrite(&outBps, 2, 1, outFile);
    fwrite("data", 1, 4, outFile);
    fwrite(&outDataSize, 4, 1, outFile);

    float maxPeak = 0.0f;
    for (float v : output48k) maxPeak = std::max(maxPeak, std::abs(v));
    float scale = 32768.0f;
    float am = maxPeak / 0.99f;
    if (am > 1.0f) scale = 32768.0f / am;

    for (float v : output48k) {
        float s = std::clamp(v * scale, -32768.0f, 32767.0f);
        int16_t sample = (int16_t)s;
        fwrite(&sample, 2, 1, outFile);
    }
    fclose(outFile);
    LOGI("Offline export: %s (%lld samples, %d Hz)", outputPath.c_str(), (long long)output48k.size(), outSr);
    if (cb) cb(1.0f, userData);
    return true;
}
