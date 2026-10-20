#ifndef RVC_DSP_UTILS_H
#define RVC_DSP_UTILS_H
#include <cstdint>
#include <vector>
namespace DSPUtils {
    void applyHannWindow(float* buf, int32_t sz);
    void applyHammingWindow(float* buf, int32_t sz);
    void applyBlackmanWindow(float* buf, int32_t sz);
    void computeFFT(const float* in, int32_t sz, float* re, float* im);
    void computeIFFT(const float* re, const float* im, int32_t sz, float* out);
    void normalizePeak(float* buf, int32_t sz, float target=0.99f);
    float getPeakLevel(const float* buf, int32_t sz);
    float getRMSLevel(const float* buf, int32_t sz);
    void applyLowPass(float* buf, int32_t sz, float cutoff);
    void applyHighPass(float* buf, int32_t sz, float cutoff);
    void applyPreEmphasis(float* buf, int32_t sz, float coeff=0.97f);
    void mixBuffers(const float* a, const float* b, float ga, float gb, float* out, int32_t sz);
    void floatToInt16(const float* in, int16_t* out, int32_t sz);
    void int16ToFloat(const int16_t* in, float* out, int32_t sz);
}
#endif
