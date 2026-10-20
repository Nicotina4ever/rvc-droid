// Lightweight host-side test for TrackMixer::mixStereo pan law.
// Compiled only when RVC_BUILD_TESTS=ON (host build, no Android deps).
// android/log.h is stubbed via the test include path.
#include "TrackMixer.h"
#include <cassert>
#include <cmath>
#include <cstdio>

static bool approx(float a, float b, float tol = 1e-3f) {
    return std::fabs(a - b) <= tol;
}

int main() {
    TrackMixer mixer;
    int32_t idx = mixer.addTrack("mono");
    assert(idx == 0);
    auto* t = mixer.getTrack(0);
    assert(t != nullptr);

    // 1 second of constant 1.0 signal at 48kHz.
    constexpr int32_t kSr = 48000;
    std::vector<float> oneSec(kSr, 1.0f);
    t->data = oneSec;
    t->volume.store(1.0f);

    float buf[8] = {};

    // Pan -1.0 (full left).
    t->pan.store(-1.0f);
    mixer.resetPosition();
    mixer.setPosition(0);
    bool any = mixer.mixStereo(buf, 4, true, true);
    assert(any);
    assert(approx(buf[0], 1.0f, 1e-2f));
    assert(approx(buf[1], 0.0f, 1e-2f));
    assert(approx(buf[2], 1.0f, 1e-2f));
    assert(approx(buf[3], 0.0f, 1e-2f));

    // Pan 0.0 (center, equal power = both ~0.707).
    t->pan.store(0.0f);
    mixer.resetPosition();
    mixer.setPosition(0);
    mixer.mixStereo(buf, 4, true, true);
    assert(approx(buf[0], 0.7071f, 1e-2f));
    assert(approx(buf[1], 0.7071f, 1e-2f));

    // Pan +1.0 (full right).
    t->pan.store(1.0f);
    mixer.resetPosition();
    mixer.setPosition(0);
    mixer.mixStereo(buf, 4, true, true);
    assert(approx(buf[0], 0.0f, 1e-2f));
    assert(approx(buf[1], 1.0f, 1e-2f));

    // Muted track must not produce data.
    t->pan.store(0.0f);
    t->muted.store(true);
    mixer.resetPosition();
    mixer.setPosition(0);
    for (auto& v : buf) v = 0.5f;  // pre-fill
    any = mixer.mixStereo(buf, 4, true, true);
    // anyData will be false, buf values remain untouched.
    assert(!any);
    for (auto v : buf) assert(approx(v, 0.5f));

    std::printf("TrackMixer pan tests: OK\n");
    return 0;
}
