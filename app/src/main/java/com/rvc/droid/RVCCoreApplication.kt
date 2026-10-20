package com.rvc.droid

import android.app.Application
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RVCCoreApplication : Application() {
    companion object {
        const val TAG = "RVCCoreApp"
        lateinit var instance: RVCCoreApplication; private set

        // Percorso del log file: SEMPRE accessibile, in entrambe le posizioni.
        // 1) getExternalFilesDir(null) = /sdcard/Android/data/<pkg>/files/  (sempre scrivibile)
        // 2) getExternalStorageDirectory()/Modelli = /sdcard/Modelli/ (legacy, richiede permessi su Android 11+)
        fun getLogFile(): java.io.File? {
            val candidates = mutableListOf<java.io.File>()
            try {
                val primary = File(instance.getExternalFilesDir(null), "native.log")
                if (primary.parentFile?.exists() == true) candidates += primary
            } catch (_: Exception) {}
            try {
                val legacyDir = File(Environment.getExternalStorageDirectory(), "Modelli")
                if (legacyDir.exists() || legacyDir.mkdirs()) candidates += File(legacyDir, "native.log")
            } catch (_: Exception) {}
            return candidates.firstOrNull()
        }

        fun logToFile(message: String) {
            try {
                val logFile = getLogFile() ?: return
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                logFile.appendText("[$stamp] [Kotlin] $message\n")
            } catch (_: Exception) {}
        }

        fun getMemoryInfo(): String {
            return try {
                val rt = Runtime.getRuntime()
                val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
                val total = rt.totalMemory() / 1024 / 1024
                val max = rt.maxMemory() / 1024 / 1024
                val am = instance.getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                "javaHeap used=${used}MB total=${total}MB max=${max}MB | largeHeap=${am.largeMemoryClass}MB"
            } catch (e: Exception) { "mem: unknown" }
        }

        /**
         * Bootstrap dei modelli "fissi" (HuBERT + rmvpe) in una posizione SEMPRE
         * accessibile (`getExternalFilesDir(null)/models/`). L'autoload scansiona
         * questa cartella per prima, quindi i modelli sono trovati senza bisogno
         * di permessi speciali o selezione manuale della cartella.
         *
         * I file sorgente possono essere ovunque: assets, /Download/Modelli,
         * /Modelli, /sdcard/Modelli. La copia è idempotente: salta i file già
         * presenti con la stessa size. Ritorna la lista dei modelli copiati.
         */
        fun ensureBaseModelsAvailable(): List<String> {
            val results = mutableListOf<String>()
            try {
                val dstDir = File(instance.getExternalFilesDir(null), "models")
                if (!dstDir.exists() && !dstDir.mkdirs()) {
                    logToFile("ensureBaseModels: cannot create ${dstDir.absolutePath}")
                    return results
                }
                val sources = listOf(
                    // (filename, copy if missing in dstDir)
                    "hubert_base.onnx" to listOf(
                        File("/storage/emulated/0/Download/Modelli/hubert_base.onnx"),
                        File("/storage/emulated/0/Download/hubert_base.onnx"),
                        File("/storage/emulated/0/Modelli/hubert_base.onnx"),
                        File("/sdcard/Download/Modelli/hubert_base.onnx"),
                        File("/sdcard/Download/hubert_base.onnx"),
                        File("/sdcard/Modelli/hubert_base.onnx")
                    ),
                    "rmvpe.onnx" to listOf(
                        File("/storage/emulated/0/Download/Modelli/rmvpe.onnx"),
                        File("/storage/emulated/0/Download/rmvpe.onnx"),
                        File("/storage/emulated/0/Modelli/rmvpe.onnx"),
                        File("/sdcard/Download/Modelli/rmvpe.onnx"),
                        File("/sdcard/Download/rmvpe.onnx"),
                        File("/sdcard/Modelli/rmvpe.onnx")
                    ),
                    "rmvpe_melfb.bin" to listOf(
                        File("/storage/emulated/0/Download/Modelli/rmvpe_melfb.bin"),
                        File("/storage/emulated/0/Download/rmvpe_melfb.bin"),
                        File("/storage/emulated/0/Modelli/rmvpe_melfb.bin"),
                        File("/sdcard/Download/Modelli/rmvpe_melfb.bin"),
                        File("/sdcard/Download/rmvpe_melfb.bin"),
                        File("/sdcard/Modelli/rmvpe_melfb.bin")
                    )
                )
                for ((name, candidates) in sources) {
                    val dst = File(dstDir, name)
                    if (dst.exists() && dst.length() > 0) {
                        results += "OK (già presente): $name (${dst.length() / 1024 / 1024} MB)"
                        continue
                    }
                    val src = candidates.firstOrNull { it.exists() && it.length() > 0 }
                    if (src == null) {
                        results += "MANCANTE sorgente: $name (cercata in ${candidates.size} posizioni)"
                        logToFile("ensureBaseModels: source not found for $name")
                        continue
                    }
                    try {
                        logToFile("ensureBaseModels: copying $name (${src.length() / 1024 / 1024} MB) from ${src.absolutePath}")
                        src.inputStream().use { input ->
                            dst.outputStream().use { output -> input.copyTo(output) }
                        }
                        results += "COPIATO: $name (${dst.length() / 1024 / 1024} MB)"
                    } catch (e: Exception) {
                        results += "ERRORE copia $name: ${e.message}"
                        logToFile("ensureBaseModels: copy failed for $name: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                logToFile("ensureBaseModels: ${e.message}")
            }
            return results
        }

        /**
         * Copia modelli "fissi" (HuBERT + rmvpe) da un tree URI SAF a
         * getExternalFilesDir(null)/models/. Usato dall'autoload quando
         * l'utente sceglie la cartella modelli via SAF picker (es. perché
         * i file sono in una posizione che File() non può aprire su
         * Android 11+).
         *
         * Restituisce lista di (filename, copied/skipped/error) per logging.
         */
        fun copyBaseModelsFromSafTree(treeUri: android.net.Uri): List<String> {
            val results = mutableListOf<String>()
            try {
                val dstDir = File(instance.getExternalFilesDir(null), "models")
                if (!dstDir.exists() && !dstDir.mkdirs()) {
                    logToFile("copyFromSaf: cannot create ${dstDir.absolutePath}")
                    return results
                }
                // Prendi il persistable permission
                try {
                    instance.contentResolver.takePersistableUriPermission(
                        treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    logToFile("copyFromSaf: takePersistableUriPermission: ${e.message}")
                }
                val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(instance, treeUri)
                if (tree == null || !tree.canRead()) {
                    results += "ERRORE: tree URI non leggibile"
                    logToFile("copyFromSaf: tree not readable")
                    return results
                }
                logToFile("copyFromSaf: scanning tree ${tree.uri}, name=${tree.name}")
                val targetFiles = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                fun walk(dir: androidx.documentfile.provider.DocumentFile, depth: Int) {
                    if (depth > 3) return
                    for (child in dir.listFiles()) {
                        val n = child.name ?: continue
                        if (child.isDirectory) {
                            walk(child, depth + 1)
                        } else {
                            val lower = n.lowercase()
                            if (lower.endsWith(".onnx") || lower.endsWith(".fbin") || lower.endsWith(".index") || lower == "rmvpe_melfb.bin") {
                                targetFiles.add(child)
                            }
                        }
                    }
                }
                walk(tree, 0)
                logToFile("copyFromSaf: trovati ${targetFiles.size} file utili: ${targetFiles.mapNotNull { it.name }}")
                for (src in targetFiles) {
                    val name = src.name ?: continue
                    val dst = File(dstDir, name)
                    if (dst.exists() && dst.length() > 0 && dst.length() == src.length()) {
                        results += "OK (già presente): $name (${dst.length() / 1024 / 1024} MB)"
                    } else {
                        try {
                            logToFile("copyFromSaf: copying $name (${src.length() / 1024 / 1024} MB)")
                            val input = instance.contentResolver.openInputStream(src.uri)
                            if (input == null) {
                                results += "ERRORE openInputStream: $name"
                            } else {
                                input.use { ins ->
                                    dst.outputStream().use { output -> ins.copyTo(output) }
                                }
                                results += "COPIATO: $name (${dst.length() / 1024 / 1024} MB)"
                            }
                        } catch (e: Exception) {
                            results += "ERRORE copia $name: ${e.message}"
                            logToFile("copyFromSaf: copy failed for $name: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                results += "ERRORE: ${e.message}"
                logToFile("copyFromSaf: ${e.message}")
            }
            return results
        }
    }

    override fun onCreate() {
        super.onCreate(); instance = this
        Log.i(TAG, "=== RVC Droid v2.0 starting ===")
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            Log.i(TAG, "heap: mem=${am.memoryClass}MB large=${am.largeMemoryClass}MB")
        } catch(e: Exception) { Log.i(TAG, "heap: unknown") }

        // Scrivi un header sul file di log COSI L'UTENTE VEDE che l'app e' partita.
        // Il file verra' creato anche se l'engine nativo non si inizializza.
        try {
            val logFile = getLogFile()
            if (logFile != null) {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                logFile.appendText("[$stamp] === RVC Droid start ===\n")
                logFile.appendText("[$stamp] logPath=${logFile.absolutePath}\n")
                logFile.appendText("[$stamp] mem=${try {
                    val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                    "memoryClass=${am.memoryClass}MB largeMemoryClass=${am.largeMemoryClass}MB"
                } catch(e: Exception) { "unknown" }}\n")
                com.rvc.droid.audio.NativeAudioBridge.initNativeLogging(logFile.absolutePath)
                Log.i(TAG, "native log path: ${logFile.absolutePath}")
            } else {
                Log.w(TAG, "no log file candidate available")
            }
        } catch (e: Exception) {
            Log.w(TAG, "native log init failed: ${e.message}")
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val msg = throwable.toString() + "\n" + sw.toString()
                Log.e(TAG, "CRASH: $msg")
                try {
                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    val logFile = getLogFile()?.parentFile?.let { File(it, "crash_log.txt") }
                    if (logFile != null) {
                        logFile.appendText("[$stamp] ${thread.name}\n$msg\n---\n")
                    }
                } catch (e2: Exception) { Log.e(TAG, "crash log write failed") }
            } finally {
                try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Exception) {}
            }
        }

        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if(level >= TRIM_MEMORY_COMPLETE) Log.w(TAG, "Memory critical!")
                else if(level >= TRIM_MEMORY_MODERATE) Log.w(TAG, "Memory pressure")
            }
            override fun onConfigurationChanged(c: android.content.res.Configuration) {}
            override fun onLowMemory() { Log.w(TAG, "Low memory") }
        })

        // Estrai automaticamente rmvpe_melfb.bin dagli asset dell'APK in filesDir e models/
        try {
            val targets = listOf(
                File(filesDir, "rmvpe_melfb.bin"),
                File(File(getExternalFilesDir(null), "models").also { it.mkdirs() }, "rmvpe_melfb.bin")
            )
            for (dst in targets) {
                if (!dst.exists() || dst.length() != 262656L) {
                    assets.open("rmvpe_melfb.bin").use { input ->
                        dst.outputStream().use { output -> input.copyTo(output) }
                    }
                    logToFile("rmvpe_melfb.bin extracted from assets -> ${dst.absolutePath}")
                }
            }
        } catch (e: Exception) {
            logToFile("Failed to extract rmvpe_melfb.bin from assets: ${e.message}")
        }

        logToFile("RVCCoreApplication ready. Modelli base: tocca Scegli/Prepara Base per importarli.")
    }
}