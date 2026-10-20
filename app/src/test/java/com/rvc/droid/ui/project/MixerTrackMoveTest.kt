package com.rvc.droid.ui.project

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica la logica di swap delle tracce in MixerView.onMoveTrack.
 * La logica è:
 *  - ordina le tracce per offsetMs
 *  - trova la posizione corrente
 *  - trova la posizione target (currentIdx + direction)
 *  - scambia gli offsetMs di A e B
 *  - ritorna la nuova lista
 *
 * Testiamo questa logica replicandola in pure Kotlin (senza dipendere
 * dal bridge C++ che richiede native lib caricata).
 */
class MixerTrackMoveTest {

    private data class T(val i: Int, val off: Long)

    /**
     * Replica 1:1 della logica in MainScreen.onMoveTrack.
     * Restituisce la lista con gli offset scambiati.
     */
    private fun move(tracks: List<T>, trackIdx: Int, direction: Int): List<T> {
        val sorted = tracks.sortedBy { it.off }
        val currentIdx = sorted.indexOfFirst { it.i == trackIdx }
        if (currentIdx < 0) return tracks
        val target = currentIdx + direction
        if (target < 0 || target >= sorted.size) return tracks
        val a = sorted[currentIdx]
        val b = sorted[target]
        val aOff = a.off
        return tracks.map {
            when (it.i) {
                a.i -> it.copy(off = b.off)
                b.i -> it.copy(off = aOff)
                else -> it
            }
        }
    }

    @Test
    fun `move up swaps with previous`() {
        val tracks = listOf(T(0, 0), T(1, 5000), T(2, 10000))
        val r = move(tracks, 1, -1)
        // T(1) ora ha off=0, T(0) ha off=5000
        assertEquals(5000L, r.first { it.i == 0 }.off)
        assertEquals(0L, r.first { it.i == 1 }.off)
        assertEquals(10000L, r.first { it.i == 2 }.off)
    }

    @Test
    fun `move down swaps with next`() {
        val tracks = listOf(T(0, 0), T(1, 5000), T(2, 10000))
        val r = move(tracks, 0, +1)
        // T(0) ha off=5000, T(1) ha off=0
        assertEquals(5000L, r.first { it.i == 0 }.off)
        assertEquals(0L, r.first { it.i == 1 }.off)
        assertEquals(10000L, r.first { it.i == 2 }.off)
    }

    @Test
    fun `move up at top is a no-op`() {
        val tracks = listOf(T(0, 0), T(1, 5000))
        val r = move(tracks, 0, -1)
        assertEquals(tracks, r)
    }

    @Test
    fun `move down at bottom is a no-op`() {
        val tracks = listOf(T(0, 0), T(1, 5000))
        val r = move(tracks, 1, +1)
        assertEquals(tracks, r)
    }

    @Test
    fun `move on unknown track idx is a no-op`() {
        val tracks = listOf(T(0, 0), T(1, 5000))
        val r = move(tracks, 99, -1)
        assertEquals(tracks, r)
    }

    @Test
    fun `unsorted order is handled by sort by offsetMs`() {
        // La lista arriva fuori ordine: MixerTrackState non garantisce
        // l'ordinamento per offsetMs. La funzione deve ordinare prima.
        val tracks = listOf(T(2, 10000), T(0, 0), T(1, 5000))
        val r = move(tracks, 2, -1) // T(2) è in fondo, sale → diventa T(1)
        assertEquals(5000L, r.first { it.i == 2 }.off)
        assertEquals(10000L, r.first { it.i == 1 }.off)
        assertEquals(0L, r.first { it.i == 0 }.off)
    }
}
