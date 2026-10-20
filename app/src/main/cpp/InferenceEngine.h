#ifndef RVC_INFERENCE_ENGINE_H
#define RVC_INFERENCE_ENGINE_H

#include <algorithm>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <thread>
#include <cstdint>
namespace Ort { class Env; class Session; }

class InferenceEngine {
public:
    InferenceEngine();
    ~InferenceEngine();

    // ==== model loading (ONNX synth/HuBERT/RMVPE / FAISS .fbin) ====
    bool loadSynthesizerModel(const std::string& filePath);   // synth .onnx (6 inputs: phone, phone_lengths, pitch, pitchf, ds, rnd)
    bool loadContentVecModel(const std::string& filePath);    // hubert .onnx (input [1,L] -> [1,T,768])
    bool loadF0Model(const std::string& filePath);            // rmvpe.onnx (input [1,128,T] mel -> [1,T,360])
    bool loadIndex(const std::string& filePath);              // .fbin (export_faiss_bin.py)
    bool isSynthLoaded() const { return mSynthLoaded; }
    bool isVecLoaded() const { return mVecLoaded; }
    bool isF0Loaded() const { return mF0Loaded; }
    bool isIndexLoaded() const { return mIndexLoaded; }
    std::string getSynthModelName() const;
    std::string getVecModelName() const;
    std::string getIndexName() const;

    // ==== processing ====
    void process(const float* input, int32_t numFrames, int32_t inputRate,
                 int32_t pitchShift, float* output);
    void pitchShiftOnly(const float* input, int32_t numFrames,
                        int32_t inputRate, int32_t semitones, float* output);
    typedef void (*ProgressCallback)(float progress, void* userData);
    bool processOffline(const std::string& inputPath, const std::string& outputPath,
                        int32_t pitchShift, ProgressCallback cb = nullptr, void* userData = nullptr);
    void release();

    // ==== parameters ====
    void setFormantShift(int semitones) { mFormantShift = semitones; }
    void setF0Method(int method) { (void)method; mF0Method = 0; } // sempre rmvpe (top quality)
    void setIndexRate(float r) { mIndexRate = std::clamp(r, 0.0f, 1.0f); }
    void setProtection(float p) { mProtection = std::clamp(p, 0.0f, 1.0f); }
    void setNoiseScale(float s) { (void)s; } // rnd grezzo (come rvc_python: senza noise_scale)
    void setRmsMixRate(float r) { mRmsMixRate = std::clamp(r, 0.0f, 1.0f); }
    void setFilterRadius(int r) { (void)r; } // rmvpe non usa il median filter
    int getFormantShift() const { return mFormantShift; }
    int getF0Method() const { return 0; }
    float getIndexRate() const { return mIndexRate; }
    float getProtection() const { return mProtection; }
    float getNoiseScale() const { return 1.0f; }

private:
    // ==== DSP helpers ====
    void resampleTo16k(const float* in, int32_t inFrames, int32_t inRate,
                       std::vector<float>& out16k);
    void resampleTo(const float* in, int32_t inFrames, int32_t inRate,
                    int32_t outRate, std::vector<float>& out);
    void firDecimate3(const float* in, int32_t inFrames, std::vector<float>& out16k);
    void butterFiltfilt(std::vector<float>& x);
    void reflectPad(const std::vector<float>& x, int32_t pad, std::vector<float>& out);

    // ==== rmvpe (F0) ====
    void computeMel(const float* audio16k, int32_t frames, int32_t nFrames,
                    std::vector<float>& melOut);   // melOut [nFrames*melBins] log-mel
    void decodeF0(const float* hidden, int32_t nFrames, std::vector<float>& f0);
    void extractF0RMVPE(const float* audio16k, int32_t frames,
                        int32_t shift, std::vector<int64_t>& pitch, std::vector<float>& pitchf);

    // ==== catena RVC (identica a VERIFICA_E2E.py) ====
    bool runChain(const float* in48k, int32_t n48k, int32_t inRate, int32_t shift,
                  std::vector<float>& out48k);

    // ==== hubert / synth ====
    void extractPhone(const std::vector<float>& audio16k,
                      std::vector<float>& feats, int32_t& tLen);
    bool synthesize(const std::vector<float>& feats, int32_t tLen,
                    const std::vector<int64_t>& pitch, const std::vector<float>& pitchf,
                    std::vector<float>& out);

    // ==== faiss .fbin ====
    bool loadFbin(const std::string& path);
    const float* indexGetVector(int64_t gid);  // accesso on-demand con cache
    void blendWithIndex(std::vector<float>& feats, int32_t tLen);
    void indexSearch(const float* query, std::vector<int64_t>& ids, std::vector<float>& scores);

    // ==== live streaming (worker thread) ====
    void workerLoop();
    bool mWorkerRunning = false;
    std::thread mWorker;
    std::mutex mQMutex;
    std::condition_variable mCv;
    std::vector<float> mLiveIn;   // 48k, accumulo
    std::vector<float> mLiveOut;  // 48k, output pronto
    std::atomic<bool> mStopLive{false};
    int32_t mLiveShift = 0, mLiveRate = 48000;
    std::mutex mChainMutex;       // serializza chain (worker + offline)
    std::mutex mMutex;            // protezione stato/models

    // ==== stato ====
    bool mSynthLoaded = false, mVecLoaded = false, mF0Loaded = false, mIndexLoaded = false;
    std::string mSynthModelName, mVecModelName, mIndexName;
    // Percorsi dei modelli caricati (per diagnostica: processOffline ABORT log
    // li include per aiutare a capire quale file manca dopo un run).
    std::string mSynthPath, mVecPath, mF0Path;
    std::unique_ptr<Ort::Session> mHubert;
    std::unique_ptr<Ort::Session> mSynth;
    Ort::Env* mEnv = nullptr;
    Ort::Session* mRmvpeSession = nullptr;

    // rmvpe mel constants
    std::vector<float> mMelBasis;  // [128][513]
    int32_t mMelBins = 128, mMelNfft = 1024, mMelHop = 160;

    // faiss index (.fbin) - i vettori big_npy sono letti on-demand dal file
    // per supportare index grandi (fino a ~500MB) senza occupare tutta la RAM.
    int32_t mIndexDim = 0, mIndexNlist = 0, mIndexNprobe = 1, mIndexMetric = 1;
    int64_t mIndexNtotal = 0;
    int64_t mIndexBigNpyOffset = 0;   // posizione iniziale del blocco big_npy nel file .fbin
    std::vector<float> mIndexCentroids;
    std::vector<std::vector<int64_t>> mIndexLists;
    FILE* mIndexFbinFile = nullptr;
    // cache LRU minima per ridurre seek ripetuti su index grandi
    static constexpr int32_t kIndexVecCache = 256;
    std::vector<int64_t> mIndexVecCacheIds;
    std::vector<float> mIndexVecCacheData;  // flattened: kIndexVecCache * dim

    // params
    static constexpr int32_t kTargetRate = 16000;
    static constexpr int32_t kSynthRate = 48000;
    static constexpr int32_t kWindow = 160;
    static constexpr int32_t kTPad = 16000;      // reflect pad 16k
    static constexpr int32_t kTPadTgt = 48000;   // trim 48k
    static constexpr int32_t kFeatSize = 768;
    static constexpr int32_t kRndDim = 192;
    static constexpr int32_t kLiveChunk = 144000;   // 3s @48k
    static constexpr int32_t kOfflineChunk = 480000; // 10s @48k
    int mFormantShift = 0, mF0Method = 0;
    float mIndexRate = 0.5f, mProtection = 0.33f, mRmsMixRate = 1.0f;
};

#endif
