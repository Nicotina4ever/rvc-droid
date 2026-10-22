#ifndef RVC_TRACK_MIXER_H
#define RVC_TRACK_MIXER_H

#include <cstdint>
#include <vector>
#include <atomic>
#include <mutex>
#include <string>

class TrackMixer {
public:
    static constexpr int32_t kMaxTracks = 32;
    static constexpr int32_t kRingBufferCapacity = 131072; // ~2.7s @ 48kHz

    struct Track {
        std::string name;
        std::vector<float> data;
        std::atomic<int64_t> position{0};
        std::atomic<int64_t> offset{0};      // Start time in frames
        std::atomic<float> volume{1.0f};
        std::atomic<float> pan{0.0f};
        std::atomic<bool> muted{false};
        std::atomic<bool> solo{false};
        std::atomic<bool> active{false};
        // Ducking: amount 0..1, target = traccia con trackIndex-1.
        // Quando il segnale target supera la soglia, questa traccia viene
        // attenuata. Stato interno: env (envelope follower), reduction.
        std::atomic<float> duckAmount{0.0f};
        std::atomic<float> duckEnv{0.0f};     // envelope del target
        std::atomic<float> duckReduction{0.0f}; // attenuazione corrente 0..1
        bool isEmpty() const { return data.empty(); }
        int64_t availableFrames(int64_t currentEnginePos) const {
            if (currentEnginePos < offset) return 0;
            int64_t posInTrack = currentEnginePos - offset;
            if (posInTrack >= (int64_t)data.size()) return 0;
            return (int64_t)data.size() - posInTrack;
        }
    };

    TrackMixer();
    ~TrackMixer();

    // Add a new track, returns track index or -1 if full
    int32_t addTrack(const std::string& name = "");
    // Remove a track
    void removeTrack(int32_t index);
    // Get number of tracks
    int32_t getTrackCount() const;
    // Get track by index (thread-safe, returns nullptr if invalid)
    Track* getTrack(int32_t index);

    // Write audio data into a track's ring buffer
    void writeTrack(int32_t index, const float* data, int32_t numFrames);
    // Mix specific tracks into output buffer (mono)
    bool mix(float* output, int32_t numFrames, bool includeVoice = true, bool includeMusic = true);
    // Mix into interleaved stereo buffer (LRLR...). Per-track pan is applied with
    // equal-power law. Left/right gains are clamped to [0, 1] on the consumer side.
    bool mixStereo(float* output, int32_t numFrames, bool includeVoice = true, bool includeMusic = true);

    void resetPosition() { mGlobalPosition.store(0); }
    void setPosition(int64_t frames) { mGlobalPosition.store(frames); }
    int64_t getPosition() const { return mGlobalPosition.load(); }

    // Track control
    void setTrackVolume(int32_t index, float volume);
    void setTrackPan(int32_t index, float pan);
    void setTrackMute(int32_t index, bool muted);
    void setTrackSolo(int32_t index, bool solo);
    void setTrackName(int32_t index, const std::string& name);
    void setTrackActive(int32_t index, bool active);
    void setTrackOffset(int32_t index, int64_t offsetFrames);
    void setTrackDuck(int32_t index, float amount);

    // Clear all tracks
    void clearAll();
    // Clear a specific track data
    void clearTrack(int32_t index);

    // Import a WAV file as a new track
    bool importWav(const char* path, const std::string& name = "");
    // Import a WAV file into an existing track (used to arm/record a specific track)
    bool importWavAt(const char* path, int32_t index, const std::string& name = "");

    // Get the maximum length of the project in frames
    int64_t getTotalDuration() const;

private:
    bool importWavInternal(const char* path, const std::string& name, int32_t targetIndex);
    mutable std::mutex mMutex;
    std::vector<std::unique_ptr<Track>> mTracks;
    std::atomic<int64_t> mGlobalPosition{0};

    // Pan law: equal power pan
    static void panLaw(float pan, float& leftGain, float& rightGain);
};

#endif
