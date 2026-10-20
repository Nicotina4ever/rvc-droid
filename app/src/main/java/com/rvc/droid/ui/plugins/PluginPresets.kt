package com.rvc.droid.ui.plugins

import android.content.Context
import com.rvc.droid.ui.components.EQBandState
import com.rvc.droid.ui.components.NoiseGateState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Snapshot completo della catena effetti: serve per i preset utente salvati
 * (i "plugin preset" della DAW) e per i preset di fabbrica.
 */
data class FxPresetSnapshot(
    val name: String,
    val gate: NoiseGateState,
    val voiceBands: List<EQBandState>,
    val musicBands: List<EQBandState>,
    val compThreshold: Float,
    val compRatio: Float,
    val compAttack: Float,
    val compRelease: Float,
    val compMakeup: Float,
    val limiterThreshold: Float,
    val limiterRelease: Float,
    val reverbMix: Float,
    val reverbRoomSize: Float,
    val reverbDamping: Float,
    val voiceVolume: Float,
    val musicVolume: Float,
    val masterVolume: Float,
    val monitoringGain: Float,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("gate_enabled", gate.enabled)
        put("gate_threshold", gate.threshold.toDouble())
        put("gate_attack", gate.attackMs.toDouble())
        put("gate_hold", gate.holdMs.toDouble())
        put("gate_release", gate.releaseMs.toDouble())
        put("voice_bands", bandsToJson(voiceBands))
        put("music_bands", bandsToJson(musicBands))
        put("comp_threshold", compThreshold.toDouble())
        put("comp_ratio", compRatio.toDouble())
        put("comp_attack", compAttack.toDouble())
        put("comp_release", compRelease.toDouble())
        put("comp_makeup", compMakeup.toDouble())
        put("limiter_threshold", limiterThreshold.toDouble())
        put("limiter_release", limiterRelease.toDouble())
        put("reverb_mix", reverbMix.toDouble())
        put("reverb_room", reverbRoomSize.toDouble())
        put("reverb_damping", reverbDamping.toDouble())
        put("voice_volume", voiceVolume.toDouble())
        put("music_volume", musicVolume.toDouble())
        put("master_volume", masterVolume.toDouble())
        put("monitoring_gain", monitoringGain.toDouble())
    }

    companion object {
        fun bandsToJson(bands: List<EQBandState>): JSONArray = JSONArray().apply {
            bands.forEach { band ->
                put(JSONObject().apply {
                    put("freq", band.freq.toDouble())
                    put("gain", band.gainDB.toDouble())
                    put("q", band.q.toDouble())
                    put("enabled", band.enabled)
                })
            }
        }

        fun bandsFromJson(array: JSONArray?, fallback: List<EQBandState>): List<EQBandState> {
            if (array == null) return fallback
            return try {
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    EQBandState(
                        freq = item.optDouble("freq", 1000.0).toFloat(),
                        gainDB = item.optDouble("gain", 0.0).toFloat(),
                        q = item.optDouble("q", 1.0).toFloat(),
                        enabled = item.optBoolean("enabled", true),
                    )
                }
            } catch (_: Exception) {
                fallback
            }
        }

        fun fromJson(json: JSONObject, fallbackName: String = "Preset"): FxPresetSnapshot {
            val defaultGate = NoiseGateState()
            val defaultVoice = defaultVoiceBands()
            val defaultMusic = defaultMusicBands()
            return FxPresetSnapshot(
                name = json.optString("name", fallbackName),
                gate = NoiseGateState(
                    enabled = json.optBoolean("gate_enabled", defaultGate.enabled),
                    threshold = json.optDouble("gate_threshold", defaultGate.threshold.toDouble()).toFloat(),
                    attackMs = json.optDouble("gate_attack", defaultGate.attackMs.toDouble()).toFloat(),
                    holdMs = json.optDouble("gate_hold", defaultGate.holdMs.toDouble()).toFloat(),
                    releaseMs = json.optDouble("gate_release", defaultGate.releaseMs.toDouble()).toFloat(),
                ),
                voiceBands = bandsFromJson(json.optJSONArray("voice_bands"), defaultVoice),
                musicBands = bandsFromJson(json.optJSONArray("music_bands"), defaultMusic),
                compThreshold = json.optDouble("comp_threshold", -20.0).toFloat(),
                compRatio = json.optDouble("comp_ratio", 3.0).toFloat(),
                compAttack = json.optDouble("comp_attack", 5.0).toFloat(),
                compRelease = json.optDouble("comp_release", 80.0).toFloat(),
                compMakeup = json.optDouble("comp_makeup", 0.0).toFloat(),
                limiterThreshold = json.optDouble("limiter_threshold", -1.0).toFloat(),
                limiterRelease = json.optDouble("limiter_release", 80.0).toFloat(),
                reverbMix = json.optDouble("reverb_mix", 0.0).toFloat(),
                reverbRoomSize = json.optDouble("reverb_room", 0.5).toFloat(),
                reverbDamping = json.optDouble("reverb_damping", 0.5).toFloat(),
                voiceVolume = json.optDouble("voice_volume", 0.0).toFloat(),
                musicVolume = json.optDouble("music_volume", 0.0).toFloat(),
                masterVolume = json.optDouble("master_volume", 0.0).toFloat(),
                monitoringGain = json.optDouble("monitoring_gain", 0.7).toFloat(),
            )
        }

        fun defaultVoiceBands(): List<EQBandState> = listOf(
            EQBandState(freq = 120f, gainDB = 0f, q = 0.9f),
            EQBandState(freq = 500f, gainDB = 0f, q = 1.0f),
            EQBandState(freq = 3000f, gainDB = 0f, q = 1.2f),
            EQBandState(freq = 9000f, gainDB = 0f, q = 0.8f),
        )

        fun defaultMusicBands(): List<EQBandState> = listOf(
            EQBandState(freq = 80f, gainDB = 0f, q = 0.8f),
            EQBandState(freq = 400f, gainDB = 0f, q = 1.0f),
            EQBandState(freq = 2500f, gainDB = 0f, q = 1.0f),
            EQBandState(freq = 10000f, gainDB = 0f, q = 0.7f),
        )
    }
}

/** Preset di fabbrica professionali per spot/podcast/musica. */
object FxFactoryPresets {
    fun flat(): FxPresetSnapshot = FxPresetSnapshot(
        name = "Flat / Bypass",
        gate = NoiseGateState(enabled = false),
        voiceBands = FxPresetSnapshot.defaultVoiceBands(),
        musicBands = FxPresetSnapshot.defaultMusicBands(),
        compThreshold = 0f,
        compRatio = 1f,
        compAttack = 5f,
        compRelease = 50f,
        compMakeup = 0f,
        limiterThreshold = -0.5f,
        limiterRelease = 60f,
        reverbMix = 0f,
        reverbRoomSize = 0.5f,
        reverbDamping = 0.5f,
        voiceVolume = 0f,
        musicVolume = 0f,
        masterVolume = 0f,
        monitoringGain = 0.7f,
    )

    fun voceSpot(): FxPresetSnapshot = FxPresetSnapshot(
        name = "Voce Spot",
        gate = NoiseGateState(enabled = true, threshold = 0.05f, attackMs = 1f, holdMs = 50f, releaseMs = 100f),
        voiceBands = listOf(
            EQBandState(freq = 120f, gainDB = -1.5f, q = 0.9f),
            EQBandState(freq = 500f, gainDB = 0.8f, q = 1.0f),
            EQBandState(freq = 3000f, gainDB = 2.5f, q = 1.2f),
            EQBandState(freq = 9000f, gainDB = 1.0f, q = 0.8f),
        ),
        musicBands = FxPresetSnapshot.defaultMusicBands(),
        compThreshold = -22f,
        compRatio = 3f,
        compAttack = 5f,
        compRelease = 80f,
        compMakeup = 3f,
        limiterThreshold = -0.8f,
        limiterRelease = 80f,
        reverbMix = 0.08f,
        reverbRoomSize = 0.35f,
        reverbDamping = 0.55f,
        voiceVolume = 0f,
        musicVolume = 0f,
        masterVolume = 0f,
        monitoringGain = 0.7f,
    )

    fun podcast(): FxPresetSnapshot = FxPresetSnapshot(
        name = "Podcast",
        gate = NoiseGateState(enabled = true, threshold = 0.06f, attackMs = 2f, holdMs = 80f, releaseMs = 140f),
        voiceBands = listOf(
            EQBandState(freq = 100f, gainDB = -2.5f, q = 0.8f),
            EQBandState(freq = 250f, gainDB = 1.0f, q = 1.0f),
            EQBandState(freq = 4000f, gainDB = 1.5f, q = 1.0f),
            EQBandState(freq = 8000f, gainDB = 0.5f, q = 0.8f),
        ),
        musicBands = FxPresetSnapshot.defaultMusicBands(),
        compThreshold = -24f,
        compRatio = 5f,
        compAttack = 3f,
        compRelease = 120f,
        compMakeup = 4f,
        limiterThreshold = -1f,
        limiterRelease = 100f,
        reverbMix = 0.04f,
        reverbRoomSize = 0.25f,
        reverbDamping = 0.6f,
        voiceVolume = 0f,
        musicVolume = 0f,
        masterVolume = 0f,
        monitoringGain = 0.7f,
    )

    fun musica(): FxPresetSnapshot = FxPresetSnapshot(
        name = "Musica",
        gate = NoiseGateState(enabled = false),
        voiceBands = FxPresetSnapshot.defaultVoiceBands(),
        musicBands = listOf(
            EQBandState(freq = 80f, gainDB = 1.5f, q = 0.8f),
            EQBandState(freq = 400f, gainDB = -0.5f, q = 1.0f),
            EQBandState(freq = 2500f, gainDB = 0.8f, q = 1.0f),
            EQBandState(freq = 10000f, gainDB = 1.0f, q = 0.7f),
        ),
        compThreshold = -18f,
        compRatio = 2.5f,
        compAttack = 10f,
        compRelease = 150f,
        compMakeup = 2f,
        limiterThreshold = -0.5f,
        limiterRelease = 80f,
        reverbMix = 0.05f,
        reverbRoomSize = 0.4f,
        reverbDamping = 0.5f,
        voiceVolume = 0f,
        musicVolume = 0f,
        masterVolume = 0f,
        monitoringGain = 0.7f,
    )

    fun all(): List<FxPresetSnapshot> = listOf(flat(), voceSpot(), podcast(), musica())
}

/** Salvataggio/caricamento dei preset utente nella memoria privata. */
object FxPresetStore {
    private fun dir(context: Context): File = File(context.filesDir, "fx_presets").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { file -> file.isFile && file.extension.equals("json", true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun save(context: Context, snapshot: FxPresetSnapshot): File {
        val safe = snapshot.name.trim().ifBlank { "Preset" }.replace(Regex("[^A-Za-z0-9 _\\-]"), "").trim()
        val file = File(dir(context), "${safe.ifBlank { "Preset" }}.json")
        file.writeText(snapshot.toJson().toString(2))
        return file
    }

    fun load(file: File): FxPresetSnapshot? = try {
        FxPresetSnapshot.fromJson(JSONObject(file.readText()), file.nameWithoutExtension)
    } catch (_: Exception) {
        null
    }

    fun delete(file: File): Boolean = file.delete()
}
