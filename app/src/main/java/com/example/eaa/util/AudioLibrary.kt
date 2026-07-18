package com.example.eaa.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.eaa.model.GeneratedItem
import java.io.File

/**
 * Реестр сгенерированных аудио + сохранение в публичную папку Music/ElevenAudioGenerator.
 *
 * Список берётся не только из SharedPreferences, но и с диска (externalCacheDir),
 * так что даже если реестр потерян/старый — потерянные MP3 всё равно появятся в UI.
 */
object AudioLibrary {

    private const val TAG = "ElevenAudioGen.Lib"
    private const val PREF_NAME = "eaa_library"
    private const val PREF_KEY = "items"
    private const val PUBLIC_SUBDIR = "ElevenAudioGenerator"
    private val FILENAME_REGEX = Regex("^(?<voice>.+?)_(?<ts>\\d{10,19})\\.mp3$")

    /** Все сгенерированные файлы (из кэша + реестра). Свежие сверху. */
    fun list(context: Context): List<GeneratedItem> {
        val byPath = LinkedHashMap<String, GeneratedItem>()

        // 1) Сканируем кэш-папку — это даст нам ВСЕ файлы, даже без записи в реестре.
        val cacheDir = context.externalCacheDir
        if (cacheDir != null && cacheDir.exists()) {
            cacheDir.listFiles { f -> f.isFile && f.extension.equals("mp3", true) }?.forEach { f ->
                val match = FILENAME_REGEX.matchEntire(f.name)
                val ts = match?.groups?.get("ts")?.value?.toLongOrNull() ?: f.lastModified()
                val voice = match?.groups?.get("voice")?.value?.replace('_', ' ') ?: "Unknown"
                byPath[f.absolutePath] = GeneratedItem(f, "", voice, ts)
            }
        }

        // 2) Добавляем записи из реестра, перекрывая те, что нашли на диске, более точными метаданными.
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(PREF_KEY, emptySet()) ?: emptySet()
        for (line in raw) {
            val parts = line.split("|", limit = 4)
            if (parts.size < 4) continue
            val (path, voiceId, voiceName, ts) = parts
            val f = File(path)
            if (f.exists()) {
                byPath[path] = GeneratedItem(
                    file = f,
                    voiceId = voiceId,
                    voiceName = voiceName,
                    createdAt = ts.toLongOrNull() ?: f.lastModified()
                )
            } else {
                Log.w(TAG, "Registry references missing file: $path — будет удалён из реестра")
            }
        }

        // Чистим реестр от записей, указывающих на отсутствующие файлы.
        val live = byPath.values.map { encode(it) }.toSet()
        if (prefs.getStringSet(PREF_KEY, emptySet()) != live) {
            prefs.edit().putStringSet(PREF_KEY, live).apply()
        }

        return byPath.values.sortedByDescending { it.createdAt }
    }

    fun add(context: Context, file: File, voiceId: String, voiceName: String) {
        val item = GeneratedItem(file, voiceId, voiceName, System.currentTimeMillis())
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current += encode(item)
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    fun remove(context: Context, item: GeneratedItem) {
        val deleted = runCatching { item.file.delete() }.getOrDefault(false)
        Log.i(TAG, "remove ${item.file.absolutePath} → deleted=$deleted")
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: return
        current.remove(encode(item))
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    private fun encode(i: GeneratedItem): String =
        "${i.file.absolutePath}|${i.voiceId}|${i.voiceName}|${i.createdAt}"

    /**
     * Сохранить аудиофайл в публичную папку Music/ElevenAudioGenerator/.
     * На Android 10+ — MediaStore, на старых — прямой путь в публичный MUSIC.
     * Возвращает отображаемое имя (например, "ElevenAudioGenerator/Name.mp3") или null при ошибке.
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
            Log.e(TAG, "MediaStore export failed: ${it.message}", it)
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
        }.getOrElse {
            Log.e(TAG, "Legacy export failed: ${it.message}", it)
            null
        }
    }
}
