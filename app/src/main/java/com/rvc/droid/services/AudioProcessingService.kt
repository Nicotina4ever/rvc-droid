package com.rvc.droid.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.rvc.droid.MainActivity
import com.rvc.droid.R
import com.rvc.droid.audio.NativeAudioBridge

class AudioProcessingService : Service() {

    companion object {
        private const val TAG = "AudioProcessingService"
        private const val CHANNEL_ID = "rvc_audio_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.rvc.droid.services.START_PROCESSING"
        const val ACTION_STOP = "com.rvc.droid.services.STOP_PROCESSING"
        const val ACTION_UPDATE_PARAMS = "com.rvc.droid.services.UPDATE_PARAMS"
        const val EXTRA_RVC_ENABLED = "rvc_enabled"
        const val EXTRA_FORMANT_SHIFT = "formant_shift"
        const val EXTRA_F0_METHOD = "f0_method"
        const val EXTRA_INDEX_RATE = "index_rate"
        const val EXTRA_PROTECTION = "protection"
        const val EXTRA_NOISE_SCALE = "noise_scale"
        const val EXTRA_PITCH_SHIFT = "pitch_shift"
    }

    private val binder = LocalBinder()
    private var isProcessing = false

    inner class LocalBinder : Binder() {
        fun getService(): AudioProcessingService = this@AudioProcessingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val rvcEnabled = intent.getBooleanExtra(EXTRA_RVC_ENABLED, false)
                startProcessing(rvcEnabled)
            }
            ACTION_STOP -> stopProcessing()
            ACTION_UPDATE_PARAMS -> {
                val formant = intent.getIntExtra(EXTRA_FORMANT_SHIFT, 0)
                val f0 = intent.getIntExtra(EXTRA_F0_METHOD, 0)
                val index = intent.getFloatExtra(EXTRA_INDEX_RATE, 0.5f)
                val protect = intent.getFloatExtra(EXTRA_PROTECTION, 0.5f)
                val noise = intent.getFloatExtra(EXTRA_NOISE_SCALE, 0.666f)
                updateRvcParams(formant, f0, index, protect, noise)
            }
        }
        return START_NOT_STICKY
    }

    fun startProcessing(rvcEnabled: Boolean = false) {
        if (isProcessing) return
        try {
            if (!NativeAudioBridge.isInitialized) {
                NativeAudioBridge.initialize()
            }
            NativeAudioBridge.startAudio(rvcEnabled)
            isProcessing = true
            startForeground(NOTIFICATION_ID, buildNotification("Processing audio"))
            Log.i(TAG, "Processing started (RVC=$rvcEnabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start processing: ${e.message}")
        }
    }

    fun stopProcessing() {
        if (!isProcessing) return
        try {
            NativeAudioBridge.stopAudio()
            isProcessing = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.i(TAG, "Processing stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop processing: ${e.message}")
        }
    }

    fun updateRvcParams(formantShift: Int, f0Method: Int, indexRate: Float, protection: Float, noiseScale: Float) {
        try {
            NativeAudioBridge.setRvcParams(formantShift, f0Method, indexRate, protection, noiseScale)
            Log.i(TAG, "RVC params updated: formant=$formantShift f0=$f0Method idx=$indexRate prot=$protection noise=$noiseScale")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update params: ${e.message}")
        }
    }

    fun isProcessing(): Boolean = isProcessing

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "RVC audio processing service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioProcessingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("RVC-Droid")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopProcessing()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }
}
