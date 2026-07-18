package com.example.eaa.ui

import com.example.eaa.api.Voice
import com.example.eaa.api.label

/**
 * Фильтры списка голосов — повторяют фильтры в web-консоли ElevenLabs.
 */
data class VoiceFilters(
    val category: String? = null,
    val gender: String? = null,
    val age: String? = null,
    val language: String? = null,
    val useCase: String? = null,
    val search: String = ""
) {
    fun isEmpty(): Boolean =
        category == null && gender == null && age == null && language == null && useCase == null && search.isBlank()

    fun activeCount(): Int =
        listOf(category, gender, age, language, useCase).count { it != null } + (if (search.isNotBlank()) 1 else 0)
}

/**
 * Полные наборы значений для каждого фильтра.
 * Берём объединение: (1) то, что встречается у загруженных голосов в labels,
 * (2) фиксированный справочник ElevenLabs, чтобы пользователь видел все варианты.
 */
data class VoiceFilterOptions(
    val categories: List<String>,
    val genders: List<String>,
    val ages: List<String>,
    val languages: List<String>,
    val useCases: List<String>
) {
    companion object {
        // Полный справочник вариантов из ElevenLabs Voice Library
        val ALL_CATEGORIES = listOf("premade", "cloned", "generated", "professional")
        val ALL_GENDERS = listOf("female", "male", "neutral")
        val ALL_AGES = listOf("young", "middle_aged", "old")
        val ALL_LANGUAGES = listOf(
            "en", "en-us", "en-gb", "es", "es-mx", "fr", "de", "it", "pt", "pt-br",
            "pl", "ru", "nl", "ja", "zh", "ko", "ar", "tr", "hi", "id", "vi", "uk",
            "cs", "da", "fi", "el", "he", "ms", "ro", "sv", "th", "bg", "fil"
        )
        val ALL_USE_CASES = listOf(
            "narration", "conversational", "characters", "social media", "advertising",
            "video games", "audiobooks", "educational", "entertainment", "informative",
            "news", "meditation", "asmr"
        )

        fun from(voices: List<Voice>): VoiceFilterOptions {
            fun union(default: List<String>, keys: List<String>): List<String> {
                val fromVoices = voices.flatMap { v ->
                    keys.mapNotNull { k -> v.label(k) }
                }.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                return (fromVoices + default.toSet()).sorted()
            }

            return VoiceFilterOptions(
                categories = union(ALL_CATEGORIES, listOf("category")).ifEmpty { ALL_CATEGORIES },
                genders = union(ALL_GENDERS, listOf("gender")).ifEmpty { ALL_GENDERS },
                ages = union(ALL_AGES, listOf("age")).ifEmpty { ALL_AGES },
                languages = union(ALL_LANGUAGES, listOf("language", "accent")).ifEmpty { ALL_LANGUAGES },
                useCases = union(
                    ALL_USE_CASES,
                    listOf("use case", "usecase", "description", "descriptiveness")
                ).ifEmpty { ALL_USE_CASES }
            )
        }
    }
}

fun List<Voice>.applyFilters(filters: VoiceFilters): List<Voice> {
    if (filters.isEmpty()) return this
    return filter { v ->
        val okCategory = filters.category == null || v.category?.lowercase() == filters.category
        val okGender = filters.gender == null || v.label("gender")?.lowercase() == filters.gender
        val okAge = filters.age == null || v.label("age")?.lowercase() == filters.age
        val okLang = filters.language == null ||
            v.label("language")?.lowercase() == filters.language ||
            v.label("accent")?.lowercase() == filters.language
        val okUse = filters.useCase == null ||
            v.label("use case")?.lowercase() == filters.useCase ||
            v.label("usecase")?.lowercase() == filters.useCase ||
            v.label("description")?.lowercase() == filters.useCase ||
            v.label("descriptiveness")?.lowercase() == filters.useCase
        val okSearch = filters.search.isBlank() ||
            v.name.contains(filters.search, ignoreCase = true) ||
            (v.description?.contains(filters.search, ignoreCase = true) == true)
        okCategory && okGender && okAge && okLang && okUse && okSearch
    }
}

/** Удобный лейбл "Любой" / конкретное значение для UI фильтра. */
fun filterDisplay(value: String?): String = value?.replaceFirstChar { it.uppercase() } ?: "Любой"
