package com.rvc.droid.ui.prompter

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Script del gobbo salvati come file di testo nella memoria privata dell'app. */
object PrompterStore {
    private const val PREFS = "rvc_prompter"
    private const val KEY_LAST_TEXT = "last_text"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_FONT = "font_size"
    private const val KEY_SPEED = "scroll_speed"
    private const val KEY_MIRROR = "mirror"
    private const val KEY_AUTO = "auto_scroll"

    fun scriptsDir(context: Context): File =
        File(context.filesDir, "prompter").apply { mkdirs() }

    fun list(context: Context): List<File> =
        scriptsDir(context).listFiles { file -> file.isFile && file.extension.equals("txt", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun load(file: File): String = runCatching { file.readText() }.getOrDefault("")

    fun save(context: Context, name: String, text: String): File {
        val safe = name.trim().ifBlank { defaultName() }.replace(Regex("[^A-Za-z0-9 _\\-]"), "").trim()
        val finalName = safe.ifBlank { defaultName() }
        val file = File(scriptsDir(context), "$finalName.txt")
        file.writeText(text)
        return file
    }

    fun delete(file: File): Boolean = file.delete()

    fun defaultName(): String =
        "spot_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    fun saveLast(context: Context, text: String, name: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_TEXT, text)
            .putString(KEY_LAST_NAME, name)
            .apply()
    }

    fun lastText(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_TEXT, "") ?: ""

    fun lastName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_NAME, "") ?: ""

    fun savedFont(context: Context, fallback: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_FONT, fallback)

    fun savedSpeed(context: Context, fallback: Float): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_SPEED, fallback)

    fun savedMirror(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MIRROR, false)

    fun saveControls(context: Context, fontSize: Int, speed: Float, mirror: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FONT, fontSize)
            .putFloat(KEY_SPEED, speed)
            .putBoolean(KEY_MIRROR, mirror)
            .apply()
    }

    fun savedAutoScroll(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, false)

    fun saveAutoScroll(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, value).apply()
    }
}
