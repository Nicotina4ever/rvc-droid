#include <jni.h>
#include <android/log.h>
#include "AudioEngine.h"
#include "InferenceEngine.h"
#include "MasteringChain.h"
#include "TrackMixer.h"
#include "NativeLog.h"

#define LOG_TAG "JNIBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static AudioEngine* gAE = nullptr;
static InferenceEngine* gIE = nullptr;
static MasteringChain* gMC = nullptr;
static TrackMixer* gTM = nullptr;

struct OfflineProgressContext {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

static void reportOfflineProgress(float progress, void* userData) {
    auto* context = static_cast<OfflineProgressContext*>(userData);
    if (context && context->callback && context->method) {
        context->env->CallVoidMethod(context->callback, context->method, static_cast<jfloat>(progress));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetLogPath(JNIEnv* e, jobject, jstring path) {
    const char* c = e->GetStringUTFChars(path, nullptr);
    if (c) {
        nativeLogSetPath(c);
        e->ReleaseStringUTFChars(path, c);
    }
}

// --- ENGINE LIFECYCLE ---
extern "C" JNIEXPORT jlong JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeCreateEngine(JNIEnv* env, jobject thiz) {
    LOGI("Creating native engines...");
    logFile("nativeCreateEngine: creating engines");
    delete gAE; delete gIE; delete gMC; delete gTM;
    gAE = new AudioEngine();
    gIE = new InferenceEngine();
    gMC = new MasteringChain();
    gTM = new TrackMixer();
    // Connect into audio pipeline
    gAE->setMasteringChain(gMC);
    gAE->setTrackMixer(gTM);
    gAE->setRvcCallback([](const float* in, float* out, int32_t n, int32_t sr) {
        if (gIE && gIE->isSynthLoaded()) gIE->process(in, n, sr, 0, out);
        else memcpy(out, in, n * sizeof(float));
    });
    LOGI("Engines created: AE=%p IE=%p MC=%p TM=%p", (void*)gAE, (void*)gIE, (void*)gMC, (void*)gTM);
    logFile("engines created AE=%p IE=%p", (void*)gAE, (void*)gIE);
    return reinterpret_cast<jlong>(gAE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeRelease(JNIEnv* env, jobject thiz, jlong ptr) {
    if(gAE){gAE->stop();delete gAE;gAE=nullptr;}
    if(gIE){gIE->release();delete gIE;gIE=nullptr;}
    if(gMC){delete gMC;gMC=nullptr;}
    if(gTM){delete gTM;gTM=nullptr;}
    LOGI("Engines released");
}

// --- AUDIO ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeStartAudio(JNIEnv* e, jobject t, jlong p, jboolean rvc) {
    return gAE ? (gAE->start(rvc==JNI_TRUE) ? JNI_TRUE : JNI_FALSE) : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeStartMonitoring(JNIEnv* e, jobject t, jlong p) {
    return gAE ? (gAE->startMonitoring() ? JNI_TRUE : JNI_FALSE) : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeStopAudio(JNIEnv*, jobject, jlong) { if(gAE) gAE->stop(); }
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeIsMonitoring(JNIEnv*, jobject, jlong) { return gAE&&gAE->isMonitoring()?JNI_TRUE:JNI_FALSE; }
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeEnableRVC(JNIEnv*, jobject, jlong, jboolean e) { if(gAE) gAE->enableRVC(e==JNI_TRUE); }
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeIsRvcEnabled(JNIEnv*, jobject, jlong) { return gAE&&gAE->isRvcEnabled()?JNI_TRUE:JNI_FALSE; }
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeIsRunning(JNIEnv*, jobject, jlong) { return gAE&&gAE->isRunning()?JNI_TRUE:JNI_FALSE; }

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetMonitoringGain(JNIEnv*, jobject, jlong, jfloat g) {
    if(gAE) gAE->setMonitoringGain(g);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetInputDevice(JNIEnv*, jobject, jlong, jint deviceId) {
    if(gAE) gAE->setInputDeviceId(deviceId);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetOutputDevice(JNIEnv*, jobject, jlong, jint deviceId) {
    if(gAE) gAE->setOutputDeviceId(deviceId);
}

// --- MODELS ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeLoadSynthModel(JNIEnv* e, jobject t, jlong p, jstring path) {
    const char* c = e->GetStringUTFChars(path, nullptr); if(!c)return JNI_FALSE;
    logFile("loadSynthModel: %s", c);
    bool ok = gIE && gIE->loadSynthesizerModel(std::string(c));
    logFile("loadSynthModel: %s -> %s", c, ok ? "OK" : "FAILED");
    e->ReleaseStringUTFChars(path, c); return ok?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeLoadContentVec(JNIEnv* e, jobject t, jlong p, jstring path) {
    const char* c = e->GetStringUTFChars(path, nullptr); if(!c)return JNI_FALSE;
    logFile("loadContentVec: %s", c);
    bool ok = gIE && gIE->loadContentVecModel(std::string(c));
    logFile("loadContentVec: %s -> %s", c, ok ? "OK" : "FAILED");
    e->ReleaseStringUTFChars(path, c); return ok?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeLoadF0Model(JNIEnv* e, jobject t, jlong p, jstring path) {
    const char* c = e->GetStringUTFChars(path, nullptr); if(!c)return JNI_FALSE;
    logFile("loadF0Model: %s", c);
    bool ok = gIE && gIE->loadF0Model(std::string(c));
    logFile("loadF0Model: %s -> %s", c, ok ? "OK" : "FAILED");
    e->ReleaseStringUTFChars(path, c); return ok?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetSynthModelName(JNIEnv* e, jobject, jlong) {
    return e->NewStringUTF(gIE&&gIE->isSynthLoaded()?gIE->getSynthModelName().c_str():"");
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetVecModelName(JNIEnv* e, jobject, jlong) {
    return e->NewStringUTF(gIE&&gIE->isVecLoaded()?gIE->getVecModelName().c_str():"");
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeAreModelsLoaded(JNIEnv*, jobject, jlong) {
    if(!gIE) return JNI_FALSE;
    return (gIE->isSynthLoaded() && gIE->isVecLoaded() && gIE->isF0Loaded()) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeLoadIndex(JNIEnv* e, jobject t, jlong p, jstring path) {
    const char* c = e->GetStringUTFChars(path, nullptr); if(!c)return JNI_FALSE;
    bool ok = gIE && gIE->loadIndex(std::string(c));
    e->ReleaseStringUTFChars(path, c); return ok?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetIndexName(JNIEnv* e, jobject, jlong) {
    return e->NewStringUTF(gIE&&gIE->isIndexLoaded()?gIE->getIndexName().c_str():"");
}

// --- RVC PARAMETERS ---
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetRvcParams(JNIEnv*, jobject, jlong,
    jint formantShift, jint f0Method, jfloat indexRate, jfloat protection, jfloat noiseScale) {
    if(gIE){
        gIE->setFormantShift((int)formantShift);
        gIE->setF0Method((int)f0Method);
        gIE->setIndexRate((float)indexRate);
        gIE->setProtection((float)protection);
        gIE->setNoiseScale((float)noiseScale);
        LOGI("RVC params: fs=%d f0=%d idx=%.2f prot=%.2f noise=%.2f",
            (int)formantShift, (int)f0Method, (float)indexRate, (float)protection, (float)noiseScale);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetRmsMixRate(JNIEnv*, jobject, jlong, jfloat r) {
    if(gIE) gIE->setRmsMixRate((float)r);
}

// --- PROCESSING ---
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeProcessAudio(JNIEnv* e, jobject, jlong,
    jfloatArray in, jint sr, jint ps, jfloatArray out) {
    if(!gIE)return;
    jfloat* id=e->GetFloatArrayElements(in,nullptr);
    jfloat* od=e->GetFloatArrayElements(out,nullptr);
    jsize n=e->GetArrayLength(in);
    if(gIE->isSynthLoaded()) gIE->process(id,n,sr,ps,od);
    else gIE->pitchShiftOnly(id,n,sr,ps,od);
    e->ReleaseFloatArrayElements(in,id,JNI_ABORT);
    e->ReleaseFloatArrayElements(out,od,0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativePitchShift(JNIEnv* e, jobject, jlong,
    jfloatArray in, jint sr, jint semis, jfloatArray out) {
    if(!gIE)return;
    jfloat* id=e->GetFloatArrayElements(in,nullptr);
    jfloat* od=e->GetFloatArrayElements(out,nullptr);
    jsize n=e->GetArrayLength(in);
    gIE->pitchShiftOnly(id,n,sr,semis,od);
    e->ReleaseFloatArrayElements(in,id,JNI_ABORT);
    e->ReleaseFloatArrayElements(out,od,0);
}

// --- MASTERING ---
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeMasterProcess(JNIEnv* e, jobject, jlong,
    jfloatArray voice, jfloatArray music, jfloatArray output) {
    if(!gMC)return;
    jfloat* v=e->GetFloatArrayElements(voice,nullptr);
    jfloat* m=e->GetFloatArrayElements(music,nullptr);
    jfloat* o=e->GetFloatArrayElements(output,nullptr);
    jsize n=e->GetArrayLength(voice);
    gMC->process(v,m,n,o);
    e->ReleaseFloatArrayElements(voice,v,JNI_ABORT);
    e->ReleaseFloatArrayElements(music,m,JNI_ABORT);
    e->ReleaseFloatArrayElements(output,o,0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetVoiceEQ(JNIEnv* e, jobject, jlong,
    jfloatArray freqs, jfloatArray gains, jfloatArray qs) {
    if(!gMC)return;
    jfloat* f=e->GetFloatArrayElements(freqs,nullptr);
    jfloat* g=e->GetFloatArrayElements(gains,nullptr);
    jfloat* q=e->GetFloatArrayElements(qs,nullptr);
    std::array<MasteringChain::EQBand,4> bands;
    for(int i=0;i<4;i++){bands[i].freq=f[i];bands[i].gainDB=g[i];bands[i].q=q[i];bands[i].enabled=true;}
    gMC->setVoiceEQ(bands);
    e->ReleaseFloatArrayElements(freqs,f,JNI_ABORT);
    e->ReleaseFloatArrayElements(gains,g,JNI_ABORT);
    e->ReleaseFloatArrayElements(qs,q,JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetMusicEQ(JNIEnv* e, jobject, jlong,
    jfloatArray freqs, jfloatArray gains, jfloatArray qs) {
    if(!gMC)return;
    jfloat* f=e->GetFloatArrayElements(freqs,nullptr);
    jfloat* g=e->GetFloatArrayElements(gains,nullptr);
    jfloat* q=e->GetFloatArrayElements(qs,nullptr);
    std::array<MasteringChain::EQBand,4> bands;
    for(int i=0;i<4;i++){bands[i].freq=f[i];bands[i].gainDB=g[i];bands[i].q=q[i];bands[i].enabled=true;}
    gMC->setMusicEQ(bands);
    e->ReleaseFloatArrayElements(freqs,f,JNI_ABORT);
    e->ReleaseFloatArrayElements(gains,g,JNI_ABORT);
    e->ReleaseFloatArrayElements(qs,q,JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetSideChain(JNIEnv*, jobject, jlong,
    jfloat thresh, jfloat ratio, jfloat attack, jfloat release, jfloat makeup) {
    if(gMC) gMC->setSideChainParams(thresh,ratio,attack,release,makeup);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetLimiter(JNIEnv*, jobject, jlong,
    jfloat thresh, jfloat releaseMs) {
    if(gMC) gMC->setLimiterParams(thresh,releaseMs);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetVolume(JNIEnv*, jobject, jlong,
    jfloat voiceDB, jfloat musicDB, jfloat masterDB) {
    if(gMC){gMC->setVoiceVolume(voiceDB);gMC->setMusicVolume(musicDB);gMC->setMasterVolume(masterDB);}
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetLufs(JNIEnv* e, jobject, jlong) {
    jfloatArray res = e->NewFloatArray(5);
    if(!gMC){e->SetFloatArrayRegion(res,0,5,new jfloat[5]{-70,-70,-70,-70,0});return res;}
    auto l=gMC->getLufsReading();
    jfloat d[5]={l.momentary,l.shortTerm,l.integrated,l.truePeak,l.range};
    e->SetFloatArrayRegion(res,0,5,d);
    return res;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeMasteringReset(JNIEnv*, jobject, jlong) {
    if(gMC) gMC->reset();
}

// --- NOISE GATE ---
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetNoiseGate(JNIEnv*, jobject, jlong,
    jboolean enabled, jfloat threshold, jfloat attackMs, jfloat holdMs, jfloat releaseMs) {
    if(gMC) gMC->setNoiseGate(enabled==JNI_TRUE, threshold, attackMs, holdMs, releaseMs);
}

// --- REVERB setters ---
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetReverbMix(JNIEnv*, jobject, jlong, jfloat mix) {
    if(gMC) gMC->setReverbMix(mix);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetReverbRoomSize(JNIEnv*, jobject, jlong, jfloat sz) {
    if(gMC) gMC->setReverbRoomSize(sz);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetReverbDamping(JNIEnv*, jobject, jlong, jfloat d) {
    if(gMC) gMC->setReverbDamping(d);
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetReverbMix(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getReverbMix() : 0.0f;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetReverbRoomSize(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getReverbRoomSize() : 0.5f;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetReverbDamping(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getReverbDamping() : 0.5f;
}
// --- GR METERS ---
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetCompressorGR(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getCompressorGR() : 0.0f;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetLimiterGR(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getLimiterGR() : 0.0f;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetNoiseGateGR(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getNoiseGateGR() : 0.0f;
}
// --- INPUT LEVEL ---
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetInputLevel(JNIEnv* e, jobject, jlong) {
    jfloatArray res = e->NewFloatArray(5);
    if(!gMC && !gAE){jfloat d[5]={-80,-80,-80,-80,0}; e->SetFloatArrayRegion(res,0,5,d); return res;}

    // Get direct peak from AudioEngine input callback (most reliable for mic level)
    float aePeakDB = -80.0f;
    if (gAE) aePeakDB = gAE->getInputPeakDB();

    // Get MasteringChain level (includes music/channel separation)
    float mcLeftDB = -80.0f, mcRightDB = -80.0f, mcPeakL = -80.0f, mcPeakR = -80.0f;
    bool clipping = false;
    if (gMC) {
        auto l = gMC->getInputLevel();
        mcLeftDB = l.left; mcRightDB = l.right;
        mcPeakL = l.peakLeft; mcPeakR = l.peakRight;
        clipping = l.clipping;
    }

    // For mono mic input, mirror the same live level to both channels so the meter is readable.
    float leftDB = (aePeakDB > mcLeftDB) ? aePeakDB : mcLeftDB;
    float peakL = (aePeakDB > mcPeakL) ? aePeakDB : mcPeakL;
    float rightDB = leftDB;
    float peakR = peakL;

    jfloat d[5]={leftDB, rightDB, peakL, peakR, clipping?1.0f:0.0f};
    e->SetFloatArrayRegion(res,0,5,d);
    return res;
}
// --- RECORDING (start/stop) ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeStartRecording(JNIEnv* e, jobject, jlong,
    jstring path, jstring format, jboolean rvc, jboolean mastering) {
    if(!gMC) return JNI_FALSE;
    const char* cPath = e->GetStringUTFChars(path,nullptr);
    if(!cPath) return JNI_FALSE;
    int bitsPerSample = 16;
    if(format != nullptr) {
        const char* cFmt = e->GetStringUTFChars(format,nullptr);
        if(cFmt) {
            if(strstr(cFmt,"24")!=nullptr) bitsPerSample=24;
            else if(strstr(cFmt,"32")!=nullptr) bitsPerSample=24; // 32 not supported, fallback to 24
            e->ReleaseStringUTFChars(format,cFmt);
        }
    }
    bool ok = gMC->startRecording(cPath, bitsPerSample, nullptr, rvc==JNI_TRUE, mastering==JNI_TRUE);
    e->ReleaseStringUTFChars(path,cPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeStopRecording(JNIEnv*, jobject, jlong) {
    if(gMC) gMC->stopRecording();
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeIsRecording(JNIEnv*, jobject, jlong) {
    return (gMC && gMC->isRecording()) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetRecordingDuration(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getRecordingDurationSec() : 0.0f;
}

// --- WAVEFORM PEAKS ---
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetWaveformPeaks(JNIEnv* e, jobject, jlong) {
    jfloatArray res = e->NewFloatArray(gMC ? gMC->getWaveformPeakCount() : 4096);
    if(gMC) e->SetFloatArrayRegion(res, 0, gMC->getWaveformPeakCount(), gMC->getWaveformPeaks());
    return res;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetWaveformTotalPeaks(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getWaveformTotalPeaks() : 0;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetWaveformWritePos(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getWaveformWritePos() : 0;
}

// --- BACKING TRACK ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeLoadBackingTrack(JNIEnv* e, jobject, jlong, jstring path) {
    if(!gMC) return JNI_FALSE;
    const char* c = e->GetStringUTFChars(path, nullptr);
    if(!c) return JNI_FALSE;
    gMC->loadBackingTrack(c);
    e->ReleaseStringUTFChars(path, c);
    return gMC->isBackingLoaded() ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetBackingTrackGain(JNIEnv*, jobject, jlong, jfloat g) {
    if(gMC) gMC->setBackingTrackGain(g);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetBackingTrackPosition(JNIEnv*, jobject, jlong, jlong pos) {
    if(gMC) gMC->setBackingTrackPosition((int64_t)pos);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeIsBackingLoaded(JNIEnv*, jobject, jlong) {
    return gMC && gMC->isBackingLoaded() ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetBackingTrackGain(JNIEnv*, jobject, jlong) {
    return gMC ? gMC->getBackingGain() : 0.5f;
}

// --- OFFLINE PROCESSOR ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeProcessOffline(JNIEnv* e, jobject, jlong, jstring inPath, jstring outPath, jint pitchShift, jobject callback) {
    if(!gIE) return JNI_FALSE;
    const char* in = e->GetStringUTFChars(inPath, nullptr);
    const char* out = e->GetStringUTFChars(outPath, nullptr);
    if(!in || !out) {
        if(in) e->ReleaseStringUTFChars(inPath, in);
        if(out) e->ReleaseStringUTFChars(outPath, out);
        return JNI_FALSE;
    }
    logFile("processOffline START: %s -> %s (shift=%d)", in, out, (int)pitchShift);
    bool ok = false;
    OfflineProgressContext progressContext{e, nullptr, nullptr};
    if (callback) {
        progressContext.callback = e->NewGlobalRef(callback);
        jclass callbackClass = e->GetObjectClass(callback);
        progressContext.method = e->GetMethodID(callbackClass, "onProgress", "(F)V");
        e->DeleteLocalRef(callbackClass);
    }
    try {
        ok = gIE->processOffline(std::string(in), std::string(out), pitchShift,
                                 progressContext.method ? reportOfflineProgress : nullptr,
                                 &progressContext);
    } catch (const std::exception& e) {
        logFile("processOffline EXCEPTION: %s", e.what());
    } catch (...) {
        logFile("processOffline unknown exception");
    }
    logFile("processOffline END: %s", ok ? "OK" : "FAILED");
    if (progressContext.callback) e->DeleteGlobalRef(progressContext.callback);
    e->ReleaseStringUTFChars(inPath, in);
    e->ReleaseStringUTFChars(outPath, out);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// --- TRACK MIXER ---
extern "C" JNIEXPORT jint JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeAddTrack(JNIEnv* e, jobject, jlong, jstring name) {
    if(!gTM) return -1;
    const char* c = name ? e->GetStringUTFChars(name, nullptr) : "";
    int32_t idx = gTM->addTrack(c ? c : "");
    if(name && c) e->ReleaseStringUTFChars(name, c);
    return idx;
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeRemoveTrack(JNIEnv*, jobject, jlong, jint index) {
    if(gTM) gTM->removeTrack(index);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetTrackCount(JNIEnv*, jobject, jlong) {
    return gTM ? gTM->getTrackCount() : 0;
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetMixerPositionMs(JNIEnv*, jobject, jlong) {
    if (!gTM) return 0L;
    // Engine timeline is in 48 kHz frames.
    return (gTM->getPosition() * 1000L) / 48000L;
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeGetMixerDurationMs(JNIEnv*, jobject, jlong) {
    if (!gTM) return 0L;
    return (gTM->getTotalDuration() * 1000L) / 48000L;
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetTrackVolume(JNIEnv*, jobject, jlong, jint index, jfloat vol) {
    if(gTM) gTM->setTrackVolume(index, vol);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetTrackPan(JNIEnv*, jobject, jlong, jint index, jfloat pan) {
    if(gTM) gTM->setTrackPan(index, pan);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetTrackMute(JNIEnv*, jobject, jlong, jint index, jboolean mute) {
    if(gTM) gTM->setTrackMute(index, mute==JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetTrackSolo(JNIEnv*, jobject, jlong, jint index, jboolean solo) {
    if(gTM) gTM->setTrackSolo(index, solo==JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetTrackOffset(JNIEnv*, jobject, jlong, jint index, jlong offset) {
    if(gTM) gTM->setTrackOffset(index, (int64_t)offset);
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetTrackDuck(JNIEnv*, jobject, jlong, jint index, jfloat amount) {
    if(gTM) gTM->setTrackDuck(index, (float)amount);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeImportTrack(JNIEnv* e, jobject, jlong, jstring path, jstring name) {
    if(!gTM) return JNI_FALSE;
    const char* cPath = e->GetStringUTFChars(path, nullptr);
    const char* cName = name ? e->GetStringUTFChars(name, nullptr) : "";
    bool ok = gTM->importWav(cPath, cName ? cName : "");
    if(cPath) e->ReleaseStringUTFChars(path, cPath);
    if(name && cName) e->ReleaseStringUTFChars(name, cName);
    return ok ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeClearTracks(JNIEnv*, jobject, jlong) {
    if(gTM) gTM->clearAll();
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeResetMixerPosition(JNIEnv*, jobject, jlong) {
    if(gTM) gTM->resetPosition();
}
extern "C" JNIEXPORT void JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeSetMixerPosition(JNIEnv*, jobject, jlong, jlong frames) {
    if(gTM) gTM->setPosition(frames);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeRenderMixer(JNIEnv* e, jobject, jlong, jstring outPath) {
    if(!gTM || !gMC) return JNI_FALSE;
    const char* out = e->GetStringUTFChars(outPath, nullptr);
    if(!out) return JNI_FALSE;

    int64_t totalFrames = gTM->getTotalDuration();
    if(totalFrames <= 0) { e->ReleaseStringUTFChars(outPath, out); return JNI_FALSE; }

    // Start recording on MasteringChain (offline mode)
    if(!gMC->startRecording(out, 16, nullptr, false, true)) {
        e->ReleaseStringUTFChars(outPath, out); return JNI_FALSE;
    }

    gTM->resetPosition();
    int32_t chunkSize = 4096;
    std::vector<float> vBuf(chunkSize), mBuf(chunkSize), oBuf(chunkSize);

    int64_t processed = 0;
    while(processed < totalFrames) {
        int32_t n = (int32_t)std::min((int64_t)chunkSize, totalFrames - processed);
        gTM->mix(vBuf.data(), n, true, false); // Track 0 -> Voice
        gTM->mix(mBuf.data(), n, false, true); // Tracks 1+ -> Music
        gMC->process(vBuf.data(), mBuf.data(), n, oBuf.data());
        processed += n;
        gTM->setPosition(processed);
    }

    gMC->stopRecording();
    e->ReleaseStringUTFChars(outPath, out);
    return JNI_TRUE;
}

static bool writeStereoWavInterleaved(const char* path, const float* interleaved, int32_t numFrames, int32_t sampleRate) {
    FILE* f = fopen(path, "wb");
    if (!f) { LOGE("Cannot open %s", path); return false; }
    int16_t bitsPerSample = 16;
    int16_t channels = 2;
    int32_t byteRate = sampleRate * channels * bitsPerSample / 8;
    int16_t blockAlign = channels * bitsPerSample / 8;
    int32_t dataSize = numFrames * channels * (bitsPerSample / 8);
    int32_t fileSize = 36 + dataSize;
    fwrite("RIFF", 1, 4, f);
    fwrite(&fileSize, 4, 1, f);
    fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f);
    int32_t fmtSize = 16; fwrite(&fmtSize, 4, 1, f);
    int16_t audioFmt = 1; fwrite(&audioFmt, 2, 1, f);
    fwrite(&channels, 2, 1, f);
    fwrite(&sampleRate, 4, 1, f);
    fwrite(&byteRate, 4, 1, f);
    fwrite(&blockAlign, 2, 1, f);
    fwrite(&bitsPerSample, 2, 1, f);
    fwrite("data", 1, 4, f);
    fwrite(&dataSize, 4, 1, f);
    for (int32_t i = 0; i < numFrames * 2; i++) {
        float s = interleaved[i];
        if (s > 1.0f) s = 1.0f; else if (s < -1.0f) s = -1.0f;
        int16_t sample = (int16_t)std::lrintf(s * 32767.0f);
        fwrite(&sample, 2, 1, f);
    }
    fclose(f);
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rvc_droid_audio_NativeAudioBridge_nativeRenderMixerStereo(JNIEnv* e, jobject, jlong, jstring outPath, jint sampleRate) {
    if (!gTM) return JNI_FALSE;
    const char* out = e->GetStringUTFChars(outPath, nullptr);
    if (!out) return JNI_FALSE;
    int64_t totalFrames = gTM->getTotalDuration();
    if (totalFrames <= 0) { e->ReleaseStringUTFChars(outPath, out); return JNI_FALSE; }

    gTM->resetPosition();
    int32_t chunkSize = 4096;
    std::vector<float> sBuf(chunkSize * 2);

    FILE* f = fopen(out, "wb");
    if (!f) { e->ReleaseStringUTFChars(outPath, out); return JNI_FALSE; }
    int16_t bitsPerSample = 16;
    int16_t channels = 2;
    int32_t sr = sampleRate > 0 ? (int32_t)sampleRate : 48000;
    int32_t byteRate = sr * channels * bitsPerSample / 8;
    int16_t blockAlign = channels * bitsPerSample / 8;
    int32_t dataSize = 0; int32_t fileSize = 36 + dataSize;
    int16_t audioFmt = 1;
    fwrite("RIFF", 1, 4, f);
    fwrite(&fileSize, 4, 1, f);
    fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f);
    int32_t fmtSize = 16; fwrite(&fmtSize, 4, 1, f);
    fwrite(&audioFmt, 2, 1, f);
    fwrite(&channels, 2, 1, f);
    fwrite(&sr, 4, 1, f);
    fwrite(&byteRate, 4, 1, f);
    fwrite(&blockAlign, 2, 1, f);
    fwrite(&bitsPerSample, 2, 1, f);
    fwrite("data", 1, 4, f);
    fwrite(&dataSize, 4, 1, f);

    int64_t processed = 0;
    while (processed < totalFrames) {
        int32_t n = (int32_t)std::min((int64_t)chunkSize, totalFrames - processed);
        gTM->mixStereo(sBuf.data(), n, true, true);
        for (int32_t i = 0; i < n * 2; i++) {
            float s = sBuf[i];
            if (s > 1.0f) s = 1.0f; else if (s < -1.0f) s = -1.0f;
            int16_t sample = (int16_t)std::lrintf(s * 32767.0f);
            fwrite(&sample, 2, 1, f);
        }
        processed += n;
        gTM->setPosition(processed);
    }

    int32_t pos = (int32_t)ftell(f);
    if (pos > 44) {
        int32_t ds = pos - 44;
        int32_t fs = 36 + ds;
#ifdef _WIN32
        _fseeki64(f, 4, SEEK_SET); fwrite(&fs, 4, 1, f);
        _fseeki64(f, 40, SEEK_SET); fwrite(&ds, 4, 1, f);
#else
        fseek(f, 4, SEEK_SET); fwrite(&fs, 4, 1, f);
        fseek(f, 40, SEEK_SET); fwrite(&ds, 4, 1, f);
#endif
    }
    fclose(f);
    e->ReleaseStringUTFChars(outPath, out);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    installCrashHandlers();
    LOGI("JNI_OnLoad: rvc_native loaded"); return JNI_VERSION_1_6;
}
extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*) {
    if(gAE){gAE->stop();delete gAE;gAE=nullptr;}
    if(gIE){gIE->release();delete gIE;gIE=nullptr;}
    if(gMC){delete gMC;gMC=nullptr;}
    if(gTM){delete gTM;gTM=nullptr;}
    LOGI("JNI_OnUnload: cleaned");
}
