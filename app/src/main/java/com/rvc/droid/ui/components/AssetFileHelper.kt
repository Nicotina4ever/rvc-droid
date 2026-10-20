package com.rvc.droid.ui.components

import java.io.File

data class AssetStatus(
    val label: String,
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val details: String
)

object AssetFileHelper {
    fun describeAsset(path: String, kind: String): String {
        if (path.isBlank()) {
            return "$kind not selected"
        }

        val file = File(path)
        return if (!file.exists()) {
            "$kind missing: $path"
        } else {
            val size = file.length()
            val sizeText = formatSize(size)
            "$kind ready • $sizeText • ${file.name}"
        }
    }

    fun getAssetStatus(path: String, kind: String): AssetStatus {
        val file = File(path)
        return if (!file.exists()) {
            AssetStatus(
                label = "$kind missing",
                path = path,
                exists = false,
                sizeBytes = 0L,
                lastModifiedMs = 0L,
                details = "$kind file was not found"
            )
        } else {
            AssetStatus(
                label = "$kind ready",
                path = path,
                exists = true,
                sizeBytes = file.length(),
                lastModifiedMs = file.lastModified(),
                details = formatSize(file.length())
            )
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1fKB", kb)
        val mb = kb / 1024.0
        return String.format(java.util.Locale.US, "%.1fMB", mb)
    }
}
