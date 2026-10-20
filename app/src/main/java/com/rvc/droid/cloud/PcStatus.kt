package com.rvc.droid.cloud

import org.json.JSONObject

/** Parametri RVC correnti sul PC (dal file status.json). */
data class RemoteAudioParams(
    val modelPath: String = "",
    val indexPath: String = "",
    val pitch: Int = 0,
    val method: String = "rmvpe",
    val protect: Float = 0.33f,
    val indexRate: Float = 0.3f,
    val modelVersion: String = "v2",
)

/**
 * Stato del PC letto da Dropbox (`/TestRVC/status.json`).
 * Il PC lo scrive ogni pochi secondi: nessuna connessione in ingresso.
 */
data class RemotePcStatus(
    val schema: Int = 1,
    val timestamp: Long = 0L,
    val iso: String = "",
    val host: String = "",
    val engineAvailable: Boolean = false,
    val device: String = "",
    val loadedModel: String = "",
    val queueQueued: Int = 0,
    val queueRunning: Int = 0,
    val queueDone: Int = 0,
    val queueFailed: Int = 0,
    val watcherRunning: Boolean = false,
    val watcherDir: String = "",
    val lastConfigAt: Float = 0f,
    val lastConfigApplied: Int = 0,
    val lastConfigError: String = "",
    val audio: RemoteAudioParams = RemoteAudioParams(),
) {
    fun ageSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        if (timestamp <= 0L) return Long.MAX_VALUE
        return ((nowMs / 1000L) - timestamp).coerceAtLeast(0L)
    }

    fun isOnline(nowMs: Long = System.currentTimeMillis()): Boolean = ageSeconds(nowMs) <= 60L
}

object PcStatusParser {
    fun parse(text: String): RemotePcStatus? {
        return try {
            val root = JSONObject(text)
            val providers = root.optJSONObject("providers") ?: JSONObject()
            val engine = providers.optJSONObject("engine") ?: JSONObject()
            val queue = providers.optJSONObject("queue") ?: JSONObject()
            val watcher = providers.optJSONObject("watcher") ?: JSONObject()
            val configSync = providers.optJSONObject("config") ?: JSONObject()
            val audio = root.optJSONObject("audio") ?: JSONObject()
            val engineModel = engine.optString("model_path", engine.optString("model", ""))
            val audioModel = audio.optString("model_path", "")
            RemotePcStatus(
                schema = root.optInt("schema", 1),
                timestamp = root.optLong("timestamp", 0L),
                iso = root.optString("iso", ""),
                host = root.optString("host", ""),
                engineAvailable = engine.optBoolean("available", false),
                device = engine.optString("device", ""),
                loadedModel = engineModel.ifBlank { audioModel },
                queueQueued = queue.optInt("queued", 0),
                queueRunning = queue.optInt("running", 0),
                queueDone = queue.optInt("done", 0),
                queueFailed = queue.optInt("failed", 0),
                watcherRunning = watcher.optBoolean("running", false),
                watcherDir = watcher.optString("dir", ""),
                lastConfigAt = configSync.optLong("last_check", 0L).toFloat(),
                lastConfigApplied = configSync.optInt("applied_count", 0),
                lastConfigError = configSync.optString("last_error", ""),
                audio = RemoteAudioParams(
                    modelPath = audio.optString("model_path", ""),
                    indexPath = audio.optString("index_path", ""),
                    pitch = audio.optInt("pitch", 0),
                    method = audio.optString("method", "rmvpe"),
                    protect = audio.optDouble("protect", 0.33).toFloat(),
                    indexRate = audio.optDouble("index_rate", 0.3).toFloat(),
                    modelVersion = audio.optString("model_version", "v2"),
                ),
            )
        } catch (_: Exception) {
            null
        }
    }
}
