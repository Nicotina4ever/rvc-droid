package com.rvc.droid.cloud

import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

data class DropboxConfig(
    val token: String,
    val inputDir: String = "/TestRVC/AudioIn",
    val outputDir: String = "/TestRVC/AudioOut",
    val configFile: String = "/TestRVC/config.json",
    val modelsFile: String = "/TestRVC/modelli.txt"
) {
    fun normalizePath(folder: String, filename: String): String {
        val f = folder.trim().trimEnd('/')
        val n = filename.trim().trimStart('/')
        return if (f.startsWith("/")) "$f/$n" else "/$f/$n"
    }
}

data class RemoteRvcModel(
    val name: String,
    val modelPath: String,
    val indexPath: String = ""
)

/** Voce generica di una cartella Dropbox (usata per i bundle progetto). */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isFile: Boolean,
    val sizeBytes: Long = 0L,
    val modifiedAt: String = "",
)

object DropboxClient {

    /** Elenca i file di una cartella Dropbox. Cartella assente -> lista vuota. */
    fun listFolder(token: String, folderPath: String): Result<List<RemoteEntry>> {
        if (token.isBlank()) return Result.failure(IllegalArgumentException("Token non inserito"))
        return try {
            val url = URL("https://api.dropboxapi.com/2/files/list_folder")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            val body = JSONObject()
                .put("path", folderPath.trim().ifBlank { "" })
                .put("recursive", false)
                .put("include_media_info", false)
                .put("include_deleted", false)
                .put("include_has_explicit_shared_members", false)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code == 409) {
                // path/not_found: la cartella non esiste ancora.
                return Result.success(emptyList())
            }
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return Result.failure(IOException("Lista Dropbox fallita ($code): $err"))
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val entries = root.optJSONArray("entries") ?: return Result.success(emptyList())
            val list = mutableListOf<RemoteEntry>()
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val tag = item.optString(".tag", "file")
                list.add(
                    RemoteEntry(
                        name = item.optString("name", ""),
                        path = item.optString("path_display", item.optString("path_lower", "")),
                        isFile = tag == "file",
                        sizeBytes = item.optLong("size", 0L),
                        modifiedAt = item.optString("server_modified", ""),
                    )
                )
            }
            Result.success(list.sortedByDescending { it.modifiedAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Crea una cartella Dropbox (idempotente: 409 = esiste già). */
    fun createFolder(token: String, folderPath: String): Result<Unit> {
        if (token.isBlank()) return Result.failure(IllegalArgumentException("Token non inserito"))
        return try {
            val url = URL("https://api.dropboxapi.com/2/files/create_folder_v2")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.outputStream.use {
                it.write(JSONObject().put("path", folderPath).put("autorename", false).toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code in 200..299 || code == 409) Result.success(Unit)
            else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Creazione cartella Dropbox fallita ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Verifica la validità del token e restituisce il display name dell'utente.
     */
    fun testConnection(token: String): Result<String> {
        if (token.isBlank()) return Result.failure(IllegalArgumentException("Token non inserito"))
        return try {
            val url = URL("https://api.dropboxapi.com/2/users/get_current_account")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.outputStream.use { it.write("null".toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(resp)
                val displayName = json.optJSONObject("name")?.optString("display_name")
                    ?: json.optString("email", "Utente Dropbox")
                Result.success(displayName)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Errore connessione ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scarica un file di testo da Dropbox (es. config.json o modelli.txt).
     */
    fun downloadText(token: String, dropboxPath: String): Result<String> {
        return try {
            val url = URL("https://content.dropboxapi.com/2/files/download")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            val arg = JSONObject().put("path", dropboxPath).toString()
            conn.setRequestProperty("Dropbox-API-Arg", arg)
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code in 200..299) {
                val content = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(content)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Download testo fallito ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Carica un file di testo su Dropbox (es. aggiornamento di config.json).
     */
    fun uploadText(token: String, dropboxPath: String, content: String): Result<Unit> {
        return try {
            val url = URL("https://content.dropboxapi.com/2/files/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            val arg = JSONObject()
                .put("path", dropboxPath)
                .put("mode", "overwrite")
                .put("autorename", false)
                .put("mute", false)
                .put("strict_conflict", false)
                .toString()
            conn.setRequestProperty("Dropbox-API-Arg", arg)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true

            val bytes = content.toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Upload testo fallito ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Carica un file binario (es. WAV) su Dropbox verso AudioIn/.
     */
    fun uploadFile(
        token: String,
        dropboxPath: String,
        localFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        if (!localFile.exists()) return Result.failure(FileNotFoundException("File locale non trovato: ${localFile.absolutePath}"))
        return try {
            val totalBytes = localFile.length()
            val url = URL("https://content.dropboxapi.com/2/files/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            val arg = JSONObject()
                .put("path", dropboxPath)
                .put("mode", "overwrite")
                .put("autorename", false)
                .put("mute", false)
                .put("strict_conflict", false)
                .toString()
            conn.setRequestProperty("Dropbox-API-Arg", arg)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setFixedLengthStreamingMode(totalBytes)
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.doOutput = true

            FileInputStream(localFile).use { input ->
                conn.outputStream.use { output ->
                    val buffer = ByteArray(8192)
                    var bytesWritten = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        if (totalBytes > 0) {
                            onProgress(bytesWritten.toFloat() / totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Upload binario fallito ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifica se un file esiste su Dropbox (usato per il polling di AudioOut).
     */
    fun checkFileExists(token: String, dropboxPath: String): Result<Boolean> {
        return try {
            val url = URL("https://api.dropboxapi.com/2/files/get_metadata")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val body = JSONObject().put("path", dropboxPath).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(true)
            } else if (code == 409) {
                // Not found or path conflict
                Result.success(false)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Controllo esistenza file fallito ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scarica un file binario da Dropbox salvandolo in destFile locale.
     */
    fun downloadFile(
        token: String,
        dropboxPath: String,
        destFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        return try {
            val url = URL("https://content.dropboxapi.com/2/files/download")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            val arg = JSONObject().put("path", dropboxPath).toString()
            conn.setRequestProperty("Dropbox-API-Arg", arg)
            conn.connectTimeout = 20000
            conn.readTimeout = 30000

            val code = conn.responseCode
            if (code in 200..299) {
                val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
                destFile.parentFile?.mkdirs()
                val tempOut = File(destFile.parentFile, "${destFile.name}.downloading")
                conn.inputStream.use { input ->
                    FileOutputStream(tempOut).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                onProgress(bytesRead.toFloat() / totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
                if (destFile.exists()) destFile.delete()
                tempOut.renameTo(destFile)
                Result.success(Unit)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Download file fallito ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina un file da Dropbox (opzionale per pulire AudioIn o AudioOut).
     */
    fun deleteFile(token: String, dropboxPath: String): Result<Unit> {
        return try {
            val url = URL("https://api.dropboxapi.com/2/files/delete_v2")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val body = JSONObject().put("path", dropboxPath).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 200..299 || code == 409) {
                Result.success(Unit)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(IOException("Delete file fallito ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Esegue il parsing di modelli.txt per estrarre la lista di modelli remoti.
     * Formato flessibile per riga:
     *   Nome visualizzato | percorso_pth_su_pc | percorso_index_su_pc
     * Le righe vuote o che iniziano con # vengono ignorate.
     */
    fun parseModels(text: String): List<RemoteRvcModel> {
        val list = mutableListOf<RemoteRvcModel>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val parts = trimmed.split("|").map { it.trim() }
            if (parts.size >= 2) {
                val name = parts[0]
                val pth = parts[1]
                val index = if (parts.size >= 3) parts[2] else ""
                list.add(RemoteRvcModel(name = name, modelPath = pth, indexPath = index))
            } else if (parts.size == 1 && parts[0].isNotBlank()) {
                val name = parts[0]
                // Modello con nome semplice
                list.add(RemoteRvcModel(name = name, modelPath = name, indexPath = ""))
            }
        }
        return list
    }
}
