#ifndef RVC_MASTERING_CHAIN_H
#define RVC_MASTERING_CHAIN_H

#include <cstdint>
#include <vector>
#include <array>
#include <cstdio>
#include <cstring>
#include <chrono>

class MasteringChain {
public:
    MasteringChain();
    ~MasteringChain();

    // Main processing pipeline. rawVoice = pre-RVC signal for recording without RVC
    void process(const float* voice, const float* music, int32_t numFrames, float* output,
                 const float* rawVoice = nullptr);

    struct EQBand { float freq=1000.0f; float gainDB=0.0f; float q=1.0f; bool enabled=true; };
    void setVoiceEQ(const std::array<EQBand,4>& bands);
    void setMusicEQ(const std::array<EQBand,4>& bands);
    void setSideChainParams(float threshDB, float ratio, float attackMs, float releaseMs, float makeupDB);
    void setLimiterParams(float threshDB, float releaseMs);
    void setVoiceVolume(float dB);
    void setMusicVolume(float dB);
    void setMasterVolume(float dB);

    struct LufsReading { float momentary=-70,shortTerm=-70,integrated=-70,truePeak=-70,range=0; };
    LufsReading getLufsReading() const;
    void reset();
    void setSampleRate(int32_t sr) { mSampleRate=sr; }

    float getCompressorGR() const;
    float getLimiterGR() const;
    float getNoiseGateGR() const;

    void setNoiseGate(bool enabled, float threshold, float attackMs, float holdMs, float releaseMs);

    struct InputLevelReading {
        float left=-80, right=-80, peakLeft=-80, peakRight=-80;
        bool clipping=false;
    };
    InputLevelReading getInputLevel() const;

    bool startRecording(const char* path, int bitsPerSample, const char* format,
                        bool includeRVC, bool includeMastering);
    void stopRecording();
    bool isRecording() const { return mIsRecording; }
    float getRecordingDurationSec() const;

    // REVERB (Freeverb-style, mono)
    void setReverbMix(float mix);
    void setReverbRoomSize(float sz);
    void setReverbDamping(float damp);
    float getReverbMix() const { return mReverbMix; }
    float getReverbRoomSize() const { return mReverbRoomSize; }
    float getReverbDamping() const { return mReverbDamping; }

    // Waveform peaks for real-time UI (scrolling)
    const float* getWaveformPeaks() const { return mWaveformPeaks; }
    int getWaveformPeakCount() const { return kWaveformPeaks; }
    int getWaveformTotalPeaks() const { return mWaveformPeaksCollected; }
    int getWaveformWritePos() const { return mWaveformWritePos; }

    // Backing track
    void loadBackingTrack(const char* wavPath);
    void setBackingTrackGain(float g) { mBackingGain = g; }
    void setBackingTrackPosition(int64_t frames) { mBackingReadPos = frames; }
    bool isBackingLoaded() const { return mBackingLoaded; }
    float getBackingGain() const { return mBackingGain; }

private:
    struct Biquad {
        float b0=1,b1=0,b2=0,a1=0,a2=0,x1=0,x2=0,y1=0,y2=0;
        void process(const float* in, float* out, int32_t n);
        void calcPeakEQ(float f, float g, float q, float sr);
        void resetSt() { x1=x2=y1=y2=0; }
    };
    struct EQ4B {
        std::array<Biquad,4> bands;
        void process(const float* in, float* out, int32_t n);
        void cfg(const std::array<EQBand,4>& p, float sr);
        void rst();
    };
    struct SideComp {
        float thresh=-20,ratio=4,attack=5,release=50,makeup=0,env=0,gr=0;
        void process(const float* in, const float* sc, float* out, int32_t n, float sr);
        void rst();
    };
    struct Limiter {
        float thresh=-0.5f,release=60,gr=0,peak=0,relCoeff=0;
        void process(float* buf, int32_t n, float sr);
        void rst();
    };
    struct LufsMeter {
        double momSum=0; int32_t momFrames=0;
        static constexpr int kSTF=3*48000; float stBuf[kSTF]={0}; int32_t stPos=0;
        double intSum=0,intSqSum=0; int64_t intFrames=0; float truePeak=0;
        void proc(const float* in, int32_t n, float sr);
        LufsReading read(int32_t sr) const;
        void rst();
    };
    struct NoiseGate {
        bool enabled=false; float threshold=0.05f; float attackMs=1.0f;
        float holdMs=50.0f; float releaseMs=100.0f;
        float env=0,envHold=0,gr=0; int32_t holdCount=0;
        float currentGain=1.0f, targetGain=1.0f;
        void process(const float* in, float* out, int32_t n, float sr);
        void rst();
    };

    // --- FREVERB ---
    struct CombFilter {
        std::vector<float> buffer;
        int bufSize=0, bufIndex=0;
        float feedback=0.84f;
        float damp1=0, damp2=0;
        float lpOut=0;
        void setDelay(int samples, float sr);
        void process(const float* in, float* out, int32_t n);
    };
    struct AllPassFilter {
        std::vector<float> buffer;
        int bufSize=0, bufIndex=0;
        float feedback=0.5f;
        void setDelay(int samples);
        void process(const float* in, float* out, int32_t n);
    };
    struct Reverb {
        CombFilter comb[4];
        AllPassFilter allpass[2];
        float wet=0.33f, dry=0.67f;
        float roomSize=0.5f, damping=0.5f;
        bool enabled=false;
        void init(float sr);
        void process(const float* in, float* out, int32_t n);
        void reset();
    };

    int32_t mSampleRate=48000;
    EQ4B mVoiceEQ, mMusicEQ;
    SideComp mComp;
    Limiter mLimiter;
    LufsMeter mLufs;
    NoiseGate mNoiseGate;
    Reverb mReverb;
    float mReverbMix=0, mReverbRoomSize=0.5f, mReverbDamping=0.5f;

    float mInputPeakL=0, mInputPeakR=0;
    int32_t mWavBitsPerSample=16;
    bool mIsRecording=false, mIncludeRVC=true, mIncludeMastering=true;
    FILE* mRecordFile=nullptr;
    int64_t mRecordStartTime=0, mRecordFramesWritten=0;
    // Peak buffer for waveform visualization (scrolling)
    static constexpr int kWaveformPeaks = 4096;
    float mWaveformPeaks[kWaveformPeaks] = {};
    int mWaveformWritePos = 0;      // next write position (monotonic)
    int mWaveformPeaksCollected = 0; // total peaks collected
    float mWaveformAccum = 0;
    int mWaveformCount = 0;
    static constexpr int kWaveformDecimation = 64; // samples per peak

    // Backing track state
    bool mBackingLoaded = false;
    float mBackingGain = 0.5f;
    std::vector<float> mBackingBuffer;
    int64_t mBackingReadPos = 0;
    int32_t mBackingSampleRate = 0;

    float mVoiceGain=1, mMusicGain=1, mMasterGain=1;
    std::vector<float> mBufV, mBufM, mBufC, mBufNG, mBufRawV, mBufRvb;
};

#endif
