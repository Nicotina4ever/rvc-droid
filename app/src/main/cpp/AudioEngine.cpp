#include "AudioEngine.h"
#include "MasteringChain.h"
#include "TrackMixer.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    mRingBuffer.resize(kRingBufferCapacity, 0.0f);
    LOGI("AudioEngine created");
}

AudioEngine::~AudioEngine() { stop(); LOGI("AudioEngine destroyed"); }

bool AudioEngine::start(bool enableRVC) {
    if (mIsRunning.load()) return true;
    mIsMonitoring.store(false);
    mRvcEnabled.store(enableRVC);
    mRingWriteIndex.store(0);
    mRingReadIndex.store(0);
    mInputPeak.store(0.0f);
    // Pre-allocate processing buffers
    mZeroBuf.resize(2048, 0.0f);
    mMasterBuf.resize(2048, 0.0f);
    mRawVoiceBuf.resize(2048, 0.0f);
    if (!openInputStream()) { LOGE("Failed to open input stream"); return false; }
    LOGI("Input stream opened, sampleRate=%d", mSampleRate.load());
    if (!openOutputStream()) { LOGE("Failed to open output stream"); closeStreams(); return false; }
    LOGI("Output stream opened");

    oboe::Result inResult = mInputStream->start();
    if (inResult != oboe::Result::OK) {
        LOGE("Failed to start input stream: %s", oboe::convertToText(inResult));
        closeStreams(); return false;
    }
    oboe::Result outResult = mOutputStream->start();
    if (outResult != oboe::Result::OK) {
        LOGE("Failed to start output stream: %s", oboe::convertToText(outResult));
        mInputStream->stop(); mInputStream->close(); mInputStream.reset();
        return false;
    }
    mIsRunning.store(true);
    LOGI("AudioEngine started @ %d Hz", mSampleRate.load());
    return true;
}

bool AudioEngine::startMonitoring() {
    if (mIsRunning.load() || mIsMonitoring.load()) return true;
    mRvcEnabled.store(false); // Monitor raw input
    mRingWriteIndex.store(0);
    mRingReadIndex.store(0);
    mInputPeak.store(0.0f);
    if (!openInputStream()) return false;
    if (!openOutputStream()) { closeStreams(); return false; }

    if (mInputStream->start() != oboe::Result::OK || mOutputStream->start() != oboe::Result::OK) {
        closeStreams(); return false;
    }
    mIsMonitoring.store(true);
    LOGI("AudioEngine monitoring started");
    return true;
}

void AudioEngine::stop() {
    mIsRunning.store(false);
    mIsMonitoring.store(false);
    closeStreams();
}

bool AudioEngine::openInputStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Shared) // Use Shared for higher compatibility
           ->setFormat(oboe::AudioFormat::Float)
           ->setDataCallback(this)->setErrorCallback(this)
           ->setChannelCount(oboe::ChannelCount::Mono)
           ->setSampleRate(48000)
           ->setInputPreset(oboe::InputPreset::Generic)
           ->setFramesPerCallback(oboe::Unspecified);

    if (mInputDeviceId != oboe::Unspecified) {
        builder.setDeviceId(mInputDeviceId);
    }

    oboe::Result result = builder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGE("Could not open input stream: %s", oboe::convertToText(result));
        return false;
    }
    mSampleRate.store(mInputStream->getSampleRate());
    mFramesPerBurst.store(mInputStream->getFramesPerBurst());
    LOGI("Input opened: %dHz, %d fpb, fmt=%d, sharing=%d, device=%d",
        mSampleRate.load(), mFramesPerBurst.load(), mInputStream->getFormat(),
        mInputStream->getSharingMode(), mInputStream->getDeviceId());
    return true;
}

bool AudioEngine::openOutputStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Shared)
           ->setFormat(oboe::AudioFormat::Float)
           ->setDataCallback(this)->setErrorCallback(this)
           ->setChannelCount(oboe::ChannelCount::Mono)
           ->setSampleRate(mSampleRate.load())
           ->setUsage(oboe::Usage::Media)
           ->setFramesPerCallback(oboe::Unspecified);

    if (mOutputDeviceId != oboe::Unspecified) {
        builder.setDeviceId(mOutputDeviceId);
    }

    oboe::Result result = builder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Could not open output stream: %s", oboe::convertToText(result));
        return false;
    }
    LOGI("Output opened: %dHz, %d fpb, device=%d",
        mSampleRate.load(), mOutputStream->getFramesPerBurst(), mOutputStream->getDeviceId());
    return true;
}

void AudioEngine::closeStreams() {
    if (mInputStream) { mInputStream->stop(); mInputStream->close(); mInputStream.reset(); }
    if (mOutputStream) { mOutputStream->stop(); mOutputStream->close(); mOutputStream.reset(); }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    if (stream == mInputStream.get()) {
        float* in = static_cast<float*>(audioData);
        int32_t w = mRingWriteIndex.load();

        float maxVal = 0.0f;
        for (int32_t i = 0; i < numFrames; i++) {
            float val = std::abs(in[i]);
            if (val > maxVal) maxVal = val;
            mRingBuffer[(w + i) % kRingBufferCapacity] = in[i];
        }

        // Track input peak directly (independent of MasteringChain/output callback)
        mInputPeak.store(maxVal);

        mRingWriteIndex.store((w + numFrames) % kRingBufferCapacity);
        return oboe::DataCallbackResult::Continue;
    }
    if (stream == mOutputStream.get()) {
        float* out = static_cast<float*>(audioData);
        int32_t r = mRingReadIndex.load();
        int32_t w = mRingWriteIndex.load();
        int32_t avail = (w - r + kRingBufferCapacity) % kRingBufferCapacity;

        // Ensure buffers are large enough
        if((int32_t)mZeroBuf.size() < numFrames) {
            mZeroBuf.resize(numFrames + 64, 0.0f);
            mMasterBuf.resize(numFrames + 64, 0.0f);
            mRawVoiceBuf.resize(numFrames + 64, 0.0f);
        }

        // 1) Get Mic Voice
        float* voiceBuf = out; // We can use output buffer directly for voice path
        if (avail >= numFrames) {
            for (int32_t i = 0; i < numFrames; i++)
                voiceBuf[i] = mRingBuffer[(r + i) % kRingBufferCapacity];
            mRingReadIndex.store((r + numFrames) % kRingBufferCapacity);
        } else {
            memset(voiceBuf, 0, numFrames * sizeof(float));
        }

        // Save raw mic voice
        memcpy(mRawVoiceBuf.data(), voiceBuf, numFrames * sizeof(float));

        // 2) Apply RVC to Mic Voice
        if (mRvcEnabled.load() && mRvcCallback) {
            std::lock_guard<std::mutex> lock(mCallbackMutex);
            if (mRvcCallback) mRvcCallback(voiceBuf, voiceBuf, numFrames, mSampleRate.load());
        } else {
            for (int32_t i = 0; i < numFrames; i++) voiceBuf[i] *= mMonitoringGain.load();
        }

        // 3) Mix Timeline tracks into correct MasteringChain pins
        float* musicBuf = mZeroBuf.data(); // Use zeroBuf to collect music tracks
        memset(musicBuf, 0, numFrames * sizeof(float));

        if (mTrackMixer) {
            std::lock_guard<std::mutex> lock(mCallbackMutex);
            // Mix Track 0 into Voice
            mTrackMixer->mix(voiceBuf, numFrames, true, false);
            // Mix Tracks 1..31 into Music
            mTrackMixer->mix(musicBuf, numFrames, false, true);
            // Advance global position once
            mTrackMixer->setPosition(mTrackMixer->getPosition() + numFrames);
        }

        // 4) Mastering: voice=voiceBuf, music=musicBuf
        if (mMasteringChain) {
            mMasteringChain->process(voiceBuf, musicBuf, numFrames, mMasterBuf.data(), mRawVoiceBuf.data());
            memcpy(out, mMasterBuf.data(), numFrames * sizeof(float));
        } else {
            // Mix-down if no mastering chain
            for (int32_t i = 0; i < numFrames; i++) out[i] = voiceBuf[i] + musicBuf[i];
        }

        return oboe::DataCallbackResult::Continue;
    }
    return oboe::DataCallbackResult::Stop;
}

bool AudioEngine::onError(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Error: %s", oboe::convertToText(error));
    mIsRunning.store(false);
    return false;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Error after close: %s. Attempting restart...", oboe::convertToText(error));
    std::lock_guard<std::mutex> lock(mCallbackMutex);
    if (mIsRunning.load() || mIsMonitoring.load()) {
        bool rvc = mRvcEnabled.load();
        bool monitoring = mIsMonitoring.load();
        closeStreams();
        if (monitoring) startMonitoring();
        else start(rvc);
    }
}
