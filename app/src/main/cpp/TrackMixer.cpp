#include "TrackMixer.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <cstdio>

#define LOG_TAG "TrackMixer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

TrackMixer::TrackMixer() {
    LOGI("TrackMixer created (max %d tracks)", kMaxTracks);
}

TrackMixer::~TrackMixer() {
    clearAll();
    LOGI("TrackMixer destroyed");
}

int32_t TrackMixer::addTrack(const std::string& name) {
    std::lock_guard<std::mutex> lock(mMutex);
    if ((int32_t)mTracks.size() >= kMaxTracks) {
        LOGE("Max tracks reached (%d)", kMaxTracks);
        return -1;
    }
    auto track = std::make_unique<Track>();
    track->name = name.empty() ? "Track " + std::to_string(mTracks.size() + 1) : name;
    track->active.store(true);
    int32_t index = static_cast<int32_t>(mTracks.size());
    mTracks.push_back(std::move(track));
    LOGI("Added track %d: %s", index, mTracks[index]->name.c_str());
    return index;
}

void TrackMixer::removeTrack(int32_t index) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    LOGI("Removed track %d: %s", index, mTracks[index]->name.c_str());
    mTracks.erase(mTracks.begin() + index);
}

int32_t TrackMixer::getTrackCount() const {
    std::lock_guard<std::mutex> lock(mMutex);
    return static_cast<int32_t>(mTracks.size());
}

TrackMixer::Track* TrackMixer::getTrack(int32_t index) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return nullptr;
    return mTracks[index].get();
}

bool TrackMixer::mix(float* output, int32_t numFrames, bool includeVoice, bool includeMusic) {
    std::lock_guard<std::mutex> lock(mMutex);
    int64_t currentPos = mGlobalPosition.load();

    bool anySolo = false;
    for (auto& track : mTracks) {
        if (track && track->solo.load()) {
            anySolo = true;
            break;
        }
    }

    memset(output, 0, numFrames * sizeof(float));
    bool anyData = false;

    for (int32_t index = 0; index < (int32_t)mTracks.size(); index++) {
        auto& track = mTracks[index];
        if (!track || !track->active.load()) continue;

        // Routing logic: Track 0 is Voice, others are Music
        if (index == 0 && !includeVoice) continue;
        if (index > 0 && !includeMusic) continue;

        bool shouldPlay = anySolo ? track->solo.load() : !track->muted.load();
        if (!shouldPlay) continue;

        float vol = track->volume.load();
        int64_t offset = track->offset.load();

        // Ducking: se la traccia "superiore" (index-1) sta suonando forte,
        // attenua questa in modo proporzionale a duckAmount (0..1).
        // Soglia: -30 dBFS (ampiezza ≈ 0.0316). Attack 5ms, release 100ms.
        float duckReduction = 0.0f; // 0 = no duck, 1 = completamente duck
        float duckAmt = track->duckAmount.load();
        if (duckAmt > 0.01f && index > 0) {
            auto& target = mTracks[index - 1];
            if (target && target->active.load()) {
                // Calcola envelope RMS del target sul blocco corrente
                int64_t targetOff = target->offset.load();
                float rms = 0.0f;
                int32_t count = 0;
                for (int32_t i = 0; i < numFrames; i++) {
                    int64_t framePos = currentPos + i;
                    if (framePos >= targetOff) {
                        int64_t ti = framePos - targetOff;
                        if (ti >= 0 && ti < (int64_t)target->data.size()) {
                            rms += target->data[ti] * target->data[ti];
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    rms = std::sqrt(rms / count);
                    // Soglia: -30 dBFS = 0.0316 lineare
                    constexpr float kDuckThresh = 0.0316f;
                    if (rms > kDuckThresh) {
                        // Quanto sopra la soglia, normalizzato a [0..1] su 12 dB headroom
                        float over = (rms - kDuckThresh) / kDuckThresh; // 0..N
                        if (over > 4.0f) over = 4.0f;
                        duckReduction = (over / 4.0f) * duckAmt;
                        if (duckReduction > duckAmt) duckReduction = duckAmt;
                    }
                }
                // Smooth attack/release usando lo stato precedente
                float prev = track->duckReduction.load();
                float attackCoef = 0.10f;   // 5ms @ 48k / 240 = ~0.04, ma ok
                float releaseCoef = 0.001f; // ~100ms
                float coef = (duckReduction > prev) ? attackCoef : releaseCoef;
                float smoothed = prev + (duckReduction - prev) * coef;
                track->duckReduction.store(smoothed);
                duckReduction = smoothed;
            }
        }

        for (int32_t i = 0; i < numFrames; i++) {
            int64_t framePos = currentPos + i;
            if (framePos >= offset) {
                int64_t trackIdx = framePos - offset;
                if (trackIdx >= 0 && trackIdx < (int64_t)track->data.size()) {
                    float gain = vol * (1.0f - duckReduction);
                    output[i] += track->data[trackIdx] * gain;
                    anyData = true;
                }
            }
        }
    }

    // Note: We don't advance mGlobalPosition here because we might call mix twice (once for voice, once for music)
    return anyData;
}

bool TrackMixer::mixStereo(float* output, int32_t numFrames, bool includeVoice, bool includeMusic) {
    std::lock_guard<std::mutex> lock(mMutex);
    int64_t currentPos = mGlobalPosition.load();

    bool anySolo = false;
    for (auto& track : mTracks) {
        if (track && track->solo.load()) {
            anySolo = true;
            break;
        }
    }

    memset(output, 0, numFrames * 2 * sizeof(float));
    bool anyData = false;

    for (int32_t index = 0; index < (int32_t)mTracks.size(); index++) {
        auto& track = mTracks[index];
        if (!track || !track->active.load()) continue;

        if (index == 0 && !includeVoice) continue;
        if (index > 0 && !includeMusic) continue;

        bool shouldPlay = anySolo ? track->solo.load() : !track->muted.load();
        if (!shouldPlay) continue;

        float vol = track->volume.load();
        float pan = track->pan.load();
        float leftGain = 1.0f, rightGain = 1.0f;
        panLaw(pan, leftGain, rightGain);
        int64_t offset = track->offset.load();
        // Ducking verso track index-1 (stessa logica del mix mono, smooth
        // attack/release riusando duckReduction atomic).
        float duckAmt = track->duckAmount.load();
        float duckReduction = 0.0f;
        if (duckAmt > 0.01f && index > 0) {
            auto& target = mTracks[index - 1];
            if (target && target->active.load()) {
                int64_t targetOff = target->offset.load();
                float rms = 0.0f;
                int32_t count = 0;
                for (int32_t i = 0; i < numFrames; i++) {
                    int64_t framePos = currentPos + i;
                    if (framePos >= targetOff) {
                        int64_t ti = framePos - targetOff;
                        if (ti >= 0 && ti < (int64_t)target->data.size()) {
                            rms += target->data[ti] * target->data[ti];
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    rms = std::sqrt(rms / count);
                    constexpr float kDuckThresh = 0.0316f;
                    if (rms > kDuckThresh) {
                        float over = (rms - kDuckThresh) / kDuckThresh;
                        if (over > 4.0f) over = 4.0f;
                        duckReduction = (over / 4.0f) * duckAmt;
                        if (duckReduction > duckAmt) duckReduction = duckAmt;
                    }
                }
                float prev = track->duckReduction.load();
                float coef = (duckReduction > prev) ? 0.10f : 0.001f;
                float smoothed = prev + (duckReduction - prev) * coef;
                track->duckReduction.store(smoothed);
                duckReduction = smoothed;
            }
        }

        for (int32_t i = 0; i < numFrames; i++) {
            int64_t framePos = currentPos + i;
            if (framePos >= offset) {
                int64_t trackIdx = framePos - offset;
                if (trackIdx >= 0 && trackIdx < (int64_t)track->data.size()) {
                    float gain = vol * (1.0f - duckReduction);
                    float sample = track->data[trackIdx] * gain;
                    output[2 * i] += sample * leftGain;
                    output[2 * i + 1] += sample * rightGain;
                    anyData = true;
                }
            }
        }
    }

    return anyData;
}

void TrackMixer::setTrackVolume(int32_t index, float volume) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->volume.store(volume);
}

void TrackMixer::setTrackPan(int32_t index, float pan) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->pan.store(pan);
}

void TrackMixer::setTrackMute(int32_t index, bool muted) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->muted.store(muted);
}

void TrackMixer::setTrackSolo(int32_t index, bool solo) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->solo.store(solo);
}

void TrackMixer::setTrackName(int32_t index, const std::string& name) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->name = name;
}

void TrackMixer::setTrackActive(int32_t index, bool active) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->active.store(active);
}

void TrackMixer::setTrackOffset(int32_t index, int64_t offsetFrames) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->offset.store(offsetFrames);
}

void TrackMixer::setTrackDuck(int32_t index, float amount) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    if (amount < 0.0f) amount = 0.0f;
    if (amount > 1.0f) amount = 1.0f;
    mTracks[index]->duckAmount.store(amount);
    LOGI("TrackMixer: track %d duckAmount=%.2f", index, amount);
}

void TrackMixer::clearAll() {
    std::lock_guard<std::mutex> lock(mMutex);
    for (auto& track : mTracks) {
        if (track) {
            track->data.clear();
            track->offset.store(0);
        }
    }
    mGlobalPosition.store(0);
}

void TrackMixer::clearTrack(int32_t index) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (index < 0 || index >= (int32_t)mTracks.size()) return;
    mTracks[index]->data.clear();
    mTracks[index]->offset.store(0);
}

bool TrackMixer::importWav(const char* path, const std::string& name) {
    FILE* f = fopen(path, "rb");
    if (!f) {
        LOGE("Cannot open WAV: %s", path);
        return false;
    }

    char riff[5] = {};
    fread(riff, 1, 4, f);
    riff[4] = 0;
    if (strcmp(riff, "RIFF") != 0) { fclose(f); return false; }

    fseek(f, 8, SEEK_SET);
    char wave[5] = {};
    fread(wave, 1, 4, f);
    wave[4] = 0;
    if (strcmp(wave, "WAVE") != 0) { fclose(f); return false; }

    fseek(f, 12, SEEK_SET);
    char fmtChunk[5] = {};
    fread(fmtChunk, 1, 4, f);
    fmtChunk[4] = 0;
    int32_t chunkSize;
    fread(&chunkSize, 4, 1, f);
    int16_t audioFmt;
    fread(&audioFmt, 2, 1, f);
    int16_t ch;
    fread(&ch, 2, 1, f);
    int32_t sr;
    fread(&sr, 4, 1, f);
    int32_t byteRate;
    fread(&byteRate, 4, 1, f);
    int16_t blockAlign;
    fread(&blockAlign, 2, 1, f);
    int16_t bitsPerSample;
    fread(&bitsPerSample, 2, 1, f);

    fseek(f, 20 + chunkSize, SEEK_SET);

    char dataTag[5] = {};
    while (1) {
        if (fread(dataTag, 1, 4, f) != 4) break;
        dataTag[4] = 0;
        int32_t dataSize;
        fread(&dataSize, 4, 1, f);
        if (strcmp(dataTag, "data") == 0) {
            int32_t numSamples = dataSize / (bitsPerSample / 8);
            std::vector<float> samples(numSamples);
            for (int32_t i = 0; i < numSamples; i++) {
                if (bitsPerSample == 16) {
                    int16_t s;
                    fread(&s, 2, 1, f);
                    samples[i] = s / 32768.0f;
                } else if (bitsPerSample == 24) {
                    int32_t s = 0;
                    unsigned char bytes[3];
                    fread(bytes, 1, 3, f);
                    s = bytes[0] | (bytes[1] << 8) | (bytes[2] << 16);
                    if (s & 0x800000) s |= ~0xFFFFFF;
                    samples[i] = s / 8388608.0f;
                } else if (bitsPerSample == 8) {
                    unsigned char s;
                    fread(&s, 1, 1, f);
                    samples[i] = (s / 128.0f) - 1.0f;
                } else {
                    break;
                }
            }

            int32_t monoFrames = (ch == 2) ? numSamples / 2 : numSamples;
            if (ch == 2) {
                std::vector<float> mono(monoFrames);
                for (int32_t i = 0; i < monoFrames; i++) {
                    mono[i] = (samples[i * 2] + samples[i * 2 + 1]) * 0.5f;
                }
                samples = std::move(mono);
            }

            // The engine timeline is expressed in 48 kHz frames. Convert imported
            // material once here so offsets and total duration use one time base.
            if (sr > 0 && sr != 48000 && !samples.empty()) {
                const double ratio = 48000.0 / static_cast<double>(sr);
                const int32_t resampledFrames = std::max<int32_t>(1, static_cast<int32_t>(samples.size() * ratio));
                std::vector<float> resampled(resampledFrames);
                for (int32_t i = 0; i < resampledFrames; i++) {
                    const double sourcePos = i / ratio;
                    const int32_t sourceIndex = static_cast<int32_t>(sourcePos);
                    const int32_t nextIndex = std::min(sourceIndex + 1, static_cast<int32_t>(samples.size()) - 1);
                    const float fraction = static_cast<float>(sourcePos - sourceIndex);
                    resampled[i] = samples[sourceIndex] * (1.0f - fraction) + samples[nextIndex] * fraction;
                }
                samples = std::move(resampled);
                LOGI("Resampled track %s: %d Hz -> 48000 Hz", path, sr);
            }

            int32_t trackIndex = addTrack(name.empty() ? path : name);
            if (trackIndex < 0) { fclose(f); return false; }

            auto* track = getTrack(trackIndex);
            if (track) {
                track->data = std::move(samples);
                track->active.store(true);
            }

            LOGI("Imported WAV: %s (%d frames, %dHz, %d-bit, %dch) -> Track %d",
                 path, monoFrames, sr, bitsPerSample, ch, trackIndex);
            fclose(f);
            return true;
        } else {
            fseek(f, dataSize, SEEK_CUR);
        }
    }

    fclose(f);
    return false;
}

int64_t TrackMixer::getTotalDuration() const {
    std::lock_guard<std::mutex> lock(mMutex);
    int64_t maxFrames = 0;
    for (auto& track : mTracks) {
        if (track && !track->data.empty()) {
            int64_t endPos = track->offset.load() + (int64_t)track->data.size();
            if (endPos > maxFrames) maxFrames = endPos;
        }
    }
    return maxFrames;
}

void TrackMixer::panLaw(float pan, float& leftGain, float& rightGain) {
    float angle = (pan + 1.0f) * 0.5f * (float)M_PI * 0.5f;
    leftGain = std::cos(angle);
    rightGain = std::sin(angle);
}
