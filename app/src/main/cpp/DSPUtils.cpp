#include "DSPUtils.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace DSPUtils {

// --- WINDOWING ---
void applyHannWindow(float* b, int32_t s) {
    for (int i = 0; i < s; i++)
        b[i] *= 0.5f * (1.0f - cosf(2.0f * (float)M_PI * i / (s - 1)));
}
void applyHammingWindow(float* b, int32_t s) {
    for (int i = 0; i < s; i++)
        b[i] *= 0.54f - 0.46f * cosf(2.0f * (float)M_PI * i / (s - 1));
}
void applyBlackmanWindow(float* b, int32_t s) {
    for (int i = 0; i < s; i++)
        b[i] *= 0.42f - 0.5f * cosf(2.0f * (float)M_PI * i / (s - 1))
                      + 0.08f * cosf(4.0f * (float)M_PI * i / (s - 1));
}

// --- COOLEY-TUKEY RADIX-2 FFT ---
static void fftBitReverse(float* re, float* im, int32_t n) {
    for (int32_t i = 1, j = 0; i < n; i++) {
        int32_t bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
            std::swap(re[i], re[j]);
            std::swap(im[i], im[j]);
        }
    }
}

void computeFFT(const float* in, int32_t s, float* re, float* im) {
    int32_t n = 1;
    while (n < s) n <<= 1;
    if (n > s) n = s;

    for (int32_t i = 0; i < n; i++) { re[i] = in[i]; im[i] = 0.0f; }
    for (int32_t i = n; i < s; i++) { re[i] = 0.0f; im[i] = 0.0f; }

    fftBitReverse(re, im, n);

    for (int32_t len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * (float)M_PI / (float)len;
        float wrCos = cosf(ang), wrSin = sinf(ang);
        for (int32_t i = 0; i < n; i += len) {
            float wR = 1.0f, wI = 0.0f;
            for (int32_t j = 0; j < len / 2; j++) {
                int32_t u = i + j;
                int32_t t = i + j + len / 2;
                float tR = wR * re[t] - wI * im[t];
                float tI = wR * im[t] + wI * re[t];
                re[t] = re[u] - tR;
                im[t] = im[u] - tI;
                re[u] = re[u] + tR;
                im[u] = im[u] + tI;
                float newWR = wR * wrCos - wI * wrSin;
                float newWI = wR * wrSin + wI * wrCos;
                wR = newWR;
                wI = newWI;
            }
        }
    }
}

void computeIFFT(const float* re, const float* im, int32_t s, float* out) {
    int32_t n = 1;
    while (n < s) n <<= 1;
    if (n > s) n = s;

    std::vector<float> tmpRe(n), tmpIm(n);
    for (int32_t i = 0; i < n; i++) { tmpRe[i] = re[i]; tmpIm[i] = -im[i]; }

    fftBitReverse(tmpRe.data(), tmpIm.data(), n);

    for (int32_t len = 2; len <= n; len <<= 1) {
        float ang = 2.0f * (float)M_PI / (float)len;
        float wrCos = cosf(ang), wrSin = sinf(ang);
        for (int32_t i = 0; i < n; i += len) {
            float wR = 1.0f, wI = 0.0f;
            for (int32_t j = 0; j < len / 2; j++) {
                int32_t u = i + j;
                int32_t t = i + j + len / 2;
                float tR = wR * tmpRe[t] - wI * tmpIm[t];
                float tI = wR * tmpIm[t] + wI * tmpRe[t];
                tmpRe[t] = tmpRe[u] - tR;
                tmpIm[t] = tmpIm[u] - tI;
                tmpRe[u] = tmpRe[u] + tR;
                tmpIm[u] = tmpIm[u] + tI;
                float newWR = wR * wrCos - wI * wrSin;
                float newWI = wR * wrSin + wI * wrCos;
                wR = newWR;
                wI = newWI;
            }
        }
    }

    float invN = 1.0f / (float)n;
    for (int32_t i = 0; i < n; i++) out[i] = tmpRe[i] * invN;
    for (int32_t i = n; i < s; i++) out[i] = 0.0f;
}

// --- LEVEL METERS ---
void normalizePeak(float* b, int32_t s, float t) {
    float p = getPeakLevel(b, s);
    if (p > 1e-10f) { float g = t / p; for (int i = 0; i < s; i++) b[i] *= g; }
}
float getPeakLevel(const float* b, int32_t s) {
    float p = 0; for (int32_t i = 0; i < s; i++) p = std::max(p, std::abs(b[i])); return p;
}
float getRMSLevel(const float* b, int32_t s) {
    float ss = 0; for (int32_t i = 0; i < s; i++) ss += b[i] * b[i]; return std::sqrt(ss / s);
}

// --- FILTERS ---
void applyLowPass(float* b, int32_t s, float c) {
    if (c <= 0 || c >= 1) return;
    float a = 1.0f / (1.0f + 1.0f / (2.0f * (float)M_PI * c));
    float p = b[0];
    for (int32_t i = 1; i < s; i++) { p += a * (b[i] - p); b[i] = p; }
}
void applyHighPass(float* b, int32_t s, float c) {
    if (c <= 0 || c >= 1) return;
    float RC = 1.0f / (2.0f * (float)M_PI * c);
    float a = RC / (RC + 1.0f);
    float p = b[0], po = b[0];
    for (int32_t i = 1; i < s; i++) { po = a * (po + b[i] - p); p = b[i]; b[i] = po; }
}
void applyPreEmphasis(float* b, int32_t s, float c) {
    for (int32_t i = s - 1; i > 0; i--) b[i] = b[i] - c * b[i - 1];
}

// --- MIX / FORMAT ---
void mixBuffers(const float* a, const float* b, float ga, float gb, float* o, int32_t s) {
    for (int32_t i = 0; i < s; i++) o[i] = std::clamp(a[i] * ga + b[i] * gb, -1.0f, 1.0f);
}
void floatToInt16(const float* in, int16_t* out, int32_t s) {
    for (int32_t i = 0; i < s; i++) {
        float c = std::clamp(in[i], -1.0f, 1.0f);
        out[i] = (int16_t)(c * 32767.0f);
    }
}
void int16ToFloat(const int16_t* in, float* out, int32_t s) {
    for (int32_t i = 0; i < s; i++) out[i] = in[i] / 32768.0f;
}

}
