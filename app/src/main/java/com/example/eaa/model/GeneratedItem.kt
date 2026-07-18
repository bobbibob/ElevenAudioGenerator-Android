package com.example.eaa.model

import java.io.File

/**
 * Один сгенерированный аудиофайл. Лежит на диске (file) и в реестре (id).
 *
 * [displayName] — пользовательское имя (то, что показывается в UI и в имени файла
 * при сохранении). Если пусто, в UI используется [voiceName].
 */
data class GeneratedItem(
    val file: File,
    val voiceId: String,
    val voiceName: String,
    val createdAt: Long,
    val displayName: String = ""
) {
    val id: String get() = file.absolutePath
}
