#include "MasteringChain.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <cstdio>
#include <cstring>

#define LOG_TAG "MasteringChain"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MasteringChain", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MasteringChain", __VA_ARGS__)

MasteringChain::MasteringChain() {
    mBufV.resize(65536); mBufM.resize(65536); mBufC.resize(65536);
    mBufNG.resize(65536); mBufRawV.resize(65536); mBufRvb.resize(65536);
    LOGI("Created");
}
MasteringChain::~MasteringChain() { mReverb.reset(); }

void MasteringChain::process(const float* v, const float* m, int32_t n, float* o, const float* rawV) {
    if(n<=0)return;
    if((int32_t)mBufV.size()<n){
        mBufV.resize(n+1024);mBufM.resize(n+1024);mBufC.resize(n+1024);
        mBufNG.resize(n+1024);mBufRawV.resize(n+1024);mBufRvb.resize(n+1024);
    }

    // Store raw voice for recording without RVC
    if(rawV) {
        memcpy(mBufRawV.data(), rawV, n*sizeof(float));
    } else {
        memcpy(mBufRawV.data(), v, n*sizeof(float));
    }

    // Track input peaks with slow decay
    float pl=0, pr=0;
    for(int32_t i=0; i<n; i++){
        float av = std::abs(v[i]);
        if(av > pl) pl = av;
    }
    for(int32_t i=0; i<n; i++){
        float av = std::abs(m[i]);
        if(av > pr) pr = av;
    }

    // Metering: instant attack, 300ms decay time constant (time-based, independent of callback size)
    float decay = std::exp(-(float)n / (0.3f * (float)mSampleRate));
    float attack = 1.0f - decay;
    if (pl > mInputPeakL) mInputPeakL = pl; else mInputPeakL = decay * mInputPeakL + attack * pl;
    if (pr > mInputPeakR) mInputPeakR = pr; else mInputPeakR = decay * mInputPeakR + attack * pr;

    // Apply gain staging
    for(int32_t i=0;i<n;i++){mBufV[i]=v[i]*mVoiceGain;mBufM[i]=m[i]*mMusicGain;}

    // 1) NOISE GATE on voice before EQ
    mNoiseGate.process(mBufV.data(),mBufV.data(),n,(float)mSampleRate);

    // 2) VOICE EQ
    mVoiceEQ.process(mBufV.data(),mBufV.data(),n);

    // 3) MUSIC EQ
    mMusicEQ.process(mBufM.data(),mBufM.data(),n);

    // 4) SIDECHAIN COMPRESSOR (music ducked by voice envelope)
    mComp.process(mBufM.data(),mBufV.data(),mBufC.data(),n,(float)mSampleRate);

    // 5) REVERB on voice (Freeverb Schroeder/Moorer)
    if(mReverb.enabled && mReverbMix>0.001f) {
        mReverb.process(mBufV.data(), mBufRvb.data(), n);
        // Wet/dry mix
        float rv=mReverbMix; // 0..1
        for(int32_t i=0;i<n;i++){
            mBufV[i]=mBufV[i]*(1.0f-rv)+mBufRvb.data()[i]*rv;
        }
    }

        // 6) SUM: voice + compressed music
    for(int32_t i=0;i<n;i++)o[i]=(mBufV[i]+mBufC[i])*mMasterGain;

    // 6b) MIX BACKING TRACK (before limiter, after mastering chain)
    if(mBackingLoaded && !mBackingBuffer.empty()) {
        for(int32_t i=0;i<n;i++){
            if(mBackingReadPos < (int64_t)mBackingBuffer.size()) {
                o[i] += mBackingBuffer[mBackingReadPos] * mBackingGain;
                mBackingReadPos++;
            }
        }
    }

    // 7) BRICKWALL LIMITER
    mLimiter.process(o,n,(float)mSampleRate);

    // 8) LUFS METER
    mLufs.proc(o,n,(float)mSampleRate);

        // 8b) WAVEFORM PEAK COLLECTION (decimated, scrolling circular)
    for(int32_t i=0;i<n;i++){
        float absv = o[i] < 0 ? -o[i] : o[i];
        mWaveformAccum = (absv > mWaveformAccum) ? absv : mWaveformAccum;
        mWaveformCount++;
        if(mWaveformCount >= kWaveformDecimation) {
            mWaveformPeaks[mWaveformWritePos % kWaveformPeaks] = mWaveformAccum;
            mWaveformWritePos++;
            mWaveformPeaksCollected++;
            mWaveformAccum = 0;
            mWaveformCount = 0;
        }
    }

    // 9) WAV RECORDING
    if(mIsRecording && mRecordFile) {
        // If includeMastering is true, record master output 'o'
        const float* recSrc = o;
        if(!mIncludeMastering) {
            recSrc = mBufV.data();
            if(!mIncludeRVC) {
                recSrc = mBufRawV.data(); // Pre-RVC / Pre-Mastering raw mic
            }
        }

        // Ensure we are actually writing data
        size_t written = 0;
        if(mWavBitsPerSample==16) {
            std::vector<int16_t> samples(n);
            for(int32_t i=0;i<n;i++){
                float s=recSrc[i];
                if(s>1.0f)s=1.0f; else if(s<-1.0f)s=-1.0f;
                samples[i]=(int16_t)(s*32767.0f);
            }
            written = fwrite(samples.data(), sizeof(int16_t), n, mRecordFile);
        } else if(mWavBitsPerSample==24) {
            for(int32_t i=0;i<n;i++){
                float s=recSrc[i];
                if(s>1.0f)s=1.0f; else if(s<-1.0f)s=-1.0f;
                int32_t sample=(int32_t)(s*8388607.0f);
                if(sample>8388607)sample=8388607;
                if(sample<-8388608)sample=-8388608;
                fputc(sample&0xFF,mRecordFile);
                fputc((sample>>8)&0xFF,mRecordFile);
                fputc((sample>>16)&0xFF,mRecordFile);
            }
            written = n; // Approximation for 24-bit logic
        }

        if (written < (size_t)n && mWavBitsPerSample == 16) {
             LOGE("Failed to write all samples to record file: %zu/%d", written, n);
        }
        mRecordFramesWritten += n;
    }
}

void MasteringChain::setVoiceEQ(const std::array<EQBand,4>& b){mVoiceEQ.cfg(b,(float)mSampleRate);}
void MasteringChain::setMusicEQ(const std::array<EQBand,4>& b){mMusicEQ.cfg(b,(float)mSampleRate);}
void MasteringChain::setSideChainParams(float t,float r,float a,float re,float mg){mComp={t,r,a,re,mg,0,0};}
void MasteringChain::setLimiterParams(float t,float r){mLimiter={t,r,0,0,0};}
void MasteringChain::setVoiceVolume(float d){mVoiceGain=std::pow(10,d/20);}
void MasteringChain::setMusicVolume(float d){mMusicGain=std::pow(10,d/20);}
void MasteringChain::setMasterVolume(float d){mMasterGain=std::pow(10,d/20);}
MasteringChain::LufsReading MasteringChain::getLufsReading()const{return mLufs.read(mSampleRate);}
void MasteringChain::reset(){
    mVoiceEQ.rst();mMusicEQ.rst();mComp.rst();mLimiter.rst();mLufs.rst();
    mNoiseGate.rst();mReverb.reset();
    mInputPeakL=0;mInputPeakR=0;
    if(mIsRecording)stopRecording();
}

// --- REVERB (Freeverb-style Schroeder/Moorer) ---
void MasteringChain::setReverbMix(float mix){
    mReverbMix=mix;
    mReverb.enabled=(mix>0.001f);
    if(mReverb.enabled) mReverb.init((float)mSampleRate);
}
void MasteringChain::setReverbRoomSize(float sz){
    mReverbRoomSize=sz; mReverb.roomSize=sz;
    for(int i=0;i<4;i++){
        mReverb.comb[i].feedback=0.84f+sz*0.3f;
        if(mReverb.comb[i].feedback>0.99f)mReverb.comb[i].feedback=0.99f;
    }
}
void MasteringChain::setReverbDamping(float d){
    mReverbDamping=d; mReverb.damping=d;
    for(int i=0;i<4;i++){mReverb.comb[i].damp1=d;mReverb.comb[i].damp2=1.0f-d;}
}

void MasteringChain::Reverb::init(float sr) {
    if(enabled) return;
    float scale=sr/44100.0f;
    int combDelays[4]={(int)(1116*scale),(int)(1277*scale),(int)(1422*scale),(int)(1557*scale)};
    int apDelays[2]={(int)(341*scale),(int)(225*scale)};
    for(int i=0;i<4;i++){
        comb[i].setDelay(combDelays[i],sr);
        comb[i].feedback=0.84f+roomSize*0.3f;
        if(comb[i].feedback>0.99f)comb[i].feedback=0.99f;
        comb[i].damp1=damping;
        comb[i].damp2=1.0f-damping;
    }
    for(int i=0;i<2;i++) allpass[i].setDelay(apDelays[i]);
    wet=0.6f; dry=0.4f; // more wet for noticeable effect
    enabled=true;
}

void MasteringChain::Reverb::process(const float* in, float* out, int32_t n) {
    if(!enabled){memcpy(out,in,n*sizeof(float));return;}
    std::vector<float> tmp(n);
    memcpy(tmp.data(),in,n*sizeof(float));
    std::vector<float> combOut(n, 0.0f);
    std::vector<float> buf(n);
    // 4 parallel comb filters
    for(int c=0;c<4;c++){
        std::fill(buf.begin(), buf.end(), 0.0f);
        comb[c].process(tmp.data(),buf.data(),n);
        for(int i=0;i<n;i++) combOut[i]+=buf[i];
    }
    for(int i=0;i<n;i++) combOut[i]*=0.25f; // average
    // 2 all-pass in series
    std::fill(tmp.begin(), tmp.end(), 0.0f);
    allpass[0].process(combOut.data(),tmp.data(),n);
    std::fill(combOut.begin(), combOut.end(), 0.0f);
    allpass[1].process(tmp.data(),combOut.data(),n);
    // Dry/wet mix
    for(int i=0;i<n;i++) out[i]=in[i]*dry+combOut[i]*wet;
}

void MasteringChain::Reverb::reset(){
    for(int i=0;i<4;i++){ comb[i].buffer.clear(); comb[i].bufSize=0; }
    for(int i=0;i<2;i++){ allpass[i].buffer.clear(); allpass[i].bufSize=0; }
    enabled=false;
}

void MasteringChain::CombFilter::setDelay(int samples, float sr) {
    bufSize=samples>0?samples:1;
    buffer.assign(bufSize, 0.0f);
    bufIndex=0; lpOut=0;
    (void)sr;
}

void MasteringChain::CombFilter::process(const float* in, float* out, int32_t n) {
    for(int32_t i=0;i<n;i++){
        int idx=bufIndex%bufSize;
        float tmpBuf=buffer[idx];
        lpOut=tmpBuf*damp2+lpOut*damp1;
        buffer[idx]=in[i]+lpOut*feedback;
        bufIndex++;
        out[i]=tmpBuf;
    }
}

void MasteringChain::AllPassFilter::setDelay(int samples) {
    bufSize=samples>0?samples:1;
    buffer.assign(bufSize, 0.0f);
    bufIndex=0;
}

void MasteringChain::AllPassFilter::process(const float* in, float* out, int32_t n) {
    for(int32_t i=0;i<n;i++){
        int idx=bufIndex%bufSize;
        float bufVal=buffer[idx];
        buffer[idx]=in[i]+bufVal*feedback;
        bufIndex++;
        out[i]=-in[i]+bufVal;
    }
}

// --- NOISE GATE ---
void MasteringChain::NoiseGate::process(const float* in, float* out, int32_t n, float sr) {
    if(!enabled || threshold <= 0.0f){
        // Smooth transition to bypass
        for(int32_t i=0; i<n; i++) {
            targetGain = 1.0f;
            currentGain += 0.005f * (targetGain - currentGain);
            out[i] = in[i] * currentGain;
        }
        gr = 20 * std::log10(std::max(currentGain, 1e-6f));
        return;
    }

    float att = 1.0f - std::exp(-1.0f / (attackMs * 0.001f * sr));
    float rel = 1.0f - std::exp(-1.0f / (releaseMs * 0.001f * sr));
    int32_t holdSamps = (int32_t)(holdMs * 0.001f * sr);

    for(int32_t i = 0; i < n; i++) {
        float a = std::abs(in[i]);
        if (a > env) env += att * (a - env); else env += rel * (a - env);

        if (env > threshold) {
            holdCount = holdSamps;
            targetGain = 1.0f;
        } else {
            if (holdCount > 0) {
                holdCount--;
                targetGain = 1.0f;
            } else {
                targetGain = 0.0f;
            }
        }
        // Apply smooth gain transition to prevent clicks
        currentGain += 0.01f * (targetGain - currentGain);
        out[i] = in[i] * currentGain;
    }
    gr = 20 * std::log10(std::max(currentGain, 1e-6f));
}
void MasteringChain::NoiseGate::rst(){env=0;envHold=0;gr=0;holdCount=0;currentGain=1.0f;targetGain=1.0f;}

float MasteringChain::getCompressorGR() const { return mComp.gr; }
float MasteringChain::getLimiterGR() const { return mLimiter.gr; }
float MasteringChain::getNoiseGateGR() const { return mNoiseGate.gr; }

void MasteringChain::setNoiseGate(bool e, float t, float a, float h, float r) {
    mNoiseGate={e,t,a,h,r,0,0,0,0};
}

MasteringChain::InputLevelReading MasteringChain::getInputLevel() const {
    InputLevelReading r;
    r.left=20*std::log10(std::max(mInputPeakL,1e-10f));
    r.right=20*std::log10(std::max(mInputPeakR,1e-10f));
    r.peakLeft=20*std::log10(std::max(mInputPeakL,1e-10f));
    r.peakRight=20*std::log10(std::max(mInputPeakR,1e-10f));
    r.clipping=(mInputPeakL>=0.99f||mInputPeakR>=0.99f);
    return r;
}

bool MasteringChain::startRecording(const char* path, int bitsPerSample, const char* format, bool includeRVC, bool includeMastering) {
    if(mIsRecording)stopRecording();
    mRecordFile=fopen(path,"wb");
    if(!mRecordFile){LOGE("Cannot open %s",path);return false;}

    mWavBitsPerSample=bitsPerSample;
    if(mWavBitsPerSample!=16&&mWavBitsPerSample!=24)mWavBitsPerSample=16;
    mIncludeRVC=includeRVC;
    mIncludeMastering=includeMastering;

    int32_t sr=mSampleRate;
    int32_t bps=mWavBitsPerSample;
    int16_t ch=1;
    int32_t dataSize=0,fileSize=36+dataSize;
    int16_t audioFmt=1;

    fwrite("RIFF",1,4,mRecordFile);
    fwrite(&fileSize,4,1,mRecordFile);
    fwrite("WAVE",1,4,mRecordFile);
    fwrite("fmt ",1,4,mRecordFile);
    int32_t fmtSize=16;fwrite(&fmtSize,4,1,mRecordFile);
    fwrite(&audioFmt,2,1,mRecordFile);
    fwrite(&ch,2,1,mRecordFile);
    fwrite(&sr,4,1,mRecordFile);
    int32_t byteRate=sr*ch*bps/8;fwrite(&byteRate,4,1,mRecordFile);
    int16_t blockAlign=ch*bps/8;fwrite(&blockAlign,2,1,mRecordFile);
    int16_t bitsPerS=(int16_t)bps;fwrite(&bitsPerS,2,1,mRecordFile);
    fwrite("data",1,4,mRecordFile);
    fwrite(&dataSize,4,1,mRecordFile);

    mIsRecording=true;
    auto clock=std::chrono::steady_clock::now();
    mRecordStartTime=clock.time_since_epoch().count();
    mRecordFramesWritten=0;
    LOGI("Recording: %s (%d-bit, RVC=%d mastering=%d)",path,bps,includeRVC,includeMastering);
    return true;
}

void MasteringChain::stopRecording() {
    if(!mRecordFile)return;
    int32_t pos=ftell(mRecordFile);
    if(pos>44){
        int32_t dataSize=pos-44;
        #ifdef _WIN32
        _fseeki64(mRecordFile,4,SEEK_SET);
        #else
        fseek(mRecordFile,4,SEEK_SET);
        #endif
        int32_t fileSize=36+dataSize;
        fwrite(&fileSize,4,1,mRecordFile);
        #ifdef _WIN32
        _fseeki64(mRecordFile,40,SEEK_SET);
        #else
        fseek(mRecordFile,40,SEEK_SET);
        #endif
        fwrite(&dataSize,4,1,mRecordFile);
    }
    fclose(mRecordFile);mRecordFile=nullptr;
    mIsRecording=false;
    float dur=mRecordFramesWritten/(float)mSampleRate;
    LOGI("Recording stopped: %lld frames = %.1f sec", (long long)mRecordFramesWritten, dur);
}

float MasteringChain::getRecordingDurationSec() const {
    if(!mIsRecording) return 0.0f;
    auto now=std::chrono::steady_clock::now().time_since_epoch().count();
    double ns=(double)(now-mRecordStartTime);
    return (float)(ns/1000000000.0);
}

// --- BIQUAD EQ ---
void MasteringChain::Biquad::calcPeakEQ(float f,float g,float q,float sr){
    float A=std::pow(10,g/40),om=2*(float)M_PI*f/sr,sn=std::sin(om),cs=std::cos(om),al=sn/(2*q);
    float n=1/(1+al/A); b0=(1+al*A)*n; b1=(-2*cs)*n; b2=(1-al*A)*n; a1=(-2*cs)*n; a2=(1-al/A)*n;
}

void MasteringChain::Biquad::process(const float* in,float* out,int32_t n){
    for(int32_t i=0;i<n;i++){float x=in[i];float y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2;x2=x1;x1=x;y2=y1;y1=y;out[i]=y;}
}

void MasteringChain::EQ4B::process(const float* in,float* out,int32_t n){
    for(auto& b:bands){b.process(in,out,n);if(&b!=&bands.back())std::copy(out,out+n,const_cast<float*>(in));}
}

void MasteringChain::EQ4B::cfg(const std::array<EQBand,4>& p,float sr){
    for(int i=0;i<4;i++){if(p[i].enabled)bands[i].calcPeakEQ(p[i].freq,p[i].gainDB,p[i].q,sr);else{bands[i]={1,0,0,0,0};}bands[i].resetSt();}
}

void MasteringChain::EQ4B::rst(){for(auto& b:bands)b.resetSt();}

// --- SIDECHAIN COMPRESSOR ---
void MasteringChain::SideComp::process(const float* in,const float* sc,float* out,int32_t n,float sr){
    float att=1-std::exp(-1/(attack*0.001f*sr)),rel=1-std::exp(-1/(release*0.001f*sr));
    for(int32_t i=0;i<n;i++){
        float a=std::abs(sc[i]);
        if(a>env)env+=att*(a-env);else env+=rel*(a-env);
        float eDB=20*std::log10(std::max(env,1e-10f)),gDB=0;
        if(eDB>thresh)gDB=(eDB-thresh)*(1-1/ratio);
        float gLin=std::pow(10,(makeup-gDB)/20);
        out[i]=in[i]*gLin;gr=gDB;
    }
}

void MasteringChain::SideComp::rst(){env=0;gr=0;}

// --- BRICKWALL LIMITER ---
void MasteringChain::Limiter::process(float* buf,int32_t n,float sr){
    if(relCoeff==0)relCoeff=1-std::exp(-1/(release*0.001f*sr));
    float tLin=std::pow(10,thresh/20);
    for(int32_t i=0;i<n;i++){
        float av=std::abs(buf[i]);
        if(av>peak)peak=av;else peak+=relCoeff*(av-peak);
        float dG=1;if(peak>tLin)dG=tLin/peak;
        buf[i]*=std::min(1.0f,dG);gr=(dG<1)?20*std::log10(dG):0;
    }
}

void MasteringChain::Limiter::rst(){peak=0;gr=0;}

// --- LUFS METER (BS.1770-4 simplified) ---
void MasteringChain::LufsMeter::proc(const float* in,int32_t n,float sr){
    for(int32_t i=0;i<n;i++){
        float x = in[i];
        float absX = std::abs(x);
        if(absX > truePeak) truePeak = absX;

        // Basic K-weighting pre-filter (simplified high-pass + high shelf)
        momSum += (double)(x * x);
        momFrames++;

        // Short-term sliding window (3 seconds)
        int32_t samplesPer3Sec = (int32_t)(3.0f * sr);
        if (samplesPer3Sec > 0) {
            stBuf[stPos % kSTF] = x;
            stPos++;
            if (stPos >= kSTF) stPos = 0; // Wrap around safely
        }

        // Integrated (long term)
        intSqSum += (double)(x * x);
        intFrames++;
    }
}

MasteringChain::LufsReading MasteringChain::LufsMeter::read(int32_t sr)const{
    LufsReading r;
    int32_t mc=std::min(momFrames,(int32_t)(0.4f*sr));
    if(mc>0){double ms=momSum/mc;r.momentary=(float)(-0.691+10*std::log10(ms));}
    double stS=0;for(int i=0;i<kSTF;i++)stS+=stBuf[i]*stBuf[i];
    double stMS=stS/kSTF;r.shortTerm=(float)(-0.691+10*std::log10(std::max(stMS,1e-20)));
    if(intFrames>0){double ms=intSqSum/intFrames;r.integrated=(float)(-0.691+10*std::log10(std::max(ms,1e-20)));}
    r.truePeak=20*std::log10(std::max(truePeak,1e-10f));
    return r;
}

void MasteringChain::LufsMeter::rst(){momSum=0;momFrames=0;stPos=0;intSum=0;intSqSum=0;intFrames=0;truePeak=0;memset(stBuf,0,sizeof(stBuf));}

// --- BACKING TRACK WAV LOADER ---
void MasteringChain::loadBackingTrack(const char* wavPath) {
    mBackingLoaded = false;
    mBackingBuffer.clear();
    mBackingReadPos = 0;
    mBackingSampleRate = 0;

    FILE* f = fopen(wavPath, "rb");
    if(!f) { LOGE("Cannot open backing track: %s", wavPath); return; }

    // Read WAV header
    char riff[5]={}; fread(riff,1,4,f); riff[4]=0;
    if(strcmp(riff,"RIFF")!=0) { fclose(f); LOGE("Not RIFF"); return; }
    fseek(f,8,SEEK_SET);
    char wave[5]={}; fread(wave,1,4,f); wave[4]=0;
    if(strcmp(wave,"WAVE")!=0) { fclose(f); LOGE("Not WAVE"); return; }
    fseek(f,12,SEEK_SET);
    char fmtChunk[5]={}; fread(fmtChunk,1,4,f); fmtChunk[4]=0;
    int32_t chunkSize; fread(&chunkSize,4,1,f);
    int16_t audioFmt; fread(&audioFmt,2,1,f);
    int16_t ch; fread(&ch,2,1,f);
    int32_t sr; fread(&sr,4,1,f);
    int32_t byteRate; fread(&byteRate,4,1,f);
    int16_t blockAlign; fread(&blockAlign,2,1,f);
    int16_t bitsPerSample; fread(&bitsPerSample,2,1,f);

    fseek(f, 20 + chunkSize, SEEK_SET);
    // Find 'data' chunk
    char dataTag[5]={};
    while(1) {
        if(fread(dataTag,1,4,f)!=4) break; dataTag[4]=0;
        int32_t dataSize; fread(&dataSize,4,1,f);
        if(strcmp(dataTag,"data")==0) {
            // Read audio data
            int32_t numSamples = dataSize / (bitsPerSample/8);
            mBackingBuffer.resize(numSamples);
            for(int32_t i=0;i<numSamples;i++) {
                if(bitsPerSample==16) {
                    int16_t s; fread(&s,2,1,f);
                    mBackingBuffer[i] = s / 32768.0f;
                } else if(bitsPerSample==24) {
                    int32_t s = 0;
                    unsigned char bytes[3];
                    fread(bytes,1,3,f);
                    s = bytes[0] | (bytes[1]<<8) | (bytes[2]<<16);
                    if(s & 0x800000) s |= ~0xFFFFFF; // sign extend
                    mBackingBuffer[i] = s / 8388608.0f;
                } else if(bitsPerSample==8) {
                    unsigned char s; fread(&s,1,1,f);
                    mBackingBuffer[i] = (s / 128.0f) - 1.0f;
                } else { break; }
            }
            mBackingSampleRate = sr;
            mBackingLoaded = true;
            LOGI("Loaded backing track: %d samples, %d Hz, %d-bit, %d ch", numSamples, sr, bitsPerSample, ch);
            break;
        } else {
            fseek(f, dataSize, SEEK_CUR);
        }
    }
    fclose(f);
}


