package com.example.eaa.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for ElevenLabs API.
 */
interface ElevenLabsService {
    @GET("voices")
    suspend fun fetchVoices(@Header("xi-api-key") apiKey: String): VoiceResponse

    @GET("shared-voices")
    suspend fun fetchSharedVoices(
        @Header("xi-api-key") apiKey: String,
        @Query("page_size") pageSize: Int = 100,
        @Query("page") page: Int = 0
    ): SharedVoicesResponse

    @GET("user/subscription")
    suspend fun fetchSubscription(@Header("xi-api-key") apiKey: String): SubscriptionResponse

    @POST("text-to-speech/{voiceId}")
    suspend fun synthesize(
        @Path("voiceId") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Query("output_format") outputFormat: String,
        @Body request: SynthesizeRequest
    ): ResponseBody

    /**
     * Клонирование голоса (Instant Voice Cloning):
     *   POST /v1/voices/add
     * multipart/form-data:
     *   - name: String
     *   - files[]: аудио-файлы (sample1, sample2, ...)
     *   - description (опц.)
     *
     * Возвращает voice_id нового голоса.
     */
    @Multipart
    @POST("voices/add")
    suspend fun cloneVoice(
        @Header("xi-api-key") apiKey: String,
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody,
        @Part files: List<MultipartBody.Part>,
        @Part("labels") labels: RequestBody
    ): CloneVoiceResponse
}

@JsonClass(generateAdapter = true)
data class CloneVoiceResponse(
    @Json(name = "voice_id") val voiceId: String,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class VoiceResponse(@Json(name = "voices") val voices: List<Voice>)

@JsonClass(generateAdapter = true)
data class SharedVoicesResponse(
    @Json(name = "voices") val voices: List<SharedVoice>,
    @Json(name = "has_more") val hasMore: Boolean? = null,
    @Json(name = "last_sort_id") val lastSortId: String? = null
)

@JsonClass(generateAdapter = true)
data class SharedVoice(
    @Json(name = "public_owner_id") val publicOwnerId: String? = null,
    @Json(name = "voice_id") val voiceId: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "labels") val labels: Map<String, String>? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "gender") val gender: String? = null,
    @Json(name = "age") val age: String? = null,
    @Json(name = "accent") val accent: String? = null,
    @Json(name = "use_case") val useCase: String? = null
)

@JsonClass(generateAdapter = true)
data class Voice(
    @Json(name = "voice_id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "labels") val labels: Map<String, String>? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    @Json(name = "available_for_tiers") val availableForTiers: List<String>? = null,
    @Json(name = "settings") val settings: VoiceSettings? = null
)

@JsonClass(generateAdapter = true)
data class SynthesizeRequest(
    @Json(name = "text") val text: String,
    @Json(name = "model_id") val modelId: String = "eleven_multilingual_v2",
    @Json(name = "voice_settings") val voiceSettings: VoiceSettings,
    @Json(name = "pronunciation_dictionary_locators") val pronunciationDictionaryLocators: List<Any>? = null,
    @Json(name = "seed") val seed: Long? = null,
    @Json(name = "previous_text") val previousText: String? = null,
    @Json(name = "next_text") val nextText: String? = null,
    @Json(name = "previous_request_ids") val previousRequestIds: List<String>? = null,
    @Json(name = "next_request_ids") val nextRequestIds: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class VoiceSettings(
    @Json(name = "stability") val stability: Double = 0.5,
    @Json(name = "similarity_boost") val similarityBoost: Double = 0.75,
    @Json(name = "style") val style: Double = 0.0,
    @Json(name = "use_speaker_boost") val useSpeakerBoost: Boolean = true,
    @Json(name = "speed") val speed: Double = 1.0,
    @Json(name = "pitch") val pitch: Double = 0.0
)

fun Voice.label(key: String): String? = labels?.get(key)

@JsonClass(generateAdapter = true)
data class SubscriptionResponse(
    @Json(name = "tier") val tier: String? = null,
    @Json(name = "character_count") val characterCount: Int? = null,
    @Json(name = "character_limit") val characterLimit: Int? = null,
    @Json(name = "next_character_count_reset_unix") val nextResetUnix: Long? = null,
    @Json(name = "allowed_to_run_unlimited") val allowedUnlimited: Boolean? = null,
    @Json(name = "status") val status: String? = null
)

object OutputFormats {
    data class Option(val id: String, val label: String, val hint: String)
    val ALL: List<Option> = listOf(
        Option("mp3_44100_128", "MP3 44.1 кГц 128 kbps", "Стандарт, универсальный. Баланс качества и размера."),
        Option("mp3_22050_32",  "MP3 22 кГц 32 kbps",   "Голосовые сообщения, маленький файл."),
        Option("mp3_24000_48",  "MP3 24 кГц 48 kbps",   "Компромисс между размером и качеством."),
        Option("mp3_44100_64",  "MP3 44.1 кГц 64 kbps", "Хорошее качество, меньше места."),
        Option("mp3_44100_96",  "MP3 44.1 кГц 96 kbps", "Близко к CD, но компактнее 128."),
        Option("mp3_44100_192", "MP3 44.1 кГц 192 kbps", "Высокий битрейт, ближе к lossless."),
        Option("pcm_16000",     "PCM 16 кГц",           "WAV без сжатия, 16 кГц."),
        Option("pcm_22050",     "PCM 22 кГц",           "WAV без сжатия, 22 кГц."),
        Option("pcm_24000",     "PCM 24 кГц",           "WAV без сжатия, 24 кГц."),
        Option("pcm_44100",     "PCM 44.1 кГц",         "WAV без сжатия, CD-качество.")
    )
}
