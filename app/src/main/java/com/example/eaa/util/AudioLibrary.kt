package com.example.eaa.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.eaa.model.GeneratedItem
import java.io.File

/**
 * Реестр сгенерированных аудио + сохранение в выбранную пользователем папку.
 *
 * Список берётся не только из SharedPreferences, но и с диска (externalCacheDir),
 * так что даже если реестр потерян/старый — потерянные MP3 всё равно появятся в UI.
 */
object AudioLibrary {

    private const val TAG = "ElevenAudioGen.Lib"
    private const val PREF_NAME = "eaa_library"
    private const val PREF_KEY = "items"
    private const val PREF_SAVE_TREE = "save_tree_uri"
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
                byPath[f.absolutePath] = GeneratedItem(
                    file = f,
                    voiceId = "",
                    voiceName = voice,
                    createdAt = ts,
                    displayName = ""
                )
            }
        }

        // 2) Добавляем записи из реестра, перекрывая те, что нашли на диске, более точными метаданными.
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(PREF_KEY, emptySet()) ?: emptySet()
        for (line in raw) {
            // Формат: path|voiceId|voiceName|createdAt|displayName(displayName может отсутствовать)
            val parts = line.split("|", limit = 5)
            if (parts.size < 4) continue
            val (path, voiceId, voiceName, ts) = parts
            val displayName = if (parts.size >= 5) parts[4] else ""
            val f = File(path)
            if (f.exists()) {
                byPath[path] = GeneratedItem(
                    file = f,
                    voiceId = voiceId,
                    voiceName = voiceName,
                    createdAt = ts.toLongOrNull() ?: f.lastModified(),
                    displayName = displayName
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

    fun add(
        context: Context,
        file: File,
        voiceId: String,
        voiceName: String,
        displayName: String = ""
    ) {
        val item = GeneratedItem(
            file = file,
            voiceId = voiceId,
            voiceName = voiceName,
            createdAt = System.currentTimeMillis(),
            displayName = displayName
        )
        save(context, item)
    }

    fun remove(context: Context, item: GeneratedItem) {
        val deleted = runCatching { item.file.delete() }.getOrDefault(false)
        Log.i(TAG, "remove ${item.file.absolutePath} → deleted=$deleted")
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: return
        current.remove(encode(item))
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    /** Установить/снять пользовательское имя. */
    fun setDisplayName(context: Context, item: GeneratedItem, newName: String) {
        val updated = item.copy(displayName = newName.trim())
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(encode(item))
        current += encode(updated)
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    /** Сохранить URI выбранной пользователем папки для экспорта. */
    fun setSaveTree(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (uri == null) prefs.edit().remove(PREF_SAVE_TREE).apply()
        else prefs.edit().putString(PREF_SAVE_TREE, uri.toString()).apply()
    }

    fun getSaveTree(context: Context): Uri? {
        val s = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(PREF_SAVE_TREE, null) ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    private fun save(context: Context, item: GeneratedItem) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current += encode(item)
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    private fun encode(i: GeneratedItem): String =
        "${i.file.absolutePath}|${i.voiceId}|${i.voiceName}|${i.createdAt}|${i.displayName}"

    /**
     * Имя файла при экспорте: displayName (если задан) → "<voiceName>_<ts>.mp3".
     * Расширение .mp3 всегда добавляется.
     */
    /** Видимое имя для UI: displayName, иначе voiceName. */
    fun visibleName(item: GeneratedItem): String =
        item.displayName.ifBlank { item.voiceName }

    fun exportFileName(item: GeneratedItem): String {
        val base = item.displayName.ifBlank { "${item.voiceName}_${item.createdAt}" }
        val safe = sanitizeFileName(base)
        return if (safe.endsWith(".mp3", ignoreCase = true)) safe else "$safe.mp3"
    }

    /** Сделать имя безопасным для ФС. */
    fun sanitizeFileName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
            .replace(Regex("\\s+"), " ")
        return cleaned.ifBlank { "audio_${System.currentTimeMillis()}" }
    }

    /**
     * Сохранить аудиофайл в выбранную пользователем папку (если задана),
     * иначе — в публичную Music/ElevenAudioGenerator/.
     *
     * Возвращает строку для UI: "<папка>/<имя_файла>" или null при ошибке.
     */
    fun exportToUserFolder(context: Context, item: GeneratedItem): String? {
        val displayName = exportFileName(item)
        val tree = getSaveTree(context)
        return if (tree != null) {
            exportToTree(context, tree, item.file, displayName)
        } else {
            // fallback: Music/ElevenAudioGenerator
            val publicName = exportToMusic(context, item.file, displayName) ?: return null
            publicName
        }
    }

    /**
     * Старое имя метода оставлено для совместимости (использовалось в коде).
     * Сохраняет в публичную Music/PUBLIC_SUBDIR через MediaStore или legacy FS.
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

    /**
     * Сохранение в пользовательскую папку, выбранную через ACTION_OPEN_DOCUMENT_TREE.
     * Поддерживает Android 5+ (через DocumentsContract).
     */
    private fun exportToTree(context: Context, treeUri: Uri, src: File, displayName: String): String? {
        return runCatching {
            val tree = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )
            val resolver = context.contentResolver
            // Проверяем, нет ли уже файла с таким именем — если есть, дописываем (1), (2)…
            var name = displayName
            val ext = ".mp3"
            val stem = if (name.endsWith(ext, ignoreCase = true)) name.dropLast(ext.length) else name
            var counter = 1
            while (fileExistsInTree(context, tree, name)) {
                name = "$stem ($counter)$ext"
                counter++
            }

            val doc = android.provider.DocumentsContract.createDocument(
                resolver, tree, "audio/mpeg", name
            ) ?: return@runCatching null
            resolver.openOutputStream(doc)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            val folderName = humanFolderName(context, treeUri)
            if (folderName != null) "$folderName/$name" else name
        }.getOrElse {
            Log.e(TAG, "Tree export failed: ${it.message}", it)
            null
        }
    }

    private fun fileExistsInTree(context: Context, treeUri: Uri, name: String): Boolean {
        val resolver = context.contentResolver
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        )
        return runCatching {
            resolver.query(
                childrenUri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                val idx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) {
                    if (idx >= 0 && c.getString(idx) == name) return true
                }
            }
            false
        }.getOrDefault(false)
    }

    /** Человекочитаемое имя выбранной папки, для UI/тостов. */
    fun humanFolderName(context: Context, treeUri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                treeUri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (i >= 0) c.getString(i) else null
                } else null
            }
        }.getOrNull()
    }
}
