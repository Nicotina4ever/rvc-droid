package com.rvc.droid.cloud

import org.junit.Assert.*
import org.junit.Test

class DropboxClientTest {

    @Test
    fun testNormalizePath() {
        val config = DropboxConfig(token = "dummy_token")
        assertEquals("/TestRVC/AudioIn/spot01.wav", config.normalizePath("/TestRVC/AudioIn", "spot01.wav"))
        assertEquals("/TestRVC/AudioIn/spot01.wav", config.normalizePath("TestRVC/AudioIn/", "/spot01.wav"))
        assertEquals("/TestRVC/AudioOut/out.wav", config.normalizePath("/TestRVC/AudioOut/", "out.wav"))
    }

    @Test
    fun testParseModels() {
        val sampleText = """
            # Lista modelli RVC per lo studio
            Nicola-Spot2 | K:/AI/rvc-portable/Modelli/Nicola-Spot2.pth | K:/AI/rvc-portable/Modelli/added_IVF1456_Flat_nprobe_1_Nicola-Spot2_v2.index
            Voce-Trailer | K:/AI/rvc-portable/Modelli/Trailer.pth | 
            
            # Altro modello
            Voce-Radio | K:/AI/rvc-portable/Modelli/Radio.pth
            ModelloSenzaPercorso
        """.trimIndent()

        val models = DropboxClient.parseModels(sampleText)
        assertEquals(4, models.size)

        assertEquals("Nicola-Spot2", models[0].name)
        assertEquals("K:/AI/rvc-portable/Modelli/Nicola-Spot2.pth", models[0].modelPath)
        assertEquals("K:/AI/rvc-portable/Modelli/added_IVF1456_Flat_nprobe_1_Nicola-Spot2_v2.index", models[0].indexPath)

        assertEquals("Voce-Trailer", models[1].name)
        assertEquals("K:/AI/rvc-portable/Modelli/Trailer.pth", models[1].modelPath)
        assertEquals("", models[1].indexPath)

        assertEquals("Voce-Radio", models[2].name)
        assertEquals("K:/AI/rvc-portable/Modelli/Radio.pth", models[2].modelPath)

        assertEquals("ModelloSenzaPercorso", models[3].name)
    }

    @Test
    fun testEmptyTokenFailure() {
        val res = DropboxClient.testConnection("")
        assertTrue(res.isFailure)
    }
}
