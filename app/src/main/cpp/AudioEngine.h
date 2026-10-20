#ifndef RVC_AUDIO_ENGINE_H
#define RVC_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <functional>
#include <memory>
#include <mutex>
#include <vector>

class MasteringChain;
class TrackMixer;

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(bool enableRVC = false);
    bool startMonitoring();
    void stop();
    bool isRunning() const { return mIsRunning.load(); }
    bool isMonitoring() const { return mIsMonitoring.load(); }

    void enableRVC(bool enabled) { mRvcEnabled.store(enabled); }
    bool isRvcEnabled() const { return mRvcEnabled.load(); }

    using RvcProcessCallback = std::function<void(
        const float* input, float* output, int32_t numFrames, int32_t sampleRate)>;

    void setRvcCallback(RvcProcessCallback callback) {
        std::lock_guard<std::mutex> lock(mCallbackMutex);
        mRvcCallback = callback;
    }

    void setMasteringChain(MasteringChain* mc) {
        std::lock_guard<std::mutex> lock(mCallbackMutex);
        mMasteringChain = mc;
    }

    void setTrackMixer(TrackMixer* tm) {
        std::lock_guard<std::mutex> lock(mCallbackMutex);
        mTrackMixer = tm;
    }

    int32_t getSampleRate() const { return mSampleRate; }
    int32_t getFramesPerBurst() const { return mFramesPerBurst; }

    void setMonitoringGain(float gain) { mMonitoringGain.store(gain); }
    float getMonitoringGain() const { return mMonitoringGain.load(); }

    void setInputDeviceId(int32_t deviceId) { mInputDeviceId = deviceId; }
    void setOutputDeviceId(int32_t deviceId) { mOutputDeviceId = deviceId; }

    // Direct input peak tracking from mic callback (not dependent on MasteringChain)
    float getInputPeakDB() const {
        float p = mInputPeak.load();
        return p > 0.0f ? 20.0f * std::log10(p) : -80.0f;
    }
    void resetInputPeak() { mInputPeak.store(0.0f); }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    bool onError(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openInputStream();
    bool openOutputStream();
    void closeStreams();

    static constexpr int32_t kRingBufferCapacity = 4096;
    std::vector<float> mRingBuffer;
    std::atomic<int32_t> mRingWriteIndex{0};
    std::atomic<int32_t> mRingReadIndex{0};

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::atomic<bool> mIsRunning{false};
    std::atomic<bool> mIsMonitoring{false};
    std::atomic<bool> mRvcEnabled{false};
    std::atomic<int32_t> mSampleRate{44100};
    std::atomic<int32_t> mFramesPerBurst{192};
    std::atomic<int32_t> mInputDeviceId{oboe::Unspecified};
    std::atomic<int32_t> mOutputDeviceId{oboe::Unspecified};

    std::mutex mCallbackMutex;
    RvcProcessCallback mRvcCallback;
    MasteringChain* mMasteringChain = nullptr;
    TrackMixer* mTrackMixer = nullptr;
    std::vector<float> mZeroBuf, mMasterBuf, mRawVoiceBuf;
    std::atomic<float> mMonitoringGain{0.7f};
    std::atomic<float> mInputPeak{0.0f}; // peak from input callback directly
};

#endif
