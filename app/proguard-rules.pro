-keep class com.rvc.droid.audio.NativeAudioBridge { *; }
-keep class com.rvc.droid.services.AudioProcessingService { *; }
-keep class com.rvc.droid.RVCCoreApplication { *; }
-keep class com.rvc.droid.MainActivity { *; }
-keep class ai.onnxruntime.** { *; }
-keep class org.json.** { *; }
-keep class com.rvc.droid.audio.WavEditor { *; }

# JNI reflection callbacks: il C++ chiama GetMethodID("onProgress", "(F)V")
-keep,allowobfuscation interface com.rvc.droid.audio.OfflineProgressCallback {
    public void onProgress(float);
}
-keep class com.rvc.droid.audio.OfflineProgressCallback$* { *; }

# Qualsiasi classe che il JNI usa via reflection deve preservare nome + membro
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

