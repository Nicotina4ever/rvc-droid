package com.rvc.droid.ui.rvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelArtifactDiscoveryTest {
    @Test
    fun discoversValidSetAndMatchingIndex() {
        val root = Files.createTempDirectory("rvc-artifacts").toFile()
        val models = File(root, "Modelli").also { it.mkdirs() }
        File(models, "Nicola_Spot.onnx").createNewFile()
        File(models, "hubert_base.onnx").createNewFile()
        File(models, "rmvpe.onnx").createNewFile()
        File(models, "rmvpe_melfb.bin").outputStream().use { it.write(ByteArray(ModelArtifactDiscovery.MEL_BASIS_BYTES.toInt())) }
        File(models, "added_IVF_Spot.index").createNewFile()

        val found = ModelArtifactDiscovery.discover(listOf(root))

        assertEquals("Nicola_Spot.onnx", found.synth?.name)
        assertEquals("hubert_base.onnx", found.contentVec?.name)
        assertEquals("rmvpe.onnx", found.f0?.name)
        assertEquals("added_IVF_Spot.index", found.index?.name)
        assertTrue(found.hasRequiredModels)
    }

    @Test
    fun rejectsF0WithoutValidMelBasis() {
        val root = Files.createTempDirectory("rvc-artifacts-invalid").toFile()
        File(root, "voice.onnx").createNewFile()
        File(root, "hubert_base.onnx").createNewFile()
        File(root, "rmvpe.onnx").createNewFile()
        File(root, "rmvpe_melfb.bin").writeBytes(ByteArray(12))

        val found = ModelArtifactDiscovery.discover(listOf(root))

        assertFalse(found.hasF0)
        assertFalse(found.hasRequiredModels)
    }

    @Test
    fun ignoresLegacyPtlArtifacts() {
        val root = Files.createTempDirectory("rvc-artifacts-legacy").toFile()
        File(root, "Nicola-Spot.ptl").createNewFile()
        File(root, "hubert_base.ptl").createNewFile()

        val found = ModelArtifactDiscovery.discover(listOf(root))

        assertFalse(found.hasSynth)
        assertFalse(found.hasContentVec)
    }

    @Test
    fun diagnosticsListMissingArtifacts() {
        val root = Files.createTempDirectory("rvc-artifacts-diag").toFile()
        val (artifacts, diagnostics) = ModelArtifactDiscovery.discoverWithDiagnostics(listOf(root))

        assertFalse(artifacts.hasRequiredModels)
        val codes = diagnostics.map { it.code }
        assertTrue("SYNTH_MISSING" in codes)
        assertTrue("HUBERT_MISSING" in codes)
        assertTrue("F0_MISSING" in codes)
        assertTrue("MEL_BASIS" in codes || artifacts.f0 == null)
    }

    @Test
    fun prefersFbinOverIndex() {
        val root = Files.createTempDirectory("rvc-artifacts-fbin").toFile()
        File(root, "Nicola-Spot.onnx").createNewFile()
        File(root, "hubert_base.onnx").createNewFile()
        File(root, "rmvpe.onnx").createNewFile()
        writeMelBasis(File(root, "rmvpe_melfb.bin"))
        File(root, "added_IVF4074_Flat_nprobe_1_Nicola-Spot_v2.index").createNewFile()
        File(root, "Nicola-Spot_v2.fbin").createNewFile()

        val found = ModelArtifactDiscovery.discover(listOf(root))
        val (_, diags) = ModelArtifactDiscovery.discoverWithDiagnostics(listOf(root))

        assertNotNull(found.index)
        assertEquals("fbin", found.index!!.extension.lowercase())
        val codes = diags.map { it.code }
        assertFalse("INDEX_NOT_FBIN" in codes)
    }

    @Test
    fun warnsWhenOnlyLegacyIndex() {
        val root = Files.createTempDirectory("rvc-artifacts-idx").toFile()
        File(root, "Nicola-Spot.onnx").createNewFile()
        File(root, "hubert_base.onnx").createNewFile()
        File(root, "rmvpe.onnx").createNewFile()
        writeMelBasis(File(root, "rmvpe_melfb.bin"))
        File(root, "added_IVF4074_Flat_nprobe_1_Nicola-Spot_v2.index").createNewFile()

        val found = ModelArtifactDiscovery.discover(listOf(root))
        val (_, diags) = ModelArtifactDiscovery.discoverWithDiagnostics(listOf(root))

        assertNotNull(found.index)
        assertEquals("index", found.index!!.extension.lowercase())
        val codes = diags.map { it.code }
        assertTrue("INDEX_NOT_FBIN" in codes)
    }

    private fun writeMelBasis(file: File) {
        file.outputStream().use { it.write(ByteArray(ModelArtifactDiscovery.MEL_BASIS_BYTES.toInt())) }
    }
}
