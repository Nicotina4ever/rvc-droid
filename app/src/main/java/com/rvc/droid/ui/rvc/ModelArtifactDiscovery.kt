package com.rvc.droid.ui.rvc

import java.io.File

/** Finds and validates one RVC artifact set from app and user-selected directories. */
object ModelArtifactDiscovery {
    const val MEL_BASIS_BYTES = 262_656L

    data class ArtifactSet(
        val synth: File? = null,
        val contentVec: File? = null,
        val f0: File? = null,
        val index: File? = null,
        val melBasis: File? = null
    ) {
        val hasSynth: Boolean get() = synth != null
        val hasContentVec: Boolean get() = contentVec != null
        val hasF0: Boolean get() = f0 != null && melBasis != null
        val hasRequiredModels: Boolean get() = hasSynth && hasContentVec && hasF0
    }

    fun searchDirectories(externalFilesDir: File?, filesDir: File, selectedDir: String): List<File> {
        return buildList {
            externalFilesDir?.let {
                add(it)
                add(File(it, "models"))
            }
            add(filesDir)
            add(File(filesDir, "models"))
            add(File(filesDir, "rvc_models"))
            add(File("/sdcard/Modelli/Modelli"))
            add(File("/sdcard/Modelli"))
            add(File("/storage/emulated/0/Modelli/Modelli"))
            add(File("/storage/emulated/0/Modelli"))
            add(File("/sdcard/Download/Modelli"))
            add(File("/storage/emulated/0/Download/Modelli"))
            add(File("/sdcard/Download"))
            add(File("/storage/emulated/0/Download"))
            if (selectedDir.isNotBlank()) {
                add(File(selectedDir))
                add(File(selectedDir, "models"))
                add(File(selectedDir, "Modelli"))
            }
        }.distinctBy { it.absolutePath.lowercase() }
    }

    data class Diagnostic(val code: String, val message: String)

    fun discoverWithDiagnostics(directories: List<File>): Pair<ArtifactSet, List<Diagnostic>> {
        val accessible = directories.filter { dir ->
            try { dir.isDirectory } catch (_: SecurityException) { false }
        }
        val inaccessible = directories - accessible
        val diags = mutableListOf<Diagnostic>()
        inaccessible.forEach { diags += Diagnostic("PERM", "Cartella non accessibile: ${it.absolutePath}") }

        val artifacts = discover(accessible)
        if (artifacts.synth == null) diags += Diagnostic("SYNTH_MISSING", "Modello voce ONNX non trovato")
        if (artifacts.contentVec == null) diags += Diagnostic("HUBERT_MISSING", "HuBERT ONNX non trovato")
        if (artifacts.f0 == null) diags += Diagnostic("F0_MISSING", "rmvpe.onnx non trovato")
        if (artifacts.f0 != null && artifacts.melBasis == null) diags += Diagnostic("MEL_BASIS", "rmvpe_melfb.bin da 262656 byte assente o non valido accanto a ${artifacts.f0.name}")
        if (artifacts.synth != null && artifacts.index == null) diags += Diagnostic("INDEX_MISSING", "Nessun index .fbin/.index associato al synth")
        if (artifacts.index != null && artifacts.index!!.extension.equals("index", true)) {
            diags += Diagnostic(
                "INDEX_NOT_FBIN",
                "Index .index (faiss) rilevato: NON verra' caricato dall'app Android. " +
                "Convertilo in .fbin con tools/export_faiss_bin.py (K:\\AI\\Android\\rvc-portable-android\\tools\\) " +
                "e posizionalo accanto al .onnx dello stesso modello."
            )
        }
        return artifacts to diags
    }

    fun discover(directories: List<File>): ArtifactSet {
        val files = directories.asSequence()
            .filter { it.isDirectory }
            .flatMap { directory ->
                directory.walkTopDown()
                    .onEnter { it.toPath().nameCount - directory.toPath().nameCount <= 2 }
                    .filter { it.isFile }
                    .asSequence()
            }
            .distinctBy { it.absolutePath.lowercase() }
            .toList()

        val synth = files.filter { isSynthCandidate(it) }
            .sortedBy { it.absolutePath.lowercase() }
            .firstOrNull()
        val contentVec = files.filter { isContentVecCandidate(it) }
            .sortedBy { it.absolutePath.lowercase() }
            .firstOrNull()
        val f0Candidates = files.filter { it.name.equals("rmvpe.onnx", ignoreCase = true) }
            .sortedBy { it.absolutePath.lowercase() }
        val f0 = f0Candidates.firstOrNull { melBasisFor(it) != null }
        val melBasis = f0?.let(::melBasisFor)
        val index = synth?.let { synthFile ->
            val normalizedBase = normalize(synthFile.nameWithoutExtension)
            files.filter { isIndexCandidate(it) }
                .filter { candidate ->
                    val normalizedName = normalize(candidate.nameWithoutExtension)
                    normalizedName.contains(normalizedBase) || normalizedName.contains("ivf")
                }
                // Prefer .fbin (formato replicato C++) over .index (faiss nativo non supportato).
                .sortedWith(
                    compareBy<File> { !normalize(it.nameWithoutExtension).contains(normalizedBase) }
                        .thenBy { if (it.extension.equals("fbin", true)) 0 else 1 }
                        .thenBy { it.absolutePath.lowercase() }
                )
                .firstOrNull()
        }
        return ArtifactSet(synth, contentVec, f0, index, melBasis)
    }

    private fun isSynthCandidate(file: File): Boolean {
        if (!file.extension.equals("onnx", true)) return false
        val name = file.name.lowercase()
        return listOf("nicola", "lozio", "spot", "narra", "model", "weights").any(name::contains)
    }

    private fun isContentVecCandidate(file: File): Boolean {
        if (!file.extension.equals("onnx", true)) return false
        val name = file.name.lowercase()
        return name.contains("hubert") || name.contains("vec") || name.contains("content")
    }

    private fun isIndexCandidate(file: File): Boolean =
        file.extension.equals("index", true) || file.extension.equals("fbin", true)

    private fun melBasisFor(f0: File): File? {
        val sibling = File(f0.parentFile, "rmvpe_melfb.bin")
        if (sibling.isFile && sibling.length() == MEL_BASIS_BYTES) return sibling
        val fallbacks = listOf(
            File("/data/data/com.rvc.droid/files/rmvpe_melfb.bin"),
            File("/data/user/0/com.rvc.droid/files/rmvpe_melfb.bin"),
            File("/storage/emulated/0/Android/data/com.rvc.droid/files/models/rmvpe_melfb.bin"),
            File("/storage/emulated/0/Android/data/com.rvc.droid/files/rmvpe_melfb.bin")
        )
        return fallbacks.firstOrNull { it.isFile && it.length() == MEL_BASIS_BYTES }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "")
}
