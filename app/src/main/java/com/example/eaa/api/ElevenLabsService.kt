package com.example.eaa.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for ElevenLabs TTS API.
 */
interface ElevenLabsService {
    @GET("voices")
    suspend fun fetchVoices(@Header("xi-api-key") apiKey: String): VoiceResponse

    /**
     * @param outputFormat e.g. "mp3_44100_128" — передаётся в query, не в body
     */
    @POST("text-to-speech/{voiceId}")
    suspend fun synthesize(
        @Path("voiceId") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Query("output_format") outputFormat: String,
        @Body request: SynthesizeRequest
    ): ResponseBody

    /** Баланс и подписка. GET /v1/user/subscription. */
    @GET("user/subscription")
    suspend fun fetchSubscription(@Header("xi-api-key") apiKey: String): SubscriptionResponse
}

@JsonClass(generateAdapter = true)
data class VoiceResponse(
    @Json(name = "voices") val voices: List<Voice>
)

/**
 * Расширенная модель голоса — поля совпадают с тем, что возвращает
 * GET /v1/voices в ElevenLabs. Необязательные поля помечены как null.
 */
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
    @Json(name = "voice_settings") val voiceSettings: VoiceSettings
)

@JsonClass(generateAdapter = true)
data class VoiceSettings(
    @Json(name = "stability") val stability: Double,
    @Json(name = "similarity_boost") val similarityBoost: Double,
    @Json(name = "style") val style: Double = 0.0,
    @Json(name = "use_speaker_boost") val useSpeakerBoost: Boolean = true,
    @Json(name = "speed") val speed: Double = 1.0,
    @Json(name = "pitch") val pitch: Double = 0.0
)

/** Удобный доступ к label-характеристикам голоса. */
fun Voice.label(key: String): String? = labels?.get(key)

/**
 * Ответ GET /v1/user/subscription. Нас интересует прежде всего [characterCount]
 * (остаток кредитов) и [tier] — уровень подписки.
 */
@JsonClass(generateAdapter = true)
data class SubscriptionResponse(
    @Json(name = "tier") val tier: String? = null,
    @Json(name = "character_count") val characterCount: Int? = null,
    @Json(name = "character_limit") val characterLimit: Int? = null,
    @Json(name = "next_character_count_reset_unix") val nextResetUnix: Long? = null,
    @Json(name = "allowed_to_run_unlimited") val allowedUnlimited: Boolean? = null,
    @Json(name = "status") val status: String? = null
)
