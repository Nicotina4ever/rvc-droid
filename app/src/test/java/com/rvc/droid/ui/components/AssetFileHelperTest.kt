package com.rvc.droid.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AssetFileHelperTest {
    @Test
    fun describeAssetReturnsReadableStatusForExistingFile() {
        val tempDir = Files.createTempDirectory("asset-helper").toFile()
        val file = File(tempDir, "model.onnx")
        file.writeText("test-data")

        val summary = AssetFileHelper.describeAsset(file.absolutePath, "synth")

        assertTrue(summary.contains("model.onnx"))
        assertTrue(summary.contains("ready") || summary.contains("Synth"))
    }

    @Test
    fun describeAssetFlagsMissingFilesClearly() {
        val summary = AssetFileHelper.describeAsset("/missing/model.onnx", "synth")

        assertTrue(summary.contains("missing"))
        assertTrue(summary.contains("synth"))
    }
}
