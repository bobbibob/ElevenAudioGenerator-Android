package com.example.eaa.model

import java.io.File

/**
 * Один сгенерированный аудиофайл. Лежит на диске (file) и в реестре (id).
 */
data class GeneratedItem(
    val file: File,
    val voiceId: String,
    val voiceName: String,
    val createdAt: Long
) {
    val id: String get() = file.absolutePath
}
