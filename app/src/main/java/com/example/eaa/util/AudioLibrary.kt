package com.example.eaa.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.eaa.model.GeneratedItem
import java.io.File

/**
 * Реестр сгенерированных аудио + сохранение в публичную папку Music/ElevenAudioGenerator.
 */
object AudioLibrary {

    private const val PREF_NAME = "eaa_library"
    private const val PREF_KEY = "items"
    private const val PUBLIC_SUBDIR = "ElevenAudioGenerator"

    /** Список всех сгенерированных файлов, зарегистрированных в приложении. */
    fun list(context: Context): List<GeneratedItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(PREF_KEY, emptySet()) ?: emptySet()
        val out = mutableListOf<GeneratedItem>()
        for (line in raw) {
            val parts = line.split("|", limit = 4)
            if (parts.size < 4) continue
            val (path, voiceId, voiceName, ts) = parts
            val f = File(path)
            if (f.exists()) {
                out += GeneratedItem(
                    file = f,
                    voiceId = voiceId,
                    voiceName = voiceName,
                    createdAt = ts.toLongOrNull() ?: 0L
                )
            }
        }
        return out.sortedByDescending { it.createdAt }
    }

    fun add(context: Context, file: File, voiceId: String, voiceName: String) {
        val item = GeneratedItem(file, voiceId, voiceName, System.currentTimeMillis())
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current += encode(item)
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    fun remove(context: Context, item: GeneratedItem) {
        runCatching { item.file.delete() }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: return
        current.remove(encode(item))
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    private fun encode(i: GeneratedItem): String =
        "${i.file.absolutePath}|${i.voiceId}|${i.voiceName}|${i.createdAt}"

    /**
     * Сохранить аудиофайл в публичную папку Music/ElevenAudioGenerator/.
     * На Android 10+ (Q) — через MediaStore, на старых — через Environment.getExternalStoragePublicDirectory.
     * Возвращает отображаемое имя (например, "ElevenAudioGenerator/Name_123.mp3") или null при ошибке.
     */
    fun exportToMusic(context: Context, file: File, displayName: String): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(context, file, displayName)
        } else {
            exportLegacy(file, displayName)
        }
    }

    private fun exportViaMediaStore(context: Context, src: File, displayName: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + PUBLIC_SUBDIR)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            "$PUBLIC_SUBDIR/$displayName"
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun exportLegacy(src: File, displayName: String): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val outDir = File(musicDir, PUBLIC_SUBDIR)
            if (!outDir.exists()) outDir.mkdirs()
            val dst = File(outDir, displayName)
            src.copyTo(dst, overwrite = true)
            "$PUBLIC_SUBDIR/$displayName"
        }.getOrNull()
    }
}
