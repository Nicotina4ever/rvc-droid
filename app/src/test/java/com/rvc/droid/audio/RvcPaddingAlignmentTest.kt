package com.rvc.droid.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la formula di padding audio introdotta in InferenceEngine.cpp
 * (runChain, step 3.5) per allineare la lunghezza dell'audio in modo che,
 * DOPO il reflect pad di 16000, la lunghezza totale sia multipla di
 * 16 * 160 = 2560 samples (16 frame mel interni al modello RMVPE).
 *
 * Senza questo allineamento RMVPE fallisce con
 * "Axis 2 has mismatched dimensions of 25 and 24" sull'ultimo batch.
 *
 * La funzione `padForMelFrames` è una replica 1:1 della logica C++.
 */
class RvcPaddingAlignmentTest {

    /**
     * Replica Kotlin della logica in InferenceEngine.cpp step 3.5:
     * dopo reflect pad di 16000, la lunghezza deve essere multipla di 2560.
     * Quindi a16_len deve essere tale che (a16_len + 16000) % 2560 == 0,
     * equivalentemente a16_len % 2560 == (2560 - 16000 % 2560) % 2560.
     *
     * 16000 % 2560 = 16000 - 6*2560 = 16000 - 15360 = 640
     * Quindi vogliamo a16_len % 2560 == (2560 - 640) % 2560 = 1920.
     * Se a16_len % 2560 != 1920, aggiungiamo padding per arrivarci.
     */
    private fun padForMelFrames(a16Size: Int): Int {
        val kTPad = 16000
        val kMelFrameGroup = 16 * 160  // 2560
        val target = a16Size
        val rem = (target + kTPad) % kMelFrameGroup
        return if (rem == 0) target else target + (kMelFrameGroup - rem)
    }

    private fun isAlignedForMelFrames(a16Size: Int): Boolean {
        val kTPad = 16000
        val kMelFrameGroup = 16 * 160
        return (a16Size + kTPad) % kMelFrameGroup == 0
    }

    @Test
    fun `audio 48000 needs no pad to be mel-aligned`() {
        // 48000 + 16000 = 64000, 64000 / 2560 = 25, esatto multiplo
        assertTrue(isAlignedForMelFrames(48000))
        assertEquals(48000, padForMelFrames(48000))
    }

    @Test
    fun `audio 126122 from log needs pad to mel boundary`() {
        // 126122 + 16000 = 142122, 142122 / 2560 = 55.5 → non multiplo
        val padded = padForMelFrames(126122)
        // Verifica che dopo il pad + reflect pad sia multiplo di 2560
        assertTrue(isAlignedForMelFrames(padded))
        // Esattamente: 126122 + pad = 142080 (multiplo di 2560, -42)
        // quindi pad = 142080 - 126122 = 15958... aspetta, ricalcolo:
        // 126122 + 16000 = 142122. 2560 * 56 = 143360. Pad = 143360 - 142122 = 1238
        // Quindi 126122 + 1238 = 127360, + 16000 = 143360 (multiplo di 2560)
        val expectedPadded = 126122 + 1238
        assertEquals(expectedPadded, padded)
        assertEquals(0, (padded + 16000) % 2560)
    }

    @Test
    fun `audio 64000 from log needs pad to mel boundary`() {
        // 64000 + 16000 = 80000, 2560*31 = 79360, 2560*32 = 81920
        // Pad = 81920 - 80000 = 1920
        val padded = padForMelFrames(64000)
        assertEquals(64000 + 1920, padded)
        assertEquals(0, (padded + 16000) % 2560)
    }

    @Test
    fun `multiple sizes produce aligned lengths`() {
        // Genera 100 lunghezze random e verifica che la formula produca
        // sempre un multiplo di 2560 dopo il reflect pad.
        val rng = java.util.Random(42)
        repeat(100) {
            val size = rng.nextInt(1_000_000)
            val padded = padForMelFrames(size)
            assertTrue(
                "padded=$padded not aligned for input=$size",
                (padded + 16000) % 2560 == 0
            )
            // Il pad non deve mai essere > 2560 samples (altrimenti è ridicolo)
            assertTrue("padded=$padded added > 2560 for input=$size", padded - size <= 2560)
        }
    }
}
