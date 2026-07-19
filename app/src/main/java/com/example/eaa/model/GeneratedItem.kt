package com.example.eaa.model

import java.io.File

/**
 * Один сгенерированный аудиофайл. Лежит на диске (file) и в реестре (id).
 *
 * [displayName] — пользовательское имя (то, что показывается в UI и в имени файла
 * при сохранении). Если пусто, в UI используется [voiceName].
 *
 * [characterCount] — сколько символов исходного текста было отправлено в ElevenLabs.
 * [chunkCount] — на сколько чанков ElevenLabs API разбил запрос.
 * [costCredits] — примерная стоимость в кредитах ElevenLabs (≈ 1 кредит за символ
 * для eleven_multilingual_v2, в UI показывается как «~N кр.»).
 */
data class GeneratedItem(
    val file: File,
    val voiceId: String,
    val voiceName: String,
    val createdAt: Long,
    val displayName: String = "",
    val characterCount: Int = 0,
    val chunkCount: Int = 1,
    val costCredits: Int = 0
) {
    val id: String get() = file.absolutePath
}
