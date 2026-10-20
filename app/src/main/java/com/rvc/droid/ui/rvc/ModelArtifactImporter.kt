package com.rvc.droid.ui.rvc

import android.content.ContentResolver
import android.net.Uri
import java.io.File

/** Copies selected SAF artifacts into the app private files dir so the JNI side can read them. */
object ModelArtifactImporter {
    private const val MODELS_DIR = "rvc_models"

    fun modelsDir(filesDir: File): File = File(filesDir, MODELS_DIR).apply { mkdirs() }

    fun importTo(
        resolver: ContentResolver,
        srcUri: Uri,
        preferredName: String,
        filesDir: File
    ): File? {
        val target = File(modelsDir(filesDir), preferredName)
        return runCatching {
            resolver.openInputStream(srcUri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.takeIf { it.isFile && it.length() > 0L }
        }.getOrNull()
    }
}
