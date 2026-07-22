package com.example.eaa.ui

import com.example.eaa.api.SharedVoice
import com.example.eaa.api.Voice
import com.example.eaa.api.label
import com.example.eaa.util.ClonedVoicesStore

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
        val ALL_CATEGORIES = listOf(
            "premade", "cloned", "generated", "professional",
            "community", "famous", "high_quality", "professionals"
        )
        val ALL_GENDERS = listOf("female", "male", "neutral")
        val ALL_AGES = listOf("young", "middle_aged", "old")
        val ALL_LANGUAGES = listOf(
            "en", "en-us", "en-gb", "en-au", "en-in",
            "es", "es-mx", "fr", "de", "it", "pt", "pt-br",
            "pl", "ru", "nl", "ja", "zh", "ko", "ar", "tr", "hi",
            "id", "vi", "uk", "cs", "da", "fi", "el", "he", "ms",
            "ro", "sv", "th", "bg", "fil", "ta", "no"
        )
        val ALL_USE_CASES = listOf(
            "narration", "conversational", "characters", "social media",
            "advertising", "video games", "audiobooks", "educational",
            "entertainment", "informative", "news", "meditation", "asmr"
        )

        /**
         * Строим списки опций из загруженных голосов + объединение со справочником.
         * [extra] — SharedVoice, пришедшие из /v1/shared-voices.
         */
        fun from(voices: List<Voice>, shared: List<SharedVoice> = emptyList()): VoiceFilterOptions {
            fun union(default: List<String>, keys: List<String>): List<String> {
                val fromVoices = voices.flatMap { v ->
                    keys.mapNotNull { k -> v.label(k) }
                }.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

                val fromShared = shared.flatMap { v ->
                    listOfNotNull(
                        v.labels?.entries?.firstOrNull { it.key in keys }?.value,
                        v.language, v.gender, v.age, v.accent, v.useCase
                    )
                }.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

                return (fromVoices + fromShared + default.toSet()).sorted()
            }

            return VoiceFilterOptions(
                categories = union(ALL_CATEGORIES, listOf("category")).ifEmpty { ALL_CATEGORIES },
                genders = union(ALL_GENDERS, listOf("gender")).ifEmpty { ALL_GENDERS },
                ages = union(ALL_AGES, listOf("age")).ifEmpty { ALL_AGES },
                languages = union(ALL_LANGUAGES, listOf("language", "accent")).ifEmpty { ALL_LANGUAGES },
                useCases = union(ALL_USE_CASES, listOf("use case", "usecase", "use_case")).ifEmpty { ALL_USE_CASES }
            )
        }
    }
}

/** Универсальный интерфейс — чтобы фильтры работали и с приватными, и с shared голосами. */
interface VoiceLike {
    val id: String
    val name: String
    val description: String?
    val category: String?
    val previewUrl: String?
    fun label(key: String): String?
}

fun Voice.toLike(): VoiceLike = object : VoiceLike {
    override val id = this@toLike.id
    override val name = this@toLike.name
    override val description = this@toLike.description
    override val category = this@toLike.category
    override val previewUrl = this@toLike.previewUrl
    override fun label(key: String): String? = this@toLike.label(key)
}

fun SharedVoice.toLike(): VoiceLike {
    val s = this
    return object : VoiceLike {
        override val id: String = s.voiceId ?: s.publicOwnerId ?: s.name
        override val name: String = s.name
        override val description: String? = s.description
        override val category: String? = s.category
        override val previewUrl: String? = s.previewUrl
        override fun label(key: String): String? = when (key) {
            "gender" -> s.gender
            "age" -> s.age
            "accent" -> s.accent
            "language" -> s.language
            "use case", "usecase", "use_case" -> s.useCase
            else -> s.labels?.get(key)
        }
    }
}

fun List<VoiceLike>.applyFilters(filters: VoiceFilters): List<VoiceLike> {
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
            v.label("use_case")?.lowercase() == filters.useCase
        val okSearch = filters.search.isBlank() ||
            v.name.contains(filters.search, ignoreCase = true) ||
            (v.description?.contains(filters.search, ignoreCase = true) == true)
        okCategory && okGender && okAge && okLang && okUse && okSearch
    }
}

fun filterDisplay(value: String?): String = value?.replaceFirstChar { it.uppercase() } ?: "Любой"


/**
 * Адаптер ClonedVoicesStore.ClonedVoice → VoiceLike, чтобы клоны участвовали
 * в общем filteredVoices / VoicePicker. previewUrl пустой, но имя/описание
 * сохраняются.
 */
fun ClonedVoicesStore.ClonedVoice.toLike(): VoiceLike {
    val c = this
    return object : VoiceLike {
        override val id: String = c.voiceId
        override val name: String = c.name
        override val description: String? = c.description.takeIf { it.isNotBlank() }
        override val category: String? = "cloned"
        override val previewUrl: String? = null
        override fun label(key: String): String? = when (key) {
            "gender", "age", "accent", "language", "use case", "usecase", "use_case" -> null
            else -> null
        }
    }
}
