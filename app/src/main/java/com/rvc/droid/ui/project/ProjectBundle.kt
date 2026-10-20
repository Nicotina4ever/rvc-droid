package com.rvc.droid.ui.project

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BundleExportResult(
    val file: File,
    val sizeBytes: Long,
    val clips: Int,
    val includesVideo: Boolean,
)

data class BundleImportResult(
    val projectFile: File,
    val clips: Int,
    val projectName: String,
)

/**
 * Bundle progetto portatile: un unico .zip con project.json, clip audio e video.
 * Serve per spostare un progetto tra telefono e PC (o tra due telefoni) via Dropbox.
 */
object ProjectBundle {
    private const val MAX_ENTRIES = 4000
    private const val MAX_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB
    private const val PROJECT_FILE = "project.json"
    private const val CLIPS_DIR = "clips"
    private const val VIDEO_DIR = "video"

    fun suggestedZipName(projectName: String): String {
        val safe = projectName.trim().ifBlank { ProjectManager.defaultProjectName() }
            .replace(Regex("[^A-Za-z0-9 _\\-]"), "_")
            .replace(" ", "_")
            .trim('_')
            .ifBlank { "project" }
        return "$safe.rvcdaw.zip"
    }

    // ------------------------------------------------------------------ export
    fun export(context: Context, state: ProjectState, targetZip: File): Result<BundleExportResult> {
        val staging = File(context.cacheDir, "bundle_export").apply {
            deleteRecursively()
            mkdirs()
        }
        return try {
            val clipsDir = File(staging, CLIPS_DIR).apply { mkdirs() }
            val videoDir = File(staging, VIDEO_DIR).apply { mkdirs() }
            val pathMap = mutableMapOf<String, String>()
            val usedNames = mutableSetOf<String>()

            fun store(sourcePath: String, dir: File, relativeDir: String): String? {
                if (sourcePath.isBlank()) return null
                val source = File(sourcePath)
                if (!source.isFile || !source.canRead()) return null
                val safeBase = source.name.replace(Regex("[^A-Za-z0-9._\\-]"), "_")
                    .ifBlank { "file_${usedNames.size}" }
                var storedName = safeBase
                var counter = 2
                while (storedName.lowercase() in usedNames) {
                    val dot = safeBase.lastIndexOf('.')
                    storedName = if (dot > 0) {
                        safeBase.substring(0, dot) + "_$counter" + safeBase.substring(dot)
                    } else {
                        "${safeBase}_$counter"
                    }
                    counter++
                }
                usedNames.add(storedName.lowercase())
                source.copyTo(File(dir, storedName), overwrite = true)
                return "$relativeDir/$storedName"
            }

            val recordings = state.recordings.map { clip ->
                val relative = store(clip.path, clipsDir, CLIPS_DIR)
                if (relative != null) pathMap[File(clip.path).absolutePath] = relative
                clip.copy(path = relative ?: "")
            }
            val mixerTracks = state.mixerTracks.map { track ->
                val relative = pathMap[File(track.clipPath).absolutePath]
                    ?: store(track.clipPath, clipsDir, CLIPS_DIR)
                if (relative != null) pathMap[File(track.clipPath).absolutePath] = relative
                track.copy(clipPath = relative ?: "")
            }
            val videoRelative = state.videoPath?.let { store(it, videoDir, VIDEO_DIR) }

            val portable = state.copy(
                name = state.name,
                recordings = recordings,
                mixerTracks = mixerTracks,
                videoPath = videoRelative,
            )
            val projectJson = File(staging, PROJECT_FILE)
            ProjectManager.saveProject(portable, projectJson)

            targetZip.parentFile?.mkdirs()
            zipDirectory(staging, targetZip)
            Result.success(
                BundleExportResult(
                    file = targetZip,
                    sizeBytes = targetZip.length(),
                    clips = recordings.count { it.path.isNotBlank() },
                    includesVideo = videoRelative != null,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            staging.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ import
    fun import(context: Context, zipFile: File): Result<BundleImportResult> {
        val staging = File(context.cacheDir, "bundle_import").apply {
            deleteRecursively()
            mkdirs()
        }
        return try {
            unzipSafely(zipFile, staging)
            val projectJson = File(staging, PROJECT_FILE)
            if (!projectJson.isFile) {
                throw IllegalArgumentException("Bundle non valido: manca $PROJECT_FILE")
            }
            val name = zipFile.name.removeSuffix(".rvcdaw.zip").removeSuffix(".zip")
                .replace(Regex("[^A-Za-z0-9 _\\-]"), "_").ifBlank { "imported" }
            val persistent = File(context.filesDir, "bundles").apply { mkdirs() }
            val bundleDir = File(persistent, name + "_" + timestamp())
            bundleDir.mkdirs()
            // Sposta i contenuti estratti (tranne staging) nella cartella persistente.
            staging.listFiles()?.forEach { item ->
                if (item.name == PROJECT_FILE) return@forEach
                val dest = File(bundleDir, item.name)
                if (item.isDirectory) item.copyRecursively(dest, overwrite = true) else item.copyTo(dest, overwrite = true)
            }

            val root = JSONObject(projectJson.readText())
            var clipCount = 0

            fun resolveRelative(relative: String): String? {
                if (relative.isBlank()) return null
                val candidate = File(bundleDir, relative)
                return if (candidate.isFile) candidate.absolutePath else null
            }

            val recordings = root.optJSONArray("recordings")
            if (recordings != null) {
                for (i in 0 until recordings.length()) {
                    val item = recordings.optJSONObject(i) ?: continue
                    val resolved = resolveRelative(item.optString("path", ""))
                    if (resolved != null) {
                        item.put("path", resolved)
                        clipCount++
                    } else {
                        item.put("path", "")
                    }
                }
            }
            val mixerTracks = root.optJSONArray("mixerTracks")
            if (mixerTracks != null) {
                for (i in 0 until mixerTracks.length()) {
                    val item = mixerTracks.optJSONObject(i) ?: continue
                    val resolved = resolveRelative(item.optString("path", ""))
                    item.put("path", resolved ?: "")
                }
            }
            val video = root.optString("videoPath", "")
            if (video.isNotBlank()) {
                root.put("videoPath", resolveRelative(video) ?: "")
            }

            val projectsDir = ProjectManager.getProjectsDir()
                ?: throw IllegalStateException("Cartella progetti non inizializzata")
            projectsDir.mkdirs()
            val projectFile = File(
                projectsDir,
                name + "_imported_" + timestamp() + ".rvcp"
            )
            projectFile.writeText(root.toString(2))
            Result.success(
                BundleImportResult(
                    projectFile = projectFile,
                    clips = clipCount,
                    projectName = root.optString("name", name),
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- helpers
    private fun zipDirectory(directory: File, target: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            val rootPath = directory.toPath()
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = rootPath.relativize(file.toPath()).toString().replace('\\', '/')
                zip.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun unzipSafely(zipFile: File, destination: File) {
        val destinationCanonical = destination.canonicalFile
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > MAX_ENTRIES) throw IllegalArgumentException("Bundle con troppi file")
                val target = File(destination, entry.name)
                val canonical = target.canonicalFile
                if (!canonical.path.startsWith(destinationCanonical.path + File.separator) &&
                    canonical != destinationCanonical
                ) {
                    throw IllegalArgumentException("Bundle non sicuro: ${entry.name}")
                }
                if (entry.isDirectory) {
                    canonical.mkdirs()
                } else {
                    canonical.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(canonical)).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw IllegalArgumentException("Bundle troppo grande")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        if (entries == 0) throw IllegalArgumentException("Bundle vuoto")
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
