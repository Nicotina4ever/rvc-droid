package com.rvc.droid.ui.project

import android.content.Context
import com.rvc.droid.ui.components.EQBandState
import com.rvc.droid.ui.components.NoiseGateState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class RecordingClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class MixerTrackState(
    val clipId: String = UUID.randomUUID().toString(),
    val trackIndex: Int,
    val clipPath: String,
    val clipName: String,
    val durationMs: Long,
    val offsetMs: Long = 0,
    // Effetti per-traccia (vedi MixerView tab "Tracce")
    val volume: Float = 0.85f,      // 0..1
    val pan: Float = 0f,            // -1 (L) .. +1 (R)
    val muted: Boolean = false,
    val solo: Boolean = false,
    val duckAmount: Float = 0f,     // 0..1, duck verso la traccia trackIndex-1
)

data class ProjectState(
    val name: String = "Untitled",
    val version: Int = 1,
    val createdAt: String = "",
    val updatedAt: String = "",
    val videoPath: String? = null,
    val isRvcEnabled: Boolean = false,
    val pitchShift: Int = 0,
    val synthModelPath: String = "",
    val vecModelPath: String = "",
    val noiseGate: NoiseGateState = NoiseGateState(),
    val voiceVolume: Float = 0f,
    val musicVolume: Float = 0f,
    val masterVolume: Float = 0f,
    val voiceBands: List<EQBandState> = List(4) { EQBandState() },
    val musicBands: List<EQBandState> = List(4) { EQBandState() },
    val compThreshold: Float = -20f,
    val compRatio: Float = 4f,
    val compAttack: Float = 5f,
    val compRelease: Float = 50f,
    val compMakeup: Float = 6f,
    val limiterThreshold: Float = -1f,
    val limiterRelease: Float = 10f,
    val reverbMix: Float = 0f,
    val reverbRoomSize: Float = 0.5f,
    val reverbDamping: Float = 0.5f,
    val monitoringGain: Float = 0.7f,
    val recordings: List<RecordingClip> = emptyList(),
    val mixerTracks: List<MixerTrackState> = emptyList()
)

object ProjectManager {
    private const val TAG = "ProjectManager"
    private const val PROJECT_EXT = ".rvcp"
    private var projectsDir: File? = null

    fun initialize(context: Context) {
        projectsDir = File(context.filesDir, "projects").also { it.mkdirs() }
    }

    fun getProjectsDir(): File? = projectsDir

    fun saveProject(state: ProjectState, file: File): Boolean {
        return try {
            val root = JSONObject()
            root.put("name", state.name)
            root.put("version", state.version)
            root.put("createdAt", state.createdAt)
            root.put("updatedAt", SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US).format(Date()))
            root.put("isRvcEnabled", state.isRvcEnabled)
            root.put("pitchShift", state.pitchShift)
            root.put("synthModelPath", state.synthModelPath)
            root.put("vecModelPath", state.vecModelPath)

            val ng = JSONObject()
            ng.put("enabled", state.noiseGate.enabled)
            ng.put("threshold", state.noiseGate.threshold.toDouble())
            ng.put("attackMs", state.noiseGate.attackMs.toDouble())
            ng.put("holdMs", state.noiseGate.holdMs.toDouble())
            ng.put("releaseMs", state.noiseGate.releaseMs.toDouble())
            root.put("noiseGate", ng)

            root.put("voiceVolume", state.voiceVolume.toDouble())
            root.put("musicVolume", state.musicVolume.toDouble())
            root.put("masterVolume", state.masterVolume.toDouble())

            root.put("voiceBands", bandsToJson(state.voiceBands))
            root.put("musicBands", bandsToJson(state.musicBands))

            root.put("compThreshold", state.compThreshold.toDouble())
            root.put("compRatio", state.compRatio.toDouble())
            root.put("compAttack", state.compAttack.toDouble())
            root.put("compRelease", state.compRelease.toDouble())
            root.put("compMakeup", state.compMakeup.toDouble())

            root.put("limiterThreshold", state.limiterThreshold.toDouble())
            root.put("limiterRelease", state.limiterRelease.toDouble())

            root.put("reverbMix", state.reverbMix.toDouble())
            root.put("reverbRoomSize", state.reverbRoomSize.toDouble())
            root.put("reverbDamping", state.reverbDamping.toDouble())
            root.put("monitoringGain", state.monitoringGain.toDouble())

            root.put("videoPath", state.videoPath ?: "")

            val recs = JSONArray()
            state.recordings.forEach { r ->
                val o = JSONObject()
                o.put("id", r.id); o.put("name", r.name)
                o.put("path", r.path); o.put("durationMs", r.durationMs)
                o.put("createdAt", r.createdAt)
                recs.put(o)
            }
            root.put("recordings", recs)

            val mtrks = JSONArray()
            state.mixerTracks.forEach { t ->
                val o = JSONObject()
                o.put("id", t.clipId)
                o.put("index", t.trackIndex); o.put("path", t.clipPath)
                o.put("name", t.clipName); o.put("duration", t.durationMs)
                o.put("offset", t.offsetMs)
                mtrks.put(o)
            }
            root.put("mixerTracks", mtrks)

            file.writeText(root.toString(2))
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun loadProject(file: File): ProjectState? {
        return try {
            val root = JSONObject(file.readText())
            val ngObj = root.optJSONObject("noiseGate") ?: JSONObject()
            ProjectState(
                name = root.optString("name", "Untitled"),
                version = root.optInt("version", 1),
                createdAt = root.optString("createdAt", ""),
                updatedAt = root.optString("updatedAt", ""),
                isRvcEnabled = root.optBoolean("isRvcEnabled", false),
                pitchShift = root.optInt("pitchShift", 0),
                synthModelPath = root.optString("synthModelPath", ""),
                vecModelPath = root.optString("vecModelPath", ""),
                noiseGate = NoiseGateState(
                    enabled = ngObj.optBoolean("enabled", false),
                    threshold = ngObj.optDouble("threshold", 0.05).toFloat(),
                    attackMs = ngObj.optDouble("attackMs", 1.0).toFloat(),
                    holdMs = ngObj.optDouble("holdMs", 50.0).toFloat(),
                    releaseMs = ngObj.optDouble("releaseMs", 100.0).toFloat()
                ),
                voiceVolume = root.optDouble("voiceVolume", 0.0).toFloat(),
                musicVolume = root.optDouble("musicVolume", 0.0).toFloat(),
                masterVolume = root.optDouble("masterVolume", 0.0).toFloat(),
                voiceBands = bandsFromJson(root.optJSONArray("voiceBands")),
                musicBands = bandsFromJson(root.optJSONArray("musicBands")),
                compThreshold = root.optDouble("compThreshold", -20.0).toFloat(),
                compRatio = root.optDouble("compRatio", 4.0).toFloat(),
                compAttack = root.optDouble("compAttack", 5.0).toFloat(),
                compRelease = root.optDouble("compRelease", 50.0).toFloat(),
                compMakeup = root.optDouble("compMakeup", 6.0).toFloat(),
                limiterRelease = root.optDouble("limiterRelease", 10.0).toFloat(),
                reverbMix = root.optDouble("reverbMix", 0.0).toFloat(),
                reverbRoomSize = root.optDouble("reverbRoomSize", 0.5).toFloat(),
                reverbDamping = root.optDouble("reverbDamping", 0.5).toFloat(),
                monitoringGain = root.optDouble("monitoringGain", 0.7).toFloat(),
                videoPath = root.optString("videoPath", "").takeIf { it.isNotBlank() },
                recordings = clipsFromJson(root.optJSONArray("recordings")),
                mixerTracks = mixerTracksFromJson(root.optJSONArray("mixerTracks"))
            )
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun mixerTracksFromJson(arr: JSONArray?): List<MixerTrackState> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MixerTrackState(
                clipId = o.optString("id", UUID.randomUUID().toString()),
                trackIndex = o.optInt("index", 0),
                clipPath = o.optString("path", ""),
                clipName = o.optString("name", "Track"),
                durationMs = o.optLong("duration", 0L),
                offsetMs = o.optLong("offset", 0L),
                volume = o.optDouble("volume", 0.85).toFloat(),
                pan = o.optDouble("pan", 0.0).toFloat(),
                muted = o.optBoolean("muted", false),
                solo = o.optBoolean("solo", false),
                duckAmount = o.optDouble("duckAmount", 0.0).toFloat()
            )
        }
    }

    private fun clipsFromJson(arr: JSONArray?): List<RecordingClip> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RecordingClip(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Clip"),
                path = o.optString("path", ""),
                durationMs = o.optLong("durationMs", 0L),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    fun listProjects(): List<File> {
        return projectsDir?.listFiles { f -> f.extension == PROJECT_EXT.removePrefix(".") }?.toList() ?: emptyList()
    }

    fun deleteProject(file: File): Boolean = file.delete()

    private fun bandsToJson(bands: List<EQBandState>): JSONArray {
        val arr = JSONArray()
        bands.forEach { b ->
            val o = JSONObject()
            o.put("freq", b.freq.toDouble()); o.put("gainDB", b.gainDB.toDouble())
            o.put("q", b.q.toDouble()); o.put("enabled", b.enabled)
            arr.put(o)
        }
        return arr
    }

    private fun bandsFromJson(arr: JSONArray?): List<EQBandState> {
        if (arr == null) return List(4) { EQBandState() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EQBandState(
                freq = o.optDouble("freq", 1000.0).toFloat(),
                gainDB = o.optDouble("gainDB", 0.0).toFloat(),
                q = o.optDouble("q", 1.0).toFloat(),
                enabled = o.optBoolean("enabled", true)
            )
        }
    }

    fun defaultProjectName(): String = "project_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
}
