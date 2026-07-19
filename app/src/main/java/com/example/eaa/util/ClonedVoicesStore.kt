package com.example.eaa.util

import android.content.Context

/**
 * Локальный реестр клонированных голосов. Хранит пары (voiceId, name,
 * description, createdAt) в SharedPreferences "eaa_cloned_voices" — нужно
 * для:
 *   1) показа списка клонов в CloneVoiceScreen с превью-кнопкой;
 *   2) фолбэка, если /v1/voices не сразу вернёт клон (сеть / кеш).
 */
object ClonedVoicesStore {

    data class ClonedVoice(
        val voiceId: String,
        val name: String,
        val description: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )

    private const val PREF_NAME = "eaa_cloned_voices"
    private const val PREF_KEY = "items"

    fun add(context: Context, voiceId: String, name: String, description: String = "") {
        if (voiceId.isBlank()) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.removeAll { it.startsWith("$voiceId|") }
        current += encode(ClonedVoice(voiceId, name, description))
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    fun remove(context: Context, voiceId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: return
        current.removeAll { it.startsWith("$voiceId|") }
        prefs.edit().putStringSet(PREF_KEY, current).apply()
    }

    fun list(context: Context): List<ClonedVoice> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(PREF_KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { decode(it) }.sortedByDescending { it.createdAt }
    }

    private fun encode(v: ClonedVoice): String =
        "${v.voiceId}|${v.name.replace("|", "/")}|${v.description.replace("|", "/")}|${v.createdAt}"

    private fun decode(s: String): ClonedVoice? {
        val parts = s.split("|", limit = 4)
        if (parts.size < 4) return null
        return ClonedVoice(
            voiceId = parts[0],
            name = parts[1],
            description = parts[2],
            createdAt = parts[3].toLongOrNull() ?: System.currentTimeMillis()
        )
    }
}
