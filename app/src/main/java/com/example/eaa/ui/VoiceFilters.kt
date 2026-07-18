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
}

data class VoiceFilterOptions(
    val categories: List<String>,
    val genders: List<String>,
    val ages: List<String>,
    val languages: List<String>,
    val useCases: List<String>
) {
    companion object {
        fun from(voices: List<Voice>): VoiceFilterOptions {
            fun uniqueLower(keys: List<String>): List<String> =
                voices.flatMap { v -> keys.mapNotNull { k -> v.label(k) } }
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

            return VoiceFilterOptions(
                categories = voices.mapNotNull { it.category?.lowercase() }.distinct().sorted(),
                genders = uniqueLower(listOf("gender")),
                ages = uniqueLower(listOf("age")),
                languages = uniqueLower(listOf("language", "accent")),
                useCases = uniqueLower(listOf("use case", "usecase", "description", "descriptiveness"))
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
