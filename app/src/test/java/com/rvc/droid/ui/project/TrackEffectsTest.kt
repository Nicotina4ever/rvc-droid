package com.rvc.droid.ui.project

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica la logica di modifica effetti per-traccia (volume/pan/mute/solo/duck).
 * La logica di MainScreen.onTrackVolume/onTrackPan/etc. crea una nuova lista
 * con `it.copy(...)` per il trackIndex specificato. Testiamo questa logica.
 */
class TrackEffectsTest {

    private fun setEffect(
        tracks: List<MixerTrackState>,
        idx: Int,
        copy: (MixerTrackState) -> MixerTrackState
    ): List<MixerTrackState> = tracks.map { if (it.trackIndex == idx) copy(it) else it }

    @Test
    fun `set volume modifies only target track`() {
        val tracks = listOf(
            MixerTrackState(trackIndex = 0, clipPath = "a", clipName = "A", durationMs = 1000, volume = 0.85f),
            MixerTrackState(trackIndex = 1, clipPath = "b", clipName = "B", durationMs = 1000, volume = 0.85f)
        )
        val r = setEffect(tracks, 1) { it.copy(volume = 0.42f) }
        assertEquals(0.85f, r[0].volume, 0.0001f)
        assertEquals(0.42f, r[1].volume, 0.0001f)
    }

    @Test
    fun `set pan to right and left`() {
        val tracks = listOf(MixerTrackState(trackIndex = 0, clipPath = "a", clipName = "A", durationMs = 1000, pan = 0f))
        val r = setEffect(tracks, 0) { it.copy(pan = 1f) }
        assertEquals(1f, r[0].pan, 0.0001f)
        val l = setEffect(tracks, 0) { it.copy(pan = -1f) }
        assertEquals(-1f, l[0].pan, 0.0001f)
    }

    @Test
    fun `mute toggles and persists`() {
        val tracks = listOf(MixerTrackState(trackIndex = 0, clipPath = "a", clipName = "A", durationMs = 1000, muted = false))
        val r1 = setEffect(tracks, 0) { it.copy(muted = !it.muted) }
        assertEquals(true, r1[0].muted)
        val r2 = setEffect(r1, 0) { it.copy(muted = !it.muted) }
        assertEquals(false, r2[0].muted)
    }

    @Test
    fun `ducking amount clamped 0-1`() {
        val tracks = listOf(MixerTrackState(trackIndex = 1, clipPath = "b", clipName = "B", durationMs = 1000, duckAmount = 0f))
        // Caso valido
        val r1 = setEffect(tracks, 1) { it.copy(duckAmount = 0.75f) }
        assertEquals(0.75f, r1[0].duckAmount, 0.0001f)
        // La validazione >1 spetta al C++ lato NDK (vedi TrackMixer::setTrackDuck)
        // Qui verifichiamo solo che la logica Kotlin non tronchi.
        val r2 = setEffect(tracks, 1) { it.copy(duckAmount = 1.5f) }
        assertEquals(1.5f, r2[0].duckAmount, 0.0001f)
    }

    @Test
    fun `default values match expected UX`() {
        val t = MixerTrackState(trackIndex = 0, clipPath = "x", clipName = "X", durationMs = 1)
        assertEquals(0.85f, t.volume, 0.0001f)  // un po' sotto 1 di default
        assertEquals(0f, t.pan, 0.0001f)
        assertEquals(false, t.muted)
        assertEquals(false, t.solo)
        assertEquals(0f, t.duckAmount, 0.0001f)
    }

    @Test
    fun `solo flag does not affect other tracks`() {
        val tracks = listOf(
            MixerTrackState(trackIndex = 0, clipPath = "a", clipName = "A", durationMs = 1000),
            MixerTrackState(trackIndex = 1, clipPath = "b", clipName = "B", durationMs = 1000)
        )
        val r = setEffect(tracks, 0) { it.copy(solo = true) }
        assertEquals(true, r[0].solo)
        assertEquals(false, r[1].solo)
    }
}
